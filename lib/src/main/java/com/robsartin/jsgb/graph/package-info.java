/**
 * Port of {@code gb_graph}. It defines the three record types shared by every GraphBase generator
 * and algorithm — {@link com.robsartin.jsgb.graph.Vertex}, {@link com.robsartin.jsgb.graph.Arc},
 * and {@link com.robsartin.jsgb.graph.Graph} — and the utility-slot convention, {@link
 * com.robsartin.jsgb.graph.Util}, that lets any algorithm attach data of its own to any graph
 * without changing these types. Each graph's 14-character {@code utilTypes} string records what the
 * six vertex slots, two arc slots, and six graph slots currently hold: {@code I} for a {@code
 * long}, {@code S}/{@code V}/{@code A}/{@code G} for a string/vertex/arc/graph reference, or {@code
 * Z} for unused. The C arena allocator ({@code gb_alloc}/{@code gb_free}) is not ported, since the
 * JVM already manages memory; in its place, {@link com.robsartin.jsgb.graph.Vertex#index} stands in
 * for the address order C used to compare vertices, {@link com.robsartin.jsgb.graph.Arc#mate}
 * stands in for the {@code a+1}/{@code a-1} pointer arithmetic that paired an edge's two arcs, and
 * a graph's retained arc blocks (allocated {@link com.robsartin.jsgb.graph.Gb#ARCS_PER_BLOCK} at a
 * time) stand in for the arena's block list.
 */
package com.robsartin.jsgb.graph;
