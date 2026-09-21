package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsgbTest {

  @TempDir Path workDir;

  private static PrintStream sink() {
    return new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.ISO_8859_1);
  }

  private static CStdin emptyStdin() {
    return new CStdin(InputStream.nullInputStream());
  }

  @Test
  @DisplayName("dispatch prints usage with every registered demo name and returns 1 when unknown")
  void shouldPrintUsageWhenDemoNameIsUnknown() {
    ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    PrintStream err = new PrintStream(errBytes, true, StandardCharsets.ISO_8859_1);

    int code = Jsgb.dispatch(new String[] {"nope"}, emptyStdin(), sink(), err, workDir);

    assertThat(code).isEqualTo(1);
    assertThat(errBytes.toString(StandardCharsets.ISO_8859_1))
        .isEqualTo("Usage: jsgb <demo> [arguments]\n  queen\n  test_sample\n");
  }

  @Test
  @DisplayName("dispatch prints usage and returns 1 when no demo name is given")
  void shouldPrintUsageWhenArgsAreEmpty() {
    ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    PrintStream err = new PrintStream(errBytes, true, StandardCharsets.ISO_8859_1);

    int code = Jsgb.dispatch(new String[0], emptyStdin(), sink(), err, workDir);

    assertThat(code).isEqualTo(1);
    assertThat(errBytes.toString(StandardCharsets.ISO_8859_1)).startsWith("Usage: jsgb");
  }

  @Test
  @DisplayName("exitStatus masks a negative C return value to the low 8 bits")
  void shouldMaskNegativeReturnValueWhenExitStatusComputed() {
    assertThat(Jsgb.exitStatus(-2)).isEqualTo(254);
  }

  @Test
  @DisplayName("dispatch runs the named demo and returns its own C return value")
  void shouldRunQueenAndWriteQueenGbWhenDispatched() {
    int code = Jsgb.dispatch(new String[] {"queen"}, emptyStdin(), sink(), sink(), workDir);

    assertThat(code).isZero();
    assertThat(workDir.resolve("queen.gb")).exists();
  }
}
