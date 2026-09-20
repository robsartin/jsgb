package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.raman.Raman;
import com.robsartin.jsgb.rand.Rand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Every case here reproduces a `print_sample` run of the C library recorded in oracle_inc2.out. */
class OracleInc2Test {

  private static final long[] DST = {0x20000000L, 0x10000000L, 0x10000000L};

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
}
