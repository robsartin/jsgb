package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/**
 * Golden-test harness: every case captured from the C binaries under {@code
 * /oracle/demos/<demo>/<case>.*} is run through the matching {@link Jsgb#DEMOS} entry and checked
 * byte for byte against the C's recorded stdout, stderr, exit status and written files.
 */
class DemoOracleTest {

  @TempDir Path tmp;

  private static Path oracleRoot() {
    try {
      return Path.of(DemoOracleTest.class.getResource("/oracle/demos").toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  /** One dynamic test per {@code <demo>/<case>} found under the oracle root and registered. */
  @TestFactory
  Stream<DynamicTest> shouldReproduceCOutputWhenDemoRunsCapturedCase() throws IOException {
    Path root = oracleRoot();
    List<DynamicTest> tests = new ArrayList<>();
    List<Path> demoDirs;
    try (Stream<Path> entries = Files.list(root)) {
      demoDirs =
          entries
              .filter(Files::isDirectory)
              .filter(p -> Jsgb.DEMOS.containsKey(p.getFileName().toString()))
              .sorted()
              .toList();
    }
    for (Path demoDir : demoDirs) {
      String demo = demoDir.getFileName().toString();
      List<Path> exitFiles;
      try (Stream<Path> entries = Files.list(demoDir)) {
        exitFiles =
            entries.filter(p -> p.getFileName().toString().endsWith(".exit")).sorted().toList();
      }
      for (Path exitFile : exitFiles) {
        String fileName = exitFile.getFileName().toString();
        String caseName = fileName.substring(0, fileName.length() - ".exit".length());
        tests.add(dynamicTest(demo + "/" + caseName, () -> runCase(demoDir, demo, caseName)));
      }
    }
    return tests.stream();
  }

  /** Guards against the scan above silently finding nothing to run. */
  @Test
  @DisplayName("the oracle scan finds at least one case to run")
  void shouldRunAtLeastOneCaseWhenHarnessScansOracle() throws IOException {
    assertThat(shouldReproduceCOutputWhenDemoRunsCapturedCase().count()).isGreaterThanOrEqualTo(1);
  }

  /**
   * Guards against the {@link Jsgb#DEMOS}-membership filter above silently dropping an oracle
   * directory whose demo was never registered: every subdirectory of {@code /oracle/demos} must be
   * a registered demo name.
   */
  @Test
  @DisplayName("every oracle demo directory names a demo registered in Jsgb.DEMOS")
  void shouldRegisterEveryOracleDemoWhenAllPorted() throws IOException {
    List<String> unregistered;
    try (Stream<Path> entries = Files.list(oracleRoot())) {
      unregistered =
          entries
              .filter(Files::isDirectory)
              .map(p -> p.getFileName().toString())
              .filter(name -> !Jsgb.DEMOS.containsKey(name))
              .sorted()
              .toList();
    }
    assertThat(unregistered).isEmpty();
  }

  /**
   * Guards against a case leaving process-fresh globals dirty for the next one: the C starts each
   * program in a fresh process, so {@code runCase} must restore the {@code run}-entry defaults once
   * a case finishes, not just reset them at the top of the next {@code run}.
   */
  @Test
  @DisplayName("runCase restores process-fresh globals when a case leaves them dirty")
  void shouldRestoreProcessFreshGlobalsWhenCaseEnds() throws IOException {
    Path laddersDir = oracleRoot().resolve("ladders");
    runCase(laddersDir, "ladders", "alpha_heur_verbose");

    assertThat(com.robsartin.jsgb.graph.Gb.verbose).isEqualTo(0);
    assertThat(com.robsartin.jsgb.dijk.Dijkstra.queue)
        .isInstanceOf(com.robsartin.jsgb.dijk.DList.class);
    assertThat(Mems.mems).isEqualTo(0);
  }

  private void runCase(Path demoDir, String demo, String caseName) throws IOException {
    Path dir = Files.createTempDirectory(tmp, "case");
    String seedPrefix = caseName + ".seed.";
    try (Stream<Path> entries = Files.list(demoDir)) {
      for (Path p : entries.toList()) {
        String name = p.getFileName().toString();
        if (name.startsWith(seedPrefix)) {
          Files.copy(p, dir.resolve(name.substring(seedPrefix.length())));
        }
      }
    }

    List<String> args = readLines(demoDir.resolve(caseName + ".args"));
    byte[] stdin = readBytesOrEmpty(demoDir.resolve(caseName + ".in"));

    ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(outBytes, true, StandardCharsets.ISO_8859_1);
    PrintStream err = new PrintStream(errBytes, true, StandardCharsets.ISO_8859_1);
    CStdin in = new CStdin(new ByteArrayInputStream(stdin));

    int code;
    try {
      code = Jsgb.DEMOS.get(demo).run(args.toArray(new String[0]), in, out, err, dir);
    } finally {
      // The C starts each program in a fresh process; restore the run-entry defaults so one
      // case's process-fresh globals never leak into the next case's assertions.
      com.robsartin.jsgb.graph.Gb.verbose = 0;
      com.robsartin.jsgb.dijk.Dijkstra.queue = new com.robsartin.jsgb.dijk.DList();
      Mems.mems = 0;
    }

    assertThat(outBytes.toString(StandardCharsets.ISO_8859_1))
        .isEqualTo(readStringOrEmpty(demoDir.resolve(caseName + ".out")));
    assertThat(errBytes.toString(StandardCharsets.ISO_8859_1))
        .isEqualTo(readStringOrEmpty(demoDir.resolve(caseName + ".err")));
    assertThat(Jsgb.exitStatus(code))
        .isEqualTo(Integer.parseInt(Files.readString(demoDir.resolve(caseName + ".exit")).trim()));

    String filePrefix = caseName + ".file.";
    try (Stream<Path> entries = Files.list(demoDir)) {
      for (Path p : entries.toList()) {
        String name = p.getFileName().toString();
        if (name.startsWith(filePrefix)) {
          String written = name.substring(filePrefix.length());
          assertThat(Files.readAllBytes(dir.resolve(written))).isEqualTo(Files.readAllBytes(p));
        }
      }
    }
  }

  private static List<String> readLines(Path p) throws IOException {
    return Files.exists(p) ? Files.readAllLines(p, StandardCharsets.ISO_8859_1) : List.of();
  }

  private static byte[] readBytesOrEmpty(Path p) throws IOException {
    return Files.exists(p) ? Files.readAllBytes(p) : new byte[0];
  }

  private static String readStringOrEmpty(Path p) throws IOException {
    return Files.exists(p) ? new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1) : "";
  }
}
