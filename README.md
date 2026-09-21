# jsgb

A Java port of Donald Knuth's Stanford GraphBase (SGB) that reproduces the C
output byte for byte: the same seeds produce the same graphs, and the C
distribution's `sample.correct` and `test.correct` are the acceptance tests.

Design: `docs/superpowers/specs/2026-09-19-jsgb-design.md`. Decisions: `docs/adr/`.

## Build and test

Requires JDK 25 (Gradle toolchain) and a JDK able to launch Gradle 9.7.1.

    ./gradlew check

`check` runs Spotless (google-java-format), the JUnit suite, ArchUnit, and the
JaCoCo coverage gate (line ≥ 80%, branch ≥ 65%).

## Layout

`lib/` holds the library: one package per SGB module under
`com.robsartin.jsgb`. Kernel: `flip`, `io`, `graph`, `sort`. Generators:
`raman`, `basic`, `rand`, `words`, `roget`, `miles`, `plane`, `books`, `econ`,
`games`, `lisa`, `gates`. Algorithms: `dijk`, `save`. Data files ship
unmodified under `lib/src/main/resources/sgb/`; a `jsgb.data.dir` system
property names a directory to search before the classpath, playing the role
of the C `DATA_DIRECTORY`.

`demos/` holds `TestSample`, the port of `test_sample` (it prints the salient
characteristics of a handful of generated graphs so the output can be
compared, stanza by stanza, against the SGB distribution's own
`sample.correct`), the twelve ported SGB demonstration programs below, and
`Jsgb`, the `jsgb <demo> [arguments]` launcher that dispatches to them — the
full, current list of registered names is `Jsgb.DEMOS`. `lib`'s tests read
the same oracle fixtures through the extra test resource root declared in
`lib/build.gradle.kts`, so `demos/src/test/resources/oracle` stays the single
canonical copy.

## Running the demos

`./gradlew :demos:run --args="ladders -v"` runs one demo through Gradle,
passing everything after the demo name on as its own arguments. For repeated
use, `./gradlew :demos:installDist` builds a standalone launcher script at
`demos/build/install/jsgb/bin/jsgb`, so `demos/build/install/jsgb/bin/jsgb
miles_span -n50` runs the same way without going through Gradle each time.

Five of the twelve are interactive and read from stdin: `ladders` prompts
for a starting and a goal five-letter word and looks for a word ladder
between them; `girth` prompts for a branching factor and a graph-size
parameter and reports a Ramanujan graph's diameter and girth; `football`
prompts for two 1990 college-football teams and looks for a chain of game
results connecting them; `multiply` and `take_risc` prompt for the numbers
they multiply, divide or run through the microRISC circuit.

One sentence per program:

- `queen` builds the union of two chesspiece-move boards, saves it as
  `queen.gb`, and lists every vertex and its arcs.
- `word_components` finds the `words` graph's connected components with a
  size-weighted union-find.
- `roget_components` finds a Roget's-Thesaurus cross-reference graph's
  strongly connected components with Tarjan's algorithm.
- `ladders` searches for a shortest word ladder between two five-letter
  words with Dijkstra's algorithm.
- `book_components` finds a novel's character-encounter graph's biconnected
  components with the Hopcroft-Tarjan algorithm.
- `econ_order` searches for a locally feed-forward-minimising ordering of a
  flow graph's sectors.
- `miles_span` finds a highway-mileage graph's minimum spanning tree four
  different ways, reporting each algorithm's memory-reference count.
- `girth` reports a Ramanujan graph's diameter and girth, both by formula
  and by breadth-first search.
- `multiply` evaluates an arbitrary-precision binary multiplier circuit
  against numbers typed at it.
- `take_risc` runs Knuth's 16-bit microRISC gate graph as a multiply/divide
  calculator.
- `assign_lisa` finds a minimum-cost assignment on a Mona-Lisa grey-level
  matrix with the Hungarian algorithm, optionally as encapsulated
  PostScript.
- `football` searches for a chain of 1990 college-football results that
  "proves" one team better than another.
- `test_sample` (also reachable as `jsgb test_sample`) prints the sample
  sequence, all sixteen stanzas of `sample.correct`, and writes `test.gb`.

`./gradlew :demos:run --args="test_sample"` prints the sample sequence
above; `./gradlew :demos:run` with no `--args` prints the launcher's usage
(the full demo list) instead, since `Jsgb` is the module's Gradle `run`
entry point. The sample sequence is also exercised directly by `./gradlew
:demos:test`.

## Regenerating oracles

`scripts/regen-oracle.sh` rebuilds the C Stanford GraphBase and regenerates
the `demos/src/test/resources/oracle` fixtures (see that directory's
`MANIFEST.md` for provenance).

## Licence

MIT for the Java code. The Stanford GraphBase data files are redistributed
unmodified under their own terms; see `NOTICE`.
