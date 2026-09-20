package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.books.Books;
import com.robsartin.jsgb.econ.Econ;
import com.robsartin.jsgb.graph.Graph;
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
}
