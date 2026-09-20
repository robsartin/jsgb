package com.robsartin.jsgb.graph;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HashTest {

  @BeforeEach
  void resetKernel() {
    Gb.reset();
  }

  @Test
  @DisplayName("bucket index matches the C hash for known names in a 5757-vertex graph")
  void shouldMatchCBucketsWhenHashingKnownNames() {
    Gb.newGraph(5757L);
    assertThat(Gb.hashBucket("aargh")).isEqualTo(HASH_AARGH);
    assertThat(Gb.hashBucket("words")).isEqualTo(HASH_WORDS);
    assertThat(Gb.hashBucket("")).isZero();
  }

  @Test
  @DisplayName("hash_setup then hash_lookup finds every named vertex and nothing else")
  void shouldFindVerticesWhenLookedUpAfterSetup() {
    Graph g = Gb.newGraph(4L);
    String[] names = {"alpha", "beta", "gamma", "delta"};
    for (int i = 0; i < 4; i++) {
      g.vertices[i].name = names[i];
    }
    Gb.hashSetup(g);
    assertThat(g.utilTypes).isEqualTo("VVZZZZZZZZZZZZ");
    for (int i = 0; i < 4; i++) {
      assertThat(Gb.hashLookup(names[i], g)).isSameAs(g.vertices[i]);
    }
    assertThat(Gb.hashLookup("epsilon", g)).isNull();
  }

  @Test
  @DisplayName("hash_lookup on a null or empty graph returns null")
  void shouldReturnNullWhenGraphMissingOrEmpty() {
    assertThat(Gb.hashLookup("x", null)).isNull();
    Graph empty = Gb.newGraph(0L);
    assertThat(Gb.hashLookup("x", empty)).isNull();
  }

  @Test
  @DisplayName("hash_setup leaves the current graph unchanged")
  void shouldRestoreCurrentGraphWhenSetupFinishes() {
    Graph g = Gb.newGraph(2L);
    Graph other = Gb.newGraph(2L);
    Gb.hashSetup(g);
    assertThat(Gb.curGraph()).isSameAs(other);
  }

  // Values computed by the C hash (gb_graph.w section 45) with n = 5757.
  private static final int HASH_AARGH = 4279;
  private static final int HASH_WORDS = 1924;
}
