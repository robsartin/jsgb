# Increment 4: the twelve demo programs and the jsgb launcher — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the twelve SGB demonstration programs as classes under `com.robsartin.jsgb.demo`, each reproducing its C counterpart's stdout, stderr, exit status and written files byte for byte on every captured case, behind a `jsgb <demo> [args]` launcher.

**Architecture:** One class per program with the C's `main` body as `run(args, in, out, err, workDir)` returning the C's return value; `main` calls `System.exit(run(...) & 0xff)`. Three small helpers make the C's I/O idioms exact: `CStdin` (C `fgets`/`getchar` over an `InputStream`), `Scan` (`sscanf` prefix-and-integer matching), `Mems` (the `o`/`oo`/`ooo` memory-reference counters). A data-driven golden test runs every captured case for every registered demo. Library modules that print (`dijk`, `gates`) have their `out` stream pointed at the demo's stream for the duration of `run` (ADR 0022).

**Tech Stack:** unchanged (Java 25, Gradle, JUnit 6, AssertJ, JaCoCo, Spotless).

**Spec:** `docs/superpowers/specs/2026-09-19-jsgb-design.md` ("Demos", "Testing" and the Amendments); ADR 0019 (hand-rolled argument parsing), ADR 0020 (mates by position), ADR 0021/0023 (aux vertices, index comparisons), ADR 0022 (per-module `PrintStream`). C sources: `~/code/sgb/<demo>.w`; tangled copies at `/private/tmp/claude-501/-Users-sartin/022e4f66-ed5a-4350-bddf-2203947541a8/scratchpad/sgb-build/<demo>.c` while that directory survives (`scripts/regen-oracle.sh` rebuilds it).

## Global Constraints

- Everything from the increment 1–3b plans' Global Constraints binds (base package `com.robsartin.jsgb`, JDK 25, Spotless google-java-format, JaCoCo line ≥ 0.80 / branch ≥ 0.65 per module, `should<Expected>When<Condition>` + `@DisplayName`, pure TDD with an observed red, Javadoc on every public member, stage-by-path commits with the trailer `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`, Gradle run blocking, YAGNI).
- Branch `10-increment-4` (issue #10). Do not push; the orchestrator pushes and opens the PR.
- **The C is the authority.** The oracle is `demos/src/test/resources/oracle/demos/<demo>/<case>.*`, captured from the C binaries by `scripts/oracle/capture-demos.sh` (read its header for the file conventions; read the script body for every case's exact command line and stdin). Every case must pass through the golden harness; when a plan expectation and the C disagree, the C wins — correct the test and report it with the C section.
- **Demo contract** (Task 1 defines it; every demo follows it):
  - `public static int run(String[] args, CStdin in, PrintStream out, PrintStream err, Path workDir)` — `args` excludes the program name; returns exactly what the C `main` returns (negative values included). `public static void main(String[] args)` = `System.exit(Jsgb.exitStatus(run(args, new CStdin(System.in), Jsgb.stdout(), Jsgb.stderr(), Path.of(""))))` where `exitStatus(c) = c & 0xff`.
  - Usage and error messages print the C's text with the bare demo name in place of `argv[0]` (the captures were made with `argv[0]` = the bare name).
  - Files the C reads or writes relative to the current directory (`queen.gb`, `lisa.eps`, `-gfoo`) resolve against `workDir`.
  - **Process-fresh globals:** the C starts each program in a fresh process. At the top of `run`, reset every global the program reads or the library reads on its behalf: `Gb.verbose = 0`, `Mems.mems = 0` where used, `Dijkstra.queue = new DList()` in `ladders`, and point `Dijkstra.out` / `Gates.out` at `out` for the duration of `run` (restore in `finally`).
  - `printf` formats become `String.format(Locale.ROOT, ...)`: `%ld` → `%d`, `%lu` → `%d` for values known nonnegative (else `Long.toUnsignedString`), `%+ld` → `%+d`, `% 4ld` → `% 4d`, `%8ld` → `%8d`, `%-2ld` → `%-2d`, `%02ld` → `%02d`, `%02lx`/`%04lx` → `%02x`/`%04x`. `putchar(c)` → `out.print((char) c)`. `%d` of a C `long` prints the low 32 bits: `(int) value`.
  - `sscanf(arg, "-n%lu", &n) == 1` → `Scan.scan(arg, "-n")` (null when it does not match); `%lu` and `%ld` are the same function (the stored bit pattern is what matters). `strcmp(arg, "-v") == 0` → `arg.equals("-v")`; `strncmp(arg, "-g", 2) == 0` → `arg.startsWith("-g")` with the rest as the value.
  - `fgets(buffer, N, stdin)` → `in.fgets(N)` (null at EOF; the C leaves `buffer` unchanged then, so keep the previous String); `getchar()` → `in.getchar()`.
  - Pointer idioms as in earlier increments: `a->tip > v` → `a.tip.index > v.index`; `a+1`/`a-1` → `a.mate`; a standalone `Vertex dummy` or a sentinel that is only compared/stored → `Gb.allocAuxVertices(1)[0]` (ADR 0021/0023, never a borrowed array slot); `g->vertices + k` → `g.vertices[k]`.
  - Mems: the C's `o,` / `oo,` / `ooo,` / `oooo,` comma operands count a memory reference exactly when C evaluates that operand. Put the `Mems.o()` call at the same point — inside the same short-circuit branch, loop test or loop body — and never hoist it across an `&&`/`||`/`?:`. `if (o, x < y)` → `Mems.o(); if (x < y)`; `while (j > 0 && (oo, u = heap_elt(j))->dist > d)` → `while (j > 0) { Mems.oo(); u = heapElt(j); if (u.z.I <= d) break; … }`.
- Signed/unsigned: demos declare some locals `unsigned long` (`n`, `r`, `m`…); comparisons on them use `Long.compareUnsigned` where a negative value is representable from the command line (e.g. `-n-5`), and `while (r--)` on an unsigned `r` is `while (r-- != 0)`.
- No ArchUnit rule for `demo` (it may depend on every library package); `lib` gains nothing this increment except `Gb.allocAuxArcs` (Task 5) and `Games`' arc mates (Task 8), each with its own red test.

## File structure

```
demos/build.gradle.kts                                   Task 1: mainClass = Jsgb, applicationName = "jsgb"
demos/src/main/java/com/robsartin/jsgb/demo/
  Demo.java            Task 1  functional interface: int run(String[] args, CStdin in, PrintStream out, PrintStream err, Path workDir)
  CStdin.java          Task 1  fgets(int size) / getchar() over an InputStream, ISO-8859-1
  Scan.java            Task 1  sscanf-style "prefix + integer" matcher
  Mems.java            Task 1  public static long mems; o(), oo(), ooo(), oooo()
  Jsgb.java            Task 1  launcher: DEMOS registry (LinkedHashMap, C names), main, exitStatus, stdout()/stderr()
  Queen.java           Task 1
  WordComponents.java  Task 2
  RogetComponents.java Task 2
  Ladders.java         Task 3
  BookComponents.java  Task 4
  EconOrder.java       Task 4
  MilesSpan.java       Task 5   (+ lib: Gb.allocAuxArcs)
  Girth.java           Task 6
  Multiply.java        Task 6
  TakeRisc.java        Task 6
  AssignLisa.java      Task 7
  Football.java        Task 8   (+ lib: Games sets arc mates)
demos/src/test/java/com/robsartin/jsgb/demo/
  DemoOracleTest.java  Task 1  @TestFactory over oracle/demos/<demo>/<case>.exit for every registered demo
  CStdinTest, ScanTest, JsgbTest (Task 1); per-demo unit tests for pure helpers (Tasks 2–8)
demos/src/test/resources/oracle/demos/<demo>/<case>.{args,in,out,err,exit,file.*,seed.*}   already on the branch
scripts/oracle/capture-demos.sh, scripts/regen-oracle.sh, oracle/MANIFEST.md                already on the branch
README.md              Task 8
```

Registered names and classes (the C names are the launcher's keys): `assign_lisa`, `book_components`, `econ_order`, `football`, `girth`, `ladders`, `miles_span`, `multiply`, `queen`, `roget_components`, `take_risc`, `word_components`, plus `test_sample` → `TestSample.run(out, workDir)` returning 0.

---

### Task 1: demo infrastructure, launcher, golden harness, `queen`

**Files:** create `Demo`, `CStdin`, `Scan`, `Mems`, `Jsgb`, `Queen`, tests `CStdinTest`, `ScanTest`, `JsgbTest`, `DemoOracleTest`; modify `demos/build.gradle.kts` (`mainClass = "com.robsartin.jsgb.demo.Jsgb"`, `applicationName = "jsgb"`), `TestSample.java` only if its `run` needs a tiny adapter (it should not; wrap it in the registry lambda).

**Interfaces:**
- `CStdin(InputStream in)`: `String fgets(int size)` — reads bytes until `size − 1` bytes have been read or a `'\n'` has been read (included) ; returns them as an ISO-8859-1 String; returns `null` when EOF is hit before any byte is read (exactly `fgets`: a partial last line without a newline is returned, the next call returns null). `int getchar()` — next byte or −1. Both share one underlying stream, so `getchar` after a short `fgets` sees the leftover bytes.
- `Scan.scan(String arg, String prefix)`: returns the integer that `sscanf(arg, prefix + "%ld", &x) == 1` would store, as a `Long`, or `null`. Matching: `arg` starts with `prefix`; then any run of C whitespace is skipped; then an optional `+`/`-`; then at least one decimal digit; the digit run is parsed (`Long.parseLong` of the run with the sign; a run that overflows `long` may throw — out of scope, note it in Javadoc); anything after the digits is ignored (`-n5x` scans as 5). Empty prefix is allowed (`sscanf(buffer, "%ld", &m)`).
- `Mems`: `public static long mems;` `public static void o()`, `oo()`, `ooo()`, `oooo()`.
- `Demo`: `@FunctionalInterface int run(String[] args, CStdin in, PrintStream out, PrintStream err, Path workDir)`.
- `Jsgb`: `public static final Map<String, Demo> DEMOS` (LinkedHashMap in the order listed above, filled by the tasks as demos land — Task 1 registers `queen` and `test_sample`); `static int exitStatus(int c) { return c & 0xff; }`; `static PrintStream stdout()`/`stderr()` (ISO-8859-1, autoflush); `public static void main(String[] args)`: no args or unknown name → `stderr` gets `Usage: jsgb <demo> [arguments]` then one line per registered name indented two spaces, exit status 1; otherwise `System.exit(exitStatus(DEMOS.get(args[0]).run(rest, new CStdin(System.in), stdout(), stderr(), Path.of(""))))`. Factor the dispatch into `static int dispatch(String[] args, CStdin in, PrintStream out, PrintStream err, Path workDir)` returning the status (1 for usage) so it is testable without `System.exit`.
- `DemoOracleTest`: `@TestFactory Stream<DynamicTest> shouldReproduceCOutputWhenDemoRunsCapturedCase()`: root = `Path.of(getClass().getResource("/oracle/demos").toURI())`; for each subdirectory whose name is in `Jsgb.DEMOS` (sorted), for each `*.exit` file (sorted) one dynamic test named `<demo>/<case>` that: creates a temp dir; copies every `<case>.seed.<name>` to `<name>` in it; reads `<case>.args` (lines; absent → none), `<case>.in` (bytes; absent → empty); runs the demo with ISO-8859-1 capture streams; asserts stdout equals `<case>.out` (bytes, compared as ISO-8859-1 strings so a diff is readable), stderr equals `<case>.err` or empty, `Jsgb.exitStatus(code)` equals the integer in `<case>.exit`, and for each `<case>.file.<name>` the bytes of `<name>` in the temp dir equal it; deletes the temp dir. A second plain test `shouldRunAtLeastOneCaseWhenHarnessScansOracle` asserts the factory yields ≥ 1 test (guards against a silently empty scan). Task 8 adds the assertion that every oracle subdirectory is registered.
- `Queen` (queen.w): `run` ignores `args`; `g = board(3,4,0,0,-1,0,0); gg = board(3,4,0,0,-2,0,0); ggg = gunion(g, gg, 0, 0); Save.saveGraph(ggg, workDir.resolve("queen.gb").toString());` then if `ggg == null` prints `Something went wrong (panic code %ld)!` else the listing exactly as section 2 (`Queen Moves on a 3x4 Board`, the id, `has %ld vertices and %ld arcs:`, each vertex name and `  -> %s, length %ld` per arc); returns 0.

- [ ] **Step 1: Failing tests.** `CStdinTest` (fgets stops at newline and keeps it; fgets stops at size−1 bytes and the next fgets/getchar continues from the leftover; null at EOF; partial last line returned then null; getchar −1 at EOF; ISO-8859-1 bytes ≥ 0x80 round-trip), `ScanTest` (`scan("-n50","-n")` = 50; `scan("-n","-n")` null; `scan("-n5x","-n")` = 5; `scan("-n-5","-n")` = −5; `scan("-x5","-n")` null; `scan("  42","")` = 42; `scan("abc","")` null), `JsgbTest` (unknown demo → usage text on err with every registered name, status 1; `exitStatus(-2)` = 254; `dispatch("queen")` returns 0 and writes `queen.gb` in `workDir`), `DemoOracleTest` as above. Run: compilation fails.
- [ ] **Step 2: Implement** all of the above; `queen/default` (with its `.file.queen.gb` artifact) passes through the harness.
- [ ] **Step 3: `spotlessApply`, `check`, commit** `Add demo infrastructure, jsgb launcher, golden harness and queen`.

---

### Task 2: `word_components`, `roget_components`

**Source:** `word_components.w` (104 lines tangled), `roget_components.w` (207).

Notes:
- `WordComponents`: `g = Words.words(0, null, 0, 0)`; slots `link` = `z.V`, `master` = `y.V`, `size` = `x.I`, `weight` = `u.I`; the union-find with the size-weighted merge and the `c` counter that prints `" with"` before the second partner and `","` afterwards (`(c==2?" with":",")` after `c++ > 0`); `while (a && a->tip > v)` → `a.tip.index > v.index`; the trailer listing of non-isolated words outside the giant component, 12 per line. Formats `%4ld: %5ld %s`, `[%ld]`, `; c=%ld,i=%ld,m=%ld`.
- `RogetComponents`: arguments `-n%lu -d%lu -p%lu -s%ld -gfoo`, else usage `Usage: roget_components [-nN][-dN][-pN][-sN][-gfoo]` on stderr, return −2. `g = filename != null ? Save.restoreGraph(workDir.resolve(filename).toString()) : Roget.roget(n, d, p, s)`; null → stderr `Sorry, can't create the graph! (error code %ld)`, return −1. `specs(v)` = `(filename != null ? v.index + 1 : v.u.I)` and `v.name` (`cat_no` = `u.I`). Tarjan's algorithm exactly as sections 10–17 with slots `rank` = `z.I`, `parent` = `y.V`, `untagged` = `x.A`, `link` = `w.V`, `min` = `v.V`, `arc_from` = `x.V` (the ref half of the slot `untagged` used as an arc; separate fields in Java, same phase separation as C), `infinity` = `g.n`; output lines `Strong component `%ld %s'`, ` also includes:`, ` %ld %s (from %ld %s; ..to %ld %s)`, `\nLinks between components:`, `%ld %s -> %ld %s (e.g., %ld %s -> %ld %s)`.

- [ ] **Step 1:** register both in `Jsgb.DEMOS`; the harness now fails on `word_components/default`, `roget_components/{default,small,restored,usage}` (RED: class missing → compile error; after stubs, output mismatch). Add one unit test each for a pure piece: `WordComponents` none needed beyond the golden case (say so); `RogetComponentsTest.shouldPrintUsageWhenArgumentUnknown` asserting the stderr text and −2 directly.
- [ ] **Step 2: Implement**, `check`, commit `Port word_components and roget_components`.

---

### Task 3: `ladders`

**Source:** `ladders.w` (331). Notes:
- Flags parsed from the LAST argument backwards (`while (--argc)`): `-v` → `Gb.verbose = 1`, `-a`, `-f`, `-h`, `-e`, `-n%lu` (`randm = 0`), `-r%lu` (`randm = 1`), `-s%ld`; else usage `Usage: ladders [-v][-a][-f][-h][-e][-nN][-rN][-sN]` → −2. Then `if (alph || randm) freq = 0; if (freq) heur = 0;`.
- `g = Words.words(n, randm ? new long[9] : null, 0, seed)`; null → stderr `Sorry, I couldn't build a dictionary (trouble code %ld)!` and return `Gb.panicCode` (the `quit_if` macro). Verbose banner lines (section 8). Section 9: alphabetic lengths `a.len = a_dist(a.a.I)` where `loc` = `a.I` is the position of the differing letter and `a_dist(k) = |p[k] − q[k]|` on the two names; or frequency lengths `a.len = freqCost(a.tip)` with `freqCost(v)`: `acc = v.u.I; k = 16; while (acc != 0) { k--; acc >>= 1; } return k < 0 ? 0 : k`. Section 10: `Dijkstra.queue = (alph || freq || heur) ? new Buckets128() : new DList()`.
- Main loop: `out.print('\n')`; `promptForFive("Starting", …)` ≠ 0 → break; `promptForFive("    Goal", …)` ≠ 0 → back to the Starting prompt (the C's `goto restart` skips the `putchar('\n')`). `promptForFive(s)`: prints `%s word: `, then reads with `in.getchar()` until `'\n'`: EOF → return −1 (the caller breaks on any nonzero); with `echo`, every character read is echoed to `out`; a non-lowercase character marks the word invalid (`q = p + 5`), otherwise up to five letters are stored; exactly five → return 0, none → return 1, else print `(Please type five lowercase letters and RETURN.)` and prompt again. Store the five letters as a String `start`/`goal`.
- Section 14–16: `gg = Gb.newGraph(0)` (null → `Gb.NO_ROOM + 5` via quit_if); `gg.vertices = g.vertices; gg.n = g.n;` the spare slot `gg.vertices[(int) gg.n]` gets `name = start`; `uu = Words.findWord(start, Ladders::plantNewEdge)`; null → `uu = gg.vertices[(int) gg.n++]`. Same for `goal` unless `start.equals(goal)` (then `vv = uu`). If both were new (`gg.n == g.n + 2`) and `hammDist(start, goal) == 1`: `gg.n--; plantNewEdge(uu); gg.n++`. `plantNewEdge(v)`: `u = gg.vertices[(int) gg.n]; Gb.newEdge(u, v, 1); if (alph) u.arcs.len = u.arcs.mate.len = alphDist(u.name, v.name); else if (freq) { u.arcs.len = freqCost(v); u.arcs.mate.len = 20; }` (the C's `(u->arcs-1)` is the mate: `u` has the higher index so its arc is the second of the pair). `Gb.troubleCode != 0` → `NO_ROOM + 6`.
- Section 21: `minDist = Dijkstra.dijkstra(uu, vv, gg, heur ? (alph ? alphHeur : hammHeur) : null)` with `alphHeur(v) = alphDist(v.name, goal)`, `hammHeur(v) = hammDist(v.name, goal)`; `< 0` → `Sorry, there's no ladder from %s to %s.` else `Dijkstra.printDijkstraResult(vv)`. `Dijkstra.out = out` for the whole `run`.
- Section 25 cleanup: `for (k = gg.n − 1; k >= g.n; k--) { uu = g.vertices[k]; for (a = uu.arcs; a != null; a = a.next) { vv = a.tip; vv.arcs = vv.arcs.next; } uu.arcs = null; }` then `Gb.recycle(gg)`.
- Cases: `plain`, `alpha_heur_verbose`, `freq_verbose`, `hamming_heur`, `random200`, `n50_verbose`, `echo_badword`, `eof_at_start`, `usage`.

- [ ] **Step 1:** register `ladders`; unit tests `LaddersTest`: `shouldCostFewerWhenWordIsCommon` (`freqCost` of weight 0 → 16, of 1 → 15, of 65535 → 0, of 1 << 20 → 0), `shouldMeasureLetterDistanceWhenWordsDiffer` (`alphDist("aaaaa","abcaa")` = 3, `hammDist` = 2), `shouldPromptAgainWhenWordIsNotFiveLowercaseLetters` (a `CStdin` over `"WORLD\nworld\n"` yields the retry message and then 0 with `goal == "world"`). RED: compile error, then the harness's nine cases.
- [ ] **Step 2: Implement**, `check`, commit `Port ladders`.

---

### Task 4: `book_components`, `econ_order`

**Source:** `book_components.w` (246), `econ_order.w` (218). Notes:
- `BookComponents`: args `-ttitle`, `-n%lu -x%lu -f%lu -l%lu -i%ld -o%ld -s%ld`, `-v` (verbose 1), `-V` (2), `-gfoo`; usage `Usage: book_components [-ttitle][-nN][-xN][-fN][-lN][-iN][-oN][-sN][-v][-gfoo]` → −2; `if (filename != null) verbose = 0`. Graph from `Save.restoreGraph(workDir.resolve(filename))` or `Books.book(t, n, x, f, l, i, o, s)`; null → stderr `Sorry, can't create the graph! (error code %ld)` → −1. `Biconnectivity analysis of %s\n\n`. `vertexName(v)`: `filename != null ? v.name : "" + GbIo.imapChr(v.u.I / 36) + GbIo.imapChr(v.u.I % 36)` (`short_code` = `u.I`). Verbose listing (`%s=%s` or `%s=%s, %s [weight %ld]` with `i * inCount(y.I) + o * outCount(x.I)` and `desc` = `z.S`). Sections 12–19: Hopcroft–Tarjan with `dummy = Gb.allocAuxVertices(1)[0]` (`dummy.rank = 0`), slots `rank` = `z.I`, `parent` = `y.V`, `untagged` = `x.A`, `link` = `w.V`, `min` = `v.V`, `artic_pt` static per run (reset to null); output lines exactly as section 19 (`Isolated vertex %s`, ` and %s (this ends a connected component of the graph)`, ` and articulation point %s`, `Bicomponent %s`, ` also includes:`, ` %s (from %s; ..to %s)`). Cases: `default`, `homer_v`, `jean_V`, `david_window`, `restored`, `usage`.
- `EconOrder`: args `-n%lu -r%lu -s%ld -t%ld -v -V -g` (exact `-g` = greedy); usage `Usage: econ_order [-nN][-rN][-sN][-tN][-g][-v][-V]` → −2. `g = Econ.econ(n, 2, 0, s)`; null → stderr `Sorry, can't create the matrix! (error code %ld)` → −1. Header lines; `mat[79][79]`, `del[79][79]` from `a.a.I` (`flow`) indexed by `v.index`, `a.tip.index`; `n = g.n`; the lower-bound sum; `Flip.initRand(t)`; `while (r-- != 0)` (unsigned) → section 8: random permutation via `Flip.unifRand(k + 1)` (the exact swap sequence of section 9), `score`, verbose>1 listing; the descent loop (sections 10–13: `best_d = greedy ? 0 : INF`, the two `d` accumulations, `%8ld after step %ld` when verbose else a `.` every 1000 steps, the rotation with its `verbose > 1` trace `Now move %s to the %s, past` and `    %s (%ld)`); the summary `\n%s is %ld, found after %ld step%s.` and the order listing when `verbose || score < best_score`. Cases: `default`, `small_v`, `greedy_V`, `usage`.

- [ ] **Step 1:** register both; unit tests: `BookComponentsTest.shouldEncodeShortCodeWhenNotRestored` (`vertexName` of `u.I = 37` → the two imap characters for 1 and 1), `EconOrderTest.shouldPrintUsageWhenArgumentUnknown`. RED then the ten harness cases.
- [ ] **Step 2: Implement**, `check`, commit `Port book_components and econ_order`.

---

### Task 5: `miles_span` (+ `Gb.allocAuxArcs`)

**Source:** `miles_span.w` (1083 lines tangled; the largest demo). Read the CWEB for the prose on each structure. Notes:
- `lib`: add `public static Arc[] allocAuxArcs(int count)` to `Gb` next to `allocAuxVertices` (the C's `gb_typed_alloc(g->n, Arc, g->aux_data)`: arcs registered on no graph). RED unit test in `GbTest`: the returned arcs have indices 0..count−1 and `g.arcBlocks()` is unchanged.
- Args `-n%lu -N%lu -W%lu -P%lu -d%lu -r%lu -s%ld -v -gfoo`; usage `Usage: miles_span [-nN][-dN][-rN][-sN][-NN][-WN][-PN][-v][-gfoo]` → −2; `if (file_name) r = 1`. Loop `while (r-- != 0)`: graph from `Save.restoreGraph` or `Miles.miles(n, n_weight, w_weight, p_weight, 0, d, s)`; `g == null || g.n <= 1` → stderr `Sorry, can't create the graph! (error code %ld)` → −1. Section 5: `The graph %s has %ld edges,`, `krusk`, `  and it isn't connected.` / `  and its minimum spanning tree has length %ld.`, the four `takes %ld mems` lines, the two `jar_pr` runs (binary heap, then Fibonacci heap after `newarc` allocation), `cher_tar_kar`; bug messages return −4/−5/−3; `Gb.recycle(g); s++`.
- `INFINITY` = `-1L` (the C's `(unsigned long) -1`); `report(u, v, l)` prints `  %ld miles between %s and %s [%ld mems]`. `Mems.mems` is the C's `mems`; every `o`/`oo`/`ooo`/`oooo` placed per the Global Constraints rule.
- Slots (from the C `#define`s): `from` = `a.V`, `klink` = `b.A`, `clink` = `z.V`, `comp` = `y.V`, `csize` = `x.I`, `dist` = `z.I`, `backlink` = `y.V`, `KNOWN` = `Gb.ONE` (`v->backlink > KNOWN` ⇔ `backlink != null && backlink != Gb.ONE`), `heap_elt(i)` = `gv[i].u.V()` (the vertex array doubles as the heap), `heap_index` = `v.I`, `newarc` = `u.A`, `parent` = `newarc.tip`, `child` = `newarc.a.V`, `lsib` = `v.V`, `rsib` = `w.V`, `rank_tag` = `x.I`, `qchild` = `a.A`, `qsib` = `b.A`, `qcount` = `a.I`, `pq` = `newarc`, `findex` = `csize`, `matx(j,k)` = `gv[j*lo_sqrt+k].z.I`, `matx_arc(j,k)` = `gv[j*lo_sqrt+k].v.A`, `INF` = 30000. The C overlays these on the same unions in different phases; Java's separate `I`/`ref` halves never conflict, but do not "clean up" a slot the C leaves stale.
- `edge_trick & (siz_t) a ? a − 1 : a + 1` (section 63) is the mate: `a.mate`.
- The four priority-queue variants are plain static methods with a static `Vertex[] gv`, `long hsize`, `Vertex fHeap`, `Vertex[] newRoots = new Vertex[46]`, plus `Arc[] aucket = new Arc[64], bucket = new Arc[64]`; `qtraverse(h, visit)` takes a `Consumer<Arc>`; `note_edge` uses the static `kk`. Unsigned `k`/`j` heap indices are small; use `long`/`int`.
- Cases: `default`, `verbose50`, `repeat`, `weights`, `usage`.

- [ ] **Step 1:** `GbTest` red for `allocAuxArcs`; register `miles_span`; `MilesSpanTest.shouldPrintUsageWhenArgumentUnknown`; RED then the harness cases (the mems counts in the verbose cases are the sharp check on `o` placement).
- [ ] **Step 2: Implement**, `check`, commit `Port miles_span` (two commits are fine: the kernel seam first).

---

### Task 6: `girth`, `multiply`, `take_risc`

**Source:** `girth.w` (211), `multiply.w` (291), `take_risc.w` (150). Notes:
- All three use the `prompt(s)` macro: print `s`, then `line = in.fgets(N)`; null → leave the loop (girth/take_risc: the outer `while(1)`; multiply: `prompt` inside `step1`/`step2` breaks the main loop). Buffer sizes: girth 15, take_risc 99, multiply 999. `sscanf(buffer, "%ld", &x) != 1` → `Scan.scan(buffer, "") == null`.
- `Girth`: the banner; per round `p`, `q`; `g = Raman.raman(p, q, 0, 0)`; null → the panic-code message table of section 5 (` Sorry, I couldn't make that graph (%s).` with `Gb.panicCode` against `VERY_BAD_SPECS`, `+1`, `BAD_SPECS + 5/6/1/7/3/2`, else `not enough memory`); else sections 10, 6, 8, 9, 7, 12 verbatim (`bipartite = n == (q+1)*q*(q−1)`; the girth/diameter bounds arithmetic in `long`; the BFS with `sentinel = Gb.allocAuxVertices(1)[0]`, `link` = `w.V`, `dist` = `v.I`, `back` = `u.V`, `girth = 999`, the `%8ld vertices at distance %ld%s` lines and `So the diameter is %ld, and the girth is %ld.`); `Gb.recycle(g)`. Cases `session`, `eof`.
- `Multiply`: args `m n [seed]` (`argc < 3 || argc > 4` or unparsable → usage `Usage: multiply m n [seed]` → −2); negatives made positive; `seed = −1` unless a third arg parses (negated if negative); `m, n < 2 → 2`; `> 999` → `Sorry, I'm set up only for precision less than 1000 bits.` → −1; `g = Gates.prod(m, n)`; null → the three-way message → −3. Seed ≥ 0: `g = Gates.partialGates(g, m, 0, seed, buf)` then section 9 converts the forced-bit string `buf` (high bit first) to decimal `y` by repeated doubling on a decimal digit string; `"0"` → `Please try another seed value; %d makes the answer zero!` (`(int) seed`) → −5; else `OK, I'm ready to multiply any %ld-bit number by %s.`; null → `Sorry, I couldn't process the graph (trouble code %ld)!` → −9. `(I'm simulating a logic circuit with %ld gates, depth %ld.)` with `depth(g)` (sections 13–15, `dp` = `u.I`, booleans skipped via `null`/`Gb.ONE`). The loop (sections 7, 8, 11, 12): `prompt("\nNumber, please? ")`; skip leading `'0'`s; a bare newline → `break` unless zeros were skipped (then the number is `"0"`); a non-digit before the newline → print `Excuse me... I'm looking for a nonnegative sequence of decimal digits.` (no newline) and prompt again; more than 301 digits → `Sorry, that's too big.`; `Another? ` likewise when `seed < 0`. `decimalToBinary(x, n)` (section 10: repeated halving of the decimal string, emitting `n` low-order bits; returns the bits and whether digits remained — the C tests `*z` afterwards): `(Sorry, %s has more than %ld bits.)` → `continue`. `Gates.gateEval(g, bits, outBits) < 0` → `??? An internal error occurred!` (no newline) → 666. Section 12 converts the output bits (`outBits`, high bit first as `print_gates` orders outputs) to decimal `z`; print `%sx%s=%s%s.` with `"\n "` when `x.length() + y.length() > 35`. Cases `plain`, `seeded`, `big`, `bad_digits`, `too_big`, `usage`, `precision`, `negative`.
- `TakeRisc`: `trace = args.length > 0 ? 8 : 0` (the C tests `argc > 1`); `g = Gates.risc(8)`; null → `Sorry, I couldn't generate the graph (trouble code %ld)!` → −1; `Welcome to the world of microRISC.`; the ROM `memry` (34 words, the table in section 6 — copy it from the C, it is the same table `GatesTest` already holds); the prompt dialogue of sections 4–5 with its `goto step0/step1/step2` structure (write it as a small state machine that reproduces the exact message order: `\nGimme a number: `, `Excuse me, I meant a positive number: `, `That number's too big; please try again: `, `OK, now gimme another: `); `memry[1] = m; memry[3] = n; memry[5] = 10; Gates.runRisc(g, memry, 34, trace)`; `p = riscState[4]; o = riscState[16] & 1`; `The product of %ld and %ld is %ld%s.`; then `memry[5] = 7`, run again, `q = riscState[4]; r = (riscState[2] + n) & 0x7fff`; `The quotient is %ld, and the remainder is %ld.`. `Gates.out = out` during `run`. Cases `plain`, `trace`, `errors`, `big_then_bad`.

- [ ] **Step 1:** register the three; unit tests `MultiplyTest.shouldConvertDecimalWhenBitsRequested` (`decimalToBinary("13", 4)` → `"1101"` with nothing left; `("13", 3)` → left over), `MultiplyTest.shouldConvertBitsWhenDecimalRequested` (section 12 on `"1101"` → `"13"`), `GirthTest.shouldReportBoundsWhenGraphIsBipartite` (the section 6/7 arithmetic for p=3, q=7: `dl = 5`, `gu = 10`, `gl = 8` — from the `session` capture), `TakeRiscTest.shouldPrintUsageLikeDialogueWhenNumberIsZero` (the `errors` case's first lines via a `CStdin`). RED then the fourteen harness cases.
- [ ] **Step 2: Implement**, `check`, commit `Port girth, multiply and take_risc`.

---

### Task 7: `assign_lisa`

**Source:** `assign_lisa.w` (453). Notes:
- Args `m= n= d= m0= m1= n0= n1= d0= d1=` (`Scan.scan(arg, "m=")` etc. — note `"m="` does not match `"m0=5"`), `-s` (the `smile` macro of section 5 of the CWEB: read it; it sets `m0, m1, n0, n1` to the 16×32 smile window and then `d1 = 100000`; the capture's id is `lisa(16,32,255,94,110,97,129,0,100000)`), `-e` (the `eyes` macro: 20×50 window, `d1 = 200000`; id `lisa(19,49,255,61,80,91,140,0,200000)`), `-c`, `-h`, `-v`, `-V`, `-p`, `-P`; usage `Usage: assign_lisa [param=value] [-s] [-c] [-h] [-v] [-p] [-P]` → −2.
- `mtx = Lisa.lisa(m, n, d, m0, m1, n0, n1, d0, d1)`; null → stderr `Sorry, can't create the matrix! (error code %ld)` → −1; `Assignment problem for %s%s` with `Lisa.lisaId` and `, complemented`; re-read `m, n, d` from `lisaId`; `if (m != n) heur = 0`; `-p` prints the matrix with `% 4ld` per entry (complemented when `-c`); `-P` writes `lisa.eps` in `workDir` (section 28/30/31: the header lines with `%ld` widths, the hex image rows — `float conv = (float) (255.0 / (float) d); long x = (long) (conv * (float) value); "%02x" of min(x, 255)`, a newline every 32 pixels and after a short last row — and at the end the `bx` box per assignment; an unopenable file → stderr `Sorry, I can't open the file `lisa.eps'!` and `PostScript = 0`).
- `Mems.mems = 0`; section 24/25: `if (m > n)` transpose into `tmtx` (`Temporarily transposing rows and columns...` when `verbose > 1`); arrays `col_mate[m]`, `row_mate[n]`, `parent_row[n]`, `unchosen_row[m]`, `row_dec[m]`, `col_inc[n]`, `slack[n]`, `slack_row[n]`; `compl == 0` → `aa(k,l) = d − aa(k,l)`; the heuristic (section 12) and the Hungarian algorithm of sections 16–23 with every `o`/`oo`/`ooo` exactly placed (the `mems` totals in `Solved in %ld mems%s.` and the verbose ` After %ld mems I've matched %ld rows.` lines are the check); verbose > 1 traces (` matching col %ld==row %ld`, `  node %ld: unmatched row %ld`, `  node %ld: row %ld==col %ld--row %ld`, ` Decreasing uncovered elements by %ld produces zero at [%ld,%ld]`, ` rematching col %ld==row %ld`, ` Breakthrough at node %ld of %ld!`); the three sanity checks to stderr returning −6/−66/−666; `-p` prints `The following entries produce an optimum assignment:` and ` [%ld,%ld]` (transposed-aware); final `Solved in %ld mems%s.` with ` with square-matrix heuristic`.
- `aa(k,l)` = `mtx[k*n+l]` with `long` arithmetic; `INF = 0x7fffffff`; `m`, `n` are unsigned but small.
- Cases: `small_p`, `rect_v`, `transposed_V_c`, `heur_v`, `window`, `eps` (with `.file.lisa.eps`), `smile`, `eyes_v`, `bad_window`, `usage`.

- [ ] **Step 1:** register; `AssignLisaTest.shouldFormatEpsPixelWhenConversionUsesFloat` (the exact `float` expression for `d = 255`, value 128 → `"80"`, and for `d = 100`, value 77 → the C's result computed the same way, asserting the string) and `shouldPrintUsageWhenArgumentUnknown`. RED then the ten harness cases.
- [ ] **Step 2: Implement**, `check`, commit `Port assign_lisa`.

---

### Task 8: `football` (+ `Games` arc mates), coverage assertion, README

**Source:** `football.w` (482). Notes:
- `lib` prerequisite: `Games` section 24 creates each game's two arcs with `gb_new_arc` and the C addresses them as `a` and `a+1`; `football` needs `(a+1)->del`. Set `a.mate = v.arcs; v.arcs.mate = a;` right after the two `newArc` calls (ADR 0020: mates by position). RED test in `GamesTest`: `shouldPairArcsAsMatesWhenGameRecorded` asserting `u.arcs.mate.tip == u` and `mate.mate == u.arcs` (fails today: `mate` is null).
- Args: `argc == 3 && argv[2].equals("-v")` → `Gb.verbose = 2` and treat as two args (the C's `verbose = argc = 2`); no arg → `width = 0`; one numeric arg → `width = |value|`; else usage `Usage: football [searchwidth]` → −2. `g = Games.games(0,…,0)`; null → stderr `Sorry, can't create the graph! (error code %ld)` → −1. Section 5: for arcs with `a.tip.index > v.index`: `a.a.I (del) = a.len − a.mate.len; a.mate.a.I = −a.a.I`.
- `promptForTeam(s)`: prints `%s team: `; `line = in.fgets(30)` (null → keep the previous buffer; the buffer starts as `""`, so EOF loops exactly as the C does — every capture ends with a blank line); `buffer.startsWith("\n")` → null; the name is the buffer up to its first `'\n'` (or all 29 characters); exact match on `v.name` → that vertex; else ` (Sorry, I don't know any team by that name.)` and ` (One team I do know is %s...)` with `g.vertices[(int) Flip.unifRand(g.n)].name`, and prompt again. Main loop: `out.print('\n')`; `start` null → break; `goal` null or `start == goal` (with ` (Um, please give me the names of two DISTINCT teams.)`) → back to the Starting prompt without the newline.
- `Node` class: `Arc game; long totLen; Node prev, next; long tag`; `newNode(x, d)`: `prev = x; totLen = (x != null ? x.totLen : 0) + d`. Slots: `del` = `a.I`, `blocked` = `u.I`, `valid` = `v.V`, `link` = `w.V`, `rank` = `z.I`, `parent` = `u.V`, `untagged` = `x.A`, `min` = `v.V`, `date` = `b.I`, `nickname` = `y.S`; `dummy = Gb.allocAuxVertices(1)[0]`; `MAX_N = 120`, `list = new Node[MAX_N]`, `size = new long[MAX_N]`.
- Width 0 (section 17/18): the greedy walk with the reachability marking; `best_arc`/`last_arc` start null (the C leaves them uninitialised; unreachable goal is undefined in C). Width > 0 (sections 19–35): the Tarjan pass (28–34) run once per step, the `h = u.parent.rank` bucket, the sorted insertion of section 22 (`(h > 0 && size[h] == width) || (h == 0 && size[0] > 0)` etc.), the bucket reversal of section 23, and the verbose trace of section 24: the C stores `(++mm << 8) + m` into the dead `next` pointer of the popped node — use the `tag` field instead (`curNode.tag = (++mm << 8) + m`) and print `[%lu,%lu]=[%lu,%lu]&%s (%+ld)` with `prev != null ? prev.tag & 0xff : 0` and `prev != null ? prev.tag >>> 8 : 0`.
- Section 15/16: reverse the chain, then per game the date line (`d <= 5 → Aug %02d (d+26)`, `<= 35 → Sep`, `<= 66 → Oct`, `<= 96 → Nov`, `<= 127 → Dec`, else `Jan 01`) followed by `: %s %s %ld, %s %s %ld` and ` (%+ld)`.
- Cases: `greedy`, `width5`, `width3_v`, `unknown_and_same`, `usage`.
- `DemoOracleTest.shouldRegisterEveryOracleDemoWhenAllPorted`: every subdirectory of `/oracle/demos` is a key of `Jsgb.DEMOS` (the stale-green guard for the harness's registration filter).
- README: replace the "Running the sample" section with "Running the demos" (`./gradlew :demos:run --args="ladders -v"`, `./gradlew :demos:installDist` then `demos/build/install/jsgb/bin/jsgb miles_span -n50`, the three interactive programs read stdin, `test_sample` is also a launcher entry), list the twelve programs in one sentence each, and keep the module table honest. `docs/superpowers/specs/...` needs no amendment unless the C forces a deviation (record one if so).

- [ ] **Step 1:** `GamesTest` red for mates; register `football`; `FootballTest.shouldFormatDateWhenDayGiven` (0 → `Aug 26`, 5 → `Aug 31`, 6 → `Sep 01`, 96 → `Nov 30`, 127 → `Dec 31`, 128 → `Jan 01`); the coverage assertion (RED until football is registered — observe it red before registering). Then the harness cases.
- [ ] **Step 2: Implement**, `check`, commit(s) `Set arc mates for game pairs`, `Port football; every demo oracle case is registered`, `Document the jsgb launcher`.

---

## Self-review notes

- Spec coverage: twelve demos + launcher + the `Mems` counter + hand-rolled argument parsing (ADR 0019) + one golden test per captured case; `test_sample` is reachable from the launcher. Sixty captured cases across `demos/`, all reached through `Jsgb.DEMOS` once Task 8's assertion holds.
- Names used across tasks: `Demo.run` signature, `CStdin.fgets/getchar`, `Scan.scan`, `Mems.o/oo/ooo/oooo/mems`, `Jsgb.DEMOS/dispatch/exitStatus`, `Gb.allocAuxArcs` (Task 5), `Games` mates (Task 8).
