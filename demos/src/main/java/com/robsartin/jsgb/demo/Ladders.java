package com.robsartin.jsgb.demo;

import com.robsartin.jsgb.dijk.Buckets128;
import com.robsartin.jsgb.dijk.DList;
import com.robsartin.jsgb.dijk.Dijkstra;
import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import com.robsartin.jsgb.words.Words;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

/**
 * Port of {@code ladders}: repeatedly prompts for a starting and a goal five-letter word, then
 * looks for a shortest "word ladder" between them in the {@code words} graph using Dijkstra's
 * algorithm, optionally with alphabetic-distance or letter-frequency arc lengths and a lowerbound
 * heuristic.
 */
public final class Ladders {

  private Ladders() {}

  /** Entry point: runs with the real process streams and exits with the C's return value. */
  public static void main(String[] args) {
    System.exit(
        Jsgb.exitStatus(
            run(args, new CStdin(System.in), Jsgb.stdout(), Jsgb.stderr(), Path.of(""))));
  }

  /** {@code prompt_for_five}'s outcome: {@code status} (0, 1 or -1) and the word read, if any. */
  record PromptResult(int status, String word) {}

  /** Runs the program: parses arguments, builds the dictionary, then loops on ladder requests. */
  public static int run(String[] args, CStdin in, PrintStream out, PrintStream err, Path workDir) {
    Gb.verbose = 0;
    Dijkstra.queue = new DList();
    PrintStream savedDijkstraOut = Dijkstra.out;
    Dijkstra.out = out;
    try {
      return runBody(args, in, out, err);
    } finally {
      Dijkstra.out = savedDijkstraOut;
    }
  }

  private static int runBody(String[] args, CStdin in, PrintStream out, PrintStream err) {
    boolean alphFlag = false;
    boolean freqFlag = false;
    boolean heurFlag = false;
    boolean echoFlag = false;
    boolean randmFlag = false;
    long nRaw = 0;
    long seed = 0;

    for (int i = args.length - 1; i >= 0; i--) {
      String arg = args[i];
      Long value;
      if (arg.equals("-v")) {
        Gb.verbose = 1;
      } else if (arg.equals("-a")) {
        alphFlag = true;
      } else if (arg.equals("-f")) {
        freqFlag = true;
      } else if (arg.equals("-h")) {
        heurFlag = true;
      } else if (arg.equals("-e")) {
        echoFlag = true;
      } else if ((value = Scan.scan(arg, "-n")) != null) {
        nRaw = value;
        randmFlag = false;
      } else if ((value = Scan.scan(arg, "-r")) != null) {
        nRaw = value;
        randmFlag = true;
      } else if ((value = Scan.scan(arg, "-s")) != null) {
        seed = value;
      } else {
        err.print("Usage: ladders [-v][-a][-f][-h][-e][-nN][-rN][-sN]\n");
        return -2;
      }
    }
    final boolean alph = alphFlag;
    final boolean randm = randmFlag;
    final boolean freq = (alph || randm) ? false : freqFlag;
    final boolean heur = freq ? false : heurFlag;
    final boolean echo = echoFlag;
    final long n = nRaw;

    Graph g = Words.words(n, randm ? new long[9] : null, 0L, seed);
    if (g == null) {
      err.print(
          String.format(
              Locale.ROOT,
              "Sorry, I couldn't build a dictionary (trouble code %d)!\n",
              Gb.panicCode));
      return (int) Gb.panicCode;
    }

    if (Gb.verbose != 0) {
      if (alph) {
        out.print("(alphabetic distance selected)\n");
      }
      if (freq) {
        out.print("(frequency-based distances selected)\n");
      }
      if (heur) {
        out.print("(lowerbound heuristic will be used to focus the search)\n");
      }
      if (randm) {
        out.print(
            String.format(Locale.ROOT, "(random selection of %d words with seed %d)\n", g.n, seed));
      } else {
        out.print(String.format(Locale.ROOT, "(the graph has %d words)\n", g.n));
      }
    }

    if (alph) {
      for (int i = (int) g.n - 1; i >= 0; i--) {
        Vertex u = g.vertices[i];
        for (Arc a = u.arcs; a != null; a = a.next) {
          a.len = aDist((int) a.a.I, u.name, a.tip.name);
        }
      }
    } else if (freq) {
      for (int i = (int) g.n - 1; i >= 0; i--) {
        Vertex u = g.vertices[i];
        for (Arc a = u.arcs; a != null; a = a.next) {
          a.len = freqCost(a.tip);
        }
      }
    }

    if (alph || freq || heur) {
      Dijkstra.queue = new Buckets128();
    }

    while (true) {
      out.print("\n");
      String start = null;
      String goal = null;
      while (true) {
        PromptResult startResult = promptForFive("Starting", in, out, echo);
        if (startResult.status() != 0) {
          return 0;
        }
        start = startResult.word();
        PromptResult goalResult = promptForFive("    Goal", in, out, echo);
        if (goalResult.status() != 0) {
          continue;
        }
        goal = goalResult.word();
        break;
      }

      int code = runOneLadder(g, start, goal, alph, freq, heur, out, err);
      if (code != Integer.MIN_VALUE) {
        return code;
      }
    }
  }

  /**
   * Section 13: builds the transient two-vertex extension for one {@code start}/{@code goal} pair,
   * runs {@link Dijkstra#dijkstra}, prints the result, and cleans the extension back out. Returns
   * {@link Integer#MIN_VALUE} to continue the prompt loop, or a demo return value on a fatal error.
   */
  private static int runOneLadder(
      Graph g,
      String start,
      String goal,
      boolean alph,
      boolean freq,
      boolean heur,
      PrintStream out,
      PrintStream err) {
    Graph gg = Gb.newGraph(0L);
    if (gg == null) {
      return quitCantBuild(err, Gb.NO_ROOM + 5);
    }
    gg.vertices = g.vertices;
    gg.n = g.n;

    Consumer<Vertex> plantNewEdge =
        v -> {
          Vertex u = gg.vertices[(int) gg.n];
          Gb.newEdge(u, v, 1L);
          if (alph) {
            u.arcs.len = u.arcs.mate.len = alphDist(u.name, v.name);
          } else if (freq) {
            u.arcs.len = freqCost(v);
            u.arcs.mate.len = 20;
          }
        };

    gg.vertices[(int) gg.n].name = start;
    Vertex uu = Words.findWord(start, plantNewEdge);
    if (uu == null) {
      uu = gg.vertices[(int) gg.n];
      gg.n++;
    }
    Vertex vv;
    if (start.equals(goal)) {
      vv = uu;
    } else {
      gg.vertices[(int) gg.n].name = goal;
      vv = Words.findWord(goal, plantNewEdge);
      if (vv == null) {
        vv = gg.vertices[(int) gg.n];
        gg.n++;
      }
    }
    if (gg.n == g.n + 2 && hammDist(start, goal) == 1) {
      gg.n--;
      plantNewEdge.accept(uu);
      gg.n++;
    }
    if (Gb.troubleCode != 0) {
      return quitCantBuild(err, Gb.NO_ROOM + 6);
    }

    ToLongFunction<Vertex> heuristic =
        !heur ? null : (alph ? v -> alphDist(v.name, goal) : v -> hammDist(v.name, goal));
    long minDist = Dijkstra.dijkstra(uu, vv, gg, heuristic);
    if (minDist < 0) {
      out.print(
          String.format(Locale.ROOT, "Sorry, there's no ladder from %s to %s.\n", start, goal));
    } else {
      Dijkstra.printDijkstraResult(vv);
    }

    for (int k = (int) gg.n - 1; k >= (int) g.n; k--) {
      Vertex spare = g.vertices[k];
      for (Arc a = spare.arcs; a != null; a = a.next) {
        Vertex real = a.tip;
        real.arcs = real.arcs.next;
      }
      spare.arcs = null;
    }
    Gb.recycle(gg);
    return Integer.MIN_VALUE;
  }

  /** The {@code quit_if} macro's shared message and return value. */
  private static int quitCantBuild(PrintStream err, long code) {
    err.print(
        String.format(
            Locale.ROOT, "Sorry, I couldn't build a dictionary (trouble code %d)!\n", code));
    return (int) code;
  }

  /**
   * {@code prompt_for_five(s,p)}: prints {@code "<s> word: "}, then reads characters until a
   * newline. Status 0 means exactly five lowercase letters were read (returned as {@code word});
   * status 1 means an empty line (EOF is never reached); status -1 means EOF before any newline.
   * Anything else (a non-lowercase character, or the wrong number of characters) prints a retry
   * message and prompts again.
   */
  static PromptResult promptForFive(String s, CStdin in, PrintStream out, boolean echo) {
    while (true) {
      out.print(s + " word: ");
      StringBuilder word = new StringBuilder();
      int count = 0;
      boolean invalid = false;
      while (true) {
        int c = in.getchar();
        if (c == -1) {
          return new PromptResult(-1, null);
        }
        if (echo) {
          out.print((char) c);
        }
        if (c == '\n') {
          break;
        }
        if (c < 'a' || c > 'z') {
          invalid = true;
        } else if (!invalid && word.length() < 5) {
          word.append((char) c);
        }
        count++;
      }
      if (!invalid && count == 5) {
        return new PromptResult(0, word.toString());
      }
      if (count == 0) {
        return new PromptResult(1, null);
      }
      out.print("(Please type five lowercase letters and RETURN.)\n");
    }
  }

  /**
   * {@code freq_cost(v)}: 16 minus the number of significant bits in {@code v}'s weight ({@code
   * u.I}), floored at 0 — a common (high-weight) word costs less than a rare one.
   */
  static long freqCost(Vertex v) {
    long acc = v.u.I;
    long k = 16;
    while (acc != 0) {
      k--;
      acc >>= 1;
    }
    return k < 0 ? 0 : k;
  }

  /**
   * {@code a_dist(k)}: the absolute difference between {@code p} and {@code q}'s {@code k}-th
   * letter.
   */
  private static long aDist(int k, String p, String q) {
    char pc = p.charAt(k);
    char qc = q.charAt(k);
    return pc < qc ? qc - pc : pc - qc;
  }

  /** {@code alph_dist(p,q)}: the sum of {@link #aDist} over all five letter positions. */
  static long alphDist(String p, String q) {
    long total = 0;
    for (int k = 0; k < 5; k++) {
      total += aDist(k, p, q);
    }
    return total;
  }

  /**
   * {@code hamm_dist(p,q)}: the number of letter positions where {@code p} and {@code q} differ.
   */
  static long hammDist(String p, String q) {
    long total = 0;
    for (int k = 0; k < 5; k++) {
      if (p.charAt(k) != q.charAt(k)) {
        total++;
      }
    }
    return total;
  }
}
