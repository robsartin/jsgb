---
status: Accepted
date: "2026-09-21"
topic: demo-run-body-golden-harness
tags: [project, demos, testing]
supersedes: []
related: [static-global-state, hand-rolled-demo-arguments, swappable-per-module-print-stream]
---
# 24. Demo programs are run bodies returning the C's value, behind a file-convention golden harness

## Context

Increment 4 ports the twelve SGB demonstration programs (`Jsgb.DEMOS` is the authority for which
twelve, and for their registered names). Each demo is a C `main` that parses `argv`, reads and
writes stdio and files relative to the current directory, and returns an `int` the shell turns
into an exit status. Reproducing a demo byte for byte needs a way to run it many times in one JVM
process without one run's global state or file I/O leaking into the next, and a way to check every
captured case (stdout, stderr, exit status, written files) without hand-writing one test per case.

ADR 0019 already commits demo argument parsing and printed messages to matching the C exactly.
ADR 0014 gives every C global a static Java home and a save/reset discipline; ADR 0022 gives
`Dijkstra` and `Gates` a swappable `out` field so a test can capture their printed output. Demos
extend both patterns rather than inventing a third: each demo reads and writes the same statics
the library already declared homes for, and points the library's `out` fields at its own stream
for the duration of a run.

A separate correction to the Context: ADR 0019 and the design spec's demo table both describe
three programs as interactive (`football`, `girth`, `multiply`). Working through all twelve found
five that read stdin: those three plus `ladders` (prompts for a starting and a goal word) and
`take_risc` (prompts for the numbers it multiplies, divides). The design spec is amended (dated,
in its own Amendments section) to say the same; this ADR does not restate the count elsewhere it
could drift from that correction.

## Decision

1. Each demo exposes `public static int run(String[] args, CStdin in, PrintStream out, PrintStream
   err, Path workDir)`, returning exactly the C `main`'s return value, negative values included.
   `main` masks that value to a shell exit status (`c & 0xff`) exactly once, in the `Jsgb` launcher
   (`Jsgb.exitStatus`), never inside a demo. A demo's own tests and the golden harness both assert
   against the C's literal return value (`-2`, `-9`, ...) before masking, matching what a reader of
   the C source sees.

2. `CStdin` is the demo layer's C-stdio seam: `fgets(size)` and `getchar()` share one underlying
   `InputStream` read as a single ISO-8859-1 byte stream. `fgets` stops at `size - 1` bytes or a
   trailing `'\n'` (kept), returns `null` only when EOF is hit before any byte is read, and returns
   a partial final line once before the next call returns `null` — reproducing `fgets`' size limit
   and the property that `getchar` afterward sees whatever bytes `fgets` left unconsumed.

3. Process-fresh globals: the C starts every program in a fresh process, so `run` resets what it
   or the library reads on its behalf (`Gb.verbose`, `Dijkstra.queue`, and any other static a demo
   touches) at its own top, and points a library module's swappable stream (ADR 0022) at its own
   `out` for the duration of the call, restoring the saved stream in a `finally` — the same pattern
   ADR 0014 already established for statics, now also covering the redirect. The golden harness
   goes one step further than a single `run` needs to: after each captured case it restores those
   same defaults itself, because the harness — unlike a real process — runs case after case in one
   JVM, and a case that leaves a global dirty must not depend on some later case happening to reset
   it. `Mems` is a `run`-scoped static counter, incremented exactly where the C evaluates an `o`,
   `oo`, `ooo`, or `oooo` comma operand; several verbose cases print the running count, so the
   placement is itself oracle-checked, not just an internal bookkeeping detail.

4. The golden harness is a data-driven JUnit `@TestFactory` over
   `demos/src/test/resources/oracle/demos/<demo>/<case>.{args,in,out,err,exit,file.*,seed.*}`: for
   every subdirectory whose name is a key of `Jsgb.DEMOS`, for every `<case>.exit` found, one
   dynamic test runs that demo with the case's arguments and stdin and asserts stdout, stderr, the
   masked exit status, and every written file match the captured bytes exactly.
   `scripts/oracle/capture-demos.sh` is the single record of every case's exact command line and
   stdin — this ADR does not duplicate that list — and a harness test asserts every oracle
   subdirectory names a demo actually registered in `Jsgb.DEMOS`, so a captured-but-unregistered
   demo fails loudly instead of silently never running.

## Alternatives considered

- **`run` returning the already-masked exit status** — rejected: it moves the C-to-shell masking
  decision into every demo instead of the one launcher that owns it, and it loses the ability to
  assert a demo's own tests against the C's literal negative return value, which is what a reader
  comparing against the C source actually sees.
- **`BufferedReader`/`Scanner` over `System.in`, or `System.setIn`, for demo stdin** — rejected on
  the same grounds ADR 0022 rejected `System.setOut` for library output: line-oriented readers
  cannot reproduce `fgets`' fixed-size buffer or the leftover-byte handoff to `getchar`, are not
  guaranteed byte-exact for the ISO-8859-1 charset the oracle is captured in, and `System.setIn` is
  JVM-global, so a parallel test run or the harness's own case-after-case reuse could interleave or
  clobber another case's stdin.
- **A per-run `Demo` instance owning its own state** instead of static globals — rejected: it
  contradicts ADR 0014's decision that SGB's globals get static Java homes, not instance state;
  the C's globals really are process-wide, and a demo's `run` is already the C's `main` body, not
  a constructor.
- **A subprocess per case** (build `demos:installDist` once, then `ProcessBuilder` each captured
  case against the installed launcher script) — rejected: slow relative to an in-process JUnit
  factory across dozens of cases, gives JaCoCo no coverage of demo code since it never runs inside
  the test JVM, and makes `argv`/stdin encoding and process exit-status extraction
  platform-dependent in ways the in-process seam avoids entirely.
- **A JSON manifest per case** instead of the file-extension convention
  (`.args`/`.in`/`.out`/`.err`/`.exit`/`.file.*`/`.seed.*`) — rejected: it needs a parser in
  `capture-demos.sh` (a shell script) to write it and another to read it in the harness, duplicating
  exactly what the flat files already record for free; binary artifacts (a written `.gb` or `.eps`
  file) are cleanest kept as files on disk, not re-encoded into a JSON string field.
- **A parameter or per-instance `Mems` counter** — rejected: the C's `mems` is a single file-scope
  global incremented by every generator and algorithm a demo calls, not something scoped to one
  call; giving it instance scope would require threading it everywhere ADR 0014 already decided
  against threading global state.

## Consequences

- Every demo's `main` is a two-line adapter (`System.exit(Jsgb.exitStatus(run(...)))`); all
  interesting behavior, including the C's negative-return error paths, lives in `run` and is
  directly testable and directly comparable to the C source.
- A demo that forgets to reset a global it touches, or forgets to restore a library's `out` field,
  is only caught if some oracle case happens to depend on the default — this ADR's own motivating
  bug (the golden harness not restoring defaults after a case) shows that gap is real; the harness
  now closes it for every case, but a demo's own unit tests that call `run` directly, outside the
  harness, do not get that protection for free.
- Adding a thirteenth demo means: register it in `Jsgb.DEMOS`, give it a `run` following this
  contract, add its cases to `scripts/oracle/capture-demos.sh`, and nothing else — the harness
  picks it up by the registry-membership scan, and the coverage assertion (Decision 4) fails loudly
  if the registration step is skipped.
- `docs/superpowers/specs/2026-09-19-jsgb-design.md` carries a dated amendment correcting its
  demo-table's interactive-program count from three to five, alongside this ADR.
