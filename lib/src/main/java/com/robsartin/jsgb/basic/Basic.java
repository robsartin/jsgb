package com.robsartin.jsgb.basic;

import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;

/**
 * Port of {@code gb_basic}: six subroutines that generate standard graphs of various types (boards,
 * simplexes, subsets, permutations, partitions, binary trees), together with six routines that
 * combine or transform existing graphs, and the ten {@code gb_basic.h} shortcut macros for common
 * special cases. This port provides {@link #board}, {@link #simplex}, {@link #subsets}, {@link
 * #perms}, {@link #parts}, {@link #binary}, {@link #complement}, {@link #gunion}, {@link
 * #intersection}, {@link #lines}, {@link #product} and {@link #induced}; the standard applications
 * of {@link #induced}, {@link #biComplete} and {@link #wheel}; and the shortcuts {@link #complete},
 * {@link #transitive}, {@link #empty}, {@link #circuit}, {@link #cycle}, {@link #disjointSubsets},
 * {@link #petersen}, {@link #allPerms}, {@link #allParts} and {@link #allTrees}.
 *
 * <p>Several generators share a handful of C-global scratch arrays, translated here as {@code
 * private static} fields exactly as in the C (single-threaded, reused across calls): {@code nn}
 * (component/coordinate sizes), {@code wr} (does a component wrap around?), {@code del}
 * (displacement vector for the current {@link #board} move), {@code sig} (partial sums of squares
 * of displacements, or partial coordinate sums for {@link #simplex}/{@link #subsets}), {@code xx}
 * and {@code yy} (coordinate values before/after a move, or upper-bound/partial-sum tables), and
 * {@code buffer} (vertex-name assembly, represented here with a {@link StringBuilder} instead of a
 * fixed {@code char} array).
 *
 * <p>The C source also defines several one-letter macros that rename a vertex's or graph's utility
 * slots for readability: {@code tmp} is {@code u.V}, {@code tlen} is {@code z.A}, {@code mult} is
 * {@code v.I}, {@code minlen} is {@code w.I}, {@code map} is {@code z.V}, {@code ind} is {@code
 * z.I}, and {@code subst} is {@code y.G}. {@code tmp}/{@code tlen} are the multi-arc-merge scratch
 * used by {@link #gunion}, {@link #intersection} and {@link #induced} (a vertex's existing arcs or
 * edges are noted there, via {@code tmp}/{@code tlen}, before more are added, so a repeat can be
 * merged instead of duplicated); {@code minlen} is {@link #intersection}'s own per-vertex running
 * minimum.
 *
 * <p>{@code mult}, {@code map}, {@code ind} and {@code subst} belong to {@link #induced}. Every
 * vertex of the graph being induced must first be given an "induction code" in its {@code ind}
 * field ({@code v.z.I}): 0 to eliminate the vertex, 1 to keep it, {@code k>1} to split it into
 * {@code k} nonadjacent clones with the same neighbors, or {@code k<0} to identify it with every
 * other vertex sharing that value of {@code k}. When {@code ind} is {@link #IND_GRAPH} or more, the
 * vertex's {@code subst} field ({@code v.y.G(graph)}) must instead point at a graph whose vertices
 * are substituted in its place (used by {@link #wheel} to hang a cycle off a hub). {@link #induced}
 * records each surviving vertex's original {@code ind} in its first clone's {@code mult} field and
 * points {@code map} ({@code v.z.V()}) at that clone while it works, restoring {@code ind} from
 * {@code mult} (and clearing {@code map}) before it returns, so a caller's graph is left exactly as
 * it was handed in. {@link #biComplete} and {@link #wheel} set {@code ind} and, when substituting,
 * {@code subst} on a trivial two-vertex board before calling {@link #induced} themselves; a direct
 * caller of {@link #induced} is expected to do the same.
 */
public final class Basic {

  private Basic() {}

  /** {@code MAX_D}: the largest number of dimensions/coordinates a generator will accept. */
  public static final int MAX_D = 91;

  /** {@code BUF_SIZE}: the size of the C's vertex-name assembly buffer. */
  public static final int BUF_SIZE = 4096;

  /** {@code MAX_NNN}: {@link #board} refuses to build a board with more cells than this. */
  public static final float MAX_NNN = 1000000000.0f;

  /**
   * {@code IND_GRAPH}: when a vertex's {@code ind} field ({@code z.I}) is this or greater, {@link
   * #induced} substitutes a copy of the graph in its {@code subst} field ({@code y.G}) in its
   * place, instead of splitting the vertex into {@code ind} plain clones.
   */
  public static final long IND_GRAPH = 1000000000;

  /** {@code cartesian}: {@link #product}'s {@code type} for the cartesian product. */
  public static final long CARTESIAN = 0;

  /** {@code direct}: {@link #product}'s {@code type} for the direct (tensor) product. */
  public static final long DIRECT = 1;

  /**
   * {@code strong}: {@link #product}'s {@code type} for the strong product (cartesian + direct).
   */
  public static final long STRONG = 2;

  // Section 10: component sizes, wraparound flags, move displacements and their partial sums of
  // squares (board), and coordinate values before/after a move, or upper-bound tables (simplex,
  // subsets). Sized MAX_D+2, matching the C's static arrays (MAX_D+1 for nn/wr/del/xx/yy, MAX_D+2
  // for sig; one array size covers both here).
  private static final long[] nn = new long[MAX_D + 2];
  private static final long[] wr = new long[MAX_D + 2];
  private static final long[] del = new long[MAX_D + 2];
  private static final long[] sig = new long[MAX_D + 2];
  private static final long[] xx = new long[MAX_D + 2];
  private static final long[] yy = new long[MAX_D + 2];

  /**
   * {@code board(n1,n2,n3,n4,piece,wrap,directed)}: moves of a generalized chesspiece on a
   * generalized rectangular board. {@code n1..n4} determine the board's dimensions (see the C
   * documentation of {@code board} in {@code gb_basic.w} for the full scanning rule); {@code piece}
   * selects the move (0 defaults to a wazir move, negative allows arbitrary multiples); {@code
   * wrap} is a bit mask of coordinates that wrap around; {@code directed} makes the graph directed
   * when nonzero. Returns {@code null} and sets {@link Gb#panicCode} on failure (too many
   * dimensions, too many cells, or out of memory).
   */
  public static Graph board(
      long n1, long n2, long n3, long n4, long piece, long wrap, long directed) {
    long j;
    long k;
    long d;
    Vertex v;
    long n;
    long p;
    long l;

    // Section 11: normalize the board-size parameters.
    if (piece == 0) {
      piece = 1;
    }
    if (n1 <= 0) {
      n1 = n2 = 8;
      n3 = 0;
    }
    nn[1] = n1;
    normalize:
    {
      if (n2 <= 0) {
        k = 2;
        d = -n2;
        n3 = n4 = 0;
      } else {
        nn[2] = n2;
        if (n3 <= 0) {
          k = 3;
          d = -n3;
          n4 = 0;
        } else {
          nn[3] = n3;
          if (n4 <= 0) {
            k = 4;
            d = -n4;
          } else {
            nn[4] = n4;
            d = 4;
            break normalize;
          }
        }
      }
      if (d == 0) {
        d = k - 1;
        break normalize;
      }
      if (replicateComponentSizes(k, d)) {
        return null;
      }
    }
    // now nn[1] through nn[d] are set up

    // Section 13: set up a graph with n vertices.
    Graph newGraph;
    {
      float nnn = 1.0f;
      n = 1;
      for (j = 1; j <= d; j++) {
        nnn *= (float) nn[(int) j];
        if (nnn > MAX_NNN) {
          Gb.panicCode = Gb.VERY_BAD_SPECS;
          Gb.troubleCode = 0;
          return null;
        }
        n *= nn[(int) j];
      }
      newGraph = Gb.newGraph(n);
      if (newGraph == null) {
        Gb.panicCode = Gb.NO_ROOM;
        Gb.troubleCode = 0;
        return null;
      }
      newGraph.id =
          "board("
              + n1
              + ","
              + n2
              + ","
              + n3
              + ","
              + n4
              + ","
              + piece
              + ","
              + wrap
              + ","
              + (directed != 0 ? 1 : 0)
              + ")";
      newGraph.utilTypes = "ZZZIIIZZZZZZZZ";

      // Section 14: give names to the vertices.
      nn[0] = xx[0] = xx[1] = xx[2] = xx[3] = 0;
      for (k = 4; k <= d; k++) {
        xx[(int) k] = 0;
      }
      Vertex[] verts = newGraph.vertices;
      StringBuilder q = new StringBuilder();
      for (int vi = 0; ; vi++) {
        v = verts[vi];
        q.setLength(0);
        for (k = 1; k <= d; k++) {
          q.append('.').append(xx[(int) k]);
        }
        v.name = Gb.saveString(q.substring(1));
        v.x.I = xx[1];
        v.y.I = xx[2];
        v.z.I = xx[3];
        for (k = d; xx[(int) k] + 1 == nn[(int) k]; k--) {
          xx[(int) k] = 0;
        }
        if (k == 0) {
          break;
        }
        xx[(int) k]++;
      }
    }

    // Section 15: insert arcs or edges for all legal moves.
    Vertex[] verts = newGraph.vertices;
    // Section 16: initialize the wr, sig and del tables.
    {
      long w = wrap;
      for (k = 1; k <= d; k++, w >>= 1) {
        wr[(int) k] = w & 1;
        del[(int) k] = sig[(int) k] = 0;
      }
      sig[0] = del[0] = sig[(int) (d + 1)] = 0;
    }
    p = piece < 0 ? -piece : piece;
    outer:
    while (true) {
      // Section 17: advance to the next nonnegative del vector, or break if done.
      for (k = d; sig[(int) k] + (del[(int) k] + 1) * (del[(int) k] + 1) > p; k--) {
        del[(int) k] = 0;
      }
      if (k == 0) {
        break outer;
      }
      del[(int) k]++;
      sig[(int) (k + 1)] = sig[(int) k] + del[(int) k] * del[(int) k];
      for (k++; k <= d; k++) {
        sig[(int) (k + 1)] = sig[(int) k];
      }
      if (sig[(int) (d + 1)] < p) {
        continue outer;
      }

      while (true) {
        // Section 19: generate moves for the current del vector, over every vertex.
        for (k = 1; k <= d; k++) {
          xx[(int) k] = 0;
        }
        for (int vi = 0; ; vi++) {
          v = verts[vi];
          // Section 20: generate moves from v corresponding to del.
          for (k = 1; k <= d; k++) {
            yy[(int) k] = xx[(int) k] + del[(int) k];
          }
          noMore:
          for (l = 1; ; l++) {
            // Section 22: correct for wraparound, or stop (no_more) if off the board.
            for (k = 1; k <= d; k++) {
              if (yy[(int) k] < 0) {
                if (wr[(int) k] == 0) {
                  break noMore;
                }
                do {
                  yy[(int) k] += nn[(int) k];
                } while (yy[(int) k] < 0);
              } else if (yy[(int) k] >= nn[(int) k]) {
                if (wr[(int) k] == 0) {
                  break noMore;
                }
                do {
                  yy[(int) k] -= nn[(int) k];
                } while (yy[(int) k] >= nn[(int) k]);
              }
            }
            // Section 21: stop (no_more) if yy == xx; only possible when piece < 0.
            if (piece < 0) {
              boolean equal = true;
              for (k = 1; k <= d; k++) {
                if (yy[(int) k] != xx[(int) k]) {
                  equal = false;
                  break;
                }
              }
              if (equal) {
                break noMore;
              }
            }
            // Section 23: record a legal move from xx to yy.
            j = yy[1];
            for (k = 2; k <= d; k++) {
              j = nn[(int) k] * j + yy[(int) k];
            }
            if (directed != 0) {
              Gb.newArc(v, verts[(int) j], l);
            } else {
              Gb.newEdge(v, verts[(int) j], l);
            }
            if (piece > 0) {
              break noMore;
            }
            for (k = 1; k <= d; k++) {
              yy[(int) k] += del[(int) k];
            }
          }
          for (k = d; xx[(int) k] + 1 == nn[(int) k]; k--) {
            xx[(int) k] = 0;
          }
          if (k == 0) {
            break;
          }
          xx[(int) k]++;
        }

        // Section 18: advance to the next signed del vector, or restore del and break.
        for (k = d; del[(int) k] <= 0; k--) {
          del[(int) k] = -del[(int) k];
        }
        if (sig[(int) k] == 0) {
          break;
        }
        del[(int) k] = -del[(int) k];
      }
    }

    if (Gb.troubleCode != 0) {
      Gb.recycle(newGraph);
      Gb.panicCode = Gb.ALLOC_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    return newGraph;
  }

  /**
   * {@code simplex(n,n0,n1,n2,n3,n4,directed)}: vertices are sequences of nonnegative integers
   * {@code (x0,...,xd)} summing to {@code n}, bounded coordinate-wise by {@code n0..nd} (see the C
   * documentation of {@code simplex} in {@code gb_basic.w} for how {@code n0..n4} determine {@code
   * d} and the bounds); two vertices are adjacent when they differ by &plusmn;1 in exactly two
   * components. {@code directed} restricts arcs to lexicographically increasing neighbors when
   * nonzero. Returns {@code null} and sets {@link Gb#panicCode} on failure.
   */
  public static Graph simplex(long n, long n0, long n1, long n2, long n3, long n4, long directed) {
    long j;
    long k;
    Vertex v;

    // Section 27: normalize the simplex parameters.
    long[] nArgs = {n0, n1, n2, n3, n4};
    long d = normalizeSimplexArgs(n, nArgs);
    if (d < 0) {
      return null;
    }
    n0 = nArgs[0];
    n1 = nArgs[1];
    n2 = nArgs[2];
    n3 = nArgs[3];
    n4 = nArgs[4];

    // Section 28/29/30: determine the number of feasible points, and allocate the graph.
    long nverts = countFeasiblePoints(n, d);
    if (nverts < 0) {
      return null;
    }
    Graph newGraph = Gb.newGraph(nverts);
    if (newGraph == null) {
      Gb.panicCode = Gb.NO_ROOM;
      Gb.troubleCode = 0;
      return null;
    }
    newGraph.id =
        "simplex("
            + Long.toUnsignedString(n)
            + ","
            + n0
            + ","
            + n1
            + ","
            + n2
            + ","
            + n3
            + ","
            + n4
            + ","
            + (directed != 0 ? 1 : 0)
            + ")";
    newGraph.utilTypes = "VVZIIIZZZZZZZZ"; // hash table will be used

    // Section 31: name the points and create the arcs or edges.
    Vertex[] verts = newGraph.vertices;
    int vi = 0;
    yy[(int) (d + 1)] = 0;
    sig[0] = n;
    for (k = d; k >= 0; k--) {
      yy[(int) k] = yy[(int) (k + 1)] + nn[(int) k];
    }
    if (yy[0] >= n) {
      k = 0;
      xx[0] = yy[1] >= n ? 0 : n - yy[1];
      while (true) {
        // Section 32: complete the partial solution (x0,...,xk).
        if (completePartialSolution(k, d)) {
          return null;
        }
        v = verts[vi];
        // Section 34: assign a symbolic name for (x0,...,xd) to vertex v.
        assignSimplexName(v, d);
        Gb.hashIn(v);
        // Section 35: create arcs or edges from previous points to v.
        for (j = 0; j < d; j++) {
          if (xx[(int) j] != 0) {
            xx[(int) j]--;
            for (k = j + 1; k <= d; k++) {
              if (xx[(int) k] < nn[(int) k]) {
                xx[(int) k]++;
                Vertex u = Gb.hashOut(buildDotName(d));
                if (u == null) {
                  Gb.panicCode = Gb.IMPOSSIBLE + 2;
                  Gb.troubleCode = 0;
                  return null;
                }
                if (directed != 0) {
                  Gb.newArc(u, v, 1L);
                } else {
                  Gb.newEdge(u, v, 1L);
                }
                xx[(int) k]--;
              }
            }
            xx[(int) j]++;
          }
        }
        vi++;
        // Section 33: advance to the next partial solution, or stop (goto last).
        k = advanceToNextSolution(d);
        if (k < 0) {
          break;
        }
      }
    }
    if (vi != newGraph.n) {
      Gb.panicCode = Gb.IMPOSSIBLE;
      Gb.troubleCode = 0;
      return null;
    }

    if (Gb.troubleCode != 0) {
      Gb.recycle(newGraph);
      Gb.panicCode = Gb.ALLOC_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    return newGraph;
  }

  /**
   * {@code subsets(n,n0,n1,n2,n3,n4,size_bits,directed)}: the same vertices as {@link #simplex},
   * read as {@code n}-element submultisets of {@code {n0*0, n1*1, ..., nd*d}}; two vertices are
   * adjacent when the cardinality of their intersection matches a bit set in {@code sizeBits}.
   * {@code directed} restricts arcs to {@code u <= v} when nonzero. Returns {@code null} and sets
   * {@link Gb#panicCode} on failure.
   */
  public static Graph subsets(
      long n, long n0, long n1, long n2, long n3, long n4, long sizeBits, long directed) {
    long j;
    long k;
    Vertex v;

    // Section 27: normalize the simplex parameters (shared with simplex/perms).
    long[] nArgs = {n0, n1, n2, n3, n4};
    long d = normalizeSimplexArgs(n, nArgs);
    if (d < 0) {
      return null;
    }
    n0 = nArgs[0];
    n1 = nArgs[1];
    n2 = nArgs[2];
    n3 = nArgs[3];
    n4 = nArgs[4];

    // Section 38/29/30: determine the number of feasible points, and allocate the graph.
    long nverts = countFeasiblePoints(n, d);
    if (nverts < 0) {
      return null;
    }
    Graph newGraph = Gb.newGraph(nverts);
    if (newGraph == null) {
      Gb.panicCode = Gb.NO_ROOM;
      Gb.troubleCode = 0;
      return null;
    }
    newGraph.id =
        "subsets("
            + Long.toUnsignedString(n)
            + ","
            + n0
            + ","
            + n1
            + ","
            + n2
            + ","
            + n3
            + ","
            + n4
            + ",0x"
            + Long.toHexString(sizeBits)
            + ","
            + (directed != 0 ? 1 : 0)
            + ")";
    newGraph.utilTypes = "ZZZIIIZZZZZZZZ"; // hash table will not be used

    // Section 39: name the subsets and create the arcs or edges.
    Vertex[] verts = newGraph.vertices;
    int vi = 0;
    yy[(int) (d + 1)] = 0;
    sig[0] = n;
    for (k = d; k >= 0; k--) {
      yy[(int) k] = yy[(int) (k + 1)] + nn[(int) k];
    }
    if (yy[0] >= n) {
      k = 0;
      xx[0] = yy[1] >= n ? 0 : n - yy[1];
      while (true) {
        // Section 32: complete the partial solution (x0,...,xk).
        if (completePartialSolution(k, d)) {
          return null;
        }
        v = verts[vi];
        // Section 34: assign a symbolic name for (x0,...,xd) to vertex v.
        assignSimplexName(v, d);
        // Section 40: create arcs or edges from previous subsets to v, by brute-force comparison.
        for (int ui = 0; ui <= vi; ui++) {
          Vertex u = verts[ui];
          long ss = subsetIntersectionSize(u.name, d);
          if (ss < 64 && (sizeBits & (1L << ss)) != 0) {
            if (directed != 0) {
              Gb.newArc(u, v, 1L);
            } else {
              Gb.newEdge(u, v, 1L);
            }
          }
        }
        vi++;
        // Section 33: advance to the next partial solution, or stop (goto last).
        k = advanceToNextSolution(d);
        if (k < 0) {
          break;
        }
      }
    }
    if (vi != newGraph.n) {
      Gb.panicCode = Gb.IMPOSSIBLE;
      Gb.troubleCode = 0;
      return null;
    }

    if (Gb.troubleCode != 0) {
      Gb.recycle(newGraph);
      Gb.panicCode = Gb.ALLOC_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    return newGraph;
  }

  /**
   * {@code short_imap}: section 51's 92-character alphabet used by {@link #perms} to encode
   * multiset elements as single characters &mdash; the GraphBase name alphabet without the four
   * characters that would need quoting within a string ({@code \}, {@code "}, space, newline).
   */
  private static final String SHORT_IMAP =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
          + "abcdefghijklmnopqrstuvwxyz_^~&@,;.:?!%#$+-*/|<=>()[]{}`'";

  /**
   * {@code perms(n0,n1,n2,n3,n4,maxInv,directed)}: vertices are the permutations of a multiset
   * described by {@code n0..n4} (interpreted exactly as in {@link #simplex}/{@link #subsets}, via
   * {@link #normalizeSimplexArgs}, borrowing that code with {@code n} temporarily set to {@link
   * #BUF_SIZE}) that have at most {@code maxInv} inversions; two permutations are adjacent when one
   * is obtained from the other by swapping two adjacent elements. If {@code maxInv} is zero or
   * exceeds the maximum possible number of inversions, it is reset to that maximum, and the graph's
   * id reflects the adjusted value. {@code directed} restricts arcs to permutations with exactly
   * one more inversion when nonzero. Returns {@code null} and sets {@link Gb#panicCode} on failure
   * (too many elements in the multiset, or too big).
   */
  public static Graph perms(
      long n0, long n1, long n2, long n3, long n4, long maxInv, long directed) {
    long i;
    long j;
    long k;
    long s;
    Vertex v;
    long n;

    // Section 44: normalize the permutation parameters, then reuse section 27 with n = BUF_SIZE.
    if (n0 == 0) {
      n0 = 1;
      n1 = 0;
    } else if (n0 < 0) {
      n1 = n0;
      n0 = 1;
    }
    n = BUF_SIZE;
    long[] nArgs = {n0, n1, n2, n3, n4};
    long d = normalizeSimplexArgs(n, nArgs);
    if (d < 0) {
      return null;
    }
    n0 = nArgs[0];
    n1 = nArgs[1];
    n2 = nArgs[2];
    n3 = nArgs[3];
    n4 = nArgs[4];

    // Section 45: determine n (total elements) and the maximum possible number of inversions.
    long ss = 0;
    s = 0;
    for (k = 0; k <= d; k++) {
      if (nn[(int) k] >= BUF_SIZE) {
        Gb.panicCode = Gb.BAD_SPECS;
        Gb.troubleCode = 0;
        return null;
      }
      ss += s * nn[(int) k];
      s += nn[(int) k];
    }
    if (s >= BUF_SIZE) {
      Gb.panicCode = Gb.BAD_SPECS + 1;
      Gb.troubleCode = 0;
      return null;
    }
    n = s;
    if (maxInv == 0 || maxInv > ss) {
      maxInv = ss;
    }

    // Sections 46/47: create a graph with one vertex for each permutation.
    long[] coef = new long[(int) maxInv + 1];
    coef[0] = 1;
    s = nn[0];
    for (j = 1; j <= d; j++) {
      for (k = 1; k <= nn[(int) j]; k++) {
        i = maxInv;
        long ii = i - k - s;
        for (; ii >= 0; ii--, i--) {
          coef[(int) i] -= coef[(int) ii];
        }
        i = k;
        ii = 0;
        for (; i <= maxInv; i++, ii++) {
          coef[(int) i] += coef[(int) ii];
          if (coef[(int) i] > 1000000000) {
            Gb.panicCode = Gb.VERY_BAD_SPECS + 1;
            Gb.troubleCode = 0;
            return null;
          }
        }
      }
      s += nn[(int) j];
    }
    long nverts = 1;
    for (k = 1; k <= maxInv; k++) {
      nverts += coef[(int) k];
      if (nverts > 1000000000) {
        Gb.panicCode = Gb.VERY_BAD_SPECS;
        Gb.troubleCode = 0;
        return null;
      }
    }
    Graph newGraph = Gb.newGraph(nverts);
    if (newGraph == null) {
      Gb.panicCode = Gb.NO_ROOM;
      Gb.troubleCode = 0;
      return null;
    }
    newGraph.id =
        "perms("
            + n0
            + ","
            + n1
            + ","
            + n2
            + ","
            + n3
            + ","
            + n4
            + ","
            + Long.toUnsignedString(maxInv)
            + ","
            + (directed != 0 ? 1 : 0)
            + ")";
    newGraph.utilTypes = "VVZZZZZZZZZZZZ"; // hash table will be used

    // Sections 48-53: name the permutations and create the arcs or edges. xtab/ytab/ztab are
    // local scratch, allocated fresh per call as in the C's gb_typed_alloc (not the shared static
    // nn/xx/yy/sig fields); Java's zero-initialized arrays match the C's calloc-backed allocator.
    long[] xtab = new long[(int) n + 1];
    long[] ytab = new long[(int) n + 1];
    long[] ztab = new long[(int) n + 1];
    long m = 0;
    j = 0;
    s = nn[0];
    for (k = 1; ; k++) {
      xtab[(int) k] = ztab[(int) k] = j;
      if (k == s) {
        j++;
        if (j > d) {
          break;
        }
        s += nn[(int) j];
      }
    }

    Vertex[] verts = newGraph.vertices;
    int vi = 0;
    // Section 52's buffer, sized exactly n long: jsgb always starts from a zero-filled array and
    // never leaves it un-terminated across calls, so it never carries the C's cross-call garbage.
    char[] buf = new char[(int) n];
    outer:
    while (true) {
      v = verts[vi];
      // Section 52: assign a symbolic name for (x1,...,xn) to vertex v.
      for (k = 1; k <= n; k++) {
        buf[(int) (k - 1)] = SHORT_IMAP.charAt((int) xtab[(int) k]);
      }
      v.name = Gb.saveString(new String(buf));
      Gb.hashIn(v);

      // Section 53: create arcs or edges from previous permutations to v.
      for (j = 1; j < n; j++) {
        if (xtab[(int) j] > xtab[(int) (j + 1)]) {
          char saveA = buf[(int) (j - 1)];
          char saveB = buf[(int) j];
          buf[(int) (j - 1)] = SHORT_IMAP.charAt((int) xtab[(int) (j + 1)]);
          buf[(int) j] = SHORT_IMAP.charAt((int) xtab[(int) j]);
          Vertex u = Gb.hashOut(new String(buf));
          if (u == null) {
            Gb.panicCode = Gb.IMPOSSIBLE + 2;
            Gb.troubleCode = 0;
            return null;
          }
          if (directed != 0) {
            Gb.newArc(u, v, 1L);
          } else {
            Gb.newEdge(u, v, 1L);
          }
          buf[(int) (j - 1)] = saveA;
          buf[(int) j] = saveB;
        }
      }
      vi++;

      // Section 49: advance to the next permutation, or stop if there are no more.
      boolean move = false;
      for (k = n; k >= 1; k--) {
        if (m < maxInv && ytab[(int) k] < k - 1) {
          if (ytab[(int) k] < ytab[(int) (k - 1)] || ztab[(int) k] > ztab[(int) (k - 1)]) {
            move = true;
            break;
          }
        }
        if (ytab[(int) k] != 0) {
          for (j = k - ytab[(int) k]; j < k; j++) {
            xtab[(int) j] = xtab[(int) (j + 1)];
          }
          m -= ytab[(int) k];
          ytab[(int) k] = 0;
          xtab[(int) k] = ztab[(int) k];
        }
      }
      if (!move) {
        break outer;
      }
      j = k - ytab[(int) k];
      xtab[(int) j] = xtab[(int) (j - 1)];
      xtab[(int) (j - 1)] = ztab[(int) k];
      ytab[(int) k]++;
      m++;
    }
    if (vi != newGraph.n) {
      Gb.panicCode = Gb.IMPOSSIBLE;
      Gb.troubleCode = 0;
      return null;
    }

    if (Gb.troubleCode != 0) {
      Gb.recycle(newGraph);
      Gb.panicCode = Gb.ALLOC_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    return newGraph;
  }

  /**
   * {@code parts(n,maxParts,maxSize,directed)}: vertices are the partitions of {@code n} into at
   * most {@code maxParts} parts, each part at most {@code maxSize}; two partitions are adjacent
   * when one is obtained from the other by combining two parts. {@code maxParts}/{@code maxSize}
   * default to {@code n} when zero or greater than {@code n}. {@code directed} restricts arcs to
   * partitions with exactly one more part when nonzero. Returns {@code null} and sets {@link
   * Gb#panicCode} on failure (too many parts allowed, or too big).
   */
  public static Graph parts(long n, long maxParts, long maxSize, long directed) {
    long i;
    long j;
    long k;
    long s;
    long d;
    Vertex v;

    if (maxParts == 0 || maxParts > n) {
      maxParts = n;
    }
    if (maxSize == 0 || maxSize > n) {
      maxSize = n;
    }
    if (maxParts > MAX_D) {
      Gb.panicCode = Gb.BAD_SPECS;
      Gb.troubleCode = 0;
      return null;
    }

    // Section 56: create a graph with one vertex for each partition.
    long[] coef = new long[(int) n + 1];
    coef[0] = 1;
    for (k = 1; k <= maxParts; k++) {
      for (j = n, i = n - k - maxSize; i >= 0; i--, j--) {
        coef[(int) j] -= coef[(int) i];
      }
      for (j = k, i = 0; j <= n; i++, j++) {
        coef[(int) j] += coef[(int) i];
        if (coef[(int) j] > 1000000000) {
          Gb.panicCode = Gb.VERY_BAD_SPECS;
          Gb.troubleCode = 0;
          return null;
        }
      }
    }
    long nverts = coef[(int) n];
    Graph newGraph = Gb.newGraph(nverts);
    if (newGraph == null) {
      Gb.panicCode = Gb.NO_ROOM;
      Gb.troubleCode = 0;
      return null;
    }
    newGraph.id =
        "parts("
            + Long.toUnsignedString(n)
            + ","
            + Long.toUnsignedString(maxParts)
            + ","
            + Long.toUnsignedString(maxSize)
            + ","
            + (directed != 0 ? 1 : 0)
            + ")";
    newGraph.utilTypes = "VVZZZZZZZZZZZZ"; // hash table will be used

    // Sections 57-62: name the partitions and create the arcs or edges.
    Vertex[] verts = newGraph.vertices;
    int vi = 0;
    xx[0] = maxSize;
    sig[1] = n;
    s = 1;
    for (k = maxParts; k > 0; k--, s++) {
      yy[(int) k] = s;
    }
    if (maxSize * maxParts >= n) {
      k = 1;
      xx[1] = (n - 1) / maxParts + 1;
      outer:
      while (true) {
        // Section 58: complete the partial solution (x1,...,xk).
        s = sig[(int) k] - xx[(int) k];
        k++;
        for (; s != 0; k++) {
          sig[(int) k] = s;
          xx[(int) k] = (s - 1) / yy[(int) k] + 1;
          s -= xx[(int) k];
        }
        d = k - 1;

        v = verts[vi];
        // Section 61: assign the name x1+...+xd to vertex v.
        StringBuilder name = new StringBuilder();
        for (k = 1; k <= d; k++) {
          name.append('+').append(xx[(int) k]);
        }
        v.name = Gb.saveString(name.substring(1));
        Gb.hashIn(v);

        // Sections 61/62: create arcs or edges from v to previous partitions.
        if (d < maxParts) {
          xx[(int) (d + 1)] = 0;
          for (j = 1; j <= d; j++) {
            if (xx[(int) j] != xx[(int) (j + 1)]) {
              long b = xx[(int) j] / 2;
              long a = xx[(int) j] - b;
              for (; b != 0; a++, b--) {
                if (assignPartitionSplit(v, j, a, b, d, directed)) {
                  return null;
                }
              }
            }
            nn[(int) j] = xx[(int) j];
          }
        }
        vi++;

        // Section 59: advance to the next partial solution, or stop if there are no more.
        if (d == 1) {
          break outer;
        }
        k = d - 1;
        boolean advanced = false;
        while (true) {
          if (xx[(int) k] < sig[(int) k] && xx[(int) k] < xx[(int) (k - 1)]) {
            advanced = true;
            break;
          }
          if (k == 1) {
            break;
          }
          k--;
        }
        if (!advanced) {
          break outer;
        }
        xx[(int) k]++;
      }
    }
    if (vi != newGraph.n) {
      Gb.panicCode = Gb.IMPOSSIBLE;
      Gb.troubleCode = 0;
      return null;
    }

    if (Gb.troubleCode != 0) {
      Gb.recycle(newGraph);
      Gb.panicCode = Gb.ALLOC_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    return newGraph;
  }

  /**
   * {@code binary(n,maxHeight,directed)}: vertices are the binary trees with {@code n} internal
   * nodes, all leaves at a height at most {@code maxHeight} from the root; two trees are adjacent
   * when one is obtained from the other by a single application of the associative law (a
   * "rotation"). {@code maxHeight} defaults to {@code n} when zero. {@code directed} makes rotation
   * arcs go from a tree containing {@code (a.b).c} to one containing {@code a.(b.c)} in its place
   * when nonzero. Returns {@code null} and sets {@link Gb#panicCode} on failure (too many nodes,
   * height over 30, or too big).
   */
  public static Graph binary(long n, long maxHeight, long directed) {
    long i;
    long j;
    long k;
    long s;
    long d;
    Vertex v;

    if (2 * n + 2 > BUF_SIZE) {
      Gb.panicCode = Gb.BAD_SPECS;
      Gb.troubleCode = 0;
      return null;
    }
    if (maxHeight == 0 || maxHeight > n) {
      maxHeight = n;
    }
    if (maxHeight > 30) {
      Gb.panicCode = Gb.VERY_BAD_SPECS;
      Gb.troubleCode = 0;
      return null;
    }

    // Sections 65/66: determine the number of vertices, one per binary tree.
    long nverts;
    if (n >= 20 && maxHeight >= 6) {
      // Section 66: compute nverts using the R series, to avoid overflow.
      d = (1L << maxHeight) - 1 - n;
      if (d > 8) {
        Gb.panicCode = Gb.BAD_SPECS + 1;
        Gb.troubleCode = 0;
        return null;
      }
      if (d < 0) {
        nverts = 0;
      } else {
        nn[0] = nn[1] = 1;
        for (k = 2; k <= d; k++) {
          nn[(int) k] = 0;
        }
        for (j = 2; j <= maxHeight; j++) {
          for (k = d; k > 0; k--) {
            float ss = 0.0f;
            for (i = k; i >= 0; i--) {
              ss += ((float) nn[(int) i]) * ((float) nn[(int) (k - i)]);
            }
            if (ss > MAX_NNN) {
              Gb.panicCode = Gb.VERY_BAD_SPECS + 1;
              Gb.troubleCode = 0;
              return null;
            }
            s = 0;
            for (i = k; i >= 0; i--) {
              s += nn[(int) i] * nn[(int) (k - i)];
            }
            nn[(int) k] = s;
          }
          i = (1L << j) - 1;
          if (i <= d) {
            nn[(int) i]++;
          }
        }
        nverts = nn[(int) d];
      }
    } else {
      // The C reuses the shared static nn (sized MAX_D+2) here, which is safe for it only because
      // 2n + 2 > BUF_SIZE already bounds n well below MAX_D for any n this branch can still reach
      // when maxHeight is small; a legal n up to BUF_SIZE/2 - 1 (with maxHeight < 6) would overflow
      // that shared array, so this branch uses its own local array of exactly the size it needs.
      long[] local = new long[(int) n + 2];
      local[0] = local[1] = 1;
      for (j = 2; j <= maxHeight; j++) {
        for (k = n - 1; k > 0; k--) {
          s = 0;
          for (i = k; i >= 0; i--) {
            s += local[(int) i] * local[(int) (k - i)];
          }
          local[(int) (k + 1)] = s;
        }
      }
      nverts = local[(int) n];
    }

    Graph newGraph = Gb.newGraph(nverts);
    if (newGraph == null) {
      Gb.panicCode = Gb.NO_ROOM;
      Gb.troubleCode = 0;
      return null;
    }
    newGraph.id =
        "binary("
            + Long.toUnsignedString(n)
            + ","
            + Long.toUnsignedString(maxHeight)
            + ","
            + (directed != 0 ? 1 : 0)
            + ")";
    newGraph.utilTypes = "VVZZZZZZZZZZZZ"; // hash table will be used

    // Sections 67-72: name the trees and create the arcs or edges. xtab/ytab/ltab/stab are local
    // scratch, allocated fresh per call as in the C's gb_typed_alloc (not a shared static field).
    d = n + n;
    long[] xtab = new long[(int) d + 1];
    long[] ytab = new long[(int) d + 1];
    long[] ltab = new long[(int) d + 1];
    long[] stab = new long[(int) d + 1];
    ltab[0] = 1L << maxHeight;
    stab[0] = n; // ytab[0] = 0, already zero-initialized

    Vertex[] verts = newGraph.vertices;
    int vi = 0;
    if (ltab[0] > n) {
      k = 0;
      xtab[0] = n != 0 ? 1 : 0;
      outer:
      while (true) {
        // Section 70: complete the partial tree x0...xk.
        for (j = k + 1; j <= d; j++) {
          if (xtab[(int) (j - 1)] != 0) {
            ltab[(int) j] = ltab[(int) (j - 1)] >> 1;
            ytab[(int) j] = ytab[(int) (j - 1)] + ltab[(int) j];
            stab[(int) j] = stab[(int) (j - 1)];
          } else {
            ytab[(int) j] = ytab[(int) (j - 1)] & (ytab[(int) (j - 1)] - 1);
            ltab[(int) j] = ytab[(int) (j - 1)] - ytab[(int) j];
            stab[(int) j] = stab[(int) (j - 1)] - 1;
          }
          xtab[(int) j] = stab[(int) j] <= ytab[(int) j] ? 0 : 1;
        }

        v = verts[vi];
        // Section 71: assign a Polish prefix code name to vertex v (d+1 characters, '.'/'x').
        char[] name = new char[(int) d + 1];
        for (k = 0; k <= d; k++) {
          name[(int) k] = xtab[(int) k] != 0 ? '.' : 'x';
        }
        v.name = Gb.saveString(new String(name));
        Gb.hashIn(v);

        // Section 72: create arcs or edges from v to previous trees.
        for (j = 0; j < d; j++) {
          if (xtab[(int) j] == 1 && xtab[(int) (j + 1)] == 1) {
            long ii = j + 1;
            s = 0;
            while (s >= 0) {
              xtab[(int) ii] = xtab[(int) (ii + 1)];
              s += (xtab[(int) (ii + 1)] << 1) - 1;
              ii++;
            }
            xtab[(int) ii] = 1;
            char[] otherName = new char[(int) d + 1];
            for (k = 0; k <= d; k++) {
              otherName[(int) k] = xtab[(int) k] != 0 ? '.' : 'x';
            }
            Vertex u = Gb.hashOut(new String(otherName));
            if (u != null) {
              if (directed != 0) {
                Gb.newArc(v, u, 1L);
              } else {
                Gb.newEdge(v, u, 1L);
              }
            }
            for (ii--; ii > j; ii--) {
              xtab[(int) (ii + 1)] = xtab[(int) ii];
            }
            xtab[(int) (ii + 1)] = 1;
          }
        }
        vi++;

        // Section 69: advance to the next partial tree, or stop if there are no more.
        k = d - 1;
        while (true) {
          if (k <= 0) {
            break outer; // happens only when n <= 1
          }
          if (xtab[(int) k] != 0) {
            break; // find rightmost 1
          }
          k--;
        }
        k--;
        boolean advanced = false;
        while (true) {
          if (xtab[(int) k] == 0 && ltab[(int) k] > 1) {
            advanced = true;
            break;
          }
          if (k == 0) {
            break;
          }
          k--;
        }
        if (!advanced) {
          break outer;
        }
        xtab[(int) k]++;
      }
    }
    if (vi != newGraph.n) {
      Gb.panicCode = Gb.IMPOSSIBLE;
      Gb.troubleCode = 0;
      return null;
    }

    if (Gb.troubleCode != 0) {
      Gb.recycle(newGraph);
      Gb.panicCode = Gb.ALLOC_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    return newGraph;
  }

  /**
   * {@code complement(g,copy,self,directed)}: a graph with the same vertices as {@code g} but
   * complemented arcs &mdash; {@code u} and {@code v} are adjacent in the result exactly when they
   * were not adjacent in {@code g}. If {@code self} is nonzero, a vertex gets a self-loop in the
   * result exactly when it lacked one in {@code g} (and never otherwise). If {@code copy} is
   * nonzero, a double complement is done instead: the result reproduces {@code g}'s own adjacency
   * (arcs and, if {@code self} allows it, self-loops), with duplicate arcs removed and lengths
   * reset to 1. If {@code directed} is nonzero, the result is directed; otherwise {@code g} is
   * assumed undirected and the result is too. Returns {@code null} and sets {@link Gb#panicCode} on
   * failure ({@code g} missing, or out of memory).
   */
  public static Graph complement(Graph g, long copy, long self, long directed) {
    if (g == null) {
      Gb.panicCode = Gb.MISSING_OPERAND;
      Gb.troubleCode = 0;
      return null;
    }
    Graph newGraph = copyVertexNames(g);
    if (newGraph == null) {
      return null;
    }
    Gb.makeCompoundId(
        newGraph,
        "complement(",
        g,
        "," + flag(copy) + "," + flag(self) + "," + flag(directed) + ")");

    // Section 76: insert complementary arcs or edges.
    int n = (int) g.n;
    Vertex[] newVerts = newGraph.vertices;
    Vertex[] gVerts = g.vertices;
    for (int i = 0; i < n; i++) {
      Vertex v = gVerts[i];
      Vertex u = newVerts[i];
      for (Arc a = v.arcs; a != null; a = a.next) {
        newVerts[a.tip.index].u.V(u);
      }
      if (directed != 0) {
        for (int j = 0; j < n; j++) {
          Vertex vv = newVerts[j];
          if ((vv.u.V() == u && copy != 0) || (vv.u.V() != u && copy == 0)) {
            if (vv != u || self != 0) {
              Gb.newArc(u, vv, 1L);
            }
          }
        }
      } else {
        for (int j = (self != 0 ? i : i + 1); j < n; j++) {
          Vertex vv = newVerts[j];
          if ((vv.u.V() == u && copy != 0) || (vv.u.V() != u && copy == 0)) {
            Gb.newEdge(u, vv, 1L);
          }
        }
      }
    }
    for (int i = 0; i < n; i++) {
      newVerts[i].u.V(null);
    }

    if (Gb.troubleCode != 0) {
      Gb.recycle(newGraph);
      Gb.panicCode = Gb.ALLOC_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    return newGraph;
  }

  /**
   * {@code gunion(g,gg,multi,directed)}: a graph with the vertices and arcs of {@code g} together
   * with the arcs of {@code gg}. {@code gg} is assumed to have the same vertices as {@code g}
   * (matched by position); if {@code gg} has more vertices, the extras and every arc touching them
   * are ignored. Both inputs are assumed undirected unless {@code directed} is nonzero. If {@code
   * multi} is nonzero, multiple arcs between the same pair are all reproduced (with their original
   * lengths); otherwise at most one survives (the shortest). Returns {@code null} and sets {@link
   * Gb#panicCode} on failure ({@code g} or {@code gg} missing, or out of memory).
   */
  public static Graph gunion(Graph g, Graph gg, long multi, long directed) {
    if (g == null || gg == null) {
      Gb.panicCode = Gb.MISSING_OPERAND;
      Gb.troubleCode = 0;
      return null;
    }
    Graph newGraph = copyVertexNames(g);
    if (newGraph == null) {
      return null;
    }
    Gb.makeDoubleCompoundId(
        newGraph, "gunion(", g, ",", gg, "," + flag(multi) + "," + flag(directed) + ")");

    // Sections 79-80: insert arcs or edges present in either g or gg.
    int n = (int) g.n;
    int ggN = (int) gg.n;
    Vertex[] newVerts = newGraph.vertices;
    Vertex[] gVerts = g.vertices;
    Vertex[] ggVerts = gg.vertices;
    for (int i = 0; i < n; i++) {
      Vertex v = gVerts[i];
      Vertex vv = newVerts[i];
      for (Arc a = v.arcs; a != null; a = a.next) {
        Vertex u = newVerts[a.tip.index];
        a = unionArc(vv, u, a, multi, directed);
      }
      if (i < ggN) {
        Vertex vvv = ggVerts[i];
        for (Arc a = vvv.arcs; a != null; a = a.next) {
          if (a.tip.index < n) {
            Vertex u = newVerts[a.tip.index];
            a = unionArc(vv, u, a, multi, directed);
          }
        }
      }
    }
    for (int i = 0; i < n; i++) {
      newVerts[i].u.V(null);
      newVerts[i].z.A(null);
    }

    if (Gb.troubleCode != 0) {
      Gb.recycle(newGraph);
      Gb.panicCode = Gb.ALLOC_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    return newGraph;
  }

  /**
   * {@code intersection(g,gg,multi,directed)}: a graph with the vertices of {@code g} but only the
   * arcs that appear in both {@code g} and {@code gg} (matched by vertex position; extra vertices
   * of {@code gg} beyond {@code g}'s count, and arcs touching them, are ignored). Both inputs are
   * assumed undirected unless {@code directed} is nonzero. If {@code multi} is nonzero, the result
   * may have multiple arcs between a pair &mdash; the smaller of the two inputs' multiplicities,
   * each arc's length the larger of the two sides' minimum lengths; otherwise at most one survives
   * (the smallest such maximum). Returns {@code null} and sets {@link Gb#panicCode} on failure
   * ({@code g} or {@code gg} missing, or out of memory).
   */
  public static Graph intersection(Graph g, Graph gg, long multi, long directed) {
    if (g == null || gg == null) {
      Gb.panicCode = Gb.MISSING_OPERAND;
      Gb.troubleCode = 0;
      return null;
    }
    Graph newGraph = copyVertexNames(g);
    if (newGraph == null) {
      return null;
    }
    Gb.makeDoubleCompoundId(
        newGraph, "intersection(", g, ",", gg, "," + flag(multi) + "," + flag(directed) + ")");

    // Sections 82-86: insert arcs or edges present in both g and gg.
    int n = (int) g.n;
    int ggN = (int) gg.n;
    Vertex[] newVerts = newGraph.vertices;
    Vertex[] gVerts = g.vertices;
    Vertex[] ggVerts = gg.vertices;
    for (int i = 0; i < n; i++) {
      if (i >= ggN) {
        continue;
      }
      Vertex v = gVerts[i];
      Vertex vv = newVerts[i];
      Vertex vvv = ggVerts[i];

      // Section 85: take note of all arcs from v.
      for (Arc a = v.arcs; a != null; a = a.next) {
        Vertex u = newVerts[a.tip.index];
        if (u.u.V() == vv) {
          u.v.I++;
          if (a.len < u.w.I) {
            u.w.I = a.len;
          }
        } else {
          u.u.V(vv);
          u.v.I = 0;
          u.w.I = a.len;
        }
        if (u == vv && directed == 0 && Gb.isFirstOfSelfLoop(a)) {
          a = a.mate;
        }
      }

      for (Arc a = vvv.arcs; a != null; a = a.next) {
        if (a.tip.index >= n) {
          continue;
        }
        Vertex u = newVerts[a.tip.index];
        if (u.u.V() != vv) {
          continue;
        }
        long l = u.w.I;
        if (a.len > l) {
          l = a.len;
        }
        if (u.v.I < 0) {
          // Section 84: update the minimum of multiple maxima.
          Arc b = u.z.A();
          if (l < b.len) {
            b.len = l;
            if (directed == 0) {
              b.mate.len = l;
            }
          }
        } else {
          // Section 83: generate a new arc or edge for the intersection.
          if (directed != 0) {
            Gb.newArc(vv, u, l);
          } else {
            if (vv.index <= u.index) {
              Gb.newEdge(vv, u, l);
            }
            if (vv == u && Gb.isFirstOfSelfLoop(a)) {
              a = a.mate;
            }
          }
          if (multi == 0) {
            u.z.A(vv.arcs);
            u.v.I = -1;
          } else if (u.v.I == 0) {
            u.u.V(null);
          } else {
            u.v.I--;
          }
        }
      }
    }

    // Section 86: clear out the temporary utility fields.
    for (int i = 0; i < n; i++) {
      Vertex nv = newVerts[i];
      nv.u.V(null);
      nv.z.A(null);
      nv.v.I = 0;
      nv.w.I = 0;
    }

    if (Gb.troubleCode != 0) {
      Gb.recycle(newGraph);
      Gb.panicCode = Gb.ALLOC_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    return newGraph;
  }

  /**
   * {@code lines(g,directed)}: the line graph of {@code g}. If {@code directed} is zero, the result
   * has one vertex for each edge of (undirected) {@code g}, two vertices adjacent exactly when the
   * corresponding edges share an endpoint. If {@code directed} is nonzero, the result has one
   * vertex for each arc of (directed) {@code g}, with an arc from {@code u} to {@code v} when the
   * arc for {@code u} ends where the arc for {@code v} begins. All arcs of the result have length
   * 1. Utility fields {@code u.V} and {@code v.V} of each result vertex point back to the {@code
   * g}-vertices that define its arc or edge, and {@code w.A} points to the {@code g}-arc itself
   * ({@code u.V <= v.V} in the undirected case); {@link Graph#utilTypes} is left at its default,
   * since these are pointers into {@code g} rather than data the graph owns. Returns {@code null}
   * and sets {@link Gb#panicCode} on failure ({@code g} missing, out of memory, or {@code g} does
   * not obey the conventions for an undirected graph when {@code directed} is zero).
   */
  public static Graph lines(Graph g, long directed) {
    if (g == null) {
      Gb.panicCode = Gb.MISSING_OPERAND;
      Gb.troubleCode = 0;
      return null;
    }
    // Section 89: set up a graph whose vertices are the lines of g.
    long m = directed != 0 ? g.m : g.m / 2;
    Graph newGraph = Gb.newGraph(m);
    if (newGraph == null) {
      Gb.panicCode = Gb.NO_ROOM;
      Gb.troubleCode = 0;
      return null;
    }
    Gb.makeCompoundId(newGraph, "lines(", g, directed != 0 ? ",1)" : ",0)");

    if (buildLineVertices(newGraph, g, m, directed)) {
      return null; // near_panic already recovered and panicked
    }
    if (directed != 0) {
      insertDirectedLineArcs(newGraph, m); // section 92
    } else {
      insertUndirectedLineEdges(newGraph, g, m); // section 93
    }
    restoreLinesPristine(newGraph, m, directed); // section 88

    if (Gb.troubleCode != 0) {
      Gb.recycle(newGraph);
      Gb.panicCode = Gb.ALLOC_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    return newGraph;
  }

  /**
   * Section 89 (with section 91 folded in): walks {@code g}'s vertices from last to first, turning
   * each surviving arc into a new vertex of {@code newGraph}. Temporarily rewires each processed
   * edge's mate tip to point at its new vertex (undone by {@link #restoreLinesPristine}) and, for
   * the first new vertex touching a given {@code g}-vertex {@code v}, moves {@code v}'s {@code z}
   * slot into that new vertex's {@code z} slot so it can be found again as {@code v.map} and
   * restored later. Returns {@code true} if it had to recover via {@link #linesNearPanic} (bad
   * data: {@code g} does not obey the undirected-graph conventions, or its vertex/arc counts don't
   * match what was declared).
   */
  private static boolean buildLineVertices(Graph newGraph, Graph g, long m, long directed) {
    Vertex[] newVerts = newGraph.vertices;
    Vertex[] gVerts = g.vertices;
    int ui = 0;
    for (int vi = (int) g.n - 1; vi >= 0; vi--) {
      Vertex v = gVerts[vi];
      boolean mapped = false;
      for (Arc a = v.arcs; a != null; a = a.next) {
        Vertex vv = a.tip;
        if (directed == 0) {
          if (vv.index < v.index) {
            continue;
          }
          if (vv.index >= g.n || gVerts[vv.index] != vv) {
            linesNearPanic(newGraph, ui, directed);
            return true;
          }
        }
        Vertex u = newVerts[ui];
        u.u.V(v);
        u.v.V(vv);
        u.w.A(a);
        if (directed == 0) {
          if (ui >= m || a.mate == null || a.mate.tip != v) {
            linesNearPanic(newGraph, ui, directed);
            return true;
          }
          if (v == vv && Gb.isFirstOfSelfLoop(a)) {
            a = a.mate; // skip second half of self-loop
          } else {
            a.mate.tip = u;
          }
        }
        u.name =
            Gb.saveString(
                prefix(v.name, (BUF_SIZE - 3) / 2)
                    + (directed != 0 ? "->" : "--")
                    + prefix(vv.name, BUF_SIZE / 2 - 1));
        if (!mapped) {
          u.z.V(v.z.V()); // u.map = v.map, whatever v's z slot held before
          v.z.V(u); // v.map = u
          mapped = true;
        }
        ui++;
      }
    }
    if (ui != m) {
      linesNearPanic(newGraph, ui, directed);
      return true;
    }
    return false;
  }

  /**
   * Section 90: recovers from bad data found while building {@code newGraph}'s vertices &mdash;
   * restores {@code g} (over just the {@code filled} vertices actually built), recycles {@code
   * newGraph}, and panics {@link Gb#INVALID_OPERAND}.
   */
  private static void linesNearPanic(Graph newGraph, long filled, long directed) {
    restoreLinesPristine(newGraph, filled, directed);
    Gb.recycle(newGraph);
    Gb.panicCode = Gb.INVALID_OPERAND;
    Gb.troubleCode = 0;
  }

  /**
   * Section 88: restores {@code g} to its pristine original condition after {@link
   * #buildLineVertices} borrowed each touched vertex's {@code z} slot &mdash; puts back the value
   * {@code buildLineVertices} saved in each first new vertex's {@code z} slot, and (undirected
   * only) restores each new vertex's {@code g}-arc's mate's tip to the original vertex.
   */
  private static void restoreLinesPristine(Graph newGraph, long filled, long directed) {
    Vertex[] newVerts = newGraph.vertices;
    Vertex v = null;
    for (int ui = 0; ui < filled; ui++) {
      Vertex u = newVerts[ui];
      if (u.u.V() != v) {
        v = u.u.V();
        v.z.V(u.z.V()); // restore v's original z slot
        u.z.V(null);
      }
      if (directed == 0) {
        u.w.A().mate.tip = v;
      }
    }
  }

  /**
   * Section 92: for each new vertex {@code u} representing an arc {@code v -> vv} of {@code g},
   * adds an arc to every new vertex representing one of {@code vv}'s own surviving arcs (found via
   * {@code vv.map}, walking forward through {@code newGraph}'s vertices while they still represent
   * arcs from {@code vv}).
   */
  private static void insertDirectedLineArcs(Graph newGraph, long m) {
    Vertex[] newVerts = newGraph.vertices;
    for (int ui = 0; ui < m; ui++) {
      Vertex u = newVerts[ui];
      Vertex v = u.v.V();
      if (v.arcs != null) {
        Vertex mapped = v.z.V();
        int vi = mapped.index;
        do {
          Gb.newArc(u, newVerts[vi], 1L);
          vi++;
        } while (newVerts[vi].u.V() == v);
      }
    }
  }

  /**
   * Section 93: for each new vertex {@code u} representing an edge {@code {v, vv}} of {@code g},
   * adds an edge to every earlier-built new vertex representing another edge sharing {@code v} or
   * {@code vv}. The first vertex's prior lines are found via {@code v.map}, walking forward through
   * {@code newGraph}'s vertices below {@code u}; the second vertex's prior lines are found by
   * scanning its (partially rewired) arc list, where an already-processed arc's tip now points into
   * {@code newGraph} instead of {@code g}. The C's {@code vv >= v && vv < g->vertices + g->n} test
   * (is {@code vv} still an unrewired vertex of {@code g}?) is translated as {@code vv.index >=
   * v.index && vv.index < g.n && g.vertices[vv.index] == vv}; the last conjunct is required because
   * an already-rewired arc's tip is a {@code newGraph} vertex whose {@code index} can coincide with
   * a legitimate {@code g}-vertex index.
   */
  private static void insertUndirectedLineEdges(Graph newGraph, Graph g, long m) {
    Vertex[] newVerts = newGraph.vertices;
    for (int ui = 0; ui < m; ui++) {
      Vertex u = newVerts[ui];
      boolean mapped = false;
      Vertex v = u.u.V(); // look first for prior lines that touch the first vertex
      for (Vertex vv = v.z.V(); vv.index < u.index; vv = newVerts[vv.index + 1]) {
        Gb.newEdge(u, vv, 1L);
      }
      v = u.v.V(); // then look for prior lines that touch the other one
      for (Arc a = v.arcs; a != null; a = a.next) {
        Vertex vv = a.tip;
        if (vv.index < u.index && newVerts[vv.index] == vv) {
          Gb.newEdge(u, vv, 1L);
        } else if (vv.index >= v.index && vv.index < g.n && g.vertices[vv.index] == vv) {
          mapped = true;
        }
      }
      if (mapped && v.index > u.u.V().index) {
        for (Vertex vv = v.z.V(); vv.u.V() == v; vv = newVerts[vv.index + 1]) {
          Gb.newEdge(u, vv, 1L);
        }
      }
    }
  }

  /**
   * {@code product(g,gg,type,directed)}: the product of {@code g} and {@code gg} ({@link
   * #CARTESIAN}, {@link #DIRECT} or {@link #STRONG}). Vertices are ordered pairs {@code (v,v')} of
   * a {@code g}-vertex and a {@code gg}-vertex, named {@code "v-name,v'-name"}; vertex {@code i *
   * gg.n + j} is {@code (g.vertices[i], gg.vertices[j])}. The cartesian product has an arc from
   * {@code (u,u')} to {@code (v,u')} whenever {@code g} has one from {@code u} to {@code v}, and
   * from {@code (u,u')} to {@code (u,v')} whenever {@code gg} has one from {@code u'} to {@code
   * v'}; its arc lengths are copied from the original arc. The direct product has an arc from
   * {@code (u,u')} to {@code (v,v')} in the same circumstances, with length the minimum of the two
   * original arcs' lengths. The strong product has both kinds of arcs. If {@code directed} is zero,
   * both inputs are assumed undirected and the result is too. Returns {@code null} and sets {@link
   * Gb#panicCode} on failure ({@code g} or {@code gg} missing, too many vertices, or out of
   * memory).
   */
  public static Graph product(Graph g, Graph gg, long type, long directed) {
    if (g == null || gg == null) {
      Gb.panicCode = Gb.MISSING_OPERAND;
      Gb.troubleCode = 0;
      return null;
    }
    // Section 97 (part): guard against overflow, then set up ordered-pair vertices.
    float testProduct = (float) g.n * (float) gg.n;
    if (testProduct > MAX_NNN) {
      Gb.panicCode = Gb.VERY_BAD_SPECS;
      Gb.troubleCode = 0;
      return null;
    }
    long n = g.n * gg.n;
    Graph newGraph = Gb.newGraph(n);
    if (newGraph == null) {
      Gb.panicCode = Gb.NO_ROOM;
      Gb.troubleCode = 0;
      return null;
    }
    Vertex[] newVerts = newGraph.vertices;
    Vertex[] gVerts = g.vertices;
    Vertex[] ggVerts = gg.vertices;
    int ggN = (int) gg.n;
    int gN = (int) g.n;
    int vi = 0;
    int vvi = 0;
    for (int ui = 0; ui < n; ui++) {
      newVerts[ui].name =
          Gb.saveString(
              prefix(gVerts[vi].name, BUF_SIZE / 2 - 1)
                  + ","
                  + prefix(ggVerts[vvi].name, (BUF_SIZE - 1) / 2));
      vvi++;
      if (vvi == ggN) {
        vvi = 0;
        vi++;
      }
    }
    Gb.makeDoubleCompoundId(
        newGraph,
        "product(",
        g,
        ",",
        gg,
        "," + ((type != 0 ? 2 : 0) - (type & 1)) + "," + flag(directed) + ")");

    if ((type & 1) == 0) {
      insertCartesianProductArcs(newGraph, g, gg, directed); // sections 97-98
    }
    if (type != 0) {
      insertDirectProductArcs(newGraph, g, gg, directed); // section 99
    }

    if (Gb.troubleCode != 0) {
      Gb.recycle(newGraph);
      Gb.panicCode = Gb.ALLOC_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    return newGraph;
  }

  /**
   * Sections 97-98: the cartesian-product arcs or edges &mdash; {@code gg}'s arcs replicated across
   * every one of {@code g}'s {@code n} copies (section 97), then {@code g}'s arcs replicated across
   * every one of {@code gg}'s {@code n} copies (section 98).
   */
  private static void insertCartesianProductArcs(Graph newGraph, Graph g, Graph gg, long directed) {
    Vertex[] newVerts = newGraph.vertices;
    Vertex[] gVerts = g.vertices;
    Vertex[] ggVerts = gg.vertices;
    int ggN = (int) gg.n;
    int gN = (int) g.n;
    for (int ui = 0; ui < ggN; ui++) {
      Vertex u = ggVerts[ui];
      for (Arc a = u.arcs; a != null; a = a.next) {
        Vertex v = a.tip;
        if (directed == 0) {
          if (u.index > v.index) {
            continue;
          }
          if (u == v && Gb.isFirstOfSelfLoop(a)) {
            a = a.mate;
          }
        }
        for (int k = 0; k < gN; k++) {
          Vertex uu = newVerts[u.index + k * ggN];
          Vertex vv = newVerts[v.index + k * ggN];
          if (directed != 0) {
            Gb.newArc(uu, vv, a.len);
          } else {
            Gb.newEdge(uu, vv, a.len);
          }
        }
      }
    }
    for (int i = 0; i < gN; i++) {
      Vertex u = gVerts[i];
      int uuBase = i * ggN;
      for (Arc a = u.arcs; a != null; a = a.next) {
        Vertex v = a.tip;
        if (directed == 0) {
          if (u.index > v.index) {
            continue;
          }
          if (u == v && Gb.isFirstOfSelfLoop(a)) {
            a = a.mate;
          }
        }
        int vvBase = v.index * ggN;
        for (int j = 0; j < ggN; j++) {
          Vertex uu = newVerts[uuBase + j];
          Vertex vv = newVerts[vvBase + j];
          if (directed != 0) {
            Gb.newArc(uu, vv, a.len);
          } else {
            Gb.newEdge(uu, vv, a.len);
          }
        }
      }
    }
  }

  /**
   * Section 99: the direct-product arcs or edges &mdash; for every arc {@code uu -> vv} of {@code
   * g} and every arc {@code u -> v} of {@code gg}, an arc from {@code (uu,u)} to {@code (vv,v)}
   * whose length is the minimum of the two arcs' lengths.
   */
  private static void insertDirectProductArcs(Graph newGraph, Graph g, Graph gg, long directed) {
    Vertex[] newVerts = newGraph.vertices;
    Vertex[] gVerts = g.vertices;
    Vertex[] ggVerts = gg.vertices;
    int ggN = (int) gg.n;
    int gN = (int) g.n;
    for (int i = 0; i < gN; i++) {
      Vertex uu = gVerts[i];
      for (Arc a = uu.arcs; a != null; a = a.next) {
        Vertex vv = a.tip;
        if (directed == 0) {
          if (uu.index > vv.index) {
            continue;
          }
          if (uu == vv && Gb.isFirstOfSelfLoop(a)) {
            a = a.mate;
          }
        }
        int vvBase = vv.index * ggN;
        for (int ui = 0; ui < ggN; ui++) {
          Vertex u = ggVerts[ui];
          for (Arc aa = u.arcs; aa != null; aa = aa.next) {
            long length = Math.min(a.len, aa.len);
            Vertex v = aa.tip;
            Vertex from = newVerts[i * ggN + u.index];
            Vertex to = newVerts[vvBase + v.index];
            if (directed != 0) {
              Gb.newArc(from, to, length);
            } else {
              Gb.newEdge(from, to, length);
            }
          }
        }
      }
    }
  }

  /** {@code "%.*s"}: {@code s} truncated to at most {@code max} characters. */
  private static String prefix(String s, int max) {
    return s.length() <= max ? s : s.substring(0, Math.max(max, 0));
  }

  /**
   * Section 75, shared by {@link #complement}, {@link #gunion} and {@link #intersection}: a new
   * graph with {@code g.n} vertices, named after {@code g}'s. Returns {@code null} and sets {@link
   * Gb#panicCode} to {@link Gb#NO_ROOM} on allocation failure.
   */
  private static Graph copyVertexNames(Graph g) {
    Graph newGraph = Gb.newGraph(g.n);
    if (newGraph == null) {
      Gb.panicCode = Gb.NO_ROOM;
      Gb.troubleCode = 0;
      return null;
    }
    int n = (int) g.n;
    for (int i = 0; i < n; i++) {
      newGraph.vertices[i].name = Gb.saveString(g.vertices[i].name);
    }
    return newGraph;
  }

  /**
   * Section 80, used by {@link #gunion} for both {@code g}'s and {@code gg}'s arcs: inserts a union
   * arc or edge from {@code vv} to {@code u} (the tip of {@code a}, already mapped into the new
   * graph), respecting {@code multi} and {@code directed}, and returns {@code a} unchanged or (in
   * the undirected self-loop case) advanced past the second half of the loop.
   */
  private static Arc unionArc(Vertex vv, Vertex u, Arc a, long multi, long directed) {
    if (directed != 0) {
      if (multi != 0 || u.u.V() != vv) {
        Gb.newArc(vv, u, a.len);
      } else {
        Arc b = u.z.A();
        if (a.len < b.len) {
          b.len = a.len;
        }
      }
      u.u.V(vv);
      u.z.A(vv.arcs);
    } else if (u.index >= vv.index) {
      if (multi != 0 || u.u.V() != vv) {
        Gb.newEdge(vv, u, a.len);
      } else {
        Arc b = u.z.A();
        if (a.len < b.len) {
          b.len = a.len;
          b.mate.len = a.len;
        }
      }
      u.u.V(vv);
      u.z.A(vv.arcs);
      if (u == vv && Gb.isFirstOfSelfLoop(a)) {
        a = a.mate;
      }
    }
    return a;
  }

  /** {@code flag(x)}: {@code "1"} if {@code x} is nonzero, else {@code "0"}, for id strings. */
  private static String flag(long x) {
    return x != 0 ? "1" : "0";
  }

  /**
   * Section 12: {@code d > MAX_D} panics ({@link Gb#BAD_SPECS}); otherwise replicates {@code
   * nn[1..kStart-1]} periodically into {@code nn[kStart..d]}. Returns {@code true} if it panicked.
   */
  private static boolean replicateComponentSizes(long kStart, long d) {
    if (d > MAX_D) {
      Gb.panicCode = Gb.BAD_SPECS;
      Gb.troubleCode = 0;
      return true;
    }
    long j = 1;
    long k = kStart;
    while (k <= d) {
      nn[(int) k] = nn[(int) j];
      j++;
      k++;
    }
    return false;
  }

  /**
   * Section 27: argument massaging shared by {@link #simplex} and {@link #subsets} (and, in a later
   * task, {@code perms}). Mutates {@code nArgs[0..4]} (n0..n4) in place and {@code nn[0..d]}.
   * Returns {@code d}, or {@code -1} if it panicked ({@link Gb#BAD_SPECS} already set).
   */
  private static long normalizeSimplexArgs(long n, long[] nArgs) {
    long n0 = nArgs[0];
    long n1 = nArgs[1];
    long n2 = nArgs[2];
    long n3 = nArgs[3];
    long n4 = nArgs[4];
    if (n0 == 0) {
      n0 = -2;
    }
    long d;
    long k = 0;
    boolean shortcut = false;
    if (n0 < 0) {
      k = 2;
      nn[0] = n;
      d = -n0;
      n1 = n2 = n3 = n4 = 0;
    } else {
      if (n0 > n) {
        n0 = n;
      }
      nn[0] = n0;
      if (n1 <= 0) {
        k = 2;
        d = -n1;
        n2 = n3 = n4 = 0;
      } else {
        if (n1 > n) {
          n1 = n;
        }
        nn[1] = n1;
        if (n2 <= 0) {
          k = 3;
          d = -n2;
          n3 = n4 = 0;
        } else {
          if (n2 > n) {
            n2 = n;
          }
          nn[2] = n2;
          if (n3 <= 0) {
            k = 4;
            d = -n3;
            n4 = 0;
          } else {
            if (n3 > n) {
              n3 = n;
            }
            nn[3] = n3;
            if (n4 <= 0) {
              k = 5;
              d = -n4;
            } else {
              if (n4 > n) {
                n4 = n;
              }
              nn[4] = n4;
              d = 4;
              shortcut = true;
            }
          }
        }
      }
    }
    nArgs[0] = n0;
    nArgs[1] = n1;
    nArgs[2] = n2;
    nArgs[3] = n3;
    nArgs[4] = n4;
    if (shortcut) {
      return d;
    }
    if (d == 0) {
      return k - 2;
    }
    nn[(int) (k - 1)] = nn[0];
    if (replicateComponentSizes(k, d)) {
      return -1;
    }
    return d;
  }

  /**
   * Sections 29/30: the coefficient of {@code z^n} in {@code (1+z+...+z^nn[0])...(1+z+...+z^nn[d])
   * }, i.e. the number of feasible {@code (x0,...,xd)}. Returns {@code -1} if it panicked ({@link
   * Gb#VERY_BAD_SPECS} already set).
   */
  private static long countFeasiblePoints(long n, long d) {
    long[] coef = new long[(int) n + 1];
    for (int k = 0; k <= nn[0]; k++) {
      coef[k] = 1;
    }
    for (long j = 1; j <= d; j++) {
      // Section 30: multiply the power-series coefficients by 1+z+...+z^nn[j].
      long k = n;
      long i = n - nn[(int) j] - 1;
      for (; i >= 0; k--, i--) {
        coef[(int) k] -= coef[(int) i];
      }
      long s = 1;
      for (k = 1; k <= n; k++) {
        s += coef[(int) k];
        if (s > 1000000000) {
          Gb.panicCode = Gb.VERY_BAD_SPECS;
          Gb.troubleCode = 0;
          return -1;
        }
        coef[(int) k] = s;
      }
    }
    return coef[(int) n];
  }

  /**
   * Section 32: completes the partial solution {@code xx[kStart..d]}, given {@code xx[0..kStart-1]
   * } and {@code sig[kStart]} already set. Returns {@code true} if it panicked ({@link
   * Gb#IMPOSSIBLE}{@code + 1} already set) &mdash; "can't happen" per the C comment.
   */
  private static boolean completePartialSolution(long kStart, long d) {
    long k = kStart;
    long s = sig[(int) k] - xx[(int) k];
    k++;
    for (; k <= d; k++) {
      sig[(int) k] = s;
      if (s <= yy[(int) (k + 1)]) {
        xx[(int) k] = 0;
      } else {
        xx[(int) k] = s - yy[(int) (k + 1)];
      }
      s -= xx[(int) k];
    }
    if (s != 0) {
      Gb.panicCode = Gb.IMPOSSIBLE + 1;
      Gb.troubleCode = 0;
      return true;
    }
    return false;
  }

  /**
   * Section 33: advances {@code xx} to the next partial solution, i.e. the largest {@code k} such
   * that {@code xx[k]} can be increased. Returns the incremented index, or {@code -1} if there are
   * no more solutions (the C's {@code goto last}). When {@code d = 0} there is no coordinate left
   * to advance (the C's {@code for(k=d-1;;k--)} would read {@code xx[-1]}, an out-of-bounds array
   * access the C leaves undefined); the single feasible point is the only solution, so this stops
   * immediately.
   */
  private static long advanceToNextSolution(long d) {
    if (d == 0) {
      return -1;
    }
    long k = d - 1;
    while (true) {
      if (xx[(int) k] < sig[(int) k] && xx[(int) k] < nn[(int) k]) {
        break;
      }
      if (k == 0) {
        return -1;
      }
      k--;
    }
    xx[(int) k]++;
    return k;
  }

  /** Section 34's name-building loop: {@code ".x0.x1...xd"} with the leading dot stripped. */
  private static String buildDotName(long d) {
    StringBuilder p = new StringBuilder();
    for (long k = 0; k <= d; k++) {
      p.append('.').append(xx[(int) k]);
    }
    return p.substring(1);
  }

  /**
   * Section 34: assigns vertex {@code v} the symbolic name for the current {@code xx[0..d]}, and
   * stashes the first three coordinates in its {@code x}, {@code y} and {@code z} slots.
   */
  private static void assignSimplexName(Vertex v, long d) {
    v.name = Gb.saveString(buildDotName(d));
    v.x.I = xx[0];
    v.y.I = xx[1];
    v.z.I = xx[2];
  }

  /**
   * Section 40: the number of elements common to the multiset named {@code name} (an earlier
   * vertex, {@code ".x0.x1...xd"} with the leading dot already stripped) and the current {@code
   * xx[0..d]}.
   */
  private static long subsetIntersectionSize(String name, long d) {
    long ss = 0;
    int idx = 0;
    for (long j = 0; j <= d; j++) {
      int dot = name.indexOf('.', idx);
      long field = Long.parseLong(dot < 0 ? name.substring(idx) : name.substring(idx, dot));
      idx = dot < 0 ? name.length() : dot + 1;
      ss += Math.min(xx[(int) j], field);
    }
    return ss;
  }

  /**
   * Section 62: generates the subpartition obtained by splitting {@code xx[j]} into {@code a+b}
   * (copying the other parts of {@code xx[1..d]} into {@code nn[1..d+1]} in sorted order), looks it
   * up, and connects {@code v} to it (source first, per {@link #parts}' documented arc direction).
   * Returns {@code true} if it panicked ({@link Gb#IMPOSSIBLE}{@code + 2} already set) &mdash;
   * "can't happen" per the C comment.
   */
  private static boolean assignPartitionSplit(
      Vertex v, long j, long a, long b, long d, long directed) {
    long k = j + 1;
    while (xx[(int) k] > a) {
      nn[(int) (k - 1)] = xx[(int) k];
      k++;
    }
    nn[(int) (k - 1)] = a;
    while (xx[(int) k] > b) {
      nn[(int) k] = xx[(int) k];
      k++;
    }
    nn[(int) k] = b;
    for (; k <= d; k++) {
      nn[(int) (k + 1)] = xx[(int) k];
    }
    StringBuilder name = new StringBuilder();
    for (k = 1; k <= d + 1; k++) {
      name.append('+').append(nn[(int) k]);
    }
    Vertex u = Gb.hashOut(name.substring(1));
    if (u == null) {
      Gb.panicCode = Gb.IMPOSSIBLE + 2;
      Gb.troubleCode = 0;
      return true;
    }
    if (directed != 0) {
      Gb.newArc(v, u, 1L);
    } else {
      Gb.newEdge(v, u, 1L);
    }
    return false;
  }

  /**
   * {@code induced(g,description,self,multi,directed)}: a graph obtained from {@code g} by
   * eliminating, retaining, splitting or identifying vertices, per each vertex's {@code ind} field
   * ({@code v.z.I}, set by the caller before this call): 0 eliminates the vertex; 1 retains it;
   * {@code k>1} splits it into {@code k} nonadjacent clones with the same neighbors {@code v} had;
   * {@code k<0} identifies it with every other vertex sharing that value of {@code k}. When a
   * vertex's {@code ind} is {@link #IND_GRAPH} or more, its {@code subst} field ({@code v.y.G()})
   * must point at a graph whose vertices are substituted in its place (used by {@link #wheel}).
   * Duplicate arcs are discarded unless {@code multi} is nonzero; self-loops are discarded unless
   * {@code self} is nonzero. {@code description}, if non-null, is folded into the result's {@code
   * id}. If {@code directed} is zero, {@code g} is assumed undirected and the result is too. On
   * return, {@code g}'s {@code ind} fields are restored to their original values, whatever this
   * call did to them along the way. Returns {@code null} and sets {@link Gb#panicCode} on failure
   * ({@code g} missing, a vertex marked {@link #IND_GRAPH} or more with no {@code subst} graph, too
   * many resulting vertices, or out of memory).
   */
  public static Graph induced(Graph g, String description, long self, long multi, long directed) {
    if (g == null) {
      Gb.panicCode = Gb.MISSING_OPERAND;
      Gb.troubleCode = 0;
      return null;
    }
    Vertex[] gVerts = g.vertices;
    int gN = (int) g.n;

    // Section 107: determine n (total new vertices) and nn (negative-vertex count).
    long n = 0;
    long nn = 0;
    for (int i = 0; i < gN; i++) {
      long ind = gVerts[i].z.I;
      if (ind > 0) {
        if (n > IND_GRAPH) {
          Gb.panicCode = Gb.VERY_BAD_SPECS;
          Gb.troubleCode = 0;
          return null;
        }
        if (ind >= IND_GRAPH) {
          if (gVerts[i].y.G() == null) {
            Gb.panicCode = Gb.MISSING_OPERAND + 1;
            Gb.troubleCode = 0;
            return null;
          }
          n += gVerts[i].y.G().n;
        } else {
          n += ind;
        }
      } else if (ind < -nn) {
        nn = -ind;
      }
    }
    if (n > IND_GRAPH || nn > IND_GRAPH) {
      Gb.panicCode = Gb.VERY_BAD_SPECS + 1;
      Gb.troubleCode = 0;
      return null;
    }
    n += nn;

    Graph newGraph = Gb.newGraph(n);
    if (newGraph == null) {
      Gb.panicCode = Gb.NO_ROOM;
      Gb.troubleCode = 0;
      return null;
    }
    Vertex[] newVerts = newGraph.vertices;

    // Section 108: assign names to the new vertices, and create a map from g to new_graph.
    int ui = 0;
    for (long k = 1; k <= nn; k++, ui++) {
      Vertex u = newVerts[ui];
      u.v.I = -k;
      u.name = Gb.saveString(Long.toString(-k));
    }
    for (int i = 0; i < gN; i++) {
      Vertex v = gVerts[i];
      long k = v.z.I;
      if (k < 0) {
        v.z.V(newVerts[(int) (-k - 1)]);
      } else if (k > 0) {
        Vertex u = newVerts[ui];
        u.v.I = k;
        v.z.V(u);
        if (k <= 2) {
          u.name = Gb.saveString(v.name);
          ui++;
          if (k == 2) {
            newVerts[ui].name = Gb.saveString(v.name + "'");
            ui++;
          }
        } else if (k >= IND_GRAPH) {
          ui = substituteGraph(v, newVerts, ui, self, multi, directed);
        } else {
          for (long j = 0; j < k; j++, ui++) {
            newVerts[ui].name = Gb.saveString(prefix(v.name, BUF_SIZE - 12) + ":" + j);
          }
        }
      }
    }
    Gb.makeCompoundId(
        newGraph,
        "induced(",
        g,
        ","
            + (description == null ? "" : description)
            + ","
            + flag(self)
            + ","
            + flag(multi)
            + ","
            + flag(directed)
            + ")");

    // Sections 110-113: insert arcs or edges for induced vertices.
    for (int i = 0; i < gN; i++) {
      Vertex v = gVerts[i];
      Vertex mapped = v.z.V();
      if (mapped == null) {
        continue;
      }
      long k = mapped.v.I;
      if (k < 0) {
        k = 1;
      } else if (k >= IND_GRAPH) {
        k = v.y.G().n;
      }
      int uIndex = mapped.index;
      for (; k > 0; k--, uIndex++) {
        Vertex u = newVerts[uIndex];
        if (multi == 0) {
          // Section 111: take note of existing edges that touch u.
          for (Arc a = u.arcs; a != null; a = a.next) {
            Vertex tip = a.tip;
            tip.u.V(u);
            if (directed != 0 || tip.index > u.index || Gb.isFirstOfSelfLoop(a)) {
              tip.z.A(a);
            } else {
              tip.z.A(a.mate);
            }
          }
        }
        for (Arc a = v.arcs; a != null; a = a.next) {
          Vertex vv = a.tip;
          Vertex vvMapped = vv.z.V();
          if (vvMapped == null) {
            continue;
          }
          long j = vvMapped.v.I;
          if (j < 0) {
            j = 1;
          } else if (j >= IND_GRAPH) {
            j = vv.y.G().n;
          }
          Vertex uu = vvMapped;
          if (directed == 0) {
            if (vv.index < v.index) {
              continue;
            }
            if (vv == v) {
              if (Gb.isFirstOfSelfLoop(a)) {
                a = a.mate;
              }
              j = k;
              uu = u;
            }
          }
          // Section 112: insert arcs or edges from u to uu through uu+j-1.
          int uuIndex = uu.index;
          for (; j > 0; j--, uuIndex++) {
            Vertex uuVertex = newVerts[uuIndex];
            if (u == uuVertex && self == 0) {
              continue;
            }
            if (uuVertex.u.V() == u && multi == 0) {
              // Section 113: update the minimum arc length from u to uu, then continue.
              Arc b = uuVertex.z.A();
              if (a.len < b.len) {
                b.len = a.len;
                if (directed == 0) {
                  b.mate.len = a.len;
                }
              }
              continue;
            }
            if (directed != 0) {
              Gb.newArc(u, uuVertex, a.len);
            } else {
              Gb.newEdge(u, uuVertex, a.len);
            }
            uuVertex.u.V(u);
            uuVertex.z.A(directed != 0 || u.index <= uuVertex.index ? u.arcs : uuVertex.arcs);
          }
        }
      }
    }

    // Section 109: restore g to its original state, and clear the new graph's scratch fields.
    for (int i = 0; i < gN; i++) {
      Vertex v = gVerts[i];
      Vertex mapped = v.z.V();
      if (mapped != null) {
        v.z.I = mapped.v.I;
        v.z.ref = null;
      }
    }
    int totalN = (int) newGraph.n;
    for (int i = 0; i < totalN; i++) {
      Vertex nv = newVerts[i];
      nv.u.I = 0;
      nv.u.ref = null;
      nv.v.I = 0;
      nv.v.ref = null;
      nv.z.I = 0;
      nv.z.ref = null;
    }

    if (Gb.troubleCode != 0) {
      Gb.recycle(newGraph);
      Gb.panicCode = Gb.ALLOC_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    return newGraph;
  }

  /**
   * Section 114: names and wires up the clones of {@code v}'s substituted graph ({@code v.y.G()}),
   * starting at {@code newVerts[uBase]}, one clone per vertex of the substituted graph, in order.
   * Each clone is named {@code v.name + ":" + <that vertex's name>} (both truncated to fit), and
   * the substituted graph's own arcs or edges are copied among the clones, subject to {@code self}
   * and {@code multi} exactly as {@link #induced}'s main arc-copying loop is. Returns the index
   * just past the last clone written.
   */
  private static int substituteGraph(
      Vertex v, Vertex[] newVerts, int uBase, long self, long multi, long directed) {
    Graph gg = v.y.G();
    Vertex[] ggVerts = gg.vertices;
    int ggN = (int) gg.n;
    for (int j = 0; j < ggN; j++) {
      Vertex vv = ggVerts[j];
      Vertex u = newVerts[uBase + j];
      u.name =
          Gb.saveString(
              prefix(v.name, BUF_SIZE / 2 - 1) + ":" + prefix(vv.name, (BUF_SIZE - 1) / 2));
      for (Arc a = vv.arcs; a != null; a = a.next) {
        Vertex vvv = a.tip;
        Vertex uu = newVerts[uBase + vvv.index];
        if (vvv == vv && self == 0) {
          continue;
        }
        if (uu.u.V() == u && multi == 0) {
          Arc b = uu.z.A();
          if (a.len < b.len) {
            b.len = a.len;
            if (directed == 0) {
              b.mate.len = a.len;
            }
          }
          continue;
        }
        if (directed == 0) {
          if (vvv.index < vv.index) {
            continue;
          }
          if (vvv == vv && Gb.isFirstOfSelfLoop(a)) {
            a = a.mate;
          }
          Gb.newEdge(u, uu, a.len);
        } else {
          Gb.newArc(u, uu, a.len);
        }
        uu.u.V(u);
        uu.z.A(directed != 0 || u.index <= uu.index ? u.arcs : uu.arcs);
      }
    }
    return uBase + ggN;
  }

  /**
   * {@code bi_complete(n1,n2,directed)}: the complete bipartite graph with parts of sizes {@code
   * n1} and {@code n2}, built by splitting a trivial two-vertex graph's vertices via {@link
   * #induced}. {@link Gb#markBipartite} records the size of the first part. Returns {@code null}
   * and sets {@link Gb#panicCode} on failure (too many vertices, or out of memory).
   */
  public static Graph biComplete(long n1, long n2, long directed) {
    Graph newGraph = board(2L, 0L, 0L, 0L, 1L, 0L, directed);
    if (newGraph != null) {
      newGraph.vertices[0].z.I = n1;
      newGraph.vertices[1].z.I = n2;
      newGraph = induced(newGraph, null, 0L, 0L, directed);
      if (newGraph != null) {
        newGraph.id =
            "bi_complete("
                + Long.toUnsignedString(n1)
                + ","
                + Long.toUnsignedString(n2)
                + ","
                + flag(directed)
                + ")";
        Gb.markBipartite(newGraph, n1);
      }
    }
    return newGraph;
  }

  /**
   * {@code wheel(n,n1,directed)}: {@code n1} hub vertices, all connected to every vertex of an
   * {@code n}-vertex rim (a cycle if undirected, a circuit if directed, from hub to rim and
   * around), built via {@link #induced}'s {@link #IND_GRAPH} substitution feature. Returns {@code
   * null} and sets {@link Gb#panicCode} on failure (too many vertices, or out of memory).
   */
  public static Graph wheel(long n, long n1, long directed) {
    Graph newGraph = board(2L, 0L, 0L, 0L, 1L, 0L, directed);
    if (newGraph != null) {
      newGraph.vertices[0].z.I = n1;
      newGraph.vertices[1].z.I = IND_GRAPH;
      newGraph.vertices[1].y.G(board(n, 0L, 0L, 0L, 1L, 1L, directed));
      newGraph = induced(newGraph, null, 0L, 0L, directed);
      if (newGraph != null) {
        newGraph.id =
            "wheel("
                + Long.toUnsignedString(n)
                + ","
                + Long.toUnsignedString(n1)
                + ","
                + flag(directed)
                + ")";
      }
    }
    return newGraph;
  }

  /** {@code complete(n)}: the complete graph on {@code n} vertices. */
  public static Graph complete(long n) {
    return board(n, 0L, 0L, 0L, -1L, 0L, 0L);
  }

  /** {@code transitive(n)}: the transitive tournament on {@code n} vertices. */
  public static Graph transitive(long n) {
    return board(n, 0L, 0L, 0L, -1L, 0L, 1L);
  }

  /** {@code empty(n)}: {@code n} vertices with no arcs. */
  public static Graph empty(long n) {
    return board(n, 0L, 0L, 0L, 2L, 0L, 0L);
  }

  /** {@code circuit(n)}: the undirected cycle on {@code n} vertices. */
  public static Graph circuit(long n) {
    return board(n, 0L, 0L, 0L, 1L, 1L, 0L);
  }

  /** {@code cycle(n)}: the directed cycle on {@code n} vertices. */
  public static Graph cycle(long n) {
    return board(n, 0L, 0L, 0L, 1L, 1L, 1L);
  }

  /**
   * {@code disjoint_subsets(n,k)}: the {@code k}-subsets of an {@code n}-element set, adjacent when
   * disjoint.
   */
  public static Graph disjointSubsets(long n, long k) {
    return subsets(k, 1L, 1L - n, 0L, 0L, 0L, 1L, 0L);
  }

  /** {@code petersen()}: the Petersen graph, {@code disjoint_subsets(5,2)}. */
  public static Graph petersen() {
    return disjointSubsets(5L, 2L);
  }

  /** {@code all_perms(n,directed)}: all {@code n!} permutations of an {@code n}-element set. */
  public static Graph allPerms(long n, long directed) {
    return perms(1L - n, 0L, 0L, 0L, 0L, 0L, directed);
  }

  /** {@code all_parts(n,directed)}: all partitions of {@code n}. */
  public static Graph allParts(long n, long directed) {
    return parts(n, 0L, 0L, directed);
  }

  /** {@code all_trees(n,directed)}: all binary trees with {@code n} internal nodes. */
  public static Graph allTrees(long n, long directed) {
    return binary(n, 0L, directed);
  }
}
