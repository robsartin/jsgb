# jsgb: a bit-exact Java port of the Stanford GraphBase

Date: 2026-09-19
Status: approved in conversation, awaiting written review

## Goal

Port Knuth's Stanford GraphBase (SGB) to Java such that every generator,
algorithm, and demonstration program produces byte-identical output to the C
original for the same inputs and seeds. The reference is the SGB release of
2025-12-28 as mirrored at `~/code/sgb` (git commit
`88fac2f051f445d68521dbe6cb756a43e5d53e8a`, github.com/ascherer/sgb).

"Act alike" was decided as **bit-exact**: `sample.correct` and `test.correct`
from the C distribution are acceptance oracles, and each demo is checked
against captured output of the C binary.

## Non-goals

- Idiomatic Java API design. The API mirrors the C names, parameter order, and
  conventions on purpose.
- Thread safety. SGB is a single-threaded library with global state; so is jsgb.
- Porting the CWEB documentation layer as literate source. Exposition lives in
  Javadoc instead.
- Porting the `ANSI/`, `MSVC/`, `AMIGA/` change files or `sgb.spec` packaging.
- Reproducing the C `Area` allocator. Only its observable consequences for
  `save_graph` (arc block layout) are reproduced.

## Source of truth

The C sources are the specification for every function. Where this document
and the C disagree, the C wins and this document is corrected by a dated
amendment. Parameter types are taken from the `PROTOTYPES/*.ch` change files,
which give full ANSI prototypes for every public function.

## Layout

- Gradle, Kotlin DSL, JDK 25 toolchain, JUnit 6, ArchUnit, JaCoCo, Spotless
  with google-java-format.
- Base package and Gradle group: `com.robsartin.jsgb`.
- Two subprojects: `lib` (kernel, generators, algorithms, save/restore) and
  `demos` (the twelve demonstration programs plus `TestSample`).
- One Java package per SGB module, named after it without the `gb_` prefix:
  `graph`, `io`, `flip`, `sort`, `basic`, `books`, `econ`, `games`, `gates`,
  `lisa`, `miles`, `plane`, `raman`, `rand`, `roget`, `words`, `dijk`, `save`.
  Each has a `package-info.java` carrying that module's introduction in prose.
- Demos live under `com.robsartin.jsgb.demo`, one class per program, plus a
  `Jsgb` launcher that dispatches on the first argument.
- The eleven `.dat` files ship unmodified under `lib/src/main/resources/sgb/`
  with their header comments intact. A `jsgb.data.dir` system property names an
  external directory to try first, playing the role of the C `DATA_DIRECTORY`.
- `LICENSE` is MIT for the Java code. `NOTICE` carries the Stanford University
  copyright text from `README` and `boilerplate.w` verbatim, and the data
  directory README states the files are unmodified Stanford GraphBase files.
- ADRs in `docs/adr/`, scaffolded with adr-toolkit.

## Kernel semantics

### API shape

Static methods with the C names and parameter order, on a class named for the
module: `Words.words(n, wtVector, wtThreshold, seed)`, `Basic.board(...)`,
`Dijkstra.dijkstra(u, v, g, hh)`, `Save.saveGraph(g, file)`.

`Graph`, `Vertex`, and `Arc` are mutable final classes with public fields
mirroring the C structs: `v.arcs`, `v.name`, `a.tip`, `a.next`, `a.len`,
`g.vertices`, `g.n`, `g.m`, `g.id`, `g.utilTypes`. Every generator and demo
reads these directly; getters would add noise without adding safety.

All C `long` and `unsigned long` parameters and fields become Java `long`.
Where the C parameter was `unsigned long`, comparisons use
`Long.compareUnsigned` so that out-of-range negative arguments behave as in C.

### Util slots

Each `Vertex` has slots `u, v, w, x, y, z`; each `Arc` has `a, b`; each
`Graph` has `uu, vv, ww, xx, yy, zz`. A slot is a mutable `Util` holding a
`long I` and an `Object ref`, with typed accessors `V()`, `A()`, `G()`, `S()`
that cast, and setters for each. Type safety is by convention, as in C.

`Graph.utilTypes` is the 14-character string exactly as in C: positions 0 to 5
describe the Vertex slots, 6 to 7 the Arc slots, 8 to 13 the Graph slots, each
one of `I S V A G Z`. Default `"ZZZZZZZZZZZZZZ"`. Only `save` and `restore`
interpret it.

Each module declares its slot claims as named constants (for example
`Dijkstra.DIST` is slot `z`, `Words.WEIGHT` is slot `u`) and its Javadoc lists
them, matching the C `#define` macros one for one.

### Global state

SGB globals are kept as static state in the owning class, each with a
package-visible reset used by tests. JUnit runs single-threaded.

| C global | Java home |
|---|---|
| `verbose`, `panic_code`, `gb_trouble_code`, `extra_n` | `Gb` in `graph` |
| `cur_graph` and the private arc/string allocation cursors | `Gb` in `graph` |
| RNG array and cursor | `Flip` |
| open data file, `io_errors`, `str_buf` | `GbIo` |
| `gb_sorted[]` | `LinkSort` |
| `init_queue`, `enqueue`, `requeue`, `del_min` | `Dijkstra.queue` |
| `chapters`, `chap_name[]` | `Books` |
| `risc_state[]` | `Gates` |

Panics keep the C convention: the generator sets `Gb.panicCode` and returns
null. `TestSample` prints the code and the output must match `sample.correct`.

### Ordering without pointers

Three C behaviours depend on memory addresses. Each gets an explicit stand-in.

1. `gb_new_edge` places the two arcs of an edge according to `u < v`.
   `Vertex` carries a package-private `index` into its graph's vertex array,
   which is what address order meant in C because all vertices of a graph sit
   in one allocated array.
2. The mate of an arc was found by pointer arithmetic (`a+1` or `a-1`). `Arc`
   carries an explicit `mate` reference, set by `newEdge`.
3. `save_graph` numbers arcs by position within the graph's arc blocks,
   including unused slots, which is why `test.correct` lists 102 arcs for a
   graph with 10. `Graph` keeps its arcs in blocks of 102 (`arcs_per_block`)
   in allocation order, and `save` enumerates them in that order. The C sorts
   blocks by address, which for sequential allocation is allocation order;
   this is an assumption and is recorded as a risk below.

Strings are Java `String`s. `gb_save_string` becomes identity, since the save
format writes strings by value and never preserves string identity.

### Bit-exact primitives

- `Flip`: lagged-Fibonacci generator `a_n = (a_{n-55} - a_{n-24}) mod 2^31`
  with the C's 56-element array, sentinel, `flipCycle`, `initRand(seed)`, and
  `unifRand(m)` by rejection against 2^31. Tests assert the published
  checkpoints after `initRand(-314159)`: `A[42]=2147326568`,
  `A[8]=1073977445`, `A[29]=536517481`, and `unifRand` values from
  `test_flip`.
- `LinkSort.linksort(Sortable head)`: the six-pass radix-256 sort, passes 1 and
  2 keyed by `nextRand() >> 23` for tie-breaking, passes 3 to 6 by key bytes.
  Results in the static `sorted[256]`. `Sortable` exposes `key()`, `link()`,
  `setLink()`; each caller adapts its record type as the C did by struct
  overlay.
- Name hash: `h += (h ^ (h >> 1)) + 314159 * byte`, bucket `h % g.n`, prime
  516595003, computed over ISO-8859-1 bytes of the name.
- `GbIo`: reads data files as ISO-8859-1 bytes so the checksum
  `(sum of 2^l * code_l) mod 1073741741` sees the same values C does. Public
  API mirrors C: `rawOpen`, `open`, `close`, `rawClose`, `newline`, `eof`,
  `ch`, `backup`, `digit`, `number`, `string`, `imapChr`, `imapOrd`,
  `newChecksum`, and the `ioErrors` bitmask with the C bit values.

## Generators and algorithms

Each generator is one final class with static factory methods following the C
skeleton: clear the trouble code, seed the RNG, open the data file, create the
graph, set `id` and `utilTypes`, panic on failure. Module dependencies are the
C's: `plane` uses `miles`; `basic` transformers take any graph; nothing else
crosses module lines.

Function pointers become interfaces:

- Dijkstra's four queue hooks become `PriorityQueueStrategy` with
  `initQueue(long)`, `enqueue(Vertex, long)`, `requeue(Vertex, long)`,
  `delMin()`. The doubly-linked-list implementation is the default; the
  128-bucket implementation is public as the alternative.
- The Dijkstra heuristic `hh` is a `ToLongFunction<Vertex>`.
- `Words.findWord(String, Consumer<Vertex>)` and
  `Plane.delaunay(Graph, BiConsumer<Vertex, Vertex>)`.

`gates` keeps `gateEval`, `printGates`, `partialGates`, `runRisc`, and
`riscState` as in C.

## Demos

One class per program under `com.robsartin.jsgb.demo`, each with a `main`, and
a `Jsgb` launcher so that `jsgb ladders -v` and `jsgb miles_span -n50` work
from the Gradle `application` install script. Argument parsing is hand-rolled
to match the C exactly, since usage messages are observable output. The three
interactive programs (`football`, `girth`, `multiply`) read stdin as before.
Exit codes are the C values reduced modulo 256, which is what a shell saw.

`miles_span` and `assign_lisa` report memory-reference counts. A `Mems`
counter in `demos` provides `o()`, `oo()`, `ooo()` and the Java code calls
them at exactly the points the C macros appear.

## Error handling

Three channels, as in C:

1. Generator failures: `Gb.panicCode` set, null returned.
2. Data-file problems: `GbIo.ioErrors` bitmask set, `open` returns it.
3. Programming errors that C left undefined, such as `newArc` with no current
   graph: `IllegalStateException`. This is the one place jsgb is stricter than
   C.

## Testing

### Oracle tests

Reference outputs are copied verbatim into `demos/src/test/resources/oracle/`:

- `sample.correct` and `test.correct` from the distribution.
- One stdout capture per demo per case, produced by the C binaries built from
  the reference SGB commit. A manifest (`oracle/MANIFEST.md`) records for each
  capture the command line, any stdin, and the SGB commit hash.
- Generated files such as `queen.gb`.

`scripts/regen-oracle.sh` rebuilds the C distribution from `~/code/sgb` (needs
`brew install cweb`; builds with `-Wno-implicit-int -Wno-deprecated-non-prototype
-Wno-implicit-function-declaration`) and regenerates every capture, so the
oracle set is reproducible and the manifest is the single source for which
commit it came from.

Each generator gets a golden test that extracts its stanza from
`sample.correct` and compares the Java `printSample` output byte for byte, so
the suite grows one stanza at a time. `TestSample` runs the entire sequence at
the end of increment 3 and diffs the whole file. Each demo gets one golden test
per captured case.

### Unit tests

Pure TDD per module. The C kernel self-tests (`test_io`, `test_graph`,
`test_flip`) are translated to JUnit. Test names follow `should...When...` with
`@DisplayName`.

### Architecture tests

ArchUnit enforces: `graph`, `io`, `flip`, `sort` depend on no other jsgb
package; generator packages depend only on the kernel, except `plane` may
depend on `miles`; `dijk` and `save` depend only on the kernel; `demo` may
depend on anything; no cycles.

### Gates

Spotless check, tests, JaCoCo with line coverage above 80 percent and branch
coverage above 65 percent, on GitHub Actions with JDK 25.

## Decisions to record as ADRs

Each with the rejected alternative and why it lost:

1. Static global state over a context object (rejected: breaks the C API shape,
   no benefit at SGB scale).
2. Public mutable fields on `Graph`, `Vertex`, `Arc` (rejected: getters and
   setters, triple the noise at every call site).
3. Null plus `panicCode` over a `PanicException` (rejected: every C call site
   and `test_sample` branch on the code and print it; exceptions would need
   catching at each of them to reproduce output).
4. Hidden vertex `index` and explicit `Arc.mate` over any pointer-order
   emulation (rejected: `System.identityHashCode` ordering is not stable).
5. Arc blocks of 102 retained for save fidelity (rejected: flat arc list,
   which cannot reproduce `test.correct`).
6. Javadoc as the literate layer over CWEB-for-Java (rejected: tooling fights
   every edit).
7. Hand-rolled argument parsing in demos over picocli (rejected: usage messages
   are part of the oracle).

## Increments

Four increments, each its own plan from this design:

1. Scaffold and kernel: Gradle, CI, ADR baseline, LICENSE and NOTICE, data
   files, then `flip`, `io`, `graph`, `sort` with the translated self-tests.
2. Pure and random generators plus save: `raman`, `basic`, `rand`, `save`,
   gated by the `test.correct` diff and the first four `sample.correct`
   stanzas.
3. Data-driven generators and Dijkstra: `words`, `roget`, `miles`, `plane`,
   `books`, `econ`, `games`, `lisa`, `gates`, `dijk`, gated by the remaining
   stanzas and the full `TestSample` diff.
4. Demos, easiest first: `queen`, `ladders`, `word_components`,
   `roget_components`, `miles_span`, `book_components`, `econ_order`, `girth`,
   `multiply`, `take_risc`, `assign_lisa`, `football`.

## Risks

- `save_graph` block order. The C sorts memory blocks by address; jsgb uses
  allocation order. If `test.correct` was produced by an allocator that did not
  hand out increasing addresses, the arc numbering will differ. The
  `test.correct` diff in increment 2 detects this immediately.
- Signed versus unsigned arithmetic. Any place the C relies on `unsigned long`
  wraparound must be found by reading the C, not by testing alone. The stanza
  oracles catch the cases `test_sample` exercises; demo oracles catch more.
- `LinkSort` callers. The C overlays a `{key, link}` node on whatever record is
  being sorted. Each caller's overlay must be read to know which fields serve
  as key and link.
- Mem counts. `miles_span` and `assign_lisa` outputs include mem totals, so
  every `o`/`oo`/`ooo` in those two programs must be placed identically.

## Amendments

### 2026-09-19: `sort` depends on `flip`

The Architecture tests section says the kernel packages `graph`, `io`, `flip`,
`sort` depend on no other jsgb package. That is wrong for `sort`: `gb_linksort`
draws random numbers from `gb_flip` for its two tie-breaking passes. The rule is
now: `graph`, `io`, `flip` depend on no other jsgb package; `sort` depends only
on `flip`. Found while porting `gb_sort` in increment 1.
