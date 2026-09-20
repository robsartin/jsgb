package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.basic.Basic;
import com.robsartin.jsgb.dijk.Buckets128;
import com.robsartin.jsgb.dijk.DList;
import com.robsartin.jsgb.dijk.Dijkstra;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Vertex;
import com.robsartin.jsgb.miles.Miles;
import com.robsartin.jsgb.plane.Plane;
import com.robsartin.jsgb.roget.Roget;
import com.robsartin.jsgb.words.Words;
import java.io.PrintStream;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Every case reproduces a run of the C library recorded in oracle_inc3a.out. */
class OracleInc3aTest {

  @Test
  @DisplayName("words graphs print exactly as the C, including the bad_specs panic")
  void shouldMatchOracleWhenWordsPrinted() {
    long[] wt1 = {1, 1, 1, 1, 1, 1, 1, 1, 1};
    long[] bad = {100, -80589, 50000, 18935, -18935, 18935, 18935, 18935, 18935};
    assertThat(
            Oracle.capture(ps -> TestSample.printSample(Words.words(50L, null, 1000L, 1L), 3, ps)))
        .isEqualTo(Oracle.inc3a("words_top"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Words.words(20L, wt1, 0L, 7L), 0, ps)))
        .isEqualTo(Oracle.inc3a("words_equal"));
    assertThat(
            Oracle.capture(
                ps -> TestSample.printSample(Words.words(100L, bad, 70000000L, 69L), 5, ps)))
        .isEqualTo(Oracle.inc3a("words_bad"));
  }

  @Test
  @DisplayName("find_word prints exactly what the C harness printed")
  void shouldMatchOracleWhenFindWordProbed() {
    Words.words(5757L, null, 0L, 69L);
    String out =
        Oracle.capture(
            ps -> {
              Consumer<Vertex> pr = v -> ps.print(v.name + " ");
              Vertex v = Words.findWord("words", null);
              ps.print((v == null ? "NULL" : v.name) + "\n");
              v = Words.findWord("zzzzz", pr);
              ps.print("|" + (v == null ? "NULL" : v.name) + "\n");
              v = Words.findWord("graph", pr);
              ps.print("|" + (v == null ? "NULL" : v.name) + "\n");
            });
    assertThat(out).isEqualTo(Oracle.inc3a("find_word"));
  }

  @Test
  @DisplayName("find_word's neighbour callback prints exactly what the C harness printed")
  void shouldMatchOracleWhenFindWordEnumeratesNeighbours() {
    Words.words(5757L, null, 0L, 69L);
    String out =
        Oracle.capture(
            ps -> {
              Consumer<Vertex> pr = v -> ps.print(v.name + " ");
              Vertex v = Words.findWord("zords", pr);
              ps.print("|" + (v == null ? "NULL" : v.name) + "\n");
              v = Words.findWord("qqqqq", pr);
              ps.print("|" + (v == null ? "NULL" : v.name) + "\n");
            });
    assertThat(out).isEqualTo(Oracle.inc3a("find_word_neighbours"));
  }

  @Test
  @DisplayName("roget graphs print exactly as the C")
  void shouldMatchOracleWhenRogetPrinted() {
    assertThat(
            Oracle.capture(ps -> TestSample.printSample(Roget.roget(100L, 2L, 500L, 3L), 10, ps)))
        .isEqualTo(Oracle.inc3a("roget_small"));
    assertThat(
            Oracle.capture(ps -> TestSample.printSample(Roget.roget(1022L, 0L, 0L, 0L), 1000, ps)))
        .isEqualTo(Oracle.inc3a("roget_full"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Roget.roget(0L, 0L, 0L, 5L), 0, ps)))
        .isEqualTo(Oracle.inc3a("roget_default"));
  }

  @Test
  @DisplayName("miles graphs print exactly as the C")
  void shouldMatchOracleWhenMilesPrinted() {
    assertThat(
            Oracle.capture(
                ps -> TestSample.printSample(Miles.miles(50L, 0L, 0L, 0L, 0L, 10L, 0L), 0, ps)))
        .isEqualTo(Oracle.inc3a("miles_span_default"));
    assertThat(
            Oracle.capture(
                ps -> TestSample.printSample(Miles.miles(128L, 0L, 0L, 0L, 300L, 0L, 0L), 5, ps)))
        .isEqualTo(Oracle.inc3a("miles_dist300"));
    assertThat(
            Oracle.capture(
                ps -> TestSample.printSample(Miles.miles(30L, 100L, 100L, 1L, 0L, 3L, 2L), 2, ps)))
        .isEqualTo(Oracle.inc3a("miles_weighted"));
    com.robsartin.jsgb.graph.Graph g = Miles.miles(10L, 0L, 0L, 0L, 0L, 0L, 4L);
    assertThat(Miles.milesDistance(g.vertices[0], g.vertices[1]))
        .isEqualTo(Oracle.inc3aReturn("miles_distance"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(g, 1, ps)))
        .isEqualTo(Oracle.inc3a("miles_distance"));
    assertThat(
            Oracle.capture(
                ps -> TestSample.printSample(Miles.miles(10L, 200000L, 0L, 0L, 0L, 0L, 1L), 0, ps)))
        .isEqualTo(Oracle.inc3a("miles_bad"));
  }

  @Test
  @DisplayName("plane graphs and delaunay print exactly as the C")
  void shouldMatchOracleWhenPlaneGraphsPrinted() {
    assertThat(
            Oracle.capture(
                ps -> TestSample.printSample(Plane.plane(10L, 0L, 0L, 0L, 0L, 1L), 0, ps)))
        .isEqualTo(Oracle.inc3a("plane_small"));
    assertThat(
            Oracle.capture(
                ps -> TestSample.printSample(Plane.plane(20L, 100L, 100L, 1L, 0L, 5L), 20, ps)))
        .isEqualTo(Oracle.inc3a("plane_inf"));
    assertThat(
            Oracle.capture(
                ps -> TestSample.printSample(Plane.plane(30L, 500L, 500L, 1L, 300L, 7L), 3, ps)))
        .isEqualTo(Oracle.inc3a("plane_prob"));
    assertThat(
            Oracle.capture(
                ps -> TestSample.printSample(Plane.plane(1L, 0L, 0L, 0L, 0L, 1L), 0, ps)))
        .isEqualTo(Oracle.inc3a("plane_bad"));
    com.robsartin.jsgb.graph.Graph g = Plane.plane(6L, 100L, 100L, 0L, 0L, 3L);
    String out =
        Oracle.capture(
            ps -> {
              Plane.delaunay(
                  g,
                  (u, v) ->
                      ps.print(
                          (u == null ? "INF" : u.name)
                              + "-"
                              + (v == null ? "INF" : v.name)
                              + "\n"));
              TestSample.printSample(g, 2, ps);
            });
    assertThat(out).isEqualTo(Oracle.inc3a("delaunay"));
    assertThat(
            Oracle.capture(
                ps -> TestSample.printSample(Plane.planeMiles(20L, 0L, 0L, 0L, 0L, 0L, 1L), 0, ps)))
        .isEqualTo(Oracle.inc3a("plane_miles_small"));
    assertThat(
            Oracle.capture(
                ps ->
                    TestSample.printSample(
                        Plane.planeMiles(40L, 0L, 0L, 0L, 1L, 20000L, 9L), 40, ps)))
        .isEqualTo(Oracle.inc3a("plane_miles_prob"));
  }

  private static String dijkstraCase(Runnable body) {
    PrintStream saved = Dijkstra.out;
    try {
      return Oracle.capture(
          ps -> {
            Dijkstra.out = ps;
            body.run();
          });
    } finally {
      Dijkstra.out = saved;
      Dijkstra.queue = new DList();
      Gb.verbose = 0;
    }
  }

  @Test
  @DisplayName("dijkstra and print_dijkstra_result print exactly as the C")
  void shouldMatchOracleWhenShortestPathsPrinted() {
    com.robsartin.jsgb.graph.Graph roget = Roget.roget(1022L, 0L, 0L, 0L);
    assertThat(
            dijkstraCase(
                () -> {
                  long d = Dijkstra.dijkstra(roget.vertices[0], roget.vertices[2], roget, null);
                  Dijkstra.out.print("return=" + d + "\n");
                  Dijkstra.printDijkstraResult(roget.vertices[2]);
                }))
        .isEqualTo(Oracle.inc3a("dijkstra_roget"));
    assertThat(
            dijkstraCase(
                () -> {
                  long d = Dijkstra.dijkstra(roget.vertices[4], roget.vertices[900], roget, null);
                  Dijkstra.out.print("return=" + d + "\n");
                  Dijkstra.printDijkstraResult(roget.vertices[900]);
                }))
        .isEqualTo(Oracle.inc3a("dijkstra_roget_far"));
    assertThat(
            dijkstraCase(
                () -> {
                  Dijkstra.queue = new Buckets128();
                  long d = Dijkstra.dijkstra(roget.vertices[4], roget.vertices[900], roget, null);
                  Dijkstra.out.print("return=" + d + "\n");
                  Dijkstra.printDijkstraResult(roget.vertices[900]);
                }))
        .isEqualTo(Oracle.inc3a("dijkstra_128"));
    com.robsartin.jsgb.graph.Graph path = Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 1L);
    assertThat(
            dijkstraCase(
                () -> {
                  long d = Dijkstra.dijkstra(path.vertices[2], path.vertices[0], path, null);
                  Dijkstra.out.print("return=" + d + "\n");
                  Dijkstra.printDijkstraResult(path.vertices[0]);
                }))
        .isEqualTo(Oracle.inc3a("dijkstra_unreachable"));
    com.robsartin.jsgb.graph.Graph m1 = Miles.miles(20L, 0L, 0L, 0L, 0L, 0L, 1L);
    assertThat(
            dijkstraCase(
                () -> {
                  Gb.verbose = 1;
                  long d = Dijkstra.dijkstra(m1.vertices[0], m1.vertices[19], m1, v -> v.x.I / 4);
                  Gb.verbose = 0;
                  Dijkstra.out.print("return=" + d + "\n");
                  Dijkstra.printDijkstraResult(m1.vertices[19]);
                }))
        .isEqualTo(Oracle.inc3a("dijkstra_heuristic"));
    com.robsartin.jsgb.graph.Graph m2 = Miles.miles(20L, 0L, 0L, 0L, 0L, 0L, 1L);
    assertThat(
            dijkstraCase(
                () -> {
                  Gb.verbose = 1;
                  long d = Dijkstra.dijkstra(m2.vertices[3], m2.vertices[7], m2, null);
                  Gb.verbose = 0;
                  Dijkstra.out.print("return=" + d + "\n");
                  Dijkstra.printDijkstraResult(m2.vertices[7]);
                }))
        .isEqualTo(Oracle.inc3a("dijkstra_verbose_plain"));
  }
}
