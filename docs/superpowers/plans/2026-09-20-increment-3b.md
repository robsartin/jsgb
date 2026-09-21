# Increment 3b: books, econ, games, lisa, gates, full TestSample — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port `gb_books`, `gb_econ`, `gb_games`, `gb_lisa`, and `gb_gates` bit-exactly, then complete `TestSample.run` so the entire `sample.correct` reproduces byte for byte, gated by stanzas 4–7 and 9 and thirty-two C-generated oracle cases.

**Architecture:** As increments 1–3a: one package per SGB module (`books`, `econ`, `games`, `lisa`, `gates`) of static methods with the C names; util slots by convention; the C is the authority. `gates` is the only module whose vertices' `y.I` slot holds a *type character* and whose graph slot `zz.A` holds an output-arc list; it uses `Gb.ONE` (ADR 0021) for the boolean constant in arc tips and `Gb.allocAuxVertices` for the scratch vertices of `reduce`. Library output (`print_gates`, `run_risc`) goes through a swappable `PrintStream`, as `dijk` does.

**Tech Stack:** unchanged.

**Spec:** `docs/superpowers/specs/2026-09-19-jsgb-design.md` with its Amendments. C sources: `~/code/sgb/gb_books.w`, `gb_econ.w`, `gb_games.w`, `gb_lisa.w`, `gb_gates.w`, `test_sample.w`; tangled copies at `/private/tmp/claude-501/-Users-sartin/022e4f66-ed5a-4350-bddf-2203947541a8/scratchpad/sgb-build/` while that directory survives.

## Global Constraints

- Everything from the increment 1, 2 and 3a plans' Global Constraints binds (base package, JDK 25, Spotless, JaCoCo gate, test naming, pure TDD with planted controls where code is already correct, stage by explicit path, the `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>` trailer, Gradle blocking, Javadoc on every public member, `package-info.java` per package).
- Branch `8-increment-3b` (issue #8). Do not push; the orchestrator pushes and opens the PR.
- **The C is the authority.** Oracle: `scripts/oracle/oracle_inc3b.c` linked against `libgb.a`, output in `demos/src/test/resources/oracle/inc3b/oracle_inc3b.out`. Hand-derived expectations in this plan may be wrong; when the C disagrees, correct the test and report it with the C section.
- **Translation rules** from the increment 2 and 3a plans apply, plus the spec's unsigned rule that increment 3a's final review enforced: **every comparison on a C `unsigned long` parameter uses `Long.compareUnsigned`** (`n`, `x`, `first_chapter`, `last_chapter` in books; `n`, `omit`, `threshold` in econ; `n` in games; all nine of `lisa`'s arguments and `bi_lisa`'s `thresh`; `regs`, `m`, `n`, `r`, `prob`, `size`, `trace_regs` in gates), and ids print them with `Long.toUnsignedString`. Signed C parameters (weights, days, seeds, `bi_lisa`'s `c`) compare as signed.
- **Pointer-into-array idioms** (this increment is full of them): a C `Vertex*` that is advanced with `+k` or `++` becomes an `int` index into `g.vertices`; a C `node*` into a static node array becomes an `int` index or a direct reference; `p + 1` on nodes means "the next node in the array". Keep the C's variable names.
- **Static state** that outlives the call stays static (`books`: `chapters`, `chapName`; `lisa`: `lisaId`; `gates`: `riscState`, `out`); scratch statics the C reuses (`node_block`, hash tables) are re-created per call.
- **Union aliasing in `gates`:** the C stores the *type* in `y.I` (`typ`), the *value* in `x.I` (`val`), `foo` in `x.V`, `alt` in `z.V`, `bit` in `z.I`, `bar` and `lnk` both in `w.V`. In Java `Util` has separate `I` and `ref`, so `val`/`foo` and `bit`/`alt` do not clobber each other; the C never reads one after writing the other on the same vertex except where the plan says so. `bar` and `lnk` share `w.ref` exactly as in C.

## File structure

```
lib/src/main/java/com/robsartin/jsgb/books/Books.java (+package-info)   Task 1
lib/src/main/java/com/robsartin/jsgb/econ/Econ.java (+package-info)     Task 2
lib/src/main/java/com/robsartin/jsgb/games/Games.java (+package-info)   Task 3
lib/src/main/java/com/robsartin/jsgb/lisa/Lisa.java (+package-info)     Task 4
lib/src/main/java/com/robsartin/jsgb/gates/Gates.java (+package-info)   Tasks 5, 6
lib/src/test/java/com/robsartin/jsgb/{books,econ,games,lisa,gates}/*Test.java
lib/src/test/java/com/robsartin/jsgb/ArchitectureTest.java              Task 7
demos/src/main/java/com/robsartin/jsgb/demo/TestSample.java              Task 7 (stanzas 4–7, 9)
demos/src/test/java/com/robsartin/jsgb/demo/Oracle.java                  Task 1: inc3b(name), inc3bReturn(name)
demos/src/test/java/com/robsartin/jsgb/demo/OracleInc3bTest.java         Tasks 1–6 (create in Task 1)
demos/src/test/java/com/robsartin/jsgb/demo/TestSampleTest.java          stanza tests (Tasks 1–6), full diff (Task 7)
demos/src/test/resources/oracle/inc3b/oracle_inc3b.out                   already on the branch (C output)
demos/src/test/resources/oracle/MANIFEST.md                              Task 1: inc3b row
scripts/oracle/oracle_inc3b.c                                            already on the branch
scripts/regen-oracle.sh                                                  Task 1: also build/run oracle_inc3b
```

`oracle_inc3b.out` cases (`==name`, or `==name=value` carrying a return value): `book_anna`, `book_david`, `bi_book_jean`, `bi_book_homer`, `book_bad`, `book_chapters` (a custom first line `chapters=… first=… last=…`), `econ_full`, `econ_omit2`, `econ_greedy`, `econ_users`, `econ_default`, `games_full`, `games_window`, `games_neg`, `games_bad`, `lisa_matrix` and `lisa_window` (the `lisa_id` line then the matrix rows), `plane_lisa_small`, `plane_lisa_window`, `bi_lisa`, `bi_lisa_c`, `lisa_bad`, `risc2_sample`, `risc2_eval=0` (the output vector on one line), `run_risc_mult` (the trace, `return=0`, then the eighteen `risc_state` values space-separated with a trailing space), `run_risc_div` (no trace), `prod22` (`print_gates` output then a `print_sample`), `prod33_sample`, `partial_prod33` and `partial_risc_stanza4` (the `buf` line then a `print_sample`), `prod_bad`, `risc2_gates` (`print_gates` of `risc(2)`, 1630 gate lines plus 16 output lines). Read `scripts/oracle/oracle_inc3b.c` for the exact calls.

---

### Task 1: `Books` (gb_books): `book`, `biBook`

**Files:**
- Create: `lib/src/main/java/com/robsartin/jsgb/books/Books.java`, `package-info.java`
- Create: `lib/src/test/java/com/robsartin/jsgb/books/BooksTest.java`
- Modify: `demos/src/test/java/com/robsartin/jsgb/demo/Oracle.java` (`inc3b`, `inc3bReturn`), `TestSampleTest.java` (stanza 5), `MANIFEST.md`, `scripts/regen-oracle.sh`
- Create: `demos/src/test/java/com/robsartin/jsgb/demo/OracleInc3bTest.java`

**Interfaces:**
- `public static Graph book(String title, long n, long x, long firstChapter, long lastChapter, long inWeight, long outWeight, long seed)` and `public static Graph biBook(...)` with the same parameters; both call a private `bgraph(boolean bipartite, ...)`.
- Public statics as in C: `public static long chapters;` `public static String[] chapName = new String[MAX_CHAPS]` with `chapName[0] = ""` (the C initialises only element 0).
- Constants `MAX_CHAPS = 360`, `MAX_CHARS = 600`, `MAX_CODE = 1296`; slots `shortCode` = `u.I`, `outCount` = `x.I`, `inCount` = `y.I`, `desc` = `z.S`, `chapNo` = `a.I`; `utilTypes = "IZZIISIZZZZZZZ"`.

**Source:** `gb_books.w` sections 5–29 (tangled `gb_books.c`, 360 lines). Notes:
- `Flip.initRand(seed)` first. Section 10: `n == 0 → MAX_CHARS`; `firstChapter == 0 → 1`; `lastChapter == 0 → MAX_CHAPS`; weight bounds ±1000000 → `BAD_SPECS`; file name `Gb.prefix(title, 6) + ".dat"` (`"%.6s.dat"`); `GbIo.open` nonzero → `EARLY_DATA_FAULT`.
- Section 16 (first pass, character list): a static `Node[] nodeBlock` of 600 (re-created per call) and `Node[] xnode` of 1296; `while ((c = number(36)) != 0) { c >= MAX_CODE || ch() != ' ' → SYNTAX_ERROR; too many → +1; node.link = previous node (null for the first); code; xnode[c] = node; in = out = chap = 0; vert = null; newline(); }`; `characters = count; newline();`.
- Section 19 (chapter pass): `for (k = 1; k < MAX_CHAPS && !eof(); k++) { s = string(':'); if (s.startsWith("&")) k--; while (ch() != '\n') { c = number(36); c >= MAX_CODE → +4; p = xnode[c]; null → +5; if (p.chap != k) { p.chap = k; if (k in [firstChapter, lastChapter]) p.in++ else p.out++; } } newline(); }`; `k == MAX_CHAPS → +6`; `chapters = k − 1`; `close()` nonzero → `LATE_DATA_FAULT`. (The `k in [first, last]` test uses unsigned comparisons.)
- Section 27: `n > characters → n = characters; x > n → x = n; lastChapter > chapters → lastChapter = chapters; firstChapter > lastChapter → firstChapter = lastChapter + 1; newGraph(n − x + (bipartite ? lastChapter − firstChapter + 1 : 0))`; `utilTypes`; id `(bipartite ? "bi_" : "") + "book(\"" + title + "\"," + unsigned(n) + "," + unsigned(x) + "," + unsigned(firstChapter) + "," + unsigned(lastChapter) + "," + inWeight + "," + outWeight + "," + seed + ")"` (clamped values, as the oracles show: `book("david",30,5,1,64,1,1,5)`); if bipartite: `Gb.markBipartite(g, n − x)` and `chapBase = (n − x) − firstChapter` (an index offset: the chapter vertex for chapter `k` is `g.vertices[chapBase + k]`).
- Section 28: keys `inWeight * in + outWeight * out + 0x40000000`; `LinkSort.linksort(nodeBlock[characters − 1])` (the list runs from the last character back to the first); walk `sorted[127..0]`: `if (x > 0) x--; else p.vert = g.vertices[next++]; if (--k == 0) break` with `k = n`.
- Section 29/17: reopen the file (`open` nonzero → `IMPOSSIBLE + 1`); `while ((c = number(36)) != 0) { v = xnode[c].vert; if (v != null) { ch() != ' ' → IMPOSSIBLE; v.name = string(','); ch() != ',' → SYNTAX_ERROR + 2; ch() != ' ' → + 3; v.desc = string('\n'); inCount = in; outCount = out; shortCode = c; } newline(); } newline();`.
- Then section 20 (bipartite) or 22 (cliques):
  - Section 22: `for (k = 1; !eof(); k++) { s = string(':'); if (s.startsWith("&")) k--; else { if (s.endsWith("\n")) strip it; chapName[k] = s; } if (k in [first, last]) { c = ch(); while (c != '\n') { collect into a clique list: do { c = number(36); if (xnode[c].vert != null) add it; c = ch(); } while (c == ','); for each pair (u, v) in list order: section 25 — skip if `u` already has an arc to `v`; `newEdge(u, v, 1)`; `a = u.index < v.index ? u.arcs : v.arcs; a.a.I = a.mate.a.I = k;` } } newline(); }`. The clique table holds at most 30 vertices (the C's static array); a longer clique is a data error the C would overflow on — throw `IllegalStateException` if it happens (it does not in the five data files).
  - Section 20: reset every node's `chap = 0`; `for (k = 1; !eof(); k++) { s = string(':'); if (s.startsWith("&")) k--; else { strip a trailing '\n'; chapName[k] = s; } if (k in [first, last]) { u = g.vertices[chapBase + k]; if (!s.startsWith("&")) { u.name = chapName[k]; u.desc = ""; inCount = outCount = 0; } while (ch() != '\n') { c = number(36); p = xnode[c]; if (p.chap != k) { p.chap = k; v = p.vert; if (v != null) { newEdge(v, u, 1); u.inCount++; } else u.outCount++; } } } newline(); }`.
  - `close()` nonzero → `IMPOSSIBLE + 2`; final `troubleCode` → `ALLOC_FAULT` after `recycle`.
- `GbIo.string(':')` on a line without a colon returns the rest of the line including its `'\n'` (that is why the C strips it); `GbIo.string(',')` for a name stops at the comma.

- [ ] **Step 1: Failing tests**

`BooksTest.java`:
```java
package com.robsartin.jsgb.books;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BooksTest {

  @Test
  @DisplayName("book(homer,500,400,2,12,10000,-123456,789) is stanza 5 with Eetion at vertex 81")
  void shouldMatchStanzaWhenSampleParametersGiven() {
    Graph g = Books.book("homer", 500L, 400L, 2L, 12L, 10000L, -123456L, 789L);
    assertThat(g.id).isEqualTo("book(\"homer\",500,400,2,12,10000,-123456,789)");
    assertThat(g.n).isEqualTo(100L);
    assertThat(g.m).isEqualTo(4L);
    assertThat(g.utilTypes).isEqualTo("IZZIISIZZZZZZZ");
    assertThat(g.vertices[81].name).isEqualTo("Eetion");
    assertThat(g.vertices[81].u.I).isEqualTo(90L);
    assertThat(g.vertices[81].x.I).isEqualTo(2L);
    assertThat(g.vertices[81].y.I).isEqualTo(1L);
    assertThat(g.vertices[81].z.S()).isEqualTo("king of Cilicia, father of AH");
    assertThat(g.vertices[81].arcs.tip.name).isEqualTo("Andromache");
    assertThat(g.vertices[81].arcs.a.I).isEqualTo(6L);
    assertThat(Books.chapters).isEqualTo(24L);
  }

  @Test
  @DisplayName("bi_book adds one vertex per selected chapter, named after the chapter, and marks the bipartite split")
  void shouldAddChapterVerticesWhenBipartite() {
    Graph g = Books.biBook("homer", 100L, 0L, 10L, 20L, 1L, 1L, 3L);
    assertThat(g.id).isEqualTo("bi_book(\"homer\",100,0,10,20,1,1,3)");
    assertThat(g.n).isEqualTo(111L);
    assertThat(g.uu.I).isEqualTo(100L);
    assertThat(g.utilTypes).isEqualTo("IZZIISIZIZZZZZ");
    assertThat(g.vertices[105].name).isEqualTo("15");
    assertThat(g.vertices[105].z.S()).isEmpty();
    assertThat(Books.chapName[15]).isEqualTo("15");
  }

  @Test
  @DisplayName("weights beyond a million panic with bad_specs and a missing title is an early data fault")
  void shouldPanicWhenSpecsAreBad() {
    assertThat(Books.book("huck", 0L, 0L, 0L, 0L, 2000000L, 0L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS);
    assertThat(Books.book("nosuch", 0L, 0L, 0L, 0L, 1L, 1L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.EARLY_DATA_FAULT);
  }
}
```
The `chapters` value for `homer` is read from the oracle `book_chapters` case pattern; if the C gives a different count for homer, correct it (the data file decides).

`OracleInc3bTest.java` (create):
```java
package com.robsartin.jsgb.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.books.Books;
import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Every case reproduces a run of the C library recorded in oracle_inc3b.out. */
class OracleInc3bTest {

  @Test
  @DisplayName("book and bi_book print exactly as the C")
  void shouldMatchOracleWhenBooksPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Books.book("anna", 50L, 10L, 1L, 10L, 1L, 1L, 1L), 3, ps))).isEqualTo(Oracle.inc3b("book_anna"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Books.book("david", 30L, 5L, 0L, 0L, 1L, 1L, 5L), 0, ps))).isEqualTo(Oracle.inc3b("book_david"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Books.biBook("jean", 100L, 0L, 1L, 5L, 1L, 1L, 2L), 82, ps))).isEqualTo(Oracle.inc3b("bi_book_jean"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Books.biBook("homer", 100L, 0L, 10L, 20L, 1L, 1L, 3L), 105, ps))).isEqualTo(Oracle.inc3b("bi_book_homer"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Books.book("huck", 0L, 0L, 0L, 0L, 2000000L, 0L, 1L), 0, ps))).isEqualTo(Oracle.inc3b("book_bad"));
    Graph g = Books.book("huck", 20L, 0L, 0L, 0L, 1L, 1L, 4L);
    String out =
        Oracle.capture(
            ps -> {
              ps.print("chapters=" + Books.chapters + " first=" + Books.chapName[1] + " last=" + Books.chapName[(int) Books.chapters] + "\n");
              TestSample.printSample(g, 0, ps);
            });
    assertThat(out).isEqualTo(Oracle.inc3b("book_chapters"));
  }
}
```

Append to `TestSampleTest` (import `Books`):
```java
  @Test
  @DisplayName("stanza 5: book(homer,500,400,2,12,10000,-123456,789) at vertex 81")
  void shouldMatchSampleCorrectWhenBookStanzaPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Books.book("homer", 500L, 400L, 2L, 12L, 10000L, -123456L, 789L), 81, ps))).isEqualTo(SampleCorrect.stanza(5));
  }

  @Test
  @DisplayName("oracle_inc3b.out cases are addressable by name")
  void shouldExtractInc3bCasesWhenReadingOracleFile() {
    assertThat(Oracle.inc3b("book_bad")).isEqualTo("\nOoops, we just ran into panic code 30!\n");
    assertThat(Oracle.inc3bReturn("risc2_eval")).isZero();
    assertThat(Oracle.inc3b("risc2_eval")).isEqualTo("0000000000000001\n");
  }
```

- [ ] **Step 2: Run to verify failure** — `./gradlew :lib:test --tests 'com.robsartin.jsgb.books.BooksTest'`: compilation FAILS; `./gradlew :demos:test` likewise (`Oracle.inc3b`).

- [ ] **Step 3: Implement** `Oracle.inc3b`/`inc3bReturn` (through the existing shared loader), `Books.java` with Javadoc (the character-encounter graphs of five novels; `book` joins characters that appear in a chapter together, `bi_book` joins characters to chapters; the weight/selection rule; the public `chapters`/`chapName`), `package-info.java`, the MANIFEST row, and the regen-script block for `oracle_inc3b`.

- [ ] **Step 4: Run to verify pass, gate, commit**

Run: `./gradlew spotlessApply && ./gradlew check`.

```bash
git add lib/src/main/java/com/robsartin/jsgb/books lib/src/test/java/com/robsartin/jsgb/books demos/src/test scripts/regen-oracle.sh
git commit -m "Port gb_books: book and bi_book

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: `Econ` (gb_econ): `econ`

**Files:**
- Create: `lib/src/main/java/com/robsartin/jsgb/econ/Econ.java`, `package-info.java`
- Create: `lib/src/test/java/com/robsartin/jsgb/econ/EconTest.java`
- Modify: `OracleInc3bTest.java` (econ cases), `TestSampleTest.java` (stanza 6)

**Interfaces:**
- `public static Graph econ(long n, long omit, long threshold, long seed)`; `n`, `omit`, `threshold` are C `unsigned long`.
- Constants `MAX_N = 81`, `NORM_N = 79`, `ADJ_SEC = 80`; slots `flow` = `a.I` on arcs, `SIC_codes` = `z.A` (an `Arc` list whose `len` fields are SIC codes and whose `tip`s are null), `sector_total` = `y.I`; `utilTypes = "ZZZZIAIZZZZZZZ"`.

**Source:** `gb_econ.w` sections 7–30 (tangled `gb_econ.c`, 408 lines). Notes:
- A private `Node` class: `rchild` (Node), `title` (String), `long[] table = new long[MAX_N + 1]`, `total`, `thresh`, `sic`, `tag`, `link` (Node), `sicList` (Arc), and an `int idx` giving its position in `nodeBlock`, because the C uses `p + 1` ("the node after p") for the left child and `p - 1`/`p--` loops. `nodeBlock` has `2 * MAX_N - 3 = 159` nodes (fresh per call); `stack` is a `Deque<Node>` or an array with a pointer; `nodeIndex = new Node[MAX_N + 1]`; `vertIndex = new Vertex[MAX_N + 1]`. `total` is `unsigned long` in C; every value is a sum of nonnegative table entries, so plain `long` arithmetic is exact — but comparisons on it (`q.link.total > pl.total`) still use `Long.compareUnsigned` for fidelity.
- Section 9 (unsigned): `omit > 2 → 2`; `n == 0 || n > MAX_N − omit → n = MAX_N − omit; else if n + omit < 3 → omit = 3 − n`; `threshold > 65536 → 65536`. Section 10: `newGraph(n)`, id `econ(%lu,%lu,%lu,%ld)`.
- Section 14/15: `open("econ.dat")` nonzero → `EARLY_DATA_FAULT`. For `p` over `nodeBlock[0 .. 2·NORM_N − 2]` (157 nodes): `title = string(':')`; `title.length() > 43 → SYNTAX_ERROR`; `ch() != ':' → +1`; `sic = c = number(10)`; `c == 0 → push p` else `{ nodeIndex[c] = p; if stack nonempty: pop().rchild = nodeBlock[p.idx + 1]; }`; `ch() != '\n' → +2`; `newline()`. Then: stack nonempty → `+3`; any `nodeIndex[k] == null` for `k = NORM_N..1` → `+4`; `nodeBlock[157].title = "Adjustments"; .sic = ADJ_SEC; nodeIndex[ADJ_SEC] = it; nodeBlock[158].title = "Users"; nodeIndex[MAX_N] = it` (its `sic` stays 0).
- Section 16 for `k = 1..MAX_N`: `ch() != '\n' → +5; newline(); p = nodeIndex[k]; s = 0; for j = 1..MAX_N−1: { p.table[j] = x = number(10); s += x; nodeIndex[j].total += x; if j % 10 == 0 { ch() != '\n' → +6; newline(); } else ch() != ',' → +7 }; p.table[MAX_N] = s`.
- Section 17: `l = n + omit − 2`; `l == NORM_N` → section 18 (all `nodeIndex[k].tag = 1`, `k = NORM_N..1`); else if `seed != 0` → section 21; else → section 19.
- Section 21 (random subtree): `nodeBlock[0].tag = l`; for `p` from `nodeIndex[ADJ_SEC].idx − 1` down to index 1 (`p > node_block`): if `p.rchild != null` → section 22 (fills `p.table[0..]` with the polynomial: `pl = nodeBlock[p.idx + 1]; pr = p.rchild; table[1] = table[2] = 1; …` transcribe, including section 23's convolution); then for `p` from index 0 up to `< nodeIndex[ADJ_SEC].idx`: if `p.tag > 1` → `l = p.tag; pl, pr as above; pl.rchild == null → pl.tag = 1, pr.tag = l − 1; else pr.rchild == null → pl.tag = l − 1, pr.tag = 1; else section 24` (`ss` sum with the `p == nodeBlock[0] && l > 29 && l < 67` scaled branch using `((pl.table[k] + 0x3ff) >> 10)`, `rr = Flip.unifRand(ss)`, the re-scan loop `for (ss = 0, k = start; ss <= rr; k++) …`, then `pl.tag = k − 1; pr.tag = l − k + 1`). `j` in section 24 is a local flag; keep it local.
- Section 19 (greedy, `seed == 0`): `special = nodeIndex[MAX_N]`; for `p` from `nodeIndex[ADJ_SEC].idx − 1` down to 0: if `p.rchild != null` → `p.total = nodeBlock[p.idx + 1].total + p.rchild.total`; `special.link = nodeBlock[0]; nodeBlock[0].link = special; k = 1; while (k < l)` → section 20 (`p = special.link; special.link = p.link; if p.rchild == null → p.tag = 1; else { pl, pr; insert pl then pr into the list after the last node whose total is greater (`for (q = special; compareUnsigned(q.link.total, pl.total) > 0; q = q.link);`); k++ }`); then every node on the list from `special.link` until `special` gets `tag = 1`.
- Section 28 (for `p` from `nodeIndex[ADJ_SEC]` down to index 0): `p.sic != 0 → { p.sicList = Gb.virginArc(); p.sicList.len = p.sic; }` else `{ pl, pr; if p.tag == 0 → p.tag = pl.tag + pr.tag; if p.tag <= 1 → section 29 }` (merge: `a = pl.sicList; jj = pl.sic; kk = pr.sic; p.sicList = a; walk to the end; a.next = pr.sicList; for k = MAX_N..1: q = nodeIndex[k]; if q != null { if q != pl && q != pr → q.table[jj] += q.table[kk]; p.table[k] = pl.table[k] + pr.table[k]; }; p.total = pl.total + pr.total; p.sic = jj; p.table[jj] += p.table[kk]; nodeIndex[jj] = p; nodeIndex[kk] = null`).
- Section 30: `omit == 2 → nodeIndex[ADJ_SEC] = nodeIndex[MAX_N] = null; omit == 1 → nodeIndex[MAX_N] = null; else { for k = ADJ_SEC..1: p = nodeIndex[k]; if p != null → p.table[MAX_N] = p.total − p.table[MAX_N]; p = nodeIndex[MAX_N]; p.total = p.table[MAX_N]; p.table[MAX_N] = 0 }`.
- Section 27: `for k = MAX_N..1: p = nodeIndex[k]; if p != null: p.thresh = threshold == 0 ? −99999999 : ((p.total >> 16) * threshold) + (((p.total & 0xffff) * threshold) >> 16)`.
- Section 25: `vi = n; for k = MAX_N..1: p = nodeIndex[k]; if p != null { vi--; v = g.vertices[vi]; vertIndex[k] = v; v.name = p.title; v.z.A(p.sicList); v.y.I = p.total; } else vertIndex[k] = null`; `vi != 0 → IMPOSSIBLE`; then `for j = MAX_N..1: p = nodeIndex[j]; if p != null { u = vertIndex[j]; for k = MAX_N..1: v = vertIndex[k]; if v != null && p.table[k] != 0 && p.table[k] > nodeIndex[k].thresh { Gb.newArc(u, v, 1); u.arcs.a.I = p.table[k]; } }`.
- `close()` nonzero → `LATE_DATA_FAULT`; `troubleCode` nonzero → recycle + `ALLOC_FAULT`. The C's `panic` sets `panicCode`, clears `troubleCode`, returns null.

- [ ] **Step 1: Failing tests**

`EconTest.java`:
```java
package com.robsartin.jsgb.econ;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EconTest {

  @Test
  @DisplayName("econ(40,0,400,-111) is stanza 6: printing and publishing at vertex 11")
  void shouldMatchStanzaWhenSampleParametersGiven() {
    Graph g = Econ.econ(40L, 0L, 400L, -111L);
    assertThat(g.id).isEqualTo("econ(40,0,400,-111)");
    assertThat(g.n).isEqualTo(40L);
    assertThat(g.m).isEqualTo(512L);
    assertThat(g.utilTypes).isEqualTo("ZZZZIAIZZZZZZZ");
    assertThat(g.vertices[11].name).isEqualTo("Printing and publishing");
    assertThat(g.vertices[11].y.I).isEqualTo(69451L);
    Arc sic = g.vertices[11].z.A();
    assertThat(sic.tip).isNull();
    assertThat(g.vertices[11].arcs.tip.name).isEqualTo("Food, liquor, and candy");
    assertThat(g.vertices[11].arcs.a.I).isEqualTo(1863L);
    assertThat(g.vertices[39].name).isEqualTo("Users");
    assertThat(g.vertices[39].y.I).isEqualTo(3999362L);
  }

  @Test
  @DisplayName("the full 81-sector table needs no merging and every sector keeps its own SIC arc")
  void shouldKeepAllSectorsWhenNIsMaximal() {
    Graph g = Econ.econ(81L, 0L, 0L, 1L);
    assertThat(g.id).isEqualTo("econ(81,0,0,1)");
    assertThat(g.n).isEqualTo(81L);
    assertThat(g.vertices[0].name).isEqualTo("Livestock and livestock products");
    assertThat(g.vertices[0].z.A().len).isEqualTo(1L);
    assertThat(g.vertices[0].z.A().next).isNull();
    assertThat(g.vertices[80].name).isEqualTo("Users");
  }

  @Test
  @DisplayName("omit=2 drops Adjustments and Users and clamps the request")
  void shouldClampWhenOmitGiven() {
    Graph g = Econ.econ(40L, 2L, 1000L, 5L);
    assertThat(g.id).isEqualTo("econ(40,2,1000,5)");
    assertThat(g.n).isEqualTo(40L);
    for (int i = 0; i < 40; i++) {
      assertThat(g.vertices[i].name).isNotIn("Adjustments", "Users");
    }
    assertThat(Econ.econ(0L, 0L, 0L, 0L).id).isEqualTo("econ(81,0,0,0)");
    assertThat(Econ.econ(2L, 5L, 0L, 0L).id).isEqualTo("econ(2,2,0,0)");
  }
}
```
(The `econ(2,5,…)` expectation is hand-derived from section 9: `omit → 2`, `n + omit = 4 ≥ 3`; verify against the C by adding it to the harness only if the implementer doubts it — the id in the test is what the Java must print.)

Add to `OracleInc3bTest` (import `Econ`):
```java
  @Test
  @DisplayName("econ prints exactly as the C for the full, omitted, greedy, users and default cases")
  void shouldMatchOracleWhenEconPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Econ.econ(81L, 0L, 0L, 1L), 0, ps))).isEqualTo(Oracle.inc3b("econ_full"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Econ.econ(40L, 2L, 1000L, 5L), 5, ps))).isEqualTo(Oracle.inc3b("econ_omit2"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Econ.econ(10L, 1L, 0L, 0L), 2, ps))).isEqualTo(Oracle.inc3b("econ_greedy"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Econ.econ(20L, 0L, 0L, 7L), 19, ps))).isEqualTo(Oracle.inc3b("econ_users"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Econ.econ(0L, 0L, 0L, 0L), 80, ps))).isEqualTo(Oracle.inc3b("econ_default"));
  }
```

Append to `TestSampleTest` (import `Econ`):
```java
  @Test
  @DisplayName("stanza 6: econ(40,0,400,-111) at vertex 11")
  void shouldMatchSampleCorrectWhenEconStanzaPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Econ.econ(40L, 0L, 400L, -111L), 11, ps))).isEqualTo(SampleCorrect.stanza(6));
  }
```

- [ ] **Step 2: Run to verify failure** — compilation fails on `Econ`.
- [ ] **Step 3: Implement** `Econ.java` with Javadoc (the 1985 U.S. input-output sector table; `n` sectors, `omit` of the two special sectors, `threshold` on flows in units of 1/65536 of the receiving sector's total, `seed` selecting a random subtree of merges or, when 0, the greedy merge), `package-info.java`.
- [ ] **Step 4: Verify pass, `spotlessApply`, `check`, commit** `Port gb_econ: econ`.

---

### Task 3: `Games` (gb_games): `games`

**Files:**
- Create: `lib/src/main/java/com/robsartin/jsgb/games/Games.java`, `package-info.java`
- Create: `lib/src/test/java/com/robsartin/jsgb/games/GamesTest.java`
- Modify: `OracleInc3bTest.java`, `TestSampleTest.java` (stanza 7)

**Interfaces:**
- `public static Graph games(long n, long ap0Weight, long upi0Weight, long ap1Weight, long upi1Weight, long firstDay, long lastDay, long seed)`; only `n` is unsigned.
- Constants `MAX_N = 120`, `MAX_DAY = 128`, `MAX_WEIGHT = 131072`, `HASH_PRIME = 1009`, `HOME = 1`, `NEUTRAL = 2`, `AWAY = 3`; slots `ap` = `u.I`, `upi` = `v.I`, `abbr` = `x.S`, `nickname` = `y.S`, `conference` = `z.S` (null for independents; `print_sample` already prints `(null)`), `venue` = `a.I`, `date` = `b.I`; `utilTypes = "IIZSSSIIZZZZZZ"`.

**Source:** `gb_games.w` sections 7–24 (tangled `gb_games.c`, 334 lines). Notes:
- `Node implements Sortable`: `key`, `link` (Sortable), `name`, `nick`, `abb`, `a0, u0, a1, u1`, `conf` (String, null = independent), `hashLink`, `vert`. `nodeBlock = new Node[MAX_N]` fresh per call; `hashBlock = new Node[HASH_PRIME]`; `confBlock` a `List<String>` (`m` = its size). Static limits `ma0 = 1451, mu0 = 666, ma1 = 1475, mu1 = 847`.
- Section 9: `n == 0 || n > MAX_N → MAX_N` (unsigned); any weight outside `±MAX_WEIGHT` → `BAD_SPECS`; `firstDay < 0 → 0`; `lastDay == 0 || lastDay > MAX_DAY → MAX_DAY`. Section 10: id `games(%lu,%ld,%ld,%ld,%ld,%ld,%ld,%ld)` with the clamped values (oracle: `games(120,0,0,0,0,0,128,1)`, `games(10,-1,-1,-1,-1,0,128,3)`).
- Section 14/15, `open("games.dat")` nonzero → `EARLY_DATA_FAULT`; for `k = 0..MAX_N−1`: `p = nodeBlock[k] = new Node(); if k > 0: p.link = nodeBlock[k − 1]; p.abb = string(' '); abb.length() > 5 || ch() != ' ' → SYNTAX_ERROR;` section 16 hash insert: `h = 0; for each char c of abb: h = (h + h + c) % HASH_PRIME; p.hashLink = hashBlock[h]; hashBlock[h] = p;` `p.name = string('('); length > 23 || ch() != '(' → +1; p.nick = string(')'); length > 21 || ch() != ')' → +2;` section 17: `s = string(';'); ch() != ';' → +3; if !s.equals("Independent") { look up s in confBlock; if absent add it; p.conf = the stored string }`; section 18: `a0 = number(10); a0 > ma0 || ch() != ',' → +4; u0 = number(10); u0 > mu0 || ch() != ';' → +5; a1; > ma1 || ch() != ',' → +6; u1; > mu1 || ch() != '\n' → +7; key = ap0Weight*a0 + upi0Weight*u0 + ap1Weight*a1 + upi1Weight*u1 + 0x40000000; newline()`. (The length checks mirror `q > &p->abb[6]` where `q` points past the NUL: a 6-character abbreviation fails.)
- Section 19: `LinkSort.linksort(nodeBlock[MAX_N − 1])`; `vi = 0; for j = 127..0: for p over sorted[j] via link: if vi < n → section 20 (`v = vertices[vi++]; ap = (a0 << 16) + a1; upi = (u0 << 16) + u1; x.S(abb); y.S(nick); z.S(conf); name; p.vert = v`) else `p.abb = ""` (so `teamLookup` never matches it).
- `teamLookup()`: `h = 0; sb; while (GbIo.digit(10) < 0) { c = ch(); h = (h + h + c) % HASH_PRIME; sb.append(c); } GbIo.backup(); for p over hashBlock[h] chain: if p.abb.equals(sb) return p.vert; return null`. (`gb_digit(10)` consumes the character when it is a digit and returns −1 without consuming otherwise — confirm against `GbIo.digit`'s contract.)
- Section 21: `today = 0; while (!eof()) { if (ch() == '>') → section 22 else backup(); u = teamLookup(); su = number(10); ven = ch(); '@' → HOME; ',' → NEUTRAL; else → SYNTAX_ERROR + 8; v = teamLookup(); sv = number(10); ch() != '\n' → +9; if (u != null && v != null && today >= firstDay && today <= lastDay) → section 24; newline(); }`. Section 22: `c = ch(); d = switch c { 'A' → −26; 'S' → 5; 'O' → 35; 'N' → 66; 'D' → 96; 'J' → 127; default → 1000 }; d += number(10); d < 0 || d > MAX_DAY → SYNTAX_ERROR − 1; today = d; newline();` — and then execution continues into the game parse of the *next* line, exactly as the C's `if … else gb_backup();` falls through.
- Section 24: `if (u.index > v.index) { swap u,v; swap su,sv; ven = HOME + AWAY − ven; } Gb.newArc(u, v, su); Gb.newArc(v, u, sv); a = u.arcs; if (v.arcs.index != a.index + 1) → IMPOSSIBLE + 9` (the C's `v->arcs != a+1`: the two arcs are consecutive slots; games allocates arcs only in pairs from a fresh graph, so a pair never straddles a 102-arc block); `a.a.I = ven; v.arcs.a.I = HOME + AWAY − ven; a.b.I = v.arcs.b.I = today`. Leave `mate` null (these are `gb_new_arc` arcs).
- `close()` nonzero → `LATE_DATA_FAULT`; `troubleCode` → recycle + `ALLOC_FAULT`.

- [ ] **Step 1: Failing tests**

`GamesTest.java`:
```java
package com.robsartin.jsgb.games;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GamesTest {

  @Test
  @DisplayName("games(60,70,80,-90,-101,60,0,999999999) is stanza 7: Maryland at vertex 14")
  void shouldMatchStanzaWhenSampleParametersGiven() {
    Graph g = Games.games(60L, 70L, 80L, -90L, -101L, 60L, 0L, 999999999L);
    assertThat(g.id).isEqualTo("games(60,70,80,-90,-101,60,128,999999999)");
    assertThat(g.n).isEqualTo(60L);
    assertThat(g.m).isEqualTo(114L);
    assertThat(g.utilTypes).isEqualTo("IIZSSSIIZZZZZZ");
    Vertex md = g.vertices[14];
    assertThat(md.name).isEqualTo("Maryland");
    assertThat(md.u.I).isEqualTo(2752512L);
    assertThat(md.v.I).isEqualTo(131072L);
    assertThat(md.x.S()).isEqualTo("MD");
    assertThat(md.y.S()).isEqualTo("Terps");
    assertThat(md.z.S()).isEqualTo("Atlantic Coast");
    Arc a = md.arcs;
    assertThat(a.tip.name).isEqualTo("Louisiana Tech");
    assertThat(a.tip.z.ref).isNull();
    assertThat(a.len).isEqualTo(34L);
    assertThat(a.a.I).isEqualTo(2L);
    assertThat(a.b.I).isEqualTo(111L);
    assertThat(a.next.tip.name).isEqualTo("Virginia");
    assertThat(a.next.a.I).isEqualTo(1L);
    assertThat(a.next.b.I).isEqualTo(83L);
  }

  @Test
  @DisplayName("home and away arcs of one game are consecutive, mirrored, and dated")
  void shouldPairArcsWhenGameRecorded() {
    Graph g = Games.games(120L, 0L, 0L, 0L, 0L, 0L, 0L, 1L);
    assertThat(g.id).isEqualTo("games(120,0,0,0,0,0,128,1)");
    Vertex u = g.vertices[0];
    Arc a = u.arcs;
    Vertex v = a.tip;
    Arc back = v.arcs;
    while (back.tip != u) {
      back = back.next;
    }
    assertThat(a.a.I + back.a.I).isEqualTo(4L);
    assertThat(back.b.I).isEqualTo(a.b.I);
    assertThat(g.vertices[0].z.S()).isEqualTo("Patriot"); // games_full oracle, V0 Lafayette
  }

  @Test
  @DisplayName("a weight beyond 131072 panics with bad_specs")
  void shouldPanicWhenWeightTooLarge() {
    assertThat(Games.games(5L, 200000L, 0L, 0L, 0L, 0L, 0L, 1L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS);
  }
}
```
Add to `OracleInc3bTest` (import `Games`):
```java
  @Test
  @DisplayName("games prints exactly as the C")
  void shouldMatchOracleWhenGamesPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Games.games(120L, 0L, 0L, 0L, 0L, 0L, 0L, 1L), 0, ps))).isEqualTo(Oracle.inc3b("games_full"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Games.games(30L, 1L, 1L, 1L, 1L, 60L, 90L, 7L), 3, ps))).isEqualTo(Oracle.inc3b("games_window"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Games.games(10L, -1L, -1L, -1L, -1L, -5L, 0L, 3L), 0, ps))).isEqualTo(Oracle.inc3b("games_neg"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Games.games(5L, 200000L, 0L, 0L, 0L, 0L, 0L, 1L), 0, ps))).isEqualTo(Oracle.inc3b("games_bad"));
  }
```

Append to `TestSampleTest` (import `Games`):
```java
  @Test
  @DisplayName("stanza 7: games(60,70,80,-90,-101,60,0,999999999) at vertex 14")
  void shouldMatchSampleCorrectWhenGamesStanzaPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Games.games(60L, 70L, 80L, -90L, -101L, 60L, 0L, 999999999L), 14, ps))).isEqualTo(SampleCorrect.stanza(7));
  }
```

- [ ] **Step 2: Run to verify failure** — compilation fails on `Games`.
- [ ] **Step 3: Implement** `Games.java` with Javadoc (the 1990 college football season: 120 teams, arcs per game carrying scores as lengths, venue and date; team selection by weighted AP/UPI poll rankings; the day window), `package-info.java`.
- [ ] **Step 4: Verify pass, `spotlessApply`, `check`, commit** `Port gb_games: games`.

---

### Task 4: `Lisa` (gb_lisa): `lisa`, `planeLisa`, `biLisa`

**Files:**
- Create: `lib/src/main/java/com/robsartin/jsgb/lisa/Lisa.java`, `package-info.java`
- Create: `lib/src/test/java/com/robsartin/jsgb/lisa/LisaTest.java`
- Modify: `OracleInc3bTest.java`, `TestSampleTest.java` (stanza 9)

**Interfaces:**
- `public static long[] lisa(long m, long n, long d, long m0, long m1, long n0, long n1, long d0, long d1)` — all nine parameters are C `unsigned long`; returns the `m × n` row-major matrix or null after a panic. (The C's `Area` parameter is dropped: Java arrays need no arena.)
- `public static String lisaId = "lisa(360,250,9999999999,359,360,249,250,9999999999,9999999999)";` overwritten by every `lisa` call that passes its spec checks.
- `public static Graph planeLisa(long m, long n, long d, long m0, long m1, long n0, long n1, long d0, long d1)` (all unsigned); `public static Graph biLisa(long m, long n, long m0, long m1, long n0, long n1, long thresh, long c)` (`c` signed, the rest unsigned).
- Constants `MAX_M = 360`, `MAX_N = 250`, `MAX_D = 255`, `EL_GORDO = 0x7fffffff`; slots `pixel_value` = `x.I`, `first_pixel` = `y.I`, `last_pixel` = `z.I`, `matrix_rows` = `uu.I`, `matrix_cols` = `vv.I`; `planeLisa` utilTypes `"ZZZIIIZZIIZZZZ"`; `biLisa` sets `utilTypes.charAt(7) = 'I'` on the default string (so `"ZZZZZZZIZZZZZZ"`) and edge weights in `b.I`.

**Source:** `gb_lisa.w` sections 6–36 (tangled `gb_lisa.c`, 452 lines). Notes:
- Section 8 (all unsigned comparisons): `m1 == 0 || m1 > MAX_M → MAX_M; m1 <= m0 → BAD_SPECS + 1; n1 == 0 || n1 > MAX_N → MAX_N; n1 <= n0 → +2; capM = m1 − m0; capN = n1 − n0; m == 0 → capM; n == 0 → capN; d == 0 → MAX_D; d1 == 0 → MAX_D * capM * capN; d1 <= d0 → +3; d1 >= 0x80000000 → +4; capD = d1 − d0`; then `lisaId = "lisa(" + nine unsigned values + ")"`. Section 9: the matrix `new long[(int) (m * n)]` (a request too large for a Java array is the C's `no_room + 1`: panic with that code rather than throw).
- Section 19: `open("lisa.dat")` nonzero → `EARLY_DATA_FAULT`; `for i < m0: 5 × newline()`. Section 13: `kappa = 0; outRow = 0` (an index into the matrix); `for (k = 0, kap = 0; k < m; k++) { zero out[outRow .. outRow+n); nextKap = kap + capM; do { if (kap >= kappa) { section 21; kappa += m; } nk = min(kappa, nextKap); f = nk − kap; section 12; kap = nk; } while (kap < nextKap); for l < n: section 18 on out[outRow + l]; outRow += n; }`. (In C `out_row` advances inside the section-18 loop; the net effect is `outRow += n`.)
- Section 21 (one 250-pixel input row, base 85): `inRow = new long[MAX_N]` static; `for (j = 15, cp = 0; ; cp += 4) { dd = digit(85); dd = dd*85 + digit(85); dd = dd*85 + digit(85); if (cp == MAX_N − 2) break; dd = dd*85 + digit(85); dd = dd*85 + digit(85); inRow[cp+3] = dd & 0xff; dd = (dd >> 8) & 0xffffff; inRow[cp+2] = dd & 0xff; dd >>= 8; inRow[cp+1] = dd & 0xff; inRow[cp] = dd >> 8; if (--j == 0) { newline(); j = 15; } } inRow[cp+1] = dd & 0xff; inRow[cp] = dd >> 8; newline();`.
- Section 12: `lambda = n; cp = n0; for (l = 0, lam = 0; l < n; l++) { sum = 0; nextLam = lam + capN; do { if (lam >= lambda) { cp++; lambda += n; } nl = min(lambda, nextLam); sum += (nl − lam) * inRow[cp]; lam = nl; } while (lam < nextLam); out[outRow + l] += f * sum; }`.
- Section 18: `v <= d0 → 0; v >= d1 → d; else naOverB(d, v − d0, capD)` — `v` is a `long` compared with unsigned `d0`/`d1`, so use `Long.compareUnsigned`. `naOverB(n, a, b)` (section 15/17): `nmax = EL_GORDO / a; if (n <= nmax) return (n * a) / b;` else the bit-serial division with `bit[30]`, `aThresh = b − a`, `bThresh = (b + 1) >> 1`, transcribed verbatim.
- Section 20: `for i = m1 .. MAX_M−1: 5 × newline(); close()` nonzero → `LATE_DATA_FAULT`.
- `planeLisa`: `a = lisa(...)`; null → return null. Parse `m`, `n` back out of `lisaId` (the C's `sscanf(lisa_id, "lisa(%lu,%lu,", &m, &n)`), `f = new long[n]`, `regs = 0`. Section 28 with `apos` an `int` index that starts at `n * (m + 1) − 1` (one row *past* the matrix; the row `k == m` never dereferences it): `for (k = m; k >= 0; k--) for (l = n − 1; l >= 0; l--, apos--) { if (k < m) { if (k > 0 && a[apos − n] == a[apos]) { for (j = l; f[j] != j; j = f[j]); f[j] = l; a[apos] = l; } else if (f[l] == l) { a[apos] = −1 − a[apos]; regs++; } else a[apos] = f[l]; } if (k > 0 && l < n − 1 && a[apos − n] == a[apos − n + 1]) f[l + 1] = l; f[l] = l; }`. Section 29: `newGraph(regs)`, id `"plane_" + lisaId`, utilTypes, `uu.I = m; vv.I = n`. Section 30: `Vertex[] u = new Vertex[n]` (the C reuses `f`'s memory); `regs = 0; for (k = 0, apos = 0, aloc = 0; k < m; k++) for (l = 0; l < n; l++, apos++, aloc++) { w = u[l]; if (a[apos] < 0) { v = vertices[regs]; v.name = Long.toString(regs); v.x.I = −a[apos] − 1; v.y.I = aloc; regs++; } else v = u[(int) a[apos]]; u[l] = v; v.z.I = aloc; if (troubleCode != 0) break out; if (k > 0 && v != w) adjac(v, w); if (l > 0 && v != u[l − 1]) adjac(v, u[l − 1]); }`; `adjac(u, v)`: return if `u` already has an arc to `v`, else `newEdge(u, v, 1)`. Then `troubleCode` → recycle + `ALLOC_FAULT`.
- `biLisa`: `a = lisa(m, n, 65535, m0, m1, n0, n1, 0, 0)`; null → null; parse `m, n, m0, m1, n0, n1` from `lisaId`; `newGraph(m + n)`; id `bi_lisa(%lu,%lu,%lu,%lu,%lu,%lu,%lu,%c)` with `c != 0 ? '1' : '0'`; `utilTypes` index 7 → `'I'`; `markBipartite(g, m)`; names `"r" + k` then `"c" + l`; section 36: `apos = 0; for u in 0..m−1: for v in m..m+n−1, apos++: if (c != 0 ? compareUnsigned(a[apos], thresh) < 0 : compareUnsigned(a[apos], thresh) >= 0) { newEdge(u, v, 1); u.arcs.b.I = v.arcs.b.I = a[apos]; }` (the C compares a `long` pixel with the unsigned `thresh`).

- [ ] **Step 1: Failing tests**

`LisaTest.java`:
```java
package com.robsartin.jsgb.lisa;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LisaTest {

  @Test
  @DisplayName("lisa(4,4,255,…) reduces the whole painting to a 4x4 grey matrix and records its id")
  void shouldReduceWholePaintingWhenFourByFour() {
    long[] a = Lisa.lisa(4L, 4L, 255L, 0L, 0L, 0L, 0L, 0L, 0L);
    assertThat(Lisa.lisaId).isEqualTo("lisa(4,4,255,0,360,0,250,0,22950000)");
    assertThat(a).containsExactly(106, 110, 65, 97, 50, 66, 48, 47, 26, 30, 19, 12, 17, 34, 17, 11);
  }

  @Test
  @DisplayName("lisa with a window and explicit thresholds maps to 0..7")
  void shouldMapToRangeWhenWindowGiven() {
    long[] a = Lisa.lisa(3L, 5L, 7L, 100L, 110L, 100L, 110L, 1000L, 60000L);
    assertThat(Lisa.lisaId).isEqualTo("lisa(3,5,7,100,110,100,110,1000,60000)");
    assertThat(a).containsExactly(0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0);
  }

  @Test
  @DisplayName("plane_lisa(100,100,50,1,300,1,200,2975050,11900200) is stanza 9")
  void shouldMatchStanzaWhenSampleParametersGiven() {
    Graph g = Lisa.planeLisa(100L, 100L, 50L, 1L, 300L, 1L, 200L, 50L * 299L * 199L, 200L * 299L * 199L);
    assertThat(g.id).isEqualTo("plane_lisa(100,100,50,1,300,1,200,2975050,11900200)");
    assertThat(g.n).isEqualTo(2452L);
    assertThat(g.m).isEqualTo(10814L);
    assertThat(g.utilTypes).isEqualTo("ZZZIIIZZIIZZZZ");
    assertThat(g.uu.I).isEqualTo(100L);
    assertThat(g.vv.I).isEqualTo(100L);
    assertThat(g.vertices[1294].name).isEqualTo("1294");
    assertThat(g.vertices[1294].x.I).isEqualTo(11L);
    assertThat(g.vertices[1294].y.I).isEqualTo(2407L);
    assertThat(g.vertices[1294].z.I).isEqualTo(2408L);
    assertThat(g.vertices[1294].arcs.tip.name).isEqualTo("1295");
  }

  @Test
  @DisplayName("bi_lisa joins rows to columns whose pixel passes the threshold and stores the pixel on both arcs")
  void shouldJoinRowsToColumnsWhenBipartite() {
    Graph g = Lisa.biLisa(10L, 10L, 100L, 110L, 100L, 110L, 20000L, 1L);
    assertThat(g.id).isEqualTo("bi_lisa(10,10,100,110,100,110,20000,1)");
    assertThat(g.n).isEqualTo(20L);
    assertThat(g.m).isEqualTo(54L);
    assertThat(g.utilTypes).isEqualTo("ZZZZZZZIIZZZZZ");
    assertThat(g.uu.I).isEqualTo(10L);
    assertThat(g.vertices[12].name).isEqualTo("c2");
    assertThat(g.vertices[12].arcs.tip.name).isEqualTo("r4");
    assertThat(g.vertices[12].arcs.b.I).isEqualTo(19275L);
    assertThat(g.vertices[12].arcs.mate.b.I).isEqualTo(19275L);
  }

  @Test
  @DisplayName("an empty row window panics with bad_specs+1 and leaves the id alone")
  void shouldPanicWhenWindowEmpty() {
    Lisa.lisa(4L, 4L, 255L, 0L, 0L, 0L, 0L, 0L, 0L);
    assertThat(Lisa.planeLisa(5L, 5L, 0L, 10L, 10L, 0L, 0L, 0L, 0L)).isNull();
    assertThat(Gb.panicCode).isEqualTo(Gb.BAD_SPECS + 1);
    assertThat(Lisa.lisaId).isEqualTo("lisa(4,4,255,0,360,0,250,0,22950000)");
  }
}
```

Add to `OracleInc3bTest` (import `Lisa`):
```java
  @Test
  @DisplayName("lisa, plane_lisa and bi_lisa print exactly as the C")
  void shouldMatchOracleWhenLisaPrinted() {
    long[] a = Lisa.lisa(4L, 4L, 255L, 0L, 0L, 0L, 0L, 0L, 0L);
    assertThat(matrix(a, 4)).isEqualTo(Oracle.inc3b("lisa_matrix"));
    long[] b = Lisa.lisa(3L, 5L, 7L, 100L, 110L, 100L, 110L, 1000L, 60000L);
    assertThat(matrix(b, 5)).isEqualTo(Oracle.inc3b("lisa_window"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Lisa.planeLisa(20L, 20L, 10L, 0L, 0L, 0L, 0L, 0L, 0L), 0, ps))).isEqualTo(Oracle.inc3b("plane_lisa_small"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Lisa.planeLisa(0L, 0L, 0L, 100L, 110L, 100L, 110L, 0L, 0L), 3, ps))).isEqualTo(Oracle.inc3b("plane_lisa_window"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Lisa.biLisa(10L, 10L, 0L, 0L, 0L, 0L, 30000L, 0L), 0, ps))).isEqualTo(Oracle.inc3b("bi_lisa"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Lisa.biLisa(10L, 10L, 100L, 110L, 100L, 110L, 20000L, 1L), 12, ps))).isEqualTo(Oracle.inc3b("bi_lisa_c"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Lisa.planeLisa(5L, 5L, 0L, 10L, 10L, 0L, 0L, 0L, 0L), 0, ps))).isEqualTo(Oracle.inc3b("lisa_bad"));
  }

  /** The harness's printing of a lisa matrix: the id line, then rows of space-separated values. */
  private static String matrix(long[] a, int cols) {
    StringBuilder sb = new StringBuilder(Lisa.lisaId).append('\n');
    for (int k = 0; k < a.length; k++) {
      sb.append(a[k]).append(k % cols == cols - 1 ? '\n' : ' ');
    }
    return sb.toString();
  }
```
(`Oracle.inc3b("lisa_matrix")` must return the id line plus the rows — check the shared loader treats a case body as everything up to the next `==` line, whatever its first line is.)

Append to `TestSampleTest` (import `Lisa`):
```java
  @Test
  @DisplayName("stanza 9: plane_lisa(100,100,50,1,300,1,200,2975050,11900200) at vertex 1294")
  void shouldMatchSampleCorrectWhenPlaneLisaStanzaPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Lisa.planeLisa(100L, 100L, 50L, 1L, 300L, 1L, 200L, 50L * 299L * 199L, 200L * 299L * 199L), 1294, ps))).isEqualTo(SampleCorrect.stanza(9));
  }
```

- [ ] **Step 2: Run to verify failure** — compilation fails on `Lisa`.
- [ ] **Step 3: Implement** `Lisa.java` with Javadoc (the Mona Lisa as a 360×250 grey-level image; `lisa` averages a window down to `m × n` and rescales into `0..d`; `plane_lisa` merges 4-connected regions of equal value into vertices adjacent when regions touch; `bi_lisa` joins rows to columns by pixel threshold), `package-info.java`.
- [ ] **Step 4: Verify pass, `spotlessApply`, `check`, commit** `Port gb_lisa: lisa, plane_lisa, bi_lisa`.

---

### Task 5: `Gates` part 1 (gb_gates): `risc`, `gateEval`, `printGates`, `runRisc`

**Files:**
- Create: `lib/src/main/java/com/robsartin/jsgb/gates/Gates.java`, `package-info.java`
- Create: `lib/src/test/java/com/robsartin/jsgb/gates/GatesTest.java`
- Modify: `OracleInc3bTest.java`

**Interfaces:**
- `public static Graph risc(long regs)` (unsigned); `public static long gateEval(Graph g, String inVec, StringBuilder outVec)` (either may be null; returns −2 for a null graph, −1 for an unknown gate type, else 0; `outVec` is cleared then filled); `public static void printGates(Graph g)`; `public static long runRisc(Graph g, long[] rom, long size, long traceRegs)` (`rom` entries, `size`, `traceRegs` unsigned); `public static final long[] riscState = new long[18];` `public static PrintStream out = new PrintStream(System.out, true, StandardCharsets.ISO_8859_1);` (as `Dijkstra.out`).
- Gate-type constants (the C stores the character code in `typ` = `y.I`): `AND = '&'` (38), `OR = '|'` (124), `NOT = '~'` (126), `XOR = '^'` (94), plus `'I'` (73), `'L'` (76), `'C'` (67), `'='` (61). Slots: `val` = `x.I`, `foo` = `x.ref` (Vertex), `typ` = `y.I`, `alt` = `z.ref` (Vertex), `bit` = `z.I`, `bar` and `lnk` = `w.ref` (Vertex; they alias each other as in C), `outs` = `g.zz.A()`. `utilTypes = "ZZZIIVZZZZZZZA"`, `DELAY = 100`.
- Task 6 adds `prod`, `partialGates` and the private `reduce`; leave room (the static `nextVert`/`prefix`/`count` helpers are shared).

**Source:** `gb_gates.w` sections 3–49 (tangled `gb_gates.c`, lines 1–330 and 656–1100). Notes:
- Static builder state: `Vertex[] verts` (the graph being built) and `int nextVert` replace the C's `next_vert` pointer; `String prefix`; `long count`. `newVert(char t)`: `v = verts[nextVert++]; v.name = count < 0 ? prefix : prefix + count; if (count >= 0) count++; v.y.I = t; return v`. `startPrefix(s)`: `prefix = s; count = 0`. `numericPrefix(a, b)`: `prefix = a + b + ":"; count = 0`. `make2..make5`: `newVert(t)` then `Gb.newArc(v, vi, DELAY)` in argument order. `comp(v)`: `if (v.w.V() != null) return v.w.V(); u = verts[nextVert++]; u.w.V(v); v.w.V(u); u.name = v.name + "~"; u.y.I = NOT; Gb.newArc(u, v, 1); return u`. `makeXor`, `makeAdder(n, int[] or Vertex[] x, y, z, carry, add)` as in sections 15 and 38; `evenComp(s, v) = (s & 1) != 0 ? v : comp(v)`; `latchit(u, latch)`: `latch.z.V(make2(AND, u, runBit))`; `firstOf(n, t)`: `n` calls to `newVert(t)`, returning the **index** of the first (the C returns a pointer it later offsets with `+ k`).
- **Evaluation order is load-bearing.** Every vertex is numbered by the order in which `newVert`/`comp` allocate it, and the names and `print_sample` output depend on it. Transcribe each C expression in its textual order; Java evaluates arguments left to right, which is what the compiler that produced the oracle did. Do not hoist, reorder, or "simplify" any `comp(...)` call, and keep Knuth's `do2..do5` staging (evaluate the argument expressions into `t1..t5` in order, then call `makeN`).
- Pointer arrays: `mem[16]` are `Vertex`s; `prog` and `reg[r]` are `int` base indices (`prog + k` → `verts[prog + k]`, `reg[r] + k` → `verts[reg[r] + k]`); the C's `Vertex*` arrays that only hold gate results (`tmp`, `dest_match`, `old_dest`, `old_src`, `inc_dest`, `source`, `log`, `shift`, `sum`, `diff`, `next_loc`, `next_next_loc`, `result`, `mod`, `dest`) stay `Vertex[]`.
- Section 16: `regs < 2 || regs > 16 → 16` (unsigned); `newGraph(1400 + 115 * regs)`; id `risc(%lu)`; `verts = g.vertices; nextVert = 0`. Sections 19–36 in the order the C composes them: 19, 21, 22 (23, 24, 39, then the `source` loop), 26, 27, 41 (42, then the two adders and `sum[17]`/`diff[17]`), 29 (30, 31, 34, 35, 32, 36). Section 36's output arcs: `a = Gb.virginArc(); a.tip = make2(AND, t4, runBit); a.next = g.zz.A(); g.zz.A(a)`. Finally `nextVert != g.n → IMPOSSIBLE`; `troubleCode` → recycle + `ALLOC_FAULT`.
- Layout the tests and `runRisc` rely on: vertex 0 = `RUN`, 1..16 = `M0..M15`, 17..26 = `P0..P9`, 27 = `S`, 28 = `N`, 29 = `K`, 30 = `V`, 31 = `X`, `32 + 16r + k` = `Rr:k` (so bit 15 of register `r` is `16r + 47`).
- `gateEval` (sections 3–6): `if (g == null) return −2; vi = 0; if (inVec != null) for (i = 0; i < inVec.length() && vi < g.n; ) verts[vi++].x.I = inVec.charAt(i++) − '0'; for (; vi < g.n; vi++) { v; switch ((int) v.y.I) { 'I' → continue; 'L' → t = v.z.V().x.I; AND → t = 1, t &= tip.x.I over arcs; OR → 0, |=; XOR → 0, ^=; NOT → 1 − arcs.tip.x.I; default → return −1; } v.x.I = t; } if (outVec != null) { outVec.setLength(0); for a over outs: outVec.append((char) ('0' + tipValue(a.tip))); } return 0`, with `isBoolean(v) = v == null || v == Gb.ONE`, `theBoolean(v) = v == Gb.ONE ? 1 : 0`, `tipValue(v) = isBoolean(v) ? theBoolean(v) : v.x.I`. (`Gb.isBoolean` covers only `ONE`; define the two-case helper privately in `Gates`.)
- `printGates` (section 49) on `out`: per vertex `name + " = "`, then by type: `'I'` → `input`; `'L'` → `latch` plus `"ed " + alt.name` if `alt != null`; `'~'` → `"~ "`; `'C'` → `"constant " + bit`; `'='` → `"copy of " + alt.name`; then for each arc, `" " + (char) typ + " "` before every arc but the first, then `tip.name`; newline. Then each output arc: `Output 0`/`Output 1` for a boolean tip (`null` is 0), else `Output name`.
- `runRisc` (sections 43–47) on `out`: header `" r%-2d "` per traced register then `" P XSNKV MEM\n"`; `r = gateEval(g, "0", null); if (r < 0) return r; verts[0].x.I = 1; loop { l = 0; for a over outs: l = 2*l + a.tip.x.I; if (traceRegs != 0) section 46; if (compareUnsigned(l, size) >= 0) break; m = rom[(int) l]; for vi = 1..16: verts[vi].x.I = m & 1; m >>>= 1; gateEval(g, null, null); }`; section 46: for each traced register `r` (`compareUnsigned(r, traceRegs) < 0`): `vi = 16r + 47; m = 0; if (verts[vi].y.I == 'L') for k < 16: m = 2m + verts[vi].z.V().x.I, vi--; print "%04x "`; then the program counter from vertices 26 down to 17 (`m = 2m + alt.val`, ten bits), `x, s, n, c, o` from vertices 31, 27, 28, 29, 30 (`alt.val` each), print `String.format(Locale.ROOT, "%03x%c%c%c%c%c ", m << 2, x ? 'X' : '.', s ? 'S' : '.', n ? 'N' : '.', c ? 'K' : '.', o ? 'V' : '.')`, then `"????\n"` if `l >= size` (unsigned) else `"%04x\n"` of `rom[l]`. After the loop, if tracing: `"Execution terminated with memory address %04x.\n"` of `l`. Section 47 fills `riscState[0..15]` like the trace (for all sixteen registers, `m = 0` when the vertex is not a latch), `riscState[16] = ((((pc * 4 + x) * 2 + s) * 2 + n) * 2 + c) * 2 + o`, `riscState[17] = l`; return 0.

- [ ] **Step 1: Failing tests**

`GatesTest.java`:
```java
package com.robsartin.jsgb.gates;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Graph;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GatesTest {

  @Test
  @DisplayName("risc(2) has 1630 gates laid out RUN, M0..M15, P0..P9, S, N, K, V, X, R0:0..")
  void shouldLayOutRegistersWhenRiscBuilt() {
    Graph g = Gates.risc(2L);
    assertThat(g.id).isEqualTo("risc(2)");
    assertThat(g.n).isEqualTo(1630L);
    assertThat(g.m).isEqualTo(3972L);
    assertThat(g.utilTypes).isEqualTo("ZZZIIVZZZZZZZA");
    assertThat(g.vertices[0].name).isEqualTo("RUN");
    assertThat(g.vertices[0].y.I).isEqualTo(73L); // 'I'
    assertThat(g.vertices[1].name).isEqualTo("M0");
    assertThat(g.vertices[17].name).isEqualTo("P0");
    assertThat(g.vertices[27].name).isEqualTo("S");
    assertThat(g.vertices[31].name).isEqualTo("X");
    assertThat(g.vertices[32].name).isEqualTo("R0:0");
    assertThat(g.vertices[47].name).isEqualTo("R0:15");
    assertThat(g.vertices[47].y.I).isEqualTo(76L); // 'L'
    assertThat(g.vertices[63].name).isEqualTo("R1:15");
    assertThat(g.zz.A().tip.name).isEqualTo("Z598");
    assertThat(Gates.risc(0L).id).isEqualTo("risc(16)");
    assertThat(Gates.risc(0L).n).isEqualTo(3240L);
  }

  @Test
  @DisplayName("gate_eval reads the input vector, propagates, and writes the output vector")
  void shouldEvaluateWhenInputVectorGiven() {
    Graph g = Gates.risc(2L);
    StringBuilder out = new StringBuilder("stale");
    assertThat(Gates.gateEval(g, "10000000000000001", out)).isZero();
    assertThat(out.toString()).isEqualTo("0000000000000001");
    assertThat(Gates.gateEval(null, null, null)).isEqualTo(-2L);
    g.vertices[100].y.I = 'Q';
    assertThat(Gates.gateEval(g, null, null)).isEqualTo(-1L);
  }

  @Test
  @DisplayName("run_risc multiplies 3 by 4 on the 8-register machine and leaves the product in r4")
  void shouldMultiplyWhenRomIsTheTakeRiscProgram() {
    long[] rom = {
      0x2ff0, 0x1111, 0x1a30, 0x3333, 0x7f70, 0x5555, 0x0f8f, 0x3a21, 0x1a01, 0x0a12, 0x3a01,
      0x4000, 0x5000, 0x6000, 0x2a63, 0x0f95, 0x3063, 0x1061, 0x6ac1, 0x5fd1, 0x2a63, 0x039b,
      0x0843, 0x3463, 0x1561, 0x2863, 0x0c94, 0x4861, 0x6ac1, 0x2a63, 0x5a41, 0x0398, 0x6666,
      0x0fa7
    };
    rom[1] = 3;
    rom[3] = 4;
    rom[5] = 10;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream saved = Gates.out;
    Gates.out = new PrintStream(baos, true, StandardCharsets.ISO_8859_1);
    try {
      assertThat(Gates.runRisc(Gates.risc(8L), rom, 34L, 0L)).isZero();
    } finally {
      Gates.out = saved;
    }
    assertThat(baos.toString(StandardCharsets.ISO_8859_1)).isEmpty();
    assertThat(Gates.riscState)
        .containsExactly(65535, 4, 65535, 1, 12, 65535, 0, 6, 0, 0, 0, 0, 0, 0, 0, 0, 3968, 65535);
  }
}
```

Add to `OracleInc3bTest` (import `Gates`):
```java
  @Test
  @DisplayName("risc, gate_eval, run_risc and print_gates print exactly as the C")
  void shouldMatchOracleWhenRiscPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Gates.risc(2L), 1, ps))).isEqualTo(Oracle.inc3b("risc2_sample"));
    StringBuilder buf = new StringBuilder();
    assertThat(Gates.gateEval(Gates.risc(2L), "10000000000000001", buf)).isEqualTo(Oracle.inc3bReturn("risc2_eval"));
    assertThat(buf + "\n").isEqualTo(Oracle.inc3b("risc2_eval"));
    long[] rom = { /* the 34 words above */ };
    rom[1] = 3; rom[3] = 4; rom[5] = 10;
    Graph g = Gates.risc(8L);
    assertThat(captureGates(ps -> {
      ps.print("return=" + Gates.runRisc(g, rom, 34L, 8L) + "\n");
      for (long s : Gates.riscState) ps.print(s + " ");
      ps.print("\n");
    })).isEqualTo(Oracle.inc3b("run_risc_mult"));
    rom[5] = 7;
    assertThat(captureGates(ps -> {
      ps.print("return=" + Gates.runRisc(g, rom, 34L, 0L) + "\n");
      for (long s : Gates.riscState) ps.print(s + " ");
      ps.print("\n");
    })).isEqualTo(Oracle.inc3b("run_risc_div"));
    assertThat(captureGates(ps -> Gates.printGates(Gates.risc(2L)))).isEqualTo(Oracle.inc3b("risc2_gates"));
  }

  /** Like Oracle.capture but also routes Gates.out to the captured stream, restoring it after. */
  private static String captureGates(Consumer<PrintStream> printer) {
    PrintStream saved = Gates.out;
    try {
      return Oracle.capture(ps -> { Gates.out = ps; printer.accept(ps); });
    } finally {
      Gates.out = saved;
    }
  }
```
(Write the ROM literal out in full in the test; the same 34 words as `GatesTest`. `run_risc_mult`'s body is the 75-line trace, then `return=0`, then the eighteen state values each followed by a space, then a newline — the harness printed them with `"%lu "`.)

- [ ] **Step 2: Run to verify failure** — compilation fails on `Gates`.
- [ ] **Step 3: Implement** `Gates.java` (sections 3–49) with Javadoc (a gate graph: vertices typed `I`/`L`/`C`/`=`/`&`/`|`/`^`/`~` whose arcs point at their inputs, an output list on the graph; `risc` is Knuth's 16-bit RISC with `regs` registers; `gate_eval` propagates a bit vector; `run_risc` executes a ROM on the machine), `package-info.java`.
- [ ] **Step 4: Verify pass, `spotlessApply`, `check`, commit** `Port gb_gates part 1: risc, gate_eval, print_gates, run_risc`.

---

### Task 6: `Gates` part 2: `reduce`, `prod`, `partialGates` (stanza 4)

**Files:**
- Modify: `lib/src/main/java/com/robsartin/jsgb/gates/Gates.java`, `GatesTest.java`, `OracleInc3bTest.java`, `TestSampleTest.java` (stanza 4)

**Interfaces:**
- `public static Graph prod(long m, long n)` (unsigned); `public static Graph partialGates(Graph g, long r, long prob, long seed, StringBuilder buf)` (`r`, `prob` unsigned; `buf` may be null, else it is cleared and receives one character per input gate at or after `r`: its forced bit, or `*`); private `static Graph reduce(Graph g)`.

**Source:** `gb_gates.w` sections 51–85 (tangled lines 330–655 and 1100–1500). Notes:
- **`reduce` (sections 51–65).** Locals: `sentinel = g.vertices[(int) g.n]` (`newGraph` allocates `n + extraN` slots, so the slot exists; `prod` shrinks `g.n` before calling, and the slot at the new `n` is a real allocated vertex, as in C); `latchPtr`; `long n = 0`; aux-vertex cursor `Vertex[] auxBlock = null; int auxNext = 0, auxMax = 0` (the C's local `next_vert`/`max_next_vert`, filled from `Gb.allocAuxVertices(7)` seven at a time); `Arc availArc = null` (a free list threaded through `a.a.A()`). `g == null → MISSING_OPERAND`.
  - Outer `while (true)`: `latchPtr = null`; for `vi = 0 .. g.n − 1` (the C's `v < sentinel` pointer walk over the main array only) apply section 53 to `v = g.vertices[vi]`; then section 52 over the latch chain (`for (v = latchPtr; v != null; v = v.v.V())`: `u = v.z.V(); if u.typ == '=' → v.alt = u.alt; else if u.typ == 'C' { v.typ = 'C'; v.bit = u.bit; noConstantsYet = false }`); `if (noConstantsYet) break`.
  - Section 53 is a switch full of `goto`s. Translate them as a small tail enum computed by the case code and applied afterwards: `BREAK` (fall out of the switch), `MAKE_EQ` (`alt` already set), `MAKE_1`, `MAKE_0`, `MAKE_CONSTANT` (`bit` already set), `DONE` (skip the bar reset). Apply: `MAKE_1 → bit = 1, then MAKE_CONSTANT; MAKE_0 → bit = 0, then MAKE_CONSTANT; MAKE_CONSTANT → typ = 'C', then arcs = null; MAKE_EQ → typ = '=', then arcs = null`; then for every tail except `DONE`: `v.bar = null`; and for every tail: `v.foo = g.vertices[vi + 1]` (the C's `v + 1`; for the last vertex that is `sentinel`). `test_single_arg` is `if (v.arcs.next != null) BREAK else { v.alt = v.arcs.tip; MAKE_EQ }` and is reached from AND, OR and (by fallthrough) XOR. Cases: `'L'`: `v.v.V(latchPtr); latchPtr = v; BREAK`. `'I'`, `'C'`: `BREAK`. `'='`: `u = v.alt; if u.typ == '=' → v.alt = u.alt; else if u.typ == 'C' { v.bit = u.bit; MAKE_CONSTANT } ; BREAK`. `NOT` (54): `u = v.arcs.tip; if u.typ == '=' → u = v.arcs.tip = u.alt; if u.typ == 'C' { v.bit = 1 − u.bit; MAKE_CONSTANT } else if (u.bar != null) { v.alt = u.bar; MAKE_EQ } else { u.bar = v; v.bar = u; DONE }`. `AND` (55) and `OR` (56): the arc-list walk with `a`/`aa` and the inner `b` scan, unlinking bypassed arcs (`aa.next = a.next` or `v.arcs = a.next`), `MAKE_0`/`MAKE_1` on a contradicting or absorbing input, `MAKE_1` (AND) / `MAKE_0` (OR) when no arcs remain, else `test_single_arg`. `XOR` (57–59): as the C, including `cmp`, the `bb` scan with `double_bypass`, pushing each bypassed arc onto `availArc` via `a.a.A(availArc); availArc = a`, `MAKE_CONSTANT` with `bit = cmp` when arcless, and when `cmp != 0` section 58: find the first arc whose tip has a bar, or, at the last arc, section 59: take a fresh aux vertex `nv` (allocating seven when `auxNext == auxMax`), `nv.typ = NOT; nv.name = u.name + "~"; nv.arcs = availArc; availArc.tip = u; availArc = availArc.a.A(); nv.arcs.next = null; nv.bar = u; nv.foo = u.foo; u.foo = u.bar = nv`; then `a.tip = u.bar`; then fall into `test_single_arg`. **Transcribe the list surgery literally, `aa`/`bb` bookkeeping included, even where it looks odd; do not repair it.**
  - Section 60: walk the `foo` chain from `g.vertices[0]` until `sentinel`, setting `lnk = null` (this also clears every `bar`: same slot). For each output arc: `v = a.tip; if isBoolean(v) continue; if v.typ == '=' → v = a.tip = v.alt; if v.typ == 'C' { a.tip = v.bit == 1 ? Gb.ONE : null; continue; }` then section 61 (the `lnk`-threaded traversal counting `n`, with `if (u.index < v.index) n++` for a latch's `alt` — in `risc` no XOR gate exists and in `prod` no latch exists, so an aux vertex never reaches this comparison; document that in a comment).
  - Section 62: `ng = newGraph(n)`; `ng.id = g.id; ng.utilTypes = "ZZZIIVZZZZZZZA"; int next = 0; latchPtr = null;` walk the `foo` chain: `if (v.lnk != null) { u = ng.vertices[next++]; v.lnk = u; section 63 }` (`u.name = v.name; u.typ = v.typ; if v.typ == 'L' { u.alt = latchPtr; latchPtr = v; } reverse v.arcs in place; for a over v.arcs: newArc(u, a.tip.lnk, a.len)`). Section 64: `while (latchPtr != null) { u = latchPtr.lnk; v = u.alt; u.alt = latchPtr.alt.lnk; latchPtr = v; if (u.alt.index < u.index) section 65 }` (`v = u.alt; u.alt = ng.vertices[next++]; name = v.name + ">" + u.name; typ = OR; newArc(u.alt, v, DELAY) twice`). Then reverse `g.outs` in place and for each: `b = virginArc(); b.tip = isBoolean(a.tip) ? a.tip : a.tip.lnk; b.next = ng.outs; ng.outs = b`. `Gb.recycle(g); return ng`. (`gb_new_graph` made `ng` current, so the new arcs land in `ng`'s blocks.)
- **`prod` (sections 66–83).** `m < 2 → 2; n < 2 → 2` (unsigned). Section 69: `mpn = m + n; f = 4; j = 3; k = 5; while (k < mpn) { k = k + j; j = k − j; f++; }`. `g = newGraph((6m − 7 + 3f) * mpn)`; null → `NO_ROOM`; id `prod(%lu,%lu)`; utilTypes. Tables (the C carves them from two arenas; use separate arrays): `long[] flog = new long[mpn + 1], down = new long[mpn + 1], anc = new long[f + 1]; Vertex[] w = new Vertex[mpn], c = new Vertex[f * mpn]`. `a_pos(j) = j < m ? j + 1 : m + 5 * ((j − m) >> 1) + 3 + (((j − m) & 1) << 1)`.
  - Section 70: `verts = g.vertices; nextVert = 0; startPrefix("X"); x = firstOf(m, 'I'); startPrefix("Y"); y = firstOf(n, 'I')` (`x`, `y` are int indices). Section 72: for `j < m`: `numericPrefix('A', j)`; `j` constants (`v = newVert('C'); v.bit = 0`); `n` gates `make2(AND, verts[x + j], verts[y + k])`; `mpn − j − n` more constants. Section 73 for `j < m − 2` with `alpha`/`beta` as int indices (`a_pos(3j) * mpn`, `a_pos(3j + 1) * mpn`, then `nextVert − 2·mpn` and `a_pos(3j + 2) * mpn`, then `nextVert − 3·mpn` and `nextVert − mpn`) and prefixes `P j`, `Q j`, `A (m + 2j)`, `R j`, `A (m + 2j + 1)` (one `'C'` bit-0 vertex first, then `mpn − 1` ORs). Section 74: `alpha = a_pos(3m − 6) * mpn; beta = a_pos(3m − 5) * mpn; "U" XORs; "V" ANDs`.
  - Section 76: `flog[1] = 0; flog[2] = 2; down[1] = 0; down[2] = 1; for (i = 3, j = 2, k = 3, l = 3; l <= mpn; l++) { if (l > k) { k = k + j; j = k − j; i++; } flog[l] = i; down[l] = l − k + j; }`.
  - Section 78: `vv = nextVert − mpn; uu = vv − mpn` (indices); `startPrefix("W"); v = newVert('C'); v.bit = 0; w[0] = v; v = newVert('='); v.alt = verts[vv]; w[1] = v; for (k = 2; k < mpn; k++) { section 79: for (l = 0, j = k; ; l++, j = down[j]) { anc[l] = j; if (j == 2) break; } i = 1; cc = verts[vv + k − 1]; dd = verts[uu + k − 1]; while (true) { j = anc[l]; section 80: v = verts[nextVert++]; v.name = "B" + k + ":" + j; v.typ = AND; newArc(v, dd, DELAY); ff = flog[j − i]; newArc(v, ff > 0 ? c[k − i + (ff − 2) * mpn] : verts[vv + k − i − 1], DELAY); section 81: if (l != 0) { v = verts[nextVert++]; v.name = "C" + k + ":" + j; v.typ = OR; } else v = newVert(OR); newArc(v, cc, DELAY); newArc(v, verts[nextVert − 2], DELAY); if (flog[j] < flog[j + 1]) c[k + (flog[j] − 2) * mpn] = v; if (l == 0) break; cc = v; section 82: v = verts[nextVert++]; v.name = "D" + k + ":" + j; v.typ = AND; newArc(v, dd, DELAY); newArc(v, ff > 0 ? verts[c[k − i + (ff − 2) * mpn].index + 1] : verts[uu + k − i − 1], DELAY); dd = v; i = j; l--; } w[k] = v; }`. (The C reuses `f` for `flog[j − i]`; name it `ff` in Java. `c[...] + 1` is the vertex *after* that table entry.)
  - Section 83: `startPrefix("Z"); for k < mpn: a = virginArc(); a.tip = make2(XOR, verts[uu + k], w[k]); a.next = g.outs; g.outs = a`. Then `g.n = nextVert`; `troubleCode` → recycle + `ALLOC_FAULT`; `return reduce(g)`.
- **`partialGates` (sections 84–85).** `g == null → MISSING_OPERAND; Flip.initRand(seed); if (buf != null) buf.setLength(0); for (vi = r; compareUnsigned(vi, g.n) < 0; vi++) { v; switch typ: 'C', '=' → continue; 'I' → if (compareUnsigned(Flip.nextRand() >> 15, prob) >= 0) { v.typ = 'C'; v.bit = Flip.nextRand() >> 30; buf?.append((char) ('0' + v.bit)); } else buf?.append('*'); break; default → stop the loop; } g = reduce(g); if (g != null) { s = g.id; if (s.length() > 54) s = s.substring(0, 51) + "..."; g.id = "partial_gates(" + s + "," + unsigned(r) + "," + unsigned(prob) + "," + seed + ")"; } return g`.

- [ ] **Step 1: Failing tests**

Append to `GatesTest`:
```java
  @Test
  @DisplayName("prod(2,2) reduces to the 12-gate multiplier the C prints")
  void shouldBuildMultiplierWhenProdCalled() {
    Graph g = Gates.prod(2L, 2L);
    assertThat(g.id).isEqualTo("prod(2,2)");
    assertThat(g.n).isEqualTo(12L);
    assertThat(g.m).isEqualTo(16L);
    assertThat(g.vertices[0].name).isEqualTo("X0");
    assertThat(g.vertices[4].name).isEqualTo("A0:0");
    assertThat(g.vertices[8].name).isEqualTo("U1");
    assertThat(g.vertices[8].y.I).isEqualTo(94L); // '^'
    assertThat(g.zz.A().tip.name).isEqualTo("B3:2");
    StringBuilder out = new StringBuilder();
    assertThat(Gates.gateEval(g, "1111", out)).isZero();
    assertThat(out.toString()).isEqualTo("1001"); // 3 x 3 = 9, output bits high to low
    assertThat(Gates.prod(0L, 0L).id).isEqualTo("prod(2,2)");
  }

  @Test
  @DisplayName("partial_gates forces inputs at random, reports them in buf, and renames the graph")
  void shouldForceInputsWhenPartialGatesCalled() {
    StringBuilder buf = new StringBuilder("stale");
    Graph g = Gates.partialGates(Gates.prod(3L, 3L), 2L, 50000L, 1L, buf);
    assertThat(buf.toString()).isEqualTo("****");
    assertThat(g.id).isEqualTo("partial_gates(prod(3,3),2,50000,1)");
    assertThat(g.n).isEqualTo(39L);
    Graph h = Gates.partialGates(Gates.risc(0L), 1L, 43210L, 98765L, buf);
    assertThat(buf.toString()).isEqualTo("*1*1***101**010*");
    assertThat(h.id).isEqualTo("partial_gates(risc(16),1,43210,98765)");
    assertThat(h.n).isEqualTo(1702L);
    assertThat(h.m).isEqualTo(3796L);
    assertThat(h.vertices[79].name).isEqualTo("R10:10");
    assertThat(h.vertices[79].z.V().name).isEqualTo("Z898");
    assertThat(Gates.partialGates(null, 1L, 1L, 1L, null)).isNull();
    assertThat(com.robsartin.jsgb.graph.Gb.panicCode).isEqualTo(com.robsartin.jsgb.graph.Gb.MISSING_OPERAND);
  }
```
(The `"1001"` expectation for `prod(2,2)` on input `X0 X1 Y0 Y1 = 1111` is hand-derived from the `prod22` oracle's output order `B3:2, Z2, U1, A0:0`; if the C disagrees, the oracle wins — but the `gate_eval` result must then be explained, not just changed.)

Add to `OracleInc3bTest`:
```java
  @Test
  @DisplayName("prod and partial_gates print exactly as the C")
  void shouldMatchOracleWhenProdPrinted() {
    Graph g = Gates.prod(2L, 2L);
    assertThat(captureGates(ps -> { Gates.printGates(g); TestSample.printSample(g, 0, ps); })).isEqualTo(Oracle.inc3b("prod22"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Gates.prod(3L, 3L), 10, ps))).isEqualTo(Oracle.inc3b("prod33_sample"));
    StringBuilder buf = new StringBuilder();
    Graph p = Gates.partialGates(Gates.prod(3L, 3L), 2L, 50000L, 1L, buf);
    assertThat(Oracle.capture(ps -> { ps.print(buf + "\n"); TestSample.printSample(p, 5, ps); })).isEqualTo(Oracle.inc3b("partial_prod33"));
    Graph q = Gates.partialGates(Gates.risc(0L), 1L, 43210L, 98765L, buf);
    assertThat(Oracle.capture(ps -> { ps.print(buf + "\n"); TestSample.printSample(q, 79, ps); })).isEqualTo(Oracle.inc3b("partial_risc_stanza4"));
    assertThat(Oracle.capture(ps -> TestSample.printSample(Gates.partialGates(null, 1L, 1L, 1L, null), 0, ps))).isEqualTo(Oracle.inc3b("prod_bad"));
  }
```

Append to `TestSampleTest` (import `Gates`):
```java
  @Test
  @DisplayName("stanza 4: partial_gates(risc(0),1,43210,98765) at vertex 79")
  void shouldMatchSampleCorrectWhenGatesStanzaPrinted() {
    assertThat(Oracle.capture(ps -> TestSample.printSample(Gates.partialGates(Gates.risc(0L), 1L, 43210L, 98765L, null), 79, ps))).isEqualTo(SampleCorrect.stanza(4));
  }
```

- [ ] **Step 2: Run to verify failure** — compilation fails on `prod`/`partialGates`.
- [ ] **Step 3: Implement** `reduce`, `prod`, `partialGates` with Javadoc (`prod` is an `m × n` parallel multiplier built from a Wallace-style adder tree, always reduced; `partial_gates` fixes a random subset of the inputs from `r` on and reduces; `reduce` is the constant-propagation/duplicate-elimination pass).
- [ ] **Step 4: Verify pass, `spotlessApply`, `check`, commit** `Port gb_gates part 2: reduce, prod, partial_gates`.

---

### Task 7: complete `TestSample`, architecture rules, docs

**Files:**
- Modify: `demos/src/main/java/com/robsartin/jsgb/demo/TestSample.java` (stanzas 4–7 and 9 in sequence), `demos/src/test/java/com/robsartin/jsgb/demo/TestSampleTest.java` (`shouldMatchSampleCorrectPrefixWhenMainRuns` becomes the full-file test), `lib/src/test/java/com/robsartin/jsgb/ArchitectureTest.java`, `README.md`, `demos/src/test/resources/oracle/MANIFEST.md` (mention the custom-printed inc3b cases: `book_chapters`, `lisa_*`, `risc2_eval`, `run_risc_*`, `prod22`, `partial_*`, `risc2_gates`).

- [ ] **Step 1: Failing test** — rewrite the `main`-run test to compare against the whole file:
```java
  @Test
  @DisplayName("run reproduces sample.correct byte for byte")
  void shouldMatchSampleCorrectWhenMainRuns() throws Exception {
    String expected = Files.readString(Path.of(getClass().getResource("/oracle/sample.correct").toURI()), StandardCharsets.ISO_8859_1);
    assertThat(Oracle.capture(ps -> TestSample.run(ps, dir))).isEqualTo(expected);
    assertThat(dir.resolve("test.gb")).exists();
  }
```
  and add ArchUnit whitelist rules in the existing style: `books` → `books, graph, io, flip, sort, java`; `econ` → `econ, graph, io, flip, java`; `games` → `games, graph, io, flip, sort, java`; `lisa` → `lisa, graph, io, java`; `gates` → `gates, graph, flip, java`. Plant a control once: temporarily reference `Flip` from `Lisa`, see the rule fail, remove it, and say so in the report.
- [ ] **Step 2: Run to verify failure** — the full-file test fails on the missing stanzas (the diff starts at stanza 4).
- [ ] **Step 3: Implement** — in `TestSample.run`, replace the two comments with the C's calls in test_sample.w order: after the `gunion` stanza, `printSample(Gates.partialGates(Gates.risc(0L), 1L, 43210L, 98765L, null), 79, out); printSample(Books.book("homer", 500L, 400L, 2L, 12L, 10000L, -123456L, 789L), 81, out); printSample(Econ.econ(40L, 0L, 400L, -111L), 11, out); printSample(Games.games(60L, 70L, 80L, -90L, -101L, 60L, 0L, 999999999L), 14, out);` before `miles`, and `printSample(Lisa.planeLisa(100L, 100L, 50L, 1L, 300L, 1L, 200L, 50L * 299L * 199L, 200L * 299L * 199L), 1294, out);` between `miles` and `planeMiles`. Update the class Javadoc and README (`:demos:run` now prints all sixteen stanzas; list the five new packages in the module table).
- [ ] **Step 4: Verify pass, `spotlessApply`, `check`, commit** `Complete TestSample: all sixteen stanzas of sample.correct reproduce`.

---

## Self-review notes

- Spec coverage: gb_books, gb_econ, gb_games, gb_lisa, gb_gates and the full test_sample are the remainder of the library after increment 3a; increment 4 is the twelve demos.
- Every oracle case in `oracle_inc3b.out` is consumed by a test (Tasks 1–6); `sample.correct` is consumed whole in Task 7.
- Names used across tasks: `Books.book/biBook/chapters/chapName`, `Econ.econ`, `Games.games`, `Lisa.lisa/lisaId/planeLisa/biLisa`, `Gates.risc/gateEval/printGates/runRisc/riscState/out/prod/partialGates`, `Oracle.inc3b/inc3bReturn`.
