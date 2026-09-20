package com.robsartin.jsgb.graph;

/** An arc: the vertex it points to, the next arc from the same vertex, a length, two slots. */
public final class Arc {
  public Vertex tip;
  public Arc next;
  public long len;

  /**
   * The other arc of the same edge when this arc was created by {@link Gb#newEdge}; null for arcs
   * created by {@link Gb#newArc}. Replaces the C's {@code a+1}/{@code a-1} pointer arithmetic.
   */
  public Arc mate;

  public final Util a = new Util();
  public final Util b = new Util();
}
