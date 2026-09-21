package com.robsartin.jsgb.demo;

import com.robsartin.jsgb.books.Books;
import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import com.robsartin.jsgb.io.GbIo;
import com.robsartin.jsgb.save.Save;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Port of {@code book_components}: builds (or restores) a novel's character-encounter graph, then
 * finds its biconnected components with the Hopcroft-Tarjan algorithm, printing each bicomponent
 * and, between them, the articulation point that joins it to the rest of the graph.
 *
 * <p>Slots used during the traversal: {@code rank} = {@code z.I}, {@code parent} = {@code y.V},
 * {@code untagged} = {@code x.A}, {@code link} = {@code w.V}, {@code min} = {@code v.V}. A vertex
 * built (not restored) also carries its two-letter data-file code in {@code short_code} ({@code
 * u.I}), its description in {@code desc} ({@code z.S}), and its in-range/out-of-range chapter
 * counts in {@code in_count} ({@code y.I}) and {@code out_count} ({@code x.I}) — these overlap the
 * traversal slots but are read only before the traversal begins, exactly as the C reuses the same
 * union members in separate phases.
 */
public final class BookComponents {

  private BookComponents() {}

  /** Entry point: runs with the real process streams and exits with the C's return value. */
  public static void main(String[] args) {
    System.exit(
        Jsgb.exitStatus(
            run(args, new CStdin(System.in), Jsgb.stdout(), Jsgb.stderr(), Path.of(""))));
  }

  /**
   * Runs the program: parses arguments, builds or restores the graph, then prints its bicomponents.
   */
  public static int run(String[] args, CStdin in, PrintStream out, PrintStream err, Path workDir) {
    Gb.verbose = 0;
    String title = "anna";
    long n = 0;
    long x = 0;
    long f = 0;
    long l = 0;
    long i = 1;
    long o = 1;
    long s = 0;
    String filename = null;

    for (int idx = args.length - 1; idx >= 0; idx--) {
      String arg = args[idx];
      Long value;
      if (arg.startsWith("-t")) {
        title = arg.substring(2);
      } else if ((value = Scan.scan(arg, "-n")) != null) {
        n = value;
      } else if ((value = Scan.scan(arg, "-x")) != null) {
        x = value;
      } else if ((value = Scan.scan(arg, "-f")) != null) {
        f = value;
      } else if ((value = Scan.scan(arg, "-l")) != null) {
        l = value;
      } else if ((value = Scan.scan(arg, "-i")) != null) {
        i = value;
      } else if ((value = Scan.scan(arg, "-o")) != null) {
        o = value;
      } else if ((value = Scan.scan(arg, "-s")) != null) {
        s = value;
      } else if (arg.equals("-v")) {
        Gb.verbose = 1;
      } else if (arg.equals("-V")) {
        Gb.verbose = 2;
      } else if (arg.startsWith("-g")) {
        filename = arg.substring(2);
      } else {
        err.print(
            "Usage: book_components [-ttitle][-nN][-xN][-fN][-lN][-iN][-oN][-sN][-v][-gfoo]\n");
        return -2;
      }
    }
    if (filename != null) {
      Gb.verbose = 0;
    }

    Graph g =
        filename != null
            ? Save.restoreGraph(workDir.resolve(filename).toString())
            : Books.book(title, n, x, f, l, i, o, s);
    if (g == null) {
      err.print(
          String.format(
              Locale.ROOT, "Sorry, can't create the graph! (error code %d)\n", Gb.panicCode));
      return -1;
    }
    out.print(String.format(Locale.ROOT, "Biconnectivity analysis of %s\n\n", g.id));

    if (Gb.verbose != 0) {
      printVerboseListing(g, filename, i, o, out);
    }

    findBiconnectedComponents(g, filename, out);
    return 0;
  }

  /** Section 5: the {@code -v}/{@code -V} pre-traversal listing of every vertex. */
  private static void printVerboseListing(
      Graph g, String filename, long inWeight, long outWeight, PrintStream out) {
    for (int idx = 0; idx < g.n; idx++) {
      Vertex v = g.vertices[idx];
      if (Gb.verbose == 1) {
        out.print(String.format(Locale.ROOT, "%s=%s\n", vertexName(v, filename), v.name));
      } else {
        out.print(
            String.format(
                Locale.ROOT,
                "%s=%s, %s [weight %d]\n",
                vertexName(v, filename),
                v.name,
                v.z.S(),
                inWeight * v.y.I + outWeight * v.x.I));
      }
    }
    out.print("\n");
  }

  /** {@code vertex_name(v,i)}: the restored name, or the two-letter data-file code. */
  static String vertexName(Vertex v, String filename) {
    if (filename != null) {
      return v.name;
    }
    return "" + GbIo.imapChr(v.u.I / 36) + GbIo.imapChr(v.u.I % 36);
  }

  /**
   * Sections 12-19: the non-recursive Hopcroft-Tarjan walk that discovers and prints each
   * bicomponent as its root is popped from the active stack, interleaved with the articulation
   * point that follows it.
   */
  private static void findBiconnectedComponents(Graph g, String filename, PrintStream out) {
    Vertex dummy = Gb.allocAuxVertices(1)[0];
    dummy.z.I = 0;
    for (int idx = 0; idx < g.n; idx++) {
      Vertex v = g.vertices[idx];
      v.z.I = 0;
      v.x.A(v.arcs);
    }
    long nn = 0;
    Vertex activeStack = null;
    Vertex articPt = null;

    for (int idx = 0; idx < g.n; idx++) {
      Vertex vv = g.vertices[idx];
      if (vv.z.I != 0) {
        continue;
      }
      Vertex v = vv;
      v.y.V(dummy);
      v.z.I = ++nn;
      v.w.V(activeStack);
      activeStack = v;
      v.v.V(v.y.V());

      do {
        Arc a = v.x.A();
        if (a != null) {
          Vertex u = a.tip;
          v.x.A(a.next);
          if (u.z.I != 0) {
            if (u.z.I < v.v.V().z.I) {
              v.v.V(u);
            }
          } else {
            u.y.V(v);
            v = u;
            v.z.I = ++nn;
            v.w.V(activeStack);
            activeStack = v;
            v.v.V(v.y.V());
          }
        } else {
          Vertex u = v.y.V();
          if (v.v.V() == u) {
            if (u == dummy) {
              if (articPt != null) {
                out.print(
                    String.format(
                        Locale.ROOT,
                        " and %s (this ends a connected component of the graph)\n",
                        vertexName(articPt, filename)));
              } else {
                out.print(
                    String.format(Locale.ROOT, "Isolated vertex %s\n", vertexName(v, filename)));
              }
              activeStack = null;
              articPt = null;
            } else {
              if (articPt != null) {
                out.print(
                    String.format(
                        Locale.ROOT,
                        " and articulation point %s\n",
                        vertexName(articPt, filename)));
              }
              Vertex t = activeStack;
              activeStack = v.w.V();
              out.print(String.format(Locale.ROOT, "Bicomponent %s", vertexName(v, filename)));
              if (t == v) {
                out.print("\n");
              } else {
                out.print(" also includes:\n");
                while (t != v) {
                  out.print(
                      String.format(
                          Locale.ROOT,
                          " %s (from %s; ..to %s)\n",
                          vertexName(t, filename),
                          vertexName(t.y.V(), filename),
                          vertexName(t.v.V(), filename)));
                  t = t.w.V();
                }
              }
              articPt = u;
            }
          } else if (v.v.V().z.I < u.v.V().z.I) {
            u.v.V(v.v.V());
          }
          v = u;
        }
      } while (v != dummy);
    }
  }
}
