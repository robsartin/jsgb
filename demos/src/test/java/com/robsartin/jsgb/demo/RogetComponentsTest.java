package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link RogetComponents} pieces not exercised enough by a single golden case.
 */
class RogetComponentsTest {

  @Test
  void shouldPrintUsageWhenArgumentUnknown() {
    ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    PrintStream out =
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.ISO_8859_1);
    PrintStream err = new PrintStream(errBytes, true, StandardCharsets.ISO_8859_1);
    CStdin in = new CStdin(new ByteArrayInputStream(new byte[0]));

    int code = RogetComponents.run(new String[] {"-q"}, in, out, err, Path.of("."));

    assertThat(code).isEqualTo(-2);
    assertThat(errBytes.toString(StandardCharsets.ISO_8859_1))
        .isEqualTo("Usage: roget_components [-nN][-dN][-pN][-sN][-gfoo]\n");
  }
}
