---
status: Accepted
date: "2026-09-20"
topic: mates-by-position-for-restored-graphs
tags: [project, graph]
supersedes: []
related: [ordering-without-pointers]
---
# 20. Mates by position for restored graphs

## Context

`Arc.mate` is set only by `newEdge` (ADR 0017), which pairs the two arcs of an edge it just
created. But `restore_graph` rebuilds a graph from a file — it does not call `newEdge` — and
downstream algorithms such as `random_lengths`, `gunion`, and `lines` still need to find an arc's
inverse on a restored graph, because they follow the C's positional rule from `gb_graph.w`:

> The inverse of an arc `a` from `u` to `v` will be arc `a+1` if and only if `u<v` or
> `a->next=a+1`; it will be arc `a-1` if and only if `u>=v` and `a->next!=a+1`. The condition
> `a->next=a+1` can hold only if `u=v`.

That is `edge_trick`: mate-by-slot-parity, independent of how the arc was created. Increment 1
gave `Arc` no way to express slot adjacency at all, because `next` is an object reference and `+1`
is address arithmetic with no Java equivalent; `Arc.index` (this task) now records slot position
within a block, so slot adjacency becomes expressible as ordinary integer arithmetic.

## Decision

`restore` (a later task) pairs every restored arc by the C's positional rule directly on slots,
before any mate exists: for the arc at slot `i` in a block, walk its vertex's arc list to learn
`u`, then the inverse is the arc at slot `i+1` iff `u.index < v.index` or `a.next` is the arc at
slot `i+1`, else the arc at slot `i-1`. This is `a->next == a+1` read off `Arc.index` — `restore`
cannot use a `mate`-based test at this point because pairing is what *establishes* `mate`.

`Gb.isFirstOfSelfLoop(Arc)`, added by this task, is a separate, consumer-side query for code that
runs *after* mates are assigned — either a graph built by `newEdge`, or a restored graph once
`restore` has finished pairing. It requires `a.mate != null` by design: on an unpaired restored
arc, or on a directed graph's arc (`newArc`, no mate), the question "is this the first arc of a
self-loop *edge*" has no answer yet, so the method reports `false` rather than reading `a.next`
against a slot-adjacent arc that has not been confirmed to be its mate.

## Alternatives considered

- **Leave mates null after restore** — rejected: `test_sample`'s fourth stanza calls
  `random_lengths` on a restored undirected graph and reads the mate's length; a null mate there
  would either throw or silently diverge from the C's output, which the acceptance oracles check
  byte for byte.
- **Re-derive mates lazily inside each algorithm that needs one** — rejected: every consumer
  (`random_lengths`, `gunion`, `lines`, and others still to come) would have to repeat the same
  positional rule, multiplying the places a mismatch with the C could hide.

## Consequences

Restored *directed* graphs get mates that are as meaningless as the C's `a+1`/`a-1` would be for
them — `edge_trick` is defined in terms of undirected-edge layout, and nothing in the ported
algorithms reads a directed graph's mate. `Arc.index`, added for this purpose, also lets `save`
(a later task) number arcs by position the same way `Vertex.index` lets it number vertices.
