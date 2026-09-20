/**
 * Port of {@code gb_plane}: the {@code plane} and {@code plane_miles} planar-graph generators,
 * built on a general-purpose incremental Delaunay triangulation, {@code delaunay}. See {@link
 * com.robsartin.jsgb.plane.Plane} for the arc-pool and branch-node data structures the
 * triangulation uses, the geometric predicates it relies on, and the two edge callbacks that turn a
 * triangulation into a graph.
 */
package com.robsartin.jsgb.plane;
