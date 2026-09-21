package com.robsartin.jsgb.demo;

import com.robsartin.jsgb.basic.Basic;
import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import com.robsartin.jsgb.save.Save;
import java.io.PrintStream;
import java.nio.file.Path;

/**
 * Port of {@code queen}: builds the union of two chesspiece-move boards on a 3x4 board, saves it as
 * {@code queen.gb} under the working directory, and lists every vertex and its arcs.
 */
public final class Queen {

  private Queen() {}

  /** Entry point: runs with the real process streams and exits with the C's return value. */
  public static void main(String[] args) {
    System.exit(
        Jsgb.exitStatus(
            run(args, new CStdin(System.in), Jsgb.stdout(), Jsgb.stderr(), Path.of(""))));
  }

  /** Runs the program; {@code args} is ignored, matching the C's {@code main()}. */
  public static int run(String[] args, CStdin in, PrintStream out, PrintStream err, Path workDir) {
    Graph g = Basic.board(3L, 4L, 0L, 0L, -1L, 0L, 0L);
    Graph gg = Basic.board(3L, 4L, 0L, 0L, -2L, 0L, 0L);
    Graph ggg = Basic.gunion(g, gg, 0L, 0L);
    Save.saveGraph(ggg, workDir.resolve("queen.gb").toString());
    printResult(ggg, out);
    return 0;
  }

  /**
   * {@code if (ggg == null) ... else ...} of section 2: the panic line, or the banner, id, vertex
   * and arc counts, and one line per vertex plus one indented line per outgoing arc.
   */
  static void printResult(Graph ggg, PrintStream out) {
    if (ggg == null) {
      out.print("Something went wrong (panic code " + Gb.panicCode + ")!\n");
      return;
    }
    out.print("Queen Moves on a 3x4 Board\n\n");
    out.print("  The graph whose official name is\n" + ggg.id + "\n");
    out.print("  has " + ggg.n + " vertices and " + ggg.m + " arcs:\n\n");
    for (int i = 0; i < ggg.n; i++) {
      Vertex v = ggg.vertices[i];
      out.print(v.name + "\n");
      for (Arc a = v.arcs; a != null; a = a.next) {
        out.print("  -> " + a.tip.name + ", length " + a.len + "\n");
      }
    }
  }
}
