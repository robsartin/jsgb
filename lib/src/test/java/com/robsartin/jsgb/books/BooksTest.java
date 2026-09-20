package com.robsartin.jsgb.books;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BooksTest {

  @Test
  @DisplayName("book(homer,500,400,2,12,10000,-123456,789) is stanza 5 with Eetion at vertex 81")
  void shouldMatchStanzaWhenSampleParametersGiven() {
    Graph g = Books.book("homer", 500L, 400L, 2L, 12L, 10000L, -123456L, 789L);
    assertThat(g.id).isEqualTo("book(\"homer\",500,400,2,12,10000,-123456,789)");
    assertThat(g.n).isEqualTo(100L);
    assertThat(g.m).isEqualTo(4L);
    assertThat(g.utilTypes).isEqualTo("IZZIISIZZZZZZZ");
    assertThat(g.vertices[81].name).isEqualTo("Eetion");
    assertThat(g.vertices[81].u.I).isEqualTo(90L);
    assertThat(g.vertices[81].x.I).isEqualTo(2L);
    assertThat(g.vertices[81].y.I).isEqualTo(1L);
    assertThat(g.vertices[81].z.S()).isEqualTo("king of Cilicia, father of AH");
    assertThat(g.vertices[81].arcs.tip.name).isEqualTo("Andromache");
    assertThat(g.vertices[81].arcs.a.I).isEqualTo(6L);
    assertThat(Books.chapters).isEqualTo(24L);
  }

  @Test
  @DisplayName(
      "bi_book adds one vertex per selected chapter, named after the chapter, and marks the bipartite split")
  void shouldAddChapterVerticesWhenBipartite() {
    Graph g = Books.biBook("homer", 100L, 0L, 10L, 20L, 1L, 1L, 3L);
    assertThat(g.id).isEqualTo("bi_book(\"homer\",100,0,10,20,1,1,3)");
    assertThat(g.n).isEqualTo(111L);
    assertThat(g.uu.I).isEqualTo(100L);
    assertThat(g.utilTypes).isEqualTo("IZZIISIZIZZZZZ");
    assertThat(g.vertices[105].name).isEqualTo("15");
    assertThat(g.vertices[105].z.S()).isEmpty();
    assertThat(Books.chapName[15]).isEqualTo("15");
  }

  @Test
  @DisplayName(
      "weights beyond a million panic with bad_specs and a missing title is an early data fault")
  void shouldPanicWhenSpecsAreBad() {
    assertThat(Books.book("huck", 0L, 0L, 0L, 0L, 2000000L, 0L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS);
    assertThat(Books.book("nosuch", 0L, 0L, 0L, 0L, 1L, 1L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.EARLY_DATA_FAULT);
  }
}
