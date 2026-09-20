package com.robsartin.jsgb.games;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GamesTest {

  @Test
  @DisplayName("games(60,70,80,-90,-101,60,0,999999999) is stanza 7: Maryland at vertex 14")
  void shouldMatchStanzaWhenSampleParametersGiven() {
    Graph g = Games.games(60L, 70L, 80L, -90L, -101L, 60L, 0L, 999999999L);
    assertThat(g.id).isEqualTo("games(60,70,80,-90,-101,60,128,999999999)");
    assertThat(g.n).isEqualTo(60L);
    assertThat(g.m).isEqualTo(114L);
    assertThat(g.utilTypes).isEqualTo("IIZSSSIIZZZZZZ");
    Vertex md = g.vertices[14];
    assertThat(md.name).isEqualTo("Maryland");
    assertThat(md.u.I).isEqualTo(2752512L);
    assertThat(md.v.I).isEqualTo(131072L);
    assertThat(md.x.S()).isEqualTo("MD");
    assertThat(md.y.S()).isEqualTo("Terps");
    assertThat(md.z.S()).isEqualTo("Atlantic Coast");
    Arc a = md.arcs;
    assertThat(a.tip.name).isEqualTo("Louisiana Tech");
    assertThat(a.tip.z.ref).isNull();
    assertThat(a.len).isEqualTo(34L);
    assertThat(a.a.I).isEqualTo(2L);
    assertThat(a.b.I).isEqualTo(111L);
    assertThat(a.next.tip.name).isEqualTo("Virginia");
    assertThat(a.next.a.I).isEqualTo(1L);
    assertThat(a.next.b.I).isEqualTo(83L);
  }

  @Test
  @DisplayName("home and away arcs of one game are consecutive, mirrored, and dated")
  void shouldPairArcsWhenGameRecorded() {
    Graph g = Games.games(120L, 0L, 0L, 0L, 0L, 0L, 0L, 1L);
    assertThat(g.id).isEqualTo("games(120,0,0,0,0,0,128,1)");
    Vertex u = g.vertices[0];
    Arc a = u.arcs;
    Vertex v = a.tip;
    Arc back = v.arcs;
    while (back.tip != u) {
      back = back.next;
    }
    assertThat(a.a.I + back.a.I).isEqualTo(4L);
    assertThat(back.b.I).isEqualTo(a.b.I);
    assertThat(g.vertices[0].z.S()).isEqualTo("Patriot"); // games_full oracle, V0 Lafayette
  }

  @Test
  @DisplayName("a weight beyond 131072 panics with bad_specs")
  void shouldPanicWhenWeightTooLarge() {
    assertThat(Games.games(5L, 200000L, 0L, 0L, 0L, 0L, 0L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS);
  }
}
