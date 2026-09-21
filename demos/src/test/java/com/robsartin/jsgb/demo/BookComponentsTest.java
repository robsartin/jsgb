package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Vertex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BookComponentsTest {

  @Test
  @DisplayName("vertexName encodes the short code as two imap characters when not restored")
  void shouldEncodeShortCodeWhenNotRestored() {
    Vertex v = Gb.allocAuxVertices(1)[0];
    v.u.I = 37;

    assertThat(BookComponents.vertexName(v, null)).isEqualTo("11");
  }
}
