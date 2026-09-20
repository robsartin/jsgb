package com.robsartin.jsgb.miles;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MilesTest {

  @Test
  @DisplayName("miles(50,-500,100,1,500,5,314159) is stanza 8 with Saint Louis at vertex 20")
  void shouldMatchStanzaWhenSampleParametersGiven() {
    Graph g = Miles.miles(50L, -500L, 100L, 1L, 500L, 5L, 314159L);
    assertThat(g.id).isEqualTo("miles(50,-500,100,1,500,5,314159)");
    assertThat(g.n).isEqualTo(50L);
    assertThat(g.m).isEqualTo(164L);
    assertThat(g.utilTypes).isEqualTo("ZZIIIIZZZZZZZZ");
    assertThat(g.vertices[20].name).isEqualTo("Saint Louis, MO");
    assertThat(g.vertices[20].w.I).isEqualTo(453085L);
    assertThat(g.vertices[20].x.I).isEqualTo(3293L);
    assertThat(g.vertices[20].y.I).isEqualTo(1785L);
    assertThat(g.vertices[20].z.I).isEqualTo(24L);
    assertThat(g.vertices[20].arcs.tip.name).isEqualTo("Tupelo, MS");
    assertThat(g.vertices[20].arcs.len).isEqualTo(364L);
  }

  @Test
  @DisplayName("miles_distance reads the retained matrix and the id prints the adjusted max_degree")
  void shouldReportDistanceWhenGraphBuilt() {
    Graph g = Miles.miles(10L, 0L, 0L, 0L, 0L, 0L, 4L);
    assertThat(g.id).isEqualTo("miles(10,0,0,0,0,9,4)");
    assertThat(Miles.milesDistance(g.vertices[0], g.vertices[1])).isEqualTo(520L);
    assertThat(g.m).isEqualTo(90L);
  }

  @Test
  @DisplayName("out-of-range weights panic with bad_specs")
  void shouldPanicWhenWeightsOutOfRange() {
    assertThat(Miles.miles(10L, 200000L, 0L, 0L, 0L, 0L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS);
    assertThat(Miles.miles(10L, 0L, 0L, 101L, 0L, 0L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS);
  }
}
