package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit coverage for {@link Girth} pieces not exercised enough by a single golden case. */
class GirthTest {

  @Test
  @DisplayName("diameterGirthBounds and girthLowerBound match the session capture for p=3, q=7")
  void shouldReportBoundsWhenGraphIsBipartite() {
    long[] dlGu = Girth.diameterGirthBounds(3L, 7L, 336L);

    assertThat(dlGu).containsExactly(5L, 10L);
    assertThat(Girth.girthLowerBound(3L, 7L, true)).isEqualTo(8L);
  }
}
