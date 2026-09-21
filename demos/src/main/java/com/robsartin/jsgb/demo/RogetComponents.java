package com.robsartin.jsgb.demo;

import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import com.robsartin.jsgb.roget.Roget;
import com.robsartin.jsgb.save.Save;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Port of {@code roget_components}: builds (or restores) a Roget's-Thesaurus cross-reference graph,
 * finds its strongly connected components with Tarjan's algorithm, then lists the arcs that cross
 * from one component to another.
 *
 * <p>Slots: {@code rank} = {@code z.I}, {@code parent} = {@code y.V}, {@code untagged} = {@code
 * x.A}, {@code link} = {@code w.V}, {@code min} = {@code v.V}, {@code arc_from} = {@code x.V} (the
 * reference half of the {@code untagged} slot, reused once the traversal phase is over, exactly as
 * the C aliases the same union member for both), {@code infinity} = {@code g.n}, {@code cat_no} =
 * {@code u.I}.
 */
public final class RogetComponents {

  private RogetComponents() {}

  /** Entry point: runs with the real process streams and exits with the C's return value. */
  public static void main(String[] args) {
    System.exit(
        Jsgb.exitStatus(
            run(args, new CStdin(System.in), Jsgb.stdout(), Jsgb.stderr(), Path.of(""))));
  }

  /** Runs the program: parses arguments, builds or restores the graph, then prints its SCCs. */
  public static int run(String[] args, CStdin in, PrintStream out, PrintStream err, Path workDir) {
    long n = 0;
    long d = 0;
    long p = 0;
    long s = 0;
    String filename = null;

    for (int i = args.length - 1; i >= 0; i--) {
      String arg = args[i];
      Long value;
      if ((value = Scan.scan(arg, "-n")) != null) {
        n = value;
      } else if ((value = Scan.scan(arg, "-d")) != null) {
        d = value;
      } else if ((value = Scan.scan(arg, "-p")) != null) {
        p = value;
      } else if ((value = Scan.scan(arg, "-s")) != null) {
        s = value;
      } else if (arg.startsWith("-g")) {
        filename = arg.substring(2);
      } else {
        err.print("Usage: roget_components [-nN][-dN][-pN][-sN][-gfoo]\n");
        return -2;
      }
    }

    Graph g =
        filename != null
            ? Save.restoreGraph(workDir.resolve(filename).toString())
            : Roget.roget(n, d, p, s);
    if (g == null) {
      err.print(
          String.format(
              Locale.ROOT, "Sorry, can't create the graph! (error code %d)\n", Gb.panicCode));
      return -1;
    }
    out.print("Reachability analysis of " + g.id + "\n\n");

    Vertex settledStack = findStrongComponents(g, filename, out);
    printLinksBetweenComponents(g, filename, out, settledStack);
    return 0;
  }

  /** {@code specs(v)}: the numeric half printed before {@code v.name}. */
  private static long specNum(Vertex v, String filename) {
    return filename != null ? v.index + 1L : v.u.I;
  }

  /**
   * Sections 10-16: the non-recursive Tarjan's-algorithm walk that discovers and prints each strong
   * component as its root is popped from the active stack. Returns the head of {@code
   * settledStack}, the {@code w} (link)-threaded list of every vertex in the order it settled, for
   * {@link #printLinksBetweenComponents}.
   */
  private static Vertex findStrongComponents(Graph g, String filename, PrintStream out) {
    for (int i = (int) g.n - 1; i >= 0; i--) {
      Vertex vertex = g.vertices[i];
      vertex.z.I = 0;
      vertex.x.A(vertex.arcs);
    }
    long nn = 0;
    Vertex activeStack = null;
    Vertex settledStack = null;

    for (int i = 0; i < g.n; i++) {
      Vertex vv = g.vertices[i];
      if (vv.z.I != 0) {
        continue;
      }
      Vertex v = vv;
      v.y.V(null);
      v.z.I = ++nn;
      v.w.V(activeStack);
      activeStack = v;
      v.v.V(v);

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
            v.v.V(v);
          }
        } else {
          Vertex u = v.y.V();
          if (v.v.V() == v) {
            Vertex t = activeStack;
            activeStack = v.w.V();
            v.w.V(settledStack);
            settledStack = t;
            out.print(
                String.format(
                    Locale.ROOT, "Strong component `%d %s'", specNum(v, filename), v.name));
            if (t == v) {
              out.print("\n");
            } else {
              out.print(" also includes:\n");
              while (t != v) {
                out.print(
                    String.format(
                        Locale.ROOT,
                        " %d %s (from %d %s; ..to %d %s)\n",
                        specNum(t, filename),
                        t.name,
                        specNum(t.y.V(), filename),
                        t.y.V().name,
                        specNum(t.v.V(), filename),
                        t.v.V().name));
                t.z.I = g.n;
                t.y.V(v);
                t = t.w.V();
              }
            }
            v.z.I = g.n;
            v.y.V(v);
          } else if (v.v.V().z.I < u.v.V().z.I) {
            u.v.V(v.v.V());
          }
          v = u;
        }
      } while (v != null);
    }
    return settledStack;
  }

  /** Section 17: reports every arc whose endpoints settled into two different components. */
  private static void printLinksBetweenComponents(
      Graph g, String filename, PrintStream out, Vertex settledStack) {
    out.print("\nLinks between components:\n");
    for (Vertex v = settledStack; v != null; v = v.w.V()) {
      Vertex u = v.y.V();
      u.x.V(u);
      for (Arc a = v.arcs; a != null; a = a.next) {
        Vertex w = a.tip.y.V();
        if (w.x.V() != u) {
          w.x.V(u);
          out.print(
              String.format(
                  Locale.ROOT,
                  "%d %s -> %d %s (e.g., %d %s -> %d %s)\n",
                  specNum(u, filename),
                  u.name,
                  specNum(w, filename),
                  w.name,
                  specNum(v, filename),
                  v.name,
                  specNum(a.tip, filename),
                  a.tip.name));
        }
      }
    }
  }
}
