package com.robsartin.jsgb.econ;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EconTest {

  @Test
  @DisplayName("econ(40,0,400,-111) is stanza 6: printing and publishing at vertex 11")
  void shouldMatchStanzaWhenSampleParametersGiven() {
    Graph g = Econ.econ(40L, 0L, 400L, -111L);
    assertThat(g.id).isEqualTo("econ(40,0,400,-111)");
    assertThat(g.n).isEqualTo(40L);
    assertThat(g.m).isEqualTo(512L);
    assertThat(g.utilTypes).isEqualTo("ZZZZIAIZZZZZZZ");
    assertThat(g.vertices[11].name).isEqualTo("Printing and publishing");
    assertThat(g.vertices[11].y.I).isEqualTo(69451L);
    Arc sic = g.vertices[11].z.A();
    assertThat(sic.tip).isNull();
    assertThat(g.vertices[11].arcs.tip.name).isEqualTo("Food, liquor, and candy");
    assertThat(g.vertices[11].arcs.a.I).isEqualTo(1863L);
    assertThat(g.vertices[39].name).isEqualTo("Users");
    assertThat(g.vertices[39].y.I).isEqualTo(3999362L);
  }

  @Test
  @DisplayName("the full 81-sector table needs no merging and every sector keeps its own SIC arc")
  void shouldKeepAllSectorsWhenNIsMaximal() {
    Graph g = Econ.econ(81L, 0L, 0L, 1L);
    assertThat(g.id).isEqualTo("econ(81,0,0,1)");
    assertThat(g.n).isEqualTo(81L);
    assertThat(g.vertices[0].name).isEqualTo("Livestock and livestock products");
    assertThat(g.vertices[0].z.A().len).isEqualTo(1L);
    assertThat(g.vertices[0].z.A().next).isNull();
    assertThat(g.vertices[80].name).isEqualTo("Users");
  }

  @Test
  @DisplayName("omit=2 drops Adjustments and Users and clamps the request")
  void shouldClampWhenOmitGiven() {
    Graph g = Econ.econ(40L, 2L, 1000L, 5L);
    assertThat(g.id).isEqualTo("econ(40,2,1000,5)");
    assertThat(g.n).isEqualTo(40L);
    for (int i = 0; i < 40; i++) {
      assertThat(g.vertices[i].name).isNotIn("Adjustments", "Users");
    }
    assertThat(Econ.econ(0L, 0L, 0L, 0L).id).isEqualTo("econ(81,0,0,0)");
    assertThat(Econ.econ(2L, 5L, 0L, 0L).id).isEqualTo("econ(2,2,0,0)");
  }
}
