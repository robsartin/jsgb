package com.robsartin.jsgb.dijk;

import com.robsartin.jsgb.graph.Vertex;

/**
 * The C's four priority-queue function pointers ({@code init_queue}, {@code enqueue}, {@code
 * requeue}, {@code del_min}), gathered into one object so {@link Dijkstra} can be handed an
 * alternate queueing strategy. {@link Dijkstra} uses only these four operations to track vertices
 * that have been seen but are not yet known.
 */
public interface PriorityQueueHooks {

  /** {@code init_queue(d)}: makes the queue empty and prepares for subsequent keys {@code >= d}. */
  void initQueue(long d);

  /** {@code enqueue(v,d)}: puts vertex {@code v} in the queue with key value {@code v.dist = d}. */
  void enqueue(Vertex v, long d);

  /**
   * {@code requeue(v,d)}: takes vertex {@code v} out of the queue and enters it again with the
   * smaller key value {@code v.dist = d}.
   */
  void requeue(Vertex v, long d);

  /**
   * {@code del_min()}: removes a vertex with minimum key from the queue and returns it, or {@code
   * null} if the queue is empty.
   */
  Vertex delMin();
}
