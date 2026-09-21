package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FootballTest {

  @Test
  @DisplayName("formatDate renders a game's day-of-season as a month and two-digit day")
  void shouldFormatDateWhenDayGiven() {
    assertThat(Football.formatDate(0L)).isEqualTo("Aug 26");
    assertThat(Football.formatDate(5L)).isEqualTo("Aug 31");
    assertThat(Football.formatDate(6L)).isEqualTo("Sep 01");
    assertThat(Football.formatDate(96L)).isEqualTo("Nov 30");
    assertThat(Football.formatDate(127L)).isEqualTo("Dec 31");
    assertThat(Football.formatDate(128L)).isEqualTo("Jan 01");
  }
}
