package com.robsartin.jsgb.dijk;

import com.robsartin.jsgb.graph.Vertex;

/**
 * Port of {@code gb_dijk}'s alternate priority queue (sections 21-24), for use when every arc
 * length in the graph is known to be less than 128. It maintains 128 doubly linked circular lists,
 * one per key value modulo 128 ({@link Dijkstra#HEAD}{@code [d & 0x7f]}), which never holds more
 * than 128 distinct key values at once when lengths are restricted to {@code 0, ..., 127}; every
 * queue operation then runs in constant time (amortised, for {@link #delMin}), faster than {@link
 * DList} once the queue gets large.
 */
public final class Buckets128 implements PriorityQueueHooks {

  /** Smallest key that may currently be present in the queue. */
  private static long masterKey;

  private static Vertex bucket(long d) {
    return Dijkstra.HEAD[(int) (d & 0x7f)];
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

  private static void setDist(Vertex v, long d) {
    v.z.I = d;
  }

  /** {@code init_128(d)}: empties all 128 lists and sets the smallest key that may appear. */
  @Override
  public void initQueue(long d) {
    masterKey = d;
    for (Vertex u : Dijkstra.HEAD) {
      setLlink(u, u);
      setRlink(u, u);
    }
  }

  /**
   * {@code enq_128(v,d)}: inserts {@code v} at the end of the list for key {@code d}. If the number
   * of lists were not a power of 2, the bucket would be a remainder by division instead of a
   * bitwise and.
   */
  @Override
  public void enqueue(Vertex v, long d) {
    Vertex u = bucket(d);
    setDist(v, d);
    Vertex uLlink = llink(u);
    setLlink(v, uLlink);
    setRlink(uLlink, v);
    setRlink(v, u);
    setLlink(u, v);
  }

  /**
   * {@code req_128(v,d)}: removes {@code v} from its current list and reinserts it at the end of
   * the list for its new, smaller key {@code d}, lowering {@link #masterKey} if needed. In the
   * application to Dijkstra's algorithm the new {@code d} is always {@code masterKey} or more, but
   * this mirrors the C's general-purpose implementation (also used for minimum spanning trees).
   */
  @Override
  public void requeue(Vertex v, long d) {
    Vertex vLlink = llink(v);
    Vertex vRlink = rlink(v);
    setRlink(vLlink, vRlink); // remove v
    setLlink(vRlink, vLlink);
    setDist(v, d);
    Vertex u = bucket(d);
    Vertex uLlink = llink(u);
    setLlink(v, uLlink);
    setRlink(uLlink, v);
    setRlink(v, u);
    setLlink(u, v);
    if (d < masterKey) {
      masterKey = d; // not needed for Dijkstra's algorithm
    }
  }

  /**
   * {@code del_128()}: removes and returns a vertex with minimum key, scanning the 128 lists
   * starting from {@link #masterKey}; {@code null} if all are empty.
   */
  @Override
  public Vertex delMin() {
    for (long d = masterKey; d < masterKey + 128; d++) {
      Vertex u = bucket(d);
      Vertex t = rlink(u);
      if (t != u) { // found a nonempty list with minimum key
        masterKey = d;
        Vertex tRlink = rlink(t);
        setRlink(u, tRlink);
        setLlink(tRlink, u);
        return t; // incidentally, t.dist == d
      }
    }
    return null; // all 128 lists are empty
  }
}
