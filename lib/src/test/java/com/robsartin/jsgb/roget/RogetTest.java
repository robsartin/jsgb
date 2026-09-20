package com.robsartin.jsgb.roget;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RogetTest {

  @Test
  @DisplayName("roget(1022,0,0,0) is the full thesaurus graph with 5075 cross-references")
  void shouldBuildFullGraphWhenDefaultsUsed() {
    Graph g = Roget.roget(0L, 0L, 0L, 0L);
    assertThat(g.id).isEqualTo("roget(1022,0,0,0)");
    assertThat(g.n).isEqualTo(1022L);
    assertThat(g.m).isEqualTo(5075L);
    assertThat(g.utilTypes).isEqualTo("IZZZZZZZZZZZZZ");
    assertThat(g.vertices[1000].name).isEqualTo("non-design");
    assertThat(g.vertices[1000].u.I).isEqualTo(636L);
    assertThat(g.vertices[1000].arcs.tip.name).isEqualTo("intention");
  }

  @Test
  @DisplayName("roget(1000,3,1009,1009) is stanza 12: thought at vertex 40 with five arcs")
  void shouldMatchStanzaWhenSampleParametersGiven() {
    Graph g = Roget.roget(1000L, 3L, 1009L, 1009L);
    assertThat(g.n).isEqualTo(1000L);
    assertThat(g.m).isEqualTo(3573L);
    assertThat(g.vertices[40].name).isEqualTo("thought");
    assertThat(g.vertices[40].u.I).isEqualTo(461L);
    assertThat(g.vertices[40].arcs.tip.name).isEqualTo("imagination");
  }
}
