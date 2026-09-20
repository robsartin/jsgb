package com.robsartin.jsgb.graph;

/**
 * One utility slot: the C {@code util} union. A slot holds either a {@code long} ({@link #I}) or a
 * reference ({@link #ref}) to a {@link Vertex}, {@link Arc}, {@link Graph} or {@link String}. Which
 * view is meaningful is recorded, per slot, in {@link Graph#utilTypes}; type safety is by
 * convention, as in C.
 */
public final class Util {
  public long I;
  public Object ref;

  public Vertex V() {
    return (Vertex) ref;
  }

  public Arc A() {
    return (Arc) ref;
  }

  public Graph G() {
    return (Graph) ref;
  }

  public String S() {
    return (String) ref;
  }

  public void V(Vertex v) {
    ref = v;
  }

  public void A(Arc a) {
    ref = a;
  }

  public void G(Graph g) {
    ref = g;
  }

  public void S(String s) {
    ref = s;
  }
}
