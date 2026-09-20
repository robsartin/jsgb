package com.robsartin.jsgb.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A graph: its vertex array, counts, id, utility-type string, six slots, and its arc storage. */
public final class Graph {
  /** The vertices, {@code n + extra_n} of them; null after {@link Gb#recycle}. */
  public Vertex[] vertices;

  /** Number of vertices. */
  public long n;

  /** Number of arcs. */
  public long m;

  /** GraphBase identification, at most {@link Gb#ID_FIELD_SIZE} - 1 characters. */
  public String id = "";

  /** Fourteen characters describing the slots: u v w x y z, then a b, then uu..zz. */
  public String utilTypes = "ZZZZZZZZZZZZZZ";

  public final Util uu = new Util();
  public final Util vv = new Util();
  public final Util ww = new Util();
  public final Util xx = new Util();
  public final Util yy = new Util();
  public final Util zz = new Util();

  final List<Arc[]> arcBlocks = new ArrayList<>();

  final List<Vertex[]> extraVertexBlocks = new ArrayList<>();

  Graph() {}

  /**
   * The arc blocks in allocation order; blocks created by {@link Gb#newArc}/{@link Gb#newEdge} have
   * {@link Gb#ARCS_PER_BLOCK} slots; blocks from {@link Gb#allocArcs}/{@link Gb#restoreStorage}
   * have exactly the size requested.
   */
  public List<Arc[]> arcBlocks() {
    return Collections.unmodifiableList(arcBlocks);
  }

  /**
   * Vertex blocks allocated by {@link Gb#allocVertices} beyond {@link #vertices}, in allocation
   * order.
   */
  public List<Vertex[]> extraVertexBlocks() {
    return Collections.unmodifiableList(extraVertexBlocks);
  }
}
