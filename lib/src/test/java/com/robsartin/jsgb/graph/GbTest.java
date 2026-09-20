package com.robsartin.jsgb.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GbTest {

  @BeforeEach
  void resetKernel() {
    Gb.reset();
  }

  @Test
  @DisplayName("test_graph: edges and arcs link as the C self-test expects")
  void shouldLinkArcsWhenTranslatedCSelfTestRuns() {
    Graph g = Gb.newGraph(2L);
    Vertex u = g.vertices[0];
    Vertex v = g.vertices[1];
    u.name = Gb.saveString("vertex 0");
    v.name = Gb.saveString("vertex 1");

    Gb.newEdge(v, u, -1L);
    Gb.newEdge(u, u, 1L);
    Gb.newArc(v, u, -1L);

    // The C checks v->name[7] + g->n == v->arcs->next->tip->name[7] + g->m - 2,
    // i.e. '1' + 2 == '0' + 5 - 2: v's second arc points at u.
    assertThat(g.m).isEqualTo(5L);
    assertThat(v.arcs.next.tip).isSameAs(u);
    assertThat(v.arcs.tip).isSameAs(u); // the newArc(v,u) is at the head of v's list
  }

  @Test
  @DisplayName("gb_new_edge with u before v puts u's arc first in the block and v's arc second")
  void shouldPlaceArcsInIndexOrderWhenUPrecedesV() {
    Graph g = Gb.newGraph(2L);
    Vertex u = g.vertices[0];
    Vertex v = g.vertices[1];
    Gb.newEdge(u, v, 7L);
    Arc[] block = g.arcBlocks().get(0);
    assertThat(block).hasSize(Gb.ARCS_PER_BLOCK);
    assertThat(u.arcs).isSameAs(block[0]);
    assertThat(v.arcs).isSameAs(block[1]);
    assertThat(u.arcs.tip).isSameAs(v);
    assertThat(v.arcs.tip).isSameAs(u);
    assertThat(u.arcs.mate).isSameAs(v.arcs);
    assertThat(v.arcs.mate).isSameAs(u.arcs);
    assertThat(u.arcs.len).isEqualTo(7L);
    assertThat(v.arcs.len).isEqualTo(7L);
    assertThat(g.m).isEqualTo(2L);
  }

  @Test
  @DisplayName("gb_new_edge with v before u puts v's arc first in the block")
  void shouldPlaceArcsInIndexOrderWhenVPrecedesU() {
    Graph g = Gb.newGraph(2L);
    Vertex u = g.vertices[1];
    Vertex v = g.vertices[0];
    Gb.newEdge(u, v, 3L);
    Arc[] block = g.arcBlocks().get(0);
    assertThat(v.arcs).isSameAs(block[0]);
    assertThat(u.arcs).isSameAs(block[1]);
    assertThat(v.arcs.tip).isSameAs(u);
    assertThat(u.arcs.tip).isSameAs(v);
  }

  @Test
  @DisplayName("a self-loop edge puts both arcs on the same vertex, second slot first in the list")
  void shouldChainBothArcsWhenEdgeIsSelfLoop() {
    Graph g = Gb.newGraph(1L);
    Vertex u = g.vertices[0];
    Gb.newEdge(u, u, 1L);
    Arc[] block = g.arcBlocks().get(0);
    assertThat(u.arcs).isSameAs(block[0]);
    assertThat(u.arcs.next).isSameAs(block[1]);
    assertThat(block[1].next).isNull();
  }

  @Test
  @DisplayName("gb_new_arc appends one arc at the head of u's list and does not set a mate")
  void shouldPrependArcWhenNewArcCalled() {
    Graph g = Gb.newGraph(2L);
    Vertex u = g.vertices[0];
    Vertex v = g.vertices[1];
    Gb.newArc(u, v, 5L);
    Gb.newArc(u, v, 6L);
    assertThat(u.arcs.len).isEqualTo(6L);
    assertThat(u.arcs.next.len).isEqualTo(5L);
    assertThat(u.arcs.mate).isNull();
    assertThat(v.arcs).isNull();
    assertThat(g.m).isEqualTo(2L);
  }

  @Test
  @DisplayName("the 103rd arc starts a second block of 102")
  void shouldStartNewBlockWhenFirstBlockFull() {
    Graph g = Gb.newGraph(2L);
    Vertex u = g.vertices[0];
    Vertex v = g.vertices[1];
    for (int i = 0; i < 103; i++) {
      Gb.newArc(u, v, i);
    }
    assertThat(g.arcBlocks()).hasSize(2);
    assertThat(g.arcBlocks().get(1)[0].len).isEqualTo(102L);
    assertThat(g.arcBlocks().get(1)[1].tip).isNull();
  }

  @Test
  @DisplayName("gb_new_edge straddling a block boundary is rejected instead of corrupting memory")
  void shouldThrowWhenEdgeWouldStraddleBlocks() {
    Graph g = Gb.newGraph(2L);
    Vertex u = g.vertices[0];
    Vertex v = g.vertices[1];
    for (int i = 0; i < 101; i++) {
      Gb.newArc(u, v, i);
    }
    assertThatThrownBy(() -> Gb.newEdge(u, v, 0L)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("creating an arc with no current graph is an error")
  void shouldThrowWhenNoGraphIsCurrent() {
    Graph g = Gb.newGraph(1L);
    Gb.switchToGraph(null);
    assertThatThrownBy(() -> Gb.newArc(g.vertices[0], g.vertices[0], 0L))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("switch_to_graph resumes each graph's own arc block where it left off")
  void shouldResumeBlockWhenSwitchingBackToGraph() {
    Graph g2 = Gb.newGraph(2L);
    Graph g1 = Gb.newGraph(2L);
    Gb.newArc(g1.vertices[0], g1.vertices[1], 1L);
    Gb.switchToGraph(g2); // parks g1's cursor at slot 1
    Gb.newArc(g2.vertices[0], g2.vertices[1], 2L);
    Gb.switchToGraph(g1); // parks g2's cursor, resumes g1 at slot 1
    Gb.newArc(g1.vertices[1], g1.vertices[0], 3L);
    Gb.switchToGraph(g2); // parks g1, resumes g2 at slot 1
    Gb.newArc(g2.vertices[1], g2.vertices[0], 4L);

    assertThat(g1.arcBlocks()).hasSize(1);
    assertThat(g1.arcBlocks().get(0)[1].len).isEqualTo(3L);
    assertThat(g2.arcBlocks()).hasSize(1);
    assertThat(g2.arcBlocks().get(0)[1].len).isEqualTo(4L);
    assertThat(g1.ww.ref).isNotNull(); // g1 is parked: its cursor lives in ww
    assertThat(g2.ww.ref).isNull(); // g2 is current: its ww is cleared
  }

  @Test
  @DisplayName("switching to a graph that was never parked starts a fresh block")
  void shouldStartFreshBlockWhenGraphHasNoSavedCursor() {
    Graph g1 = Gb.newGraph(2L);
    Gb.newArc(g1.vertices[0], g1.vertices[1], 1L);
    Gb.newGraph(1L); // g1 was not switched away from, so its cursor was not parked
    Gb.switchToGraph(g1);
    Gb.newArc(g1.vertices[0], g1.vertices[1], 2L);
    assertThat(g1.arcBlocks()).hasSize(2);
  }
}
