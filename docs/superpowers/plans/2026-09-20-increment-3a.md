# Increment 3a: kernel decisions, words, roget, miles, plane, dijk — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Settle the two kernel decisions the increment-2 review flagged (the boolean vertex ONE and unregistered auxiliary vertices), then port `gb_words`, `gb_roget`, `gb_miles`, `gb_plane`, and `gb_dijk` bit-exactly, gated by `sample.correct` stanzas 8, 10, 12–15 and twenty-five C-generated oracle cases.

**Architecture:** As increments 1–2: one package per SGB module (`words`, `roget`, `miles`, `plane`, `dijk`) of static methods with the C names; util slots by convention; the C is the authority. New in this increment: modules that keep state after they return (`words`' hash table for `find_word`, `miles`' distance matrix for `miles_distance`, `dijk`'s queue hooks) keep it as static fields, as the C does. Output that the C sends to `stdout` (`dijkstra`'s verbose trace, `print_dijkstra_result`) goes through a swappable `PrintStream` so the oracle tests can capture it.

**Tech Stack:** unchanged (Java 25, Gradle 9.7.1, JUnit 6, AssertJ, ArchUnit, JaCoCo, Spotless).

**Spec:** `docs/superpowers/specs/2026-09-19-jsgb-design.md` with its Amendments. C sources: `~/code/sgb/gb_words.w`, `gb_roget.w`, `gb_miles.w`, `gb_plane.w`, `gb_dijk.w`, `test_sample.w`; tangled copies at `/private/tmp/claude-501/-Users-sartin/022e4f66-ed5a-4350-bddf-2203947541a8/scratchpad/sgb-build/` while that directory survives (`brew install cweb`, then `ctangle` in a scratch copy otherwise).

## Global Constraints

- Everything from the increment 1 and 2 plans' Global Constraints binds: base package `com.robsartin.jsgb`, JDK 25, Spotless before every commit, JaCoCo line ≥ 0.80 / branch ≥ 0.65 on `check`, test names `should<Expected>When<Condition>` with `@DisplayName`, pure TDD (observe the failure before implementing; planted controls when a test targets code that is already correct), stage by explicit path, commit trailer `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>` (repository convention; overrides any session default), Gradle BLOCKING in the foreground, Javadoc on every public member, `package-info.java` per package.
- Branch `6-increment-3a` (issue #6). Do not push; the orchestrator pushes and opens the PR.
- **The C is the authority.** Every oracle value came from the C library (`scripts/oracle/oracle_inc3a.c` linked against `libgb.a`, output in `demos/src/test/resources/oracle/inc3a/oracle_inc3a.out`). If the Java differs, the Java is wrong. Hand-derived unit-test values in this plan may be wrong; when the C disagrees, correct the test and report it with the C section.
- **Translation rules** from the increment 2 plan apply (pointer → index arithmetic, `a±1` → `a.mate`, `a->next == a+1` → `Gb.isFirstOfSelfLoop`, `panic(c)` → `Gb.panicCode = c; Gb.troubleCode = 0; return null` with `Gb.recycle` first where the C does, statics stay static, RNG draws in the C's order, `%lu` → `Long.toUnsignedString`, `%.*s` → `Gb.prefix` once Task 1 makes it public).
- Additional rules for this increment:
  1. **Data-file reading** goes through `GbIo` exactly as the C: `GbIo.open(name)` returning nonzero → `EARLY_DATA_FAULT`; `GbIo.close()` nonzero → `LATE_DATA_FAULT`; `gb_string(str_buf, c)` → `GbIo.string(c)` (Java strings; the C's `str_buf` scratch is not needed); `gb_char`, `gb_number`, `gb_newline`, `gb_eof` map one to one.
  2. **`gb_linksort` callers** adapt their node record to `Sortable` (`key()`, `link()`, `setLink()`) and read `LinkSort.sorted[j]` for `j = 127 .. 0`, exactly as the C reads `gb_sorted`.
  3. **Callbacks**: `find_word`'s `f` is a `java.util.function.Consumer<Vertex>`; `delaunay`'s `f` is a `java.util.function.BiConsumer<Vertex, Vertex>` (either argument may be null, meaning the point at infinity); `dijkstra`'s `hh` is a `java.util.function.ToLongFunction<Vertex>` (null means the C's `dummy` that returns 0).
  4. **Printed output** from library code (`gb_dijk` only in this increment) goes to a public static `PrintStream out` on the module class, initialised to `System.out` with ISO-8859-1, so tests can capture it.
  5. `unsigned long` parameters compared against small non-negative values are plain `long` comparisons; where the C compares a signed `long` against an unsigned parameter (for example `j > max_distance` in `gb_miles`), both sides are non-negative in every reachable case and a plain `long` comparison is exact.

## File structure

```
lib/src/main/java/com/robsartin/jsgb/graph/{Gb,Vertex}.java          Task 1: ONE sentinel, allocAuxVertices, prefix public
lib/src/main/java/com/robsartin/jsgb/save/Save.java                  Task 1: ONE in lookup/translate/fillField
demos/src/main/java/com/robsartin/jsgb/demo/TestSample.java          Task 1: ONE in prVert/prArc; Task 7: stanzas
demos/src/test/java/com/robsartin/jsgb/demo/Oracle.java              Task 1: inc3a(name), inc3aReturn(name)
docs/adr/0021-boolean-vertex-and-aux-vertices.md                     Task 1
lib/src/main/java/com/robsartin/jsgb/words/Words.java (+package-info) Task 2
lib/src/main/java/com/robsartin/jsgb/roget/Roget.java (+package-info) Task 3
lib/src/main/java/com/robsartin/jsgb/miles/Miles.java (+package-info) Task 4
lib/src/main/java/com/robsartin/jsgb/plane/Plane.java (+package-info) Task 5
lib/src/main/java/com/robsartin/jsgb/dijk/{Dijkstra,PriorityQueueHooks,DList,Buckets128}.java (+package-info) Task 6
lib/src/test/java/com/robsartin/jsgb/{graph,save,words,roget,miles,plane,dijk}/*Test.java
lib/src/test/java/com/robsartin/jsgb/ArchitectureTest.java           Task 7
demos/src/test/java/com/robsartin/jsgb/demo/OracleInc3aTest.java     Tasks 2–6 (create in Task 2)
demos/src/test/java/com/robsartin/jsgb/demo/TestSampleTest.java      stanza tests (Tasks 2–5), run prefix (Task 7)
demos/src/test/resources/oracle/inc3a/oracle_inc3a.out               already on the branch (C output)
demos/src/test/resources/oracle/MANIFEST.md                          Task 1: add the inc3a rows
scripts/oracle/oracle_inc3a.c                                        already on the branch
scripts/regen-oracle.sh                                              Task 1: also build/run oracle_inc3a
```

`oracle_inc3a.out` case names (each `==name` line; a `==name=value` header carries a return value): `words_top`, `words_equal`, `words_bad`, `find_word`, `roget_small`, `roget_full`, `roget_default`, `miles_span_default`, `miles_dist300`, `miles_weighted`, `miles_distance=520`, `miles_bad`, `plane_small`, `plane_inf`, `plane_prob`, `plane_bad`, `delaunay`, `plane_miles_small`, `plane_miles_prob`, `dijkstra_roget`, `dijkstra_roget_far`, `dijkstra_128`, `dijkstra_unreachable`, `dijkstra_heuristic`, `dijkstra_verbose_plain`. The `find_word` body and the `delaunay` body begin with custom lines printed by the harness before any `print_sample` output; the `dijkstra_*` bodies are the verbose trace (when `verbose` was set), a `return=<value>` line, and the `print_dijkstra_result` output. Read `scripts/oracle/oracle_inc3a.c` for the exact calls behind each case.

---

### Task 1: Kernel decisions — `Gb.ONE`, `Gb.allocAuxVertices`, `Gb.prefix` public, ADR 0021

**Files:**
- Modify: `lib/src/main/java/com/robsartin/jsgb/graph/Gb.java`, `lib/src/main/java/com/robsartin/jsgb/save/Save.java`, `demos/src/main/java/com/robsartin/jsgb/demo/TestSample.java`, `demos/src/test/java/com/robsartin/jsgb/demo/Oracle.java`, `demos/src/test/resources/oracle/MANIFEST.md`, `scripts/regen-oracle.sh`
- Modify tests: `lib/src/test/java/com/robsartin/jsgb/graph/GbTest.java`, `lib/src/test/java/com/robsartin/jsgb/save/SaveTest.java`, `demos/src/test/java/com/robsartin/jsgb/demo/TestSampleTest.java`
- Create: `docs/adr/0021-boolean-vertex-and-aux-vertices.md` (+ README index entry)

**Interfaces:**
- `public static final Vertex Gb.ONE`: the C's `(Vertex*) 1`, used by `gb_gates` as a boolean constant in `Arc.tip` and in `V` slots. Constructed as `new Vertex(-1)`, name `"ONE"`. `Gb.isBoolean(Vertex v)` = `v == Gb.ONE` (the C's `is_boolean(v)` is `(unsigned long) v <= 1`, i.e. NULL or 1; callers that need the NULL case test it themselves).
- `public static Vertex[] Gb.allocAuxVertices(int count)`: fresh vertices (indexes `0..count-1`) registered on no graph; the C's `gb_typed_alloc(count, Vertex, g->aux_data)`, which `save_graph` never numbers. `dijk`'s `head[128]` and `gates`' scratch vertices use it.
- `public static String Gb.prefix(String s, int max)` (was private).
- `Save`: `lookup` and `translate` treat a `V` field whose `ref == Gb.ONE` as the boolean (written `1`, never classified); `fillField` `'1'` sets `l.ref = Gb.ONE` (and `l.I = 0`), `'0'` sets `l.ref = null`. The old encoding (`ref == null && I == 1`) is removed everywhere.
- `TestSample.prVert`: `v == Gb.ONE` → `"ONE"`; `prUtil` for `V` delegates to `prVert(u.V(), ...)` with no special case.
- `Oracle.inc3a(String name)` and `Oracle.inc3aReturn(String name)`: same contract as `inc2`/`inc2Return` over `/oracle/inc3a/oracle_inc3a.out`. Refactor `Oracle` so both files load through one private loader keyed by path.

- [ ] **Step 1: Failing tests**

Append to `GbTest`:
```java
  @Test
  @DisplayName("ONE is a single sentinel vertex that no graph owns")
  void shouldExposeSentinelWhenOneUsed() {
    assertThat(Gb.ONE).isSameAs(Gb.ONE);
    assertThat(Gb.ONE.name).isEqualTo("ONE");
    assertThat(Gb.ONE.index).isEqualTo(-1);
    assertThat(Gb.isBoolean(Gb.ONE)).isTrue();
    assertThat(Gb.isBoolean(Gb.newGraph(1L).vertices[0])).isFalse();
  }

  @Test
  @DisplayName("allocAuxVertices returns indexed vertices registered on no graph")
  void shouldNotRegisterBlockWhenAuxVerticesAllocated() {
    Graph g = Gb.newGraph(1L);
    Vertex[] aux = Gb.allocAuxVertices(3);
    assertThat(aux).hasSize(3);
    assertThat(aux[2].index).isEqualTo(2);
    assertThat(aux[0].name).isSameAs(Gb.NULL_STRING);
    assertThat(g.extraVertexBlocks()).isEmpty();
  }

  @Test
  @DisplayName("prefix is the C's %.*s: truncate only when longer")
  void shouldTruncateOnlyWhenLongerWhenPrefixCalled() {
    assertThat(Gb.prefix("abcdef", 3)).isEqualTo("abc");
    assertThat(Gb.prefix("ab", 3)).isEqualTo("ab");
    assertThat(Gb.prefix("ab", -1)).isEmpty();
  }
```

Append to `SaveTest`:
```java
  @Test
  @DisplayName("a boolean tip and a boolean V slot save as 1 and restore as Gb.ONE")
  void shouldRoundTripOneWhenSlotsAreBoolean() throws Exception {
    Graph g = Gb.newGraph(2L);
    g.id = "bool";
    g.utilTypes = "VZZZZZZZZZZZZZ";
    g.vertices[0].name = "a";
    g.vertices[1].name = "b";
    Gb.newArc(g.vertices[0], Gb.ONE, 3L);
    g.vertices[1].u.V(Gb.ONE);
    Path out = dir.resolve("bool.gb");
    assertThat(Save.saveGraph(g, out.toString())).isZero();
    String text = read(out);
    assertThat(text).contains("\"a\",A0,0\n").contains("\"b\",0,1\n").contains("1,0,3\n");
    Graph r = Save.restoreGraph(out.toString());
    assertThat(r.vertices[0].arcs.tip).isSameAs(Gb.ONE);
    assertThat(r.vertices[1].u.V()).isSameAs(Gb.ONE);
    assertThat(r.vertices[1].u.I).isZero();
  }

  @Test
  @DisplayName("auxiliary vertices are never numbered by save_graph")
  void shouldIgnoreAuxVerticesWhenSaving() throws Exception {
    Graph g = Gb.newGraph(1L);
    g.id = "aux";
    g.utilTypes = "VZZZZZZZZZZZZZ";
    Vertex[] aux = Gb.allocAuxVertices(1);
    aux[0].name = "scratch";
    g.vertices[0].u.V(aux[0]);
    Path out = dir.resolve("aux.gb");
    assertThat(Save.saveGraph(g, out.toString())).isEqualTo(Save.ADDR_NOT_IN_DATA_AREA);
    assertThat(read(out)).contains(",5V,").doesNotContain("scratch");
  }
```
(In the first test the vertex record for `a` is name, arcs `A0`, then slot `u` typed `V` and empty → `0`; for `b` it is name, no arcs `0`, slot `u` = ONE → `1`. The arc record is tip `1`, next `0`, len `3`. `newGraph(1)` has `1 + extraN = 5` vertices, hence `5V`.)

Append to `TestSampleTest`:
```java
  @Test
  @DisplayName("print_sample prints ONE for a boolean arc tip and a boolean V slot")
  void shouldPrintOneWhenTipOrSlotIsBoolean() {
    Graph g = Gb.newGraph(1L);
    g.id = "one";
    g.utilTypes = "VZZZZZZZZZZZZZ";
    g.vertices[0].name = "a";
    g.vertices[0].u.V(Gb.ONE);
    Gb.newArc(g.vertices[0], Gb.ONE, 2L);
    assertThat(Oracle.capture(ps -> TestSample.printSample(g, 0, ps)))
        .isEqualTo("\n\"one\"\n1 vertices, 1 arcs, util_types VZZZZZZZZZZZZZ\nV0: \"a\"[ONE]\n   ->ONE, 2\n");
  }

  @Test
  @DisplayName("oracle_inc3a.out cases are addressable by name")
  void shouldExtractInc3aCasesWhenReadingOracleFile() {
    assertThat(Oracle.inc3a("words_top")).startsWith("\n\"words(50,0,1000,1)\"\n50 vertices, 26 arcs");
    assertThat(Oracle.inc3aReturn("miles_distance")).isEqualTo(520L);
    assertThat(Oracle.inc3a("find_word")).isEqualTo("words\n|NULL\n|graph\n");
    assertThat(Oracle.inc3a("dijkstra_unreachable")).isEqualTo("return=-1\nSorry, 0 is unreachable.\n");
  }
```
The existing `shouldPrintOneAndNullTipWhenPresent` test encodes ONE the old way (`u.I = 1`); change that line to `g.vertices[1].u.V(Gb.ONE)` and keep its expected string.

- [ ] **Step 2: Run to verify failure** — `./gradlew :lib:test --tests 'com.robsartin.jsgb.graph.GbTest'` fails to compile (`Gb.ONE`); the demos tests likewise (`Oracle.inc3a`).

- [ ] **Step 3: Implement**

`Gb.java`: `public static final Vertex ONE = new Vertex(-1);` with `ONE.name = "ONE"` set in a static block; `isBoolean`; `allocAuxVertices` (like `allocVertices` without registering); make `prefix` public with Javadoc ("the C's `%.*s`: the first `max` characters, or the whole string if shorter; a negative `max` yields the empty string, where C would print the whole string — unreachable in SGB").

`Save.java`: in `lookup(Object ref, long i, char t)` case `'V'`: `if (ref == Gb.ONE) return;` (drop the `ref == null && i == 1` test); in `translate` case `'V'`: `if (ref == Gb.ONE) { itemBuf = "1"; moveItem(); return; }`; in `fillField` case `'V'`: `'1'` → `l.ref = Gb.ONE; l.I = 0;`, `'0'` → `l.ref = null; l.I = 0;`. Update the class Javadoc's boolean paragraph.

`TestSample.java`: `prVert`: `if (v == Gb.ONE) { out.print("ONE"); return; }` right after the null check; `prUtil` case `'V'`: `out.print("["); prVert(u.V(), l, s, out); out.print("]");`.

`Oracle.java`: one private static `Map<String, String> load(String resource)` and `Map<String, Long> loadReturns(...)` used by `inc2`/`inc3a`.

`MANIFEST.md`: add rows for `inc3a/oracle_inc3a.out` (stdout of `scripts/oracle/oracle_inc3a.c`, which also prints custom lines for `find_word`, `delaunay` and the `dijkstra_*` cases, and toggles `verbose` for two of them). `scripts/regen-oracle.sh`: also compile and run `oracle_inc3a.c`, copying `oracle_inc3a.out` into `demos/src/test/resources/oracle/inc3a/`.

ADR 0021 `boolean-vertex-and-aux-vertices` (format of 0013–0020; `related: [ordering-without-pointers, mates-by-position-for-restored-graphs]`): Context — `gb_gates` stores the boolean constant as the pointer value 1 in `Arc.tip` and in `V` slots, and `partial_gates` allocates scratch vertices in `aux_data`, which `save_graph` does not number; increment 2 encoded ONE as a `V` slot with `I == 1`, which cannot live in `Arc.tip`. Decision — a single sentinel `Gb.ONE` (index −1, owned by no graph) for both uses, with `Gb.isBoolean`; `Gb.allocAuxVertices` for unregistered scratch vertices. Alternatives — keep the `I == 1` encoding and add a parallel `Arc.tipIsOne` flag (rejected: two representations of one value, and the printer and `save` would need both); a `boolean` field on `Vertex` (rejected: every vertex pays for a gates-only concept, and identity is what the C tests). Consequences — `Gb.ONE.index` is −1, so index arithmetic on it is a bug, as pointer arithmetic on the C's constant would be; `save` writes `1` and `restore` reads it back to the sentinel, so a restored gates graph compares equal by identity.

- [ ] **Step 4: Run, gate, commit**

Run: `./gradlew spotlessApply && ./gradlew clean check` — Expected: BUILD SUCCESSFUL (the `test.correct` diff and every increment-2 oracle still pass: none of them contains a boolean).

```bash
git add lib/src/main/java/com/robsartin/jsgb/graph lib/src/main/java/com/robsartin/jsgb/save lib/src/test/java/com/robsartin/jsgb/graph/GbTest.java lib/src/test/java/com/robsartin/jsgb/save/SaveTest.java demos/src docs/adr scripts/regen-oracle.sh
git commit -m "Add the ONE sentinel, auxiliary vertex allocation, and a public prefix helper

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: `Words` (gb_words) with `findWord`

**Files:**
- Create: `lib/src/main/java/com/robsartin/jsgb/words/Words.java`, `package-info.java`
- Create: `lib/src/test/java/com/robsartin/jsgb/words/WordsTest.java`
- Create: `demos/src/test/java/com/robsartin/jsgb/demo/OracleInc3aTest.java`; modify `TestSampleTest.java` (stanzas 13–15)

**Interfaces:**
- `public static Graph words(long n, long[] wtVector, long wtThreshold, long seed)`; `public static Vertex findWord(String q, Consumer<Vertex> f)`; slots `weight` = `u.I`, `loc` = `a.I` (which of the five letters differs); `utilTypes = "IZZZZZIZZZZZZZ"`; constants `HASH_PRIME = 6997`, `NODES_PER_BLOCK = 111` (the block size only matters to the C allocator; keep the constant for documentation but allocate nodes individually).
- The hash table `htab` is a static `Vertex[5][HASH_PRIME]` that outlives the call so `findWord` can use it; a second `words` call replaces it.

**Source:** `gb_words.w` sections 7–31 (tangled `gb_words.c`, 397 lines). Notes:
- `max_c = {15194, 3560, 4467, 460, 6976, 756, 362}`, `default_wt_vector = {100, 10, 4, 2, 2, 1, 1, 1, 1}`.
- `gb_init_rand(seed)` first. Then section 9: if `wtVector == null` use the default; else section 11 (a `double` accumulation: `flacc = max(|w0|, |w1|) + Σ max_c[i] * |w[i+2]|`, panic `VERY_BAD_SPECS` if `≥ 0x60000000`) then section 12 (the same in `long`, panic `BAD_SPECS` if `≥ 0x40000000`). `test_sample`'s first `words` call lands exactly on `0x40000000` and panics; the `wt_vector[1]++` call lands on `0x3fffffff` and proceeds. Use `Math.abs` on `long` for `iabs` and `(double)` conversions for `flabs` exactly as the C.
- Section 18–21: `GbIo.open("words.dat")`; loop until `eof`: read five `ch()` into `word`; then `switch (ch())`: `'*'` → `wt = wtVector[0]`, `'+'` → `wt = wtVector[1]`, `' '` or `'\n'` → `0`, else panic `SYNTAX_ERROR`; then `i = 0; do { if (i == 7) panic SYNTAX_ERROR + 1; c = number(10); if (c > max_c[i]) panic SYNTAX_ERROR + 2; wt += c * wtVector[i + 2]; i++; } while (ch() == ',');`. If `wt >= wtThreshold`: push a node `{key = wt + 0x40000000, link = stack, wd = word}` and `nn++`. `newline()`. After the loop, `close()` nonzero → `LATE_DATA_FAULT`.
- Section 22/27: `LinkSort.linksort(stack)`; `if (n == 0 || nn < n) n = nn`; `newGraph(n)`; id `"words(" + unsigned(n) + ",0," + wtThreshold + "," + seed + ")"` when the default vector was used, else `"words(" + unsigned(n) + ",{" + w0 + "," + ... + w8 + "}," + wtThreshold + "," + seed + ")"`; `utilTypes`; `htab = new Vertex[5][HASH_PRIME]`. Then if `n > 0`: walk `LinkSort.sorted[127..0]`, for each node: section 28 — the vertex name is the five characters, `weight = key - 0x40000000`, then section 29 inserts it into the five tables and creates edges to every earlier word that differs in exactly one position:
  ```
  raw = ((((c0 << 5) + c1) << 5) + c2) << 5) + c3) << 5) + c4      // c = (long) char, ASCII
  for k in 0..4:
    h = (raw - (c_k << (5 * (4 - k)))) % HASH_PRIME
    while (htab[k][h] != null) {
      r = htab[k][h].name
      if the four positions other than k match: newEdge(cur, htab[k][h], 1); cur.arcs.a.I = cur.arcs.mate.a.I = k
      h = (h == 0) ? HASH_PRIME - 1 : h - 1            // the C's hdown(k)
    }
    htab[k][h] = cur
  ```
  (`store_loc_of_diff(k)` writes `loc` on `cur_vertex->arcs` and `cur_vertex->arcs - 1`; since `cur` has the larger index, its arc is the second slot and `arcs - 1` is the mate.) Stop after `n` vertices (`if (--nn == 0) goto done`).
- `findWord(q, f)` (sections 30–31): `q` is five characters; compute `raw`; probe table 0 for an exact match (all five positions) and return it; otherwise, if `f != null`, probe all five tables for words differing in exactly position `k` and call `f` on each, in table order 0..4 and probe order; return null. A `q` shorter than five characters is a caller error; throw `IllegalArgumentException` (the C would read past the string).

- [ ] **Step 1: Failing tests**

`WordsTest.java`:
```java
package com.robsartin.jsgb.words;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WordsTest {

  @Test
  @DisplayName("words(5757,0,0,69) loads every word with the default weights")
  void shouldLoadAllWordsWhenNIsZero() {
    Graph g = Words.words(0L, null, 0L, 69L);
    assertThat(g).isNotNull();
    assertThat(g.id).isEqualTo("words(5757,0,0,69)");
    assertThat(g.n).isEqualTo(5757L);
    assertThat(g.m).isEqualTo(28270L);
    assertThat(g.utilTypes).isEqualTo("IZZZZZIZZZZZZZ");
    Vertex v = g.vertices[5555];
    assertThat(v.name).isEqualTo("laded");
    assertThat(v.u.I).isZero();
    assertThat(v.arcs.tip.name).isEqualTo("lades");
    assertThat(v.arcs.a.I).isEqualTo(4L);
    assertThat(v.arcs.mate.a.I).isEqualTo(4L);
  }

  @Test
  @DisplayName("the weight vector at the exact 2^30 boundary panics with bad_specs, one less passes")
  void shouldPanicAtBoundaryWhenWeightsSumToTwoToThirty() {
    long[] wt = {100, -80589, 50000, 18935, -18935, 18935, 18935, 18935, 18935};
    assertThat(Words.words(100L, wt, 70000000L, 69L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS);
    wt[1]++;
    Graph g = Words.words(100L, wt, 70000000L, 69L);
    assertThat(g).isNotNull();
    assertThat(g.id).isEqualTo("words(90,{100,-80588,50000,18935,-18935,18935,18935,18935,18935},70000000,69)");
    assertThat(g.n).isEqualTo(90L);
  }

  @Test
  @DisplayName("find_word returns the vertex for a present word and calls back with neighbours of an absent one")
  void shouldFindOrEnumerateNeighboursWhenLookingUpWords() {
    Words.words(0L, null, 0L, 69L);
    assertThat(Words.findWord("words", null).name).isEqualTo("words");
    List<String> seen = new ArrayList<>();
    assertThat(Words.findWord("zzzzz", seen::add)).isNull();
    assertThat(seen).isEmpty();
    seen.clear();
    List<String> near = new ArrayList<>();
    assertThat(Words.findWord("graph", v -> near.add(v.name))).isNotNull(); // present: no callbacks
    assertThat(near).isEmpty();
  }

  @Test
  @DisplayName("every edge joins two words that differ in exactly the recorded position")
  void shouldRecordDifferingPositionWhenEdgesBuilt() {
    Graph g = Words.words(200L, null, 0L, 1L);
    for (int i = 0; i < g.n; i++) {
      for (Arc a = g.vertices[i].arcs; a != null; a = a.next) {
        String x = g.vertices[i].name;
        String y = a.tip.name;
        int diffs = 0;
        int where = -1;
        for (int k = 0; k < 5; k++) {
          if (x.charAt(k) != y.charAt(k)) {
            diffs++;
            where = k;
          }
        }
        assertThat(diffs).isEqualTo(1);
        assertThat(a.a.I).isEqualTo(where);
      }
    }
  }
}
```
The first test's values are stanza 15 of `sample.correct` (`V5555: "laded"[0] -> "lades"[0], 1[4] ...`). In the callback test, `seen::add` receives a `Vertex`, so write it as `v -> seen.add(v.name)`.

`OracleInc3aTest.java` (create):
```java
package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Vertex;
import com.robsartin.jsgb.words.Words;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Every case reproduces a run of the C library recorded in oracle_inc3a.out. */
class OracleInc3aTest {

  private static final long[] DST = {0x20000000L, 0x10000000L, 0x10000000L};

  @Test
  @DisplayName("words graphs print exactly as the C, including the bad_specs panic")
  void shouldMatchOracleWhenWordsPrinted() {
    long[] wt1 = {1, 1, 1, 1, 1, 1, 1, 1, 1};
    long[] bad = {100, -80589, 50000, 18935, -18935, 18935, 18935, 18935, 18935};
    assertThat(Oracle.capture(ps -> TestSample.printSample(Words.words(50L, null, 1000L, 1L), 3, ps))).isEqualTo(Oracle.inc3a("words_top"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Words.words(20L, wt1, 0L, 7L), 0, ps))).isEqualTo(Oracle.inc3a("words_equal"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Words.words(100L, bad, 70000000L, 69L), 5, ps))).isEqualTo(Oracle.inc3a("words_bad"));
  }

  @Test
  @DisplayName("find_word prints exactly what the C harness printed")
  void shouldMatchOracleWhenFindWordProbed() {
    Words.words(5757L, null, 0L, 69L);
    String out =
        Oracle.capture(
            ps -> {
              Consumer<Vertex> pr = v -> ps.print(v.name + " ");
              Vertex v = Words.findWord("words", null);
              ps.print((v == null ? "NULL" : v.name) + "\n");
              v = Words.findWord("zzzzz", pr);
              ps.print("|" + (v == null ? "NULL" : v.name) + "\n");
              v = Words.findWord("graph", pr);
              ps.print("|" + (v == null ? "NULL" : v.name) + "\n");
            });
    assertThat(out).isEqualTo(Oracle.inc3a("find_word"));
  }
}
```

Append to `TestSampleTest` (imports `Words`):
```java
  @Test
  @DisplayName("stanzas 13 to 15: the three words calls of test_sample")
  void shouldMatchSampleCorrectWhenWordsStanzasPrinted() {
    long[] wt = {100, -80589, 50000, 18935, -18935, 18935, 18935, 18935, 18935};
    assertThat(Oracle.capture(ps -> TestSample.printSample(Words.words(100L, wt, 70000000L, 69L), 5, ps))).isEqualTo(SampleCorrect.stanza(13));
    wt[1]++;
    assertThat(Oracle.capture(ps -> TestSample.printSample(Words.words(100L, wt, 70000000L, 69L), 5, ps))).isEqualTo(SampleCorrect.stanza(14));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Words.words(0L, null, 0L, 69L), 5555, ps))).isEqualTo(SampleCorrect.stanza(15));
  }
```

- [ ] **Step 2: Run to verify failure** — `./gradlew :lib:test --tests 'com.robsartin.jsgb.words.WordsTest'`: compilation FAILS (`Words` missing).

- [ ] **Step 3: Translate `gb_words.c` into `Words.java`**

Private static class `Node implements Sortable { long key; Sortable link; String wd; }`. Class Javadoc: the five-letter-word graph, weights from the seven frequency columns, edges between words one letter apart, the `loc` slot, and that `findWord` consults the table left by the most recent `words` call. `package-info.java` three sentences.

- [ ] **Step 4: Run to verify pass, gate, commit**

Run: `./gradlew spotlessApply && ./gradlew check`. Stanza 15 (all 5757 words, 28270 arcs, vertex 5555's ten neighbours in order) pins the hash probing and edge order; if it fails while the counts match, the `hdown` wraparound or the table order in section 29 is the suspect.

```bash
git add lib/src/main/java/com/robsartin/jsgb/words lib/src/test/java/com/robsartin/jsgb/words demos/src/test
git commit -m "Port gb_words and find_word

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: `Roget` (gb_roget)

**Files:**
- Create: `lib/src/main/java/com/robsartin/jsgb/roget/Roget.java`, `package-info.java`
- Create: `lib/src/test/java/com/robsartin/jsgb/roget/RogetTest.java`
- Modify: `demos/src/test/java/com/robsartin/jsgb/demo/OracleInc3aTest.java`, `TestSampleTest.java` (stanza 12)

**Interfaces:**
- `public static Graph roget(long n, long minDistance, long prob, long seed)`; slot `catNo` = `u.I`; `utilTypes = "IZZZZZZZZZZZZZ"`; constant `MAX_N = 1022`.

**Source:** `gb_roget.w` sections 4–14 (tangled `gb_roget.c`, 152 lines). Notes:
- `Flip.initRand(seed)`; `if (n == 0 || n > MAX_N) n = MAX_N`; `newGraph(n)`; id `"roget(" + unsigned(n) + "," + unsigned(minDistance) + "," + unsigned(prob) + "," + seed + ")"`.
- Section 8: `mapping` is a static `Vertex[MAX_N + 1]`, `cats` a static `long[MAX_N]`; `for k in 0..MAX_N-1: cats[k] = k + 1; mapping[k + 1] = null`; then for `v` from the last of the `n` vertices down to the first: `j = Flip.unifRand(k); mapping[cats[j]] = v; cats[j] = cats[--k]` (note `k` starts at `MAX_N` after the first loop).
- Section 10–14: `GbIo.open("roget.dat")`; `for (k = 1; !eof(); k++)`: if `mapping[k] != null`: `number(10) != k` → `SYNTAX_ERROR`; `name = string(':')`; `ch() != ':'` → `SYNTAX_ERROR + 1`; `v.name = name; v.u.I = k`; then section 13: `j = number(10); if (j == 0) done; loop { if (j > MAX_N) SYNTAX_ERROR + 2; if (mapping[j] != null && Math.abs(j - k) >= minDistance && (prob == 0 || (Flip.nextRand() >> 15) >= prob)) newArc(v, mapping[j], 1); switch (ch()) { case '\\': newline(); if (ch() != ' ') SYNTAX_ERROR + 3; /* falls through */ case ' ': j = number(10); break; case '\n': done; default: SYNTAX_ERROR + 4; } }` and `done: newline()`. Else (section 14): `s = string('\n'); if (s.endsWith("\\")) newline(); newline();`. After the loop `close()` nonzero → `LATE_DATA_FAULT`; `k != MAX_N + 1` → `IMPOSSIBLE`.
- The RNG is consulted only when `prob != 0`, once per candidate arc that passed the earlier tests, in file order.

- [ ] **Step 1: Failing tests**

`RogetTest.java`:
```java
package com.robsartin.jsgb.roget;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RogetTest {

  @Test
  @DisplayName("roget(1022,0,0,0) is the full thesaurus graph with 5075 cross-references")
  void shouldBuildFullGraphWhenDefaultsUsed() {
    Graph g = Roget.roget(0L, 0L, 0L, 0L);
    assertThat(g.id).isEqualTo("roget(1022,0,0,0)");
    assertThat(g.n).isEqualTo(1022L);
    assertThat(g.m).isEqualTo(5075L);
    assertThat(g.utilTypes).isEqualTo("IZZZZZZZZZZZZZ");
    assertThat(g.vertices[1000].name).isEqualTo("non-design");
    assertThat(g.vertices[1000].u.I).isEqualTo(636L);
    assertThat(g.vertices[1000].arcs.tip.name).isEqualTo("intention");
  }

  @Test
  @DisplayName("roget(1000,3,1009,1009) is stanza 12: thought at vertex 40 with five arcs")
  void shouldMatchStanzaWhenSampleParametersGiven() {
    Graph g = Roget.roget(1000L, 3L, 1009L, 1009L);
    assertThat(g.n).isEqualTo(1000L);
    assertThat(g.m).isEqualTo(3573L);
    assertThat(g.vertices[40].name).isEqualTo("thought");
    assertThat(g.vertices[40].u.I).isEqualTo(461L);
    assertThat(g.vertices[40].arcs.tip.name).isEqualTo("imagination");
  }
}
```

Append to `OracleInc3aTest` (import `Roget`):
```java
  @Test
  @DisplayName("roget graphs print exactly as the C")
  void shouldMatchOracleWhenRogetPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Roget.roget(100L, 2L, 500L, 3L), 10, ps))).isEqualTo(Oracle.inc3a("roget_small"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Roget.roget(1022L, 0L, 0L, 0L), 1000, ps))).isEqualTo(Oracle.inc3a("roget_full"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Roget.roget(0L, 0L, 0L, 5L), 0, ps))).isEqualTo(Oracle.inc3a("roget_default"));
  }
```

Append to `TestSampleTest` (import `Roget`):
```java
  @Test
  @DisplayName("stanza 12: roget(1000,3,1009,1009) at vertex 40")
  void shouldMatchSampleCorrectWhenRogetStanzaPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Roget.roget(1000L, 3L, 1009L, 1009L), 40, ps))).isEqualTo(SampleCorrect.stanza(12));
  }
```

- [ ] **Step 2: Run to verify failure** — `./gradlew :lib:test --tests 'com.robsartin.jsgb.roget.RogetTest'`: compilation FAILS.

- [ ] **Step 3: Translate `gb_roget.c` into `Roget.java`** with Javadoc (categories, cross-references, the random relabelling of categories to vertices, `minDistance` and `prob` filters) and `package-info.java`.

- [ ] **Step 4: Run to verify pass, gate, commit**

Run: `./gradlew spotlessApply && ./gradlew check`.

```bash
git add lib/src/main/java/com/robsartin/jsgb/roget lib/src/test/java/com/robsartin/jsgb/roget demos/src/test
git commit -m "Port gb_roget

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: `Miles` (gb_miles) with `milesDistance`

**Files:**
- Create: `lib/src/main/java/com/robsartin/jsgb/miles/Miles.java`, `package-info.java`
- Create: `lib/src/test/java/com/robsartin/jsgb/miles/MilesTest.java`
- Modify: `demos/src/test/java/com/robsartin/jsgb/demo/OracleInc3aTest.java`, `TestSampleTest.java` (stanza 8)

**Interfaces:**
- `public static Graph miles(long n, long northWeight, long westWeight, long popWeight, long maxDistance, long maxDegree, long seed)`; `public static long milesDistance(Vertex u, Vertex v)`; slots `people` = `w.I`, `xCoord` = `x.I`, `yCoord` = `y.I`, `indexNo` = `z.I`; `utilTypes = "ZZIIIIZZZZZZZZ"`; `MAX_N = 128`.
- Static state that outlives the call, as in C: the 128×128 `distance` matrix (`milesDistance` reads it) and the 128 node records.

**Source:** `gb_miles.w` sections 5–20 (tangled `gb_miles.c`, 257 lines). Notes:
- `Flip.initRand(seed)`; section 7: `if (n == 0 || n > MAX_N) n = MAX_N; if (maxDegree == 0 || maxDegree >= n) maxDegree = n - 1;` weight bounds (`|north|, |west| ≤ 100000`, `|pop| ≤ 100`) else `BAD_SPECS`. Section 8: `newGraph(n)`, id `"miles(" + unsigned(n) + "," + north + "," + west + "," + pop + "," + unsigned(maxDistance) + "," + unsigned(maxDegree) + "," + seed + ")"` (note the id prints the adjusted `maxDegree`: `miles(128,0,0,0,300,127,0)` in the oracle).
- Section 11–13: `GbIo.open("miles.dat")` (nonzero → `EARLY_DATA_FAULT`); for `k = 127` down to `0`: node `p = nodes[k]`, `p.kk = k`, `p.link = nodes[k - 1]` when `k > 0`; `p.name = string('[')`; `ch() != '['` → `SYNTAX_ERROR`; `lat = number(10)` in `[2672, 5042]` and `ch() == ','` else `SYNTAX_ERROR + 1`; `lon = number(10)` in `[7180, 12312]` and `ch() == ']'` else `+ 2`; `pop = number(10)` in `[2521, 875538]` else `+ 3`; `key = north * (lat - 2672) + west * (lon - 7180) + pop * (pop_ - 2521) + 0x40000000` (careful: `pop_weight * (p->pop - min_pop)`); section 13: `for j = k + 1 .. 127: if (ch() != ' ') newline(); d[j][k] = d[k][j] = number(10)`; then `newline()`. `close()` nonzero → `LATE_DATA_FAULT`.
- Section 14–15: `LinkSort.linksort(nodes[127])` (the list runs 127 → 0 through `link`); walk `sorted[127..0]`: the first `n` nodes become vertices in that order: `x = 12312 - lon`, `y = lat - 2672; y += y >> 1`, `indexNo = kk`, `people = pop`, `name`; the rest get `pop = 0`.
- Sections 17–19: `if (maxDistance > 0 || maxDegree > 0)`: `if (maxDegree == 0) maxDegree = MAX_N; if (maxDistance == 0) maxDistance = 30000;` for each node `p` with `pop != 0` (`k = p.kk`): build a list `s` of the other populated nodes `q` with `key = maxDistance - d[k][q.kk]`, negating `d[k][q.kk]` for those beyond `maxDistance`; `linksort(s)`; walk `sorted[0]` counting `j`, and for `j > maxDegree` negate `d[k][q.kk]`. Then edges: for `u` over the `n` vertices, `v` after `u`: `if (d[j][k] > 0 && d[k][j] > 0) newEdge(u, v, d[j][k])` with `j = u.indexNo`, `k = v.indexNo`.
- `milesDistance(u, v)` = `d[u.z.I][v.z.I]` (may be negative after `plane_miles`; the C reads freed memory there, jsgb reads the retained matrix).
- The node record implements `Sortable`; note it is sorted twice with different keys and links, exactly as the C reuses the same records.

- [ ] **Step 1: Failing tests**

`MilesTest.java`:
```java
package com.robsartin.jsgb.miles;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MilesTest {

  @Test
  @DisplayName("miles(50,-500,100,1,500,5,314159) is stanza 8 with Saint Louis at vertex 20")
  void shouldMatchStanzaWhenSampleParametersGiven() {
    Graph g = Miles.miles(50L, -500L, 100L, 1L, 500L, 5L, 314159L);
    assertThat(g.id).isEqualTo("miles(50,-500,100,1,500,5,314159)");
    assertThat(g.n).isEqualTo(50L);
    assertThat(g.m).isEqualTo(164L);
    assertThat(g.utilTypes).isEqualTo("ZZIIIIZZZZZZZZ");
    assertThat(g.vertices[20].name).isEqualTo("Saint Louis, MO");
    assertThat(g.vertices[20].w.I).isEqualTo(453085L);
    assertThat(g.vertices[20].x.I).isEqualTo(3293L);
    assertThat(g.vertices[20].y.I).isEqualTo(1785L);
    assertThat(g.vertices[20].z.I).isEqualTo(24L);
    assertThat(g.vertices[20].arcs.tip.name).isEqualTo("Tupelo, MS");
    assertThat(g.vertices[20].arcs.len).isEqualTo(364L);
  }

  @Test
  @DisplayName("miles_distance reads the retained matrix and the id prints the adjusted max_degree")
  void shouldReportDistanceWhenGraphBuilt() {
    Graph g = Miles.miles(10L, 0L, 0L, 0L, 0L, 0L, 4L);
    assertThat(g.id).isEqualTo("miles(10,0,0,0,0,9,4)");
    assertThat(Miles.milesDistance(g.vertices[0], g.vertices[1])).isEqualTo(520L);
    assertThat(g.m).isEqualTo(90L);
  }

  @Test
  @DisplayName("out-of-range weights panic with bad_specs")
  void shouldPanicWhenWeightsOutOfRange() {
    assertThat(Miles.miles(10L, 200000L, 0L, 0L, 0L, 0L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS);
    assertThat(Miles.miles(10L, 0L, 0L, 101L, 0L, 0L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS);
  }
}
```

Append to `OracleInc3aTest` (import `Miles`):
```java
  @Test
  @DisplayName("miles graphs print exactly as the C")
  void shouldMatchOracleWhenMilesPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Miles.miles(50L, 0L, 0L, 0L, 0L, 10L, 0L), 0, ps))).isEqualTo(Oracle.inc3a("miles_span_default"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Miles.miles(128L, 0L, 0L, 0L, 300L, 0L, 0L), 5, ps))).isEqualTo(Oracle.inc3a("miles_dist300"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Miles.miles(30L, 100L, 100L, 1L, 0L, 3L, 2L), 2, ps))).isEqualTo(Oracle.inc3a("miles_weighted"));
    com.robsartin.jsgb.graph.Graph g = Miles.miles(10L, 0L, 0L, 0L, 0L, 0L, 4L);
    assertThat(Miles.milesDistance(g.vertices[0], g.vertices[1])).isEqualTo(Oracle.inc3aReturn("miles_distance"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(g, 1, ps))).isEqualTo(Oracle.inc3a("miles_distance"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Miles.miles(10L, 200000L, 0L, 0L, 0L, 0L, 1L), 0, ps))).isEqualTo(Oracle.inc3a("miles_bad"));
  }
```

Append to `TestSampleTest` (import `Miles`):
```java
  @Test
  @DisplayName("stanza 8: miles(50,-500,100,1,500,5,314159) at vertex 20")
  void shouldMatchSampleCorrectWhenMilesStanzaPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Miles.miles(50L, -500L, 100L, 1L, 500L, 5L, 314159L), 20, ps))).isEqualTo(SampleCorrect.stanza(8));
  }
```

- [ ] **Step 2: Run to verify failure** — `./gradlew :lib:test --tests 'com.robsartin.jsgb.miles.MilesTest'`: compilation FAILS.

- [ ] **Step 3: Translate `gb_miles.c` into `Miles.java`** (private static `Node implements Sortable { long key; Sortable link; long kk, lat, lon, pop; String name; }`; static `Node[] nodes = new Node[MAX_N]` re-created per call; static `long[] distance = new long[MAX_N * MAX_N]` with `d(j, k)` = `distance[MAX_N * j + k]`), Javadoc, `package-info.java`.

- [ ] **Step 4: Run to verify pass, gate, commit**

Run: `./gradlew spotlessApply && ./gradlew check`. Stanza 8 pins the two-stage sort (the second sort's `sorted[0]` walk decides which of a city's neighbours survive `maxDegree`).

```bash
git add lib/src/main/java/com/robsartin/jsgb/miles lib/src/test/java/com/robsartin/jsgb/miles demos/src/test
git commit -m "Port gb_miles and miles_distance

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: `Plane` (gb_plane): `delaunay`, `plane`, `planeMiles`

**Files:**
- Create: `lib/src/main/java/com/robsartin/jsgb/plane/Plane.java`, `package-info.java`
- Create: `lib/src/test/java/com/robsartin/jsgb/plane/PlaneTest.java`
- Modify: `demos/src/test/java/com/robsartin/jsgb/demo/OracleInc3aTest.java`, `TestSampleTest.java` (stanza 10)

**Interfaces:**
- `public static Graph plane(long n, long xRange, long yRange, long extend, long prob, long seed)`; `public static Graph planeMiles(long n, long northWeight, long westWeight, long popWeight, long extend, long prob, long seed)`; `public static void delaunay(Graph g, BiConsumer<Vertex, Vertex> f)`; constant `INFTY = 0x10000000L`; slots `xCoord` = `x.I`, `yCoord` = `y.I`, `zCoord` = `z.I`; `utilTypes = "ZZZIIIZZZZZZZZ"` for `plane` (`planeMiles` keeps `miles`' `"ZZIIIIZZZZZZZZ"`). `plane` may depend on `miles` (`Miles.miles`, `Miles.milesDistance`) and `flip`, `graph`.

**Source:** `gb_plane.w` sections 4–44 (tangled `gb_plane.c`, 693 lines). This is the intricate one; translate it faithfully and keep the C's names.

Data structures for `delaunay` (sections 25–33):
- The C keeps `6n − 6` arc records in one array and finds an arc's *mate* by mirroring its address: `mate(a) = min_arc + (max_arc − a)`. In Java: a private static class `DArc { Vertex vert; DArc next; DNode inst; final int idx; }` in an array `pool` of length `6n − 6`, with `mate(a) = pool[(6n − 7) − a.idx]`. `next_arc` is an index `nextArc` that starts at 0 and advances by 3; the mirror side is consumed from the top.
- Tree nodes: `DNode { Vertex u; Vertex v; DArc arc; DNode l; DNode r; }`. The C stores an arc pointer in a terminal node's `v` field (`terminal_node(x, p)` sets `x->v = (Vertex*) p`); in Java a terminal node has `u == null` and its arc in `arc`; when the C later turns a terminal node into an internal one (`xp->u = t; xp->v = p; xp->l = ...; xp->r = ...`) set `u`, `v`, `l`, `r` and leave `arc` alone (it is never read again). `yp->v = (Vertex*) a` re-targets a terminal node's arc: `yp.arc = a`. `new_node` allocates from blocks of 127 in the C; in Java `new DNode()`.
- `root_node` is a local `DNode`.
- The search loop `while (x->u)` descends while `x.u != null`; then `a = x.arc`.
- Section 28 (the callback loop): `for (i = 0; i < nextArc; i++) f.accept(pool[i].vert, pool[(6n − 7) − i].vert)`.

Arithmetic helpers (sections 13–24), all private static, all `long`: `intSqrt(x)` (the bit-serial square root; translate literally, including `for (k = 25, m = 0x20000000; x < m; k--, m >>= 2)` and the `do { ... } while (k != 0)` body of section 14), `signTest(x1,x2,x3,y1,y2,y3)` (sections 16–19, with the `0x4000` split products `a`, `b`, `c` and the `goto ez` path), `ccw(u,v,w)` (section 20, tie-breaking on `z` then lexicographic `x`, `y`), `incircle(t,u,v,w)` (sections 21–23 with the twelve-way `ff/gg/hh/jj` cascade of section 23 — translate the cascade as a sequence of `if (dd < 0) ... else if (dd == 0) ...` steps or a helper that returns the first nonzero of the ordered list, preserving short-circuit order), `ff`, `gg`, `hh`, `jj` (section 24).

Edge callbacks:
- `newEuclidEdge(u, v)` (section 12): `if ((Flip.nextRand() >> 15) >= gprob)` then if both non-null `newEdge(u, v, intSqrt(dx*dx + dy*dy))`, else if `infVertex != null` an edge to/from `infVertex` with length `INFTY` (`newEdge(u, infVertex, INFTY)` when `v == null`, `newEdge(infVertex, v, INFTY)` when `u == null`). The RNG draw happens on every call, before the null tests.
- `newMileEdge(u, v)` (section 44): same shape with `-Miles.milesDistance(u, v)` as the length.
- `gprob` and `infVertex` are private statics set by `plane`/`planeMiles` before calling `delaunay`.

`plane` (sections 5–6, 11): `Flip.initRand(seed)`; `xRange > 16384 || yRange > 16384` → `BAD_SPECS`; `n < 2` → `VERY_BAD_SPECS`; zero ranges default to 16384; `if (extend != 0) Gb.extraN++`; `newGraph(n)`; id `"plane(" + unsigned(n) + "," + unsigned(xRange) + "," + unsigned(yRange) + "," + unsigned(extend) + "," + unsigned(prob) + "," + seed + ")"`; `utilTypes`; for `k < n`: `x = unifRand(xRange); y = unifRand(yRange); z = (nextRand() / n) * n + k` (integer division, in that order: three draws per vertex); name `Long.toString(k)`. If `extend`: `vertices[n].name = "INF"`, its `x`, `y`, `z` = −1, `Gb.extraN--`. Then `gprob = prob; infVertex = extend != 0 ? vertices[n] : null; delaunay(g, Plane::newEuclidEdge)`; trouble check; `if (extend != 0) g.n++`; return.

`planeMiles` (sections 41–43): `if (extend != 0) Gb.extraN++; if (n == 0 || n > 128) n = 128; g = Miles.miles(n, north, west, pop, 1L, 0L, seed); if (g == null) return null;` id `"plane_miles(" + unsigned(n) + "," + north + "," + west + "," + pop + "," + unsigned(extend) + "," + unsigned(prob) + "," + seed + ")"`; `if (extend != 0) Gb.extraN--`; `gprob = prob`; if `extend`: `infVertex = vertices[g.n]` with name `"INF"` and `x`, `y`, `z` = −1, else null; `delaunay(g, Plane::newMileEdge)`; trouble check; (`gb_free(aux_data)` has no Java equivalent); `if (extend != 0) g.n++`.

`delaunay` (sections 9, 26–39): `if (g.n < 2) return;` allocate the pool; `u = vertices[0]; v = vertices[1]`; section 33 builds the initial two triangles (three arcs at the bottom of the pool and their three mates at the top, `nextArc = 3`); then for each `p` from index 2: section 35 locates the enclosing triangle by descending the tree with `ccw`; section 36 splits it (section 37 creates three new arcs and mates, `nextArc += 3`), with the special case `q == null` (section 38: the point is outside the current hull, walk the hull while `t != r && ccw(p, s, t)` flipping); then section 39 restores the Delaunay condition with `incircle` and `flip` (section 40) until `tp == r`. Keep every statement in the C's order; the only things that change are `mate()` (index mirror), `new_node` (constructor), and the terminal-node `v`/`arc` distinction.

- [ ] **Step 1: Failing tests**

`PlaneTest.java`:
```java
package com.robsartin.jsgb.plane;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlaneTest {

  @Test
  @DisplayName("plane(10,0,0,0,0,1) is a 10-point Delaunay triangulation with the C's coordinates")
  void shouldTriangulateWhenTenRandomPoints() {
    Graph g = Plane.plane(10L, 0L, 0L, 0L, 0L, 1L);
    assertThat(g.id).isEqualTo("plane(10,16384,16384,0,0,1)");
    assertThat(g.n).isEqualTo(10L);
    assertThat(g.m).isEqualTo(46L);
    assertThat(g.utilTypes).isEqualTo("ZZZIIIZZZZZZZZ");
    assertThat(g.vertices[0].x.I).isEqualTo(1389L);
    assertThat(g.vertices[0].y.I).isEqualTo(14015L);
    assertThat(g.vertices[0].z.I).isEqualTo(63752060L);
    assertThat(g.vertices[0].arcs.tip.name).isEqualTo("5");
    assertThat(g.vertices[0].arcs.len).isEqualTo(13468069L);
  }

  @Test
  @DisplayName("extend adds the vertex INF joined to the hull by INFTY edges and counts it in n")
  void shouldAddInfinityVertexWhenExtendSet() {
    Graph g = Plane.plane(20L, 100L, 100L, 1L, 0L, 5L);
    assertThat(g.n).isEqualTo(21L);
    assertThat(g.vertices[20].name).isEqualTo("INF");
    assertThat(g.vertices[20].x.I).isEqualTo(-1L);
    assertThat(g.vertices[20].arcs.len).isEqualTo(Plane.INFTY);
    assertThat(Gb.extraN).isEqualTo(4L);
  }

  @Test
  @DisplayName("bad ranges and fewer than two points panic")
  void shouldPanicWhenSpecsAreBad() {
    assertThat(Plane.plane(1L, 0L, 0L, 0L, 0L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.VERY_BAD_SPECS);
    assertThat(Plane.plane(5L, 20000L, 0L, 0L, 0L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS);
  }

  @Test
  @DisplayName("delaunay reports every arc pair of the triangulation to the callback, INF as null")
  void shouldReportPairsWhenDelaunayCalledDirectly() {
    Graph g = Plane.plane(6L, 100L, 100L, 0L, 0L, 3L);
    List<String> pairs = new ArrayList<>();
    Plane.delaunay(g, (u, v) -> pairs.add((u == null ? "INF" : u.name) + "-" + (v == null ? "INF" : v.name)));
    assertThat(pairs).hasSize(15);
    assertThat(pairs.get(0)).isEqualTo("1-0");
    assertThat(pairs).contains("INF-1", "0-INF");
  }

  @Test
  @DisplayName("plane_miles keeps miles' slots and reads distances from the retained matrix")
  void shouldBuildFromMileageWhenPlaneMilesCalled() {
    Graph g = Plane.planeMiles(20L, 0L, 0L, 0L, 0L, 0L, 1L);
    assertThat(g.id).isEqualTo("plane_miles(20,0,0,0,0,0,1)");
    assertThat(g.n).isEqualTo(20L);
    assertThat(g.m).isEqualTo(104L);
    assertThat(g.utilTypes).isEqualTo("ZZIIIIZZZZZZZZ");
    assertThat(g.vertices[0].name).isEqualTo("Wilmington, NC");
    assertThat(g.vertices[0].arcs.tip.name).isEqualTo("Savannah, GA");
    assertThat(g.vertices[0].arcs.len).isEqualTo(277L);
  }
}
```
The values are from the oracle cases `plane_small`, `plane_inf`, `delaunay`, `plane_miles_small`.

Append to `OracleInc3aTest` (import `Plane`):
```java
  @Test
  @DisplayName("plane graphs and delaunay print exactly as the C")
  void shouldMatchOracleWhenPlaneGraphsPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Plane.plane(10L, 0L, 0L, 0L, 0L, 1L), 0, ps))).isEqualTo(Oracle.inc3a("plane_small"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Plane.plane(20L, 100L, 100L, 1L, 0L, 5L), 20, ps))).isEqualTo(Oracle.inc3a("plane_inf"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Plane.plane(30L, 500L, 500L, 1L, 300L, 7L), 3, ps))).isEqualTo(Oracle.inc3a("plane_prob"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Plane.plane(1L, 0L, 0L, 0L, 0L, 1L), 0, ps))).isEqualTo(Oracle.inc3a("plane_bad"));
    com.robsartin.jsgb.graph.Graph g = Plane.plane(6L, 100L, 100L, 0L, 0L, 3L);
    String out =
        Oracle.capture(
            ps -> {
              Plane.delaunay(g, (u, v) -> ps.print((u == null ? "INF" : u.name) + "-" + (v == null ? "INF" : v.name) + "\n"));
              TestSample.printSample(g, 2, ps);
            });
    assertThat(out).isEqualTo(Oracle.inc3a("delaunay"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Plane.planeMiles(20L, 0L, 0L, 0L, 0L, 0L, 1L), 0, ps))).isEqualTo(Oracle.inc3a("plane_miles_small"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Plane.planeMiles(40L, 0L, 0L, 0L, 1L, 20000L, 9L), 40, ps))).isEqualTo(Oracle.inc3a("plane_miles_prob"));
  }
```

Append to `TestSampleTest` (import `Plane`):
```java
  @Test
  @DisplayName("stanza 10: plane_miles(50,500,-100,1,1,40000,271818) at vertex 14")
  void shouldMatchSampleCorrectWhenPlaneMilesStanzaPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Plane.planeMiles(50L, 500L, -100L, 1L, 1L, 40000L, 271818L), 14, ps))).isEqualTo(SampleCorrect.stanza(10));
  }
```

- [ ] **Step 2: Run to verify failure** — `./gradlew :lib:test --tests 'com.robsartin.jsgb.plane.PlaneTest'`: compilation FAILS.

- [ ] **Step 3: Translate `gb_plane.c` into `Plane.java`** with Javadoc (Delaunay triangulation by the incremental algorithm of Guibas, Knuth and Sharir; the point at infinity; `INFTY`; the slots) and `package-info.java`.

- [ ] **Step 4: Run to verify pass, gate, commit**

Run: `./gradlew spotlessApply && ./gradlew check`. If `plane_small` fails at the first arc, suspect `intSqrt` or the vertex-generation draw order; if the arc *set* is right but the order differs, suspect the callback loop's pairing (`i` with `(6n − 7) − i`) or the arc creation order in section 37; if only `plane_prob` fails, the RNG draw in `newEuclidEdge` is not happening on every call.

```bash
git add lib/src/main/java/com/robsartin/jsgb/plane lib/src/test/java/com/robsartin/jsgb/plane demos/src/test
git commit -m "Port gb_plane: delaunay, plane and plane_miles

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: `Dijkstra` (gb_dijk) with pluggable priority queues

**Files:**
- Create: `lib/src/main/java/com/robsartin/jsgb/dijk/Dijkstra.java`, `PriorityQueueHooks.java`, `DList.java`, `Buckets128.java`, `package-info.java`
- Create: `lib/src/test/java/com/robsartin/jsgb/dijk/DijkstraTest.java`
- Modify: `demos/src/test/java/com/robsartin/jsgb/demo/OracleInc3aTest.java`

**Interfaces:**
- `public interface PriorityQueueHooks { void initQueue(long d); void enqueue(Vertex v, long d); void requeue(Vertex v, long d); Vertex delMin(); }` — the C's four function pointers as one object.
- `public final class DList implements PriorityQueueHooks` (the C's `init_dlist`, `enlist`, `reenlist`, `del_first`: a sorted doubly linked list through slots `llink` = `v.V`, `rlink` = `w.V`, keyed on `dist` = `z.I`, with a sentinel head).
- `public final class Buckets128 implements PriorityQueueHooks` (the C's `init_128`, `enq_128`, `req_128`, `del_128`: 128 circular lists indexed by `d & 0x7f`, with `masterKey`).
- `public final class Dijkstra`: `public static PriorityQueueHooks queue = new DList();` (the C's default hook values), `public static PrintStream out = new PrintStream(System.out, true, ISO_8859_1)`, `public static long dijkstra(Vertex uu, Vertex vv, Graph gg, ToLongFunction<Vertex> hh)`, `public static void printDijkstraResult(Vertex vv)`; slot constants documented: `dist` = `z.I`, `backlink` = `y.V`, `hhVal` = `x.I`, and the queue slots `llink` = `v.V`, `rlink` = `w.V`.
- The C's `head[128]` sentinel vertices become `Gb.allocAuxVertices(128)` held statically by the two queue classes (they share one array in the C; keep one shared static in `Dijkstra` or duplicate per class — either is faithful, since only one hook set is active at a time; use one shared `static final Vertex[] HEAD` in `Dijkstra` that both classes read).

**Source:** `gb_dijk.w` sections 2–25 (tangled `gb_dijk.c`, 262 lines). Notes:
- `dijkstra`: `if (hh == null) hh = DUMMY` (a private constant `v -> 0`); section 10: clear `backlink` on all `gg.n` vertices, `uu.backlink = uu; uu.dist = 0; uu.hhVal = hh(uu); queue.initQueue(0)`; `t = uu`; if `Gb.verbose != 0` section 12: `out.print("Distances from " + uu.name); if (hh != DUMMY) out.print(" [" + uu.hhVal + "]"); out.print(":\n")`; `while (t != vv)`: section 11 relaxes `t`'s arcs (`d = t.dist - t.hhVal`; for each arc: if `v.backlink != null` and `dd = d + a.len + v.hhVal < v.dist` then `v.backlink = t; queue.requeue(v, dd)`; else if `v.backlink == null`: `v.hhVal = hh(v); v.backlink = t; queue.enqueue(v, d + a.len + v.hhVal)`); `t = queue.delMin(); if (t == null) return -1;` if verbose section 13: `out.print(" " + (t.dist - t.hhVal + uu.hhVal) + " to " + t.name); if (hh != DUMMY) out.print(" [" + t.hhVal + "]"); out.print(" via " + t.backlink.name + "\n")`. Return `vv.dist - vv.hhVal + uu.hhVal`.
- `printDijkstraResult(vv)` (section 14): `"Sorry, " + name + " is unreachable.\n"` when `backlink == null`; otherwise reverse the backlink chain, print `String.format("%10d %s\n", t.dist - t.hhVal + p.hhVal, t.name)` for each vertex from the source, then restore the chain. `%10ld` right-aligns in a field of 10.
- The two queue implementations are literal translations of sections 16–24 (`DList` uses `HEAD[0]` as the sentinel; `Buckets128` uses all 128).

- [ ] **Step 1: Failing tests**

`DijkstraTest.java`:
```java
package com.robsartin.jsgb.dijk;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.basic.Basic;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DijkstraTest {

  private ByteArrayOutputStream sink;

  @BeforeEach
  void captureOutput() {
    sink = new ByteArrayOutputStream();
    Dijkstra.out = new PrintStream(sink, true, StandardCharsets.ISO_8859_1);
    Dijkstra.queue = new DList();
    Gb.verbose = 0;
  }

  @AfterEach
  void restore() {
    Dijkstra.out = new PrintStream(System.out, true, StandardCharsets.ISO_8859_1);
  }

  private String printed() {
    return sink.toString(StandardCharsets.ISO_8859_1);
  }

  @Test
  @DisplayName("shortest path on a 4x4 grid with unit lengths is the Manhattan distance")
  void shouldFindManhattanDistanceWhenGridSearched() {
    Graph g = Basic.board(4L, 4L, 0L, 0L, 1L, 0L, 0L);
    assertThat(Dijkstra.dijkstra(g.vertices[0], g.vertices[15], g, null)).isEqualTo(6L);
    Dijkstra.printDijkstraResult(g.vertices[15]);
    String[] lines = printed().split("\n");
    assertThat(lines).hasSize(7);
    assertThat(lines[0]).isEqualTo("         0 0.0");
    assertThat(lines[6]).isEqualTo("         6 3.3");
    assertThat(g.vertices[15].y.V()).isNotNull(); // backlink chain restored
  }

  @Test
  @DisplayName("an unreachable target returns -1 and print_dijkstra_result apologises")
  void shouldReturnMinusOneWhenTargetUnreachable() {
    Graph g = Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 1L);
    assertThat(Dijkstra.dijkstra(g.vertices[2], g.vertices[0], g, null)).isEqualTo(-1L);
    Dijkstra.printDijkstraResult(g.vertices[0]);
    assertThat(printed()).isEqualTo("Sorry, 0 is unreachable.\n");
  }

  @Test
  @DisplayName("the 128-bucket queue gives the same answer as the list queue")
  void shouldAgreeWhenQueueImplementationSwapped() {
    Graph g = Basic.board(5L, 5L, 0L, 0L, 1L, 0L, 0L);
    long a = Dijkstra.dijkstra(g.vertices[0], g.vertices[24], g, null);
    Dijkstra.queue = new Buckets128();
    long b = Dijkstra.dijkstra(g.vertices[0], g.vertices[24], g, null);
    assertThat(a).isEqualTo(8L);
    assertThat(b).isEqualTo(8L);
  }

  @Test
  @DisplayName("verbose mode traces each settled vertex with the heuristic value in brackets")
  void shouldTraceWhenVerboseAndHeuristicGiven() {
    Graph g = Basic.board(2L, 0L, 0L, 0L, 1L, 0L, 0L);
    Gb.verbose = 1;
    assertThat(Dijkstra.dijkstra(g.vertices[0], g.vertices[1], g, v -> 5L)).isEqualTo(1L);
    assertThat(printed()).isEqualTo("Distances from 0 [5]:\n 1 to 1 [5] via 0\n");
  }
}
```

Append to `OracleInc3aTest` (imports `Dijkstra`, `DList`, `Buckets128`, `Roget`, `Basic`, `Miles`, `Gb`, `PrintStream`):
```java
  private static String dijkstraCase(Runnable body) {
    PrintStream saved = Dijkstra.out;
    try {
      return Oracle.capture(
          ps -> {
            Dijkstra.out = ps;
            body.run();
          });
    } finally {
      Dijkstra.out = saved;
      Dijkstra.queue = new DList();
      Gb.verbose = 0;
    }
  }

  @Test
  @DisplayName("dijkstra and print_dijkstra_result print exactly as the C")
  void shouldMatchOracleWhenShortestPathsPrinted() {
    com.robsartin.jsgb.graph.Graph roget = Roget.roget(1022L, 0L, 0L, 0L);
    assertThat(dijkstraCase(() -> {
      long d = Dijkstra.dijkstra(roget.vertices[0], roget.vertices[2], roget, null);
      Dijkstra.out.print("return=" + d + "\n");
      Dijkstra.printDijkstraResult(roget.vertices[2]);
    })).isEqualTo(Oracle.inc3a("dijkstra_roget"));
    assertThat(dijkstraCase(() -> {
      long d = Dijkstra.dijkstra(roget.vertices[4], roget.vertices[900], roget, null);
      Dijkstra.out.print("return=" + d + "\n");
      Dijkstra.printDijkstraResult(roget.vertices[900]);
    })).isEqualTo(Oracle.inc3a("dijkstra_roget_far"));
    assertThat(dijkstraCase(() -> {
      Dijkstra.queue = new Buckets128();
      long d = Dijkstra.dijkstra(roget.vertices[4], roget.vertices[900], roget, null);
      Dijkstra.out.print("return=" + d + "\n");
      Dijkstra.printDijkstraResult(roget.vertices[900]);
    })).isEqualTo(Oracle.inc3a("dijkstra_128"));
    com.robsartin.jsgb.graph.Graph path = Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 1L);
    assertThat(dijkstraCase(() -> {
      long d = Dijkstra.dijkstra(path.vertices[2], path.vertices[0], path, null);
      Dijkstra.out.print("return=" + d + "\n");
      Dijkstra.printDijkstraResult(path.vertices[0]);
    })).isEqualTo(Oracle.inc3a("dijkstra_unreachable"));
    com.robsartin.jsgb.graph.Graph m1 = Miles.miles(20L, 0L, 0L, 0L, 0L, 0L, 1L);
    assertThat(dijkstraCase(() -> {
      Gb.verbose = 1;
      long d = Dijkstra.dijkstra(m1.vertices[0], m1.vertices[19], m1, v -> v.x.I / 4);
      Gb.verbose = 0;
      Dijkstra.out.print("return=" + d + "\n");
      Dijkstra.printDijkstraResult(m1.vertices[19]);
    })).isEqualTo(Oracle.inc3a("dijkstra_heuristic"));
    com.robsartin.jsgb.graph.Graph m2 = Miles.miles(20L, 0L, 0L, 0L, 0L, 0L, 1L);
    assertThat(dijkstraCase(() -> {
      Gb.verbose = 1;
      long d = Dijkstra.dijkstra(m2.vertices[3], m2.vertices[7], m2, null);
      Gb.verbose = 0;
      Dijkstra.out.print("return=" + d + "\n");
      Dijkstra.printDijkstraResult(m2.vertices[7]);
    })).isEqualTo(Oracle.inc3a("dijkstra_verbose_plain"));
  }
```
(`Oracle.capture` must expose the `PrintStream` it creates to the body so `Dijkstra.out` can be pointed at it; it already passes `ps` to the consumer, which is what the helper uses.)

- [ ] **Step 2: Run to verify failure** — `./gradlew :lib:test --tests 'com.robsartin.jsgb.dijk.DijkstraTest'`: compilation FAILS.

- [ ] **Step 3: Translate `gb_dijk.c`** into the four classes with Javadoc (Dijkstra's algorithm with an optional consistent heuristic; the queue interface and why the C exposes both implementations; the `dist`/`backlink`/`hhVal` slots the caller may read afterwards; the `out` stream) and `package-info.java`.

- [ ] **Step 4: Run to verify pass, gate, commit**

Run: `./gradlew spotlessApply && ./gradlew check`. The `dijkstra_roget` cases pin tie-breaking in `DList.enlist` (`while (d < t.dist) t = t.llink` inserts after equal keys) and the arc relaxation order.

```bash
git add lib/src/main/java/com/robsartin/jsgb/dijk lib/src/test/java/com/robsartin/jsgb/dijk demos/src/test
git commit -m "Port gb_dijk with the list and 128-bucket priority queues

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: Architecture rules, `TestSample.run` through every ported stanza, docs

**Files:**
- Modify: `lib/src/test/java/com/robsartin/jsgb/ArchitectureTest.java`, `demos/src/main/java/com/robsartin/jsgb/demo/TestSample.java`, `demos/src/test/java/com/robsartin/jsgb/demo/TestSampleTest.java`, `README.md`, `docs/superpowers/specs/2026-09-19-jsgb-design.md` (amendment only if a deviation was recorded in a task report; otherwise none)

- [ ] **Step 1: ArchUnit whitelists** for the five new packages, same `onlyDependOnClassesThat` shape as the increment-2 rules: `words` → own, `graph`, `io`, `flip`, `sort`, `java..`; `roget` → own, `graph`, `io`, `flip`, `java..`; `miles` → own, `graph`, `io`, `flip`, `sort`, `java..`; `plane` → own, `graph`, `flip`, `miles`, `java..`; `dijk` → own, `graph`, `java..`. Probe: a temporary reference from `Roget` to `Words` must fail `rogetDependsOnlyOnKernel`; record fail then pass.

- [ ] **Step 2: `TestSample.run`** emits, in `test_sample.w` order, every stanza whose generator exists: after stanza 3 append stanza 8 (`miles(50,-500,100,1,500,5,314159)` at 20), stanza 10 (`plane_miles(50,500,-100,1,1,40000,271818)` at 14), stanza 11 (`random_bigraph(300,3,1000,-1,0,dst,-500,500,666)` at 3), stanza 12 (`roget(1000,3,1009,1009)` at 40), stanzas 13–15 (the three `words` calls, with `wt_vector` as a mutable local copy `{100,-80589,50000,18935,-18935,18935,18935,18935,18935}` and the `wt[1]++` between the first two). Stanzas 4–7 and 9 stay for increment 3b; leave a comment naming them. Update `shouldMatchSampleCorrectPrefixWhenMainRuns` to expect `HEADER + stanza(0..3) + stanza(8) + stanza(10) + stanza(11) + stanza(12) + stanza(13) + stanza(14) + stanza(15)`; TDD: the test fails first (the new stanzas are missing), then passes.

- [ ] **Step 3: README**: list the ported modules (kernel, raman, basic, rand, save, words, roget, miles, plane, dijk) and note that `./gradlew :demos:run` now prints eleven of the sixteen stanzas.

- [ ] **Step 4: Gate and commit**

Run: `./gradlew spotlessApply && ./gradlew clean check` — Expected: BUILD SUCCESSFUL.

```bash
git add lib/src/test/java/com/robsartin/jsgb/ArchitectureTest.java demos/src README.md
git commit -m "Wire the sample sequence through every ported stanza and extend the architecture rules

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Done criteria for increment 3a

- `./gradlew clean check` green for both subprojects.
- `TestSample.run` reproduces `sample.correct` stanzas 0–3, 8, 10–15; all 25 `oracle_inc3a.out` cases pass; every increment-1/2 test still passes (including the byte-exact `test.correct`).
- `Gb.ONE`, `Gb.allocAuxVertices`, public `Gb.prefix`, ADR 0021 in place; `Save` and the printer use the sentinel.
- Every public function of `gb_words`, `gb_roget`, `gb_miles`, `gb_plane`, `gb_dijk` exists under its C name with the C's parameter order; queue hooks are an interface with both C implementations public.
