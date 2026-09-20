package com.robsartin.jsgb.dijk;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.basic.Basic;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DijkstraTest {

  private ByteArrayOutputStream sink;

  @BeforeEach
  void captureOutput() {
    sink = new ByteArrayOutputStream();
    Dijkstra.out = new PrintStream(sink, true, StandardCharsets.ISO_8859_1);
    Dijkstra.queue = new DList();
    Gb.verbose = 0;
  }

  @AfterEach
  void restore() {
    Dijkstra.out = new PrintStream(System.out, true, StandardCharsets.ISO_8859_1);
  }

  private String printed() {
    return sink.toString(StandardCharsets.ISO_8859_1);
  }

  @Test
  @DisplayName("shortest path on a 4x4 grid with unit lengths is the Manhattan distance")
  void shouldFindManhattanDistanceWhenGridSearched() {
    Graph g = Basic.board(4L, 4L, 0L, 0L, 1L, 0L, 0L);
    assertThat(Dijkstra.dijkstra(g.vertices[0], g.vertices[15], g, null)).isEqualTo(6L);
    Dijkstra.printDijkstraResult(g.vertices[15]);
    String[] lines = printed().split("\n");
    assertThat(lines).hasSize(7);
    assertThat(lines[0]).isEqualTo("         0 0.0");
    assertThat(lines[6]).isEqualTo("         6 3.3");
    assertThat(g.vertices[15].y.V()).isNotNull(); // backlink chain restored
  }

  @Test
  @DisplayName("an unreachable target returns -1 and print_dijkstra_result apologises")
  void shouldReturnMinusOneWhenTargetUnreachable() {
    Graph g = Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 1L);
    assertThat(Dijkstra.dijkstra(g.vertices[2], g.vertices[0], g, null)).isEqualTo(-1L);
    Dijkstra.printDijkstraResult(g.vertices[0]);
    assertThat(printed()).isEqualTo("Sorry, 0 is unreachable.\n");
  }

  @Test
  @DisplayName("the 128-bucket queue gives the same answer as the list queue")
  void shouldAgreeWhenQueueImplementationSwapped() {
    Graph g = Basic.board(5L, 5L, 0L, 0L, 1L, 0L, 0L);
    long a = Dijkstra.dijkstra(g.vertices[0], g.vertices[24], g, null);
    Dijkstra.queue = new Buckets128();
    long b = Dijkstra.dijkstra(g.vertices[0], g.vertices[24], g, null);
    assertThat(a).isEqualTo(8L);
    assertThat(b).isEqualTo(8L);
  }

  @Test
  @DisplayName("verbose mode traces each settled vertex with the heuristic value in brackets")
  void shouldTraceWhenVerboseAndHeuristicGiven() {
    Graph g = Basic.board(2L, 0L, 0L, 0L, 1L, 0L, 0L);
    Gb.verbose = 1;
    assertThat(Dijkstra.dijkstra(g.vertices[0], g.vertices[1], g, v -> 5L)).isEqualTo(1L);
    assertThat(printed()).isEqualTo("Distances from 0 [5]:\n 1 to 1 [5] via 0\n");
  }
}
