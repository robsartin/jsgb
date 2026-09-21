package com.robsartin.jsgb.demo;

import com.robsartin.jsgb.flip.Flip;
import com.robsartin.jsgb.games.Games;
import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Port of {@code football}: repeatedly prompts for a starting and a goal team, then looks for a
 * chain of 1990 college-football game results that "proves" the goal team is (transitively) better
 * than the starting team, by the score margin ({@code del}, each game's winning margin from the
 * loser's arc) summed along the chain.
 *
 * <p>With no search width, section 17/18's greedy walk always takes the locally best-margin arc out
 * of the current vertex among those from which the goal is still reachable. With a positive search
 * width, sections 19-35 run a best-first search that keeps at most {@code width} candidate chains
 * per Tarjan-computed distance-to-goal bucket, re-running the Tarjan pass (sections 28-34) once per
 * step to re-rank the frontier.
 *
 * <p>Slots: {@code del} = {@code a.I}, {@code blocked} = {@code u.I}, {@code valid} = {@code v.V},
 * {@code link} = {@code w.V}, {@code rank} = {@code z.I}, {@code parent} = {@code u.V}, {@code
 * untagged} = {@code x.A}, {@code min} = {@code v.V}, {@code date} = {@code b.I}, {@code nickname}
 * = {@code y.S}.
 */
public final class Football {

  private static final int MAX_N = 120;

  private Football() {}

  /** Entry point: runs with the real process streams and exits with the C's return value. */
  public static void main(String[] args) {
    System.exit(
        Jsgb.exitStatus(
            run(args, new CStdin(System.in), Jsgb.stdout(), Jsgb.stderr(), Path.of(""))));
  }

  /**
   * The C {@code node} struct: one step of a candidate chain, linked back to the step before it
   * ({@code prev}) and, while still queued in a {@code list[h]} bucket, forward to the next
   * candidate in that bucket ({@code next}). {@code tag} replaces the C's reuse of the (by then
   * dead) {@code next} pointer to remember this step's verbose-trace coordinates once it is popped.
   */
  private static final class Node {
    Arc game;
    long totLen;
    Node prev;
    Node next;
    long tag;
  }

  /**
   * {@code new_node(x,d)}: a fresh chain step extending {@code x} (or none) by length {@code d}.
   */
  private static Node newNode(Node x, long d) {
    Node n = new Node();
    n.prev = x;
    n.totLen = (x != null ? x.totLen : 0) + d;
    return n;
  }

  /** Runs the program: parses arguments, builds the graph, then loops on chain requests. */
  public static int run(String[] args, CStdin in, PrintStream out, PrintStream err, Path workDir) {
    Gb.verbose = 0;

    // Section 3: argument parsing.
    int argc = args.length + 1;
    if (argc == 3 && args[1].equals("-v")) {
      Gb.verbose = 2;
      argc = 2;
    }
    long width;
    if (argc == 1) {
      width = 0;
    } else if (argc == 2) {
      Long value = Scan.scan(args[0], "");
      if (value == null) {
        err.print("Usage: football [searchwidth]\n");
        return -2;
      }
      width = value < 0 ? -value : value;
    } else {
      err.print("Usage: football [searchwidth]\n");
      return -2;
    }

    // Section 5: build the graph and each arc's del (this game's margin over its mate's).
    Graph g = Games.games(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    if (g == null) {
      err.print(
          String.format(
              Locale.ROOT, "Sorry, can't create the graph! (error code %d)\n", Gb.panicCode));
      return -1;
    }
    for (int i = 0; i < g.n; i++) {
      Vertex v = g.vertices[i];
      for (Arc a = v.arcs; a != null; a = a.next) {
        if (a.tip.index > v.index) {
          a.a.I = a.len - a.mate.len;
          a.mate.a.I = -a.a.I;
        }
      }
    }

    // Section 6: the main prompt loop.
    while (true) {
      out.print("\n");
      Vertex start;
      Vertex goal;
      while (true) {
        start = promptForTeam("Starting", g, in, out);
        if (start == null) {
          return 0;
        }
        goal = promptForTeam("   Other", g, in, out);
        if (goal == null) {
          continue;
        }
        if (start == goal) {
          out.print(" (Um, please give me the names of two DISTINCT teams.)\n");
          continue;
        }
        break;
      }

      Node result =
          width == 0 ? searchGreedy(g, start, goal) : searchWidth(g, start, goal, width, out);
      printPath(out, start, goal, result);
    }
  }

  /**
   * {@code prompt_for_team(s)}: prints {@code "<s> team: "}, then reads a name with {@code
   * fgets(buffer,30)}. A blank line (or EOF re-reading the still-blank initial buffer) returns
   * {@code null}; an unrecognized name reprints the prompt after two lines of complaint; otherwise
   * returns the matching vertex.
   */
  private static Vertex promptForTeam(String s, Graph g, CStdin in, PrintStream out) {
    String buffer = "";
    while (true) {
      out.print(s + " team: ");
      String line = in.fgets(30);
      if (line != null) {
        buffer = line;
      }
      if (buffer.startsWith("\n")) {
        return null;
      }
      int nl = buffer.indexOf('\n');
      String name = nl >= 0 ? buffer.substring(0, nl) : buffer;
      for (int i = 0; i < g.n; i++) {
        if (name.equals(g.vertices[i].name)) {
          return g.vertices[i];
        }
      }
      out.print(" (Sorry, I don't know any team by that name.)\n");
      out.print(" (One team I do know is " + g.vertices[(int) Flip.unifRand(g.n)].name + "...)\n");
    }
  }

  /**
   * Sections 17-18: the {@code width == 0} greedy walk. At each step, marks every vertex from which
   * {@code goal} is reachable without crossing an already-visited vertex, then takes the
   * highest-{@code del} outgoing arc into a marked vertex (preferring any arc directly into {@code
   * goal} only when nothing else qualifies, exactly as the C's {@code d==-10000} fallback does).
   */
  private static Node searchGreedy(Graph g, Vertex start, Vertex goal) {
    for (int i = 0; i < g.n; i++) {
      Vertex vx = g.vertices[i];
      vx.u.I = 0; // blocked
      vx.v.V(null); // valid
    }
    Node curNode = null;
    Vertex v = start;
    while (v != goal) {
      long d = -10000;
      Arc bestArc = null;
      Arc lastArc = null;
      v.u.I = 1; // blocked
      curNode = newNode(curNode, 0L);

      // Section 18: mark every vertex from which goal can still reach v without crossing a
      // blocked vertex.
      Vertex cursor = goal;
      cursor.w.V(null); // link
      cursor.v.V(v); // valid
      do {
        Arc a = cursor.arcs;
        cursor = cursor.w.V();
        for (; a != null; a = a.next) {
          if (a.tip.u.I == 0 && a.tip.v.V() != v) {
            a.tip.v.V(v);
            a.tip.w.V(cursor);
            cursor = a.tip;
          }
        }
      } while (cursor != null);

      for (Arc a = v.arcs; a != null; a = a.next) {
        if (a.a.I > d && a.tip.v.V() == v) {
          if (a.tip == goal) {
            lastArc = a;
          } else {
            bestArc = a;
            d = a.a.I;
          }
        }
      }
      curNode.game = d == -10000 ? lastArc : bestArc;
      curNode.totLen += curNode.game.a.I;
      v = curNode.game.tip;
    }
    return curNode;
  }

  /**
   * Sections 19-35: the {@code width > 0} best-first search. Repeats: re-run Tarjan's algorithm
   * (sections 28-34) rooted at {@code goal} to rank every not-yet-explored vertex by its distance
   * to {@code goal} along the still-open part of the graph; extend the current frontier by one arc
   * per neighbour, keeping at most {@code width} candidates per rank bucket (section 22); then pop
   * the best candidate from the lowest nonempty bucket, reversing that bucket first (section 23) so
   * ties break in arc order. Ends when bucket 0 is reached.
   */
  private static Node searchWidth(Graph g, Vertex start, Vertex goal, long width, PrintStream out) {
    Node[] list = new Node[MAX_N];
    long[] size = new long[MAX_N];
    for (int i = 0; i < g.n; i++) {
      list[i] = null;
      size[i] = 0;
    }
    Vertex dummy = Gb.allocAuxVertices(1)[0];
    Node curNode = null;
    long m = g.n - 1;
    long mm = 0;
    do {
      // Section 28: reset the Tarjan pass, but keep every vertex already on the current chain
      // (plus start) marked as already visited so the search never loops back through them.
      for (int i = 0; i < g.n; i++) {
        Vertex vx = g.vertices[i];
        vx.z.I = 0; // rank
        vx.x.A(vx.arcs); // untagged
      }
      for (Node x = curNode; x != null; x = x.prev) {
        x.game.tip.z.I = g.n;
      }
      start.z.I = g.n;
      long nn = 0;
      Vertex activeStack = null;
      Vertex settledStack = null;

      // Section 30: one non-recursive Tarjan DFS from goal, parented by the sentinel dummy.
      Vertex v = goal;
      v.u.V(dummy); // parent
      v.z.I = ++nn; // rank
      v.w.V(activeStack); // link
      activeStack = v;
      v.v.V(v.u.V()); // min = parent

      do {
        Arc a = v.x.A(); // untagged
        if (a != null) {
          Vertex tip = a.tip;
          v.x.A(a.next);
          if (tip.z.I != 0) {
            if (tip.z.I < v.v.V().z.I) {
              v.v.V(tip);
            }
          } else {
            tip.u.V(v); // parent
            v = tip;
            v.z.I = ++nn;
            v.w.V(activeStack);
            activeStack = v;
            v.v.V(v.u.V());
          }
        } else {
          Vertex parent = v.u.V();
          if (v.v.V() == parent) {
            if (v != goal) {
              long c = 0;
              Vertex t = activeStack;
              while (t != v) {
                c++;
                t.u.V(v); // parent
                t = t.w.V();
              }
              activeStack = v.w.V();
              v.u.V(v); // parent
              v.z.I = c + g.n;
              v.w.V(settledStack);
              settledStack = v;
            }
          } else if (v.v.V().z.I < parent.v.V().z.I) {
            parent.v.V(v.v.V());
          }
          v = parent;
        }
      } while (v != dummy);

      // Section 34: fold each settled component's rank down onto the ranks already assigned.
      while (settledStack != null) {
        v = settledStack;
        settledStack = v.w.V();
        v.z.I += v.v.V().u.V().z.I + 1 - g.n;
      }

      // Section 27: extend the frontier by one arc into every fully-explored neighbour.
      Vertex frontier = curNode != null ? curNode.game.tip : start;
      for (Arc a = frontier.arcs; a != null; a = a.next) {
        Vertex tip = a.tip;
        if (tip.x.A() == null) { // untagged == null: this vertex's Tarjan pass finished
          Node x = newNode(curNode, a.a.I);
          x.game = a;
          long h = tip.u.V().z.I; // parent.rank
          insertCandidate(list, size, width, (int) h, x);
        }
      }

      // Section 23: find the next nonempty bucket, reversing it into arc order as we go.
      while (list[(int) m] == null) {
        m--;
        Node r = null;
        Node s = list[(int) m];
        while (s != null) {
          Node t = s.next;
          s.next = r;
          r = s;
          s = t;
        }
        list[(int) m] = r;
        mm = 0;
      }
      curNode = list[(int) m];
      list[(int) m] = curNode.next;
      if (Gb.verbose != 0) {
        // Section 24: the verbose trace, keyed off the popped step's and its parent's tags.
        long prevM = curNode.prev != null ? curNode.prev.tag & 0xff : 0;
        long prevMm = curNode.prev != null ? curNode.prev.tag >>> 8 : 0;
        curNode.tag = (++mm << 8) + m;
        out.print(
            String.format(
                Locale.ROOT,
                "[%d,%d]=[%d,%d]&%s (%+d)\n",
                m,
                mm,
                prevM,
                prevMm,
                curNode.game.tip.name,
                curNode.totLen));
      }
    } while (m > 0);
    return curNode;
  }

  /**
   * Section 22: inserts {@code x} into {@code list[h]} in ascending {@code totLen} order, first
   * evicting the bucket's current worst candidate if it is already at capacity ({@code width} for a
   * positive bucket, one for bucket 0) and {@code x} is no better than it — in which case {@code x}
   * is dropped instead (the C's {@code goto done}).
   */
  private static void insertCandidate(Node[] list, long[] size, long width, int h, Node x) {
    if ((h > 0 && size[h] == width) || (h == 0 && size[0] > 0)) {
      if (x.totLen <= list[h].totLen) {
        return;
      }
      list[h] = list[h].next;
    } else {
      size[h]++;
    }
    Node p = list[h];
    Node q = null;
    while (p != null && x.totLen > p.totLen) {
      q = p;
      p = p.next;
    }
    x.next = p;
    if (q != null) {
      q.next = x;
    } else {
      list[h] = x;
    }
  }

  /**
   * Sections 15-16: reverses the {@code curNode} chain (built goal-ward, from {@code start}'s first
   * step onward) back into {@code start}-to-{@code goal} order, then prints one line per step.
   */
  private static void printPath(PrintStream out, Vertex start, Vertex goal, Node curNode) {
    Node nextNode = null;
    Node t = curNode;
    do {
      Node prevSaved = t.prev;
      t.prev = nextNode;
      nextNode = t;
      t = prevSaved;
    } while (t != null);

    Vertex v = start;
    while (v != goal) {
      Arc a = nextNode.game;
      Vertex u = a.tip;
      out.print(
          " "
              + formatDate(a.b.I)
              + ": "
              + v.name
              + " "
              + v.y.S()
              + " "
              + a.len
              + ", "
              + u.name
              + " "
              + u.y.S()
              + " "
              + (a.len - a.a.I));
      out.print(String.format(Locale.ROOT, " (%+d)\n", nextNode.totLen));
      v = u;
      nextNode = nextNode.prev;
    }
  }

  /**
   * Section 16's date arithmetic: {@code d}, the day of the season a game was played, as a month
   * name and two-digit day (the season runs from late August to early January).
   */
  static String formatDate(long d) {
    if (d <= 5) {
      return String.format(Locale.ROOT, "Aug %02d", d + 26);
    }
    if (d <= 35) {
      return String.format(Locale.ROOT, "Sep %02d", d - 5);
    }
    if (d <= 66) {
      return String.format(Locale.ROOT, "Oct %02d", d - 35);
    }
    if (d <= 96) {
      return String.format(Locale.ROOT, "Nov %02d", d - 66);
    }
    if (d <= 127) {
      return String.format(Locale.ROOT, "Dec %02d", d - 96);
    }
    return "Jan 01";
  }
}
