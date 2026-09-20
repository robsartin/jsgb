package com.robsartin.jsgb.words;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WordsTest {

  @Test
  @DisplayName("words(5757,0,0,69) loads every word with the default weights")
  void shouldLoadAllWordsWhenNIsZero() {
    Graph g = Words.words(0L, null, 0L, 69L);
    assertThat(g).isNotNull();
    assertThat(g.id).isEqualTo("words(5757,0,0,69)");
    assertThat(g.n).isEqualTo(5757L);
    assertThat(g.m).isEqualTo(28270L);
    assertThat(g.utilTypes).isEqualTo("IZZZZZIZZZZZZZ");
    Vertex v = g.vertices[5555];
    assertThat(v.name).isEqualTo("laded");
    assertThat(v.u.I).isZero();
    assertThat(v.arcs.tip.name).isEqualTo("lades");
    assertThat(v.arcs.a.I).isEqualTo(4L);
    assertThat(v.arcs.mate.a.I).isEqualTo(4L);
  }

  @Test
  @DisplayName(
      "the weight vector at the exact 2^30 boundary panics with bad_specs, one less passes")
  void shouldPanicAtBoundaryWhenWeightsSumToTwoToThirty() {
    long[] wt = {100, -80589, 50000, 18935, -18935, 18935, 18935, 18935, 18935};
    assertThat(Words.words(100L, wt, 70000000L, 69L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS);
    wt[1]++;
    Graph g = Words.words(100L, wt, 70000000L, 69L);
    assertThat(g).isNotNull();
    assertThat(g.id)
        .isEqualTo("words(90,{100,-80588,50000,18935,-18935,18935,18935,18935,18935},70000000,69)");
    assertThat(g.n).isEqualTo(90L);
  }

  @Test
  @DisplayName(
      "find_word returns the vertex for a present word and calls back with neighbours of an absent one")
  void shouldFindOrEnumerateNeighboursWhenLookingUpWords() {
    Words.words(0L, null, 0L, 69L);
    assertThat(Words.findWord("words", null).name).isEqualTo("words");
    List<String> seen = new ArrayList<>();
    assertThat(Words.findWord("zzzzz", v -> seen.add(v.name))).isNull();
    assertThat(seen).isEmpty();
    seen.clear();
    List<String> near = new ArrayList<>();
    assertThat(Words.findWord("graph", v -> near.add(v.name))).isNotNull(); // present: no callbacks
    assertThat(near).isEmpty();
  }

  @Test
  @DisplayName("every edge joins two words that differ in exactly the recorded position")
  void shouldRecordDifferingPositionWhenEdgesBuilt() {
    Graph g = Words.words(200L, null, 0L, 1L);
    for (int i = 0; i < g.n; i++) {
      for (Arc a = g.vertices[i].arcs; a != null; a = a.next) {
        String x = g.vertices[i].name;
        String y = a.tip.name;
        int diffs = 0;
        int where = -1;
        for (int k = 0; k < 5; k++) {
          if (x.charAt(k) != y.charAt(k)) {
            diffs++;
            where = k;
          }
        }
        assertThat(diffs).isEqualTo(1);
        assertThat(a.a.I).isEqualTo(where);
      }
    }
  }
}
