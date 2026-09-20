package com.robsartin.jsgb.lisa;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LisaTest {

  @Test
  @DisplayName("lisa(4,4,255,…) reduces the whole painting to a 4x4 grey matrix and records its id")
  void shouldReduceWholePaintingWhenFourByFour() {
    long[] a = Lisa.lisa(4L, 4L, 255L, 0L, 0L, 0L, 0L, 0L, 0L);
    assertThat(Lisa.lisaId).isEqualTo("lisa(4,4,255,0,360,0,250,0,22950000)");
    assertThat(a).containsExactly(106, 110, 65, 97, 50, 66, 48, 47, 26, 30, 19, 12, 17, 34, 17, 11);
  }

  @Test
  @DisplayName("lisa with a window and explicit thresholds maps to 0..7")
  void shouldMapToRangeWhenWindowGiven() {
    long[] a = Lisa.lisa(3L, 5L, 7L, 100L, 110L, 100L, 110L, 1000L, 60000L);
    assertThat(Lisa.lisaId).isEqualTo("lisa(3,5,7,100,110,100,110,1000,60000)");
    assertThat(a).containsExactly(0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0);
  }

  @Test
  @DisplayName("plane_lisa(100,100,50,1,300,1,200,2975050,11900200) is stanza 9")
  void shouldMatchStanzaWhenSampleParametersGiven() {
    Graph g =
        Lisa.planeLisa(100L, 100L, 50L, 1L, 300L, 1L, 200L, 50L * 299L * 199L, 200L * 299L * 199L);
    assertThat(g.id).isEqualTo("plane_lisa(100,100,50,1,300,1,200,2975050,11900200)");
    assertThat(g.n).isEqualTo(2452L);
    assertThat(g.m).isEqualTo(10814L);
    assertThat(g.utilTypes).isEqualTo("ZZZIIIZZIIZZZZ");
    assertThat(g.uu.I).isEqualTo(100L);
    assertThat(g.vv.I).isEqualTo(100L);
    assertThat(g.vertices[1294].name).isEqualTo("1294");
    assertThat(g.vertices[1294].x.I).isEqualTo(11L);
    assertThat(g.vertices[1294].y.I).isEqualTo(2407L);
    assertThat(g.vertices[1294].z.I).isEqualTo(2408L);
    assertThat(g.vertices[1294].arcs.tip.name).isEqualTo("1295");
  }

  @Test
  @DisplayName(
      "bi_lisa joins rows to columns whose pixel passes the threshold and stores the pixel on both arcs")
  void shouldJoinRowsToColumnsWhenBipartite() {
    Graph g = Lisa.biLisa(10L, 10L, 100L, 110L, 100L, 110L, 20000L, 1L);
    assertThat(g.id).isEqualTo("bi_lisa(10,10,100,110,100,110,20000,1)");
    assertThat(g.n).isEqualTo(20L);
    assertThat(g.m).isEqualTo(54L);
    assertThat(g.utilTypes).isEqualTo("ZZZZZZZIIZZZZZ");
    assertThat(g.uu.I).isEqualTo(10L);
    assertThat(g.vertices[12].name).isEqualTo("c2");
    assertThat(g.vertices[12].arcs.tip.name).isEqualTo("r4");
    assertThat(g.vertices[12].arcs.b.I).isEqualTo(19275L);
    assertThat(g.vertices[12].arcs.mate.b.I).isEqualTo(19275L);
  }

  @Test
  @DisplayName("an empty row window panics with bad_specs+1 and leaves the id alone")
  void shouldPanicWhenWindowEmpty() {
    Lisa.lisa(4L, 4L, 255L, 0L, 0L, 0L, 0L, 0L, 0L);
    assertThat(Lisa.planeLisa(5L, 5L, 0L, 10L, 10L, 0L, 0L, 0L, 0L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS + 1);
    assertThat(Lisa.lisaId).isEqualTo("lisa(4,4,255,0,360,0,250,0,22950000)");
  }
}
