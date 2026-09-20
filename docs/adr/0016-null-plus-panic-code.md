---
status: Accepted
date: "2026-09-19"
topic: null-plus-panic-code
tags: [project, error-handling]
supersedes: []
related: [static-global-state]
---
# 16. Null return plus Gb.panicCode over a PanicException

## Context

SGB generators in C signal failure by setting the global `panic_code` and returning `NULL`;
callers, including `test_sample`, inspect `panic_code` to decide what happened and print it as
part of the program's normal, oracle-checked output. Separately, C leaves some conditions
genuinely undefined — for example, calling `gb_new_arc` with no current graph set — where there
is no C behaviour to reproduce at all.

## Decision

jsgb generator methods reproduce the C convention: on failure they return `null` and set the
static `Gb.panicCode` (see ADR 14 for the static global state this depends on). `TestSample` and
other callers branch on `Gb.panicCode` the same way `test_sample.c` does and print it, so it
appears correctly in the byte-exact oracle comparison against `sample.correct`. For the one
category the C source leaves undefined — programming errors such as `newArc` with no current
graph — jsgb is deliberately stricter than C and throws `IllegalStateException`, since there is
no well-defined C behaviour to reproduce in those cases.

## Alternatives considered

- **A `PanicException`** (idiomatic Java error signalling for generator failure) — rejected:
  every C call site that can fail, and `test_sample` itself, branches on the numeric panic code
  and prints it as part of the program's ordinary, non-erroring control flow — a panic is not
  "exceptional" from SGB's point of view, it is a normal, oracle-relevant outcome with a numeric
  code. Using exceptions would require a try/catch at each of those call sites merely to recover
  the code and continue exactly where C continues, adding ceremony without changing behaviour,
  and risking an uncaught exception terminating a demo run at a point where the C original
  prints a diagnostic and carries on.

## Consequences

Generator return values must be null-checked by callers at exactly the points the C checked
them, which is not idiomatic modern Java (see ADR 15's related deviation from `Optional`
returns) but matches the oracle's control flow byte for byte. The single place jsgb is stricter
than C — undefined-behaviour programming errors — throws an unchecked exception rather than
silently doing whatever undefined C behaviour happened to do, since there is nothing byte-exact
to reproduce there.
