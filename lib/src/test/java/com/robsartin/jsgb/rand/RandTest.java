package com.robsartin.jsgb.rand;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RandTest {

  private static final long[] DST = {0x20000000L, 0x10000000L, 0x10000000L};

  @Test
  @DisplayName("random_graph(3,10,1,1,0,0,dist,1,2,1) is the graph test_sample saves first")
  void shouldBuildTestSampleGraphWhenSeedIsOne() {
    Graph g = Rand.randomGraph(3L, 10L, 1L, 1L, 0L, null, DST, 1L, 2L, 1L);
    assertThat(g).isNotNull();
    assertThat(g.id).isEqualTo("random_graph(3,10,1,1,0,0,dist,1,2,1)");
    assertThat(g.n).isEqualTo(3L);
    assertThat(g.m).isEqualTo(20L);
    assertThat(g.vertices[2].name).isEqualTo("2");
    for (int i = 0; i < 3; i++) {
      for (Arc a = g.vertices[i].arcs; a != null; a = a.next) {
        assertThat(a.mate).isNotNull();
        assertThat(a.mate.len).isEqualTo(a.len);
        assertThat(a.len).isBetween(1L, 2L);
      }
    }
  }

  @Test
  @DisplayName("random_graph panics on n == 0 and on bad distributions")
  void shouldPanicWhenSpecsAreBad() {
    assertThat(Rand.randomGraph(0L, 1L, 0L, 0L, 0L, null, null, 1L, 1L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS);
    assertThat(Rand.randomGraph(3L, 1L, 0L, 0L, 0L, null, null, 2L, 1L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.VERY_BAD_SPECS);
    long[] bad = {1L, 1L, 1L};
    assertThat(Rand.randomGraph(3L, 1L, 0L, 0L, 0L, bad, null, 1L, 1L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.INVALID_OPERAND + 2);
    assertThat(Rand.randomGraph(3L, 1L, 0L, 0L, 0L, null, bad, 1L, 1L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.INVALID_OPERAND + 7);
  }

  @Test
  @DisplayName("random_lengths returns the C's error codes without touching the graph")
  void shouldReturnErrorCodesWhenArgumentsAreBad() {
    assertThat(Rand.randomLengths(null, 0L, 0L, 0L, null, 0L)).isEqualTo(Gb.MISSING_OPERAND);
    Graph g = Gb.newGraph(2L);
    Gb.newEdge(g.vertices[0], g.vertices[1], 4L);
    assertThat(Rand.randomLengths(g, 0L, 3L, 1L, null, 0L)).isEqualTo(Gb.VERY_BAD_SPECS);
    long[] bad = {0x40000000L, 1L};
    assertThat(Rand.randomLengths(g, 0L, 1L, 2L, bad, 0L)).isEqualTo(1L);
    assertThat(g.vertices[0].arcs.len).isEqualTo(4L);
  }

  @Test
  @DisplayName("random_lengths with min == max sets every arc and its mate without drawing")
  void shouldSetConstantLengthsWhenRangeIsSingleValue() {
    Graph g = Gb.newGraph(2L);
    g.id = "hand";
    Gb.newEdge(g.vertices[0], g.vertices[1], 4L);
    Gb.newEdge(g.vertices[1], g.vertices[1], 4L);
    assertThat(Rand.randomLengths(g, 0L, 7L, 7L, null, 5L)).isZero();
    assertThat(g.id).isEqualTo("random_lengths(hand,0,7,7,0,5)");
    for (int i = 0; i < 2; i++) {
      for (Arc a = g.vertices[i].arcs; a != null; a = a.next) {
        assertThat(a.len).isEqualTo(7L);
      }
    }
  }

  @Test
  @DisplayName("random_graph draws u from the alias table when distFrom is given, v uniformly")
  void shouldSelectViaAliasTableWhenDistFromIsProvided() {
    Graph g = Rand.randomGraph(3L, 3L, 0L, 0L, 0L, DST, null, 1L, 1L, 7L);
    assertThat(g).isNotNull();
    assertThat(g.n).isEqualTo(3L);
    assertThat(g.m).isEqualTo(6L);
  }

  @Test
  @DisplayName(
      "random_graph combines duplicate arcs into the minimum length when multi is negative")
  void shouldCombineDuplicateArcsWhenMultiIsNegative() {
    Graph g = Rand.randomGraph(3L, 8L, -1L, 1L, 0L, null, null, 1L, 5L, 11L);
    assertThat(g).isNotNull();
    assertThat(g.m).isEqualTo(6L);
  }

  @Test
  @DisplayName("random_graph creates plain arcs for a directed multigraph")
  void shouldCreateArcsWhenGraphIsDirected() {
    Graph g = Rand.randomGraph(4L, 7L, 1L, 1L, 1L, null, null, 5L, 9L, 3L);
    assertThat(g).isNotNull();
    assertThat(g.m).isEqualTo(7L);
  }

  @Test
  @DisplayName("random_bigraph fabricates uniform distributions when dist1 and dist2 are null")
  void shouldFabricateUniformDistributionsWhenDistsAreNull() {
    Graph g = Rand.randomBigraph(2L, 3L, 5L, 0L, null, null, 1L, 3L, 5L);
    assertThat(g).isNotNull();
    assertThat(g.id).isEqualTo("random_bigraph(2,3,5,0,0,0,1,3,5)");
    assertThat(g.m).isEqualTo(10L);
    assertThat(g.uu.I).isEqualTo(2L);
  }

  @Test
  @DisplayName("random_bigraph copies provided dist1 and dist2 into the padded arrays")
  void shouldCopyProvidedDistributionsWhenGiven() {
    long[] dist1 = {0x30000000L, 0x10000000L};
    long[] dist2 = {0x20000000L, 0x10000000L, 0x10000000L};
    Graph g = Rand.randomBigraph(2L, 3L, 4L, 1L, dist1, dist2, 1L, 1L, 9L);
    assertThat(g).isNotNull();
    assertThat(g.id).isEqualTo("random_bigraph(2,3,4,1,dist,dist,1,1,9)");
  }

  @Test
  @DisplayName("random_bigraph panics when a part is empty, the range is invalid, or too wide")
  void shouldPanicWhenBigraphSpecsAreBad() {
    assertThat(Rand.randomBigraph(0L, 1L, 1L, 0L, null, null, 1L, 1L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS);
    assertThat(Rand.randomBigraph(1L, 1L, 1L, 0L, null, null, 2L, 1L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.VERY_BAD_SPECS);
    assertThat(Rand.randomBigraph(1L, 1L, 1L, 0L, null, null, -1073741824L, 1073741824L, 1L))
        .isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS + 1);
  }

  @Test
  @DisplayName("random_bigraph returns null when the inner random_graph call panics")
  void shouldReturnNullWhenInnerRandomGraphPanics() {
    long[] badDist1 = {1L, 1L};
    assertThat(Rand.randomBigraph(2L, 1L, 1L, 0L, badDist1, null, 1L, 1L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.INVALID_OPERAND + 2);
  }

  @Test
  @DisplayName("random_lengths draws non-uniform lengths from a distribution table")
  void shouldDrawFromDistributionTableWhenDistIsGiven() {
    Graph g = Gb.newGraph(2L);
    g.id = "hand2";
    Gb.newEdge(g.vertices[0], g.vertices[1], 0L);
    Gb.newEdge(g.vertices[1], g.vertices[1], 0L);
    long[] dist = {0x20000000L, 0x20000000L};
    assertThat(Rand.randomLengths(g, 0L, 4L, 5L, dist, 3L)).isZero();
    for (int i = 0; i < 2; i++) {
      for (Arc a = g.vertices[i].arcs; a != null; a = a.next) {
        assertThat(a.len).isBetween(4L, 5L);
      }
    }
  }

  @Test
  @DisplayName("random_lengths rejects a negative entry, a bad sum, or too wide a range")
  void shouldReturnRemainingErrorCodesWhenDistOrRangeIsBad() {
    Graph g = Gb.newGraph(2L);
    Gb.newEdge(g.vertices[0], g.vertices[1], 0L);
    long[] negative = {-1L, 0x40000001L};
    assertThat(Rand.randomLengths(g, 0L, 0L, 1L, negative, 0L)).isEqualTo(-1L);
    long[] tooLow = {0x10000000L, 0x10000000L};
    assertThat(Rand.randomLengths(g, 0L, 0L, 1L, tooLow, 0L)).isEqualTo(2L);
    assertThat(Rand.randomLengths(g, 0L, -1073741824L, 1073741824L, null, 0L))
        .isEqualTo(Gb.BAD_SPECS);
  }
}
