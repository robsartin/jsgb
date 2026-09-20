package com.robsartin.jsgb.dijk;

import com.robsartin.jsgb.graph.Vertex;

/**
 * Port of {@code gb_dijk}'s default priority queue (sections 16-19): a sorted doubly linked list
 * through every seen-but-not-known vertex, keyed by {@code dist}, with a single sentinel head
 * ({@link Dijkstra#HEAD}{@code [0]}) reachable from every element by following {@code llink} in
 * decreasing order of keys. This is a convenient default, good enough for applications that aren't
 * too large; {@link Buckets128} is faster once the queue gets large and arc lengths are small.
 *
 * <p>A new element tends to have a larger key than the vertices already queued, so {@link
 * #enqueue}/{@link #requeue} search from the head's {@code llink} (the largest key) downward. In
 * the special case where every arc has the same length, every vertex is added at the end of the
 * list and removed from the front without ever needing to search, so the algorithm degenerates to
 * plain breadth-first search.
 */
public final class DList implements PriorityQueueHooks {

  private static Vertex head() {
    return Dijkstra.HEAD[0];
  }

  private static Vertex llink(Vertex v) {
    return v.v.V();
  }

  private static void setLlink(Vertex v, Vertex t) {
    v.v.V(t);
  }

  private static Vertex rlink(Vertex v) {
    return v.w.V();
  }

  private static void setRlink(Vertex v, Vertex t) {
    v.w.V(t);
  }

  private static long dist(Vertex v) {
    return v.z.I;
  }

  private static void setDist(Vertex v, long d) {
    v.z.I = d;
  }

  /** {@code init_dlist(d)}: makes the queue empty, keyed for subsequent keys {@code >= d}. */
  @Override
  public void initQueue(long d) {
    Vertex head = head();
    setLlink(head, head);
    setRlink(head, head);
    setDist(head, d - 1); // a value guaranteed to be smaller than any actual key
  }

  /** {@code enlist(v,d)}: inserts {@code v}, searching from the head's largest key downward. */
  @Override
  public void enqueue(Vertex v, long d) {
    Vertex t = llink(head());
    setDist(v, d);
    while (d < dist(t)) {
      t = llink(t);
    }
    setLlink(v, t);
    Vertex tRlink = rlink(t);
    setRlink(v, tRlink);
    setLlink(tRlink, v);
    setRlink(t, v);
  }

  /** {@code reenlist(v,d)}: removes {@code v}, then reinserts it with its smaller key {@code d}. */
  @Override
  public void requeue(Vertex v, long d) {
    Vertex t = llink(v);
    Vertex vRlink = rlink(v);
    setRlink(t, vRlink);
    setLlink(vRlink, t); // remove v
    setDist(v, d); // we assume the new dist is smaller than it was before
    while (d < dist(t)) {
      t = llink(t);
    }
    setLlink(v, t);
    Vertex tRlink = rlink(t);
    setRlink(v, tRlink);
    setLlink(tRlink, v);
    setRlink(t, v);
  }

  /** {@code del_first()}: removes and returns the front of the list, or {@code null} if empty. */
  @Override
  public Vertex delMin() {
    Vertex head = head();
    Vertex t = rlink(head);
    if (t == head) {
      return null;
    }
    Vertex tRlink = rlink(t);
    setRlink(head, tRlink);
    setLlink(tRlink, head);
    return t;
  }
}
