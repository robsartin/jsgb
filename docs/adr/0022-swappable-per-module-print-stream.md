---
status: Accepted
date: "2026-09-20"
topic: swappable-per-module-print-stream
tags: [project, output]
supersedes: []
related: [bit-exact-port, javadoc-as-literate-layer]
---
# 22. Library output goes through a per-module swappable PrintStream

## Context

`gb_dijk` and `gb_gates` each print, in C, straight to `stdout`: `dijk`'s trace mode and `gates`'
`print_gates`/`run_risc` tracing. jsgb's oracle tests capture that output and compare it byte for
byte against a recorded C run, so a test needs to redirect it to a buffer without disturbing
`System.out` (which JUnit, Gradle, and any other test running concurrently also use) and without
changing which bytes get written, since the C's checksum-sensitive output is compared as
ISO-8859-1.

## Decision

Each library module that prints exposes its own `public static PrintStream out` field, constructed
as ISO-8859-1 over `System.out` by default: `Dijkstra.out` and `Gates.out`. A test that needs to
capture a module's output saves the field, swaps in a `PrintStream` over a `ByteArrayOutputStream`
(also ISO-8859-1), runs the call, reads the buffer, and restores the saved field in a `finally`
block — the same save/swap/restore pattern already used for `Gb.panicCode` and other static state.

Which modules carry an `out` field is whatever the code currently declares; this ADR does not list
them; check the classes named above.

## Alternatives considered

- **A `PrintStream` parameter on every printing method** (`printGates(Graph, PrintStream)`,
  `runRisc(Graph, long[], long, long, PrintStream)`) — rejected: ADR 0014 keeps jsgb's public API
  shaped like the C's own function signatures wherever state can stay static instead; threading a
  stream parameter through every printing method (and every method that calls one, transitively)
  breaks that shape for no benefit the static field doesn't already give a test.
- **`System.setOut` around each test** — rejected: `System.out` is JVM-global, so a parallel test
  run or the test runner's own diagnostic output could interleave with or clobber the captured
  bytes; `System.out`'s default platform charset is also not guaranteed to be ISO-8859-1, which
  would silently corrupt a checksum-sensitive byte comparison; and swapping the one global stream
  also redirects any demo code the test happens to invoke, not just the module under test.
- **A single `Gb.out` shared by every printing module, mirroring the C's one `stdout`** — not
  rejected on merit. It was simply not the choice made when `dijk` was ported in increment 3a,
  which is why `Dijkstra.out` and `Gates.out` are separate fields today. Consolidating to one field
  is cheap to do later and is left as an open option to revisit if increment 4's demos need to
  redirect more than one module's output at once; nothing here blocks that change.

## Consequences

A test (or, eventually, a demo's `main`) that wants a module's printed output captured, silenced,
or redirected swaps that module's `out` field and restores it afterward; it does not touch
`System.out` and does not need to know about any other module's stream. Adding a new printing
module means adding its own `out` field, following the same construction (ISO-8859-1 over
`System.out`) the existing fields use, not registering it anywhere central.
