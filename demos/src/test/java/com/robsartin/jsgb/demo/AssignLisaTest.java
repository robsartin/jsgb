package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssignLisaTest {

  @Test
  @DisplayName("formats an EPS pixel as the C's float conv*value, clamped and hex-encoded")
  void shouldFormatEpsPixelWhenConversionUsesFloat() {
    // conv = 255.0f/255.0f = 1.0f; x = (long) (1.0f * 128.0f) = 128
    assertThat(AssignLisa.formatEpsPixel(255, 128)).isEqualTo("80");
    // conv = (float) (255.0/100.0) = 2.55f; x = (long) (2.55f * 77.0f) = 196
    assertThat(AssignLisa.formatEpsPixel(100, 77)).isEqualTo("c4");
  }

  @Test
  @DisplayName("prints usage and returns -2 when an argument is unrecognised")
  void shouldPrintUsageWhenArgumentUnknown() {
    ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    PrintStream err = new PrintStream(errBytes, true, StandardCharsets.ISO_8859_1);
    PrintStream out =
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.ISO_8859_1);
    CStdin in = new CStdin(InputStream.nullInputStream());

    int code = AssignLisa.run(new String[] {"-q"}, in, out, err, Path.of("."));

    assertThat(code).isEqualTo(-2);
    assertThat(errBytes.toString(StandardCharsets.ISO_8859_1))
        .isEqualTo("Usage: assign_lisa [param=value] [-s] [-c] [-h] [-v] [-p] [-P]\n");
  }
}
