package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.basic.Basic;
import com.robsartin.jsgb.raman.Raman;
import com.robsartin.jsgb.rand.Rand;
import com.robsartin.jsgb.save.Save;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Every case here reproduces a `print_sample` run of the C library recorded in oracle_inc2.out. */
class OracleInc2Test {

  private static final long[] DST = {0x20000000L, 0x10000000L, 0x10000000L};

  @TempDir java.nio.file.Path dir;

  @Test
  @DisplayName("raman types 1, 2 and reduced 3 print exactly as the C")
  void shouldMatchOracleWhenRamanPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Raman.raman(5L, 3L, 1L, 0L), 1, ps)))
        .isEqualTo(Oracle.inc2("raman1"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Raman.raman(5L, 3L, 2L, 1L), 2, ps)))
        .isEqualTo(Oracle.inc2("raman2"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Raman.raman(31L, 3L, 3L, 1L), 4, ps)))
        .isEqualTo(Oracle.inc2("raman3red"));
  }

  @Test
  @DisplayName("random_graph and random_bigraph print exactly as the C")
  void shouldMatchOracleWhenRandomGraphsPrinted() {
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Rand.randomGraph(4L, 7L, 1L, 1L, 1L, null, null, 5L, 9L, 3L), 2, ps)))
        .isEqualTo(Oracle.inc2("random_dir"));
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Rand.randomGraph(3L, 3L, 0L, 0L, 0L, DST, null, 1L, 1L, 7L), 0, ps)))
        .isEqualTo(Oracle.inc2("random_dist"));
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Rand.randomGraph(3L, 8L, -1L, 1L, 0L, null, null, 1L, 5L, 11L), 1, ps)))
        .isEqualTo(Oracle.inc2("random_multi_neg"));
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Rand.randomBigraph(2L, 3L, 5L, 0L, null, null, 1L, 3L, 5L), 3, ps)))
        .isEqualTo(Oracle.inc2("random_bigraph"));
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Rand.randomGraph(0L, 1L, 0L, 0L, 0L, null, null, 1L, 1L, 1L), 0, ps)))
        .isEqualTo(Oracle.inc2("random_bad"));
  }

  @Test
  @DisplayName("board, simplex and subsets print exactly as the C")
  void shouldMatchOracleWhenGridGraphsPrinted() {
    assertThat(
            Oracle.capture(
                ps -> TestSample.printSample(Basic.board(3L, 3L, 0L, 0L, -2L, 0L, 1L), 4, ps)))
        .isEqualTo(Oracle.inc2("board_dir"));
    assertThat(
            Oracle.capture(
                ps -> TestSample.printSample(Basic.board(4L, 4L, 0L, 0L, 1L, 3L, 0L), 0, ps)))
        .isEqualTo(Oracle.inc2("board_wrap"));
    assertThat(
            Oracle.capture(
                ps -> TestSample.printSample(Basic.simplex(4L, 2L, 0L, 0L, 0L, 0L, 0L), 3, ps)))
        .isEqualTo(Oracle.inc2("simplex"));
    assertThat(
            Oracle.capture(
                ps -> TestSample.printSample(Basic.simplex(3L, -3L, 0L, 0L, 0L, 0L, 1L), 5, ps)))
        .isEqualTo(Oracle.inc2("simplex_dir"));
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(Basic.subsets(3L, -4L, 0L, 0L, 0L, 0L, 3L, 0L), 2, ps)))
        .isEqualTo(Oracle.inc2("subsets2"));
  }

  @Test
  @DisplayName("random_lengths on board graphs prints exactly as the C, with the C's return codes")
  void shouldMatchOracleWhenRandomLengthsApplied() {
    com.robsartin.jsgb.graph.Graph g = Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 0L);
    assertThat(Rand.randomLengths(g, 0L, -3L, 3L, null, 9L))
        .isEqualTo(Oracle.inc2Return("random_lengths"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(g, 1, ps)))
        .isEqualTo(Oracle.inc2("random_lengths"));
    com.robsartin.jsgb.graph.Graph d = Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 1L);
    assertThat(Rand.randomLengths(d, 1L, 10L, 12L, DST, 4L))
        .isEqualTo(Oracle.inc2Return("random_lengths_dir"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(d, 0, ps)))
        .isEqualTo(Oracle.inc2("random_lengths_dir"));
    assertThat(Rand.randomLengths(null, 0L, 0L, 0L, null, 0L))
        .isEqualTo(Oracle.inc2Return("random_lengths_null"));
  }

  @Test
  @DisplayName("perms, parts and binary print exactly as the C, including the binary panic")
  void shouldMatchOracleWhenCombinatorialGraphsPrinted() {
    assertThat(
            Oracle.capture(
                ps -> TestSample.printSample(Basic.perms(1L, 1L, 1L, 0L, 0L, 0L, 0L), 3, ps)))
        .isEqualTo(Oracle.inc2("perms"));
    assertThat(
            Oracle.capture(
                ps -> TestSample.printSample(Basic.perms(2L, 1L, 0L, 0L, 0L, 2L, 1L), 2, ps)))
        .isEqualTo(Oracle.inc2("perms_dir"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.parts(6L, 0L, 0L, 0L), 4, ps)))
        .isEqualTo(Oracle.inc2("parts"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.parts(7L, 3L, 4L, 1L), 2, ps)))
        .isEqualTo(Oracle.inc2("parts_dir"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.binary(4L, 0L, 0L), 5, ps)))
        .isEqualTo(Oracle.inc2("binary"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.binary(5L, 4L, 1L), 3, ps)))
        .isEqualTo(Oracle.inc2("binary_dir"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.binary(20L, 6L, 0L), 100, ps)))
        .isEqualTo(Oracle.inc2("binary_big"));
  }

  @Test
  @DisplayName("complement, gunion and intersection print exactly as the C")
  void shouldMatchOracleWhenSetOperationsPrinted() {
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Basic.complement(Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 1L), 0L, 0L, 1L),
                        1,
                        ps)))
        .isEqualTo(Oracle.inc2("complement_dir"));
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Basic.gunion(
                            Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 1L),
                            Basic.board(3L, 0L, 0L, 0L, -2L, 0L, 1L),
                            0L,
                            1L),
                        0,
                        ps)))
        .isEqualTo(Oracle.inc2("gunion_dir"));
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Basic.intersection(
                            Basic.board(3L, 3L, 0L, 0L, -1L, 0L, 0L),
                            Basic.board(3L, 3L, 0L, 0L, -2L, 0L, 0L),
                            0L,
                            0L),
                        4,
                        ps)))
        .isEqualTo(Oracle.inc2("intersection"));
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Basic.intersection(
                            Basic.board(3L, 0L, 0L, 0L, -2L, 0L, 1L),
                            Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 1L),
                            1L,
                            1L),
                        1,
                        ps)))
        .isEqualTo(Oracle.inc2("intersection_dir"));
  }

  @Test
  @DisplayName("lines and product print exactly as the C")
  void shouldMatchOracleWhenLinesAndProductsPrinted() {
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Basic.lines(Basic.board(3L, 3L, 0L, 0L, 1L, 0L, 0L), 0L), 5, ps)))
        .isEqualTo(Oracle.inc2("lines"));
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Basic.lines(Basic.board(3L, 0L, 0L, 0L, -2L, 0L, 1L), 1L), 1, ps)))
        .isEqualTo(Oracle.inc2("lines_dir"));
    for (String[] c :
        new String[][] {{"product_cart", "0"}, {"product_direct", "1"}, {"product_strong", "2"}}) {
      long type = Long.parseLong(c[1]);
      assertThat(
              Oracle.capture(
                  ps ->
                      TestSample.printSample(
                          Basic.product(
                              Basic.board(2L, 0L, 0L, 0L, 1L, 0L, 0L),
                              Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 0L),
                              type,
                              0L),
                          1,
                          ps)))
          .as(c[0])
          .isEqualTo(Oracle.inc2(c[0]));
    }
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Basic.product(
                            Basic.board(2L, 0L, 0L, 0L, 1L, 0L, 1L),
                            Basic.board(2L, 0L, 0L, 0L, -2L, 0L, 1L),
                            1L,
                            1L),
                        0,
                        ps)))
        .isEqualTo(Oracle.inc2("product_dir"));
  }

  @Test
  @DisplayName("induced, bi_complete and wheel print exactly as the C")
  void shouldMatchOracleWhenInducedGraphsPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.biComplete(2L, 3L, 0L), 1, ps)))
        .isEqualTo(Oracle.inc2("bi_complete"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.wheel(5L, 1L, 0L), 0, ps)))
        .isEqualTo(Oracle.inc2("wheel"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.wheel(4L, 2L, 1L), 5, ps)))
        .isEqualTo(Oracle.inc2("wheel_dir"));
    com.robsartin.jsgb.graph.Graph g = Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 0L);
    g.vertices[0].z.I = 2;
    g.vertices[1].z.I = -1;
    g.vertices[2].z.I = -2;
    assertThat(
            Oracle.capture(ps -> TestSample.printSample(Basic.induced(g, "x", 1L, 1L, 0L), 0, ps)))
        .isEqualTo(Oracle.inc2("induced_neg"));
    com.robsartin.jsgb.graph.Graph h = Basic.board(2L, 0L, 0L, 0L, 1L, 0L, 0L);
    h.vertices[0].z.I = Basic.IND_GRAPH;
    h.vertices[0].y.G(Basic.board(3L, 0L, 0L, 0L, 1L, 1L, 0L));
    h.vertices[1].z.I = 1;
    assertThat(
            Oracle.capture(ps -> TestSample.printSample(Basic.induced(h, null, 0L, 0L, 0L), 1, ps)))
        .isEqualTo(Oracle.inc2("induced_subst"));
  }

  @Test
  @DisplayName("restore_graph reproduces the C's restored board and lines graphs")
  void shouldMatchOracleWhenSavedGraphsRestored() throws Exception {
    java.nio.file.Path b = dir.resolve("b.gb");
    Save.saveGraph(Basic.board(2L, 2L, 0L, 0L, 1L, 0L, 0L), b.toString());
    assertThat(Oracle.capture(ps -> TestSample.printSample(Save.restoreGraph(b.toString()), 0, ps)))
        .isEqualTo(Oracle.inc2("restore_board"));
    java.nio.file.Path l = dir.resolve("l.gb");
    com.robsartin.jsgb.graph.Graph lines = Basic.lines(Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 0L), 0L);
    Save.saveGraph(lines, l.toString());
    assertThat(Oracle.capture(ps -> TestSample.printSample(Save.restoreGraph(l.toString()), 1, ps)))
        .isEqualTo(Oracle.inc2("restore_lines"));
  }
}
