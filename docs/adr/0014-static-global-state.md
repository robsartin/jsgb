---
status: Accepted
date: "2026-09-19"
topic: static-global-state
tags: [project, concurrency]
supersedes: []
related: [null-plus-panic-code]
---
# 14. Static global state over a context object

## Context

SGB's C implementation is built around global state: `panic_code`, `verbose`,
`gb_trouble_code`, `extra_n`, `cur_graph`, the RNG array and cursor, `gb_sorted[]`, the open
data file and `io_errors`, and several module-local globals such as the Dijkstra queue state,
`chapters`/`chap_name[]`, and `risc_state[]`. C functions read and mutate these directly rather
than receiving them as parameters. Reproducing SGB's C API shape (static functions with the C
names and parameter order, so that `Words.words(n, wtVector, wtThreshold, seed)` reads like the
C prototype) requires deciding how this global state is represented in Java.

## Decision

Each C global is kept as static state in the Java class that owns it, matching the design
spec's global-to-Java-home mapping: `Gb` in package `graph` owns `panicCode`, `verbose`, and
`curGraph`; `Flip` owns the RNG array and cursor; `GbIo` owns the open file, `ioErrors`, and
`strBuf`; `LinkSort` owns `sorted[]`; `Dijkstra.queue` owns the priority-queue globals; `Books`
owns `chapters`/`chapName[]`; `Gates` owns `riscState[]`. Each static holder exposes a
package-private reset method that tests use to restore a clean starting state between cases.
JUnit runs single-threaded for this project.

## Alternatives considered

- **A context object threaded through every call** (e.g. a `GbContext` instance passed as an
  explicit parameter to every generator, algorithm, and demo method) — rejected: it breaks the
  C API shape this port is committed to reproducing. Function signatures mirror the C
  prototypes' names and parameter order exactly; adding a context parameter to every one of them
  would diverge from that shape for no benefit SGB actually needs — the C original was never
  designed to run multiple independent instances concurrently, and no consumer of this port
  needs that either, so a context object buys nothing at SGB's scale.

## Consequences

jsgb is not thread-safe, by design, matching the C original (this is called out explicitly as
intentional in the design spec's Non-goals). Two demos or generators must never run
concurrently in the same JVM process, and test suites must not run classes touching this state
in parallel, without synchronization the library itself does not provide.
