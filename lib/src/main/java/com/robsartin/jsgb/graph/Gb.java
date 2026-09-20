package com.robsartin.jsgb.graph;

/**
 * Port of the {@code gb_graph} procedures and globals: graph creation, arc and edge creation,
 * identification strings, and the name hash. State is global and single-threaded, as in C.
 */
public final class Gb {

  public static final int ID_FIELD_SIZE = 161;
  public static final int ARCS_PER_BLOCK = 102;

  /** The C {@code null_string}: the name of every vertex until it is given one. */
  public static final String NULL_STRING = "";

  public static final long ALLOC_FAULT = -1;
  public static final long NO_ROOM = 1;
  public static final long EARLY_DATA_FAULT = 10;
  public static final long LATE_DATA_FAULT = 11;
  public static final long SYNTAX_ERROR = 20;
  public static final long BAD_SPECS = 30;
  public static final long VERY_BAD_SPECS = 40;
  public static final long MISSING_OPERAND = 50;
  public static final long INVALID_OPERAND = 60;
  public static final long IMPOSSIBLE = 90;

  /** Nonzero if verbose output is desired. */
  public static long verbose;

  /** Set by a generator that fails; see the panic constants. */
  public static long panicCode;

  /** Bit 1: allocation failed; bit 2: an illegal request was made. */
  public static long troubleCode;

  /** Number of spare vertices allocated beyond {@code n} by {@link #newGraph}. */
  public static long extraN = 4;

  private static final Graph DUMMY_GRAPH = new Graph();
  private static Graph curGraph = DUMMY_GRAPH;
  private static Arc[] curBlock;
  private static int nextIndex;

  private Gb() {}

  /** Restores the just-loaded state. Tests only. */
  static void reset() {
    verbose = 0;
    panicCode = 0;
    troubleCode = 0;
    extraN = 4;
    curGraph = DUMMY_GRAPH;
    curBlock = null;
    nextIndex = 0;
    DUMMY_GRAPH.ww.ref = null;
  }

  /** The graph that {@link #newArc} and {@link #newEdge} add to. Tests only. */
  static Graph curGraph() {
    return curGraph;
  }

  /**
   * Largest vertex count {@link #newGraph} will attempt, leaving headroom below the JVM's
   * array-size limit.
   */
  private static final long MAX_TOTAL_VERTICES = Integer.MAX_VALUE - 8L;

  /**
   * {@code gb_new_graph(n)}: a new graph with {@code n + extra_n} vertices; becomes current. As in
   * C, an impossible request (negative {@code n}, or {@code n + extra_n} too large to allocate)
   * yields {@code null} instead of a graph and, mirroring the C's {@code NULL} return, makes the
   * dummy graph current (so a subsequent {@link #newArc} throws), clears the arc cursor, and clears
   * {@link #troubleCode}.
   */
  public static Graph newGraph(long n) {
    long total0 = n + extraN;
    if (n < 0 || total0 > MAX_TOTAL_VERTICES) {
      curGraph = DUMMY_GRAPH;
      curBlock = null;
      nextIndex = 0;
      troubleCode = 0;
      return null;
    }
    Graph g = new Graph();
    int total = (int) total0;
    g.vertices = new Vertex[total];
    for (int i = 0; i < total; i++) {
      g.vertices[i] = new Vertex(i);
    }
    g.n = n;
    g.id = "gb_new_graph(" + n + ")";
    g.utilTypes = "ZZZZZZZZZZZZZZ";
    curGraph = g;
    curBlock = null;
    nextIndex = 0;
    troubleCode = 0;
    return g;
  }

  /** {@code gb_save_string(s)}: in C copies {@code s} into the graph's arena; here identity. */
  public static String saveString(String s) {
    return s;
  }

  /**
   * {@code gb_recycle(g)}: releases the graph's storage. Using {@code g} afterwards is an error.
   */
  public static void recycle(Graph g) {
    if (g != null) {
      g.vertices = null;
      g.arcBlocks.clear();
    }
  }

  /** {@code mark_bipartite(g, n1)}: records the size of the first part in {@code uu.I}. */
  public static void markBipartite(Graph g, long n1) {
    g.uu.I = n1;
    g.utilTypes = g.utilTypes.substring(0, 8) + 'I' + g.utilTypes.substring(9);
  }

  /**
   * {@code gb_typed_alloc(count, Vertex, g->data)}: a fresh vertex block registered on {@code g}.
   */
  public static Vertex[] allocVertices(Graph g, int count) {
    Vertex[] block = new Vertex[count];
    for (int i = 0; i < count; i++) {
      block[i] = new Vertex(i);
    }
    g.extraVertexBlocks.add(block);
    return block;
  }

  /** {@code gb_typed_alloc(count, Arc, g->data)}: a fresh arc block registered on {@code g}. */
  public static Arc[] allocArcs(Graph g, int count) {
    Arc[] block = newBlock(count);
    g.arcBlocks.add(block);
    return block;
  }

  /**
   * What {@code restore_graph} does after {@code gb_new_graph(0)}: drops the graph's storage and
   * allocates exactly {@code n} vertices (at least one) and one block of exactly {@code m} arcs (at
   * least one). If {@code g} is the current graph, the arc cursor is cleared (as it already is
   * right after {@code gb_new_graph(0)}, the only intended call sequence), so a later {@link
   * #newArc} starts a fresh block instead of resuming a now-discarded one; if {@code g} is not
   * current, its cursor is left alone.
   */
  public static void restoreStorage(Graph g, int n, int m) {
    int vcount = Math.max(n, 1);
    g.vertices = new Vertex[vcount];
    for (int i = 0; i < vcount; i++) {
      g.vertices[i] = new Vertex(i);
    }
    g.extraVertexBlocks.clear();
    g.arcBlocks.clear();
    allocArcs(g, Math.max(m, 1));
    if (g == curGraph) {
      curBlock = null;
      nextIndex = 0;
    }
  }

  /**
   * The C test {@code a->next == a+1}: {@code a} is the first arc of a self-loop edge. Requires
   * mates to have been assigned ({@link #newEdge} or {@code restore}); false for arcs without a
   * mate.
   */
  public static boolean isFirstOfSelfLoop(Arc a) {
    return a.mate != null && a.next == a.mate && a.mate.index == a.index + 1;
  }

  private record ArcCursor(Arc[] block, int index) {}

  private static Arc[] newBlock(int size) {
    Arc[] block = new Arc[size];
    for (int i = 0; i < size; i++) {
      block[i] = new Arc(i);
    }
    return block;
  }

  /** {@code gb_virgin_arc()}: the next unused arc slot of the current graph. */
  public static Arc virginArc() {
    if (curGraph == DUMMY_GRAPH) {
      throw new IllegalStateException("no current graph: call gb_new_graph first");
    }
    if (curBlock == null || nextIndex == curBlock.length) {
      curBlock = newBlock(ARCS_PER_BLOCK);
      curGraph.arcBlocks.add(curBlock);
      nextIndex = 1;
      return curBlock[0];
    }
    return curBlock[nextIndex++];
  }

  /** {@code gb_new_arc(u, v, len)}: a new arc from {@code u} to {@code v} in the current graph. */
  public static void newArc(Vertex u, Vertex v, long len) {
    Arc a = virginArc();
    a.tip = v;
    a.next = u.arcs;
    a.len = len;
    u.arcs = a;
    curGraph.m++;
  }

  /**
   * {@code gb_new_edge(u, v, len)}: a new undirected edge as two consecutive arcs. The arc from the
   * lower-indexed vertex occupies the first slot, as in C where the first arc was the one at the
   * lower address.
   */
  public static void newEdge(Vertex u, Vertex v, long len) {
    Arc a = virginArc();
    if (nextIndex == curBlock.length) {
      throw new IllegalStateException(
          "gb_new_edge must not be mixed with an odd number of gb_new_arc calls");
    }
    Arc mate = curBlock[nextIndex++];
    if (u.index < v.index) {
      a.tip = v;
      a.next = u.arcs;
      mate.tip = u;
      mate.next = v.arcs;
      u.arcs = a;
      v.arcs = mate;
    } else {
      mate.tip = v;
      mate.next = u.arcs;
      u.arcs = mate;
      a.tip = u;
      a.next = v.arcs;
      v.arcs = a;
    }
    a.len = len;
    mate.len = len;
    a.mate = mate;
    mate.mate = a;
    curGraph.m += 2;
  }

  /** {@code switch_to_graph(g)}: makes {@code g} current, parking the old graph's arc cursor. */
  public static void switchToGraph(Graph g) {
    curGraph.ww.ref = curBlock == null ? null : new ArcCursor(curBlock, nextIndex);
    curGraph = g == null ? DUMMY_GRAPH : g;
    if (curGraph.ww.ref instanceof ArcCursor c) {
      curBlock = c.block();
      nextIndex = c.index();
    } else {
      curBlock = null;
      nextIndex = 0;
    }
    curGraph.ww.ref = null;
    curGraph.xx.ref = null;
    curGraph.yy.ref = null;
    curGraph.zz.ref = null;
  }

  /**
   * Clamps a negative {@code max} to the empty string, whereas C's {@code %.*s} with a negative
   * precision prints the whole string. Unreachable in SGB, since {@code s1 + s2} never exceed 156
   * characters.
   */
  private static String prefix(String s, int max) {
    return s.length() <= max ? s : s.substring(0, Math.max(max, 0));
  }

  /** {@code make_compound_id(g, s1, gg, s2)}: {@code g.id = s1 + gg.id + s2}, truncated to fit. */
  public static void makeCompoundId(Graph g, String s1, Graph gg, String s2) {
    int avail = ID_FIELD_SIZE - s1.length() - s2.length();
    String tmp = gg.id;
    if (tmp.length() < avail) {
      g.id = s1 + tmp + s2;
    } else {
      g.id = s1 + prefix(tmp, avail - 5) + "...)" + s2;
    }
  }

  /** {@code make_double_compound_id}: {@code s1 + gg.id + s2 + ggg.id + s3}, truncated to fit. */
  public static void makeDoubleCompoundId(
      Graph g, String s1, Graph gg, String s2, Graph ggg, String s3) {
    int avail = ID_FIELD_SIZE - s1.length() - s2.length() - s3.length();
    if (gg.id.length() + ggg.id.length() < avail) {
      g.id = s1 + gg.id + s2 + ggg.id + s3;
    } else {
      g.id =
          s1
              + prefix(gg.id, avail / 2 - 5)
              + "...)"
              + s2
              + prefix(ggg.id, (avail - 9) / 2)
              + "...)"
              + s3;
    }
  }

  private static final long HASH_MULT = 314159;
  private static final long HASH_PRIME = 516595003;

  /** The bucket index of {@code s} in the current graph (C section 45). Tests only. */
  static int hashBucket(String s) {
    long h = 0;
    for (byte t : s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)) {
      h += (h ^ (h >> 1)) + HASH_MULT * (t & 0xff);
      while (h >= HASH_PRIME) {
        h -= HASH_PRIME;
      }
    }
    return (int) (h % curGraph.n);
  }

  /** {@code hash_in(v)}: inserts {@code v} into the current graph's name table. */
  public static void hashIn(Vertex v) {
    Vertex u = curGraph.vertices[hashBucket(v.name)];
    v.u.V(u.v.V());
    u.v.V(v);
  }

  /** {@code hash_out(s)}: the vertex of the current graph named {@code s}, or null. */
  public static Vertex hashOut(String s) {
    Vertex u = curGraph.vertices[hashBucket(s)];
    for (u = u.v.V(); u != null; u = u.u.V()) {
      if (s.equals(u.name)) {
        return u;
      }
    }
    return null;
  }

  /** {@code hash_setup(g)}: builds the name table of {@code g} in slots {@code u} and {@code v}. */
  public static void hashSetup(Graph g) {
    if (g != null && g.n > 0) {
      Graph saved = curGraph;
      curGraph = g;
      for (int i = 0; i < g.n; i++) {
        g.vertices[i].v.V(null);
      }
      for (int i = 0; i < g.n; i++) {
        hashIn(g.vertices[i]);
      }
      g.utilTypes = "VV" + g.utilTypes.substring(2);
      curGraph = saved;
    }
  }

  /** {@code hash_lookup(s, g)}: the vertex of {@code g} named {@code s}, or null. */
  public static Vertex hashLookup(String s, Graph g) {
    if (g != null && g.n > 0) {
      Graph saved = curGraph;
      curGraph = g;
      Vertex v = hashOut(s);
      curGraph = saved;
      return v;
    }
    return null;
  }
}
