---
status: Accepted
date: "2026-09-20"
topic: reduce-index-comparison-invariant
tags: [project, gates, vertex-identity]
supersedes: []
related: [boolean-vertex-and-aux-vertices, bit-exact-port]
---
# 23. `reduce` compares vertex indexes under a no-XOR-with-latches invariant

## Context

ADR 21 replaced the C's pointer comparisons with `Vertex.index` comparisons and warned that an
index is a position within a vertex's *own* array, so code that mixes vertices from different
arrays must not compare them. `Gates.reduce` (sections 60-61 and 64 of `gb_gates.w`) is a second
instance of that hazard, found while fixing `reduce`'s sentinel (it borrowed `g.vertices[g.n]`,
a slot that exists only because `Gb.newGraph` allocates spare vertices; a restored graph, or
`Gb.extraN = 0`, has none). Unlike the instances ADR 21 lists, this one is not merely hazardous:
the correctness of the C and of the port depends on an invariant that is easy to state and easy
to violate, so it needs a record of its own rather than an edit to ADR 21, which ADR 1 forbids.

Section 61's mark-and-sweep walks a latch's `alt` and compares it against the latch itself with
the C's `u < v` (ported as `u.index < v.index`), to decide whether a one-arc `OR` buffer must be
inserted ahead of the latch in the compacted graph; section 64 repeats the same test
(`u->alt < u`) during compaction. An ordinary vertex's index is its position in the main graph
array; an aux vertex (the NOT gates `reduceXor` creates, sections 57-59) comes from a
`Vertex[7]` block of `Gb.allocAuxVertices`, so its index is 0..6 within that block, unrelated to
its position in the graph being reduced. Comparing the two is meaningful only if an aux vertex
never reaches the comparison as a latch's `alt`.

## Decision

`reduce` keeps the C's comparison as an `index` comparison and relies on the invariant below;
the code cites this ADR at both comparison sites instead of restating it.

The invariant, and why every SGB generator satisfies it:

- An aux vertex is created only by `reduceXor`, to absorb an XOR gate's odd parity (sections
  58-59). `risc` builds no XOR gates (`makeXor` expands to AND/OR/NOT), so no aux vertex is ever
  created while reducing a `risc`-derived graph, and a latch's `alt` there is always a main-array
  vertex.
- `prod` builds no latches, so section 61's comparison never runs while reducing a `prod`-derived
  graph, however many aux vertices XOR reduction creates.
- `reduce` runs a second pass only when a latch folds to a constant. Each pass resets every live
  vertex's `foo` chain to `v+1` (the port's per-pass `x.V` reassignment), which unlinks any aux
  vertex spliced in during an earlier pass from the chain the next pass walks; a graph that mixes
  XOR gates and latches across passes never has one pass's latch scan reach an aux vertex created
  in another.
- `partial_gates(prod(...))` is safe by construction: `prod` returns the result of its own `reduce`
  call, whose compaction (section 62) has already turned any aux vertices into ordinary vertices of
  the fresh output graph, so the second `reduce` starts from a graph with no aux vertices at all.

The C itself is well-defined only under this invariant: a single `reduce` call is only ever asked
to combine XOR gates and latches when it cannot see both in the same pass. A future generator that
built both in one graph would have to revisit this decision before relying on `reduce`.

## Alternatives considered

- **A global allocation counter as an aux vertex's index** — rejected: there is nothing to
  reproduce it against (the C's addresses across `aux_data` and `data` are not ordered relative to
  each other either, so no oracle exercises an order a counter could match), and it would change
  `Vertex(int)`'s contract, "position within this vertex's own array", for every module that reads
  `index`, not just `gates`.
- **`IllegalStateException` when `reduce` sees both an XOR-derived aux vertex and a latch in the
  same pass** — defensible under ADR 16's category of turning the C's undefined behaviour into an
  explicit Java exception, but no SGB code reaches that state, so guarding against it now is
  speculative; deferred as YAGNI until a generator needs it.
- **Amending ADR 21 in place** — rejected: ADR 1 makes an accepted ADR immutable, and the
  repository's CI enforces it; a new record that extends the old one keeps the timeline truthful.

## Consequences

- `reduce` stays a literal transcription of the C, including its reliance on an invariant the C
  never states; the invariant is now written down where the code points.
- Anyone adding a gate generator that mixes XOR gates and latches must reopen this decision, most
  likely by taking the deferred exception alternative.
- The sentinel fix that surfaced this (an unregistered vertex from `Gb.allocAuxVertices(1)` in
  place of a borrowed array slot) is tested against the C oracle with `Gb.extraN = 0`.
