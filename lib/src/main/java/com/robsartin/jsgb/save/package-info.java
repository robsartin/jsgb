/**
 * Port of {@code gb_save}: a portable ASCII format for a GraphBase graph, plus the two procedures
 * that write and read it. Vertices and arcs are numbered by their position in the graph's own
 * storage blocks, and a pointer field is written as a symbolic reference ({@code V<k>} for the k-th
 * vertex, {@code A<k>} for the k-th arc) rather than an address, with a running checksum recorded
 * at the end of the file so a corrupted transfer is caught on restore.
 *
 * <p>Only the blocks the graph itself registers — its main vertex array, any extra vertex blocks,
 * and its arc blocks — are written or read; data reachable only through untyped or unregistered
 * pointers is not part of the file. See {@link com.robsartin.jsgb.save.Save} for the anomaly codes
 * {@code save_graph} reports when a graph does not fit the format cleanly.
 */
package com.robsartin.jsgb.save;
