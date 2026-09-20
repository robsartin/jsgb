package com.robsartin.jsgb.basic;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BasicCombinatorialTest {

  @Test
  @DisplayName("perms of a 3-element set has 6 vertices and adjusts max_inv to 3 in its id")
  void shouldEnumeratePermutationsWhenThreeDistinctElements() {
    Graph g = Basic.perms(1L, 1L, 1L, 0L, 0L, 0L, 0L);
    assertThat(g.id).isEqualTo("perms(1,1,1,0,0,3,0)");
    assertThat(g.n).isEqualTo(6L);
    assertThat(g.m).isEqualTo(12L);
    assertThat(g.utilTypes).isEqualTo("VVZZZZZZZZZZZZ");
    assertThat(g.vertices[0].name).isEqualTo("012");
    assertThat(Gb.hashLookup("210", g)).isNotNull();
  }

  @Test
  @DisplayName("parts(6) has the 11 partitions of 6 named with plus signs")
  void shouldEnumeratePartitionsWhenNIsSix() {
    Graph g = Basic.parts(6L, 0L, 0L, 0L);
    assertThat(g.id).isEqualTo("parts(6,6,6,0)");
    assertThat(g.n).isEqualTo(11L);
    // Section 58 seeds x1 at its smallest legal value, so parts are generated in increasing
    // order: "6" is the last partition of 6, not the first (the C is authoritative here; see
    // task-6-report.md for the derivation this corrects).
    assertThat(g.vertices[0].name).isEqualTo("1+1+1+1+1+1");
    assertThat(g.vertices[10].name).isEqualTo("6");
    // maxParts defaults down to n when it exceeds n (section 55), so a maxParts > MAX_D that
    // still panics needs n large enough that the clamp doesn't fire first (the C is authoritative
    // here; see task-6-report.md for the derivation this corrects).
    assertThat(Basic.parts(100L, 92L, 0L, 0L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS);
  }

  @Test
  @DisplayName("binary(4) has the 14 binary trees on 4 nodes and rejects heights over 30")
  void shouldEnumerateTreesWhenNIsFour() {
    Graph g = Basic.binary(4L, 0L, 0L);
    assertThat(g.id).isEqualTo("binary(4,4,0)");
    assertThat(g.n).isEqualTo(14L);
    assertThat(g.vertices[0].name).hasSize(9);
    assertThat(Basic.binary(40L, 31L, 0L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.VERY_BAD_SPECS);
  }
}
