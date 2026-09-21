package com.robsartin.jsgb.demo;

import com.robsartin.jsgb.econ.Econ;
import com.robsartin.jsgb.flip.Flip;
import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Port of {@code econ_order}: builds a flow graph of economic sectors, then repeatedly searches for
 * a permutation of the sectors that locally minimises "feed-forward" (the total flow that runs
 * against the chosen order) by repeatedly moving one sector past its neighbours.
 *
 * <p>{@code mat[j][k]} and {@code del[j][k]} (the flow from sector {@code j} to sector {@code k},
 * and their signed difference) are indexed by {@link Vertex#index}, not by {@code mapping}.
 */
public final class EconOrder {

  private static final long INF = 0x7fffffffL;
  private static final int MAX_SECTORS = 79;

  private EconOrder() {}

  /** Entry point: runs with the real process streams and exits with the C's return value. */
  public static void main(String[] args) {
    System.exit(
        Jsgb.exitStatus(
            run(args, new CStdin(System.in), Jsgb.stdout(), Jsgb.stderr(), Path.of(""))));
  }

  /** Runs the program: parses arguments, builds the flow matrix, then descends to local minima. */
  public static int run(String[] args, CStdin in, PrintStream out, PrintStream err, Path workDir) {
    Gb.verbose = 0;
    long n = 79;
    long s = 0;
    long t = 0;
    long r = 1;
    boolean greedy = false;

    for (int idx = args.length - 1; idx >= 0; idx--) {
      String arg = args[idx];
      Long value;
      if ((value = Scan.scan(arg, "-n")) != null) {
        n = value;
      } else if ((value = Scan.scan(arg, "-r")) != null) {
        r = value;
      } else if ((value = Scan.scan(arg, "-s")) != null) {
        s = value;
      } else if ((value = Scan.scan(arg, "-t")) != null) {
        t = value;
      } else if (arg.equals("-v")) {
        Gb.verbose = 1;
      } else if (arg.equals("-V")) {
        Gb.verbose = 2;
      } else if (arg.equals("-g")) {
        greedy = true;
      } else {
        err.print("Usage: econ_order [-nN][-rN][-sN][-tN][-g][-v][-V]\n");
        return -2;
      }
    }

    Graph g = Econ.econ(n, 2L, 0L, s);
    if (g == null) {
      err.print(
          String.format(
              Locale.ROOT, "Sorry, can't create the matrix! (error code %d)\n", Gb.panicCode));
      return -1;
    }
    out.print(String.format(Locale.ROOT, "Ordering the sectors of %s, using seed %d:\n", g.id, t));
    out.print(
        String.format(Locale.ROOT, " (%s descent method)\n", greedy ? "Steepest" : "Cautious"));

    long[][] mat = new long[MAX_SECTORS][MAX_SECTORS];
    long[][] del = new long[MAX_SECTORS][MAX_SECTORS];
    n = g.n;
    for (int vi = 0; vi < n; vi++) {
      Vertex v = g.vertices[vi];
      for (Arc a = v.arcs; a != null; a = a.next) {
        mat[v.index][a.tip.index] = a.a.I;
      }
    }
    for (int j = 0; j < n; j++) {
      for (int k = 0; k < n; k++) {
        del[j][k] = mat[j][k] - mat[k][j];
      }
    }

    long sum = 0;
    for (int j = 1; j < n; j++) {
      for (int k = 0; k < j; k++) {
        sum += Math.min(mat[j][k], mat[k][j]);
      }
    }
    out.print(
        String.format(Locale.ROOT, "(The amount of feed-forward must be at least %d.)\n", sum));

    Flip.initRand(t);
    long bestScore = INF;
    long[] mapping = new long[MAX_SECTORS];
    while (r-- != 0) {
      bestScore = runOnePermutation(g, mat, del, mapping, n, greedy, bestScore, out);
    }
    return 0;
  }

  /** Sections 8-13: one random-start descent to a local minimum, and its final report. */
  private static long runOnePermutation(
      Graph g,
      long[][] mat,
      long[][] del,
      long[] mapping,
      long n,
      boolean greedy,
      long bestScore,
      PrintStream out) {
    long steps = 0;
    long score = 0;
    for (int k = 0; k < n; k++) {
      int j = (int) Flip.unifRand(k + 1);
      mapping[k] = mapping[j];
      mapping[j] = k;
    }
    for (int j = 1; j < n; j++) {
      for (int k = 0; k < j; k++) {
        score += mat[(int) mapping[j]][(int) mapping[k]];
      }
    }
    if (Gb.verbose > 1) {
      out.print("\nInitial permutation:\n");
      for (int k = 0; k < n; k++) {
        out.print(String.format(Locale.ROOT, " %s\n", secName(g, mapping, k)));
      }
    }

    while (true) {
      long bestD = greedy ? 0 : INF;
      long bestK = -1;
      long bestJ = 0;
      for (int k = 0; k < n; k++) {
        long d = 0;
        for (int j = k - 1; j >= 0; j--) {
          d += del[(int) mapping[k]][(int) mapping[j]];
          if (d > 0 && (greedy ? d > bestD : d < bestD)) {
            bestK = k;
            bestJ = j;
            bestD = d;
          }
        }
        d = 0;
        for (int j = k + 1; j < n; j++) {
          d += del[(int) mapping[j]][(int) mapping[k]];
          if (d > 0 && (greedy ? d > bestD : d < bestD)) {
            bestK = k;
            bestJ = j;
            bestD = d;
          }
        }
      }
      if (bestK < 0) {
        break;
      }

      if (Gb.verbose != 0) {
        out.print(String.format(Locale.ROOT, "%8d after step %d\n", (int) score, (int) steps));
      } else if (steps % 1000 == 0 && steps > 0) {
        out.print(".");
      }

      if (Gb.verbose > 1) {
        out.print(
            String.format(
                Locale.ROOT,
                "Now move %s to the %s, past\n",
                secName(g, mapping, (int) bestK),
                bestJ < bestK ? "left" : "right"));
      }
      int j = (int) bestK;
      long k = mapping[j];
      do {
        if (bestJ < bestK) {
          mapping[j] = mapping[j - 1];
          j--;
        } else {
          mapping[j] = mapping[j + 1];
          j++;
        }
        if (Gb.verbose > 1) {
          out.print(
              String.format(
                  Locale.ROOT,
                  "    %s (%d)\n",
                  secName(g, mapping, j),
                  bestJ < bestK
                      ? del[(int) mapping[j + 1]][(int) k]
                      : del[(int) k][(int) mapping[j - 1]]));
        }
      } while (j != bestJ);
      mapping[j] = k;
      score -= bestD;
      steps++;
    }

    out.print(
        String.format(
            Locale.ROOT,
            "\n%s is %d, found after %d step%s.\n",
            bestScore == INF ? "Local minimum feed-forward" : "Another local minimum",
            (int) score,
            (int) steps,
            steps == 1 ? "" : "s"));
    if (Gb.verbose != 0 || score < bestScore) {
      out.print("The corresponding economic order is:\n");
      for (int k = 0; k < n; k++) {
        out.print(String.format(Locale.ROOT, " %s\n", secName(g, mapping, k)));
      }
      if (score < bestScore) {
        bestScore = score;
      }
    }
    return bestScore;
  }

  /** {@code sec_name(k)}: the name of the sector currently mapped to position {@code k}. */
  private static String secName(Graph g, long[] mapping, int k) {
    return g.vertices[(int) mapping[k]].name;
  }
}
