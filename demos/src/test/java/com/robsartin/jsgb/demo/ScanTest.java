package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScanTest {

  @Test
  @DisplayName("scan reads the value after its prefix")
  void shouldReadValueWhenPrefixMatches() {
    assertThat(Scan.scan("-n50", "-n")).isEqualTo(50L);
  }

  @Test
  @DisplayName("scan returns null when no digits follow the prefix")
  void shouldReturnNullWhenNoDigitsFollowPrefix() {
    assertThat(Scan.scan("-n", "-n")).isNull();
  }

  @Test
  @DisplayName("scan ignores trailing non-digit text after the digit run")
  void shouldIgnoreTrailingTextWhenDigitsStop() {
    assertThat(Scan.scan("-n5x", "-n")).isEqualTo(5L);
  }

  @Test
  @DisplayName("scan reads a negative value")
  void shouldReadNegativeValueWhenSignIsMinus() {
    assertThat(Scan.scan("-n-5", "-n")).isEqualTo(-5L);
  }

  @Test
  @DisplayName("scan returns null when the literal prefix does not match")
  void shouldReturnNullWhenPrefixDoesNotMatch() {
    assertThat(Scan.scan("-x5", "-n")).isNull();
  }

  @Test
  @DisplayName("scan skips leading whitespace with an empty prefix")
  void shouldSkipWhitespaceWhenPrefixEmpty() {
    assertThat(Scan.scan("  42", "")).isEqualTo(42L);
  }

  @Test
  @DisplayName("scan returns null when there are no digits at all")
  void shouldReturnNullWhenArgHasNoDigits() {
    assertThat(Scan.scan("abc", "")).isNull();
  }

  @Test
  @DisplayName("scan rejects non-ASCII decimal digits that C's %ld would not accept")
  void shouldReturnNullWhenDigitIsNonAscii() {
    assertThat(Scan.scan("-n٥", "-n")).isNull();
  }
}
