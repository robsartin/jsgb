---
status: Accepted
date: "2026-09-19"
topic: ordering-without-pointers
tags: [project, data-model]
supersedes: []
related: []
---
# 17. Explicit ordering stand-ins over pointer arithmetic

## Context

Three C behaviours depend on memory addresses that Java has no equivalent for. `gb_new_edge`
places the two arcs of an edge according to whether `u < v` as pointers, which in C means
allocation order, because all vertices of a graph sit in one allocated array. An arc's mate was
found by pointer arithmetic (`a+1` or `a-1`) rather than a stored reference. `save_graph` numbers
arcs by their position within the graph's arc blocks, including unused slots, which is why
`test.correct` reports 102 arcs for a graph that logically has 10 — the C allocator hands out
arcs 102 at a time (`arcs_per_block`) and `save_graph` walks that block layout.

## Decision

`Vertex` carries a public `index` field into its owning graph's vertex array, standing in for the
address order that `newEdge` used in C. It is public, not package-private, because later
increments need it outside this package: `save` reads `a.tip.index` to number vertices, and the
`basic` transformers read `newg.vertices[v.index]`. `Arc` carries an explicit `mate` reference,
set by `newEdge`, replacing pointer-arithmetic neighbor lookup. `Graph` keeps its arcs in fixed
blocks of 102 (`Gb.ARCS_PER_BLOCK`) in allocation order, and `save` enumerates arcs in that block
order, including unused slots, so its numbering matches `test.correct` exactly.

## Alternatives considered

- **`System.identityHashCode` ordering** (use the JVM's identity hash as a stand-in for address
  order) — rejected: it is not stable. The JVM does not guarantee `identityHashCode` is
  monotonic with allocation order, or even stable across garbage collection and object
  relocation, so it cannot reproduce the specific deterministic order that C's one contiguous
  vertex array produced.
- **A flat arc list** (store a graph's arcs as a single list with no block boundaries or unused
  slots) — rejected: it cannot reproduce `test.correct`, which explicitly encodes the C
  allocator's block-of-102 numbering (a graph with 10 arcs is reported as having 102, including
  the unused slots in its final block). The oracle output itself depends on this block layout,
  so any representation that drops it fails the acceptance test.

## Consequences

This decision rests on an assumption recorded as a risk in the design spec: the C sorts blocks
by address, which for sequential allocation happens to equal allocation order; jsgb assumes this
holds for the reference build and preserves order explicitly via block order rather than
deriving it from anything address-like. `Vertex.index` and `Arc.mate` carry no semantic meaning
to the algorithms themselves — they exist solely to reproduce this address-order-dependent
output byte for byte. `Arc.mate` is set only by `newEdge`: arcs created by `newArc`, or by a
future `restore_graph`, have a null mate, whereas the C's `edge_trick` pairs arcs by slot parity
regardless of how they were created; its only user is `miles_span -v` on a graph built purely
with `newEdge`, so the narrower Java field is sufficient.
