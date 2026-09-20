package com.robsartin.jsgb.basic;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BasicInducedTest {

  @Test
  @DisplayName("bi_complete(2,3) is K(2,3) with the first part marked")
  void shouldBuildCompleteBipartiteWhenBiCompleteCalled() {
    Graph g = Basic.biComplete(2L, 3L, 0L);
    assertThat(g.id).isEqualTo("bi_complete(2,3,0)");
    assertThat(g.n).isEqualTo(5L);
    assertThat(g.m).isEqualTo(12L);
    assertThat(g.uu.I).isEqualTo(2L);
    assertThat(g.utilTypes).isEqualTo("ZZZZZZZZIZZZZZ");
    assertThat(g.vertices[0].name).isEqualTo("0");
    assertThat(g.vertices[1].name).isEqualTo("0'");
    assertThat(g.vertices[2].name).isEqualTo("1:0");
  }

  @Test
  @DisplayName("wheel(5,1) has a hub joined to a 5-cycle")
  void shouldBuildWheelWhenWheelCalled() {
    Graph g = Basic.wheel(5L, 1L, 0L);
    assertThat(g.id).isEqualTo("wheel(5,1,0)");
    assertThat(g.n).isEqualTo(6L);
    assertThat(g.m).isEqualTo(20L);
  }

  @Test
  @DisplayName("induced with a missing substitute graph panics")
  void shouldPanicWhenSubstituteMissing() {
    Graph g = Basic.board(2L, 0L, 0L, 0L, 1L, 0L, 0L);
    g.vertices[0].z.I = Basic.IND_GRAPH;
    assertThat(Basic.induced(g, null, 0L, 0L, 0L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.MISSING_OPERAND + 1);
  }

  @Test
  @DisplayName("the header shortcuts expand to the documented board and subsets calls")
  void shouldExpandShortcutsWhenCalled() {
    assertThat(Basic.complete(4L).id).isEqualTo("board(4,0,0,0,-1,0,0)");
    assertThat(Basic.circuit(5L).id).isEqualTo("board(5,0,0,0,1,1,0)");
    assertThat(Basic.petersen().n).isEqualTo(10L);
    assertThat(Basic.petersen().m).isEqualTo(30L);
    assertThat(Basic.transitive(4L).id).isEqualTo("board(4,0,0,0,-1,0,1)");
    assertThat(Basic.empty(3L).id).isEqualTo("board(3,0,0,0,2,0,0)");
    assertThat(Basic.cycle(5L).id).isEqualTo("board(5,0,0,0,1,1,1)");
    assertThat(Basic.petersen().id).isEqualTo("subsets(2,1,-4,0,0,0,0x1,0)");
    assertThat(Basic.allPerms(3L, 0L).id).isEqualTo("perms(1,-2,0,0,0,3,0)");
    assertThat(Basic.allParts(4L, 1L).id).isEqualTo("parts(4,4,4,1)");
    assertThat(Basic.allTrees(3L, 0L).id).isEqualTo("binary(3,3,0)");
  }
}
