package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Vertex;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit coverage for {@link Ladders} pieces not exercised enough by a single golden case. */
class LaddersTest {

  @Test
  @DisplayName("freqCost costs fewer mems for a more common (higher-weight) word")
  void shouldCostFewerWhenWordIsCommon() {
    assertThat(Ladders.freqCost(weightedVertex(0))).isEqualTo(16);
    assertThat(Ladders.freqCost(weightedVertex(1))).isEqualTo(15);
    assertThat(Ladders.freqCost(weightedVertex(65535))).isEqualTo(0);
    assertThat(Ladders.freqCost(weightedVertex(1L << 20))).isEqualTo(0);
  }

  @Test
  @DisplayName("alphDist and hammDist measure letter distance between two words")
  void shouldMeasureLetterDistanceWhenWordsDiffer() {
    assertThat(Ladders.alphDist("aaaaa", "abcaa")).isEqualTo(3);
    assertThat(Ladders.hammDist("aaaaa", "abcaa")).isEqualTo(2);
  }

  @Test
  @DisplayName("promptForFive retries on an invalid word and succeeds on a valid one")
  void shouldPromptAgainWhenWordIsNotFiveLowercaseLetters() {
    CStdin in =
        new CStdin(
            new ByteArrayInputStream("WORLD\nworld\n".getBytes(StandardCharsets.ISO_8859_1)));
    ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(outBytes, true, StandardCharsets.ISO_8859_1);

    Ladders.PromptResult result = Ladders.promptForFive("    Goal", in, out, false);

    assertThat(outBytes.toString(StandardCharsets.ISO_8859_1))
        .isEqualTo(
            "    Goal word: (Please type five lowercase letters and RETURN.)\n    Goal word: ");
    assertThat(result.status()).isEqualTo(0);
    assertThat(result.word()).isEqualTo("world");
  }

  private static Vertex weightedVertex(long weight) {
    Vertex v = Gb.allocAuxVertices(1)[0];
    v.u.I = weight;
    return v;
  }
}
