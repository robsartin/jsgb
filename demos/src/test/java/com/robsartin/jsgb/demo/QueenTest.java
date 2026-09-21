package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueenTest {

  @Test
  @DisplayName("prints the panic line instead of the listing when gunion produced no graph")
  void shouldPrintPanicMessageWhenGraphIsNull() {
    Gb.panicCode = 40;
    String output = Oracle.capture(ps -> Queen.printResult(null, ps));
    Gb.panicCode = 0;
    assertThat(output).isEqualTo("Something went wrong (panic code 40)!\n");
  }
}
