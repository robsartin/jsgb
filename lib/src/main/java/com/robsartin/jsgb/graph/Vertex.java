package com.robsartin.jsgb.graph;

/** A vertex: its arc list, its name, six utility slots, and its position in the graph's array. */
public final class Vertex {
  /** Linked list of arcs coming out of this vertex. */
  public Arc arcs;

  /** Symbolic identification; {@link Gb#NULL_STRING} until set. */
  public String name = Gb.NULL_STRING;

  public final Util u = new Util();
  public final Util v = new Util();
  public final Util w = new Util();
  public final Util x = new Util();
  public final Util y = new Util();
  public final Util z = new Util();

  /**
   * Position in {@link Graph#vertices}. In C the vertices of a graph occupy one array, so pointer
   * comparison {@code u < v} meant exactly this; {@link Gb#newEdge} relies on it.
   */
  public final int index;

  Vertex(int index) {
    this.index = index;
  }
}
