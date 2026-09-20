package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.raman.Raman;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Every case here reproduces a `print_sample` run of the C library recorded in oracle_inc2.out. */
class OracleInc2Test {

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
}
