package com.robsartin.jsgb.rand;

import com.robsartin.jsgb.flip.Flip;
import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;

/**
 * Port of {@code gb_rand}: random graphs and random bigraphs with prescribed degree distributions,
 * built with Walker's alias method, plus random arc lengths. Every graph {@link #randomGraph},
 * {@link #randomBigraph} or {@link #randomLengths} produces is reproducible bit-for-bit from its
 * {@code seed}, because it is generated purely from {@link Flip}'s pseudo-random sequence.
 */
public final class Rand {

  private Rand() {}

  /** {@code dist_code(x)}: the id fragment for a distribution array, or {@code "0"} if uniform. */
  private static String distCode(long[] dist) {
    return dist != null ? "dist" : "0";
  }

  /** {@code rand_len}: a length uniform in {@code [minLen, maxLen]}, or {@code minLen} if equal. */
  private static long randLen(long minLen, long maxLen) {
    return minLen == maxLen ? minLen : minLen + Flip.unifRand(maxLen - minLen + 1);
  }

  /** The C {@code magic_entry} struct: a Walker's-alias-table cell. Package-private for testing. */
  static final class MagicEntry {
    long prob;
    long inx;
  }

  /** The C {@code node} struct: a scratch list element used only while {@link #walker} runs. */
  private static final class Node {
    long key;
    Node link;
    long j;
  }

  /**
   * {@code walker(n, nn, dist, g)}: builds a Walker's-alias table of size {@code nn} (a power of 2,
   * {@code nn >= n}) from the {@code n}-entry probability vector {@code dist}, which must sum to
   * {@code 0x40000000}. Indices {@code n .. nn-1} are treated as having probability 0.
   * Package-private for testing.
   */
  static MagicEntry[] walker(long n, long nn, long[] dist) {
    int nni = (int) nn;
    MagicEntry[] table = new MagicEntry[nni];
    for (int i = 0; i < nni; i++) {
      table[i] = new MagicEntry();
    }
    Node[] nodes = new Node[nni];
    for (int i = 0; i < nni; i++) {
      nodes[i] = new Node();
    }
    long t = 0x40000000L / nn; // exact division: nn is a power of 2 dividing 2^30
    Node hi = null;
    Node lo = null;
    int p = 0;
    long nnVar = nn;
    while (nnVar > n) {
      Node node = nodes[p];
      node.key = 0;
      node.link = lo;
      nnVar--;
      node.j = nnVar;
      lo = node;
      p++;
    }
    for (int i = (int) n - 1; i >= 0; i--) {
      Node node = nodes[p];
      node.key = dist[i];
      node.j = i;
      if (dist[i] > t) {
        node.link = hi;
        hi = node;
      } else {
        node.link = lo;
        lo = node;
      }
      p++;
    }
    while (hi != null) {
      Node hiNode = hi;
      hi = hiNode.link;
      Node loNode = lo;
      lo = loNode.link;
      MagicEntry r = table[(int) loNode.j];
      long x = t * loNode.j + loNode.key - 1;
      r.prob = x + x + 1;
      r.inx = hiNode.j;
      hiNode.key -= t - loNode.key;
      if (hiNode.key > t) {
        hiNode.link = hi;
        hi = hiNode;
      } else {
        hiNode.link = lo;
        lo = hiNode;
      }
    }
    while (lo != null) {
      Node loNode = lo;
      lo = loNode.link;
      MagicEntry r = table[(int) loNode.j];
      long x = t * loNode.j + t - 1;
      r.prob = x + x + 1;
    }
    return table;
  }

  /**
   * {@code random_graph(n,m,multi,self,directed,dist_from,dist_to,min_len,max_len,seed)}: a
   * pseudo-random graph with {@code n} vertices and {@code m} arcs or edges.
   *
   * <p>{@code multi != 0} permits duplicate arcs; {@code self != 0} permits self-loops; {@code
   * directed != 0} makes the graph directed (otherwise each arc becomes an undirected edge). {@code
   * distFrom} and {@code distTo} specify probability distributions on the arc source and
   * destination; {@code null} means uniform. Otherwise each is an array of {@code n} nonnegative
   * integers summing to {@code 0x40000000}, giving the relative probability (times {@code
   * 0x40000000}) of each vertex. {@code minLen} and {@code maxLen} bound the arc lengths, uniformly
   * distributed between them.
   *
   * <p>{@code multi == -1} acts like {@code multi == 1} except duplicate arcs are not physically
   * created; instead the existing arc's length is lowered to the minimum of all arcs sharing its
   * source and destination.
   *
   * <p>Vertices are named {@code "0"} through {@code "n-1"}. Returns {@code null} and sets {@link
   * Gb#panicCode} if the parameters are invalid.
   */
  public static Graph randomGraph(
      long n,
      long m,
      long multi,
      long self,
      long directed,
      long[] distFrom,
      long[] distTo,
      long minLen,
      long maxLen,
      long seed) {
    if (n == 0) {
      Gb.panicCode = Gb.BAD_SPECS;
      Gb.troubleCode = 0;
      return null;
    }
    if (minLen > maxLen) {
      Gb.panicCode = Gb.VERY_BAD_SPECS;
      Gb.troubleCode = 0;
      return null;
    }
    if (Long.compareUnsigned(maxLen - minLen, 0x80000000L) >= 0) {
      Gb.panicCode = Gb.BAD_SPECS + 1;
      Gb.troubleCode = 0;
      return null;
    }
    if (distFrom != null) {
      long acc = 0;
      for (int i = 0; i < n; i++) {
        long prob = distFrom[i];
        if (prob < 0) {
          Gb.panicCode = Gb.INVALID_OPERAND;
          Gb.troubleCode = 0;
          return null;
        }
        if (prob > 0x40000000L - acc) {
          Gb.panicCode = Gb.INVALID_OPERAND + 1;
          Gb.troubleCode = 0;
          return null;
        }
        acc += prob;
      }
      if (acc != 0x40000000L) {
        Gb.panicCode = Gb.INVALID_OPERAND + 2;
        Gb.troubleCode = 0;
        return null;
      }
    }
    if (distTo != null) {
      long acc = 0;
      for (int i = 0; i < n; i++) {
        long prob = distTo[i];
        if (prob < 0) {
          Gb.panicCode = Gb.INVALID_OPERAND + 5;
          Gb.troubleCode = 0;
          return null;
        }
        if (prob > 0x40000000L - acc) {
          Gb.panicCode = Gb.INVALID_OPERAND + 6;
          Gb.troubleCode = 0;
          return null;
        }
        acc += prob;
      }
      if (acc != 0x40000000L) {
        Gb.panicCode = Gb.INVALID_OPERAND + 7;
        Gb.troubleCode = 0;
        return null;
      }
    }

    Flip.initRand(seed);

    Graph newGraph = Gb.newGraph(n);
    if (newGraph == null) {
      Gb.panicCode = Gb.NO_ROOM;
      Gb.troubleCode = 0;
      return null;
    }
    Vertex[] verts = newGraph.vertices;
    for (int k = 0; k < n; k++) {
      verts[k].name = Long.toString(k);
    }
    newGraph.id =
        "random_graph("
            + Long.toUnsignedString(n)
            + ","
            + Long.toUnsignedString(m)
            + ","
            + (multi > 0 ? 1 : multi < 0 ? -1 : 0)
            + ","
            + (self != 0 ? 1 : 0)
            + ","
            + (directed != 0 ? 1 : 0)
            + ","
            + distCode(distFrom)
            + ","
            + distCode(distTo)
            + ","
            + minLen
            + ","
            + maxLen
            + ","
            + seed
            + ")";

    long nn = 1;
    int kk = 31;
    MagicEntry[] fromTable = null;
    MagicEntry[] toTable = null;
    if (distFrom != null) {
      while (nn < n) {
        nn += nn;
        kk--;
      }
      fromTable = walker(n, nn, distFrom);
    }
    if (distTo != null) {
      while (nn < n) {
        nn += nn;
        kk--;
      }
      toTable = walker(n, nn, distTo);
    }
    if (Gb.troubleCode != 0) {
      Gb.recycle(newGraph);
      Gb.panicCode = Gb.ALLOC_FAULT;
      Gb.troubleCode = 0;
      return null;
    }

    mmLoop:
    for (long mm = m; mm != 0; mm--) {
      Vertex u;
      Vertex v;
      while (true) {
        if (distFrom != null) {
          long uu = Flip.nextRand();
          int k = (int) (uu >> kk);
          MagicEntry magic = fromTable[k];
          u = uu <= magic.prob ? verts[k] : verts[(int) magic.inx];
        } else {
          u = verts[(int) Flip.unifRand(n)];
        }
        if (distTo != null) {
          long uu = Flip.nextRand();
          int k = (int) (uu >> kk);
          MagicEntry magic = toTable[k];
          v = uu <= magic.prob ? verts[k] : verts[(int) magic.inx];
        } else {
          v = verts[(int) Flip.unifRand(n)];
        }
        if (u == v && self == 0) {
          continue;
        }
        if (multi <= 0) {
          if (Gb.troubleCode != 0) {
            break mmLoop;
          }
          Arc dup = null;
          for (Arc a = u.arcs; a != null; a = a.next) {
            if (a.tip == v) {
              dup = a;
              break;
            }
          }
          if (dup != null) {
            if (multi == 0) {
              continue;
            }
            long len = randLen(minLen, maxLen);
            if (len < dup.len) {
              dup.len = len;
              if (directed == 0) {
                dup.mate.len = len;
              }
            }
            break;
          }
        }
        if (directed != 0) {
          Gb.newArc(u, v, randLen(minLen, maxLen));
        } else {
          Gb.newEdge(u, v, randLen(minLen, maxLen));
        }
        break;
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
   * {@code random_bigraph(n1,n2,m,multi,dist1,dist2,min_len,max_len,seed)}: a pseudo-random
   * bipartite graph with {@code n1} vertices in one part and {@code n2} in the other, having {@code
   * m} edges. {@code multi}, {@code dist1}, {@code dist2}, {@code minLen}, {@code maxLen} and
   * {@code seed} mean the same as the analogous {@link #randomGraph} parameters; {@code dist1} and
   * {@code dist2} give the distribution within each part. Built by reducing to a call of {@link
   * #randomGraph}, padding {@code dist1} with {@code n2} trailing zeroes and {@code dist2} with
   * {@code n1} leading zeroes (fabricating uniform vectors when either is {@code null}).
   */
  public static Graph randomBigraph(
      long n1,
      long n2,
      long m,
      long multi,
      long[] dist1,
      long[] dist2,
      long minLen,
      long maxLen,
      long seed) {
    long n = n1 + n2;
    if (n1 == 0 || n2 == 0) {
      Gb.panicCode = Gb.BAD_SPECS;
      Gb.troubleCode = 0;
      return null;
    }
    if (minLen > maxLen) {
      Gb.panicCode = Gb.VERY_BAD_SPECS;
      Gb.troubleCode = 0;
      return null;
    }
    if (Long.compareUnsigned(maxLen - minLen, 0x80000000L) >= 0) {
      Gb.panicCode = Gb.BAD_SPECS + 1;
      Gb.troubleCode = 0;
      return null;
    }

    long[] distFrom = new long[(int) n];
    long[] distTo = new long[(int) n];
    if (dist1 != null) {
      System.arraycopy(dist1, 0, distFrom, 0, (int) n1);
    } else {
      for (long k = 0; k < n1; k++) {
        distFrom[(int) k] = (0x40000000L + k) / n1;
      }
    }
    if (dist2 != null) {
      System.arraycopy(dist2, 0, distTo, (int) n1, (int) n2);
    } else {
      for (long k = 0; k < n2; k++) {
        distTo[(int) (n1 + k)] = (0x40000000L + k) / n2;
      }
    }

    Graph newGraph = randomGraph(n, m, multi, 0L, 0L, distFrom, distTo, minLen, maxLen, seed);
    if (newGraph == null) {
      return null;
    }
    newGraph.id =
        "random_bigraph("
            + Long.toUnsignedString(n1)
            + ","
            + Long.toUnsignedString(n2)
            + ","
            + Long.toUnsignedString(m)
            + ","
            + (multi > 0 ? 1 : multi < 0 ? -1 : 0)
            + ","
            + distCode(dist1)
            + ","
            + distCode(dist2)
            + ","
            + minLen
            + ","
            + maxLen
            + ","
            + seed
            + ")";
    Gb.markBipartite(newGraph, n1);
    return newGraph;
  }

  /**
   * {@code random_lengths(g,directed,min_len,max_len,dist,seed)}: assigns new pseudo-random lengths
   * to every arc of the existing graph {@code g}. If {@code dist} is {@code null}, lengths are
   * uniform between {@code minLen} and {@code maxLen} inclusive; otherwise {@code dist} is a
   * probability distribution vector of length {@code maxLen - minLen + 1}, as in {@link
   * #randomGraph}. If {@code directed == 0}, an arc {@code u -> v} and its mate {@code v -> u} are
   * treated as one edge and receive the same length.
   *
   * <p>Returns 0 on success, or a nonzero code if something goes wrong, in which case {@code g} is
   * left unchanged.
   */
  public static long randomLengths(
      Graph g, long directed, long minLen, long maxLen, long[] dist, long seed) {
    if (g == null) {
      return Gb.MISSING_OPERAND;
    }
    Flip.initRand(seed);
    if (minLen > maxLen) {
      return Gb.VERY_BAD_SPECS;
    }
    if (Long.compareUnsigned(maxLen - minLen, 0x80000000L) >= 0) {
      return Gb.BAD_SPECS;
    }

    long nn = 1;
    int kk = 31;
    MagicEntry[] table = null;
    if (dist != null) {
      long acc = 0;
      long n = maxLen - minLen + 1;
      for (int i = 0; i < n; i++) {
        long prob = dist[i];
        if (prob < 0) {
          return -1;
        }
        if (prob > 0x40000000L - acc) {
          return 1;
        }
        acc += prob;
      }
      if (acc != 0x40000000L) {
        return 2;
      }
      while (nn < n) {
        nn += nn;
        kk--;
      }
      table = walker(n, nn, dist);
      if (Gb.troubleCode != 0) {
        Gb.troubleCode = 0;
        return Gb.ALLOC_FAULT;
      }
    }

    Gb.makeCompoundId(
        g,
        "random_lengths(",
        g,
        ","
            + (directed != 0 ? 1 : 0)
            + ","
            + minLen
            + ","
            + maxLen
            + ","
            + distCode(dist)
            + ","
            + seed
            + ")");

    for (int i = 0; i < g.n; i++) {
      Vertex u = g.vertices[i];
      for (Arc a = u.arcs; a != null; a = a.next) {
        Vertex v = a.tip;
        if (directed == 0 && u.index > v.index) {
          a.len = a.mate.len;
        } else {
          long len;
          if (dist == null) {
            len = randLen(minLen, maxLen);
          } else {
            long uu = Flip.nextRand();
            int k = (int) (uu >> kk);
            len = uu <= table[k].prob ? minLen + k : minLen + table[k].inx;
          }
          a.len = len;
          if (directed == 0 && u == v && Gb.isFirstOfSelfLoop(a)) {
            a = a.mate;
            a.len = len;
          }
        }
      }
    }
    return 0;
  }
}
