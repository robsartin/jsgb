package com.robsartin.jsgb.basic;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BasicSetOpsTest {

  @Test
  @DisplayName("complement of a path with copy=1 and self=1 keeps only the original edges")
  void shouldKeepOnlyOriginalEdgesWhenComplementCopiesWithSelf() {
    Graph g = Basic.complement(Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 0L), 1L, 1L, 0L);
    assertThat(g.id).isEqualTo("complement(board(3,0,0,0,1,0,0),1,1,0)");
    assertThat(g.n).isEqualTo(3L);
    // C section 76's formula creates an edge/loop only when (adjacent-in-original == (copy!=0));
    // the original path has no self-loops, so copy=1 keeps only its 2 edges (4 arcs), regardless
    // of self=1. Verified against the tangled C: complement(board(3,0,0,0,1,0,0),1,1,0) has m=4.
    assertThat(g.m).isEqualTo(4L);
    assertThat(g.utilTypes).isEqualTo("ZZZZZZZZZZZZZZ");
    assertThat(g.vertices[0].u.ref).isNull();
  }

  @Test
  @DisplayName("complement of a null graph panics with missing_operand")
  void shouldPanicWhenOperandMissing() {
    assertThat(Basic.complement(null, 0L, 0L, 0L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.MISSING_OPERAND);
    assertThat(Basic.gunion(null, Basic.board(2L, 0L, 0L, 0L, 1L, 0L, 0L), 0L, 0L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.MISSING_OPERAND);
  }

  @Test
  @DisplayName("gunion of a path and its complement is the complete graph with loops")
  void shouldBeCompleteWhenPathUnionedWithComplement() {
    Graph path = Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 0L);
    Graph comp = Basic.complement(Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 0L), 0L, 1L, 0L);
    Graph g = Basic.gunion(path, comp, 0L, 0L);
    assertThat(g.id)
        .isEqualTo("gunion(board(3,0,0,0,1,0,0),complement(board(3,0,0,0,1,0,0),0,1,0),0,0)");
    assertThat(g.m).isEqualTo(12L); // 3 edges + 3 loops
  }

  @Test
  @DisplayName("intersection of a path with itself keeps every edge once")
  void shouldKeepEdgesWhenIntersectedWithItself() {
    Graph a = Basic.board(4L, 0L, 0L, 0L, 1L, 0L, 0L);
    Graph b = Basic.board(4L, 0L, 0L, 0L, 1L, 0L, 0L);
    Graph g = Basic.intersection(a, b, 0L, 0L);
    assertThat(g.m).isEqualTo(6L);
    assertThat(g.vertices[1].v.I).isZero();
    assertThat(g.vertices[1].w.I).isZero();
  }

  @Test
  @DisplayName(
      "intersection with a multigraph on the right keeps the smallest maximum for a repeated"
          + " pair")
  void shouldKeepSmallestMaximumWhenRightOperandRepeatsAnEdge() {
    Graph g = Basic.board(2L, 0L, 0L, 0L, 1L, 0L, 0L); // one edge 0-1 of length 1
    Graph gg = Gb.newGraph(2L);
    gg.id = "multi";
    gg.vertices[0].name = "0";
    gg.vertices[1].name = "1";
    Gb.newEdge(gg.vertices[0], gg.vertices[1], 3L);
    Gb.newEdge(gg.vertices[0], gg.vertices[1], 5L);
    Graph r = Basic.intersection(g, gg, 0L, 0L);
    assertThat(r.m).isEqualTo(2L); // one edge, two arcs
    assertThat(r.vertices[0].arcs.len).isEqualTo(3L);
    assertThat(r.vertices[0].arcs.mate.len).isEqualTo(3L);
    assertThat(r.vertices[0].arcs.next).isNull();
  }
}
