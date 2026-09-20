---
status: Accepted
date: "2026-09-19"
topic: public-mutable-fields
tags: [project, api]
supersedes: []
related: [java-conventions]
---
# 15. Public mutable fields on Graph, Vertex, Arc, and Util

## Context

The emitted baseline Java conventions ADR (11) recommends idiomatic modern Java, including
`Optional` over returning null at boundaries and, more broadly, the encapsulated style that
usually pairs with accessor methods rather than exposed fields. SGB's C structs (`gb_vertex`,
`gb_arc`, `gb_graph`, and the `util` union) are plain structs whose fields — `arcs`, `name`,
`tip`, `next`, `len`, `vertices`, `n`, `m`, `id`, `util_types`, and the per-record util slots —
are read and written directly by every generator, algorithm, and demo in the C source. There is
no accessor layer in C to mirror.

## Decision

`Graph`, `Vertex`, `Arc`, and `Util` are mutable final classes exposing public fields that
mirror the C structs field for field: `v.arcs`, `v.name`, `a.tip`, `a.next`, `a.len`,
`g.vertices`, `g.n`, `g.m`, `g.id`, `g.utilTypes`, and the `Util` slots' `long I` and `Object
ref` with their typed accessors. Every generator and demo reads and writes these fields
directly, exactly as the C source does.

## Alternatives considered

- **Getters and setters** (the idiomatic Java shape the baseline conventions ADR points toward)
  — rejected: SGB has dozens of generators and twelve demos touching these fields at every one
  of thousands of call sites; replacing direct field access with accessor calls (`v.getArcs()`
  in place of `v.arcs`) would triple the call-site noise across the port for no safety benefit,
  since type correctness on the untyped util slots is enforced by convention in Java exactly as
  it was by convention in C — each module already documents, in Javadoc, which slot it claims,
  matching the C `#define` macros one for one.

## Consequences

This is a deliberate, recorded deviation from the baseline Java conventions ADR (11)'s
encapsulation guidance, justified because the bit-exact port decision (13) outweighs it here.
Callers can put these objects into states the C original never validated against either; jsgb
accepts the same risk, at the same points, that the original C accepted, and does not add
validation the C source lacks. Field names and types are chosen to match the C prototypes in
`PROTOTYPES/*.ch` as closely as Java syntax allows, so call sites read like a direct transliteration
of the C.
