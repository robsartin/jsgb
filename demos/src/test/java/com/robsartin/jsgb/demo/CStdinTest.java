package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CStdinTest {

  private static CStdin of(String s) {
    return new CStdin(new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1)));
  }

  @Test
  @DisplayName("fgets stops at a newline and keeps it")
  void shouldStopAtNewlineWhenLineIsShort() {
    CStdin in = of("abc\ndef");
    assertThat(in.fgets(80)).isEqualTo("abc\n");
    assertThat(in.fgets(80)).isEqualTo("def");
  }

  @Test
  @DisplayName("fgets stops after size-1 bytes and getchar continues from the leftover")
  void shouldStopAtSizeLimitWhenLineIsLong() {
    CStdin in = of("abcdef\n");
    assertThat(in.fgets(4)).isEqualTo("abc");
    assertThat(in.getchar()).isEqualTo('d');
    assertThat(in.fgets(80)).isEqualTo("ef\n");
  }

  @Test
  @DisplayName("fgets returns null when EOF is hit before any byte is read")
  void shouldReturnNullWhenAtEof() {
    CStdin in = of("");
    assertThat(in.fgets(80)).isNull();
  }

  @Test
  @DisplayName("a partial last line without a newline is returned once, then fgets returns null")
  void shouldReturnPartialLineOnceWhenStreamEndsWithoutNewline() {
    CStdin in = of("last");
    assertThat(in.fgets(80)).isEqualTo("last");
    assertThat(in.fgets(80)).isNull();
  }

  @Test
  @DisplayName("getchar returns -1 at EOF")
  void shouldReturnMinusOneWhenGetcharAtEof() {
    CStdin in = of("");
    assertThat(in.getchar()).isEqualTo(-1);
  }

  @Test
  @DisplayName("bytes at or above 0x80 round-trip through ISO-8859-1")
  void shouldRoundTripHighBytesWhenPresent() {
    CStdin in = new CStdin(new ByteArrayInputStream(new byte[] {(byte) 0xE9, '\n'}));
    assertThat(in.fgets(80)).isEqualTo("é\n");
    assertThat(in.getchar()).isEqualTo(-1);
  }
}
