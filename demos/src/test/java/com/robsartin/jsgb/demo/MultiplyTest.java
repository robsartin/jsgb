package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit coverage for {@link Multiply} pieces not exercised enough by a single golden case. */
class MultiplyTest {

  @Test
  @DisplayName("decimalToBinary emits the low n bits, MSB first, and flags leftover digits")
  void shouldConvertDecimalWhenBitsRequested() {
    Multiply.DecimalToBinary fourBits = Multiply.decimalToBinary("13", 4);
    assertThat(fourBits.bits()).isEqualTo("1101");
    assertThat(fourBits.overflow()).isFalse();

    Multiply.DecimalToBinary threeBits = Multiply.decimalToBinary("13", 3);
    assertThat(threeBits.overflow()).isTrue();
  }

  @Test
  @DisplayName("binaryToDecimal converts an MSB-first bit string back to a decimal digit string")
  void shouldConvertBitsWhenDecimalRequested() {
    assertThat(Multiply.binaryToDecimal("1101")).isEqualTo("13");
  }
}
