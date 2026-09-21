package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit coverage for {@link TakeRisc} pieces not exercised enough by a single golden case. */
class TakeRiscTest {

  @Test
  @DisplayName("reprompts with the positive-number dialogue when the first number is zero")
  void shouldPrintUsageLikeDialogueWhenNumberIsZero() {
    CStdin in =
        new CStdin(new ByteArrayInputStream("0\n5\n".getBytes(StandardCharsets.ISO_8859_1)));
    ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(outBytes, true, StandardCharsets.ISO_8859_1);
    PrintStream err =
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.ISO_8859_1);

    int code = TakeRisc.run(new String[0], in, out, err, Path.of("."));

    assertThat(code).isZero();
    assertThat(outBytes.toString(StandardCharsets.ISO_8859_1))
        .isEqualTo(
            "Welcome to the world of microRISC.\n"
                + "\n"
                + "Gimme a number: "
                + "Excuse me, I meant a positive number: "
                + "OK, now gimme another: ");
  }
}
