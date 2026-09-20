package com.robsartin.jsgb.save;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.basic.Basic;
import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SaveTest {

  @TempDir Path dir;

  private String read(Path p) throws Exception {
    return Files.readString(p, StandardCharsets.ISO_8859_1);
  }

  @Test
  @DisplayName("save_graph writes a 2x2 board exactly as the C did")
  void shouldWriteOracleFileWhenBoardSaved() throws Exception {
    Path out = dir.resolve("board.gb");
    assertThat(Save.saveGraph(Basic.board(2L, 2L, 0L, 0L, 1L, 0L, 0L), out.toString())).isZero();
    String expected =
        new String(
            getClass().getResourceAsStream("/oracle/inc2/oracle_board.gb").readAllBytes(),
            StandardCharsets.ISO_8859_1);
    assertThat(read(out)).isEqualTo(expected);
  }

  @Test
  @DisplayName("save_graph reports data hidden by Z types, as the C does for lines()")
  void shouldReportIgnoredDataWhenUtilTypesHideSlots() throws Exception {
    Graph g = Basic.lines(Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 0L), 0L);
    Path out = dir.resolve("lines.gb");
    assertThat(Save.saveGraph(g, out.toString())).isEqualTo(Save.IGNORED_DATA);
    String expected =
        new String(
            getClass().getResourceAsStream("/oracle/inc2/oracle_lines.gb").readAllBytes(),
            StandardCharsets.ISO_8859_1);
    assertThat(read(out)).isEqualTo(expected);
  }

  @Test
  @DisplayName("save then restore round-trips structure and gives restored arcs their mates")
  void shouldRoundTripWhenSavedAndRestored() throws Exception {
    Path out = dir.resolve("rt.gb");
    Graph g = Basic.board(2L, 2L, 0L, 0L, 1L, 0L, 0L);
    Save.saveGraph(g, out.toString());
    Graph r = Save.restoreGraph(out.toString());
    assertThat(r).isNotNull();
    assertThat(r.id).isEqualTo("board(2,2,0,0,1,0,0)");
    assertThat(r.n).isEqualTo(4L);
    assertThat(r.m).isEqualTo(8L);
    assertThat(r.vertices).hasSize(8);
    assertThat(r.arcBlocks().get(0)).hasSize(102);
    assertThat(r.vertices[0].name).isEqualTo("0.0");
    assertThat(r.vertices[0].x.I).isZero();
    assertThat(r.vertices[2].x.I).isEqualTo(1L);
    for (int i = 0; i < r.n; i++) {
      for (Arc a = r.vertices[i].arcs; a != null; a = a.next) {
        assertThat(a.mate).isNotNull();
        assertThat(a.mate.tip).isSameAs(r.vertices[i]);
      }
    }
  }

  @Test
  @DisplayName("restore_graph panics on a missing file and on a corrupted checksum")
  void shouldPanicWhenFileMissingOrCorrupt() throws Exception {
    assertThat(Save.restoreGraph(dir.resolve("nope.gb").toString())).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.EARLY_DATA_FAULT);
    Path out = dir.resolve("bad.gb");
    Save.saveGraph(Basic.board(2L, 0L, 0L, 0L, 1L, 0L, 0L), out.toString());
    String text = read(out).replace("* Checksum ", "* Checksum 1");
    Files.writeString(out, text, StandardCharsets.ISO_8859_1);
    assertThat(Save.restoreGraph(out.toString())).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.LATE_DATA_FAULT);
  }

  @Test
  @DisplayName("save_graph returns -1 for a null or recycled graph")
  void shouldReturnMinusOneWhenGraphUnusable() {
    assertThat(Save.saveGraph(null, dir.resolve("x.gb").toString())).isEqualTo(-1L);
    Graph g = Gb.newGraph(1L);
    Gb.recycle(g);
    assertThat(Save.saveGraph(g, dir.resolve("x.gb").toString())).isEqualTo(-1L);
  }

  @Test
  @DisplayName("long strings wrap at column 78 with a backslash and restore intact")
  void shouldWrapAndRestoreWhenNameIsLong() throws Exception {
    Graph g = Gb.newGraph(1L);
    g.id = "long";
    g.vertices[0].name = "x".repeat(200);
    Path out = dir.resolve("long.gb");
    assertThat(Save.saveGraph(g, out.toString())).isZero();
    String text = read(out);
    assertThat(text).contains("\\\n");
    for (String line : text.split("\n")) {
      assertThat(line.length()).isLessThanOrEqualTo(79);
    }
    Graph r = Save.restoreGraph(out.toString());
    assertThat(r.vertices[0].name).isEqualTo("x".repeat(200));
  }
}
