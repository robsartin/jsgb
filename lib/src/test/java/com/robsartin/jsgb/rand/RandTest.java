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
  @DisplayName("walker builds Walker's alias table as the C does, and it routes draws correctly")
  void shouldBuildAliasTableWhenDistributionIsGiven() {
    Rand.MagicEntry[] table = Rand.walker(3L, 4L, DST);
    assertThat(table[0].prob).isEqualTo(0x1fffffffL);
    assertThat(table[0].inx).isEqualTo(0L);
    assertThat(table[1].prob).isEqualTo(0x3fffffffL);
    assertThat(table[1].inx).isEqualTo(0L);
    assertThat(table[2].prob).isEqualTo(0x5fffffffL);
    assertThat(table[2].inx).isEqualTo(0L);
    assertThat(table[3].prob).isEqualTo(0x5fffffffL);
    assertThat(table[3].inx).isEqualTo(0L);

    int kk = 29;
    assertThat(route(table, 0x7fffffffL, kk)).isEqualTo(0L);
    assertThat(route(table, 0x40000000L, kk)).isEqualTo(2L);
    assertThat(route(table, 0x20000000L, kk)).isEqualTo(1L);
    assertThat(route(table, 0L, kk)).isEqualTo(0L);
  }

  /** Applies the same {@code uu <= magic->prob ? k : magic->inx} rule the generators use. */
  private static long route(Rand.MagicEntry[] table, long uu, int kk) {
    int k = (int) (uu >> kk);
    Rand.MagicEntry magic = table[k];
    return uu <= magic.prob ? k : magic.inx;
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
  @DisplayName("random_graph(4,7,1,1,1,0,0,5,9,3) matches the C oracle at vertex 2")
  void shouldMatchOracleArcWhenDirectedGraphBuilt() {
    Graph g = Rand.randomGraph(4L, 7L, 1L, 1L, 1L, null, null, 5L, 9L, 3L);
    assertThat(g).isNotNull();
    assertThat(g.m).isEqualTo(7L);
    Arc a = g.vertices[2].arcs;
    assertThat(a).isNotNull();
    assertThat(a.tip.name).isEqualTo("0");
    assertThat(a.len).isEqualTo(5L);
    assertThat(a.next).isNull();
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

    // dist1 puts all source probability on vertex 0 of part 1; dist2 puts all destination
    // probability on the last vertex of part 2 (global index n1+n2-1 = 4). If the padding
    // offsets were wrong, arcs would land on the wrong vertices instead of always 0 -> 4.
    long[] forcedDist1 = {0x40000000L, 0L};
    long[] forcedDist2 = {0L, 0L, 0x40000000L};
    Graph forced = Rand.randomBigraph(2L, 3L, 3L, 1L, forcedDist1, forcedDist2, 1L, 1L, 5L);
    assertThat(forced).isNotNull();
    assertThat(forced.m).isEqualTo(6L);
    assertThat(forced.vertices[0].arcs).isNotNull();
    for (Arc a = forced.vertices[0].arcs; a != null; a = a.next) {
      assertThat(a.tip.name).isEqualTo("4");
    }
    assertThat(forced.vertices[1].arcs).isNull();
    assertThat(forced.vertices[2].arcs).isNull();
    assertThat(forced.vertices[3].arcs).isNull();
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
  @DisplayName("random_lengths(...,1,10,12,dist,4) on a hand-built path matches the C oracle")
  void shouldMatchOracleLengthWhenDistributionTableUsed() {
    Graph g = Gb.newGraph(3L);
    g.vertices[0].name = "0";
    g.vertices[1].name = "1";
    g.vertices[2].name = "2";
    Gb.newArc(g.vertices[1], g.vertices[2], 1L);
    Gb.newArc(g.vertices[0], g.vertices[1], 1L);
    long[] dist = {0x20000000L, 0x10000000L, 0x10000000L};
    assertThat(Rand.randomLengths(g, 1L, 10L, 12L, dist, 4L)).isZero();
    assertThat(g.vertices[0].arcs).isNotNull();
    assertThat(g.vertices[0].arcs.tip.name).isEqualTo("1");
    assertThat(g.vertices[0].arcs.len).isEqualTo(12L);
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
