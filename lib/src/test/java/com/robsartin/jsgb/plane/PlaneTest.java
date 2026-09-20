package com.robsartin.jsgb.plane;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlaneTest {

  @Test
  @DisplayName("plane(10,0,0,0,0,1) is a 10-point Delaunay triangulation with the C's coordinates")
  void shouldTriangulateWhenTenRandomPoints() {
    Graph g = Plane.plane(10L, 0L, 0L, 0L, 0L, 1L);
    assertThat(g.id).isEqualTo("plane(10,16384,16384,0,0,1)");
    assertThat(g.n).isEqualTo(10L);
    assertThat(g.m).isEqualTo(46L);
    assertThat(g.utilTypes).isEqualTo("ZZZIIIZZZZZZZZ");
    assertThat(g.vertices[0].x.I).isEqualTo(1389L);
    assertThat(g.vertices[0].y.I).isEqualTo(14015L);
    assertThat(g.vertices[0].z.I).isEqualTo(63752060L);
    assertThat(g.vertices[0].arcs.tip.name).isEqualTo("5");
    assertThat(g.vertices[0].arcs.len).isEqualTo(13468069L);
  }

  @Test
  @DisplayName("extend adds the vertex INF joined to the hull by INFTY edges and counts it in n")
  void shouldAddInfinityVertexWhenExtendSet() {
    Graph g = Plane.plane(20L, 100L, 100L, 1L, 0L, 5L);
    assertThat(g.n).isEqualTo(21L);
    assertThat(g.vertices[20].name).isEqualTo("INF");
    assertThat(g.vertices[20].x.I).isEqualTo(-1L);
    assertThat(g.vertices[20].arcs.len).isEqualTo(Plane.INFTY);
    assertThat(Gb.extraN).isEqualTo(4L);
  }

  @Test
  @DisplayName("bad ranges and fewer than two points panic")
  void shouldPanicWhenSpecsAreBad() {
    assertThat(Plane.plane(1L, 0L, 0L, 0L, 0L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.VERY_BAD_SPECS);
    assertThat(Plane.plane(5L, 20000L, 0L, 0L, 0L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS);
  }

  @Test
  @DisplayName("delaunay reports every arc pair of the triangulation to the callback, INF as null")
  void shouldReportPairsWhenDelaunayCalledDirectly() {
    Graph g = Plane.plane(6L, 100L, 100L, 0L, 0L, 3L);
    List<String> pairs = new ArrayList<>();
    Plane.delaunay(
        g, (u, v) -> pairs.add((u == null ? "INF" : u.name) + "-" + (v == null ? "INF" : v.name)));
    assertThat(pairs).hasSize(15);
    assertThat(pairs.get(0)).isEqualTo("1-0");
    assertThat(pairs).contains("INF-1", "0-INF");
  }

  @Test
  @DisplayName("plane_miles keeps miles' slots and reads distances from the retained matrix")
  void shouldBuildFromMileageWhenPlaneMilesCalled() {
    Graph g = Plane.planeMiles(20L, 0L, 0L, 0L, 0L, 0L, 1L);
    assertThat(g.id).isEqualTo("plane_miles(20,0,0,0,0,0,1)");
    assertThat(g.n).isEqualTo(20L);
    assertThat(g.m).isEqualTo(104L);
    assertThat(g.utilTypes).isEqualTo("ZZIIIIZZZZZZZZ");
    assertThat(g.vertices[0].name).isEqualTo("Wilmington, NC");
    assertThat(g.vertices[0].arcs.tip.name).isEqualTo("Savannah, GA");
    assertThat(g.vertices[0].arcs.len).isEqualTo(277L);
  }
}
