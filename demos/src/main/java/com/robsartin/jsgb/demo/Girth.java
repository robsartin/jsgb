package com.robsartin.jsgb.demo;

import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import com.robsartin.jsgb.raman.Raman;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Port of {@code girth}: repeatedly prompts for a branching factor {@code p} and a cube root of
 * graph size {@code q}, builds the corresponding Ramanujan graph, reports theoretical bounds on its
 * diameter and girth, then finds the exact diameter and girth by breadth-first search from one
 * vertex.
 *
 * <p>The BFS (section 12) uses slots {@code link} = {@code w.V}, {@code dist} = {@code v.I}, {@code
 * back} = {@code u.V}; {@code sentinel} is an unregistered aux vertex, only ever compared and
 * stored (ADR 0021/0023).
 */
public final class Girth {

  private Girth() {}

  /** Entry point: runs with the real process streams and exits with the C's return value. */
  public static void main(String[] args) {
    System.exit(
        Jsgb.exitStatus(
            run(args, new CStdin(System.in), Jsgb.stdout(), Jsgb.stderr(), Path.of(""))));
  }

  /** Runs the program: ignores {@code args}; loops prompting for {@code p} and {@code q}. */
  public static int run(String[] args, CStdin in, PrintStream out, PrintStream err, Path workDir) {
    out.print("This program explores the girth and diameter of Ramanujan graphs.\n");
    out.print("The bipartite graphs have q^3-q vertices, and the non-bipartite\n");
    out.print("graphs have half that number. Each vertex has degree p+1.\n");
    out.print("Both p and q should be odd prime numbers;\n");
    out.print("  or you can try p = 2 with q = 17 or 43.\n");

    while (true) {
      String line = prompt(in, out, "\nChoose a branching factor, p: ");
      if (line == null) {
        break;
      }
      Long pVal = Scan.scan(line, "");
      if (pVal == null) {
        break;
      }
      long p = pVal;

      line = prompt(in, out, "OK, now choose the cube root of graph size, q: ");
      if (line == null) {
        break;
      }
      Long qVal = Scan.scan(line, "");
      if (qVal == null) {
        break;
      }
      long q = qVal;

      Graph g = Raman.raman(p, q, 0L, 0L);
      if (g == null) {
        out.print(" Sorry, I couldn't make that graph (" + panicReason() + ").\n");
      } else {
        reportAndSearch(g, p, q, out);
        Gb.recycle(g);
      }
    }
    return 0;
  }

  /**
   * {@code prompt(s)}: prints {@code s}, flushes (implicit; streams are autoflushing) and reads.
   */
  private static String prompt(CStdin in, PrintStream out, String s) {
    out.print(s);
    return in.fgets(15);
  }

  /**
   * Section 5's panic-code-to-reason table, in the C's exact ternary-chain order (the codes never
   * collide, so order does not affect correctness, but this preserves it for review).
   */
  private static String panicReason() {
    long code = Gb.panicCode;
    if (code == Gb.VERY_BAD_SPECS) {
      return "q is out of range";
    }
    if (code == Gb.VERY_BAD_SPECS + 1) {
      return "p is out of range";
    }
    if (code == Gb.BAD_SPECS + 5) {
      return "q is too big";
    }
    if (code == Gb.BAD_SPECS + 6) {
      return "p is too big";
    }
    if (code == Gb.BAD_SPECS + 1) {
      return "q isn't prime";
    }
    if (code == Gb.BAD_SPECS + 7) {
      return "p isn't prime";
    }
    if (code == Gb.BAD_SPECS + 3) {
      return "p is a multiple of q";
    }
    if (code == Gb.BAD_SPECS + 2) {
      return "q isn't compatible with p=2";
    }
    return "not enough memory";
  }

  /** Sections 10, 6, 8, 9, 7 and 12: the report on {@code g}, then the exact BFS search. */
  private static void reportAndSearch(Graph g, long p, long q, PrintStream out) {
    long n = g.n;
    boolean bipartite = n == (q + 1) * q * (q - 1);
    out.print(
        String.format(
            Locale.ROOT,
            "The graph has %d vertices, each of degree %d, and it is %sbipartite.\n",
            n,
            p + 1,
            bipartite ? "" : "not "));

    long[] dlGu = diameterGirthBounds(p, q, n);
    out.print(
        String.format(
            Locale.ROOT,
            "Any such graph must have diameter >= %d and girth <= %d;\n",
            dlGu[0],
            dlGu[1]));

    long du = diameterUpperBound(p, bipartite ? n : 2 * n, bipartite);
    out.print(
        String.format(
            Locale.ROOT,
            "theoretical considerations tell us that this one's diameter is <= %d",
            du));
    if (p == 2) {
      out.print(".\n");
    } else {
      long gl = girthLowerBound(p, q, bipartite);
      out.print(String.format(Locale.ROOT, ",\nand its girth is >= %d.\n", gl));
    }

    bfsSearch(g, n, out);
  }

  /**
   * Section 6: {@code dl}, a lower bound on the diameter, and {@code gu}, an upper bound on the
   * girth, from the degree {@code p + 1} and vertex count {@code n}.
   */
  static long[] diameterGirthBounds(long p, long q, long n) {
    long s = p + 2;
    long dl = 1;
    long pp = p;
    long gu = 3;
    while (s < n) {
      s += pp;
      if (s <= n) {
        gu++;
      }
      dl++;
      pp *= p;
      s += pp;
      if (s <= n) {
        gu++;
      }
    }
    return new long[] {dl, gu};
  }

  /** Sections 8 and 9: {@code du}, an upper bound on the diameter. */
  private static long diameterUpperBound(long p, long nn0, boolean bipartite) {
    long nn = nn0;
    long du = 0;
    long pp = 1;
    while (pp < nn) {
      du += 2;
      pp *= p;
    }
    long qq = pp / nn;
    if (qq * qq > p) {
      du--;
    } else if ((qq + 1) * (qq + 1) > p) {
      long aa = qq;
      long bb = p - aa * aa;
      long parity = 0;
      pp -= qq * nn;
      while (true) {
        long x = (aa + qq) / bb;
        long y = nn - x * pp;
        if (y <= 0) {
          break;
        }
        aa = bb * x - aa;
        bb = (p - aa * aa) / bb;
        nn = pp;
        pp = y;
        parity ^= 1;
      }
      if (parity == 0) {
        du--;
      }
    }
    if (bipartite) {
      du++;
    }
    return du;
  }

  /** Section 7: {@code gl}, a lower bound on the girth (only computed when {@code p != 2}). */
  static long girthLowerBound(long p, long q, boolean bipartite) {
    long gl;
    long pp;
    if (bipartite) {
      long b = q * q;
      gl = 1;
      pp = p;
      while (pp <= b) {
        gl++;
        pp *= p;
      }
      gl += gl;
    } else {
      long b1 = 1 + 4 * q * q;
      long b2 = 4 + 3 * q * q;
      gl = 1;
      pp = p;
      while (pp < b1) {
        if (pp >= b2 && (gl & 1) != 0 && (p & 2) != 0) {
          break;
        }
        gl++;
        pp *= p;
      }
    }
    return gl;
  }

  /**
   * Section 12: breadth-first search from vertex 0, printing the count at each distance and,
   * finally, the exact diameter and girth.
   */
  private static void bfsSearch(Graph g, long n, PrintStream out) {
    out.print("Starting at any given vertex, there are\n");
    Vertex sentinel = Gb.allocAuxVertices(1)[0];
    long girth = 999;
    long k = 0;
    Vertex u = g.vertices[0];
    u.w.V(sentinel);
    long c = 1;
    while (c != 0) {
      Vertex v = u;
      u = sentinel;
      c = 0;
      k++;
      for (; v != sentinel; v = v.w.V()) {
        for (Arc a = v.arcs; a != null; a = a.next) {
          Vertex tip = a.tip;
          if (tip.w.V() == null) {
            tip.w.V(u);
            tip.v.I = k;
            tip.u.V(v);
            u = tip;
            c++;
          } else if (tip.v.I + k < girth && tip != v.u.V()) {
            girth = tip.v.I + k;
          }
        }
      }
      out.print(
          String.format(Locale.ROOT, "%8d vertices at distance %d%s\n", c, k, c > 0 ? "," : "."));
    }
    out.print(
        String.format(Locale.ROOT, "So the diameter is %d, and the girth is %d.\n", k - 1, girth));
  }
}
