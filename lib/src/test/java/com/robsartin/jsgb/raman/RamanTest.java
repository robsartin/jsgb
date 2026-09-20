package com.robsartin.jsgb.raman;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RamanTest {

  @Test
  @DisplayName(
      "raman(31,3,0,4) chooses type 3 and builds the 12-vertex 8-regular graph of sample.correct")
  void shouldBuildTwelveVertexGraphWhenSampleParametersGiven() {
    Graph g = Raman.raman(31L, 3L, 0L, 4L);
    assertThat(g).isNotNull();
    assertThat(g.id).isEqualTo("raman(31,3,3,4)");
    assertThat(g.n).isEqualTo(12L);
    assertThat(g.m).isEqualTo(96L);
    assertThat(g.utilTypes).isEqualTo("ZZZIIIIZZZZZZZ");
    assertThat(g.vertices[4].name).isEqualTo("(1,0;1,1)");
    assertThat(g.vertices[4].arcs.tip.name).isEqualTo("(1,2;1,0)");
    assertThat(g.vertices[4].arcs.a.I).isEqualTo(16L);
  }

  @Test
  @DisplayName("type 1 uses only slot x and names the projective point INF")
  void shouldNameInfinityWhenTypeOne() {
    Graph g = Raman.raman(5L, 3L, 1L, 0L);
    assertThat(g.utilTypes).isEqualTo("ZZZIZZIZZZZZZZ");
    assertThat(g.n).isEqualTo(4L);
    assertThat(g.vertices[3].name).isEqualTo("INF");
    assertThat(g.vertices[3].x.I).isEqualTo(3L);
  }

  @Test
  @DisplayName("bad specs panic with the C's codes and return null")
  void shouldPanicWhenSpecsAreBad() {
    assertThat(Raman.raman(31L, 2L, 0L, 0L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.VERY_BAD_SPECS);
    assertThat(Raman.raman(1L, 3L, 0L, 0L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.VERY_BAD_SPECS + 1);
    assertThat(Raman.raman(6L, 3L, 0L, 0L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS + 3);
    assertThat(Raman.raman(31L, 3L, 4L, 0L)).isNull(); // 31 is a residue mod 3: type must be 3
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS + 4);
    assertThat(Gb.troubleCode).isZero();
  }
}
