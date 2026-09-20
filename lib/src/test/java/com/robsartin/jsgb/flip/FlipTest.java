package com.robsartin.jsgb.flip;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlipTest {

  @BeforeEach
  void resetGenerator() {
    Flip.reset();
  }

  @Test
  @DisplayName("seeding with -314159 produces Knuth's published intermediate values before warm-up")
  void shouldMatchPublishedCheckpointsWhenSeededWithMinus314159() {
    Flip.seedArray(-314159L);
    assertThat(Flip.a(42)).isEqualTo(2147326568L);
    assertThat(Flip.a(8)).isEqualTo(1073977445L);
    assertThat(Flip.a(29)).isEqualTo(536517481L);
  }

  @Test
  @DisplayName("test_flip: first value after init_rand(-314159) is 119318998")
  void shouldReturn119318998WhenFirstDrawAfterSeedingWithMinus314159() {
    Flip.initRand(-314159L);
    assertThat(Flip.nextRand()).isEqualTo(119318998L);
  }

  @Test
  @DisplayName("test_flip: unif_rand(0x55555555) after 134 draws is 748103812")
  void shouldReturn748103812WhenUnifRandCalledAfter134Draws() {
    Flip.initRand(-314159L);
    for (int j = 0; j < 134; j++) {
      Flip.nextRand();
    }
    assertThat(Flip.unifRand(0x55555555L)).isEqualTo(748103812L);
  }

  @Test
  @DisplayName("first five draws after init_rand(-314159) match the C library")
  void shouldMatchCSequenceWhenDrawingFiveValues() {
    Flip.initRand(-314159L);
    long[] actual = new long[5];
    for (int i = 0; i < 5; i++) {
      actual[i] = Flip.nextRand();
    }
    assertThat(actual).containsExactly(119318998L, 1301097714L, 451151173L, 51016514L, 374261376L);
  }

  @Test
  @DisplayName("seed 0 is a valid seed and matches the C library")
  void shouldMatchCSequenceWhenSeedIsZero() {
    Flip.initRand(0L);
    assertThat(Flip.nextRand()).isEqualTo(2029883356L);
    assertThat(Flip.nextRand()).isEqualTo(2073281797L);
    assertThat(Flip.nextRand()).isEqualTo(759676350L);
    assertThat(Flip.unifRand(1000L)).isEqualTo(240L);
  }

  @Test
  @DisplayName("unif_rand never returns a value outside [0, m)")
  void shouldStayInRangeWhenUnifRandCalledRepeatedly() {
    Flip.initRand(42L);
    for (int i = 0; i < 10_000; i++) {
      long r = Flip.unifRand(7L);
      assertThat(r).isBetween(0L, 6L);
    }
  }
}
