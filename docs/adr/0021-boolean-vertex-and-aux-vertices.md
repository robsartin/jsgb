---
status: Accepted
date: "2026-09-20"
topic: boolean-vertex-and-aux-vertices
tags: [project, graph]
supersedes: []
related: [ordering-without-pointers, mates-by-position-for-restored-graphs]
---
# 21. Boolean vertex and auxiliary vertices for gb_gates

## Context

`gb_gates.w` (increment 3a) needs two C idioms that increment 2's `Vertex`, `Save`, and
`TestSample` have no room for.

First, `gb_gates` stores a boolean constant, the C's `(Vertex*) 1`, directly in places typed to
hold a vertex pointer: an arc's `tip`, and `V`-typed `util` slots. `is_boolean(v)` tests for it
with `(unsigned long) v <= 1`, which is true for both `NULL` (an absent value) and `1` (the
boolean `true`); the two are distinguishable and callers that care check `v == NULL` themselves.
Increment 2 anticipated a `V` slot holding this value by encoding it as `ref == null && I == 1` —
a spare `long` next to the reference, since `Vertex` has no field wide enough to double as a raw
pointer bit pattern. That encoding cannot represent the same constant in `Arc.tip`, which has no
paired `I` field: `Arc.tip` is a bare `Vertex` reference with nowhere to stash a `1`.

Second, `gb_gates` (and, from `gb_dijk.w`, `dijkstra`'s `head[128]`) allocates scratch vertices
with `gb_typed_alloc(count, Vertex, g->aux_data)`. `aux_data` is a *separate* allocation arena from
`g->data`, which `gb_typed_alloc(count, Vertex, g->data)` (ported as `Gb.allocVertices`) uses; the
distinction matters because `save_graph` walks only blocks reachable from `g->data` when it
classifies and numbers a graph's storage (ADR 0017's block-classification scheme, sections 23 and
27 of `gb_save.w`). A vertex from `aux_data` is deliberately invisible to `save_graph` — it is
working storage for an algorithm, not part of the graph being saved.

## Decision

A single sentinel `Gb.ONE`, constructed as `new Vertex(-1)` with `name = "ONE"`, stands for the
C's `(Vertex*) 1` everywhere a boolean vertex can appear — both `Arc.tip` and a `V`-typed `Util`
slot. `Gb.isBoolean(Vertex v)` is `v == Gb.ONE`; callers that also need the C macro's `NULL` case
test `v == null` themselves, matching the C's `is_boolean` being one macro over two possibilities
that callers already distinguish downstream (`pr_vert`, for instance, checks `NULL` and
`is_boolean` as separate arms). `Gb.ONE`'s index of `-1` marks it as outside every graph's vertex
array, exactly as address `1` is outside every graph's allocated storage in C; any code that reads
`Gb.ONE.index` for graph-relative arithmetic is a bug, the same class of bug pointer arithmetic on
the C constant would be.

`Gb.allocAuxVertices(int count)` mirrors `Gb.allocVertices` — it builds an indexed `Vertex[]` the
same way — but registers the block on no graph. `save_graph` (via `Save`'s block-lookup map, built
only from `g.vertices`, `g.extraVertexBlocks()`, and `g.arcBlocks()`) therefore never sees these
vertices and reports `ADDR_NOT_IN_DATA_AREA` for a slot pointing at one, matching the C leaving an
`aux_data` pointer untranslatable by `save_graph`.

`Save.lookup`, `Save.translate`, and `Save.fillField` are updated to treat `ref == Gb.ONE` as the
boolean case (written as `1`, read back as `Gb.ONE`), replacing the old `ref == null && I == 1`
encoding everywhere it appeared, including `TestSample.prVert`, which now prints `"ONE"` for
`v == Gb.ONE` as a third arm alongside its existing `null` check, and `TestSample.prUtil`'s `V`
case, which now delegates uniformly to `prVert(u.V(), ...)` with no special case of its own.

## Alternatives considered

- **Keep the `I == 1` encoding and add a parallel `Arc.tipIsOne` flag for the arc-tip case** —
  rejected: this gives the same value two representations (a slot-side `I==1` flag and an arc-side
  boolean flag) that every reader — `Save`, `TestSample`, and future `gb_gates` code — would have
  to check both of, multiplying the places a mismatch between them could hide.
- **A `boolean` field on `Vertex` that marks "this is the boolean constant"** — rejected: every
  vertex in every graph would carry a field meaningful only to `gb_gates`, and the C's own test is
  pointer identity (`v == (Vertex*) 1`), not a property read off an arbitrary vertex; a sentinel
  object makes identity the test in Java too, rather than reintroducing a flag to approximate it.

## Consequences

A restored graph's boolean slots and arc tips compare equal to `Gb.ONE` by identity after a
save/restore round trip, so `gb_gates`-produced graphs survive `Save` intact. `Gb.allocAuxVertices`
gives `dijkstra`'s `head[128]` and `gb_gates`' scratch vertices a home that is deliberately outside
`save_graph`'s reach, matching the C's `aux_data`/`data` split; any future generator that wants
scratch storage `save_graph` *should* number must use `Gb.allocVertices` instead. `Gb.prefix`
becoming public is a small, related surface change bundled into this task so a later `gb_gates`
task can reuse the same `%.*s`-truncation helper `make_compound_id` already relies on, instead of
duplicating it.
