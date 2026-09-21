package com.robsartin.jsgb.demo;

import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import com.robsartin.jsgb.words.Words;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Port of {@code word_components}: builds the {@code words} graph and finds its connected
 * components with a size-weighted union-find, printing each word's growing component as it is
 * discovered and, at the end, the non-isolated words that never joined the giant component.
 *
 * <p>Slots: {@code link} = {@code z.V}, {@code master} = {@code y.V}, {@code size} = {@code x.I},
 * {@code weight} = {@code u.I}.
 */
public final class WordComponents {

  private WordComponents() {}

  /** Entry point: runs with the real process streams and exits with the C's return value. */
  public static void main(String[] args) {
    System.exit(
        Jsgb.exitStatus(
            run(args, new CStdin(System.in), Jsgb.stdout(), Jsgb.stderr(), Path.of(""))));
  }

  /** Runs the program; {@code args} is ignored, matching the C's {@code main()}. */
  public static int run(String[] args, CStdin in, PrintStream out, PrintStream err, Path workDir) {
    Graph g = Words.words(0L, null, 0L, 0L);
    out.print("Component analysis of " + g.id + "\n");

    long n = 0;
    long isol = 0;
    long comp = 0;
    long m = 0;

    for (int i = 0; i < g.n; i++) {
      Vertex v = g.vertices[i];
      n++;
      out.print(String.format(Locale.ROOT, "%4d: %5d %s", n, v.u.I, v.name));

      v.z.V(v);
      v.y.V(v);
      v.x.I = 1;
      isol++;
      comp++;

      Arc a = v.arcs;
      while (a != null && a.tip.index > v.index) {
        a = a.next;
      }
      if (a == null) {
        out.print("[1]");
      } else {
        long c = 0;
        for (; a != null; a = a.next) {
          Vertex u = a.tip;
          m++;
          u = u.y.V();
          if (u != v.y.V()) {
            Vertex w = v.y.V();
            Vertex t;
            if (u.x.I < w.x.I) {
              if (c++ > 0) {
                out.print(
                    String.format(Locale.ROOT, "%s %s[%d]", c == 2 ? " with" : ",", u.name, u.x.I));
              }
              w.x.I += u.x.I;
              if (u.x.I == 1) {
                isol--;
              }
              for (t = u.z.V(); t != u; t = t.z.V()) {
                t.y.V(w);
              }
              u.y.V(w);
            } else {
              if (c++ > 0) {
                out.print(
                    String.format(Locale.ROOT, "%s %s[%d]", c == 2 ? " with" : ",", w.name, w.x.I));
              }
              if (u.x.I == 1) {
                isol--;
              }
              u.x.I += w.x.I;
              if (w.x.I == 1) {
                isol--;
              }
              for (t = w.z.V(); t != w; t = t.z.V()) {
                t.y.V(u);
              }
              w.y.V(u);
            }
            t = u.z.V();
            u.z.V(w.z.V());
            w.z.V(t);
            comp--;
          }
        }
        out.print(" in " + v.y.V().name + "[" + v.y.V().x.I + "]");
      }
      out.print(String.format(Locale.ROOT, "; c=%d,i=%d,m=%d\n", comp, isol, m));
    }

    out.print("\nThe following non-isolated words didn't join the giant component:\n");
    for (int i = 0; i < g.n; i++) {
      Vertex v = g.vertices[i];
      if (v.y.V() == v && v.x.I > 1 && v.x.I + v.x.I < g.n) {
        long c = 1;
        out.print(v.name);
        for (Vertex u = v.z.V(); u != v; u = u.z.V()) {
          if (c++ == 12) {
            out.print("\n");
            c = 1;
          }
          out.print(" " + u.name);
        }
        out.print("\n");
      }
    }

    return 0;
  }
}
