package com.robsartin.jsgb.econ;

import com.robsartin.jsgb.flip.Flip;
import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import com.robsartin.jsgb.io.GbIo;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Port of {@code gb_econ}: a flow graph built from the 1985 U.S. input-output sector table ({@code
 * econ.dat}), which arranges 79 named sectors of the economy into a nested category tree plus two
 * special sectors, {@code Adjustments} and {@code Users}.
 *
 * <p>{@link #econ} first reads that tree and the 81-by-80 flow matrix (row {@code k}, column {@code
 * j} is the flow from sector {@code k} to sector {@code j}), then collapses the tree so that only
 * {@code n - (2 - omit)} of the 79 named sectors remain as distinct vertices — every other named
 * sector is merged into whichever of its two category-mates the tree pairs it with, folding its row
 * and column into the survivor's. Which sectors survive is chosen either by a uniformly random
 * subtree of the category tree (when {@code seed != 0}) or, when {@code seed == 0}, greedily: the
 * {@code n - (2 - omit)} categories with the largest total flow are kept whole and everything else
 * is merged upward. {@code omit} (clamped to at most 2) then drops the last {@code omit} of the two
 * special sectors from the result, and {@code threshold} (clamped to at most 65536) suppresses an
 * arc for a flow smaller than {@code threshold65536} of the receiving sector's total.
 *
 * <p>Vertex slot {@code y.I} ({@code sector_total}) holds the sector's total flow; {@code z.A}
 * ({@code SIC_codes}) holds a linked list of virgin arcs whose {@code len} fields are the original
 * Standard Industrial Classification codes merged into this vertex (their {@code tip}s are always
 * {@code null} — they carry no destination, only a code). Arc slot {@code a.I} ({@code flow}) holds
 * the flow's magnitude.
 */
public final class Econ {

  private static final int MAX_N = 81;
  private static final int NORM_N = MAX_N - 2;
  private static final int ADJ_SEC = MAX_N - 1;

  private Econ() {}

  /**
   * The C {@code node} struct: one entry of the category tree, either a named sector (nonzero
   * {@link #sic}) or a category heading (zero {@link #sic}, with {@link #rchild} its second child).
   * {@link #idx} is this node's position in the {@code nodeBlock} array, standing in for the C's
   * {@code p + 1} pointer arithmetic to reach the node right after this one (its first child, when
   * it is a category heading).
   */
  private static final class Node {
    Node rchild;
    String title = "";
    final long[] table = new long[MAX_N + 1];
    long total;
    long thresh;
    long sic;
    long tag;
    Node link;
    Arc sicList;
    final int idx;

    Node(int idx) {
      this.idx = idx;
    }
  }

  /**
   * {@code econ(n,omit,threshold,seed)}: a graph of {@code n} of the sectors of the 1985 U.S.
   * economy, with an arc {@code u->v} whenever sector {@code u} sends at least {@code
   * threshold/65536} of {@code v}'s total flow to {@code v}. {@code n}, {@code omit} and {@code
   * threshold} are treated as C {@code unsigned long}.
   *
   * <p>Returns {@code null} and sets {@link Gb#panicCode} if {@code econ.dat} cannot be read or is
   * malformed.
   */
  public static Graph econ(long n, long omit, long threshold, long seed) {
    Flip.initRand(seed);

    // Section 9: clamp the parameters.
    if (Long.compareUnsigned(omit, 2) > 0) {
      omit = 2;
    }
    if (n == 0 || Long.compareUnsigned(n, MAX_N - omit) > 0) {
      n = MAX_N - omit;
    } else if (Long.compareUnsigned(n + omit, 3) < 0) {
      omit = 3 - n;
    }
    if (Long.compareUnsigned(threshold, 65536) > 0) {
      threshold = 65536;
    }

    // Section 10: create the graph.
    Graph newGraph = Gb.newGraph(n);
    if (newGraph == null) {
      Gb.panicCode = Gb.NO_ROOM;
      Gb.troubleCode = 0;
      return null;
    }
    newGraph.id =
        "econ("
            + Long.toUnsignedString(n)
            + ","
            + Long.toUnsignedString(omit)
            + ","
            + Long.toUnsignedString(threshold)
            + ","
            + seed
            + ")";
    newGraph.utilTypes = "ZZZZIAIZZZZZZZ";

    // Section 14: open the data file and allocate the category tree.
    if (GbIo.open("econ.dat") != 0) {
      Gb.panicCode = Gb.EARLY_DATA_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    Node[] nodeBlock = new Node[2 * MAX_N - 3];
    for (int i = 0; i < nodeBlock.length; i++) {
      nodeBlock[i] = new Node(i);
    }
    Node[] nodeIndex = new Node[MAX_N + 1];

    // Section 15: read the category tree, then the Adjustments and Users pseudo-sectors.
    Deque<Node> stack = new ArrayDeque<>();
    int idx;
    for (idx = 0; idx < NORM_N + NORM_N - 1; idx++) {
      Node node = nodeBlock[idx];
      node.title = GbIo.string(':');
      if (node.title.length() > 43) {
        Gb.panicCode = Gb.SYNTAX_ERROR;
        Gb.troubleCode = 0;
        return null;
      }
      if (GbIo.ch() != ':') {
        Gb.panicCode = Gb.SYNTAX_ERROR + 1;
        Gb.troubleCode = 0;
        return null;
      }
      long c = GbIo.number(10);
      node.sic = c;
      if (c == 0) {
        stack.push(node);
      } else {
        nodeIndex[(int) c] = node;
        if (!stack.isEmpty()) {
          stack.pop().rchild = nodeBlock[idx + 1];
        }
      }
      if (GbIo.ch() != '\n') {
        Gb.panicCode = Gb.SYNTAX_ERROR + 2;
        Gb.troubleCode = 0;
        return null;
      }
      GbIo.newline();
    }
    if (!stack.isEmpty()) {
      Gb.panicCode = Gb.SYNTAX_ERROR + 3;
      Gb.troubleCode = 0;
      return null;
    }
    for (int k = NORM_N; k >= 1; k--) {
      if (nodeIndex[k] == null) {
        Gb.panicCode = Gb.SYNTAX_ERROR + 4;
        Gb.troubleCode = 0;
        return null;
      }
    }
    Node adjustments = nodeBlock[idx];
    adjustments.title = "Adjustments";
    adjustments.sic = ADJ_SEC;
    nodeIndex[ADJ_SEC] = adjustments;
    Node users = nodeBlock[idx + 1];
    users.title = "Users";
    nodeIndex[MAX_N] = users;

    // Section 16: read the flow matrix, one row per sector.
    for (int k = 1; k <= MAX_N; k++) {
      long s = 0;
      if (GbIo.ch() != '\n') {
        Gb.panicCode = Gb.SYNTAX_ERROR + 5;
        Gb.troubleCode = 0;
        return null;
      }
      GbIo.newline();
      Node p = nodeIndex[k];
      for (int j = 1; j < MAX_N; j++) {
        long x = GbIo.number(10);
        p.table[j] = x;
        s += x;
        nodeIndex[j].total += x;
        if (j % 10 == 0) {
          if (GbIo.ch() != '\n') {
            Gb.panicCode = Gb.SYNTAX_ERROR + 6;
            Gb.troubleCode = 0;
            return null;
          }
          GbIo.newline();
        } else if (GbIo.ch() != ',') {
          Gb.panicCode = Gb.SYNTAX_ERROR + 7;
          Gb.troubleCode = 0;
          return null;
        }
      }
      p.table[MAX_N] = s;
    }

    // Section 17: decide which sectors survive as their own vertex.
    long l = n + omit - 2;
    if (l == NORM_N) {
      // Section 18: every named sector survives.
      for (int k = NORM_N; k >= 1; k--) {
        nodeIndex[k].tag = 1;
      }
    } else if (seed != 0) {
      randomSubtree(nodeBlock, nodeIndex, l);
    } else {
      greedyMerge(nodeBlock, nodeIndex, l);
    }

    // Section 28: build each surviving vertex's SIC-code list, merging table rows and columns.
    for (int i = nodeIndex[ADJ_SEC].idx; i >= 0; i--) {
      Node p = nodeBlock[i];
      if (p.sic != 0) {
        p.sicList = Gb.virginArc();
        p.sicList.len = p.sic;
      } else {
        Node pl = nodeBlock[p.idx + 1];
        Node pr = p.rchild;
        if (p.tag == 0) {
          p.tag = pl.tag + pr.tag;
        }
        if (p.tag <= 1) {
          // Section 29: merge pr into pl under p.
          Arc a = pl.sicList;
          long jj = pl.sic;
          long kk = pr.sic;
          p.sicList = a;
          while (a.next != null) {
            a = a.next;
          }
          a.next = pr.sicList;
          for (int k = MAX_N; k >= 1; k--) {
            Node q = nodeIndex[k];
            if (q != null) {
              if (q != pl && q != pr) {
                q.table[(int) jj] += q.table[(int) kk];
              }
              p.table[k] = pl.table[k] + pr.table[k];
            }
          }
          p.total = pl.total + pr.total;
          p.sic = jj;
          p.table[(int) jj] += p.table[(int) kk];
          nodeIndex[(int) jj] = p;
          nodeIndex[(int) kk] = null;
        }
      }
    }

    // Section 30: drop the omitted special sectors, or fold Users' bookkeeping row.
    if (omit == 2) {
      nodeIndex[ADJ_SEC] = null;
      nodeIndex[MAX_N] = null;
    } else if (omit == 1) {
      nodeIndex[MAX_N] = null;
    } else {
      for (int k = ADJ_SEC; k >= 1; k--) {
        Node p = nodeIndex[k];
        if (p != null) {
          p.table[MAX_N] = p.total - p.table[MAX_N];
        }
      }
      Node p = nodeIndex[MAX_N];
      p.total = p.table[MAX_N];
      p.table[MAX_N] = 0;
    }

    // Section 27: compute each surviving sector's arc threshold.
    for (int k = MAX_N; k >= 1; k--) {
      Node p = nodeIndex[k];
      if (p != null) {
        if (threshold == 0) {
          p.thresh = -99999999;
        } else {
          p.thresh = ((p.total >> 16) * threshold) + (((p.total & 0xffff) * threshold) >> 16);
        }
      }
    }

    // Section 25: assign vertices to the surviving sectors, then add the flow arcs.
    Vertex[] vertIndex = new Vertex[MAX_N + 1];
    int vi = (int) n;
    for (int k = MAX_N; k >= 1; k--) {
      Node p = nodeIndex[k];
      if (p != null) {
        vi--;
        Vertex v = newGraph.vertices[vi];
        vertIndex[k] = v;
        v.name = Gb.saveString(p.title);
        v.z.A(p.sicList);
        v.y.I = p.total;
      } else {
        vertIndex[k] = null;
      }
    }
    if (vi != 0) {
      Gb.panicCode = Gb.IMPOSSIBLE;
      Gb.troubleCode = 0;
      return null;
    }
    for (int j = MAX_N; j >= 1; j--) {
      Node p = nodeIndex[j];
      if (p != null) {
        Vertex u = vertIndex[j];
        for (int k = MAX_N; k >= 1; k--) {
          Vertex v = vertIndex[k];
          if (v != null && p.table[k] != 0 && p.table[k] > nodeIndex[k].thresh) {
            Gb.newArc(u, v, 1L);
            u.arcs.a.I = p.table[k];
          }
        }
      }
    }

    if (GbIo.close() != 0) {
      Gb.panicCode = Gb.LATE_DATA_FAULT;
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
   * Section 21: tags a uniformly random subtree of {@code l} of the named sectors to survive as
   * their own vertex, by first, bottom-up, counting for each category the number of ways to choose
   * a survivor-subset of each possible size (a polynomial convolution over its two children, kept
   * in {@link Node#table}), then, top-down, splitting {@code l} between each category's two
   * children in proportion to those counts.
   */
  private static void randomSubtree(Node[] nodeBlock, Node[] nodeIndex, long l) {
    nodeBlock[0].tag = l;
    int adjIdx = nodeIndex[ADJ_SEC].idx;
    for (int i = adjIdx - 1; i >= 1; i--) {
      Node p = nodeBlock[i];
      if (p.rchild != null) {
        Node pl = nodeBlock[p.idx + 1];
        Node pr = p.rchild;
        p.table[1] = 1;
        p.table[2] = 1;
        if (pl.rchild == null) {
          if (pr.rchild == null) {
            p.table[0] = 2;
          } else {
            for (int k = 2; k <= pr.table[0]; k++) {
              p.table[1 + k] = pr.table[k];
            }
            p.table[0] = pr.table[0] + 1;
          }
        } else if (pr.rchild == null) {
          for (int k = 2; k <= pl.table[0]; k++) {
            p.table[1 + k] = pl.table[k];
          }
          p.table[0] = pl.table[0] + 1;
        } else {
          p.table[2] = 0;
          for (int j = (int) pl.table[0]; j > 0; j--) {
            long t = pl.table[j];
            for (int k = (int) pr.table[0]; k > 0; k--) {
              p.table[j + k] += t * pr.table[k];
            }
          }
          p.table[0] = pl.table[0] + pr.table[0];
        }
      }
    }
    for (int i = 0; i < adjIdx; i++) {
      Node p = nodeBlock[i];
      if (p.tag > 1) {
        long ll = p.tag;
        Node pl = nodeBlock[p.idx + 1];
        Node pr = p.rchild;
        if (pl.rchild == null) {
          pl.tag = 1;
          pr.tag = ll - 1;
        } else if (pr.rchild == null) {
          pl.tag = ll - 1;
          pr.tag = 1;
        } else {
          // Section 24: split ll between pl and pr, weighted by the convolution counts.
          boolean scaled = false;
          long ss;
          if (p == nodeBlock[0]) {
            ss = 0;
            scaled = ll > 29 && ll < 67;
            long start = ll > pr.table[0] ? ll - pr.table[0] : 1;
            for (long k = start; k <= pl.table[0] && k < ll; k++) {
              ss +=
                  scaled
                      ? ((pl.table[(int) k] + 0x3ff) >> 10) * pr.table[(int) (ll - k)]
                      : pl.table[(int) k] * pr.table[(int) (ll - k)];
            }
          } else {
            ss = p.table[(int) ll];
          }
          long rr = Flip.unifRand(ss);
          long k = ll > pr.table[0] ? ll - pr.table[0] : 1;
          ss = 0;
          while (ss <= rr) {
            ss +=
                scaled
                    ? ((pl.table[(int) k] + 0x3ff) >> 10) * pr.table[(int) (ll - k)]
                    : pl.table[(int) k] * pr.table[(int) (ll - k)];
            k++;
          }
          pl.tag = k - 1;
          pr.tag = ll - k + 1;
        }
      }
    }
  }

  /**
   * Section 19: tags the {@code l} named sectors with the greatest total flow to survive as their
   * own vertex, merging the rest into their category two at a time, smallest total first.
   */
  private static void greedyMerge(Node[] nodeBlock, Node[] nodeIndex, long l) {
    Node special = nodeIndex[MAX_N];
    int adjIdx = nodeIndex[ADJ_SEC].idx;
    for (int i = adjIdx - 1; i >= 0; i--) {
      Node p = nodeBlock[i];
      if (p.rchild != null) {
        p.total = nodeBlock[p.idx + 1].total + p.rchild.total;
      }
    }
    special.link = nodeBlock[0];
    nodeBlock[0].link = special;
    long k = 1;
    while (k < l) {
      // Section 20: pop the smallest-total unresolved node and reinsert its children by total.
      Node p = special.link;
      special.link = p.link;
      if (p.rchild == null) {
        p.tag = 1;
      } else {
        Node pl = nodeBlock[p.idx + 1];
        Node pr = p.rchild;
        Node q;
        for (q = special; Long.compareUnsigned(q.link.total, pl.total) > 0; q = q.link) {
          // scanning for pl's insertion point
        }
        pl.link = q.link;
        q.link = pl;
        for (q = special; Long.compareUnsigned(q.link.total, pr.total) > 0; q = q.link) {
          // scanning for pr's insertion point
        }
        pr.link = q.link;
        q.link = pr;
        k++;
      }
    }
    for (Node p = special.link; p != special; p = p.link) {
      p.tag = 1;
    }
  }
}
