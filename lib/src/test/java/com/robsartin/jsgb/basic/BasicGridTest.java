package com.robsartin.jsgb.basic;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BasicGridTest {

  @Test
  @DisplayName("board(3,4,0,0,-1,0,0) is the 3x4 rook graph queen.w starts from")
  void shouldBuildRookMovesWhenPieceIsMinusOne() {
    Graph g = Basic.board(3L, 4L, 0L, 0L, -1L, 0L, 0L);
    assertThat(g.id).isEqualTo("board(3,4,0,0,-1,0,0)");
    assertThat(g.n).isEqualTo(12L);
    // Hand count: rows contribute 3*C(4,2)=18 undirected edges, columns 4*C(3,2)=12, for 30
    // undirected edges = 60 arcs (gb_new_edge stores both directions); the C confirms 60.
    assertThat(g.m).isEqualTo(60L);
    assertThat(g.vertices[0].name).isEqualTo("0.0");
    assertThat(g.vertices[11].name).isEqualTo("2.3");
    assertThat(g.vertices[5].x.I).isEqualTo(1L);
    assertThat(g.vertices[5].y.I).isEqualTo(1L);
    assertThat(g.utilTypes).isEqualTo("ZZZIIIZZZZZZZZ");
  }

  @Test
  @DisplayName("board defaults to an 8x8 board when n1 <= 0 and rejects more than 91 dimensions")
  void shouldDefaultAndPanicWhenDimensionsAreExtreme() {
    Graph g = Basic.board(0L, 0L, 0L, 0L, 1L, 0L, 0L);
    assertThat(g.n).isEqualTo(64L);
    assertThat(g.id).isEqualTo("board(8,8,0,0,1,0,0)");
    assertThat(Basic.board(2L, -92L, 0L, 0L, 1L, 0L, 0L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS);
  }

  @Test
  @DisplayName("simplex(3,-3,...) enumerates the 20 compositions of 3 into 4 parts")
  void shouldEnumerateCompositionsWhenSimplexBuilt() {
    Graph g = Basic.simplex(3L, -3L, 0L, 0L, 0L, 0L, 0L);
    assertThat(g.id).isEqualTo("simplex(3,-3,0,0,0,0,0)");
    assertThat(g.n).isEqualTo(20L);
    assertThat(g.utilTypes).isEqualTo("VVZIIIZZZZZZZZ");
    // Vertices are enumerated lexicographically by (x0,...,xd), so x0=0 comes first, not x0=3.
    assertThat(g.vertices[0].name).isEqualTo("0.0.0.3");
    assertThat(Gb.hashLookup("0.1.1.1", g)).isSameAs(g.vertices[5]);
  }

  @Test
  @DisplayName("subsets id prints the massaged arguments and hex size bits")
  void shouldPrintMassagedArgumentsWhenSubsetsBuilt() {
    Graph g = Basic.subsets(32L, 18L, 16L, 0L, 999L, -999L, 0x80000000L, 1L);
    assertThat(g.id).isEqualTo("subsets(32,18,16,0,0,0,0x80000000,1)");
    assertThat(g.n).isEqualTo(3L);
    assertThat(g.m).isEqualTo(2L);
  }

  @Test
  @DisplayName("simplex with no free coordinate (d=0) still enumerates its single vertex")
  void shouldBuildSingleVertexWhenSimplexHasNoFreeCoordinate() {
    // n0=5 clamps to n=2 (nn[0]=2), n1=0 forces d=0: only x0 is free, and x0=2 is forced.
    Graph g = Basic.simplex(2L, 5L, 0L, 0L, 0L, 0L, 0L);
    assertThat(g.n).isEqualTo(1L);
    assertThat(g.vertices[0].name).isEqualTo("2");
  }
}
