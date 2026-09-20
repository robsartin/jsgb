package com.robsartin.jsgb.dijk;

import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.ToLongFunction;

/**
 * Port of {@code gb_dijk}'s main algorithm: {@link #dijkstra} finds a shortest path from vertex
 * {@code uu} to vertex {@code vv} in graph {@code gg}, with the aid of an optional heuristic
 * function {@code hh}, using a version of Dijkstra's algorithm (E. W. Dijkstra, "A note on two
 * problems in connexion with graphs," <em>Numerische Mathematik</em> 1 (1959), 269-271).
 *
 * <p>If {@code hh} is {@code null}, every arc length in {@code gg} must be nonnegative. If {@code
 * hh} is non-null, it should be a function on the graph's vertices such that the length {@code d}
 * of an arc from {@code u} to {@code v} always satisfies {@code d >= hh(u) - hh(v)}; the algorithm
 * then effectively works on arc lengths {@code d - hh(u) + hh(v)}, which are all nonnegative, and
 * the shortest paths in that modified graph are the same as in the original one. A heuristic that
 * approximates the true distance to {@code vv} lets the search focus on useful arcs first.
 *
 * <p>Three vertex utility slots carry the algorithm's state, and remain readable by the caller
 * afterwards: {@code dist} ({@code z.I}) is the vertex's distance from {@code uu}, modified by
 * {@code hh}, true once the vertex is known; {@code backlink} ({@code y.V}) is non-null exactly
 * when the vertex has been seen, and then points one step closer to {@code uu} along a shortest
 * path (except {@code uu} itself, whose backlink points to itself); {@code hhVal} ({@code x.I})
 * caches {@code hh(v)} once computed. Two further slots, {@code llink} ({@code v.V}) and {@code
 * rlink} ({@code w.V}), belong to whichever {@link PriorityQueueHooks} implementation is active.
 *
 * <p>If {@link Gb#verbose} is nonzero, {@link #dijkstra} records its activity on {@link #out} by
 * printing the distances from {@code uu} to every vertex it visits.
 *
 * <p>After {@link #dijkstra} finds a shortest path, it returns that path's length. If no path from
 * {@code uu} to {@code vv} exists, it returns {@code -1}; the shortest distances from {@code uu} to
 * every vertex reachable from it will still have been computed and stored in the graph. {@link
 * #printDijkstraResult} displays the actual path found, if one exists.
 */
public final class Dijkstra {

  /**
   * The C's {@code head[128]}: 128 scratch vertices, shared by every {@link PriorityQueueHooks}
   * implementation, that never belong to a caller's graph. {@link DList} uses only {@code HEAD[0]}
   * as its single list head; {@link Buckets128} uses all 128.
   */
  static final Vertex[] HEAD = Gb.allocAuxVertices(128);

  /** The heuristic substituted when the caller passes {@code null}: always zero. */
  private static final ToLongFunction<Vertex> DUMMY = v -> 0L;

  /** The C's four priority-queue function pointers; defaults to {@link DList}. */
  public static PriorityQueueHooks queue = new DList();

  /** Where {@link #dijkstra} and {@link #printDijkstraResult} print; ISO-8859-1, like the C. */
  public static PrintStream out = new PrintStream(System.out, true, StandardCharsets.ISO_8859_1);

  private Dijkstra() {}

  private static long dist(Vertex v) {
    return v.z.I;
  }

  private static void setDist(Vertex v, long d) {
    v.z.I = d;
  }

  private static Vertex backlink(Vertex v) {
    return v.y.V();
  }

  private static void setBacklink(Vertex v, Vertex b) {
    v.y.V(b);
  }

  private static long hhVal(Vertex v) {
    return v.x.I;
  }

  private static void setHhVal(Vertex v, long h) {
    v.x.I = h;
  }

  /**
   * {@code dijkstra(uu,vv,gg,hh)}: the length of a shortest path from {@code uu} to {@code vv} in
   * {@code gg}, or {@code -1} if none exists. {@code hh}, or {@code null} for the C's {@code
   * dummy}, is the optional heuristic function described in the class documentation.
   */
  public static long dijkstra(Vertex uu, Vertex vv, Graph gg, ToLongFunction<Vertex> hh) {
    boolean usingHeuristic = hh != null;
    ToLongFunction<Vertex> h = usingHeuristic ? hh : DUMMY;

    // Section 10: make uu the only vertex seen; also make it known.
    for (int i = (int) gg.n - 1; i >= 0; i--) {
      setBacklink(gg.vertices[i], null);
    }
    setBacklink(uu, uu);
    setDist(uu, 0);
    setHhVal(uu, h.applyAsLong(uu));
    queue.initQueue(0);

    Vertex t = uu;
    if (Gb.verbose != 0) {
      // Section 12: print initial message.
      out.print("Distances from " + uu.name);
      if (usingHeuristic) {
        out.print(" [" + hhVal(uu) + "]");
      }
      out.print(":\n");
    }
    while (t != vv) {
      // Section 11: put all unseen vertices adjacent to t into the queue, and update the
      // distances of other vertices adjacent to t.
      long d = dist(t) - hhVal(t);
      for (Arc a = t.arcs; a != null; a = a.next) {
        Vertex v = a.tip;
        if (backlink(v) != null) {
          long dd = d + a.len + hhVal(v);
          if (dd < dist(v)) {
            setBacklink(v, t);
            queue.requeue(v, dd);
          }
        } else {
          setHhVal(v, h.applyAsLong(v));
          setBacklink(v, t);
          queue.enqueue(v, d + a.len + hhVal(v));
        }
      }

      t = queue.delMin();
      if (t == null) {
        return -1; // the queue became empty, so there's no way to get to vv
      }
      if (Gb.verbose != 0) {
        // Section 13: print the distance to t.
        out.print(" " + (dist(t) - hhVal(t) + hhVal(uu)) + " to " + t.name);
        if (usingHeuristic) {
          out.print(" [" + hhVal(t) + "]");
        }
        out.print(" via " + backlink(t).name + "\n");
      }
    }
    return dist(vv) - hhVal(vv) + hhVal(uu); // true distance from uu to vv
  }

  /**
   * {@code print_dijkstra_result(vv)}: prints the path {@link #dijkstra} found to {@code vv}, one
   * vertex per line as {@code "%10d %s\n"} of its true distance from the path's start and its name,
   * or {@code "Sorry, <name> is unreachable.\n"} if {@code vv}'s {@code backlink} is null. Works
   * for any vertex whose backlink is non-null, not only one that was {@code dijkstra}'s own {@code
   * vv}. The backlink chain from {@code vv} is reversed to print in forward order, then reversed
   * back, so the caller's graph is left as it was found.
   */
  public static void printDijkstraResult(Vertex vv) {
    Vertex t = null;
    Vertex p = vv;
    if (backlink(p) == null) {
      out.print("Sorry, " + p.name + " is unreachable.\n");
      return;
    }
    do { // pop an item from p to t
      Vertex q = backlink(p);
      setBacklink(p, t);
      t = p;
      p = q;
    } while (t != p); // the loop stops with t == p == uu
    do {
      out.print(String.format("%10d %s", dist(t) - hhVal(t) + hhVal(p), t.name) + "\n");
      t = backlink(t);
    } while (t != null);
    t = p;
    do { // pop an item from t to p
      Vertex q = backlink(t);
      setBacklink(t, p);
      p = t;
      t = q;
    } while (p != vv);
  }
}
