package com.robsartin.jsgb.graph;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GraphTest {

  @BeforeEach
  void resetKernel() {
    Gb.reset();
  }

  @Test
  @DisplayName("gb_new_graph(n) allocates n + extra_n vertices with empty names and default id")
  void shouldCreateVerticesWithDefaultsWhenNewGraphCalled() {
    Graph g = Gb.newGraph(2L);
    assertThat(g).isNotNull();
    assertThat(g.n).isEqualTo(2L);
    assertThat(g.m).isZero();
    assertThat(g.vertices).hasSize(2 + 4);
    for (Vertex v : g.vertices) {
      assertThat(v.name).isSameAs(Gb.NULL_STRING);
      assertThat(v.arcs).isNull();
    }
    assertThat(g.id).isEqualTo("gb_new_graph(2)");
    assertThat(g.utilTypes).isEqualTo("ZZZZZZZZZZZZZZ");
    assertThat(g.arcBlocks()).isEmpty();
    assertThat(Gb.troubleCode).isZero();
  }

  @Test
  @DisplayName("vertices carry their position in the array, which stands in for C address order")
  void shouldNumberVerticesByPositionWhenNewGraphCalled() {
    Graph g = Gb.newGraph(3L);
    for (int i = 0; i < g.vertices.length; i++) {
      assertThat(g.vertices[i].index).isEqualTo(i);
    }
  }

  @Test
  @DisplayName("extra_n is honoured when changed before gb_new_graph")
  void shouldAllocateExtraVerticesWhenExtraNChanged() {
    Gb.extraN = 1;
    assertThat(Gb.newGraph(5L).vertices).hasSize(6);
  }

  @Test
  @DisplayName("gb_new_graph makes the new graph current and clears gb_trouble_code")
  void shouldBecomeCurrentGraphWhenNewGraphCalled() {
    Gb.troubleCode = 3;
    Graph g = Gb.newGraph(1L);
    assertThat(Gb.curGraph()).isSameAs(g);
    assertThat(Gb.troubleCode).isZero();
  }

  @Test
  @DisplayName("util slots start as zero and null, and typed accessors cast")
  void shouldStartZeroAndCastWhenUtilSlotsUsed() {
    Graph g = Gb.newGraph(1L);
    Vertex v = g.vertices[0];
    assertThat(v.u.I).isZero();
    assertThat(v.u.V()).isNull();
    v.u.V(g.vertices[1]);
    assertThat(v.u.V()).isSameAs(g.vertices[1]);
    v.x.S("hello");
    assertThat(v.x.S()).isEqualTo("hello");
    Arc a = new Arc();
    v.z.A(a);
    assertThat(v.z.A()).isSameAs(a);
    g.uu.G(g);
    assertThat(g.uu.G()).isSameAs(g);
  }

  @Test
  @DisplayName("gb_save_string returns an equal string (strings need no arena in Java)")
  void shouldReturnEqualStringWhenSaveStringCalled() {
    Gb.newGraph(1L);
    assertThat(Gb.saveString("vertex 0")).isEqualTo("vertex 0");
  }

  @Test
  @DisplayName("mark_bipartite stores n_1 in uu.I and flags util_types[8] as I")
  void shouldSetN1AndUtilTypeWhenMarkBipartiteCalled() {
    Graph g = Gb.newGraph(5L);
    Gb.markBipartite(g, 2L);
    assertThat(g.uu.I).isEqualTo(2L);
    assertThat(g.utilTypes).isEqualTo("ZZZZZZZZIZZZZZ");
  }

  @Test
  @DisplayName("gb_recycle releases the vertex array and arc blocks")
  void shouldDropStorageWhenRecycled() {
    Graph g = Gb.newGraph(2L);
    Gb.recycle(g);
    assertThat(g.vertices).isNull();
    assertThat(g.arcBlocks()).isEmpty();
  }

  @Test
  @DisplayName("gb_new_graph returns null when n is negative, like the C's NULL return")
  void shouldReturnNullWhenNIsNegative() {
    assertThat(Gb.newGraph(-1L)).isNull();
    assertThat(Gb.troubleCode).isZero();
  }

  @Test
  @DisplayName("gb_new_graph returns null when n + extra_n cannot be allocated as an array")
  void shouldReturnNullWhenNTooLargeForArray() {
    assertThat(Gb.newGraph((long) Integer.MAX_VALUE)).isNull();
    assertThat(Gb.troubleCode).isZero();
  }

  @Test
  @DisplayName("a rejected gb_new_graph makes the dummy graph current")
  void shouldMakeDummyCurrentWhenNewGraphRejected() {
    Graph g = Gb.newGraph(1L);
    Gb.newGraph(-1L);
    assertThat(Gb.curGraph()).isNotSameAs(g);
  }
}
