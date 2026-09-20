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
  }

  /** The graph that {@link #newArc} and {@link #newEdge} add to. Tests only. */
  static Graph curGraph() {
    return curGraph;
  }

  /** {@code gb_new_graph(n)}: a new graph with {@code n + extra_n} vertices; becomes current. */
  public static Graph newGraph(long n) {
    Graph g = new Graph();
    int total = (int) (n + extraN);
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
}
