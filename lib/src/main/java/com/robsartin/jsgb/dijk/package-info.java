/**
 * Port of {@code gb_dijk}: {@link com.robsartin.jsgb.dijk.Dijkstra#dijkstra} finds a shortest path
 * from one vertex to another in a graph with nonnegative arc lengths, with the aid of an optional
 * consistent heuristic function, and {@link com.robsartin.jsgb.dijk.Dijkstra#printDijkstraResult}
 * displays the path found. The priority queue the algorithm uses internally is pluggable, behind
 * {@link com.robsartin.jsgb.dijk.PriorityQueueHooks}; two implementations are provided, a sorted
 * doubly linked list ({@link com.robsartin.jsgb.dijk.DList}) and a faster scheme for graphs whose
 * arc lengths are all small ({@link com.robsartin.jsgb.dijk.Buckets128}).
 */
package com.robsartin.jsgb.dijk;
