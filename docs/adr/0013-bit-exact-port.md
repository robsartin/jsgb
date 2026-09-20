---
status: Accepted
date: "2026-09-19"
topic: bit-exact-port
tags: [project, fidelity]
supersedes: []
related: []
---
# 13. Bit-exact port over behavioural equivalence

## Context

The Stanford GraphBase (SGB) is a benchmark platform, not just a graph library: its value
comes from every implementation producing exactly the same output for the same inputs and
seeds, so that results (including the published algorithm-performance numbers in Knuth's own
work) are comparable across machines, compilers, and now languages. The reference is the SGB
release of 2025-12-28 mirrored at `~/code/sgb` (commit `88fac2f051f445d68521dbe6cb756a43e5d53e8a`).
Its distribution ships `sample.correct` and `test.correct`, captured outputs the C self-tests
are checked against, and the C source itself is the fullest and most authoritative statement of
what every function does.

## Decision

jsgb reproduces the C SGB's output byte for byte, for every generator, algorithm, and demo,
given the same inputs and seeds. `sample.correct`, `test.correct`, and per-demo stdout captured
from the C binaries (see the design spec's Testing/Oracle tests section) are the acceptance
oracles for the port. The C source is the specification: where a design document and the C
disagree, the C wins and the document is corrected by a dated amendment, not the other way
around.

## Alternatives considered

- **Behavioural equivalence** (same asymptotic behaviour and correct answers, without byte
  identical output) — rejected: this discards the property that makes SGB useful as a benchmark
  platform in the first place. SGB exists so the same input graph and constants produce the
  same answer everywhere; an implementation that is merely equivalent cannot be checked against
  `sample.correct` or `test.correct`, and any comparison against previously published SGB-based
  benchmark results would no longer be valid.
- **A same-spirit Java graph library** (an idiomatic API inspired by SGB's ideas, not tied to
  its exact algorithms or output) — rejected: that is a different project with different goals.
  It would not need C source fidelity at all, and would be free to adopt modern Java graph
  representations and API conventions. jsgb's charter is specifically to port SGB, not to write
  a new graph library informed by it.

## Consequences

Several C behaviours that would normally be considered implementation details become
load-bearing and must be reproduced exactly, because the oracle files encode them: the `Flip`
lagged-Fibonacci RNG recurrence and its exact 56-element state, `LinkSort`'s radix-256 sort with
its specific tie-breaking rule for passes 1 and 2, the SGB name hash function and its checksum
modulus, and memory-layout-dependent behaviours such as arc-block numbering in `save_graph`.
Idiomatic Java design is therefore subordinate to fidelity throughout the kernel; several of the
other project ADRs in this document (static global state, public mutable fields, null plus
`panicCode`, arc blocks in place of pointer order) record specific places where an idiomatic
Java alternative was considered and rejected because it could not preserve this bit-exactness.
