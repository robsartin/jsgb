# Increment 2: raman, basic, rand, save/restore — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port `gb_raman`, `gb_basic`, `gb_rand`, and `gb_save` bit-exactly, add the `demos` subproject with Knuth's `print_sample` printer, and prove fidelity against `test.correct`, the first four `sample.correct` stanzas plus the `random_bigraph` stanza, and forty C-generated oracle cases.

**Architecture:** Same shape as increment 1: one Java package per SGB module (`raman`, `basic`, `rand`, `save`) of static methods with the C names, public mutable `Graph`/`Vertex`/`Arc` fields, util slots by convention. Three kernel seams are added first so `save`/`restore` can reproduce the C's block numbering. A new Gradle subproject `demos` holds `TestSample` (printer plus a `main` that grows stanza by stanza) and the oracle tests.

**Tech Stack:** As increment 1 (Java 25, Gradle 9.7.1, JUnit 6, AssertJ, ArchUnit, JaCoCo, Spotless). No new dependencies.

**Spec:** `docs/superpowers/specs/2026-09-19-jsgb-design.md` (read its Amendments). The C sources at `~/code/sgb/*.w` are the authority; tangled `.c` copies exist at `/private/tmp/claude-501/-Users-sartin/022e4f66-ed5a-4350-bddf-2203947541a8/scratchpad/sgb-build/` while that directory survives (`ctangle` recreates them: `brew install cweb`, then `ctangle gb_basic.w` in a scratch copy of `~/code/sgb`).

## Global Constraints

- Everything from the increment 1 plan's Global Constraints still binds: base package `com.robsartin.jsgb`, JDK 25, Spotless before every commit, JaCoCo line ≥ 0.80 / branch ≥ 0.65 on `check`, test names `should<Expected>When<Condition>` with `@DisplayName`, pure TDD (observe the failure before implementing), stage by explicit path, commit trailer `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>` (this repository's convention; it overrides any session default), Gradle commands BLOCKING in the foreground, Javadoc on every public member, `package-info.java` per package.
- Branch `4-increment-2-generators` (issue #4). Do not push; the orchestrator pushes and opens the PR.
- **The C is the authority.** When a test in this plan disagrees with the C, the C wins: report DONE_WITH_CONCERNS naming the assertion and the C section, do not silently edit an expectation. Every oracle value in this plan was produced by the C library; if the Java differs, the Java is wrong.
- **Translation rules for the generator modules** (apply everywhere; the task briefs assume them):
  1. C `long`/`unsigned long` → `long`. `int` flags (`directed ? 1 : 0`) stay ints only inside format strings.
  2. `sprintf(... "%ld")` → `Long.toString`; `"%lu"` → `Long.toUnsignedString`; `"%lx"` → `Long.toHexString`; `"%d"` of a 0/1 flag → `"1"`/`"0"`. Build strings with `StringBuilder` or concatenation; the exact text of every `id` is oracle-checked.
  3. Pointer arithmetic on vertices of one graph becomes index arithmetic: `v - g->vertices` is `v.index`; `g->vertices + k` is `g.vertices[k]`; `u < v` is `u.index < v.index`; `v < g->vertices + g->n` in a loop is `i < g.n`; `vert_offset(v, delta)` (the same-index vertex in another graph) is `other.vertices[v.index]`. A range test on a vertex that might belong to a different graph (`vv >= g->vertices + g->n`) becomes `vv.index >= g.n || g.vertices[vv.index] != vv`.
  4. Arc pairing: `(a+1)` and `(a-1)` used as "the other arc of the same edge" become `a.mate`. The C test `a->next == a+1` ("`a` is the first arc of a self-loop edge") becomes `Gb.isFirstOfSelfLoop(a)`. `(b+1)->len = x` becomes `b.mate.len = x`.
  5. `gb_new_arc`/`gb_new_edge` → `Gb.newArc`/`Gb.newEdge`; `gb_save_string(s)` → `s`; `hash_in`/`hash_out` → `Gb.hashIn`/`Gb.hashOut`; `make_compound_id` → `Gb.makeCompoundId`; `mark_bipartite` → `Gb.markBipartite`.
  6. `panic(c)` macros: set `Gb.panicCode = c`, set `Gb.troubleCode = 0`, return null. Where the C also `gb_recycle`s a half-built graph, call `Gb.recycle` first. Working-storage `gb_free` calls disappear (Java allocation). `gb_trouble_code` checks after building (`if (gb_trouble_code) { gb_recycle; panic(alloc_fault) }`) are kept as written so the control flow matches, even though Java allocation never fails that way.
  7. Module-static scratch arrays (`nn`, `wr`, `del`, `sig`, `xx`, `yy`, `buffer`) stay static fields of the module class, sized as in C (`MAX_D = 91`, `BUF_SIZE = 4096`).
  8. `float` in the C (`board`, `binary`, `product` overflow guards) → Java `float`; both are IEEE binary32.
  9. `%.*s` precision truncations (`lines`, `product`, `induced` name building) → a private `prefix(String, int)` helper in the module (same semantics as `Gb.prefix`: truncate only if longer). SGB names never approach these limits, but the helper keeps the translation literal.
  10. Order of evaluation is observable through the RNG. Keep every `gb_next_rand`/`gb_unif_rand` call in the C's order, including the `rand_len` macro being evaluated at the `gb_new_arc`/`gb_new_edge` call site.

## File structure

```
settings.gradle.kts                          add include("demos")
demos/build.gradle.kts                       java + application, depends on :lib; same spotless/jacoco/junit setup
demos/src/main/java/com/robsartin/jsgb/demo/TestSample.java   print_sample port + main
demos/src/main/java/com/robsartin/jsgb/demo/package-info.java
demos/src/test/java/com/robsartin/jsgb/demo/SampleCorrect.java     stanza extractor (test util)
demos/src/test/java/com/robsartin/jsgb/demo/Oracle.java            oracle_inc2.out case extractor (test util)
demos/src/test/java/com/robsartin/jsgb/demo/TestSampleTest.java    stanza + test.gb golden tests (grows per task)
demos/src/test/java/com/robsartin/jsgb/demo/OracleInc2Test.java    the 40 oracle cases (grows per task)
demos/src/test/resources/oracle/sample.correct       (already on the branch, verbatim)
demos/src/test/resources/oracle/test.correct         (already on the branch, verbatim)
demos/src/test/resources/oracle/inc2/oracle_inc2.out (already on the branch; C output)
demos/src/test/resources/oracle/inc2/oracle_board.gb (already on the branch; C output)
demos/src/test/resources/oracle/inc2/oracle_lines.gb (already on the branch; C output)
demos/src/test/resources/oracle/MANIFEST.md          provenance of every oracle file
scripts/oracle/oracle_inc2.c                         (already on the branch; the C harness)
scripts/regen-oracle.sh                              rebuilds the C and regenerates inc2 oracles
lib/src/main/java/com/robsartin/jsgb/graph/{Arc,Graph,Gb}.java   seams (Task 1)
lib/src/main/java/com/robsartin/jsgb/raman/Raman.java (+package-info)
lib/src/main/java/com/robsartin/jsgb/basic/Basic.java (+package-info)
lib/src/main/java/com/robsartin/jsgb/rand/Rand.java   (+package-info)
lib/src/main/java/com/robsartin/jsgb/save/Save.java   (+package-info)
lib/src/test/java/com/robsartin/jsgb/{graph,raman,basic,rand,save}/*Test.java
lib/src/test/java/com/robsartin/jsgb/ArchitectureTest.java   extend rules
docs/adr/0020-mates-by-position-for-restored-graphs.md
```

Oracle case names in `oracle_inc2.out` are the `==name` header lines; each case's text is everything after its header up to the next header (it begins with the `\n` that `print_sample` prints). The `random_lengths*` headers carry the function's return value after `=`.

---

### Task 1: Kernel seams for save/restore

**Files:**
- Modify: `lib/src/main/java/com/robsartin/jsgb/graph/Arc.java`, `Graph.java`, `Gb.java`
- Modify: `lib/src/test/java/com/robsartin/jsgb/graph/GbTest.java`
- Create: `docs/adr/0020-mates-by-position-for-restored-graphs.md`

**Interfaces:**
- Produces on `Arc`: `public final int index` (position within its block; constructor becomes package-private `Arc(int index)`; `new Arc()` no longer exists).
- Produces on `Graph`: package-private `List<Vertex[]> extraVertexBlocks` with public `List<Vertex[]> extraVertexBlocks()` (unmodifiable view, allocation order).
- Produces on `Gb`:
  - `public static Vertex[] allocVertices(Graph g, int count)`: a fresh block of `count` vertices (indexes `0..count-1` within the block), appended to `g.extraVertexBlocks`; the C `gb_typed_alloc(count, Vertex, g->data)`.
  - `public static Arc[] allocArcs(Graph g, int count)`: a fresh block of `count` arcs (indexes `0..count-1`), appended to `g.arcBlocks`; does not touch the arc cursor; the C `gb_typed_alloc(count, Arc, g->data)`.
  - `public static void restoreStorage(Graph g, int n, int m)`: what `restore_graph` does after `gb_new_graph(0)` and `gb_free(g->data)`: replaces `g.vertices` with a block of `max(n, 1)` fresh vertices, clears `extraVertexBlocks` and `arcBlocks`, then `allocArcs(g, max(m, 1))`. Leaves the arc cursor untouched (so a later `newArc` on `g` starts a new 102-block, as in C).
  - `public static boolean isFirstOfSelfLoop(Arc a)`: `a.mate != null && a.next == a.mate && a.mate.index == a.index + 1` (the C `a->next == a+1`).
  - `virginArc` rolls over on `curBlock.length` instead of `ARCS_PER_BLOCK`.

- [ ] **Step 1: Write the failing tests** (append to `GbTest`)

```java
  @Test
  @DisplayName("arcs know their slot index within their block")
  void shouldNumberArcSlotsWhenBlockAllocated() {
    Graph g = Gb.newGraph(2L);
    Gb.newArc(g.vertices[0], g.vertices[1], 1L);
    Gb.newArc(g.vertices[0], g.vertices[1], 2L);
    Arc[] block = g.arcBlocks().get(0);
    assertThat(block[0].index).isZero();
    assertThat(block[101].index).isEqualTo(101);
    assertThat(g.vertices[0].arcs.index).isEqualTo(1);
  }

  @Test
  @DisplayName("allocVertices registers an extra vertex block in allocation order")
  void shouldRegisterExtraBlockWhenAllocVerticesCalled() {
    Graph g = Gb.newGraph(3L);
    Vertex[] extra = Gb.allocVertices(g, 1);
    assertThat(extra).hasSize(1);
    assertThat(extra[0].index).isZero();
    assertThat(extra[0].name).isSameAs(Gb.NULL_STRING);
    assertThat(g.extraVertexBlocks()).containsExactly(extra);
  }

  @Test
  @DisplayName("allocArcs appends an exact-size block without moving the cursor")
  void shouldAppendExactBlockWhenAllocArcsCalled() {
    Graph g = Gb.newGraph(2L);
    Gb.newArc(g.vertices[0], g.vertices[1], 1L);
    Arc[] exact = Gb.allocArcs(g, 5);
    assertThat(exact).hasSize(5);
    assertThat(exact[4].index).isEqualTo(4);
    assertThat(g.arcBlocks()).hasSize(2);
    Gb.newArc(g.vertices[1], g.vertices[0], 2L); // cursor still in the first block
    assertThat(g.arcBlocks()).hasSize(2);
    assertThat(g.arcBlocks().get(0)[1].len).isEqualTo(2L);
  }

  @Test
  @DisplayName("restoreStorage gives exactly n vertices and one m-arc block, at least one each")
  void shouldReplaceStorageWhenRestoreStorageCalled() {
    Graph g = Gb.newGraph(0L);
    Gb.restoreStorage(g, 3, 4);
    assertThat(g.vertices).hasSize(3);
    assertThat(g.vertices[2].index).isEqualTo(2);
    assertThat(g.arcBlocks()).hasSize(1);
    assertThat(g.arcBlocks().get(0)).hasSize(4);
    assertThat(g.extraVertexBlocks()).isEmpty();
    Gb.newArc(g.vertices[0], g.vertices[1], 9L); // C: a fresh 102-block, not the restored one
    assertThat(g.arcBlocks()).hasSize(2);
    assertThat(g.arcBlocks().get(1)).hasSize(Gb.ARCS_PER_BLOCK);

    Graph empty = Gb.newGraph(0L);
    Gb.restoreStorage(empty, 0, 0);
    assertThat(empty.vertices).hasSize(1);
    assertThat(empty.arcBlocks().get(0)).hasSize(1);
  }

  @Test
  @DisplayName("isFirstOfSelfLoop is true only for the first arc of a self-loop edge")
  void shouldDetectSelfLoopFirstArcWhenEdgeIsLoop() {
    Graph g = Gb.newGraph(2L);
    Vertex u = g.vertices[0];
    Vertex v = g.vertices[1];
    Gb.newEdge(u, u, 1L);
    Gb.newEdge(u, v, 2L);
    Gb.newArc(v, v, 3L);
    Arc[] block = g.arcBlocks().get(0);
    assertThat(Gb.isFirstOfSelfLoop(block[0])).isTrue();
    assertThat(Gb.isFirstOfSelfLoop(block[1])).isFalse();
    assertThat(Gb.isFirstOfSelfLoop(block[2])).isFalse();
    assertThat(Gb.isFirstOfSelfLoop(block[3])).isFalse();
    assertThat(Gb.isFirstOfSelfLoop(block[4])).isFalse(); // newArc: no mate
  }

  @Test
  @DisplayName("virginArc rolls over on the current block's own length")
  void shouldRollOverOnBlockLengthWhenBlockIsNotStandardSize() {
    Graph g = Gb.newGraph(2L);
    Gb.restoreStorage(g, 2, 1);
    Gb.switchToGraph(g); // cursor: none; first newArc allocates a fresh block
    Gb.newArc(g.vertices[0], g.vertices[1], 1L);
    assertThat(g.arcBlocks()).hasSize(2);
  }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :lib:test --tests 'com.robsartin.jsgb.graph.GbTest'`
Expected: compilation FAILS (`index` not found on `Arc`, `allocVertices` not found).

- [ ] **Step 3: Implement**

`Arc.java`: add `public final int index;` with Javadoc "Position of this arc within its block; the C's address order within a block. `save` numbers arcs by it." Constructor `Arc(int index)` package-private.

`Graph.java`: add `final List<Vertex[]> extraVertexBlocks = new ArrayList<>();` and
```java
  /** Vertex blocks allocated by {@link Gb#allocVertices} beyond {@link #vertices}, in allocation order. */
  public List<Vertex[]> extraVertexBlocks() {
    return Collections.unmodifiableList(extraVertexBlocks);
  }
```

`Gb.java`:
```java
  private static Arc[] newBlock(int size) {
    Arc[] block = new Arc[size];
    for (int i = 0; i < size; i++) {
      block[i] = new Arc(i);
    }
    return block;
  }
```
(replace the old `newBlock()` with `newBlock(ARCS_PER_BLOCK)` at its call site), then
```java
  /** {@code gb_typed_alloc(count, Vertex, g->data)}: a fresh vertex block registered on {@code g}. */
  public static Vertex[] allocVertices(Graph g, int count) {
    Vertex[] block = new Vertex[count];
    for (int i = 0; i < count; i++) {
      block[i] = new Vertex(i);
    }
    g.extraVertexBlocks.add(block);
    return block;
  }

  /** {@code gb_typed_alloc(count, Arc, g->data)}: a fresh arc block registered on {@code g}. */
  public static Arc[] allocArcs(Graph g, int count) {
    Arc[] block = newBlock(count);
    g.arcBlocks.add(block);
    return block;
  }

  /**
   * What {@code restore_graph} does after {@code gb_new_graph(0)}: drops the graph's storage and
   * allocates exactly {@code n} vertices (at least one) and one block of exactly {@code m} arcs (at
   * least one). The arc cursor is untouched, so a later {@link #newArc} starts a fresh block.
   */
  public static void restoreStorage(Graph g, int n, int m) {
    int vcount = Math.max(n, 1);
    g.vertices = new Vertex[vcount];
    for (int i = 0; i < vcount; i++) {
      g.vertices[i] = new Vertex(i);
    }
    g.extraVertexBlocks.clear();
    g.arcBlocks.clear();
    allocArcs(g, Math.max(m, 1));
  }

  /** The C test {@code a->next == a+1}: {@code a} is the first arc of a self-loop edge. */
  public static boolean isFirstOfSelfLoop(Arc a) {
    return a.mate != null && a.next == a.mate && a.mate.index == a.index + 1;
  }
```
In `virginArc`, change `nextIndex == ARCS_PER_BLOCK` to `nextIndex == curBlock.length`; in `newEdge`, change the straddle check likewise to `nextIndex == curBlock.length`.

Note for `restoreStorage`: `g.vertices` for a graph created by `newGraph(0)` had `extraN` vertices; they are discarded, as the C frees them.

- [ ] **Step 4: Write ADR 0020**

`docs/adr/0020-mates-by-position-for-restored-graphs.md`, same frontmatter format as 0013–0019 (`status: Accepted`, `date: "2026-09-20"`, `topic: mates-by-position-for-restored-graphs`, `tags: [project, graph]`, `related: [ordering-without-pointers]`). Context: `Arc.mate` is set only by `newEdge` (ADR 0017), but `restore_graph` rebuilds a graph from a file, and `random_lengths`, `gunion`, `lines` and others then use the C's positional rule ("the inverse of `a` is `a+1` iff `u < v` or `a->next == a+1`, else `a-1`") on the restored arcs. Decision: `restore` pairs every restored arc by that rule after reading (walking each vertex's list to learn `u`), and `Arc.index` records slot position so `Gb.isFirstOfSelfLoop` can express `a->next == a+1`. Alternatives: leave mates null after restore (rejected: `test_sample`'s fourth stanza calls `random_lengths` on a restored undirected graph and reads the mate's length); re-derive mates lazily inside each algorithm (rejected: every consumer would repeat the rule). Consequences: restored directed graphs get meaningless mates, exactly as the C's `a±1` would be meaningless; nothing reads them. Add the ADR to `docs/adr/README.md` under Project, same format as the existing entries.

- [ ] **Step 5: Run, gate, commit**

Run: `./gradlew spotlessApply && ./gradlew check` — Expected: BUILD SUCCESSFUL; all previous graph tests still pass.

```bash
git add lib/src/main/java/com/robsartin/jsgb/graph lib/src/test/java/com/robsartin/jsgb/graph/GbTest.java docs/adr/0020-mates-by-position-for-restored-graphs.md docs/adr/README.md
git commit -m "Add the kernel seams save and restore need: arc index, typed blocks, self-loop test

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: `demos` subproject, `TestSample` printer, oracle utilities

**Files:**
- Modify: `settings.gradle.kts`
- Create: `demos/build.gradle.kts`, `demos/src/main/java/com/robsartin/jsgb/demo/TestSample.java`, `demos/src/main/java/com/robsartin/jsgb/demo/package-info.java`
- Create: `demos/src/test/java/com/robsartin/jsgb/demo/SampleCorrect.java`, `Oracle.java`, `TestSampleTest.java`
- Create: `demos/src/test/resources/oracle/MANIFEST.md`, `scripts/regen-oracle.sh`

**Interfaces:**
- Produces: `TestSample.printSample(Graph g, int n, PrintStream out)` (Knuth's `print_sample`, which also recycles `g`), `TestSample.main(String[])` printing the header line and, for now, nothing else (later tasks append stanzas in `sample.correct` order).
- Produces test utilities: `SampleCorrect.stanza(int i)` returns the i-th stanza (0-based) of `sample.correct` **including its leading newline**, where stanza 0 is the `raman` block; `SampleCorrect.HEADER` is `"GraphBase samples generated by test_sample:\n"`. `Oracle.inc2(String name)` returns the text of the named case from `oracle_inc2.out` (everything after the `==name` line up to the next `==` line), and `Oracle.inc2Return(String name)` returns the long after `=` in a `==name=value` header. `Oracle.capture(Consumer<PrintStream>)` runs a printer against a fresh ISO-8859-1 `PrintStream` and returns the text.

Behaviour of `print_sample` (from `test_sample.w` sections 13–18), reproduce exactly:
```
print_sample(g, n):
  out "\n"
  if g == null: out "Ooops, we just ran into panic code <panicCode>!\n"; if GbIo.ioErrors != 0: out "(The I/O error code is 0x<hex>)\n"
  else:
    out "\"" + g.id + "\"\n" + g.n + " vertices, " + g.m + " arcs, util_types " + g.utilTypes
    prUtil(g.uu, types[8], 0) ... prUtil(g.zz, types[13], 0)
    out "\n"
    out "V" + n + ": "
    if n >= g.n || n < 0: out "index is out of range!\n"
    else: prVert(g.vertices[n], 1); out "\n"
    Gb.recycle(g)
prVert(Util-or-Vertex v, l):        // called with a Vertex, or with a Util for V-typed slots
  null → "NULL"; boolean (a V slot with ref == null and I == 1) → "ONE"
  else "\"" + name + "\"" then prUtil(u..z, types[0..5], l-1); if l > 0: for each arc a: "\n   " + prArc(a, 1)
prArc(a, l): "->" + prVert(a.tip, 0); if l > 0: ", " + a.len + prUtil(a.a, types[6], l-1) + prUtil(a.b, types[7], l-1)
prUtil(u, c, l):
  'I' → "[" + u.I + "]"
  'S' → "[\"" + (u.S() == null ? "(null)" : u.S()) + "\"]"
  'A' → if l < 0 nothing; else "[" + (u.A() == null ? "NULL" : prArc(u.A(), l)) + "]"
  'V' → if l < 0 nothing; else "[" + prVert(u, l) + "]"     // note: the C falls through to default after; nothing else printed
  other → nothing
```
`%ld` of a Java `long` and `%lx` → `Long.toHexString`. Output is written as ISO-8859-1 bytes (names are ASCII in every SGB data set, but the checksum alphabet is 8-bit).

- [ ] **Step 1: Gradle**

`settings.gradle.kts`: `include("lib", "demos")`.

`demos/build.gradle.kts`: copy `lib/build.gradle.kts`, change the plugins to `java`, `application`, `jacoco`, spotless; `dependencies { implementation(project(":lib")); testImplementation(libs.junit.jupiter); testImplementation(libs.assertj); testRuntimeOnly(libs.junit.platform.launcher) }`; `application { mainClass = "com.robsartin.jsgb.demo.Jsgb" }` is NOT added yet (no launcher until increment 4); instead register nothing extra. Keep the JaCoCo rule and the `check` wiring identical to `lib`.

- [ ] **Step 2: Failing test**

`TestSampleTest.java`:
```java
package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestSampleTest {

  @Test
  @DisplayName("print_sample reports a panic exactly as the C does")
  void shouldPrintPanicWhenGraphIsNull() {
    com.robsartin.jsgb.graph.Gb.panicCode = 30;
    com.robsartin.jsgb.io.GbIo.ioErrors = 0;
    String out = Oracle.capture(ps -> TestSample.printSample(null, 0, ps));
    assertThat(out).isEqualTo("\nOoops, we just ran into panic code 30!\n");
  }

  @Test
  @DisplayName("print_sample prints a small hand-built graph with util types")
  void shouldPrintGraphWhenVertexHasArcsAndSlots() {
    com.robsartin.jsgb.graph.Graph g = com.robsartin.jsgb.graph.Gb.newGraph(2L);
    g.id = "hand";
    g.utilTypes = "IZZZZZZAZZZZZZ";
    g.vertices[0].name = "a";
    g.vertices[1].name = "b";
    g.vertices[0].u.I = 7;
    com.robsartin.jsgb.graph.Gb.newEdge(g.vertices[0], g.vertices[1], 3L);
    g.vertices[0].arcs.b.A(g.vertices[1].arcs);
    String out = Oracle.capture(ps -> TestSample.printSample(g, 0, ps));
    assertThat(out)
        .isEqualTo("\n\"hand\"\n2 vertices, 2 arcs, util_types IZZZZZZAZZZZZZ\nV0: \"a\"[7]\n   ->\"b\"[0], 3[->\"a\"[7]]\n");
  }

  @Test
  @DisplayName("the sample.correct header and stanza extraction work")
  void shouldExtractStanzasWhenReadingSampleCorrect() {
    assertThat(SampleCorrect.HEADER).isEqualTo("GraphBase samples generated by test_sample:\n");
    assertThat(SampleCorrect.stanza(0)).startsWith("\n\"raman(31,3,3,4)\"\n12 vertices, 96 arcs");
    assertThat(SampleCorrect.stanza(12)).isEqualTo("\nOoops, we just ran into panic code 30!\n");
    assertThat(SampleCorrect.stanza(15)).endsWith("->\"faded\"[430], 1[0]\n");
  }

  @Test
  @DisplayName("oracle_inc2.out cases are addressable by name")
  void shouldExtractCasesWhenReadingOracleFile() {
    assertThat(Oracle.inc2("board_dir")).startsWith("\n\"board(3,3,0,0,-2,0,1)\"\n9 vertices, 10 arcs");
    assertThat(Oracle.inc2("random_lengths")).startsWith("\n\"random_lengths(board(3,0,0,0,1,0,0),0,-3,3,0,9)\"");
    assertThat(Oracle.inc2Return("random_lengths")).isZero();
    assertThat(Oracle.inc2Return("random_lengths_null")).isEqualTo(50L);
    assertThat(Oracle.inc2("random_lengths_null")).isEmpty();
  }
}
```
In the hand-built test, the arc printed for vertex `a` is the edge to `b`, whose `b` slot (type `A`) points at `b`'s own arc back to `a`; `prArc` at `l = 0` prints only `->"a"[7]` (the `I` slot is printed at any depth).

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew :demos:test` — Expected: compilation FAILS (`TestSample`, `Oracle`, `SampleCorrect` missing).

- [ ] **Step 4: Implement**

`Oracle.java` (test util): loads `/oracle/inc2/oracle_inc2.out` from the classpath as ISO-8859-1, splits on lines starting with `==`; a map from name (text after `==` up to an optional `=`) to body; `inc2Return` parses the value after the second `=`. `capture` creates a `ByteArrayOutputStream`, a `PrintStream(baos, true, ISO_8859_1)`, runs the consumer, returns `baos.toString(ISO_8859_1)`.

`SampleCorrect.java` (test util): loads `/oracle/sample.correct` as ISO-8859-1; `HEADER` is the first line plus `\n`; the remainder is split into stanzas at every `\n` that is immediately followed by `"` or `O` at the start of a stanza — implement as: iterate lines after the header; a new stanza starts at each empty line (the `\n` that `print_sample` prints first); the stanza text is `"\n" + the lines up to the next empty line, each with its `\n``. Verify against the four assertions above; there are 16 stanzas (indices 0–15).

`TestSample.java`:
```java
package com.robsartin.jsgb.demo;

import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Util;
import com.robsartin.jsgb.graph.Vertex;
import com.robsartin.jsgb.io.GbIo;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Port of {@code test_sample}: prints salient characteristics of generated graphs so the output can
 * be compared with {@code sample.correct}. {@link #main} grows one stanza at a time as generators are
 * ported; {@link #printSample} is the printer every oracle test uses.
 */
public final class TestSample {

  private TestSample() {}

  /** Runs the sample sequence ported so far, in {@code test_sample.w} order. */
  public static void main(String[] args) {
    PrintStream out = new PrintStream(System.out, true, StandardCharsets.ISO_8859_1);
    out.print("GraphBase samples generated by test_sample:\n");
    // Stanzas are appended here by later tasks.
  }

  /** {@code print_sample(g, n)}: global characteristics of {@code g} and vertex {@code n}; recycles {@code g}. */
  public static void printSample(Graph g, int n, PrintStream out) {
    out.print("\n");
    if (g == null) {
      out.print("Ooops, we just ran into panic code " + Gb.panicCode + "!\n");
      if (GbIo.ioErrors != 0) {
        out.print("(The I/O error code is 0x" + Long.toHexString(GbIo.ioErrors) + ")\n");
      }
      return;
    }
    String s = g.utilTypes;
    out.print("\"" + g.id + "\"\n" + g.n + " vertices, " + g.m + " arcs, util_types " + s);
    prUtil(g.uu, s.charAt(8), 0, s, out);
    prUtil(g.vv, s.charAt(9), 0, s, out);
    prUtil(g.ww, s.charAt(10), 0, s, out);
    prUtil(g.xx, s.charAt(11), 0, s, out);
    prUtil(g.yy, s.charAt(12), 0, s, out);
    prUtil(g.zz, s.charAt(13), 0, s, out);
    out.print("\n");
    out.print("V" + n + ": ");
    if (n >= g.n || n < 0) {
      out.print("index is out of range!\n");
    } else {
      prVert(g.vertices[n], 1, s, out);
      out.print("\n");
    }
    Gb.recycle(g);
  }

  private static void prVert(Vertex v, int l, String s, PrintStream out) {
    if (v == null) {
      out.print("NULL");
      return;
    }
    out.print("\"" + v.name + "\"");
    prUtil(v.u, s.charAt(0), l - 1, s, out);
    prUtil(v.v, s.charAt(1), l - 1, s, out);
    prUtil(v.w, s.charAt(2), l - 1, s, out);
    prUtil(v.x, s.charAt(3), l - 1, s, out);
    prUtil(v.y, s.charAt(4), l - 1, s, out);
    prUtil(v.z, s.charAt(5), l - 1, s, out);
    if (l > 0) {
      for (Arc a = v.arcs; a != null; a = a.next) {
        out.print("\n   ");
        prArc(a, 1, s, out);
      }
    }
  }

  private static void prArc(Arc a, int l, String s, PrintStream out) {
    out.print("->");
    prVert(a.tip, 0, s, out);
    if (l > 0) {
      out.print(", " + a.len);
      prUtil(a.a, s.charAt(6), l - 1, s, out);
      prUtil(a.b, s.charAt(7), l - 1, s, out);
    }
  }

  private static void prUtil(Util u, char c, int l, String s, PrintStream out) {
    switch (c) {
      case 'I' -> out.print("[" + u.I + "]");
      case 'S' -> out.print("[\"" + (u.ref == null ? "(null)" : u.S()) + "\"]");
      case 'A' -> {
        if (l < 0) {
          return;
        }
        out.print("[");
        if (u.ref == null) {
          out.print("NULL");
        } else {
          prArc(u.A(), l, s, out);
        }
        out.print("]");
      }
      case 'V' -> {
        if (l < 0) {
          return;
        }
        out.print("[");
        if (u.ref == null && u.I == 1) {
          out.print("ONE"); // gb_gates' boolean vertex; a V slot holding the value 1
        } else {
          prVert(u.V(), l, s, out);
        }
        out.print("]");
      }
      default -> {}
    }
  }
}
```

`package-info.java`: two sentences: the demonstration programs of the Stanford GraphBase, one class each, plus `TestSample`, the installation self-test whose output is diffed against `sample.correct`.

`MANIFEST.md`:
```markdown
# Oracle files

All files here were produced by the C Stanford GraphBase, release 2025-12-28
(github.com/ascherer/sgb commit 88fac2f051f445d68521dbe6cb756a43e5d53e8a),
built on macOS with clang as `scripts/regen-oracle.sh` does.

| File | Origin |
|---|---|
| `sample.correct` | verbatim from the SGB distribution |
| `test.correct` | verbatim from the SGB distribution |
| `inc2/oracle_inc2.out` | stdout of `scripts/oracle/oracle_inc2.c` linked against `libgb.a` |
| `inc2/oracle_board.gb` | `save_graph(board(2,2,0,0,1,0,0), ...)` written by the same harness |
| `inc2/oracle_lines.gb` | `save_graph` of `lines(board(3,0,0,0,1,0,0),0)` with util_types[0..1] forced to `Z`, same harness |

`oracle_inc2.out` is a sequence of cases; each begins with a line `==name` (or
`==name=returnvalue`) followed by exactly what `print_sample` printed.
```

`scripts/regen-oracle.sh`:
```bash
#!/usr/bin/env bash
# Rebuild the C Stanford GraphBase in a scratch directory and regenerate the
# increment-2 oracle files. Needs cweb (brew install cweb) and ~/code/sgb.
set -euo pipefail
SGB="${SGB:-$HOME/code/sgb}"
HERE="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
git -C "$SGB" archive master | tar -x -C "$WORK"
cd "$WORK"
sed -i '' -e 's|^#DATADIR = \.|DATADIR = .|' -e 's|^#INCLUDEDIR = \.|INCLUDEDIR = .|' \
  -e 's|^#LIBDIR = \.|LIBDIR = .|' -e 's|^#BINDIR = \.|BINDIR = .|' \
  -e 's|^#CWEBINPUTS = \.|CWEBINPUTS = .|' -e 's|^#SYS = -DSYSV|SYS = -DSYSV|' Makefile
make lib CFLAGS="-g -I. -DSYSV -Wno-implicit-int -Wno-deprecated-non-prototype -Wno-implicit-function-declaration" >/dev/null
cp "$HERE/scripts/oracle/oracle_inc2.c" .
cc -w -I. oracle_inc2.c -L. -lgb -o oracle_inc2
./oracle_inc2 > oracle_inc2.out
OUT="$HERE/demos/src/test/resources/oracle/inc2"
cp oracle_inc2.out oracle_board.gb oracle_lines.gb "$OUT/"
echo "regenerated into $OUT (scratch: $WORK)"
```
`chmod +x scripts/regen-oracle.sh`. Do not run it as part of this task (the committed oracles are the reference); the orchestrator has verified it reproduces them.

- [ ] **Step 5: Run, gate, commit**

Run: `./gradlew spotlessApply && ./gradlew check` — Expected: BUILD SUCCESSFUL for both subprojects (the demos JaCoCo gate sees `TestSample` exercised by the tests; `main` is one uncovered method, which the line ratio absorbs).

```bash
git add settings.gradle.kts demos/build.gradle.kts demos/src scripts/regen-oracle.sh
git commit -m "Add the demos subproject with Knuth's print_sample and the oracle utilities

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: `Raman` (gb_raman)

**Files:**
- Create: `lib/src/main/java/com/robsartin/jsgb/raman/Raman.java`, `package-info.java`
- Create: `lib/src/test/java/com/robsartin/jsgb/raman/RamanTest.java`
- Modify: `demos/src/main/java/com/robsartin/jsgb/demo/TestSample.java` (append stanza 0 to `main`), `demos/src/test/java/com/robsartin/jsgb/demo/TestSampleTest.java` and `OracleInc2Test.java` (create)

**Interfaces:**
- Produces: `public static Graph raman(long p, long q, long type, long reduce)`; slot constants documented in Javadoc: Vertex `x.I`, `y.I`, `z.I` (coordinates), Arc `a.I` = `ref` (generator number). `utilTypes` `"ZZZIIZIZZZZZZZ"`, with `[4] = 'Z'` for type 1 and `[5] = 'I'` for types 3 and 4.

**Source:** `gb_raman.w` sections 4–31 (tangled `gb_raman.c`, 451 lines). This is a direct translation; the C is small enough to port line by line. Notes that matter:
- Module statics: `q_sqr`, `q_sqrt`, `q_inv` are three views of one `long[3*q]` array (`q_sqrt = q_sqr + q`, `q_inv = q_sqrt + q`): use three separate `long[q]` arrays. `name_buf` is a scratch string. `gen` is an array of `quaternion {long a0,a1,a2,a3; long bar}` (a private record-like mutable class), sized `p+2`; `gen_count`, `max_gen_count` statics; `deposit(a,b,c,d)` and `lin_frac(a,k)` are private static methods exactly as in C.
- Parameter types: `type` and `reduce` are `unsigned long` in C; the C only compares them with small constants and prints them with `%lu`, so `long` plus `Long.toUnsignedString` in the id is exact.
- Panic codes, in order of the C: `q < 3 || q > 46337` → `VERY_BAD_SPECS` (40); `p < 2` → 41; no primitive root found (`k >= q`) → `BAD_SPECS + 1` (31); `p == 2` and the needed square roots are missing → 32; `p % q == 0` → 33; type inconsistent with quadratic residuosity → 34; `q > 1289` for types 3/4 → 35; `p >= 0x3fffffff / n` → 36; `gen_count != max_gen_count` → 37; final `troubleCode` → `ALLOC_FAULT`. Each panic sets `troubleCode = 0` and returns null (`dead_panic` also frees storage — nothing to do; `late_panic` also `Gb.recycle(newGraph)`).
- `unsigned long bar` and `gen_count` comparisons are on small non-negative values; `long` is exact.
- The edge loop (section 26) uses `v->arcs->ref = kk; (v->arcs+1)->ref = k` for self-loops: `(v->arcs+1)` is the mate, so `v.arcs.mate.a.I = k`. The list surgery `if ((ap = v->arcs->next) != NULL && ap->ref == kk) { v->arcs->next = ap->next; ap->next = v->arcs; v->arcs = ap; }` translates literally with `Arc` references.
- Vertex arithmetic: `new_graph->vertices + lin_frac(...)` → `g.vertices[(int) lin_frac(...)]`; the type-2 index expression `(a < aa ? (a*(2*q-1-a))/2 + aa - 1 : (aa*(2*q-1-aa))/2 + a - 1)` is integer arithmetic on `long`; the type-3/4 expression `(d*q+b)*n_factor + (type==3 ? q_sqrt[aa] : aa) - 1` likewise.
- `sprintf(new_graph->id, "raman(%ld,%ld,%lu,%lu)", p, q, type, reduce)` prints the possibly-adjusted `type` (0 becomes 3 or 4).

- [ ] **Step 1: Failing tests**

`RamanTest.java`:
```java
package com.robsartin.jsgb.raman;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RamanTest {

  @Test
  @DisplayName("raman(31,3,0,4) chooses type 3 and builds the 12-vertex 8-regular graph of sample.correct")
  void shouldBuildTwelveVertexGraphWhenSampleParametersGiven() {
    Graph g = Raman.raman(31L, 3L, 0L, 4L);
    assertThat(g).isNotNull();
    assertThat(g.id).isEqualTo("raman(31,3,3,4)");
    assertThat(g.n).isEqualTo(12L);
    assertThat(g.m).isEqualTo(96L);
    assertThat(g.utilTypes).isEqualTo("ZZZIIIIZZZZZZZ");
    assertThat(g.vertices[4].name).isEqualTo("(1,0;1,1)");
    assertThat(g.vertices[4].arcs.tip.name).isEqualTo("(1,2;1,0)");
    assertThat(g.vertices[4].arcs.a.I).isEqualTo(16L);
  }

  @Test
  @DisplayName("type 1 uses only slot x and names the projective point INF")
  void shouldNameInfinityWhenTypeOne() {
    Graph g = Raman.raman(5L, 3L, 1L, 0L);
    assertThat(g.utilTypes).isEqualTo("ZZZIZZIZZZZZZZ");
    assertThat(g.n).isEqualTo(4L);
    assertThat(g.vertices[3].name).isEqualTo("INF");
    assertThat(g.vertices[3].x.I).isEqualTo(3L);
  }

  @Test
  @DisplayName("bad specs panic with the C's codes and return null")
  void shouldPanicWhenSpecsAreBad() {
    assertThat(Raman.raman(31L, 2L, 0L, 0L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.VERY_BAD_SPECS);
    assertThat(Raman.raman(1L, 3L, 0L, 0L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.VERY_BAD_SPECS + 1);
    assertThat(Raman.raman(6L, 3L, 0L, 0L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS + 3);
    assertThat(Raman.raman(31L, 3L, 4L, 0L)).isNull(); // 31 is a residue mod 3: type must be 3
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS + 4);
    assertThat(Gb.troubleCode).isZero();
  }
}
```

`OracleInc2Test.java` (create; later tasks append methods):
```java
package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.raman.Raman;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Every case here reproduces a `print_sample` run of the C library recorded in oracle_inc2.out. */
class OracleInc2Test {

  @Test
  @DisplayName("raman types 1, 2 and reduced 3 print exactly as the C")
  void shouldMatchOracleWhenRamanPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Raman.raman(5L, 3L, 1L, 0L), 1, ps)))
        .isEqualTo(Oracle.inc2("raman1"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Raman.raman(5L, 3L, 2L, 1L), 2, ps)))
        .isEqualTo(Oracle.inc2("raman2"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Raman.raman(31L, 3L, 3L, 1L), 4, ps)))
        .isEqualTo(Oracle.inc2("raman3red"));
  }
}
```

Append to `TestSampleTest`:
```java
  @Test
  @DisplayName("stanza 0: raman(31,3,0,4) at vertex 4")
  void shouldMatchSampleCorrectWhenRamanStanzaPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Raman.raman(31L, 3L, 0L, 4L), 4, ps)))
        .isEqualTo(SampleCorrect.stanza(0));
  }
```
(add `import com.robsartin.jsgb.raman.Raman;`).

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :lib:test --tests 'com.robsartin.jsgb.raman.RamanTest'` — Expected: compilation FAILS (`Raman` missing).

- [ ] **Step 3: Translate `gb_raman.c` into `Raman.java`**

Follow the C section by section (7, 8, 10, 11, 12, 13 with 14/16/17, 19 with 21/23/24 or 25, 26 with 27/28/29/31), keeping the C's variable names (`a, aa, b, bb, c, cc, d, dd, k, n, n_factor`). Javadoc the class with the module's introduction (Ramanujan graphs from Lubotzky, Phillips and Sarnak; quaternion generators; types 1–4) and the slot usage. `package-info.java` two or three sentences.

Append to `TestSample.main` after the header line:
```java
    printSample(Raman.raman(31L, 3L, 0L, 4L), 4, out);
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew spotlessApply && ./gradlew :lib:test --tests 'com.robsartin.jsgb.raman.*' :demos:test`
Expected: all green. If `raman3red` or stanza 0 differ, the usual culprits are the list surgery in section 26 and the `q_sqrt` lookup in section 29; compare those first.

- [ ] **Step 5: Gate and commit**

Run: `./gradlew check` — Expected: BUILD SUCCESSFUL.

```bash
git add lib/src/main/java/com/robsartin/jsgb/raman lib/src/test/java/com/robsartin/jsgb/raman demos/src
git commit -m "Port gb_raman

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: `Rand` (gb_rand)

**Files:**
- Create: `lib/src/main/java/com/robsartin/jsgb/rand/Rand.java`, `package-info.java`
- Create: `lib/src/test/java/com/robsartin/jsgb/rand/RandTest.java`
- Modify: `demos/src/test/java/com/robsartin/jsgb/demo/OracleInc2Test.java` (append), `TestSampleTest.java` (append stanza 11)

**Interfaces:**
- Produces:
  - `public static Graph randomGraph(long n, long m, long multi, long self, long directed, long[] distFrom, long[] distTo, long minLen, long maxLen, long seed)`
  - `public static Graph randomBigraph(long n1, long n2, long m, long multi, long[] dist1, long[] dist2, long minLen, long maxLen, long seed)`
  - `public static long randomLengths(Graph g, long directed, long minLen, long maxLen, long[] dist, long seed)`
  - No util slots are claimed; `utilTypes` stays as created.

**Source:** `gb_rand.w` sections 5–27 (tangled `gb_rand.c`, 443 lines). Translation notes:
- `walker(n, nn, dist, g)` (sections 18–21) builds Walker's alias table: a private static method returning a `MagicEntry[]` (`MagicEntry { long prob; long inx; }`, a private mutable class). The C node list (`key`, `link`, `j`) becomes three parallel arrays indexed by node number with `-1` for null links, or a small private class; keep the exact `hi`/`lo` push order and the `t = 0x40000000 / nn` arithmetic. The table's `aux_data` lifetime is irrelevant in Java.
- Section 11 validates `distFrom`/`distTo` **before** `Flip.initRand(seed)` is called; codes `INVALID_OPERAND` (60) + 0/1/2 for `distFrom`, + 5/6/7 for `distTo`. `n == 0` → `BAD_SPECS`; `minLen > maxLen` → `VERY_BAD_SPECS`; `Long.compareUnsigned(maxLen - minLen, 0x80000000L) >= 0` → `BAD_SPECS + 1`.
- Vertex names `"0".."n-1"`; id `"random_graph(" + Long.toUnsignedString(n) + "," + Long.toUnsignedString(m) + "," + (multi > 0 ? 1 : multi < 0 ? -1 : 0) + "," + (self != 0 ? 1 : 0) + "," + (directed != 0 ? 1 : 0) + "," + distCode(distFrom) + "," + distCode(distTo) + "," + minLen + "," + maxLen + "," + seed + ")"` where `distCode(x)` is `"dist"` when `x != null` else `"0"`.
- Section 13: `nn` doubles from 1 while `nn < n`, `kk` decrements from 31 each time; `kk` is the shift used when drawing (`k = uu >> kk`). Both tables use the same `nn`/`kk` (the C computes them in sequence; the second `while` loop is a no-op because `nn` is already ≥ `n`).
- Section 9, per requested arc, in this order: draw `u` (alias table with one `nextRand`, or `unifRand(n)`), draw `v` likewise, `if (u == v && self == 0) repeat`, then if `multi <= 0` scan `u.arcs` for an arc to `v`: `multi == 0` → repeat; `multi < 0` → `len = randLen()` (one draw unless `minLen == maxLen`), and if `len < a.len` set `a.len = len` and, if undirected, `a.mate.len = len`; then done. Otherwise `newArc(u, v, randLen())` or `newEdge(u, v, randLen())`. `randLen()` is a private static helper evaluated exactly where the C macro appears: `minLen == maxLen ? minLen : minLen + Flip.unifRand(maxLen - minLen + 1)`.
- `randomBigraph`: `n = n1 + n2`; `n1 == 0 || n2 == 0` → `BAD_SPECS`; range checks as above (`BAD_SPECS + 1`); builds `distFrom`/`distTo` of length `n` (section 23: copies or `(0x40000000 + k) / n1` for `k < n1`, and the same for `n2` into positions `n1..`), calls `randomGraph(n, m, multi, 0, 0, distFrom, distTo, minLen, maxLen, seed)`; if that returns null return null (the C would crash here); sets id `"random_bigraph(" + n1 + "," + n2 + "," + m + "," + sign(multi) + "," + distCode(dist1) + "," + distCode(dist2) + "," + minLen + "," + maxLen + "," + seed + ")"` (unsigned formatting for `n1`, `n2`, `m`), `Gb.markBipartite(g, n1)`.
- `randomLengths` (sections 24–27): `g == null` → return `MISSING_OPERAND`; `Flip.initRand(seed)` **first**, then `minLen > maxLen` → `VERY_BAD_SPECS`, range → `BAD_SPECS`; if `dist != null`: validate over `n = maxLen - minLen + 1` entries (negative → `-1`, overflow → `1`, sum ≠ `0x40000000` → `2`), compute `nn`/`kk`, build the table. Id: `Gb.makeCompoundId(g, "random_lengths(", g, "," + (directed != 0 ? 1 : 0) + "," + minLen + "," + maxLen + "," + distCode(dist) + "," + seed + ")")`. Then section 27:
  ```java
  for (int i = 0; i < g.n; i++) {
    Vertex u = g.vertices[i];
    for (Arc a = u.arcs; a != null; a = a.next) {
      Vertex v = a.tip;
      if (directed == 0 && u.index > v.index) {
        a.len = a.mate.len;
      } else {
        long len;
        if (dist == null) {
          len = randLen();
        } else {
          long uu = Flip.nextRand();
          int k = (int) (uu >> kk);
          len = uu <= table[k].prob ? minLen + k : minLen + table[k].inx;
        }
        a.len = len;
        if (directed == 0 && u == v && Gb.isFirstOfSelfLoop(a)) {
          a = a.mate;
          a.len = len;
        }
      }
    }
  }
  return 0;
  ```

- [ ] **Step 1: Failing tests**

`RandTest.java`:
```java
package com.robsartin.jsgb.rand;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RandTest {

  private static final long[] DST = {0x20000000L, 0x10000000L, 0x10000000L};

  @Test
  @DisplayName("random_graph(3,10,1,1,0,0,dist,1,2,1) is the graph test_sample saves first")
  void shouldBuildTestSampleGraphWhenSeedIsOne() {
    Graph g = Rand.randomGraph(3L, 10L, 1L, 1L, 0L, null, DST, 1L, 2L, 1L);
    assertThat(g).isNotNull();
    assertThat(g.id).isEqualTo("random_graph(3,10,1,1,0,0,dist,1,2,1)");
    assertThat(g.n).isEqualTo(3L);
    assertThat(g.m).isEqualTo(20L);
    assertThat(g.vertices[2].name).isEqualTo("2");
    for (int i = 0; i < 3; i++) {
      for (Arc a = g.vertices[i].arcs; a != null; a = a.next) {
        assertThat(a.mate).isNotNull();
        assertThat(a.mate.len).isEqualTo(a.len);
        assertThat(a.len).isBetween(1L, 2L);
      }
    }
  }

  @Test
  @DisplayName("random_graph panics on n == 0 and on bad distributions")
  void shouldPanicWhenSpecsAreBad() {
    assertThat(Rand.randomGraph(0L, 1L, 0L, 0L, 0L, null, null, 1L, 1L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS);
    assertThat(Rand.randomGraph(3L, 1L, 0L, 0L, 0L, null, null, 2L, 1L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.VERY_BAD_SPECS);
    long[] bad = {1L, 1L, 1L};
    assertThat(Rand.randomGraph(3L, 1L, 0L, 0L, 0L, bad, null, 1L, 1L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.INVALID_OPERAND + 2);
    assertThat(Rand.randomGraph(3L, 1L, 0L, 0L, 0L, null, bad, 1L, 1L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.INVALID_OPERAND + 7);
  }

  @Test
  @DisplayName("random_lengths returns the C's error codes without touching the graph")
  void shouldReturnErrorCodesWhenArgumentsAreBad() {
    assertThat(Rand.randomLengths(null, 0L, 0L, 0L, null, 0L)).isEqualTo(Gb.MISSING_OPERAND);
    Graph g = Gb.newGraph(2L);
    Gb.newEdge(g.vertices[0], g.vertices[1], 4L);
    assertThat(Rand.randomLengths(g, 0L, 3L, 1L, null, 0L)).isEqualTo(Gb.VERY_BAD_SPECS);
    long[] bad = {0x40000000L, 1L};
    assertThat(Rand.randomLengths(g, 0L, 1L, 2L, bad, 0L)).isEqualTo(1L);
    assertThat(g.vertices[0].arcs.len).isEqualTo(4L);
  }

  @Test
  @DisplayName("random_lengths with min == max sets every arc and its mate without drawing")
  void shouldSetConstantLengthsWhenRangeIsSingleValue() {
    Graph g = Gb.newGraph(2L);
    g.id = "hand";
    Gb.newEdge(g.vertices[0], g.vertices[1], 4L);
    Gb.newEdge(g.vertices[1], g.vertices[1], 4L);
    assertThat(Rand.randomLengths(g, 0L, 7L, 7L, null, 5L)).isZero();
    assertThat(g.id).isEqualTo("random_lengths(hand,0,7,7,0,5)");
    for (int i = 0; i < 2; i++) {
      for (Arc a = g.vertices[i].arcs; a != null; a = a.next) {
        assertThat(a.len).isEqualTo(7L);
      }
    }
  }
}
```

Append to `OracleInc2Test` (add `import com.robsartin.jsgb.rand.Rand;` and the `DST` constant `{0x20000000L, 0x10000000L, 0x10000000L}`):
```java
  @Test
  @DisplayName("random_graph and random_bigraph print exactly as the C")
  void shouldMatchOracleWhenRandomGraphsPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Rand.randomGraph(4L, 7L, 1L, 1L, 1L, null, null, 5L, 9L, 3L), 2, ps)))
        .isEqualTo(Oracle.inc2("random_dir"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Rand.randomGraph(3L, 3L, 0L, 0L, 0L, DST, null, 1L, 1L, 7L), 0, ps)))
        .isEqualTo(Oracle.inc2("random_dist"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Rand.randomGraph(3L, 8L, -1L, 1L, 0L, null, null, 1L, 5L, 11L), 1, ps)))
        .isEqualTo(Oracle.inc2("random_multi_neg"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Rand.randomBigraph(2L, 3L, 5L, 0L, null, null, 1L, 3L, 5L), 3, ps)))
        .isEqualTo(Oracle.inc2("random_bigraph"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Rand.randomGraph(0L, 1L, 0L, 0L, 0L, null, null, 1L, 1L, 1L), 0, ps)))
        .isEqualTo(Oracle.inc2("random_bad"));
  }
```

Append to `TestSampleTest`:
```java
  @Test
  @DisplayName("stanza 11: random_bigraph(300,3,1000,-1,0,dist,-500,500,666) at vertex 3")
  void shouldMatchSampleCorrectWhenRandomBigraphStanzaPrinted() {
    long[] dst = {0x20000000L, 0x10000000L, 0x10000000L};
    assertThat(Oracle.capture(ps -> TestSample.printSample(Rand.randomBigraph(300L, 3L, 1000L, -1L, null, dst, -500L, 500L, 666L), 3, ps)))
        .isEqualTo(SampleCorrect.stanza(11));
  }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :lib:test --tests 'com.robsartin.jsgb.rand.RandTest'` — Expected: compilation FAILS (`Rand` missing).

- [ ] **Step 3: Translate `gb_rand.c` into `Rand.java`**

Class Javadoc: random graphs with prescribed degree distributions via Walker's alias method, and random arc lengths; note that every graph is reproducible from its seed. `package-info.java` two sentences. Do **not** append anything to `TestSample.main` yet: stanza 11 sits after stanzas that need increment 3; the orchestrator's final task in this plan adds the stanzas that are complete.

- [ ] **Step 4: Run to verify pass, gate, commit**

Run: `./gradlew spotlessApply && ./gradlew check` — Expected: BUILD SUCCESSFUL, including the five oracle cases and stanza 11. If `random_bigraph` (stanza 11) differs while the small cases pass, the alias table (`walker`) is the suspect: compare sections 19–21 line by line, especially the `hi`/`lo` list order and `x = t*q->j + q->key - 1; prob = x+x+1`.

```bash
git add lib/src/main/java/com/robsartin/jsgb/rand lib/src/test/java/com/robsartin/jsgb/rand demos/src/test
git commit -m "Port gb_rand

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: `Basic` part 1 — `board`, `simplex`, `subsets`

**Files:**
- Create: `lib/src/main/java/com/robsartin/jsgb/basic/Basic.java`, `package-info.java`
- Create: `lib/src/test/java/com/robsartin/jsgb/basic/BasicGridTest.java`
- Modify: `demos/src/test/java/com/robsartin/jsgb/demo/OracleInc2Test.java`, `TestSampleTest.java` (append)

**Interfaces:**
- Produces on `Basic`: `public static Graph board(long n1, long n2, long n3, long n4, long piece, long wrap, long directed)`, `public static Graph simplex(long n, long n0, long n1, long n2, long n3, long n4, long directed)`, `public static Graph subsets(long n, long n0, long n1, long n2, long n3, long n4, long sizeBits, long directed)`; constants `MAX_D = 91`, `BUF_SIZE = 4096`, `MAX_NNN = 1000000000.0f`; slot names as private constants matching the C macros: `tmp` = `u.V`, `tlen` = `z.A`, `mult` = `v.I`, `minlen` = `w.I`, `map` = `z.V`, `ind` = `z.I` (public constant `IND_GRAPH = 1000000000`), `subst` = `y.G`. Later tasks add the other nine functions to this class.
- Convenience macros from `gb_basic.h` become static methods in Task 9 (`complete`, `transitive`, `empty`, `circuit`, `cycle`, `disjointSubsets`, `petersen`, `allPerms`, `allParts`, `allTrees`); not here.

**Source:** `gb_basic.w` sections 8–40 (tangled `gb_basic.c` lines 1–1060). Notes:
- The three functions share section 27 (argument massaging for `simplex`/`subsets`/`perms`), section 12 (`d > MAX_D` panic, `nn[k] = nn[j]` replication), section 29/30 (vertex-count coefficients), sections 31–35/39–40 (vertex enumeration). Translate each shared section once as a private static method returning the needed values via the static scratch arrays (`nn`, `xx`, `yy`, `sig`, `wr`, `del`) and a small private mutable state holder for `d`/`k` if a method needs to return two values; mirror the C's `goto done`/`goto last` with loop breaks. Panics inside shared sections must return null from the calling function: have the helper return a boolean "panicked" and check it at every call site (the panic code is already set).
- `board`: section 11 mutates `n1..n4`, `piece`; `nn[1..d]`; section 13 builds the vertex names `".x1.x2..."` from `buffer[1]` (drop the leading dot); `v.x.I, y.I, z.I = xx[1], xx[2], xx[3]`; `utilTypes = "ZZZIIIZZZZZZZZ"`. Section 16 reads `wrap` bits with a signed right shift; `-2147483648L` is `0x80000000` sign-extended, and the id prints `wrap` with `%ld` (`-2147483648` in stanza 1). Sections 15–23 are the move enumeration; translate literally with `sig[0..d+1]`, `del`, the `p = |piece|` loop and both `while (1)` loops, the `goto no_more`/`unequal` labels as loop control. The arc creation index `j = nn[k]*j + yy[k]` selects `g.vertices[(int) j]`.
- `simplex`: `nn[0] = n` when `n0 < 0`; coefficients of section 29–30 with the `s > 1000000000` guard (`VERY_BAD_SPECS`); vertex names `".x0.x1..."` drop the leading dot; slots `x = xx[0], y = xx[1], z = xx[2]`; `utilTypes = "VVZIIIZZZZZZZZ"` (the hash uses `u`/`v`); `Gb.hashIn(v)` after naming; section 35 finds neighbours by name via `Gb.hashOut` and panics `IMPOSSIBLE + 2` if missing; `newArc(u, v, 1)` or `newEdge(u, v, 1)` — note the argument order `(u, v)`. Final `v != vertices + n` → `IMPOSSIBLE`.
- `subsets`: as `simplex` for enumeration, but the edge rule of section 40 parses each earlier vertex's name (`".%ld"` fields) to compute `ss`, then `if (ss < 64 && (sizeBits & (1L << ss)) != 0)` (the C's `UL_BITS` is 64 on this platform, and the reference `sample.correct` shows `0x80000000` treated as a 64-bit value; keep `1L << ss`); `utilTypes = "ZZZIIIZZZZZZZZ"`; id `"subsets(" + Long.toUnsignedString(n) + "," + n0 + "," + n1 + "," + n2 + "," + n3 + "," + n4 + ",0x" + Long.toHexString(sizeBits) + "," + directedFlag + ")"` printed **after** section 27 has mutated `n0..n4` (stanza 2 shows `subsets(32,18,16,0,0,0,0x80000000,1)` for the call `subsets(32,18,16,0,999,-999,0x80000000,1)`).
- `simplex` id: `"simplex(" + Long.toUnsignedString(n) + "," + n0 + ... + ")"` also after mutation.

- [ ] **Step 1: Failing tests**

`BasicGridTest.java`:
```java
package com.robsartin.jsgb.basic;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BasicGridTest {

  @Test
  @DisplayName("board(3,4,0,0,-1,0,0) is the 3x4 wazir graph queen.w starts from")
  void shouldBuildWazirMovesWhenPieceIsMinusOne() {
    Graph g = Basic.board(3L, 4L, 0L, 0L, -1L, 0L, 0L);
    assertThat(g.id).isEqualTo("board(3,4,0,0,-1,0,0)");
    assertThat(g.n).isEqualTo(12L);
    assertThat(g.m).isEqualTo(34L);
    assertThat(g.vertices[0].name).isEqualTo("0.0");
    assertThat(g.vertices[11].name).isEqualTo("2.3");
    assertThat(g.vertices[5].x.I).isEqualTo(1L);
    assertThat(g.vertices[5].y.I).isEqualTo(1L);
    assertThat(g.utilTypes).isEqualTo("ZZZIIIZZZZZZZZ");
  }

  @Test
  @DisplayName("board defaults to an 8x8 board when n1 <= 0 and rejects more than 91 dimensions")
  void shouldDefaultAndPanicWhenDimensionsAreExtreme() {
    Graph g = Basic.board(0L, 0L, 0L, 0L, 1L, 0L, 0L);
    assertThat(g.n).isEqualTo(64L);
    assertThat(g.id).isEqualTo("board(8,8,0,0,1,0,0)");
    assertThat(Basic.board(2L, -92L, 0L, 0L, 1L, 0L, 0L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS);
  }

  @Test
  @DisplayName("simplex(3,-3,...) enumerates the 20 compositions of 3 into 4 parts")
  void shouldEnumerateCompositionsWhenSimplexBuilt() {
    Graph g = Basic.simplex(3L, -3L, 0L, 0L, 0L, 0L, 0L);
    assertThat(g.id).isEqualTo("simplex(3,-3,0,0,0,0,0)");
    assertThat(g.n).isEqualTo(20L);
    assertThat(g.utilTypes).isEqualTo("VVZIIIZZZZZZZZ");
    assertThat(g.vertices[0].name).isEqualTo("3.0.0.0");
    assertThat(Gb.hashLookup("0.1.1.1", g)).isSameAs(g.vertices[5]);
  }

  @Test
  @DisplayName("subsets id prints the massaged arguments and hex size bits")
  void shouldPrintMassagedArgumentsWhenSubsetsBuilt() {
    Graph g = Basic.subsets(32L, 18L, 16L, 0L, 999L, -999L, 0x80000000L, 1L);
    assertThat(g.id).isEqualTo("subsets(32,18,16,0,0,0,0x80000000,1)");
    assertThat(g.n).isEqualTo(3L);
    assertThat(g.m).isEqualTo(2L);
  }
}
```

Append to `OracleInc2Test` (add `import com.robsartin.jsgb.basic.Basic;`):
```java
  @Test
  @DisplayName("board, simplex and subsets print exactly as the C")
  void shouldMatchOracleWhenGridGraphsPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.board(3L, 3L, 0L, 0L, -2L, 0L, 1L), 4, ps)))
        .isEqualTo(Oracle.inc2("board_dir"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.board(4L, 4L, 0L, 0L, 1L, 3L, 0L), 0, ps)))
        .isEqualTo(Oracle.inc2("board_wrap"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.simplex(4L, 2L, 0L, 0L, 0L, 0L, 0L), 3, ps)))
        .isEqualTo(Oracle.inc2("simplex"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.simplex(3L, -3L, 0L, 0L, 0L, 0L, 1L), 5, ps)))
        .isEqualTo(Oracle.inc2("simplex_dir"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.subsets(3L, -4L, 0L, 0L, 0L, 0L, 3L, 0L), 2, ps)))
        .isEqualTo(Oracle.inc2("subsets2"));
  }

  @Test
  @DisplayName("random_lengths on board graphs prints exactly as the C, with the C's return codes")
  void shouldMatchOracleWhenRandomLengthsApplied() {
    com.robsartin.jsgb.graph.Graph g = Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 0L);
    assertThat(Rand.randomLengths(g, 0L, -3L, 3L, null, 9L)).isEqualTo(Oracle.inc2Return("random_lengths"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(g, 1, ps))).isEqualTo(Oracle.inc2("random_lengths"));
    com.robsartin.jsgb.graph.Graph d = Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 1L);
    assertThat(Rand.randomLengths(d, 1L, 10L, 12L, DST, 4L)).isEqualTo(Oracle.inc2Return("random_lengths_dir"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(d, 0, ps))).isEqualTo(Oracle.inc2("random_lengths_dir"));
    assertThat(Rand.randomLengths(null, 0L, 0L, 0L, null, 0L)).isEqualTo(Oracle.inc2Return("random_lengths_null"));
  }
```

Append to `TestSampleTest`:
```java
  @Test
  @DisplayName("stanza 1: board(1,1,2,-33,1,-2^31,1) at vertex 2000")
  void shouldMatchSampleCorrectWhenBoardStanzaPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.board(1L, 1L, 2L, -33L, 1L, -0x40000000L - 0x40000000L, 1L), 2000, ps)))
        .isEqualTo(SampleCorrect.stanza(1));
  }

  @Test
  @DisplayName("stanza 2: subsets(32,18,16,0,999,-999,0x80000000,1) at vertex 1")
  void shouldMatchSampleCorrectWhenSubsetsStanzaPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.subsets(32L, 18L, 16L, 0L, 999L, -999L, 0x80000000L, 1L), 1, ps)))
        .isEqualTo(SampleCorrect.stanza(2));
  }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :lib:test --tests 'com.robsartin.jsgb.basic.BasicGridTest'` — Expected: compilation FAILS (`Basic` missing).

- [ ] **Step 3: Translate sections 8–40 into `Basic.java`**

Class Javadoc: the module's introduction (classical graphs: boards, simplexes, subsets, permutations, partitions, binary trees; and graph transformations) plus the slot table. `package-info.java` three sentences. Stanza 1 builds a 2048-vertex graph in 33 dimensions (`d = 33`), so the enumeration loops must handle `d` well above 4; the static arrays are sized `MAX_D + 2` as in the C.

Append to `TestSample.main`:
```java
    printSample(Basic.board(1L, 1L, 2L, -33L, 1L, -0x40000000L - 0x40000000L, 1L), 2000, out);
    printSample(Basic.subsets(32L, 18L, 16L, 0L, 999L, -999L, 0x80000000L, 1L), 1, out);
```

- [ ] **Step 4: Run to verify pass, gate, commit**

Run: `./gradlew spotlessApply && ./gradlew check` — Expected: BUILD SUCCESSFUL. Stanza 1 is the sharpest test of `board` (33 dimensions, wraparound bits from a negative `wrap`); if it fails, check section 16's bit extraction and section 17/18's `sig`/`del` bookkeeping first.

```bash
git add lib/src/main/java/com/robsartin/jsgb/basic lib/src/test/java/com/robsartin/jsgb/basic demos/src
git commit -m "Port gb_basic's board, simplex and subsets generators

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: `Basic` part 2 — `perms`, `parts`, `binary`

**Files:**
- Modify: `lib/src/main/java/com/robsartin/jsgb/basic/Basic.java`
- Create: `lib/src/test/java/com/robsartin/jsgb/basic/BasicCombinatorialTest.java`
- Modify: `demos/src/test/java/com/robsartin/jsgb/demo/OracleInc2Test.java` (append)

**Interfaces:**
- Produces on `Basic`: `public static Graph perms(long n0, long n1, long n2, long n3, long n4, long maxInv, long directed)`, `public static Graph parts(long n, long maxParts, long maxSize, long directed)`, `public static Graph binary(long n, long maxHeight, long directed)`. All three set `utilTypes = "VVZZZZZZZZZZZZ"` (the name hash) and use `Gb.hashIn`/`Gb.hashOut`.

**Source:** `gb_basic.w` sections 43–72 (tangled lines 860–1330). Notes:
- `perms`: section 44 sets `n = BUF_SIZE` before reusing section 27 (so `n0 > n` clamps at 4096); section 45 computes `s` (total elements) and `ss` (max inversions), panics `BAD_SPECS` if any `nn[k] >= BUF_SIZE`, `BAD_SPECS + 1` if `s >= BUF_SIZE`, then `n = s` and `maxInv = ss` when `maxInv == 0 || maxInv > ss`; the id prints the adjusted `maxInv` (`perms(1,1,1,0,0,3,0)` in the oracle for `maxInv = 0`). Section 46/47 coefficient loops with the `> 1000000000` guards (`VERY_BAD_SPECS + 1` inside, `VERY_BAD_SPECS` on the total). Section 48–53: `xtab`, `ytab`, `ztab` of length `n + 1` (C allocates `3n + 3` longs); names are `n` characters from `short_imap[xtab[k]]` written right to left into `buffer[0..n-1]` (section 52); the neighbour search of section 53 swaps two characters of the name, looks it up, and swaps back. `short_imap` is the 94-character string in the C (the GraphBase alphabet without `\\`, `"`, space, newline).
- `parts`: `maxParts`/`maxSize` default to `n` when 0 or larger than `n`; `maxParts > MAX_D` → `BAD_SPECS`; section 56 coefficients (`VERY_BAD_SPECS` guard); section 57–62 enumeration with names `"a+b+c"` (`"+%ld"` per part, leading `+` dropped); section 61/62 builds neighbour names into `nn[1..d+1]` and looks them up, panicking `IMPOSSIBLE + 2` if absent. Note `newArc(v, u, 1)` / `newEdge(v, u, 1)` here (source first), unlike `simplex`.
- `binary`: `2n + 2 > BUF_SIZE` → `BAD_SPECS`; `maxHeight` defaults to `n`; `maxHeight > 30` → `VERY_BAD_SPECS`; section 65/66 vertex count with the `float` guard (`VERY_BAD_SPECS + 1`) and `d = (1 << maxHeight) - 1 - n > 8` → `BAD_SPECS + 1` (the oracle's `binary_big` case, panic 31); sections 67–72: `xtab`, `ytab`, `ltab`, `stab` of length `d + 1` where `d = 2n`; names are `d + 1` characters `'.'`/`'x'` (section 71); section 72 rotates a subtree by shifting `xtab` entries, looks the name up, and shifts back; only adds an arc if the lookup succeeds (`if (u)`), no panic.

- [ ] **Step 1: Failing tests**

`BasicCombinatorialTest.java`:
```java
package com.robsartin.jsgb.basic;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BasicCombinatorialTest {

  @Test
  @DisplayName("perms of a 3-element set has 6 vertices and adjusts max_inv to 3 in its id")
  void shouldEnumeratePermutationsWhenThreeDistinctElements() {
    Graph g = Basic.perms(1L, 1L, 1L, 0L, 0L, 0L, 0L);
    assertThat(g.id).isEqualTo("perms(1,1,1,0,0,3,0)");
    assertThat(g.n).isEqualTo(6L);
    assertThat(g.m).isEqualTo(12L);
    assertThat(g.utilTypes).isEqualTo("VVZZZZZZZZZZZZ");
    assertThat(g.vertices[0].name).isEqualTo("012");
    assertThat(Gb.hashLookup("210", g)).isNotNull();
  }

  @Test
  @DisplayName("parts(6) has the 11 partitions of 6 named with plus signs")
  void shouldEnumeratePartitionsWhenNIsSix() {
    Graph g = Basic.parts(6L, 0L, 0L, 0L);
    assertThat(g.id).isEqualTo("parts(6,6,6,0)");
    assertThat(g.n).isEqualTo(11L);
    assertThat(g.vertices[0].name).isEqualTo("6");
    assertThat(g.vertices[10].name).isEqualTo("1+1+1+1+1+1");
    assertThat(Basic.parts(5L, 92L, 0L, 0L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS);
  }

  @Test
  @DisplayName("binary(4) has the 14 binary trees on 4 nodes and rejects heights over 30")
  void shouldEnumerateTreesWhenNIsFour() {
    Graph g = Basic.binary(4L, 0L, 0L);
    assertThat(g.id).isEqualTo("binary(4,4,0)");
    assertThat(g.n).isEqualTo(14L);
    assertThat(g.vertices[0].name).hasSize(9);
    assertThat(Basic.binary(40L, 31L, 0L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.VERY_BAD_SPECS);
  }
}
```

Append to `OracleInc2Test`:
```java
  @Test
  @DisplayName("perms, parts and binary print exactly as the C, including the binary panic")
  void shouldMatchOracleWhenCombinatorialGraphsPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.perms(1L, 1L, 1L, 0L, 0L, 0L, 0L), 3, ps)))
        .isEqualTo(Oracle.inc2("perms"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.perms(2L, 1L, 0L, 0L, 0L, 2L, 1L), 2, ps)))
        .isEqualTo(Oracle.inc2("perms_dir"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.parts(6L, 0L, 0L, 0L), 4, ps)))
        .isEqualTo(Oracle.inc2("parts"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.parts(7L, 3L, 4L, 1L), 2, ps)))
        .isEqualTo(Oracle.inc2("parts_dir"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.binary(4L, 0L, 0L), 5, ps)))
        .isEqualTo(Oracle.inc2("binary"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.binary(5L, 4L, 1L), 3, ps)))
        .isEqualTo(Oracle.inc2("binary_dir"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.binary(20L, 6L, 0L), 100, ps)))
        .isEqualTo(Oracle.inc2("binary_big"));
  }
```

**A C quirk you must know about:** sections 52 (`perms`) and 71 (`binary`) write the name characters into the static `buffer` without a terminating NUL, so in a long-running C process a `perms` or `binary` name can carry trailing garbage from an earlier generator's use of `buffer`. In a fresh process the buffer is zero-filled and the names are exactly `n` characters (`perms`) or `d + 1` characters (`binary`). jsgb produces the fresh-process names (build them with a `StringBuilder` of exactly that length). The oracle harness runs the `perms` and `binary` cases first, before any other `Basic` call, for this reason; `sample.correct` and the demos never call either function.

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :lib:test --tests 'com.robsartin.jsgb.basic.BasicCombinatorialTest'` — Expected: compilation FAILS (`perms` not found).

- [ ] **Step 3: Translate sections 43–72**

Add the three methods and their private helpers to `Basic`.

- [ ] **Step 4: Run to verify pass, gate, commit**

Run: `./gradlew spotlessApply && ./gradlew check` — Expected: BUILD SUCCESSFUL.

```bash
git add lib/src/main/java/com/robsartin/jsgb/basic lib/src/test/java/com/robsartin/jsgb/basic demos/src/test
git commit -m "Port gb_basic's perms, parts and binary generators

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: `Basic` part 3 — `complement`, `gunion`, `intersection`

**Files:**
- Modify: `lib/src/main/java/com/robsartin/jsgb/basic/Basic.java`
- Create: `lib/src/test/java/com/robsartin/jsgb/basic/BasicSetOpsTest.java`
- Modify: `demos/src/test/java/com/robsartin/jsgb/demo/OracleInc2Test.java` (append)

**Interfaces:**
- Produces on `Basic`: `public static Graph complement(Graph g, long copy, long self, long directed)`, `public static Graph gunion(Graph g, Graph gg, long multi, long directed)`, `public static Graph intersection(Graph g, Graph gg, long multi, long directed)`. All three copy the `n` vertex names of `g` into a new graph (section 75) and leave `utilTypes` at the default.

**Source:** `gb_basic.w` sections 74–86 (tangled lines 1545–1815). Notes:
- Section 75 (shared): `n = g.n`; `newGraph(n)`; copy names `newg.vertices[i].name = g.vertices[i].name` for `i < n`. `g == null` → `MISSING_OPERAND` (both operands for the binary ops).
- `complement` (section 76): for each `v` of `g` (index `i`), `u = newg.vertices[i]`; mark every tip: `newg.vertices[a.tip.index].u.V(u)` (`tmp`) for `a` in `v.arcs`; then directed: for every `vv` in `newg` (`j < n`): `if ((vv.tmp == u && copy != 0) || (vv.tmp != u && copy == 0)) if (vv != u || self != 0) newArc(u, vv, 1)`; undirected: `vv` from `self != 0 ? i : i + 1` to `n - 1`, same test, `newEdge(u, vv, 1)`. Finally clear `tmp` on all `n` new vertices. Id: `Gb.makeCompoundId(newg, "complement(", g, "," + flag(copy) + "," + flag(self) + "," + flag(directed) + ")")`. Note that a tip beyond `g.n` (an extra vertex) is impossible in well-formed graphs; the C would index the new graph's extra vertices, and so does `newg.vertices[a.tip.index]`.
- `gunion` (sections 79–80): for each `v` of `g` (index `i`): `vv = newg.vertices[i]`; `vvv` = `gg.vertices[i]` if `i < gg.n` else none; process `v.arcs` with `u = newg.vertices[a.tip.index]` through section 80; then if `vvv` exists, process `vvv.arcs` with `u = newg.vertices[a.tip.index]` but only when `a.tip.index < n`. Section 80 as a private method `unionArc(Vertex vv, Vertex u, Arc a, long multi, long directed)` returning the possibly-advanced `a`:
  ```
  directed: if (multi != 0 || u.tmp != vv) newArc(vv, u, a.len) else { b = u.tlen; if (a.len < b.len) b.len = a.len; }  u.tmp = vv; u.tlen = vv.arcs;
  undirected, only if u.index >= vv.index: if (multi != 0 || u.tmp != vv) newEdge(vv, u, a.len) else { b = u.tlen; if (a.len < b.len) { b.len = a.len; b.mate.len = a.len; } }  u.tmp = vv; u.tlen = vv.arcs; if (u == vv && Gb.isFirstOfSelfLoop(a)) a = a.mate;
  ```
  (`tmp` is `u.V`, `tlen` is `z.A`.) Clear `tmp` and `tlen` on all `n` new vertices at the end. Id: `Gb.makeDoubleCompoundId(newg, "gunion(", g, ",", gg, "," + flag(multi) + "," + flag(directed) + ")")`.
- `intersection` (sections 82–86): per `v` (index `i`) with `vv = newg.vertices[i]`, skip if `i >= gg.n`; section 85 marks `g`'s arcs: `u = newg.vertices[a.tip.index]`; `if (u.tmp == vv) { u.mult++; if (a.len < u.minlen) u.minlen = a.len; } else { u.tmp = vv; u.mult = 0; u.minlen = a.len; }`; `if (u == vv && directed == 0 && Gb.isFirstOfSelfLoop(a)) a = a.mate;`. Then for `gg.vertices[i].arcs`: `u = newg.vertices[a.tip.index]` only when `a.tip.index < n`; `if (u.tmp == vv) { l = max(u.minlen, a.len); if (u.mult < 0) section 84 else section 83 }`. Section 84: `b = u.tlen; if (l < b.len) { b.len = l; if (directed == 0) b.mate.len = l; }`. Section 83: directed → `newArc(vv, u, l)`; undirected → `if (vv.index <= u.index) newEdge(vv, u, l); if (vv == u && Gb.isFirstOfSelfLoop(a)) a = a.mate;`; then `if (multi == 0) { u.tlen = vv.arcs; u.mult = -1; } else if (u.mult == 0) u.tmp = null; else u.mult--;`. Section 86 clears `tmp`, `tlen`, `mult`, `minlen` on all `n` new vertices. `mult` is `v.I`, `minlen` is `w.I`.
- `flag(x)` is `x != 0 ? "1" : "0"`.

- [ ] **Step 1: Failing tests**

`BasicSetOpsTest.java`:
```java
package com.robsartin.jsgb.basic;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BasicSetOpsTest {

  @Test
  @DisplayName("complement of a path with copy=1 and self=1 keeps the edges and adds loops")
  void shouldCopyEdgesAndAddLoopsWhenComplementCopiesWithSelf() {
    Graph g = Basic.complement(Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 0L), 1L, 1L, 0L);
    assertThat(g.id).isEqualTo("complement(board(3,0,0,0,1,0,0),1,1,0)");
    assertThat(g.n).isEqualTo(3L);
    assertThat(g.m).isEqualTo(10L); // 2 edges + 3 loops, two arcs each
    assertThat(g.utilTypes).isEqualTo("ZZZZZZZZZZZZZZ");
    assertThat(g.vertices[0].u.ref).isNull();
  }

  @Test
  @DisplayName("complement of a null graph panics with missing_operand")
  void shouldPanicWhenOperandMissing() {
    assertThat(Basic.complement(null, 0L, 0L, 0L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.MISSING_OPERAND);
    assertThat(Basic.gunion(null, Basic.board(2L, 0L, 0L, 0L, 1L, 0L, 0L), 0L, 0L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.MISSING_OPERAND);
  }

  @Test
  @DisplayName("gunion of a path and its complement is the complete graph with loops")
  void shouldBeCompleteWhenPathUnionedWithComplement() {
    Graph path = Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 0L);
    Graph comp = Basic.complement(Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 0L), 0L, 1L, 0L);
    Graph g = Basic.gunion(path, comp, 0L, 0L);
    assertThat(g.id).isEqualTo("gunion(board(3,0,0,0,1,0,0),complement(board(3,0,0,0,1,0,0),0,1,0),0,0)");
    assertThat(g.m).isEqualTo(12L); // 3 edges + 3 loops
  }

  @Test
  @DisplayName("intersection of a path with itself keeps every edge once")
  void shouldKeepEdgesWhenIntersectedWithItself() {
    Graph a = Basic.board(4L, 0L, 0L, 0L, 1L, 0L, 0L);
    Graph b = Basic.board(4L, 0L, 0L, 0L, 1L, 0L, 0L);
    Graph g = Basic.intersection(a, b, 0L, 0L);
    assertThat(g.m).isEqualTo(6L);
    assertThat(g.vertices[1].v.I).isZero();
    assertThat(g.vertices[1].w.I).isZero();
  }
}
```

Append to `OracleInc2Test`:
```java
  @Test
  @DisplayName("complement, gunion and intersection print exactly as the C")
  void shouldMatchOracleWhenSetOperationsPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.complement(Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 1L), 0L, 0L, 1L), 1, ps)))
        .isEqualTo(Oracle.inc2("complement_dir"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.gunion(Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 1L), Basic.board(3L, 0L, 0L, 0L, -2L, 0L, 1L), 0L, 1L), 0, ps)))
        .isEqualTo(Oracle.inc2("gunion_dir"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.intersection(Basic.board(3L, 3L, 0L, 0L, -1L, 0L, 0L), Basic.board(3L, 3L, 0L, 0L, -2L, 0L, 0L), 0L, 0L), 4, ps)))
        .isEqualTo(Oracle.inc2("intersection"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.intersection(Basic.board(3L, 0L, 0L, 0L, -2L, 0L, 1L), Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 1L), 1L, 1L), 1, ps)))
        .isEqualTo(Oracle.inc2("intersection_dir"));
  }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :lib:test --tests 'com.robsartin.jsgb.basic.BasicSetOpsTest'` — Expected: compilation FAILS (`complement` not found).

- [ ] **Step 3: Translate sections 74–86**

- [ ] **Step 4: Run to verify pass, gate, commit**

Run: `./gradlew spotlessApply && ./gradlew check` — Expected: BUILD SUCCESSFUL. If a unit test's arc count disagrees with what the code produces while the oracle cases pass, the C governs: re-derive the count from the C and report the correction.

```bash
git add lib/src/main/java/com/robsartin/jsgb/basic lib/src/test/java/com/robsartin/jsgb/basic demos/src/test
git commit -m "Port gb_basic's complement, gunion and intersection

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 8: `Basic` part 4 — `lines`, `product`

**Files:**
- Modify: `lib/src/main/java/com/robsartin/jsgb/basic/Basic.java`
- Create: `lib/src/test/java/com/robsartin/jsgb/basic/BasicLinesProductTest.java`
- Modify: `demos/src/test/java/com/robsartin/jsgb/demo/OracleInc2Test.java` (append)

**Interfaces:**
- Produces on `Basic`: `public static Graph lines(Graph g, long directed)`, `public static Graph product(Graph g, Graph gg, long type, long directed)`; constants `CARTESIAN = 0`, `DIRECT = 1`, `STRONG = 2`.

**Source:** `gb_basic.w` sections 87–99 (tangled lines 1836–2030). Notes:
- `lines` (sections 88–93): `m = directed != 0 ? g.m : g.m / 2`; `newGraph(m)`; id `Gb.makeCompoundId(newg, "lines(", g, directed != 0 ? ",1)" : ",0)")`. Section 89 walks `g`'s vertices **from the last (`g.n - 1`) down to 0**; for each arc `a` of `v` with `vv = a.tip`: undirected → skip if `vv.index < v.index`; if `vv` is not one of `g`'s first `n` vertices (`vv.index >= g.n || g.vertices[vv.index] != vv`) go to `near_panic`. Section 91 fills the new vertex `u`: `u.u.V(v); u.v.V(vv); u.w.A(a)`; undirected: if `u` is beyond the `m` new vertices or `a.mate.tip != v` → `near_panic`; `if (v == vv && Gb.isFirstOfSelfLoop(a)) a = a.mate; else a.mate.tip = u;` (the C temporarily points the mate's tip at the new vertex, and section 88 restores it); name `prefix(v.name, (BUF_SIZE - 3) / 2) + (directed != 0 ? "->" : "--") + prefix(vv.name, BUF_SIZE / 2 - 1)` — note the C writes `"-%c"` so the separator is `-` followed by `>` or `-`. `map` (`z.V`) chains: `if (!mapped) { u.map = v.map; v.map = u; mapped = true; }`. After the walk `u` must equal `m` new vertices, else `near_panic`. Section 92 (directed): for each new `u`: `v = u.v.V(); if (v.arcs != null) { v = v.map; do { newArc(u, v, 1); v = next vertex in the new graph's array (index + 1); } while (v.u.V() == u.v.V()); }` — `v++` steps through `newg.vertices`; the loop's guard reads the next new vertex's `u` slot, which for the vertex after the last real one is an extra vertex with a null slot, so the loop always terminates as in C. Section 93 (undirected): as written in the C with `vv < u` → `vv.index < u.index` (both in `newg`), `vv >= new_graph->vertices` → `vv` is one of `newg`'s vertices (identity check via `newg.vertices[vv.index] == vv` guarded by index range), `vv >= v && vv < g->vertices + g->n` → `vv` is a vertex of `g` with `vv.index >= v.index`; the final loop `for (vv = v.map; vv.u.V() == v; vv = next in newg)`. Section 88 (restore): for each new `u` in order: `if (u.u.V() != v) { v = u.u.V(); v.map = u.map; u.map = null; } if (directed == 0) u.w.A().mate.tip = v;`. `near_panic` (section 90): `m = number of new vertices filled so far`, run section 88 over those, `Gb.recycle(newg)`, panic `INVALID_OPERAND`. `utilTypes` is left at default: the C never sets it for `lines` even though `u`, `v`, `w` hold data — the oracle prints `ZZZZZZZZZZZZZZ` and `oracle_lines.gb` shows `save` reporting "data suppressed by Z format".
- `product` (sections 96–99): `float` overflow guard (`VERY_BAD_SPECS`); `n = g.n * gg.n`; names `prefix(v.name, BUF_SIZE / 2 - 1) + "," + prefix(vv.name, (BUF_SIZE - 1) / 2)` for `v` over `g` (outer) and `vv` over `gg` (inner), i.e. new vertex `i * gg.n + j` is `(g.vertices[i], gg.vertices[j])`; id `Gb.makeDoubleCompoundId(newg, "product(", g, ",", gg, "," + ((type != 0 ? 2 : 0) - (type & 1)) + "," + flag(directed) + ")")` — the printed type is `0` for cartesian, `1` for direct, `2` for strong. Section 97 (cartesian part, when `(type & 1) == 0`): for each `u` in `gg` and arc `a` (undirected: skip `u.index > v.index`; `if (u == v && isFirstOfSelfLoop(a)) a = a.mate`), add `newArc`/`newEdge` between `newg.vertices[u.index + k * gg.n]` and `newg.vertices[v.index + k * gg.n]` for `k = 0 .. g.n - 1` with `a.len`; then section 98 for each `u` in `g` (index `i`) and arc to `v`: same skip rules, arcs between `newg.vertices[i * gg.n + j]` and `newg.vertices[v.index * gg.n + j]` for `j = 0 .. gg.n - 1`. Section 99 (direct part, when `type != 0`): for `uu` in `g` (index `i`), arc `a` to `vv` (skip rules on `uu`/`vv`), for `u` in `gg`, arc `aa` to `v`: `length = min(a.len, aa.len)`; arc/edge from `newg.vertices[i * gg.n + u.index]` to `newg.vertices[vv.index * gg.n + v.index]`.

- [ ] **Step 1: Failing tests**

`BasicLinesProductTest.java`:
```java
package com.robsartin.jsgb.basic;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BasicLinesProductTest {

  @Test
  @DisplayName("lines of a 3x3 grid has 12 line vertices and restores the original arcs")
  void shouldBuildLineGraphWhenGridGiven() {
    Graph grid = Basic.board(3L, 3L, 0L, 0L, 1L, 0L, 0L);
    Graph g = Basic.lines(grid, 0L);
    assertThat(g.id).isEqualTo("lines(board(3,3,0,0,1,0,0),0)");
    assertThat(g.n).isEqualTo(12L);
    assertThat(g.m).isEqualTo(44L);
    assertThat(g.vertices[5].name).isEqualTo("1.0--2.0");
    assertThat(g.vertices[5].u.V().name).isEqualTo("1.0");
    assertThat(g.vertices[5].w.A().mate.tip).isSameAs(g.vertices[5].u.V());
    for (int i = 0; i < grid.n; i++) {
      assertThat(grid.vertices[i].z.ref).isNull(); // map cleared
    }
  }

  @Test
  @DisplayName("lines of a directed graph uses -> in names")
  void shouldUseArrowWhenDirected() {
    Graph g = Basic.lines(Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 1L), 1L);
    assertThat(g.n).isEqualTo(2L);
    assertThat(g.vertices[0].name).isEqualTo("1->2");
    assertThat(g.vertices[1].name).isEqualTo("0->1");
    assertThat(g.vertices[1].arcs.tip).isSameAs(g.vertices[0]);
  }

  @Test
  @DisplayName("product prints the product type as 0, 1 or 2 in its id")
  void shouldEncodeTypeWhenProductBuilt() {
    Graph a = Basic.board(2L, 0L, 0L, 0L, 1L, 0L, 0L);
    Graph b = Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 0L);
    Graph g = Basic.product(a, b, Basic.STRONG, 0L);
    assertThat(g.id).isEqualTo("product(board(2,0,0,0,1,0,0),board(3,0,0,0,1,0,0),2,0)");
    assertThat(g.n).isEqualTo(6L);
    assertThat(g.m).isEqualTo(22L);
    assertThat(g.vertices[4].name).isEqualTo("1,1");
    assertThat(Basic.product(null, b, 0L, 0L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.MISSING_OPERAND);
  }
}
```

Append to `OracleInc2Test`:
```java
  @Test
  @DisplayName("lines and product print exactly as the C")
  void shouldMatchOracleWhenLinesAndProductsPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.lines(Basic.board(3L, 3L, 0L, 0L, 1L, 0L, 0L), 0L), 5, ps)))
        .isEqualTo(Oracle.inc2("lines"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.lines(Basic.board(3L, 0L, 0L, 0L, -2L, 0L, 1L), 1L), 1, ps)))
        .isEqualTo(Oracle.inc2("lines_dir"));
    for (String[] c : new String[][] {{"product_cart", "0"}, {"product_direct", "1"}, {"product_strong", "2"}}) {
      long type = Long.parseLong(c[1]);
      assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.product(Basic.board(2L, 0L, 0L, 0L, 1L, 0L, 0L), Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 0L), type, 0L), 1, ps)))
          .as(c[0]).isEqualTo(Oracle.inc2(c[0]));
    }
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.product(Basic.board(2L, 0L, 0L, 0L, 1L, 0L, 1L), Basic.board(2L, 0L, 0L, 0L, -2L, 0L, 1L), 1L, 1L), 0, ps)))
        .isEqualTo(Oracle.inc2("product_dir"));
  }
```

- [ ] **Step 2: Run to verify failure** — `./gradlew :lib:test --tests 'com.robsartin.jsgb.basic.BasicLinesProductTest'`; Expected: compilation FAILS.

- [ ] **Step 3: Translate sections 87–99**

- [ ] **Step 4: Run to verify pass, gate, commit**

Run: `./gradlew spotlessApply && ./gradlew check` — Expected: BUILD SUCCESSFUL. The `lines` oracle (`"1.0--2.0"` at V5 with arcs to `"0.0--1.0"`, `"1.0--1.1"`, `"2.0--2.1"`) pins both the descending vertex walk and the arc order of section 93.

```bash
git add lib/src/main/java/com/robsartin/jsgb/basic lib/src/test/java/com/robsartin/jsgb/basic demos/src/test
git commit -m "Port gb_basic's lines and product

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 9: `Basic` part 5 — `induced`, `bi_complete`, `wheel`, and the convenience shortcuts

**Files:**
- Modify: `lib/src/main/java/com/robsartin/jsgb/basic/Basic.java`
- Create: `lib/src/test/java/com/robsartin/jsgb/basic/BasicInducedTest.java`
- Modify: `demos/src/test/java/com/robsartin/jsgb/demo/OracleInc2Test.java` (append)

**Interfaces:**
- Produces on `Basic`: `public static Graph induced(Graph g, String description, long self, long multi, long directed)`, `public static Graph biComplete(long n1, long n2, long directed)`, `public static Graph wheel(long n, long n1, long directed)`, and the `gb_basic.h` shortcuts as static methods: `complete(n)` = `board(n,0,0,0,-1,0,0)`, `transitive(n)` = `board(n,0,0,0,-1,0,1)`, `empty(n)` = `board(n,0,0,0,2,0,0)`, `circuit(n)` = `board(n,0,0,0,1,1,0)`, `cycle(n)` = `board(n,0,0,0,1,1,1)`, `disjointSubsets(n, k)` = `subsets(k,1,1-n,0,0,0,1,0)`, `petersen()` = `disjointSubsets(5,2)`, `allPerms(n, directed)` = `perms(1-n,0,0,0,0,0,directed)`, `allParts(n, directed)` = `parts(n,0,0,directed)`, `allTrees(n, directed)` = `binary(n,0,directed)` (these are the `gb_basic.h` macros; the header is the authority if anything here disagrees). Public constant `IND_GRAPH = 1000000000`; the `ind` slot is `z.I` and `subst` is `y.G` (document them: callers set `v.z.I` and `v.y.G(graph)` before calling `induced`).

**Source:** `gb_basic.w` sections 101–114 (tangled lines 2170–2113 end). Notes:
- Section 107 counts `n`: for each `v` of `g`: `ind = v.z.I`; `ind > 0`: `if (n > IND_GRAPH) panic VERY_BAD_SPECS; if (ind >= IND_GRAPH) { if (v.y.G() == null) panic MISSING_OPERAND + 1; n += v.subst.n } else n += ind`; `ind < -nn` → `nn = -ind`. Then `n > IND_GRAPH || nn > IND_GRAPH` → `VERY_BAD_SPECS + 1`; `n += nn`.
- Section 108 names: first `nn` vertices are `"-1".."-nn"` with `mult = -k`; then per `v`: `k = ind`; `k < 0` → `v.map = newg.vertices[-k - 1]`; `k > 0` → `u.mult = k; v.map = u;` then `k <= 2`: copy name, and for `k == 2` a second vertex named `name + "'"`; `k >= IND_GRAPH`: section 114 (substitute the graph `gg = v.subst`: names `prefix(v.name, BUF_SIZE / 2 - 1) + ":" + prefix(vv.name, (BUF_SIZE - 1) / 2)`, and copies `gg`'s arcs among the new vertices with `uu = newg.vertices[uBase + vvv.index]` where `uBase` is the index of the first substituted vertex; self-loop skip when `self == 0`; the `!multi` merge of section 113 (`b = uu.tlen; if (a.len < b.len) { b.len = a.len; if (directed == 0) b.mate.len = a.len; } continue;`); undirected: skip `vvv.index < vv.index`, `if (vvv == vv && isFirstOfSelfLoop(a)) a = a.mate`, `newEdge(u, uu, a.len)`; directed `newArc(u, uu, a.len)`; then `uu.tmp = u; uu.tlen = (directed != 0 || u.index <= uu.index) ? u.arcs : uu.arcs;`); otherwise `k` copies named `prefix(v.name, BUF_SIZE - 12) + ":" + j`.
- Id: `Gb.makeCompoundId(newg, "induced(", g, "," + (description == null ? "" : description) + "," + flag(self) + "," + flag(multi) + "," + flag(directed) + ")")`.
- Section 110–113: the main arc copy; `u = v.map` (null → skip); `k = u.mult` (`< 0` → 1; `>= IND_GRAPH` → `v.subst.n`); for each of the `k` new vertices `u` (stepping through `newg.vertices` by index): if `multi == 0` section 111 marks `u`'s existing arcs: `a.tip.tmp = u; a.tip.tlen = (directed != 0 || a.tip.index > u.index || isFirstOfSelfLoop(a)) ? a : a.mate;`. Then for each arc `a` of `v` to `vv`: `uu = vv.map` (null → skip); `j = uu.mult` normalised like `k`; undirected: skip `vv.index < v.index`; `if (vv == v) { if (isFirstOfSelfLoop(a)) a = a.mate; j = k; uu = u; }` — careful: `j = k, uu = u` must use the **current** `u` of the outer stepping loop; then section 112 over `j` vertices `uu` (stepping by index): `if (u == uu && self == 0) continue; if (uu.tmp == u && multi == 0) section 113 (continue); newArc/newEdge(u, uu, a.len); uu.tmp = u; uu.tlen = (directed != 0 || u.index <= uu.index) ? u.arcs : uu.arcs;`.
- Section 109 cleanup: for each `v` of `g` with `v.map != null`: `v.ind = v.map.mult` (writes `z.I`, which aliases `map`'s slot `z.V` in C: assigning `ind` overwrites `map`; in Java set `v.z.I = v.map.mult` **and** `v.z.ref = null` to mirror the union), then clear `u.I`, `v.I`, `z.I` (and `refs`) on all `n` new vertices.
- `biComplete`: `board(2,0,0,0,1,0,directed)`, set `vertices[0].z.I = n1`, `vertices[1].z.I = n2`, `induced(newg, null, 0, 0, directed)`, then id `"bi_complete(" + unsigned(n1) + "," + unsigned(n2) + "," + flag(directed) + ")"` and `Gb.markBipartite(g, n1)`. `wheel`: `board(2,...)`, `vertices[0].z.I = n1`, `vertices[1].z.I = IND_GRAPH`, `vertices[1].y.G(board(n,0,0,0,1,1,directed))`, `induced(...)`, id `"wheel(" + unsigned(n) + "," + unsigned(n1) + "," + flag(directed) + ")"`. Both return null if `board` or `induced` returned null.

- [ ] **Step 1: Failing tests**

`BasicInducedTest.java`:
```java
package com.robsartin.jsgb.basic;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BasicInducedTest {

  @Test
  @DisplayName("bi_complete(2,3) is K(2,3) with the first part marked")
  void shouldBuildCompleteBipartiteWhenBiCompleteCalled() {
    Graph g = Basic.biComplete(2L, 3L, 0L);
    assertThat(g.id).isEqualTo("bi_complete(2,3,0)");
    assertThat(g.n).isEqualTo(5L);
    assertThat(g.m).isEqualTo(12L);
    assertThat(g.uu.I).isEqualTo(2L);
    assertThat(g.utilTypes).isEqualTo("ZZZZZZZZIZZZZZ");
    assertThat(g.vertices[0].name).isEqualTo("0");
    assertThat(g.vertices[1].name).isEqualTo("0'");
    assertThat(g.vertices[2].name).isEqualTo("1:0");
  }

  @Test
  @DisplayName("wheel(5,1) has a hub joined to a 5-cycle")
  void shouldBuildWheelWhenWheelCalled() {
    Graph g = Basic.wheel(5L, 1L, 0L);
    assertThat(g.id).isEqualTo("wheel(5,1,0)");
    assertThat(g.n).isEqualTo(6L);
    assertThat(g.m).isEqualTo(20L);
  }

  @Test
  @DisplayName("induced with a missing substitute graph panics")
  void shouldPanicWhenSubstituteMissing() {
    Graph g = Basic.board(2L, 0L, 0L, 0L, 1L, 0L, 0L);
    g.vertices[0].z.I = Basic.IND_GRAPH;
    assertThat(Basic.induced(g, null, 0L, 0L, 0L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.MISSING_OPERAND + 1);
  }

  @Test
  @DisplayName("the header shortcuts expand to the documented board and subsets calls")
  void shouldExpandShortcutsWhenCalled() {
    assertThat(Basic.complete(4L).id).isEqualTo("board(4,0,0,0,-1,0,0)");
    assertThat(Basic.circuit(5L).id).isEqualTo("board(5,0,0,0,1,1,0)");
    assertThat(Basic.petersen().n).isEqualTo(10L);
    assertThat(Basic.petersen().m).isEqualTo(30L);
  }
}
```

Append to `OracleInc2Test`:
```java
  @Test
  @DisplayName("induced, bi_complete and wheel print exactly as the C")
  void shouldMatchOracleWhenInducedGraphsPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.biComplete(2L, 3L, 0L), 1, ps)))
        .isEqualTo(Oracle.inc2("bi_complete"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.wheel(5L, 1L, 0L), 0, ps)))
        .isEqualTo(Oracle.inc2("wheel"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.wheel(4L, 2L, 1L), 5, ps)))
        .isEqualTo(Oracle.inc2("wheel_dir"));
    com.robsartin.jsgb.graph.Graph g = Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 0L);
    g.vertices[0].z.I = 2;
    g.vertices[1].z.I = -1;
    g.vertices[2].z.I = -2;
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.induced(g, "x", 1L, 1L, 0L), 0, ps)))
        .isEqualTo(Oracle.inc2("induced_neg"));
    com.robsartin.jsgb.graph.Graph h = Basic.board(2L, 0L, 0L, 0L, 1L, 0L, 0L);
    h.vertices[0].z.I = Basic.IND_GRAPH;
    h.vertices[0].y.G(Basic.board(3L, 0L, 0L, 0L, 1L, 1L, 0L));
    h.vertices[1].z.I = 1;
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.induced(h, null, 0L, 0L, 0L), 1, ps)))
        .isEqualTo(Oracle.inc2("induced_subst"));
  }
```

- [ ] **Step 2: Run to verify failure** — `./gradlew :lib:test --tests 'com.robsartin.jsgb.basic.BasicInducedTest'`; Expected: compilation FAILS.

- [ ] **Step 3: Translate sections 101–114 and the header shortcuts**

Read `gb_basic.h` (tangled) for the exact shortcut definitions. Finish the `Basic` class Javadoc with the full slot table now that every function is present.

- [ ] **Step 4: Run to verify pass, gate, commit**

Run: `./gradlew spotlessApply && ./gradlew check` — Expected: BUILD SUCCESSFUL.

```bash
git add lib/src/main/java/com/robsartin/jsgb/basic lib/src/test/java/com/robsartin/jsgb/basic demos/src/test
git commit -m "Port gb_basic's induced, bi_complete, wheel and the header shortcuts

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 10: `Save` (gb_save): `saveGraph` and `restoreGraph`

**Files:**
- Create: `lib/src/main/java/com/robsartin/jsgb/save/Save.java`, `package-info.java`
- Create: `lib/src/test/java/com/robsartin/jsgb/save/SaveTest.java`
- Modify: `demos/src/test/java/com/robsartin/jsgb/demo/TestSampleTest.java` (append the `test.gb` diff and stanza 3), `OracleInc2Test.java` (append the two restore cases)

**Interfaces:**
- Produces: `public static long saveGraph(Graph g, String f)` (returns the anomaly bitmask, `-1` for a null or recycled graph, `-2` if the file cannot be written), `public static Graph restoreGraph(String f)` (null with `Gb.panicCode` set on any failure), anomaly constants `BAD_TYPE_CODE = 0x1, STRING_TOO_LONG = 0x2, ADDR_NOT_IN_DATA_AREA = 0x4, ADDR_IN_MIXED_BLOCK = 0x8, BAD_STRING_CHAR = 0x10, IGNORED_DATA = 0x20`, and `MAX_SV_STRING = 4095`, `MAX_SV_ID = 154`.
- Slot convention it defines for everyone: a `V`-typed slot whose `ref` is null and whose `I` is 1 is the boolean "ONE" of `gb_gates`; `save` writes it as `1` and `restore` reads `1` back into `I`.

**Source:** `gb_save.w` sections 4–46 (tangled `gb_save.c`, 758 lines; all of it was read for this plan). The C classifies memory blocks by walking pointers; the Java does the same over the graph's registered blocks (`g.vertices`, `g.extraVertexBlocks()`, `g.arcBlocks()`), which are exactly the blocks of `g->data` that hold vertices and arcs, in allocation order — the order the C ends up with because `calloc` returns increasing addresses on every platform `test.correct` was produced on (ADR 0017, spec Risks).

The complete Java follows. Transcribe it; the comments name the C sections.

```java
package com.robsartin.jsgb.save;

import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Util;
import com.robsartin.jsgb.graph.Vertex;
import com.robsartin.jsgb.io.GbIo;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of {@code gb_save}: writes a graph to a portable ASCII file and reads one back. The file
 * numbers vertices and arcs by their position in the graph's storage blocks, so a graph restored
 * from a file has the same structure, though string identity is not preserved.
 *
 * <p>Boolean vertex slots: a {@code V}-typed slot with {@code ref == null} and {@code I == 1}
 * (the {@code gb_gates} constant ONE) is written as {@code 1} and read back the same way.
 */
public final class Save {

  public static final long BAD_TYPE_CODE = 0x1;
  public static final long STRING_TOO_LONG = 0x2;
  public static final long ADDR_NOT_IN_DATA_AREA = 0x4;
  public static final long ADDR_IN_MIXED_BLOCK = 0x8;
  public static final long BAD_STRING_CHAR = 0x10;
  public static final long IGNORED_DATA = 0x20;

  /** Longest string {@code save_graph} writes without truncation. */
  public static final int MAX_SV_STRING = 4095;

  /** Longest graph id {@code save_graph} writes without truncation. */
  public static final int MAX_SV_ID = 154;

  private static final int UNK = 0;
  private static final int ARK = 1;
  private static final int VRT = 2;
  private static final int MXT = 3;

  /** A storage block of the graph being saved (the C {@code block_rep}). */
  private static final class Block {
    final Object[] slots; // Vertex[] or Arc[]
    int cat = UNK;
    long offset;
    boolean expl;

    Block(Object[] slots) {
      this.slots = slots;
    }
  }

  /** Where an object lives: its block and slot index. */
  private record Where(Block block, int index) {}

  // ---- state shared by save_graph's helpers (C statics) ----------------------------------
  private static long anomalies;
  private static List<Block> blocks;
  private static IdentityHashMap<Object, Where> where;
  private static boolean commaExpected;
  private static final StringBuilder buffer = new StringBuilder(); // the current output line
  private static String itemBuf; // the C item_buf
  private static long magic;
  private static OutputStream saveFile;

  // ---- state shared by restore_graph's helpers ---------------------------------------------
  private static Vertex[] verts;
  private static int lastVert;
  private static Arc[] arcs;
  private static int lastArc;

  private Save() {}

  // ======================================================================================
  // save_graph
  // ======================================================================================

  /** {@code save_graph(g, f)}: writes {@code g} to file {@code f}; returns the anomaly bits. */
  public static long saveGraph(Graph g, String f) {
    if (g == null || g.vertices == null) {
      return -1;
    }
    anomalies = 0;
    collectBlocks(g); // section 23
    classifyAll(g); // section 27
    long[] counts = assignOffsets(g); // sections 32, 33
    long n = counts[0];
    long m = counts[1];
    try (OutputStream out = Files.newOutputStream(Path.of(f))) {
      saveFile = out;
      buffer.setLength(0);
      magic = 0;
      // section 38: header line, not checksummed
      StringBuilder head = new StringBuilder("* GraphBase graph (util_types ");
      for (int i = 0; i < 14; i++) {
        char c = g.utilTypes.charAt(i);
        head.append(c == 'Z' || c == 'I' || c == 'V' || c == 'S' || c == 'A' ? c : 'Z');
      }
      head.append(',').append(n).append("V,").append(m).append("A)\n");
      write(head.toString());
      // section 41: the graph record
      prepareString(g.id);
      if (g.id.length() > MAX_SV_ID) {
        itemBuf = itemBuf.substring(0, MAX_SV_ID + 1) + "\"";
        anomalies |= STRING_TOO_LONG;
      }
      moveItem();
      commaExpected = true;
      translate(g.n, null, 'I');
      translate(g.m, null, 'I');
      translate(g.uu, g.utilTypes.charAt(8));
      translate(g.vv, g.utilTypes.charAt(9));
      translate(g.ww, g.utilTypes.charAt(10));
      translate(g.xx, g.utilTypes.charAt(11));
      translate(g.yy, g.utilTypes.charAt(12));
      translate(g.zz, g.utilTypes.charAt(13));
      flushout();
      // section 42/43: vertices, the main block first, then the other vertex blocks
      write("* Vertices\n");
      for (Block b : blocks) {
        if (b.cat == VRT && b.offset == 0) {
          writeVertices(g, b);
        }
      }
      for (Block b : blocks) {
        if (b.cat == VRT && b.offset != 0) {
          writeVertices(g, b);
        }
      }
      // section 44: arcs
      write("* Arcs\n");
      for (Block b : blocks) {
        if (b.cat == ARK) {
          for (Object o : b.slots) {
            Arc a = (Arc) o;
            commaExpected = false;
            translate(0, a.tip, 'V');
            translate(0, a.next, 'A');
            translate(a.len, null, 'I');
            translate(a.a, g.utilTypes.charAt(6));
            translate(a.b, g.utilTypes.charAt(7));
            flushout();
          }
        }
      }
      write("* Checksum " + magic + "\n"); // section 45
      writeWarnings(); // section 46
    } catch (IOException e) {
      return -2;
    } finally {
      saveFile = null;
      blocks = null;
      where = null;
    }
    return anomalies;
  }

  private static void writeVertices(Graph g, Block b) throws IOException {
    for (Object o : b.slots) {
      Vertex v = (Vertex) o;
      commaExpected = false;
      translate(0, v.name, 'S');
      translate(0, v.arcs, 'A');
      translate(v.u, g.utilTypes.charAt(0));
      translate(v.v, g.utilTypes.charAt(1));
      translate(v.w, g.utilTypes.charAt(2));
      translate(v.x, g.utilTypes.charAt(3));
      translate(v.y, g.utilTypes.charAt(4));
      translate(v.z, g.utilTypes.charAt(5));
      flushout();
    }
  }

  /** Section 23: the graph's vertex and arc blocks in allocation order, indexed by object. */
  private static void collectBlocks(Graph g) {
    blocks = new ArrayList<>();
    where = new IdentityHashMap<>();
    register(new Block(g.vertices));
    for (Vertex[] vb : g.extraVertexBlocks()) {
      register(new Block(vb));
    }
    for (Arc[] ab : g.arcBlocks()) {
      register(new Block(ab));
    }
  }

  private static void register(Block b) {
    blocks.add(b);
    for (int i = 0; i < b.slots.length; i++) {
      where.put(b.slots[i], new Where(b, i));
    }
  }

  /** Section 27: mark blocks reachable through typed pointers, transitively. */
  private static void classifyAll(Graph g) {
    if (g.vertices.length > 0) {
      lookup(g.vertices[0], 0, 'V'); // lookup(g->vertices, 'V')
    }
    lookup(g.uu, g.utilTypes.charAt(8));
    lookup(g.vv, g.utilTypes.charAt(9));
    lookup(g.ww, g.utilTypes.charAt(10));
    lookup(g.xx, g.utilTypes.charAt(11));
    lookup(g.yy, g.utilTypes.charAt(12));
    lookup(g.zz, g.utilTypes.charAt(13));
    boolean activity;
    do {
      activity = false;
      for (Block b : blocks) {
        if (b.cat == VRT && !b.expl) {
          for (Object o : b.slots) { // section 28
            if (b.cat != VRT) {
              break;
            }
            Vertex v = (Vertex) o;
            lookup(v.arcs, 0, 'A');
            lookup(v.u, g.utilTypes.charAt(0));
            lookup(v.v, g.utilTypes.charAt(1));
            lookup(v.w, g.utilTypes.charAt(2));
            lookup(v.x, g.utilTypes.charAt(3));
            lookup(v.y, g.utilTypes.charAt(4));
            lookup(v.z, g.utilTypes.charAt(5));
          }
        } else if (b.cat == ARK && !b.expl) {
          for (Object o : b.slots) { // section 29
            if (b.cat != ARK) {
              break;
            }
            Arc a = (Arc) o;
            lookup(a.tip, 0, 'V');
            lookup(a.next, 0, 'A');
            lookup(a.a, g.utilTypes.charAt(6));
            lookup(a.b, g.utilTypes.charAt(7));
          }
        } else {
          continue;
        }
        b.expl = true;
        activity = true;
      }
    } while (activity);
  }

  private static void lookup(Util u, char t) {
    lookup(u.ref, u.I, t);
  }

  /** Sections 25/26: classify the block that {@code ref} points into, if any. */
  private static void lookup(Object ref, long i, char t) {
    int tcat;
    switch (t) {
      case 'V' -> {
        if (ref == null && i == 1) {
          return; // the boolean ONE
        }
        tcat = VRT;
      }
      case 'A' -> tcat = ARK;
      default -> {
        return;
      }
    }
    if (ref == null) {
      return;
    }
    Where w = where.get(ref);
    if (w == null) {
      return; // not in the data area; translate() reports it
    }
    if (w.block().cat == UNK) {
      w.block().cat = tcat;
    } else if (w.block().cat != tcat) {
      w.block().cat = MXT;
    }
  }

  /** Sections 32/33: number the vertices (main block first) and the arcs; returns {n, m}. */
  private static long[] assignOffsets(Graph g) {
    long n = g.vertices.length;
    long m = 0;
    for (Block b : blocks) {
      if (b.cat == VRT) {
        if (b.slots != g.vertices) {
          b.offset = n;
          n += b.slots.length;
        }
      } else if (b.cat == ARK) {
        b.offset = m;
        m += b.slots.length;
      }
    }
    return new long[] {n, m};
  }

  private static void translate(Util u, char t) throws IOException {
    translate(u.I, u.ref, t);
  }

  /** Sections 39/40: append one field to the current line. */
  private static void translate(long i, Object ref, char t) throws IOException {
    if (commaExpected) {
      buffer.append(',');
    } else {
      commaExpected = true;
    }
    int tcat;
    switch (t) {
      case 'I' -> {
        itemBuf = Long.toString(i);
        moveItem();
        return;
      }
      case 'S' -> {
        prepareString((String) ref);
        moveItem();
        return;
      }
      case 'V' -> {
        if (ref == null && i == 1) {
          itemBuf = "1";
          moveItem();
          return;
        }
        tcat = VRT;
      }
      case 'A' -> tcat = ARK;
      case 'Z' -> {
        buffer.setLength(buffer.length() - 1); // the C's buf_ptr--
        if (i != 0 || ref != null) {
          anomalies |= IGNORED_DATA;
        }
        return;
      }
      default -> {
        anomalies |= BAD_TYPE_CODE;
        buffer.setLength(buffer.length() - 1);
        if (i != 0 || ref != null) {
          anomalies |= IGNORED_DATA;
        }
        return;
      }
    }
    itemBuf = "0";
    if (ref != null) {
      Where w = where.get(ref);
      if (w == null) {
        anomalies |= ADDR_NOT_IN_DATA_AREA;
      } else if (w.block().cat != tcat) {
        anomalies |= ADDR_IN_MIXED_BLOCK;
      } else {
        itemBuf = t + Long.toString(w.block().offset + w.index());
      }
    }
    moveItem();
  }

  /** Section 36: quote a string, replacing characters the file format cannot hold. */
  private static void prepareString(String s) {
    StringBuilder b = new StringBuilder("\"");
    if (s != null) {
      byte[] bytes = s.getBytes(StandardCharsets.ISO_8859_1);
      int k = 0;
      for (; k < bytes.length && k < MAX_SV_STRING; k++) {
        int c = bytes[k] & 0xff;
        if (c == '"' || c == '\n' || c == '\\' || GbIo.imapOrd(c) == GbIo.UNEXPECTED_CHAR) {
          anomalies |= BAD_STRING_CHAR;
          b.append('?');
        } else {
          b.append((char) c);
        }
      }
      if (k < bytes.length) {
        anomalies |= STRING_TOO_LONG;
      }
    }
    b.append('"');
    itemBuf = b.toString();
  }

  /** Section 37: append {@code itemBuf} to the line, wrapping with a backslash past column 78. */
  private static void moveItem() throws IOException {
    int l = itemBuf.length();
    if (buffer.length() + l > 78) {
      if (l <= 78) {
        flushout();
      } else {
        if (buffer.length() > 77) {
          flushout();
        }
        int p = 0;
        do {
          while (buffer.length() < 78) {
            buffer.append(itemBuf.charAt(p++));
            l--;
          }
          buffer.append('\\');
          flushout();
        } while (l > 78);
        buffer.append(itemBuf, p, itemBuf.length());
        return;
      }
    }
    buffer.append(itemBuf);
  }

  /** Section 35: end the line, fold it into the checksum, write it. */
  private static void flushout() throws IOException {
    buffer.append('\n');
    String line = buffer.toString();
    magic = GbIo.newChecksum(line.getBytes(StandardCharsets.ISO_8859_1), magic);
    write(line);
    buffer.setLength(0);
  }

  private static void write(String s) throws IOException {
    saveFile.write(s.getBytes(StandardCharsets.ISO_8859_1));
  }

  /** Section 46. */
  private static void writeWarnings() throws IOException {
    if (anomalies == 0) {
      return;
    }
    write("> WARNING: I had trouble making this file from the given graph!\n");
    if ((anomalies & BAD_TYPE_CODE) != 0) {
      write(">> The original util_types had to be corrected.\n");
    }
    if ((anomalies & IGNORED_DATA) != 0) {
      write(">> Some data suppressed by Z format was actually nonzero.\n");
    }
    if ((anomalies & STRING_TOO_LONG) != 0) {
      write(">> At least one long string had to be truncated.\n");
    }
    if ((anomalies & BAD_STRING_CHAR) != 0) {
      write(">> At least one string character had to be changed to '?'.\n");
    }
    if ((anomalies & ADDR_NOT_IN_DATA_AREA) != 0) {
      write(">> At least one pointer led out of the data area.\n");
    }
    if ((anomalies & ADDR_IN_MIXED_BLOCK) != 0) {
      write(">> At least one data block had an illegal mixture of records.\n");
    }
    if ((anomalies & (ADDR_NOT_IN_DATA_AREA + ADDR_IN_MIXED_BLOCK)) != 0) {
      write(">>  (Pointers to improper data have been changed to 0.)\n");
    }
    write("> You should be able to read this file with restore_graph,\n");
    write("> but the graph you get won't be exactly like the original.\n");
  }

  // ======================================================================================
  // restore_graph
  // ======================================================================================

  private static final Pattern HEADER =
      Pattern.compile("^\\* GraphBase graph \\(util_types ([ZIVSA]{1,14}),\\s*([-+]?\\d+)V,\\s*([-+]?\\d+)A");
  private static final Pattern CHECKSUM = Pattern.compile("^\\* Checksum\\s*([-+]?\\d+)");

  /** {@code restore_graph(f)}: reads a graph written by {@link #saveGraph}; null on failure. */
  public static Graph restoreGraph(String f) {
    Graph g = null;
    try {
      // section 5
      GbIo.rawOpen(f);
      if (GbIo.ioErrors != 0) {
        return panic(g, Gb.EARLY_DATA_FAULT);
      }
      String types;
      int n;
      int m;
      while (true) {
        String s = GbIo.string(')');
        Matcher hm = HEADER.matcher(s);
        if (hm.find() && hm.group(1).length() == 14) {
          types = hm.group(1);
          n = Integer.parseInt(hm.group(2));
          m = Integer.parseInt(hm.group(3));
          break;
        }
        if (s.isEmpty() || s.charAt(0) != '*') {
          return panic(g, Gb.SYNTAX_ERROR);
        }
      }
      // section 6
      g = Gb.newGraph(0L);
      if (g == null) {
        return panic(null, Gb.NO_ROOM);
      }
      Gb.restoreStorage(g, n, m);
      verts = g.vertices;
      lastVert = n;
      arcs = g.arcBlocks().get(0);
      lastArc = m;
      g.utilTypes = types;
      GbIo.newline();
      if (GbIo.ch() != '"') {
        return panic(g, Gb.SYNTAX_ERROR + 1);
      }
      String id = GbIo.string('"');
      if (id.length() >= 2 && id.endsWith("\\\n")) {
        GbIo.newline();
        id = id.substring(0, id.length() - 2) + GbIo.string('"');
      }
      g.id = id;
      if (GbIo.ch() != '"') {
        return panic(g, Gb.SYNTAX_ERROR + 2);
      }
      // section 15
      Gb.panicCode = 0;
      commaExpected = true;
      Util tmp = new Util();
      if (fillField(tmp, 'I') != 0) {
        return sorry(g);
      }
      g.n = tmp.I;
      if (fillField(tmp, 'I') != 0) {
        return sorry(g);
      }
      g.m = tmp.I;
      if (fillField(g.uu, types.charAt(8)) != 0
          || fillField(g.vv, types.charAt(9)) != 0
          || fillField(g.ww, types.charAt(10)) != 0
          || fillField(g.xx, types.charAt(11)) != 0
          || fillField(g.yy, types.charAt(12)) != 0
          || fillField(g.zz, types.charAt(13)) != 0
          || finishRecord() != 0) {
        return sorry(g);
      }
      // section 16
      if (!GbIo.string('\n').equals("* Vertices")) {
        return panic(g, Gb.SYNTAX_ERROR + 3);
      }
      GbIo.newline();
      for (int k = 0; k < lastVert; k++) {
        Vertex v = verts[k];
        if (fillField(tmp, 'S') != 0) {
          return sorry(g);
        }
        v.name = tmp.S();
        if (fillField(tmp, 'A') != 0) {
          return sorry(g);
        }
        v.arcs = tmp.A();
        if (fillField(v.u, types.charAt(0)) != 0
            || fillField(v.v, types.charAt(1)) != 0
            || fillField(v.w, types.charAt(2)) != 0
            || fillField(v.x, types.charAt(3)) != 0
            || fillField(v.y, types.charAt(4)) != 0
            || fillField(v.z, types.charAt(5)) != 0
            || finishRecord() != 0) {
          return sorry(g);
        }
      }
      // section 17
      if (!GbIo.string('\n').equals("* Arcs")) {
        return panic(g, Gb.SYNTAX_ERROR + 4);
      }
      GbIo.newline();
      for (int k = 0; k < lastArc; k++) {
        Arc a = arcs[k];
        if (fillField(tmp, 'V') != 0) {
          return sorry(g);
        }
        a.tip = tmp.V();
        if (fillField(tmp, 'A') != 0) {
          return sorry(g);
        }
        a.next = tmp.A();
        if (fillField(tmp, 'I') != 0) {
          return sorry(g);
        }
        a.len = tmp.I;
        if (fillField(a.a, types.charAt(6)) != 0
            || fillField(a.b, types.charAt(7)) != 0
            || finishRecord() != 0) {
          return sorry(g);
        }
      }
      // section 18
      Matcher cm = CHECKSUM.matcher(GbIo.string('\n'));
      if (!cm.find()) {
        return panic(g, Gb.SYNTAX_ERROR + 5);
      }
      long s = Long.parseLong(cm.group(1));
      if (GbIo.rawClose() != s && s >= 0) {
        return panic(g, Gb.LATE_DATA_FAULT);
      }
      pairArcsByPosition(g); // ADR 0020
      return g;
    } finally {
      verts = null;
      arcs = null;
    }
  }

  private static Graph panic(Graph g, long code) {
    Gb.panicCode = code;
    return sorry(g);
  }

  private static Graph sorry(Graph g) {
    GbIo.rawClose();
    Gb.recycle(g);
    return null;
  }

  /** Section 14. */
  private static long finishRecord() {
    if (GbIo.ch() != '\n') {
      return Gb.panicCode = Gb.SYNTAX_ERROR - 8;
    }
    GbIo.newline();
    commaExpected = false;
    return 0;
  }

  /** Sections 7, 9–12: read one field of type {@code t} into {@code l}; nonzero on failure. */
  private static long fillField(Util l, char t) {
    if (t != 'Z' && commaExpected) {
      if (GbIo.ch() != ',') {
        return Gb.panicCode = Gb.SYNTAX_ERROR - 1;
      }
      if (GbIo.ch() == '\n') {
        GbIo.newline();
      } else {
        GbIo.backup();
      }
    } else {
      commaExpected = true;
    }
    char c = GbIo.ch();
    switch (t) {
      case 'I' -> {
        if (c == '-') {
          l.I = -GbIo.number(10);
        } else {
          GbIo.backup();
          l.I = GbIo.number(10);
        }
      }
      case 'V' -> {
        if (c == 'V') {
          long k = GbIo.number(10);
          if (k >= lastVert || k < 0) {
            Gb.panicCode = Gb.SYNTAX_ERROR - 2;
          } else {
            l.ref = verts[(int) k];
          }
        } else if (c == '0' || c == '1') {
          l.I = c - '0';
          l.ref = null;
        } else {
          Gb.panicCode = Gb.SYNTAX_ERROR - 3;
        }
      }
      case 'S' -> {
        if (c != '"') {
          Gb.panicCode = Gb.SYNTAX_ERROR - 6;
        } else {
          String s = GbIo.string('"');
          while (s.length() >= 2 && s.length() <= MAX_SV_STRING + 2 && s.endsWith("\\\n")) {
            GbIo.newline();
            s = s.substring(0, s.length() - 2) + GbIo.string('"');
          }
          if (GbIo.ch() != '"') {
            Gb.panicCode = Gb.SYNTAX_ERROR - 7;
          } else if (s.isEmpty()) {
            l.ref = Gb.NULL_STRING;
          } else {
            l.ref = Gb.saveString(s);
          }
        }
      }
      case 'A' -> {
        if (c == 'A') {
          long k = GbIo.number(10);
          if (k >= lastArc || k < 0) {
            Gb.panicCode = Gb.SYNTAX_ERROR - 4;
          } else {
            l.ref = arcs[(int) k];
          }
        } else if (c == '0') {
          l.ref = null;
        } else {
          Gb.panicCode = Gb.SYNTAX_ERROR - 5;
        }
      }
      default -> GbIo.backup();
    }
    return Gb.panicCode;
  }

  /**
   * ADR 0020: give restored arcs the mates the C's positional rule implies. For an arc {@code a}
   * from {@code u} to {@code v} at slot {@code i}, the inverse is slot {@code i+1} iff {@code u < v}
   * or {@code a->next == a+1}, else slot {@code i-1}; slots outside the block get no mate.
   */
  private static void pairArcsByPosition(Graph g) {
    Arc[] block = g.arcBlocks().get(0);
    for (int i = 0; i < g.vertices.length; i++) {
      Vertex u = g.vertices[i];
      for (Arc a = u.arcs; a != null; a = a.next) {
        Vertex v = a.tip;
        int j = a.index;
        boolean forward = (v != null && u.index < v.index) || (j + 1 < block.length && a.next == block[j + 1]);
        int k = forward ? j + 1 : j - 1;
        a.mate = k >= 0 && k < block.length ? block[k] : null;
      }
    }
  }
}
```

Notes on fidelity: the C's `string_too_long` for the id truncates `item_buf` at `MAX_SV_ID + 1` characters and then appends the closing quote; `prepareString` has already limited the body to 4095 bytes, so the substring is safe. The `S` continuation loop bound `s.length() <= MAX_SV_STRING + 2` is the C's `p <= buffer` (`item_buf + 4098`, with `p` one past the NUL). `restoreStorage` gives `max(n,1)` vertices; `lastVert = n` keeps the C's bound so a zero-vertex file reads no vertex records. `HEADER` requires the util string to be exactly 14 characters, like the C's `strlen == 14` check. `Integer.parseInt` is fine: the C reads `%ld` but then allocates the arrays, so any value that fits a Java array is the only case that matters.

- [ ] **Step 1: Failing tests**

`SaveTest.java`:
```java
package com.robsartin.jsgb.save;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.basic.Basic;
import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SaveTest {

  @TempDir Path dir;

  private String read(Path p) throws Exception {
    return Files.readString(p, StandardCharsets.ISO_8859_1);
  }

  @Test
  @DisplayName("save_graph writes a 2x2 board exactly as the C did")
  void shouldWriteOracleFileWhenBoardSaved() throws Exception {
    Path out = dir.resolve("board.gb");
    assertThat(Save.saveGraph(Basic.board(2L, 2L, 0L, 0L, 1L, 0L, 0L), out.toString())).isZero();
    String expected =
        new String(getClass().getResourceAsStream("/oracle/inc2/oracle_board.gb").readAllBytes(), StandardCharsets.ISO_8859_1);
    assertThat(read(out)).isEqualTo(expected);
  }

  @Test
  @DisplayName("save_graph reports data hidden by Z types, as the C does for lines()")
  void shouldReportIgnoredDataWhenUtilTypesHideSlots() throws Exception {
    Graph g = Basic.lines(Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 0L), 0L);
    Path out = dir.resolve("lines.gb");
    assertThat(Save.saveGraph(g, out.toString())).isEqualTo(Save.IGNORED_DATA);
    String expected =
        new String(getClass().getResourceAsStream("/oracle/inc2/oracle_lines.gb").readAllBytes(), StandardCharsets.ISO_8859_1);
    assertThat(read(out)).isEqualTo(expected);
  }

  @Test
  @DisplayName("save then restore round-trips structure and gives restored arcs their mates")
  void shouldRoundTripWhenSavedAndRestored() throws Exception {
    Path out = dir.resolve("rt.gb");
    Graph g = Basic.board(2L, 2L, 0L, 0L, 1L, 0L, 0L);
    Save.saveGraph(g, out.toString());
    Graph r = Save.restoreGraph(out.toString());
    assertThat(r).isNotNull();
    assertThat(r.id).isEqualTo("board(2,2,0,0,1,0,0)");
    assertThat(r.n).isEqualTo(4L);
    assertThat(r.m).isEqualTo(8L);
    assertThat(r.vertices).hasSize(8);
    assertThat(r.arcBlocks().get(0)).hasSize(102);
    assertThat(r.vertices[0].name).isEqualTo("0.0");
    assertThat(r.vertices[0].x.I).isZero();
    assertThat(r.vertices[2].x.I).isEqualTo(1L);
    for (int i = 0; i < r.n; i++) {
      for (Arc a = r.vertices[i].arcs; a != null; a = a.next) {
        assertThat(a.mate).isNotNull();
        assertThat(a.mate.tip).isSameAs(r.vertices[i]);
      }
    }
  }

  @Test
  @DisplayName("restore_graph panics on a missing file and on a corrupted checksum")
  void shouldPanicWhenFileMissingOrCorrupt() throws Exception {
    assertThat(Save.restoreGraph(dir.resolve("nope.gb").toString())).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.EARLY_DATA_FAULT);
    Path out = dir.resolve("bad.gb");
    Save.saveGraph(Basic.board(2L, 0L, 0L, 0L, 1L, 0L, 0L), out.toString());
    String text = read(out).replace("* Checksum ", "* Checksum 1");
    Files.writeString(out, text, StandardCharsets.ISO_8859_1);
    assertThat(Save.restoreGraph(out.toString())).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.LATE_DATA_FAULT);
  }

  @Test
  @DisplayName("save_graph returns -1 for a null or recycled graph")
  void shouldReturnMinusOneWhenGraphUnusable() {
    assertThat(Save.saveGraph(null, dir.resolve("x.gb").toString())).isEqualTo(-1L);
    Graph g = Gb.newGraph(1L);
    Gb.recycle(g);
    assertThat(Save.saveGraph(g, dir.resolve("x.gb").toString())).isEqualTo(-1L);
  }

  @Test
  @DisplayName("long strings wrap at column 78 with a backslash and restore intact")
  void shouldWrapAndRestoreWhenNameIsLong() throws Exception {
    Graph g = Gb.newGraph(1L);
    g.id = "long";
    g.vertices[0].name = "x".repeat(200);
    Path out = dir.resolve("long.gb");
    assertThat(Save.saveGraph(g, out.toString())).isZero();
    String text = read(out);
    assertThat(text).contains("\\\n");
    for (String line : text.split("\n")) {
      assertThat(line.length()).isLessThanOrEqualTo(79);
    }
    Graph r = Save.restoreGraph(out.toString());
    assertThat(r.vertices[0].name).isEqualTo("x".repeat(200));
  }
}
```

Append to `TestSampleTest` (imports for `Rand`, `Save`, `Gb`, `Graph`, `Vertex`, `Files`, `Path`, `StandardCharsets`, `TempDir`):
```java
  @TempDir java.nio.file.Path dir;

  private static final long[] DST = {0x20000000L, 0x10000000L, 0x10000000L};

  @Test
  @DisplayName("test.gb written by the test_sample sequence is byte-identical to test.correct")
  void shouldWriteTestCorrectWhenSampleSequenceSaves() throws Exception {
    Graph g = Rand.randomGraph(3L, 10L, 1L, 1L, 0L, null, DST, 1L, 2L, 1L);
    Graph gg = Basic.complement(g, 1L, 1L, 0L);
    Vertex v = Gb.allocVertices(gg, 1)[0];
    v.name = Gb.saveString("Testing");
    gg.utilTypes = gg.utilTypes.substring(0, 10) + 'V' + gg.utilTypes.substring(11);
    gg.ww.V(v);
    java.nio.file.Path out = dir.resolve("test.gb");
    assertThat(Save.saveGraph(gg, out.toString())).isZero();
    Gb.recycle(g);
    Gb.recycle(gg);
    String expected = new String(getClass().getResourceAsStream("/oracle/test.correct").readAllBytes(), StandardCharsets.ISO_8859_1);
    assertThat(Files.readString(out, StandardCharsets.ISO_8859_1)).isEqualTo(expected);
  }

  @Test
  @DisplayName("stanza 3: gunion(random_lengths(restore_graph(test.gb)...), random_graph(...)) at vertex 2")
  void shouldMatchSampleCorrectWhenRestoredGraphStanzaPrinted() throws Exception {
    java.nio.file.Path out = dir.resolve("test.gb");
    Files.write(out, getClass().getResourceAsStream("/oracle/test.correct").readAllBytes());
    Graph g = Save.restoreGraph(out.toString());
    assertThat(g).isNotNull();
    assertThat(Rand.randomLengths(g, 0L, 10L, 12L, DST, 2L)).isZero();
    Graph gg = Rand.randomGraph(3L, 10L, 1L, 1L, 0L, null, DST, 1L, 2L, 1L);
    assertThat(Oracle.capture(ps -> TestSample.printSample(Basic.gunion(g, gg, 1L, 0L), 2, ps)))
        .isEqualTo(SampleCorrect.stanza(3));
  }
```

Append to `OracleInc2Test`:
```java
  @TempDir java.nio.file.Path dir;

  @Test
  @DisplayName("restore_graph reproduces the C's restored board and lines graphs")
  void shouldMatchOracleWhenSavedGraphsRestored() throws Exception {
    java.nio.file.Path b = dir.resolve("b.gb");
    Save.saveGraph(Basic.board(2L, 2L, 0L, 0L, 1L, 0L, 0L), b.toString());
    assertThat(Oracle.capture(ps -> TestSample.printSample(Save.restoreGraph(b.toString()), 0, ps)))
        .isEqualTo(Oracle.inc2("restore_board"));
    java.nio.file.Path l = dir.resolve("l.gb");
    com.robsartin.jsgb.graph.Graph lines = Basic.lines(Basic.board(3L, 0L, 0L, 0L, 1L, 0L, 0L), 0L);
    Save.saveGraph(lines, l.toString());
    assertThat(Oracle.capture(ps -> TestSample.printSample(Save.restoreGraph(l.toString()), 1, ps)))
        .isEqualTo(Oracle.inc2("restore_lines"));
  }
```

- [ ] **Step 2: Run to verify failure** — `./gradlew :lib:test --tests 'com.robsartin.jsgb.save.SaveTest'`; Expected: compilation FAILS (`Save` missing).

- [ ] **Step 3: Transcribe `Save.java` and write `package-info.java`**

`package-info.java`: three sentences on the portable ASCII format, its symbolic `V<k>`/`A<k>` references and checksum, and that only the graph's own storage blocks are written.

- [ ] **Step 4: Run to verify pass, gate, commit**

Run: `./gradlew spotlessApply && ./gradlew check` — Expected: BUILD SUCCESSFUL. The `test.correct` diff is the increment's acceptance gate; if it fails, diff the two files line by line: a numbering difference points at `assignOffsets`, a checksum-only difference at `flushout`/`moveItem`, and a missing `V7` at the extra vertex block handling.

```bash
git add lib/src/main/java/com/robsartin/jsgb/save lib/src/test/java/com/robsartin/jsgb/save demos/src/test
git commit -m "Port gb_save: save_graph and restore_graph

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 11: Architecture rules, `TestSample.main` through stanza 3, spec amendments

**Files:**
- Modify: `lib/src/test/java/com/robsartin/jsgb/ArchitectureTest.java`
- Modify: `demos/src/main/java/com/robsartin/jsgb/demo/TestSample.java`
- Modify: `demos/src/test/java/com/robsartin/jsgb/demo/TestSampleTest.java`
- Modify: `docs/superpowers/specs/2026-09-19-jsgb-design.md` (append amendments only)
- Modify: `README.md` (mention the `demos` subproject and `TestSample`)

- [ ] **Step 1: Failing architecture test**

Add to `ArchitectureTest`:
```java
  @ArchTest
  static final ArchRule generatorsDependOnlyOnKernel =
      noClasses()
          .that()
          .resideInAnyPackage(BASE + ".raman..", BASE + ".basic..", BASE + ".rand..", BASE + ".save..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(BASE + ".raman..", BASE + ".basic..", BASE + ".rand..", BASE + ".save..", BASE + ".sort..", BASE + ".demo..");
```
(`rand` and `basic` need `graph` and `flip`; `save` needs `graph` and `io`; none may use another generator or `sort`.) Prove it can fail: temporarily reference `com.robsartin.jsgb.basic.Basic.class` from `Raman`, run `./gradlew :lib:test --tests 'com.robsartin.jsgb.ArchitectureTest'`, observe `generatorsDependOnlyOnKernel` FAIL, remove the probe, observe PASS. Record both outputs in the report.

- [ ] **Step 2: `TestSample.main` through stanza 3**

Replace the body of `main` so it performs the C's opening sequence and the four stanzas ported so far, in order, writing `test.gb` in the working directory exactly as `test_sample` does:
```java
  public static void main(String[] args) {
    PrintStream out = new PrintStream(System.out, true, StandardCharsets.ISO_8859_1);
    out.print("GraphBase samples generated by test_sample:\n");
    long[] dst = {0x20000000L, 0x10000000L, 0x10000000L};
    // Save a graph to be restored later (test_sample.w section 6).
    Graph g = Rand.randomGraph(3L, 10L, 1L, 1L, 0L, null, dst, 1L, 2L, 1L);
    Graph gg = Basic.complement(g, 1L, 1L, 0L);
    Vertex v = Gb.allocVertices(gg, 1)[0];
    v.name = Gb.saveString("Testing");
    gg.utilTypes = gg.utilTypes.substring(0, 10) + 'V' + gg.utilTypes.substring(11);
    gg.ww.V(v);
    Save.saveGraph(gg, "test.gb");
    Gb.recycle(g);
    Gb.recycle(gg);
    printSample(Raman.raman(31L, 3L, 0L, 4L), 4, out);
    printSample(Basic.board(1L, 1L, 2L, -33L, 1L, -0x40000000L - 0x40000000L, 1L), 2000, out);
    printSample(Basic.subsets(32L, 18L, 16L, 0L, 999L, -999L, 0x80000000L, 1L), 1, out);
    g = Save.restoreGraph("test.gb");
    long i = Rand.randomLengths(g, 0L, 10L, 12L, dst, 2L);
    if (i != 0) {
      out.print("\nFailure code " + i + " returned by random_lengths!\n");
    } else {
      gg = Rand.randomGraph(3L, 10L, 1L, 1L, 0L, null, dst, 1L, 2L, 1L);
      printSample(Basic.gunion(g, gg, 1L, 0L), 2, out);
      Gb.recycle(g);
      Gb.recycle(gg);
    }
    // Stanzas 4 to 15 are added in increments 3 and 4.
  }
```
Refactor so the test can drive it: extract `static void run(PrintStream out, Path workDir)` that `main` calls with `System.out` and the current directory, and that writes `test.gb` under `workDir`. Add to `TestSampleTest`:
```java
  @Test
  @DisplayName("the main sequence so far reproduces the header and stanzas 0 to 3 of sample.correct")
  void shouldMatchSampleCorrectPrefixWhenMainRuns() {
    String expected = SampleCorrect.HEADER + SampleCorrect.stanza(0) + SampleCorrect.stanza(1) + SampleCorrect.stanza(2) + SampleCorrect.stanza(3);
    assertThat(Oracle.capture(ps -> TestSample.run(ps, dir))).isEqualTo(expected);
    assertThat(dir.resolve("test.gb")).exists();
  }
```

- [ ] **Step 3: Spec amendments**

Append under `## Amendments`:

`### 2026-09-20: names of perms and binary vertices` — `gb_basic` sections 52 and 71 write vertex names into a static buffer without a terminator, so in a long-running C process those names can carry trailing bytes from an earlier generator. jsgb produces the names a fresh C process produces (exactly `n` and `d+1` characters). No oracle or demo depends on the other behaviour.

`### 2026-09-20: block numbering in save_graph` — The Risks section assumed the C numbers arc blocks in allocation order. Confirmed from `gb_save.w` sections 23 and 32: blocks are sorted by address and numbered from the lowest address up, which is allocation order for every `calloc` the reference outputs were produced with; the main vertex block is numbered first regardless. `test.correct` and `oracle_board.gb` reproduce byte for byte under this rule.

`### 2026-09-20: extra vertex blocks` — `Gb.allocVertices` registers vertex blocks beyond the main array; their `index` is the position within their own block, so `newEdge` between a main and an extra vertex would order the two arcs differently from the C. No SGB code creates such an edge (`test_sample`'s stray vertex has none).

- [ ] **Step 4: README**

Add a "Running the sample" paragraph: `./gradlew :demos:run --args=''` is not wired yet; for now `TestSample` is exercised by `./gradlew :demos:test`; increment 4 adds the launcher. Mention the `demos` subproject in the Layout section and `scripts/regen-oracle.sh` under a "Regenerating oracles" heading.

- [ ] **Step 5: Gate and commit**

Run: `./gradlew spotlessApply && ./gradlew clean check` — Expected: BUILD SUCCESSFUL for `lib` and `demos`.

```bash
git add lib/src/test/java/com/robsartin/jsgb/ArchitectureTest.java demos/src README.md docs/superpowers/specs/2026-09-19-jsgb-design.md
git commit -m "Wire the sample sequence through stanza 3, extend the architecture rules, amend the spec

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Done criteria for increment 2

- `./gradlew clean check` green on JDK 25 for both subprojects.
- `test.gb` written by the Java sample sequence is byte-identical to `test.correct`.
- `TestSample.run` reproduces `sample.correct`'s header and stanzas 0–3; stanza 11 and all 40 `oracle_inc2.out` cases pass.
- Every public function of `gb_raman`, `gb_basic`, `gb_rand`, `gb_save` exists under its C name (camel-cased) with the C's parameter order.
- ADR 0020 recorded; spec amendments appended; the branch is pushed and a PR opened (orchestrator).
