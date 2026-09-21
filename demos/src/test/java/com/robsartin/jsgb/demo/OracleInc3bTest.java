package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.books.Books;
import com.robsartin.jsgb.econ.Econ;
import com.robsartin.jsgb.games.Games;
import com.robsartin.jsgb.gates.Gates;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.lisa.Lisa;
import java.io.PrintStream;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Every case reproduces a run of the C library recorded in oracle_inc3b.out. */
class OracleInc3bTest {

  @Test
  @DisplayName("book and bi_book print exactly as the C")
  void shouldMatchOracleWhenBooksPrinted() {
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Books.book("anna", 50L, 10L, 1L, 10L, 1L, 1L, 1L), 3, ps)))
        .isEqualTo(Oracle.inc3b("book_anna"));
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Books.book("david", 30L, 5L, 0L, 0L, 1L, 1L, 5L), 0, ps)))
        .isEqualTo(Oracle.inc3b("book_david"));
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Books.biBook("jean", 100L, 0L, 1L, 5L, 1L, 1L, 2L), 82, ps)))
        .isEqualTo(Oracle.inc3b("bi_book_jean"));
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Books.biBook("homer", 100L, 0L, 10L, 20L, 1L, 1L, 3L), 105, ps)))
        .isEqualTo(Oracle.inc3b("bi_book_homer"));
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Books.book("huck", 0L, 0L, 0L, 0L, 2000000L, 0L, 1L), 0, ps)))
        .isEqualTo(Oracle.inc3b("book_bad"));
    Graph g = Books.book("huck", 20L, 0L, 0L, 0L, 1L, 1L, 4L);
    String out =
        Oracle.capture(
            ps -> {
              ps.print(
                  "chapters="
                      + Books.chapters
                      + " first="
                      + Books.chapName[1]
                      + " last="
                      + Books.chapName[(int) Books.chapters]
                      + "\n");
              TestSample.printSample(g, 0, ps);
            });
    assertThat(out).isEqualTo(Oracle.inc3b("book_chapters"));
  }

  @Test
  @DisplayName(
      "econ prints exactly as the C for the full, omitted, greedy, users and default cases")
  void shouldMatchOracleWhenEconPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Econ.econ(81L, 0L, 0L, 1L), 0, ps)))
        .isEqualTo(Oracle.inc3b("econ_full"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Econ.econ(40L, 2L, 1000L, 5L), 5, ps)))
        .isEqualTo(Oracle.inc3b("econ_omit2"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Econ.econ(10L, 1L, 0L, 0L), 2, ps)))
        .isEqualTo(Oracle.inc3b("econ_greedy"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Econ.econ(20L, 0L, 0L, 7L), 19, ps)))
        .isEqualTo(Oracle.inc3b("econ_users"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Econ.econ(0L, 0L, 0L, 0L), 80, ps)))
        .isEqualTo(Oracle.inc3b("econ_default"));
  }

  @Test
  @DisplayName("games prints exactly as the C")
  void shouldMatchOracleWhenGamesPrinted() {
    assertThat(
            Oracle.capture(
                ps -> TestSample.printSample(Games.games(120L, 0L, 0L, 0L, 0L, 0L, 0L, 1L), 0, ps)))
        .isEqualTo(Oracle.inc3b("games_full"));
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(Games.games(30L, 1L, 1L, 1L, 1L, 60L, 90L, 7L), 3, ps)))
        .isEqualTo(Oracle.inc3b("games_window"));
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Games.games(10L, -1L, -1L, -1L, -1L, -5L, 0L, 3L), 0, ps)))
        .isEqualTo(Oracle.inc3b("games_neg"));
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Games.games(5L, 200000L, 0L, 0L, 0L, 0L, 0L, 1L), 0, ps)))
        .isEqualTo(Oracle.inc3b("games_bad"));
  }

  @Test
  @DisplayName("lisa, plane_lisa and bi_lisa print exactly as the C")
  void shouldMatchOracleWhenLisaPrinted() {
    long[] a = Lisa.lisa(4L, 4L, 255L, 0L, 0L, 0L, 0L, 0L, 0L);
    assertThat(matrix(a, 4)).isEqualTo(Oracle.inc3b("lisa_matrix"));
    long[] b = Lisa.lisa(3L, 5L, 7L, 100L, 110L, 100L, 110L, 1000L, 60000L);
    assertThat(matrix(b, 5)).isEqualTo(Oracle.inc3b("lisa_window"));
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Lisa.planeLisa(20L, 20L, 10L, 0L, 0L, 0L, 0L, 0L, 0L), 0, ps)))
        .isEqualTo(Oracle.inc3b("plane_lisa_small"));
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Lisa.planeLisa(0L, 0L, 0L, 100L, 110L, 100L, 110L, 0L, 0L), 3, ps)))
        .isEqualTo(Oracle.inc3b("plane_lisa_window"));
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Lisa.biLisa(10L, 10L, 0L, 0L, 0L, 0L, 30000L, 0L), 0, ps)))
        .isEqualTo(Oracle.inc3b("bi_lisa"));
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Lisa.biLisa(10L, 10L, 100L, 110L, 100L, 110L, 20000L, 1L), 12, ps)))
        .isEqualTo(Oracle.inc3b("bi_lisa_c"));
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Lisa.planeLisa(5L, 5L, 0L, 10L, 10L, 0L, 0L, 0L, 0L), 0, ps)))
        .isEqualTo(Oracle.inc3b("lisa_bad"));
  }

  /** The harness's printing of a lisa matrix: the id line, then rows of space-separated values. */
  private static String matrix(long[] a, int cols) {
    StringBuilder sb = new StringBuilder(Lisa.lisaId).append('\n');
    for (int k = 0; k < a.length; k++) {
      sb.append(a[k]).append(k % cols == cols - 1 ? '\n' : ' ');
    }
    return sb.toString();
  }

  @Test
  @DisplayName("risc, gate_eval, run_risc and print_gates print exactly as the C")
  void shouldMatchOracleWhenRiscPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Gates.risc(2L), 1, ps)))
        .isEqualTo(Oracle.inc3b("risc2_sample"));
    StringBuilder buf = new StringBuilder();
    assertThat(Gates.gateEval(Gates.risc(2L), "10000000000000001", buf))
        .isEqualTo(Oracle.inc3bReturn("risc2_eval"));
    assertThat(buf + "\n").isEqualTo(Oracle.inc3b("risc2_eval"));
    long[] rom = {
      0x2ff0, 0x1111, 0x1a30, 0x3333, 0x7f70, 0x5555, 0x0f8f, 0x3a21, 0x1a01, 0x0a12, 0x3a01,
      0x4000, 0x5000, 0x6000, 0x2a63, 0x0f95, 0x3063, 0x1061, 0x6ac1, 0x5fd1, 0x2a63, 0x039b,
      0x0843, 0x3463, 0x1561, 0x2863, 0x0c94, 0x4861, 0x6ac1, 0x2a63, 0x5a41, 0x0398, 0x6666,
      0x0fa7
    };
    rom[1] = 3;
    rom[3] = 4;
    rom[5] = 10;
    Graph g = Gates.risc(8L);
    assertThat(
            captureGates(
                ps -> {
                  ps.print("return=" + Gates.runRisc(g, rom, 34L, 8L) + "\n");
                  for (long s : Gates.riscState) {
                    ps.print(s + " ");
                  }
                  ps.print("\n");
                }))
        .isEqualTo(Oracle.inc3b("run_risc_mult"));
    rom[5] = 7;
    assertThat(
            captureGates(
                ps -> {
                  ps.print("return=" + Gates.runRisc(g, rom, 34L, 0L) + "\n");
                  for (long s : Gates.riscState) {
                    ps.print(s + " ");
                  }
                  ps.print("\n");
                }))
        .isEqualTo(Oracle.inc3b("run_risc_div"));
    assertThat(captureGates(ps -> Gates.printGates(Gates.risc(2L))))
        .isEqualTo(Oracle.inc3b("risc2_gates"));
  }

  /** Like {@link Oracle#capture} but also routes {@link Gates#out} to the captured stream. */
  private static String captureGates(Consumer<PrintStream> printer) {
    PrintStream saved = Gates.out;
    try {
      return Oracle.capture(
          ps -> {
            Gates.out = ps;
            printer.accept(ps);
          });
    } finally {
      Gates.out = saved;
    }
  }
}
