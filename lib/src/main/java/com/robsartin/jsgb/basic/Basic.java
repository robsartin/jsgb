package com.robsartin.jsgb.basic;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;

/**
 * Port of {@code gb_basic}: six subroutines that generate standard graphs of various types (boards,
 * simplexes, subsets, permutations, partitions, binary trees), together with six routines that
 * combine or transform existing graphs. This class grows across several tasks; this port currently
 * provides {@link #board}, {@link #simplex} and {@link #subsets}.
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
 * slots for readability in the functions this class will add later ({@code perms}, {@code parts},
 * {@code binary}, and the graph-transformation routines): {@code tmp} is {@code u.V}, {@code tlen}
 * is {@code z.A}, {@code mult} is {@code v.I}, {@code minlen} is {@code w.I}, {@code map} is {@code
 * z.V}, {@code ind} is {@code z.I}, and {@code subst} is {@code y.G}. {@link #IND_GRAPH} is the
 * sentinel value that {@code ind} macro's slot uses to mark an "induced graph" pseudo-vertex. None
 * of these are used by {@link #board}, {@link #simplex} or {@link #subsets}.
 */
public final class Basic {

  private Basic() {}

  /** {@code MAX_D}: the largest number of dimensions/coordinates a generator will accept. */
  public static final int MAX_D = 91;

  /** {@code BUF_SIZE}: the size of the C's vertex-name assembly buffer. */
  public static final int BUF_SIZE = 4096;

  /** {@code MAX_NNN}: {@link #board} refuses to build a board with more cells than this. */
  public static final float MAX_NNN = 1000000000.0f;

  /** {@code IND_GRAPH}: sentinel used by the {@code ind} slot macro (future {@code induced}). */
  public static final long IND_GRAPH = 1000000000;

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
}
