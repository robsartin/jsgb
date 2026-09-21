package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EconOrderTest {

  @Test
  @DisplayName("prints usage and returns -2 when an argument is unrecognised")
  void shouldPrintUsageWhenArgumentUnknown() {
    ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    PrintStream err = new PrintStream(errBytes, true, StandardCharsets.ISO_8859_1);
    PrintStream out =
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.ISO_8859_1);
    CStdin in = new CStdin(InputStream.nullInputStream());

    int code = EconOrder.run(new String[] {"-q"}, in, out, err, Path.of("."));

    assertThat(code).isEqualTo(-2);
    assertThat(errBytes.toString(StandardCharsets.ISO_8859_1))
        .isEqualTo("Usage: econ_order [-nN][-rN][-sN][-tN][-g][-v][-V]\n");
  }
}
