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

    // The C test_graph also checks the edge trick (mate lookup by slot parity); pin the block
    // layout it depends on: u.arcs = slot2 -> slot3 -> slot0, v.arcs = slot4 -> slot1.
    Arc[] block = g.arcBlocks().get(0);
    assertThat(u.arcs).isSameAs(block[2]);
    assertThat(u.arcs.next.next).isSameAs(block[0]);
    assertThat(v.arcs.next).isSameAs(block[1]);
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
    g2.xx.ref = "x";
    g2.yy.ref = "y";
    g2.zz.ref = "z";
    Gb.switchToGraph(g2); // parks g1, resumes g2 at slot 1
    Gb.newArc(g2.vertices[1], g2.vertices[0], 4L);

    assertThat(g1.arcBlocks()).hasSize(1);
    assertThat(g1.arcBlocks().get(0)[1].len).isEqualTo(3L);
    assertThat(g2.arcBlocks()).hasSize(1);
    assertThat(g2.arcBlocks().get(0)[1].len).isEqualTo(4L);
    assertThat(g1.ww.ref).isNotNull(); // g1 is parked: its cursor lives in ww
    assertThat(g2.ww.ref).isNull(); // g2 is current: its ww is cleared
    assertThat(g2.xx.ref).isNull(); // g2 is current: its xx is cleared too
    assertThat(g2.yy.ref).isNull(); // g2 is current: its yy is cleared too
    assertThat(g2.zz.ref).isNull(); // g2 is current: its zz is cleared too
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

  @Test
  @DisplayName("make_compound_id concatenates when the inner id fits")
  void shouldConcatenateWhenInnerIdFits() {
    Graph gg = Gb.newGraph(1L);
    gg.id = "board(3,4,0,0,-1,0,0)";
    Graph g = Gb.newGraph(1L);
    Gb.makeCompoundId(g, "complement(", gg, ",1,1,0)");
    assertThat(g.id).isEqualTo("complement(board(3,4,0,0,-1,0,0),1,1,0)");
  }

  @Test
  @DisplayName("make_compound_id truncates the inner id with ...) to fit 160 characters")
  void shouldTruncateWhenInnerIdTooLong() {
    Graph gg = Gb.newGraph(1L);
    gg.id = "x".repeat(160);
    Graph g = Gb.newGraph(1L);
    Gb.makeCompoundId(g, "lines(", gg, ",0)");
    // avail = 161 - 6 - 3 = 152; inner keeps avail-5 = 147 chars, then "...)"
    assertThat(g.id).isEqualTo("lines(" + "x".repeat(147) + "...)" + ",0)");
    assertThat(g.id).hasSize(160);
  }

  @Test
  @DisplayName("make_double_compound_id concatenates when both inner ids fit")
  void shouldConcatenateWhenBothInnerIdsFit() {
    Graph gg = Gb.newGraph(1L);
    gg.id = "board(3,4,0,0,-1,0,0)";
    Graph ggg = Gb.newGraph(1L);
    ggg.id = "board(3,4,0,0,-2,0,0)";
    Graph g = Gb.newGraph(1L);
    Gb.makeDoubleCompoundId(g, "gunion(", gg, ",", ggg, ",0,0)");
    assertThat(g.id).isEqualTo("gunion(board(3,4,0,0,-1,0,0),board(3,4,0,0,-2,0,0),0,0)");
  }

  @Test
  @DisplayName("make_double_compound_id truncates both inner ids when they do not fit")
  void shouldTruncateBothWhenInnerIdsTooLong() {
    Graph gg = Gb.newGraph(1L);
    gg.id = "a".repeat(100);
    Graph ggg = Gb.newGraph(1L);
    ggg.id = "b".repeat(100);
    Graph g = Gb.newGraph(1L);
    Gb.makeDoubleCompoundId(g, "gunion(", gg, ",", ggg, ",0,0)");
    // avail = 161 - 7 - 1 - 5 = 148; first keeps 148/2-5 = 69, second keeps (148-9)/2 = 69
    assertThat(g.id)
        .isEqualTo("gunion(" + "a".repeat(69) + "...)" + "," + "b".repeat(69) + "...)" + ",0,0)");
  }

  @Test
  @DisplayName("arcs know their slot index within their block")
  void shouldNumberArcSlotsWhenBlockAllocated() {
    Graph g = Gb.newGraph(2L);
    Gb.newArc(g.vertices[0], g.vertices[1], 1L);
    Gb.newArc(g.vertices[0], g.vertices[1], 2L);
    Arc[] block = g.arcBlocks().get(0);
    assertThat(block[0].index).isZero();
    assertThat(block[101].index).isEqualTo(101);
    assertThat(g.vertices[0].arcs.index).isEqualTo(1);
  }

  @Test
  @DisplayName("allocVertices registers an extra vertex block in allocation order")
  void shouldRegisterExtraBlockWhenAllocVerticesCalled() {
    Graph g = Gb.newGraph(3L);
    Vertex[] extra = Gb.allocVertices(g, 1);
    assertThat(extra).hasSize(1);
    assertThat(extra[0].index).isZero();
    assertThat(extra[0].name).isSameAs(Gb.NULL_STRING);
    assertThat(g.extraVertexBlocks()).containsExactly(extra);
  }

  @Test
  @DisplayName("allocArcs appends an exact-size block without moving the cursor")
  void shouldAppendExactBlockWhenAllocArcsCalled() {
    Graph g = Gb.newGraph(2L);
    Gb.newArc(g.vertices[0], g.vertices[1], 1L);
    Arc[] exact = Gb.allocArcs(g, 5);
    assertThat(exact).hasSize(5);
    assertThat(exact[4].index).isEqualTo(4);
    assertThat(g.arcBlocks()).hasSize(2);
    Gb.newArc(g.vertices[1], g.vertices[0], 2L); // cursor still in the first block
    assertThat(g.arcBlocks()).hasSize(2);
    assertThat(g.arcBlocks().get(0)[1].len).isEqualTo(2L);
  }

  @Test
  @DisplayName("restoreStorage gives exactly n vertices and one m-arc block, at least one each")
  void shouldReplaceStorageWhenRestoreStorageCalled() {
    Graph g = Gb.newGraph(0L);
    Gb.restoreStorage(g, 3, 4);
    assertThat(g.vertices).hasSize(3);
    assertThat(g.vertices[2].index).isEqualTo(2);
    assertThat(g.arcBlocks()).hasSize(1);
    assertThat(g.arcBlocks().get(0)).hasSize(4);
    assertThat(g.extraVertexBlocks()).isEmpty();
    Gb.newArc(g.vertices[0], g.vertices[1], 9L); // C: a fresh 102-block, not the restored one
    assertThat(g.arcBlocks()).hasSize(2);
    assertThat(g.arcBlocks().get(1)).hasSize(Gb.ARCS_PER_BLOCK);

    Graph empty = Gb.newGraph(0L);
    Gb.restoreStorage(empty, 0, 0);
    assertThat(empty.vertices).hasSize(1);
    assertThat(empty.arcBlocks().get(0)).hasSize(1);
  }

  @Test
  @DisplayName("isFirstOfSelfLoop is true only for the first arc of a self-loop edge")
  void shouldDetectSelfLoopFirstArcWhenEdgeIsLoop() {
    Graph g = Gb.newGraph(2L);
    Vertex u = g.vertices[0];
    Vertex v = g.vertices[1];
    Gb.newEdge(u, u, 1L);
    Gb.newEdge(u, v, 2L);
    Gb.newArc(v, v, 3L);
    Arc[] block = g.arcBlocks().get(0);
    assertThat(Gb.isFirstOfSelfLoop(block[0])).isTrue();
    assertThat(Gb.isFirstOfSelfLoop(block[1])).isFalse();
    assertThat(Gb.isFirstOfSelfLoop(block[2])).isFalse();
    assertThat(Gb.isFirstOfSelfLoop(block[3])).isFalse();
    assertThat(Gb.isFirstOfSelfLoop(block[4])).isFalse(); // newArc: no mate
  }

  @Test
  @DisplayName("virginArc rolls over on the current block's own length")
  void shouldRollOverOnBlockLengthWhenBlockIsNotStandardSize() {
    Graph g = Gb.newGraph(2L);
    Gb.restoreStorage(g, 2, 1);
    Gb.switchToGraph(g); // cursor: none; first newArc allocates a fresh block
    Gb.newArc(g.vertices[0], g.vertices[1], 1L);
    assertThat(g.arcBlocks()).hasSize(2);
  }
}
