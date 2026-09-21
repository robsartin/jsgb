package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemsTest {

  @BeforeEach
  void resetMems() {
    Mems.mems = 0;
  }

  @Test
  @DisplayName("o, oo, ooo and oooo each charge their own number of memory references")
  void shouldChargeReferencesWhenCountersCalled() {
    Mems.o();
    assertThat(Mems.mems).isEqualTo(1L);
    Mems.oo();
    assertThat(Mems.mems).isEqualTo(3L);
    Mems.ooo();
    assertThat(Mems.mems).isEqualTo(6L);
    Mems.oooo();
    assertThat(Mems.mems).isEqualTo(10L);
  }
}
