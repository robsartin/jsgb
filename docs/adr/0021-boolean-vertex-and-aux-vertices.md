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

`gb_gates.w` (increment 3b) needs two C idioms that increment 2's `Vertex`, `Save`, and
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

The sharpest instances of the index hazard are `Basic`'s `newVerts[a.tip.index]` subscripts in the
graph transformers and `Gb.newEdge`'s `u.index < v.index` test, which would fail or misbehave on
`Gb.ONE` exactly as the C's `vert_offset`/pointer comparison would on the address 1; no SGB code
feeds a gates graph to either.

## Amendment (2026-09-20): `reduce`'s `Vertex.index` comparison relies on an invariant, not identity

This corrects and extends the index-hazard warning above: `Gates.reduce` (sections 60-61 and 64 of
`gb_gates.w`) is a second instance, found while fixing the sentinel bug below, and it is not merely
hazardous — its correctness (in both the C and the port) depends on an invariant that is easy to
state and easy to violate.

Section 61's mark-and-sweep walks a latch's `alt` and compares it against the latch itself with the
C's `u < v` (ported as `u.index < v.index`), to decide whether a one-arc `OR` buffer must be
inserted ahead of the latch in the compacted graph; section 64 repeats the same test (`u->alt < u`)
during compaction. `Vertex.index` is the position within a vertex's *own* array — the main graph
array for an ordinary vertex, but the small `Vertex[7]` block from `Gb.allocAuxVertices` for an aux
vertex (`reduceXor`'s NOT gates, sections 57-59). An aux vertex's index is therefore 0..6 within its
own block, unrelated to its position in the graph being reduced; comparing it against a main-array
`index` is meaningful only because of an invariant that keeps an aux vertex from ever reaching this
comparison as a latch's `alt`:

- An aux vertex is created only by `reduceXor`, to absorb an XOR gate's odd parity (sections 58-59).
  `risc` builds no XOR gates (`makeXor` expands to AND/OR/NOT), so no aux vertex is ever created
  while reducing a `risc`-derived graph, and a latch's `alt` in such a graph is always a main-array
  vertex.
- `prod` builds no latches, so section 61's comparison never runs at all while reducing a
  `prod`-derived graph, regardless of how many aux vertices XOR reduction creates.
- `reduce` can run a second pass (only when a latch folds to a constant); each pass resets every
  live vertex's `foo` chain (`v.x.V(...)`, ported as `Gates`' per-pass `x.V` reassignment) to `v+1`,
  which unlinks any aux vertex `reduceXor` spliced in during pass 1 from the chain pass 2 walks. So
  even a graph that mixes XOR gates and latches across passes never has pass *N*'s latch-scan reach
  an aux vertex created in a different pass.
- `partial_gates(prod(...))` is safe by construction: `prod` always returns the result of its own
  `reduce` call, which has already compacted any aux vertices `reduceXor` created into ordinary
  vertices of the fresh output graph (section 62's compaction loop only ever visits `g.vertices`).
  The second `reduce` inside `partialGates` therefore starts from a graph with no aux vertices at
  all.

The C itself is well-defined only under this invariant: a single `reduce` call is only ever asked to
combine XOR gates and latches when it cannot see both in the same pass. No SGB generator violates
it — `risc`'s latches never see XOR gates, `prod`'s XOR gates never see latches — but a future
generator that built both in one graph would need this documented before relying on `reduce`.

### Alternatives considered

- **A global allocation counter as an aux vertex's index** — rejected: there is nothing to
  reproduce it against (the C's addresses across `aux_data` and `data` are not ordered relative to
  each other either, so no oracle exercises an order a counter could match), and it would change
  `Vertex(int)`'s contract — "position within this vertex's own array" — for every module that
  reads `index`, not just `gates`.
- **`IllegalStateException` when `reduce` sees both an XOR-derived aux vertex and a latch in the
  same pass** — defensible under ADR 0016's category of making C's undefined behaviour an explicit
  Java exception, but no SGB code reaches that state, so guarding against it now is speculative;
  deferred as YAGNI until a generator actually needs it.
