package com.robsartin.jsgb.basic;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BasicLinesProductTest {

  @Test
  @DisplayName("lines of a 3x3 grid has 12 line vertices and restores the original arcs")
  void shouldBuildLineGraphWhenGridGiven() {
    Graph grid = Basic.board(3L, 3L, 0L, 0L, 1L, 0L, 0L);
    Graph g = Basic.lines(grid, 0L);
    assertThat(g.id).isEqualTo("lines(board(3,3,0,0,1,0,0),0)");
    assertThat(g.n).isEqualTo(12L);
    assertThat(g.m).isEqualTo(44L);
    assertThat(g.vertices[5].name).isEqualTo("1.0--2.0");
    assertThat(g.vertices[5].u.V().name).isEqualTo("1.0");
    assertThat(g.vertices[5].w.A().mate.tip).isSameAs(g.vertices[5].u.V());
    for (int i = 0; i < grid.n; i++) {
      assertThat(grid.vertices[i].z.ref).isNull(); // map cleared
    }
  }

  @Test
  @DisplayName("lines of a directed graph uses -> in names")
  void shouldUseArrowWhenDirected() {
    Graph g = Basic.lines(Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 1L), 1L);
    assertThat(g.n).isEqualTo(2L);
    assertThat(g.vertices[0].name).isEqualTo("1->2");
    assertThat(g.vertices[1].name).isEqualTo("0->1");
    assertThat(g.vertices[1].arcs.tip).isSameAs(g.vertices[0]);
  }

  @Test
  @DisplayName("product prints the product type as 0, 1 or 2 in its id")
  void shouldEncodeTypeWhenProductBuilt() {
    Graph a = Basic.board(2L, 0L, 0L, 0L, 1L, 0L, 0L);
    Graph b = Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 0L);
    Graph g = Basic.product(a, b, Basic.STRONG, 0L);
    assertThat(g.id).isEqualTo("product(board(2,0,0,0,1,0,0),board(3,0,0,0,1,0,0),2,0)");
    assertThat(g.n).isEqualTo(6L);
    assertThat(g.m).isEqualTo(22L);
    assertThat(g.vertices[4].name).isEqualTo("1,1");
    assertThat(Basic.product(null, b, 0L, 0L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.MISSING_OPERAND);
  }

  @Test
  @DisplayName("lines panics when asked for the undirected line graph of a directed graph")
  void shouldPanicWhenUndirectedLinesGivenDirectedGraph() {
    Graph directedPath = Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 1L);
    assertThat(Basic.lines(directedPath, 0L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.INVALID_OPERAND);
  }
}
