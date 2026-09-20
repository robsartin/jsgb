# Increment 1: Scaffold and Kernel — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the jsgb repository (Gradle, CI, ADRs, licence, data files) and port the four SGB kernel modules `gb_flip`, `gb_io`, `gb_graph`, `gb_sort` bit-exactly, with the C self-tests translated to JUnit.

**Architecture:** One Gradle subproject `lib`, base package `com.robsartin.jsgb`, one Java package per SGB module (`flip`, `io`, `graph`, `sort`). Static-method APIs with the C names; global state kept as static fields with package-visible resets. `Graph`/`Vertex`/`Arc` are mutable classes with public fields and `Util` slots. Arc storage keeps the C's blocks of 102 so that `save_graph` (increment 2) can reproduce `test.correct`.

**Tech Stack:** Java 25 (Gradle toolchain), Gradle 9.7.1 Kotlin DSL, JUnit 6.1.3, AssertJ 3.27.7, ArchUnit 1.5.0, JaCoCo, Spotless with google-java-format, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-19-jsgb-design.md`. The C sources at `~/code/sgb/*.w` are the authority for every function. Tangled `.c` files (easier to read than CWEB) can be produced with `ctangle gb_flip.w` etc. after `brew install cweb`; a tangled copy already exists at `/private/tmp/claude-501/-Users-sartin/022e4f66-ed5a-4350-bddf-2203947541a8/scratchpad/sgb-build/` if that directory still exists.

## Global Constraints

- Base package and Gradle group: `com.robsartin.jsgb`. Root project name `jsgb`.
- JDK 25 toolchain. Gradle 9.7.1 wrapper.
- Formatting: google-java-format via Spotless; `./gradlew spotlessApply` before every commit; CI runs `spotlessCheck`.
- Coverage gate: JaCoCo line ≥ 0.80, branch ≥ 0.65, wired into `check`.
- Test names `should<Expected>When<Condition>` with `@DisplayName`. Pure TDD: write the test, run it, see it fail for the right reason, then implement.
- All C `long`/`unsigned long` → Java `long`. Data files are read as ISO-8859-1 bytes.
- Global state is static, single-threaded. Every class with static mutable state exposes a package-private `reset()` and tests call it in `@BeforeEach`.
- Stage commits by explicit path (`git add <files>`), never `git add -A`.
- Commit messages end with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- Work on branch `1-increment-1-kernel` (created by the orchestrator). Do not push; the orchestrator pushes and opens the PR.
- The `.dat` files and `test.dat` are copied byte for byte from `~/code/sgb/` and never edited. Verify with `shasum -a 256`.
- Javadoc on every public type and method carries the C's meaning in prose. Each package has a `package-info.java` with the module introduction (paraphrased from the `.w` file's opening section; do not paste Knuth's text verbatim beyond a sentence).

## File structure

```
jsgb/
  settings.gradle.kts                      root name, include("lib")
  build.gradle.kts                         empty except plugins block (conventions live in lib for now)
  gradle/libs.versions.toml                dependency versions
  gradle/wrapper/...                       Gradle 9.7.1 wrapper
  gradlew, gradlew.bat
  .gitignore
  LICENSE                                  MIT, Robert Sartin, 2026
  NOTICE                                   Stanford GraphBase copyright text verbatim
  README.md                                what jsgb is, how to build, how to run tests
  .github/workflows/ci.yml                 JDK 25, ./gradlew --no-daemon clean check
  .github/workflows/adr-immutability.yml   copied from ~/code/marshal
  docs/adr/                                ADR baseline (Task 2)
  lib/build.gradle.kts                     java-library, jacoco, spotless, archunit
  lib/src/main/java/com/robsartin/jsgb/
    flip/Flip.java                         gb_flip
    flip/package-info.java
    io/GbIo.java                           gb_io
    io/package-info.java
    graph/Util.java                        the util union
    graph/Vertex.java
    graph/Arc.java
    graph/Graph.java
    graph/Gb.java                          gb_graph procedures and globals
    graph/package-info.java
    sort/Sortable.java                     the {key, link} node contract
    sort/LinkSort.java                     gb_linksort
    sort/package-info.java
  lib/src/main/resources/sgb/*.dat         11 data files, unmodified
  lib/src/main/resources/sgb/README.md     provenance statement
  lib/src/test/java/com/robsartin/jsgb/
    flip/FlipTest.java
    io/GbIoTest.java
    graph/GbTest.java
    graph/GraphTest.java
    sort/LinkSortTest.java
    ArchitectureTest.java
  lib/src/test/resources/sgb/test.dat      unmodified, for GbIoTest
```

---

### Task 1: Gradle scaffold and `Flip` (gb_flip)

The scaffold is folded into this task because the first test needs it. `Flip` is chosen first because it has no dependencies and the C ships exact checkpoint values.

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `lib/build.gradle.kts`, `.gitignore`, `LICENSE`, `NOTICE`, `README.md`, `.github/workflows/ci.yml`, `.github/workflows/adr-immutability.yml`
- Create: `lib/src/main/java/com/robsartin/jsgb/flip/Flip.java`, `lib/src/main/java/com/robsartin/jsgb/flip/package-info.java`
- Test: `lib/src/test/java/com/robsartin/jsgb/flip/FlipTest.java`

**Interfaces:**
- Produces: `public final class Flip` with `static long nextRand()`, `static long flipCycle()`, `static void initRand(long seed)`, `static long unifRand(long m)`, package-private `static void seedArray(long seed)` (the seeding loop without the five warm-up cycles), package-private `static long a(int i)` (reads the state array), package-private `static void reset()`.

- [ ] **Step 1: Generate the Gradle wrapper and write the build files**

```bash
cd ~/code/jsgb && gradle wrapper --gradle-version 9.7.1 --distribution-type bin
```

`settings.gradle.kts`:
```kotlin
rootProject.name = "jsgb"
include("lib")
```

`build.gradle.kts` (root):
```kotlin
plugins {
    id("com.diffplug.spotless") version "8.10.0" apply false
}
```

`gradle/libs.versions.toml`:
```toml
[versions]
junit = "6.1.3"
archunit = "1.5.0"
assertj = "3.27.7"

[libraries]
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher" }
archunit-junit5 = { module = "com.tngtech.archunit:archunit-junit5", version.ref = "archunit" }
assertj = { module = "org.assertj:assertj-core", version.ref = "assertj" }
```

`lib/build.gradle.kts`:
```kotlin
plugins {
    `java-library`
    jacoco
    id("com.diffplug.spotless")
}

group = "com.robsartin.jsgb"
version = "0.1.0-SNAPSHOT"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

repositories { mavenCentral() }

dependencies {
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.archunit.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // SGB is a single-threaded library with global state; never run tests in parallel.
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
    jvmArgs("-ea")
    finalizedBy(tasks.jacocoTestReport)
}

spotless {
    java { googleJavaFormat() }
}

jacoco { toolVersion = "0.8.15" }

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit { counter = "LINE"; minimum = "0.80".toBigDecimal() }
            limit { counter = "BRANCH"; minimum = "0.65".toBigDecimal() }
        }
    }
}

tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }
```

JaCoCo 0.8.15 is the `<release>` in `https://repo1.maven.org/maven2/org/jacoco/jacoco/maven-metadata.xml` as of 2026-09-19.

`.gitignore`:
```
.gradle/
build/
*.iml
.idea/
.DS_Store
```

`LICENSE`: the MIT licence text with `Copyright (c) 2026 Robert Sartin`.

`NOTICE`:
```
jsgb is a Java re-implementation of the Stanford GraphBase. The Java source
code is original work licensed under the MIT licence (see LICENSE).

The data files under lib/src/main/resources/sgb/ and the test fixtures
sample.correct, test.correct and test.dat are unmodified files from the
Stanford GraphBase and carry the following notice:

    The Stanford GraphBase is copyright 1993 by Stanford University

    These files may be freely copied and distributed, provided that
    no changes whatsoever are made. All users are asked to help keep
    the Stanford GraphBase sources consistent and ``uncorrupted,''
    identical everywhere in the world. Changes are permissible only
    if the changed file is given a new name, different from the names of
    existing files listed below, and only if the changed file is
    clearly identified as not being part of the Stanford GraphBase.
    The author has tried his best to produce correct and useful programs,
    in order to help promote computer science research, but no warranty
    of any kind should be assumed.

Reference: Stanford GraphBase release 2025-12-28, as mirrored at
https://github.com/ascherer/sgb commit 88fac2f051f445d68521dbe6cb756a43e5d53e8a.
```

`README.md`:
```markdown
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
`com.robsartin.jsgb` (`flip`, `io`, `graph`, `sort`, then the generators).
Data files ship unmodified under `lib/src/main/resources/sgb/`; a
`jsgb.data.dir` system property names a directory to search before the
classpath, playing the role of the C `DATA_DIRECTORY`.

## Licence

MIT for the Java code. The Stanford GraphBase data files are redistributed
unmodified under their own terms; see `NOTICE`.
```

`.github/workflows/ci.yml`: copy `~/code/marshal/.github/workflows/ci.yml` and change every `21` to `25` and the job name to `build & verify (JDK 25)`. `.github/workflows/adr-immutability.yml`: copy `~/code/marshal/.github/workflows/adr-immutability.yml` unchanged.

- [ ] **Step 2: Write the failing test**

`lib/src/test/java/com/robsartin/jsgb/flip/FlipTest.java`:
```java
package com.robsartin.jsgb.flip;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlipTest {

  @BeforeEach
  void resetGenerator() {
    Flip.reset();
  }

  @Test
  @DisplayName("seeding with -314159 produces Knuth's published intermediate values before warm-up")
  void shouldMatchPublishedCheckpointsWhenSeededWithMinus314159() {
    Flip.seedArray(-314159L);
    assertThat(Flip.a(42)).isEqualTo(2147326568L);
    assertThat(Flip.a(8)).isEqualTo(1073977445L);
    assertThat(Flip.a(29)).isEqualTo(536517481L);
  }

  @Test
  @DisplayName("test_flip: first value after init_rand(-314159) is 119318998")
  void shouldReturn119318998WhenFirstDrawAfterSeedingWithMinus314159() {
    Flip.initRand(-314159L);
    assertThat(Flip.nextRand()).isEqualTo(119318998L);
  }

  @Test
  @DisplayName("test_flip: unif_rand(0x55555555) after 134 draws is 748103812")
  void shouldReturn748103812WhenUnifRandCalledAfter134Draws() {
    Flip.initRand(-314159L);
    for (int j = 0; j < 134; j++) {
      Flip.nextRand();
    }
    assertThat(Flip.unifRand(0x55555555L)).isEqualTo(748103812L);
  }

  @Test
  @DisplayName("first five draws after init_rand(-314159) match the C library")
  void shouldMatchCSequenceWhenDrawingFiveValues() {
    Flip.initRand(-314159L);
    long[] actual = new long[5];
    for (int i = 0; i < 5; i++) {
      actual[i] = Flip.nextRand();
    }
    assertThat(actual).containsExactly(119318998L, 1301097714L, 451151173L, 51016514L, 374261376L);
  }

  @Test
  @DisplayName("seed 0 is a valid seed and matches the C library")
  void shouldMatchCSequenceWhenSeedIsZero() {
    Flip.initRand(0L);
    assertThat(Flip.nextRand()).isEqualTo(2029883356L);
    assertThat(Flip.nextRand()).isEqualTo(2073281797L);
    assertThat(Flip.nextRand()).isEqualTo(759676350L);
    assertThat(Flip.unifRand(1000L)).isEqualTo(240L);
  }

  @Test
  @DisplayName("unif_rand never returns a value outside [0, m)")
  void shouldStayInRangeWhenUnifRandCalledRepeatedly() {
    Flip.initRand(42L);
    for (int i = 0; i < 10_000; i++) {
      long r = Flip.unifRand(7L);
      assertThat(r).isBetween(0L, 6L);
    }
  }
}
```

The expected values were produced by the C library in this order: `test_flip.c` (first two), then a harness calling `gb_init_rand(-314159L)` and printing five `gb_next_rand()` values, then `gb_init_rand(0L)`, three `gb_next_rand()`, one `gb_unif_rand(1000L)`.

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd ~/code/jsgb && ./gradlew :lib:test --tests 'com.robsartin.jsgb.flip.FlipTest'`
Expected: compilation FAILS with `cannot find symbol ... class Flip`.

- [ ] **Step 4: Write `Flip`**

`lib/src/main/java/com/robsartin/jsgb/flip/Flip.java`:
```java
package com.robsartin.jsgb.flip;

/**
 * Port of {@code gb_flip}: Knuth's portable subtractive lagged-Fibonacci pseudo-random number
 * generator, {@code a[n] = (a[n-55] - a[n-24]) mod 2^31}.
 *
 * <p>State is global, as in C. Every SGB generator that takes a {@code seed} calls {@link
 * #initRand(long)}; the sequence of values it then draws is defined bit for bit by this class.
 */
public final class Flip {

  private static final long TWO_TO_THE_31 = 0x80000000L;

  /** The C array {@code A[56]}; {@code A[0] = -1} is the sentinel that triggers a refill. */
  private static final long[] A = new long[56];

  /** Index form of the C pointer {@code gb_fptr}. */
  private static int fptr;

  static {
    reset();
  }

  private Flip() {}

  /** Restores the generator to its just-loaded state (unseeded). Tests only. */
  static void reset() {
    java.util.Arrays.fill(A, 0L);
    A[0] = -1;
    fptr = 0;
  }

  /** Reads {@code A[i]}. Tests only. */
  static long a(int i) {
    return A[i];
  }

  private static long modDiff(long x, long y) {
    return (x - y) & 0x7fffffffL;
  }

  /** {@code gb_next_rand()}: the next value in [0, 2^31). */
  public static long nextRand() {
    return A[fptr] >= 0 ? A[fptr--] : flipCycle();
  }

  /** {@code gb_flip_cycle()}: computes 55 more values and returns the first of them. */
  public static long flipCycle() {
    int ii;
    int jj;
    for (ii = 1, jj = 32; jj <= 55; ii++, jj++) {
      A[ii] = modDiff(A[ii], A[jj]);
    }
    for (jj = 1; ii <= 55; ii++, jj++) {
      A[ii] = modDiff(A[ii], A[jj]);
    }
    fptr = 54;
    return A[55];
  }

  /** {@code gb_init_rand(seed)}: seeds the generator. Any {@code long} is a valid seed. */
  public static void initRand(long seed) {
    seedArray(seed);
    for (int k = 0; k < 5; k++) {
      flipCycle();
    }
  }

  /** The seeding loop of {@code gb_init_rand} without the five warm-up cycles. Tests only. */
  static void seedArray(long seed) {
    long prev = seed;
    long next = 1;
    seed = prev = modDiff(prev, 0);
    A[55] = prev;
    for (int i = 21; i != 0; i = (i + 21) % 55) {
      A[i] = next;
      next = modDiff(prev, next);
      if ((seed & 1) != 0) {
        seed = 0x40000000L + (seed >> 1);
      } else {
        seed >>= 1;
      }
      next = modDiff(next, seed);
      prev = A[i];
    }
  }

  /** {@code gb_unif_rand(m)}: a uniform value in [0, m), by rejection, for 0 < m < 2^31. */
  public static long unifRand(long m) {
    long t = TWO_TO_THE_31 - (TWO_TO_THE_31 % m);
    long r;
    do {
      r = nextRand();
    } while (t <= r);
    return r % m;
  }
}
```

`lib/src/main/java/com/robsartin/jsgb/flip/package-info.java`:
```java
/**
 * GB_FLIP: portable pseudo-random numbers.
 *
 * <p>SGB needs random numbers that are identical on every machine, so it uses its own
 * generator rather than the platform's. The values are 31-bit, the period is enormous, and the
 * lagged-Fibonacci recurrence needs only subtraction. Every generator module seeds it through
 * {@link com.robsartin.jsgb.flip.Flip#initRand(long)} before drawing, which is what makes a
 * graph such as {@code words(100, wt, 0, 69)} the same graph everywhere.
 */
package com.robsartin.jsgb.flip;
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew spotlessApply && ./gradlew :lib:test --tests 'com.robsartin.jsgb.flip.FlipTest'`
Expected: BUILD SUCCESSFUL, 6 tests passed. If the checkpoint test fails, the seeding loop is wrong; compare against `gb_flip.w` section 8 and 9 rather than adjusting the expected numbers.

- [ ] **Step 6: Run the full gate**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL (Spotless clean, coverage gate satisfied since `Flip` is fully exercised).

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle gradlew gradlew.bat .gitignore LICENSE NOTICE README.md .github lib/build.gradle.kts lib/src/main/java/com/robsartin/jsgb/flip lib/src/test/java/com/robsartin/jsgb/flip
git commit -m "Scaffold Gradle build and port gb_flip

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: ADR baseline and project decisions

Documentation only; verified by the ADR index regenerating and the immutability workflow being in place. No unit test applies (config/docs exception, stated here).

**Files:**
- Create: `docs/adr/*.md` via adr-toolkit, then seven project ADRs numbered after the emitted ones.

- [ ] **Step 1: Install the adr-toolkit engine and emit the baseline**

```bash
P=/Users/sartin/.claude/plugins/cache/claude-config/adr-toolkit/badecc54d030
"$P/bin/install.sh"
"$P/.venv/bin/adr-toolkit" --manifest "$P/packs.yaml" --packs-dir "$P/packs" \
  --target ~/code/jsgb/docs/adr --project jsgb \
  --pack universal --pack java --pack cli
ls ~/code/jsgb/docs/adr
```

`install.sh` creates `$P/.venv` and prints the CLI path `$P/.venv/bin/adr-toolkit`. Packs are fixed: `universal`, `java` (pulls in `jvm`), `cli` (the demos are command-line programs).

- [ ] **Step 2: Reconcile the emitted JVM/Java ADRs with this project**

Read the emitted Java conventions ADR. It will say "Optional over returning null at boundaries" and "records for value types". jsgb deliberately deviates; the project ADRs below record the deviation and reference the baseline ADR by number as `related`. Do not edit the emitted ADRs.

- [ ] **Step 3: Write the seven project ADRs**

Number them consecutively after the last emitted ADR (call the first `N`). Each file follows the emitted frontmatter format (`status`, `date`, `topic`, `tags`, `supersedes`, `related`) and has the four sections Context, Decision, Alternatives considered, Consequences. Content, in prose (write full paragraphs; these summaries are the required substance):

1. `NNNN-bit-exact-port.md` — Context: SGB is a benchmark platform whose value is reproducibility across machines. Decision: jsgb reproduces the C output byte for byte; `sample.correct`, `test.correct`, and captured demo outputs are acceptance oracles; the C source is the specification. Alternatives: behavioural equivalence (rejected: loses the benchmark property), same-spirit Java library (rejected: a different project). Consequences: RNG, sort tie-breaking, hash, and memory-layout-dependent behaviours must be reproduced; idiomatic Java is subordinate.
2. `NNNN-static-global-state.md` — Decision: SGB globals stay static in the owning class with package-private resets; JUnit single-threaded. Alternative: a context object threaded through every call (rejected: breaks the C API shape, no benefit at SGB scale). Consequence: the library is not thread-safe, by design.
3. `NNNN-public-mutable-fields.md` — Decision: `Graph`, `Vertex`, `Arc`, `Util` expose public mutable fields mirroring the C structs. Alternative: getters and setters (rejected: triples call-site noise across every generator and demo). Related: the emitted Java conventions ADR.
4. `NNNN-null-plus-panic-code.md` — Decision: generator failure returns null and sets `Gb.panicCode`, as in C. Alternative: a `PanicException` (rejected: every C call site and `test_sample` branch on the code and print it; exceptions would have to be caught at each to reproduce output). Programming errors C left undefined throw `IllegalStateException`.
5. `NNNN-ordering-without-pointers.md` — Decision: `Vertex.index` stands in for address order in `newEdge`; `Arc.mate` replaces pointer arithmetic; arc blocks of 102 are retained so `save` can number arcs as C does. Alternatives: `System.identityHashCode` ordering (rejected: not stable), a flat arc list (rejected: cannot reproduce `test.correct`).
6. `NNNN-javadoc-as-literate-layer.md` — Decision: package-info and Javadoc carry the exposition; no CWEB. Alternative: CWEB for Java (rejected: Gradle, IDEs, and JUnit want `.java` on disk; every edit would go through ctangle).
7. `NNNN-hand-rolled-demo-arguments.md` — Decision: demo argument parsing is hand-written to match the C. Alternative: picocli (rejected: usage messages are part of the oracle output).

- [ ] **Step 4: Regenerate the index**

Re-run the emit command from Step 1 with `--plan` first to confirm it would add nothing new, then rebuild the README. If the toolkit has no index-only command, edit `docs/adr/README.md` by hand to add a `## Project` section listing the seven ADRs in the same format as the emitted entries.

- [ ] **Step 5: Verify**

Run: `ls docs/adr | wc -l` and `grep -c "^- \[" docs/adr/README.md`
Expected: the README lists every numbered ADR file. `./gradlew check` still passes (no code changed).

- [ ] **Step 6: Commit**

```bash
git add docs/adr
git commit -m "Add ADR baseline and record the port's foundational decisions

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: `GbIo` (gb_io) with data files

**Files:**
- Create: `lib/src/main/java/com/robsartin/jsgb/io/GbIo.java`, `lib/src/main/java/com/robsartin/jsgb/io/package-info.java`
- Create: `lib/src/main/resources/sgb/{anna,david,econ,games,homer,huck,jean,lisa,miles,roget,words}.dat`, `lib/src/main/resources/sgb/README.md`
- Create: `lib/src/test/resources/sgb/test.dat`
- Test: `lib/src/test/java/com/robsartin/jsgb/io/GbIoTest.java`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `public final class GbIo` with `static long ioErrors`; constants `CANT_OPEN_FILE=0x1, CANT_CLOSE_FILE=0x2, BAD_FIRST_LINE=0x4, BAD_SECOND_LINE=0x8, BAD_THIRD_LINE=0x10, BAD_FOURTH_LINE=0x20, FILE_ENDED_PREMATURELY=0x40, MISSING_NEWLINE=0x80, WRONG_NUMBER_OF_LINES=0x100, WRONG_CHECKSUM=0x200, NO_FILE_OPEN=0x400, BAD_LAST_LINE=0x800`, `UNEXPECTED_CHAR=127`; methods `static void rawOpen(String f)`, `static long open(String f)`, `static long close()`, `static long rawClose()`, `static void newline()`, `static boolean eof()`, `static char ch()`, `static void backup()`, `static long digit(int d)`, `static long number(int d)`, `static String string(char c)`, `static char imapChr(long d)`, `static long imapOrd(int c)`, `static long newChecksum(byte[] s, long old)`, package-private `static void reset()`.

Behavioural notes that matter for fidelity (from `gb_io.w`):
- The C reads each line with `fgets(buffer, 81, file)`: at most 80 bytes, stopping after a newline. A longer line is delivered in pieces and sets `MISSING_NEWLINE`. Reproduce this limit.
- After reading, trailing spaces are stripped and a single `'\n'` is appended, then a terminator. `ch()` returns bytes up to and including that `'\n'`, then returns `'\n'` forever without advancing. `backup()` moves back one position but never before the start.
- `digit(d)` and `number(d)` read while `imapOrd(current) < d`; at the terminator they stop (the C does this with the `icode[0] = d` trick).
- `string(c)` collects bytes until the terminator or `c`, not consuming `c`.
- Header lines 1 to 4 are not checksummed. `newline()` increments `lineNo`; when it exceeds `totLines` the file is marked exhausted without reading. Otherwise it reads the next line and, unless it begins with `*`, folds it into `magic` via `newChecksum` over the line bytes including the `'\n'`.
- The checksum: `a = (a + a + imapOrd(byte)) % 1073741741` per byte.
- `imap` is the 96-byte string `"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_^~&@,;.:?!%#$+-*/|\\<=>()[]{}`'\" \n"`; bytes not in it have code 127.
- `open(f)` keeps `fileName = f` truncated to 19 characters (`strncpy(file_name, f, 19)`), checks line 1 starts with `* File "f"` (untruncated `f`), lines 2 and 3 start with `*`, line 4 starts with `* (Checksum parameters ` followed by `totLines`, `,`, `finalMagic`, `)`; then calls `newline()`.
- `close()`: with no file open returns `NO_FILE_OPEN`; reads one more line and requires it to start with `* End of file "fileName"` else `BAD_LAST_LINE`; marks exhausted; closes; then `lineNo != totLines + 1` → `WRONG_NUMBER_OF_LINES` (returned immediately); then `magic != finalMagic` → `WRONG_CHECKSUM`.
- `rawClose()` closes without checks and returns `magic`.
- File resolution, in order: `f` as a filesystem path; `System.getProperty("jsgb.data.dir") + "/" + f` if the property is set; classpath resource `/sgb/f`. First hit wins; none → `CANT_OPEN_FILE`. (The C tries the bare name then `DATA_DIRECTORY`; the classpath is the extra fallback.)

- [ ] **Step 1: Copy the data files and write the provenance README**

```bash
cd ~/code/jsgb
mkdir -p lib/src/main/resources/sgb lib/src/test/resources/sgb
for f in anna david econ games homer huck jean lisa miles roget words; do cp -p ~/code/sgb/$f.dat lib/src/main/resources/sgb/; done
cp -p ~/code/sgb/test.dat lib/src/test/resources/sgb/
(cd ~/code/sgb && shasum -a 256 *.dat) > /tmp/sgb-dat.sha
(cd lib/src/main/resources/sgb && shasum -a 256 *.dat; cd ../../../test/resources/sgb && shasum -a 256 test.dat) | sort > /tmp/jsgb-dat.sha
sort /tmp/sgb-dat.sha | diff - /tmp/jsgb-dat.sha && echo IDENTICAL
```
Expected: `IDENTICAL`.

`lib/src/main/resources/sgb/README.md`:
```markdown
These eleven `.dat` files are unmodified files from the Stanford GraphBase,
copyright 1993 Stanford University, redistributed under the terms in the
repository `NOTICE`. Do not edit them; `gb_open` verifies their checksums.
Source: https://github.com/ascherer/sgb commit 88fac2f051f445d68521dbe6cb756a43e5d53e8a.
```

- [ ] **Step 2: Write the failing test (translation of `test_io.c` plus resolution cases)**

`lib/src/test/java/com/robsartin/jsgb/io/GbIoTest.java`:
```java
package com.robsartin.jsgb.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GbIoTest {

  @BeforeEach
  void resetIo() {
    GbIo.reset();
  }

  @AfterEach
  void closeAnyFile() {
    GbIo.rawClose();
    System.clearProperty("jsgb.data.dir");
  }

  @Test
  @DisplayName("test_io: reads test.dat exactly as the C self-test expects")
  void shouldPassTranslatedCSelfTestWhenReadingTestDat() {
    assertThat(GbIo.open("test.dat")).isZero();

    assertThat(GbIo.number(10)).isEqualTo(123456789L);
    assertThat(GbIo.digit(16)).isEqualTo(10L);
    GbIo.backup();
    GbIo.backup();
    assertThat(GbIo.number(16)).isEqualTo(0x9ABCDEFL);
    GbIo.newline();
    assertThat(GbIo.ch()).isEqualTo('\n');
    assertThat(GbIo.ch()).isEqualTo('\n');
    assertThat(GbIo.number(60)).isZero();
    assertThat(GbIo.string('\n')).isEmpty();
    GbIo.newline();
    assertThat(GbIo.string(':')).isEqualTo("Oops");
    assertThat(GbIo.ioErrors).isZero();
    assertThat(GbIo.digit(10)).isEqualTo(-1L);
    assertThat(GbIo.ch()).isEqualTo(':');
    assertThat(GbIo.eof()).isFalse();
    GbIo.newline();
    assertThat(GbIo.eof()).isTrue();

    assertThat(GbIo.close()).isZero();
  }

  @Test
  @DisplayName("open reports cant_open_file when the file exists nowhere")
  void shouldReportCantOpenFileWhenFileMissing() {
    assertThat(GbIo.open("no-such-file.dat")).isEqualTo(GbIo.CANT_OPEN_FILE);
    assertThat(GbIo.ioErrors).isEqualTo(GbIo.CANT_OPEN_FILE);
  }

  @Test
  @DisplayName("close reports no_file_open when nothing is open")
  void shouldReportNoFileOpenWhenClosingWithoutOpen() {
    assertThat(GbIo.close()).isEqualTo(GbIo.NO_FILE_OPEN);
  }

  @Test
  @DisplayName("data files ship on the classpath and open cleanly")
  void shouldOpenWordsDatFromClasspathWhenNoDirectoryGiven() {
    assertThat(GbIo.open("words.dat")).isZero();
    assertThat(GbIo.string(' ')).isEqualTo("aargh");
    GbIo.rawClose();
  }

  @Test
  @DisplayName("every data file closes with a clean checksum when read to the end")
  void shouldVerifyChecksumWhenEachDataFileIsReadCompletely() {
    for (String name :
        new String[] {
          "anna", "david", "econ", "games", "homer", "huck", "jean", "lisa", "miles", "roget",
          "words"
        }) {
      GbIo.reset();
      assertThat(GbIo.open(name + ".dat")).as(name).isZero();
      while (!GbIo.eof()) {
        GbIo.newline();
      }
      assertThat(GbIo.close()).as(name).isZero();
    }
  }

  @Test
  @DisplayName("jsgb.data.dir is searched before the classpath")
  void shouldReadFromDataDirWhenPropertySet() throws Exception {
    java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("jsgb-data");
    java.nio.file.Path bogus = dir.resolve("words.dat");
    java.nio.file.Files.writeString(bogus, "not a graphbase file\n", StandardCharsets.ISO_8859_1);
    System.setProperty("jsgb.data.dir", dir.toString());
    assertThat(GbIo.open("words.dat")).isEqualTo(GbIo.BAD_FIRST_LINE);
  }

  @Test
  @DisplayName("imap_ord and imap_chr are inverse over the 96-character alphabet")
  void shouldRoundTripWhenMappingEveryImapCharacter() {
    for (int d = 0; d < 96; d++) {
      char c = GbIo.imapChr(d);
      assertThat(GbIo.imapOrd(c)).as("code %d", d).isEqualTo(d);
    }
    assertThat(GbIo.imapChr(96)).isEqualTo('\0');
    assertThat(GbIo.imapChr(-1)).isEqualTo('\0');
    assertThat(GbIo.imapOrd(0x80)).isEqualTo(GbIo.UNEXPECTED_CHAR);
  }

  @Test
  @DisplayName("new_checksum folds bytes as (a + a + code) mod 1073741741")
  void shouldFoldChecksumWhenGivenBytes() {
    byte[] s = "1\n".getBytes(StandardCharsets.ISO_8859_1);
    // code('1') = 1, code('\n') = 95: ((0+0+1)*2 + 95) = 97
    assertThat(GbIo.newChecksum(s, 0L)).isEqualTo(97L);
  }
}
```

The first data line of `words.dat` is exactly `aargh` (verified with `sed -n 5p ~/code/sgb/words.dat | od -c`), so `string(' ')` runs to the end of the line and returns it.

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :lib:test --tests 'com.robsartin.jsgb.io.GbIoTest'`
Expected: compilation FAILS with `cannot find symbol ... GbIo`.

- [ ] **Step 4: Write `GbIo`**

`lib/src/main/java/com/robsartin/jsgb/io/GbIo.java`:
```java
package com.robsartin.jsgb.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Port of {@code gb_io}: reads the GraphBase data files in a system-independent, checksum-verified
 * way. Files are read as ISO-8859-1 bytes; the API is byte-oriented like the C.
 *
 * <p>Only one file can be open at a time; the state is global as in C.
 */
public final class GbIo {

  public static final long CANT_OPEN_FILE = 0x1;
  public static final long CANT_CLOSE_FILE = 0x2;
  public static final long BAD_FIRST_LINE = 0x4;
  public static final long BAD_SECOND_LINE = 0x8;
  public static final long BAD_THIRD_LINE = 0x10;
  public static final long BAD_FOURTH_LINE = 0x20;
  public static final long FILE_ENDED_PREMATURELY = 0x40;
  public static final long MISSING_NEWLINE = 0x80;
  public static final long WRONG_NUMBER_OF_LINES = 0x100;
  public static final long WRONG_CHECKSUM = 0x200;
  public static final long NO_FILE_OPEN = 0x400;
  public static final long BAD_LAST_LINE = 0x800;

  /** Code returned by {@link #imapOrd(int)} for a byte outside the GraphBase alphabet. */
  public static final int UNEXPECTED_CHAR = 127;

  /** Record of anomalies noted by the routines; a bitmask of the constants above. */
  public static long ioErrors;

  private static final int MAX_LINE = 80; // fgets(buffer, 81, ...)
  private static final long CHECKSUM_PRIME = (1L << 30) - 83;
  private static final byte[] IMAP =
      ("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
              + "abcdefghijklmnopqrstuvwxyz_^~&@,;.:?!%#$+-*/|\\<=>()[]{}`'\" \n")
          .getBytes(StandardCharsets.ISO_8859_1);
  private static final int[] ICODE = new int[256];

  static {
    java.util.Arrays.fill(ICODE, UNEXPECTED_CHAR);
    for (int k = 0; k < IMAP.length; k++) {
      ICODE[IMAP[k] & 0xff] = k;
    }
  }

  /** Current line: bytes 0..lineEnd-1 are content, byte lineEnd is '\n'; beyond is the terminator. */
  private static byte[] buffer = new byte[MAX_LINE + 2];

  private static int lineEnd; // index of the '\n'
  private static int curPos;
  private static InputStream curFile;
  private static long magic;
  private static long lineNo;
  private static long finalMagic;
  private static long totLines;
  private static boolean moreData;
  private static String fileName = "";

  private GbIo() {}

  /** Restores the just-loaded state. Tests only. */
  static void reset() {
    if (curFile != null) {
      try {
        curFile.close();
      } catch (IOException ignored) {
        // nothing sensible to do
      }
    }
    curFile = null;
    ioErrors = 0;
    lineEnd = 0;
    buffer[0] = '\n';
    curPos = 0;
    magic = lineNo = finalMagic = totLines = 0;
    moreData = false;
    fileName = "";
  }

  // ---- character codes ------------------------------------------------------------------

  /** {@code imap_chr(d)}: the character with GraphBase code {@code d}, or NUL if out of range. */
  public static char imapChr(long d) {
    return d < 0 || d >= IMAP.length ? '\0' : (char) (IMAP[(int) d] & 0xff);
  }

  /** {@code imap_ord(c)}: the GraphBase code of byte {@code c} (0..255), else 127. */
  public static long imapOrd(int c) {
    return c < 0 || c > 255 ? UNEXPECTED_CHAR : ICODE[c];
  }

  /** {@code new_checksum(s, old)}: folds every byte of {@code s} into the running checksum. */
  public static long newChecksum(byte[] s, long oldChecksum) {
    long a = oldChecksum;
    for (byte b : s) {
      a = (a + a + imapOrd(b & 0xff)) % CHECKSUM_PRIME;
    }
    return a;
  }

  // ---- line buffer ----------------------------------------------------------------------

  /** {@code fill_buf()}: reads the next line (at most 80 bytes), strips trailing spaces. */
  private static void fillBuf() {
    int n = 0;
    boolean gotNewline = false;
    boolean gotAny = false;
    try {
      while (n < MAX_LINE) {
        int b = curFile.read();
        if (b < 0) {
          break;
        }
        gotAny = true;
        buffer[n++] = (byte) b;
        if (b == '\n') {
          gotNewline = true;
          break;
        }
      }
    } catch (IOException e) {
      gotAny = false;
      n = 0;
    }
    if (!gotAny) {
      ioErrors |= FILE_ENDED_PREMATURELY;
      moreData = false;
      n = 0;
    }
    int p; // index one past the last content byte
    if (n == 0 || !gotNewline) {
      ioErrors |= MISSING_NEWLINE;
      p = n;
    } else {
      p = n - 1; // drop the '\n'
    }
    while (p > 0 && buffer[p - 1] == ' ') {
      p--;
    }
    buffer[p] = '\n';
    lineEnd = p;
    curPos = 0;
  }

  private static byte[] lineBytes() {
    return java.util.Arrays.copyOfRange(buffer, 0, lineEnd + 1);
  }

  private static boolean lineStartsWith(String prefix) {
    byte[] p = prefix.getBytes(StandardCharsets.ISO_8859_1);
    if (p.length > lineEnd) {
      return false;
    }
    for (int i = 0; i < p.length; i++) {
      if (buffer[i] != p[i]) {
        return false;
      }
    }
    return true;
  }

  /** {@code gb_newline()}: advances to the next line, folding it into the checksum. */
  public static void newline() {
    if (++lineNo > totLines) {
      moreData = false;
    }
    if (moreData) {
      fillBuf();
      if (buffer[0] != '*') {
        magic = newChecksum(lineBytes(), magic);
      }
    }
  }

  /** {@code gb_eof()}: true once the data has all been read. */
  public static boolean eof() {
    return !moreData;
  }

  /** {@code gb_char()}: the next byte of the line, or '\n' once the line is exhausted. */
  public static char ch() {
    if (curPos <= lineEnd) {
      return (char) (buffer[curPos++] & 0xff);
    }
    return '\n';
  }

  /** {@code gb_backup()}: steps back one byte, never before the line start. */
  public static void backup() {
    if (curPos > 0) {
      curPos--;
    }
  }

  private static long ordAtCursor() {
    return curPos <= lineEnd ? imapOrd(buffer[curPos] & 0xff) : Long.MAX_VALUE;
  }

  /** {@code gb_digit(d)}: reads one radix-{@code d} digit, or returns -1 without advancing. */
  public static long digit(int d) {
    if (ordAtCursor() < d) {
      return imapOrd(buffer[curPos++] & 0xff);
    }
    return -1;
  }

  /** {@code gb_number(d)}: reads a radix-{@code d} number, possibly empty (0). */
  public static long number(int d) {
    long a = 0;
    while (ordAtCursor() < d) {
      a = a * d + imapOrd(buffer[curPos++] & 0xff);
    }
    return a;
  }

  /** {@code gb_string(p, c)}: the bytes up to (not including) {@code c} or the end of line. */
  public static String string(char c) {
    int start = curPos;
    while (curPos <= lineEnd && (buffer[curPos] & 0xff) != c) {
      curPos++;
    }
    return new String(buffer, start, curPos - start, StandardCharsets.ISO_8859_1);
  }

  // ---- files ----------------------------------------------------------------------------

  private static InputStream locate(String f) {
    try {
      Path direct = Path.of(f);
      if (Files.isRegularFile(direct)) {
        return Files.newInputStream(direct);
      }
      String dir = System.getProperty("jsgb.data.dir");
      if (dir != null) {
        Path inDir = Path.of(dir, f);
        if (Files.isRegularFile(inDir)) {
          return Files.newInputStream(inDir);
        }
      }
    } catch (IOException | java.nio.file.InvalidPathException e) {
      return null;
    }
    return GbIo.class.getResourceAsStream("/sgb/" + f);
  }

  /** {@code gb_raw_open(f)}: opens {@code f} without header checks; sets {@link #ioErrors}. */
  public static void rawOpen(String f) {
    InputStream in = locate(f);
    curFile = in == null ? null : new java.io.BufferedInputStream(in);
    if (curFile != null) {
      ioErrors = 0;
      moreData = true;
      lineNo = magic = 0;
      totLines = 0x7fffffffL;
      fillBuf();
    } else {
      ioErrors = CANT_OPEN_FILE;
    }
  }

  /** {@code gb_open(f)}: opens a GraphBase data file, validating its header; 0 if OK. */
  public static long open(String f) {
    fileName = f.length() > 19 ? f.substring(0, 19) : f;
    rawOpen(f);
    if (curFile != null) {
      if (!lineStartsWith("* File \"" + f + "\"")) {
        return ioErrors |= BAD_FIRST_LINE;
      }
      fillBuf();
      if (buffer[0] != '*') {
        return ioErrors |= BAD_SECOND_LINE;
      }
      fillBuf();
      if (buffer[0] != '*') {
        return ioErrors |= BAD_THIRD_LINE;
      }
      fillBuf();
      if (!lineStartsWith("* (Checksum parameters ")) {
        return ioErrors |= BAD_FOURTH_LINE;
      }
      curPos += 23;
      totLines = number(10);
      if (ch() != ',') {
        return ioErrors |= BAD_FOURTH_LINE;
      }
      finalMagic = number(10);
      if (ch() != ')') {
        return ioErrors |= BAD_FOURTH_LINE;
      }
      newline();
    }
    return ioErrors;
  }

  /** {@code gb_close()}: verifies the trailer, line count and checksum; 0 if OK. */
  public static long close() {
    if (curFile == null) {
      return ioErrors |= NO_FILE_OPEN;
    }
    fillBuf();
    if (!lineStartsWith("* End of file \"" + fileName + "\"")) {
      ioErrors |= BAD_LAST_LINE;
    }
    moreData = false;
    lineEnd = 0;
    buffer[0] = '\n';
    try {
      curFile.close();
    } catch (IOException e) {
      curFile = null;
      return ioErrors |= CANT_CLOSE_FILE;
    }
    curFile = null;
    if (lineNo != totLines + 1) {
      return ioErrors |= WRONG_NUMBER_OF_LINES;
    }
    if (magic != finalMagic) {
      return ioErrors |= WRONG_CHECKSUM;
    }
    return ioErrors;
  }

  /** {@code gb_raw_close()}: closes without checks and returns the running checksum. */
  public static long rawClose() {
    if (curFile != null) {
      try {
        curFile.close();
      } catch (IOException ignored) {
        // the C ignores fclose's result here too
      }
      moreData = false;
      lineEnd = 0;
      buffer[0] = '\n';
      curPos = 0;
      curFile = null;
    }
    return magic;
  }
}
```

One subtlety in `close()` for the "after close, `ch()` returns '\n'" behaviour: the C sets `buffer[0] = 0`, so `gb_char()` returns '\n' without advancing. Setting `lineEnd = 0` and `buffer[0] = '\n'` gives a first `ch()` that returns '\n' and advances, then '\n' forever; the observable sequence is identical.

`lib/src/main/java/com/robsartin/jsgb/io/package-info.java`: three or four sentences: the data files must be read identically on every system, so GB_IO defines its own 96-character alphabet, reads lines of at most 80 bytes, strips trailing blanks, and keeps a running checksum that `close` checks against the value in the file header; the API is a cursor over the current line (`ch`, `backup`, `digit`, `number`, `string`) plus `newline`.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew spotlessApply && ./gradlew :lib:test --tests 'com.robsartin.jsgb.io.GbIoTest'`
Expected: 8 tests passed. If the translated self-test fails at a specific assertion, compare that step with `gb_io.w` (sections 22, 24, 26 for `gb_char`, `gb_digit/gb_number`, `gb_string`; section 9 for `fill_buf`) before touching the test.

- [ ] **Step 6: Run the full gate and commit**

Run: `./gradlew check` — Expected: BUILD SUCCESSFUL.

```bash
git add lib/src/main/java/com/robsartin/jsgb/io lib/src/main/resources/sgb lib/src/test/resources/sgb lib/src/test/java/com/robsartin/jsgb/io
git commit -m "Port gb_io and ship the GraphBase data files unmodified

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: Graph data types and `Gb.newGraph` (gb_graph part 1)

**Files:**
- Create: `lib/src/main/java/com/robsartin/jsgb/graph/{Util,Vertex,Arc,Graph,Gb}.java`, `lib/src/main/java/com/robsartin/jsgb/graph/package-info.java`
- Test: `lib/src/test/java/com/robsartin/jsgb/graph/GraphTest.java`

**Interfaces:**
- Produces:
  - `public final class Util { public long I; public Object ref; Vertex V(); Arc A(); Graph G(); String S(); void V(Vertex); void A(Arc); void G(Graph); void S(String); }`
  - `public final class Vertex { public Arc arcs; public String name; public final Util u,v,w,x,y,z; public final int index; }` — constructor package-private `Vertex(int index)`.
  - `public final class Arc { public Vertex tip; public Arc next; public long len; public Arc mate; public final Util a,b; }` — public no-arg constructor.
  - `public final class Graph { public Vertex[] vertices; public long n, m; public String id; public String utilTypes; public final Util uu,vv,ww,xx,yy,zz; public List<Arc[]> arcBlocks(); }` — constructor package-private; `arcBlocks()` returns an unmodifiable view of the blocks in allocation order.
  - `public final class Gb` with `public static long verbose, panicCode, troubleCode, extraN = 4;` `public static final String NULL_STRING = "";` panic constants `ALLOC_FAULT=-1, NO_ROOM=1, EARLY_DATA_FAULT=10, LATE_DATA_FAULT=11, SYNTAX_ERROR=20, BAD_SPECS=30, VERY_BAD_SPECS=40, MISSING_OPERAND=50, INVALID_OPERAND=60, IMPOSSIBLE=90`; `public static final int ID_FIELD_SIZE = 161; public static final int ARCS_PER_BLOCK = 102;` `static Graph newGraph(long n)`, `static String saveString(String s)`, `static void recycle(Graph g)`, `static void markBipartite(Graph g, long n1)`, package-private `static void reset()`, package-private `static Graph curGraph()`.

- [ ] **Step 1: Write the failing test**

`lib/src/test/java/com/robsartin/jsgb/graph/GraphTest.java`:
```java
package com.robsartin.jsgb.graph;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GraphTest {

  @BeforeEach
  void resetKernel() {
    Gb.reset();
  }

  @Test
  @DisplayName("gb_new_graph(n) allocates n + extra_n vertices with empty names and default id")
  void shouldCreateVerticesWithDefaultsWhenNewGraphCalled() {
    Graph g = Gb.newGraph(2L);
    assertThat(g).isNotNull();
    assertThat(g.n).isEqualTo(2L);
    assertThat(g.m).isZero();
    assertThat(g.vertices).hasSize(2 + 4);
    for (Vertex v : g.vertices) {
      assertThat(v.name).isSameAs(Gb.NULL_STRING);
      assertThat(v.arcs).isNull();
    }
    assertThat(g.id).isEqualTo("gb_new_graph(2)");
    assertThat(g.utilTypes).isEqualTo("ZZZZZZZZZZZZZZ");
    assertThat(g.arcBlocks()).isEmpty();
    assertThat(Gb.troubleCode).isZero();
  }

  @Test
  @DisplayName("vertices carry their position in the array, which stands in for C address order")
  void shouldNumberVerticesByPositionWhenNewGraphCalled() {
    Graph g = Gb.newGraph(3L);
    for (int i = 0; i < g.vertices.length; i++) {
      assertThat(g.vertices[i].index).isEqualTo(i);
    }
  }

  @Test
  @DisplayName("extra_n is honoured when changed before gb_new_graph")
  void shouldAllocateExtraVerticesWhenExtraNChanged() {
    Gb.extraN = 1;
    assertThat(Gb.newGraph(5L).vertices).hasSize(6);
  }

  @Test
  @DisplayName("gb_new_graph makes the new graph current and clears gb_trouble_code")
  void shouldBecomeCurrentGraphWhenNewGraphCalled() {
    Gb.troubleCode = 3;
    Graph g = Gb.newGraph(1L);
    assertThat(Gb.curGraph()).isSameAs(g);
    assertThat(Gb.troubleCode).isZero();
  }

  @Test
  @DisplayName("util slots start as zero and null, and typed accessors cast")
  void shouldStartZeroAndCastWhenUtilSlotsUsed() {
    Graph g = Gb.newGraph(1L);
    Vertex v = g.vertices[0];
    assertThat(v.u.I).isZero();
    assertThat(v.u.V()).isNull();
    v.u.V(g.vertices[1]);
    assertThat(v.u.V()).isSameAs(g.vertices[1]);
    v.x.S("hello");
    assertThat(v.x.S()).isEqualTo("hello");
    Arc a = new Arc();
    v.z.A(a);
    assertThat(v.z.A()).isSameAs(a);
    g.uu.G(g);
    assertThat(g.uu.G()).isSameAs(g);
  }

  @Test
  @DisplayName("gb_save_string returns an equal string (strings need no arena in Java)")
  void shouldReturnEqualStringWhenSaveStringCalled() {
    Gb.newGraph(1L);
    assertThat(Gb.saveString("vertex 0")).isEqualTo("vertex 0");
  }

  @Test
  @DisplayName("mark_bipartite stores n_1 in uu.I and flags util_types[8] as I")
  void shouldSetN1AndUtilTypeWhenMarkBipartiteCalled() {
    Graph g = Gb.newGraph(5L);
    Gb.markBipartite(g, 2L);
    assertThat(g.uu.I).isEqualTo(2L);
    assertThat(g.utilTypes).isEqualTo("ZZZZZZZZIZZZZZ");
  }

  @Test
  @DisplayName("gb_recycle releases the vertex array and arc blocks")
  void shouldDropStorageWhenRecycled() {
    Graph g = Gb.newGraph(2L);
    Gb.recycle(g);
    assertThat(g.vertices).isNull();
    assertThat(g.arcBlocks()).isEmpty();
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :lib:test --tests 'com.robsartin.jsgb.graph.GraphTest'`
Expected: compilation FAILS, `cannot find symbol ... Gb`.

- [ ] **Step 3: Write the types and `Gb` (part 1)**

`Util.java`:
```java
package com.robsartin.jsgb.graph;

/**
 * One utility slot: the C {@code util} union. A slot holds either a {@code long} ({@link #I}) or
 * a reference ({@link #ref}) to a {@link Vertex}, {@link Arc}, {@link Graph} or {@link String}.
 * Which view is meaningful is recorded, per slot, in {@link Graph#utilTypes}; type safety is by
 * convention, as in C.
 */
public final class Util {
  public long I;
  public Object ref;

  public Vertex V() {
    return (Vertex) ref;
  }

  public Arc A() {
    return (Arc) ref;
  }

  public Graph G() {
    return (Graph) ref;
  }

  public String S() {
    return (String) ref;
  }

  public void V(Vertex v) {
    ref = v;
  }

  public void A(Arc a) {
    ref = a;
  }

  public void G(Graph g) {
    ref = g;
  }

  public void S(String s) {
    ref = s;
  }
}
```

`Vertex.java`:
```java
package com.robsartin.jsgb.graph;

/** A vertex: its arc list, its name, six utility slots, and its position in the graph's array. */
public final class Vertex {
  /** Linked list of arcs coming out of this vertex. */
  public Arc arcs;

  /** Symbolic identification; {@link Gb#NULL_STRING} until set. */
  public String name = Gb.NULL_STRING;

  public final Util u = new Util();
  public final Util v = new Util();
  public final Util w = new Util();
  public final Util x = new Util();
  public final Util y = new Util();
  public final Util z = new Util();

  /**
   * Position in {@link Graph#vertices}. In C the vertices of a graph occupy one array, so
   * pointer comparison {@code u < v} meant exactly this; {@link Gb#newEdge} relies on it.
   */
  public final int index;

  Vertex(int index) {
    this.index = index;
  }
}
```

`Arc.java`:
```java
package com.robsartin.jsgb.graph;

/** An arc: the vertex it points to, the next arc from the same vertex, a length, two slots. */
public final class Arc {
  public Vertex tip;
  public Arc next;
  public long len;

  /**
   * The other arc of the same edge when this arc was created by {@link Gb#newEdge}; null for arcs
   * created by {@link Gb#newArc}. Replaces the C's {@code a+1}/{@code a-1} pointer arithmetic.
   */
  public Arc mate;

  public final Util a = new Util();
  public final Util b = new Util();
}
```

`Graph.java`:
```java
package com.robsartin.jsgb.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A graph: its vertex array, counts, id, utility-type string, six slots, and its arc storage. */
public final class Graph {
  /** The vertices, {@code n + extra_n} of them; null after {@link Gb#recycle}. */
  public Vertex[] vertices;

  /** Number of vertices. */
  public long n;

  /** Number of arcs. */
  public long m;

  /** GraphBase identification, at most {@link Gb#ID_FIELD_SIZE} - 1 characters. */
  public String id = "";

  /** Fourteen characters describing the slots: u v w x y z, then a b, then uu..zz. */
  public String utilTypes = "ZZZZZZZZZZZZZZ";

  public final Util uu = new Util();
  public final Util vv = new Util();
  public final Util ww = new Util();
  public final Util xx = new Util();
  public final Util yy = new Util();
  public final Util zz = new Util();

  final List<Arc[]> arcBlocks = new ArrayList<>();

  Graph() {}

  /** The arc blocks in allocation order; each has {@link Gb#ARCS_PER_BLOCK} slots. */
  public List<Arc[]> arcBlocks() {
    return Collections.unmodifiableList(arcBlocks);
  }
}
```

`Gb.java` (part 1; later tasks add methods to this class):
```java
package com.robsartin.jsgb.graph;

/**
 * Port of the {@code gb_graph} procedures and globals: graph creation, arc and edge creation,
 * identification strings, and the name hash. State is global and single-threaded, as in C.
 */
public final class Gb {

  public static final int ID_FIELD_SIZE = 161;
  public static final int ARCS_PER_BLOCK = 102;

  /** The C {@code null_string}: the name of every vertex until it is given one. */
  public static final String NULL_STRING = "";

  public static final long ALLOC_FAULT = -1;
  public static final long NO_ROOM = 1;
  public static final long EARLY_DATA_FAULT = 10;
  public static final long LATE_DATA_FAULT = 11;
  public static final long SYNTAX_ERROR = 20;
  public static final long BAD_SPECS = 30;
  public static final long VERY_BAD_SPECS = 40;
  public static final long MISSING_OPERAND = 50;
  public static final long INVALID_OPERAND = 60;
  public static final long IMPOSSIBLE = 90;

  /** Nonzero if verbose output is desired. */
  public static long verbose;

  /** Set by a generator that fails; see the panic constants. */
  public static long panicCode;

  /** Bit 1: allocation failed; bit 2: an illegal request was made. */
  public static long troubleCode;

  /** Number of spare vertices allocated beyond {@code n} by {@link #newGraph}. */
  public static long extraN = 4;

  private static final Graph DUMMY_GRAPH = new Graph();
  private static Graph curGraph = DUMMY_GRAPH;
  private static Arc[] curBlock;
  private static int nextIndex;

  private Gb() {}

  /** Restores the just-loaded state. Tests only. */
  static void reset() {
    verbose = 0;
    panicCode = 0;
    troubleCode = 0;
    extraN = 4;
    curGraph = DUMMY_GRAPH;
    curBlock = null;
    nextIndex = 0;
  }

  /** The graph that {@link #newArc} and {@link #newEdge} add to. Tests only. */
  static Graph curGraph() {
    return curGraph;
  }

  /** {@code gb_new_graph(n)}: a new graph with {@code n + extra_n} vertices; becomes current. */
  public static Graph newGraph(long n) {
    Graph g = new Graph();
    int total = (int) (n + extraN);
    g.vertices = new Vertex[total];
    for (int i = 0; i < total; i++) {
      g.vertices[i] = new Vertex(i);
    }
    g.n = n;
    g.id = "gb_new_graph(" + n + ")";
    g.utilTypes = "ZZZZZZZZZZZZZZ";
    curGraph = g;
    curBlock = null;
    nextIndex = 0;
    troubleCode = 0;
    return g;
  }

  /** {@code gb_save_string(s)}: in C copies {@code s} into the graph's arena; here identity. */
  public static String saveString(String s) {
    return s;
  }

  /** {@code gb_recycle(g)}: releases the graph's storage. Using {@code g} afterwards is an error. */
  public static void recycle(Graph g) {
    if (g != null) {
      g.vertices = null;
      g.arcBlocks.clear();
    }
  }

  /** {@code mark_bipartite(g, n1)}: records the size of the first part in {@code uu.I}. */
  public static void markBipartite(Graph g, long n1) {
    g.uu.I = n1;
    g.utilTypes = g.utilTypes.substring(0, 8) + 'I' + g.utilTypes.substring(9);
  }
}
```

`package-info.java` for `graph`: four or five sentences: GB_GRAPH defines the three record types and the utility-slot convention that lets any algorithm attach data to any graph; explain the 14-character `utilTypes` string; note that the C arena allocator is not ported and that `Vertex.index`, `Arc.mate`, and the retained arc blocks stand in for the three places C relied on memory addresses.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew spotlessApply && ./gradlew :lib:test --tests 'com.robsartin.jsgb.graph.GraphTest'`
Expected: 8 tests passed.

- [ ] **Step 5: Commit**

```bash
git add lib/src/main/java/com/robsartin/jsgb/graph lib/src/test/java/com/robsartin/jsgb/graph/GraphTest.java
git commit -m "Port gb_graph data types and gb_new_graph

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: Arcs, edges and arc blocks (gb_graph part 2)

**Files:**
- Modify: `lib/src/main/java/com/robsartin/jsgb/graph/Gb.java`
- Test: `lib/src/test/java/com/robsartin/jsgb/graph/GbTest.java`

**Interfaces:**
- Produces on `Gb`: `static Arc virginArc()`, `static void newArc(Vertex u, Vertex v, long len)`, `static void newEdge(Vertex u, Vertex v, long len)`, `static void switchToGraph(Graph g)`.

Behaviour (from `gb_graph.w` sections 28 to 31 and 39):
- `virginArc()`: if there is no current block or it is full, allocate a block of 102 fresh `Arc` objects, append it to the current graph's `arcBlocks`, and return slot 0 with the cursor at 1; otherwise return the slot at the cursor and advance. Calling it while no graph is current throws `IllegalStateException` (the C silently allocated into a dummy graph).
- `newArc(u, v, len)`: `a = virginArc(); a.tip = v; a.next = u.arcs; a.len = len; u.arcs = a; curGraph.m++`.
- `newEdge(u, v, len)`: `a = virginArc()`, then the next slot in the same block is the mate (`nextIndex++`). If `a` was the last slot of its block, throw `IllegalStateException` with a message quoting the C rule ("gb_new_edge must not be mixed with an odd number of gb_new_arc calls"). Then if `u.index < v.index`: `a.tip = v; a.next = u.arcs; mate.tip = u; mate.next = v.arcs; u.arcs = a; v.arcs = mate;` else `mate.tip = v; mate.next = u.arcs; u.arcs = mate; a.tip = u; a.next = v.arcs; v.arcs = a;`. Both `len` set; `a.mate = mate; mate.mate = a; curGraph.m += 2`.
- `switchToGraph(g)`: save the cursor (block and index) into the current graph's `ww.ref` as a private record `ArcCursor(Arc[] block, int index)`; make `g` current (null means the dummy graph); load the cursor from the new current graph's `ww.ref` (null cursor means no block); then set the new current graph's `ww.ref` to null. (The C also parks the string cursors in `yy`/`zz`; there are none in Java.)

- [ ] **Step 1: Write the failing test**

`lib/src/test/java/com/robsartin/jsgb/graph/GbTest.java`:
```java
package com.robsartin.jsgb.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GbTest {

  @BeforeEach
  void resetKernel() {
    Gb.reset();
  }

  @Test
  @DisplayName("test_graph: edges and arcs link as the C self-test expects")
  void shouldLinkArcsWhenTranslatedCSelfTestRuns() {
    Graph g = Gb.newGraph(2L);
    Vertex u = g.vertices[0];
    Vertex v = g.vertices[1];
    u.name = Gb.saveString("vertex 0");
    v.name = Gb.saveString("vertex 1");

    Gb.newEdge(v, u, -1L);
    Gb.newEdge(u, u, 1L);
    Gb.newArc(v, u, -1L);

    // The C checks v->name[7] + g->n == v->arcs->next->tip->name[7] + g->m - 2,
    // i.e. '1' + 2 == '0' + 5 - 2: v's second arc points at u.
    assertThat(g.m).isEqualTo(5L);
    assertThat(v.arcs.next.tip).isSameAs(u);
    assertThat(v.arcs.tip).isSameAs(u); // the newArc(v,u) is at the head of v's list
  }

  @Test
  @DisplayName("gb_new_edge with u before v puts u's arc first in the block and v's arc second")
  void shouldPlaceArcsInIndexOrderWhenUPrecedesV() {
    Graph g = Gb.newGraph(2L);
    Vertex u = g.vertices[0];
    Vertex v = g.vertices[1];
    Gb.newEdge(u, v, 7L);
    Arc[] block = g.arcBlocks().get(0);
    assertThat(block).hasSize(Gb.ARCS_PER_BLOCK);
    assertThat(u.arcs).isSameAs(block[0]);
    assertThat(v.arcs).isSameAs(block[1]);
    assertThat(u.arcs.tip).isSameAs(v);
    assertThat(v.arcs.tip).isSameAs(u);
    assertThat(u.arcs.mate).isSameAs(v.arcs);
    assertThat(v.arcs.mate).isSameAs(u.arcs);
    assertThat(u.arcs.len).isEqualTo(7L);
    assertThat(v.arcs.len).isEqualTo(7L);
    assertThat(g.m).isEqualTo(2L);
  }

  @Test
  @DisplayName("gb_new_edge with v before u puts v's arc first in the block")
  void shouldPlaceArcsInIndexOrderWhenVPrecedesU() {
    Graph g = Gb.newGraph(2L);
    Vertex u = g.vertices[1];
    Vertex v = g.vertices[0];
    Gb.newEdge(u, v, 3L);
    Arc[] block = g.arcBlocks().get(0);
    assertThat(v.arcs).isSameAs(block[0]);
    assertThat(u.arcs).isSameAs(block[1]);
    assertThat(v.arcs.tip).isSameAs(u);
    assertThat(u.arcs.tip).isSameAs(v);
  }

  @Test
  @DisplayName("a self-loop edge puts both arcs on the same vertex, second slot first in the list")
  void shouldChainBothArcsWhenEdgeIsSelfLoop() {
    Graph g = Gb.newGraph(1L);
    Vertex u = g.vertices[0];
    Gb.newEdge(u, u, 1L);
    Arc[] block = g.arcBlocks().get(0);
    assertThat(u.arcs).isSameAs(block[0]);
    assertThat(u.arcs.next).isSameAs(block[1]);
    assertThat(block[1].next).isNull();
  }

  @Test
  @DisplayName("gb_new_arc appends one arc at the head of u's list and does not set a mate")
  void shouldPrependArcWhenNewArcCalled() {
    Graph g = Gb.newGraph(2L);
    Vertex u = g.vertices[0];
    Vertex v = g.vertices[1];
    Gb.newArc(u, v, 5L);
    Gb.newArc(u, v, 6L);
    assertThat(u.arcs.len).isEqualTo(6L);
    assertThat(u.arcs.next.len).isEqualTo(5L);
    assertThat(u.arcs.mate).isNull();
    assertThat(v.arcs).isNull();
    assertThat(g.m).isEqualTo(2L);
  }

  @Test
  @DisplayName("the 103rd arc starts a second block of 102")
  void shouldStartNewBlockWhenFirstBlockFull() {
    Graph g = Gb.newGraph(2L);
    Vertex u = g.vertices[0];
    Vertex v = g.vertices[1];
    for (int i = 0; i < 103; i++) {
      Gb.newArc(u, v, i);
    }
    assertThat(g.arcBlocks()).hasSize(2);
    assertThat(g.arcBlocks().get(1)[0].len).isEqualTo(102L);
    assertThat(g.arcBlocks().get(1)[1].tip).isNull();
  }

  @Test
  @DisplayName("gb_new_edge straddling a block boundary is rejected instead of corrupting memory")
  void shouldThrowWhenEdgeWouldStraddleBlocks() {
    Graph g = Gb.newGraph(2L);
    Vertex u = g.vertices[0];
    Vertex v = g.vertices[1];
    for (int i = 0; i < 101; i++) {
      Gb.newArc(u, v, i);
    }
    assertThatThrownBy(() -> Gb.newEdge(u, v, 0L)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("creating an arc with no current graph is an error")
  void shouldThrowWhenNoGraphIsCurrent() {
    Graph g = Gb.newGraph(1L);
    Gb.switchToGraph(null);
    assertThatThrownBy(() -> Gb.newArc(g.vertices[0], g.vertices[0], 0L))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("switch_to_graph resumes each graph's own arc block where it left off")
  void shouldResumeBlockWhenSwitchingBackToGraph() {
    Graph g1 = Gb.newGraph(2L);
    Gb.newArc(g1.vertices[0], g1.vertices[1], 1L);
    Graph g2 = Gb.newGraph(2L);
    Gb.newArc(g2.vertices[0], g2.vertices[1], 2L);
    Gb.switchToGraph(g1);
    Gb.newArc(g1.vertices[1], g1.vertices[0], 3L);
    Gb.switchToGraph(g2);
    Gb.newArc(g2.vertices[1], g2.vertices[0], 4L);

    assertThat(g1.arcBlocks()).hasSize(1);
    assertThat(g1.arcBlocks().get(0)[1].len).isEqualTo(3L);
    assertThat(g2.arcBlocks()).hasSize(1);
    assertThat(g2.arcBlocks().get(0)[1].len).isEqualTo(4L);
    assertThat(g1.ww.ref).isNotNull(); // g1 is parked: its cursor lives in ww
    assertThat(g2.ww.ref).isNull(); // g2 is current: its ww is cleared
  }

  @Test
  @DisplayName("switching to a graph that was never parked starts a fresh block")
  void shouldStartFreshBlockWhenGraphHasNoSavedCursor() {
    Graph g1 = Gb.newGraph(2L);
    Gb.newArc(g1.vertices[0], g1.vertices[1], 1L);
    Gb.newGraph(1L); // g1 was not switched away from, so its cursor was not parked
    Gb.switchToGraph(g1);
    Gb.newArc(g1.vertices[0], g1.vertices[1], 2L);
    assertThat(g1.arcBlocks()).hasSize(2);
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :lib:test --tests 'com.robsartin.jsgb.graph.GbTest'`
Expected: compilation FAILS, `cannot find symbol ... newEdge`.

- [ ] **Step 3: Add the methods to `Gb`**

Add inside `Gb`:
```java
  private record ArcCursor(Arc[] block, int index) {}

  private static Arc[] newBlock() {
    Arc[] block = new Arc[ARCS_PER_BLOCK];
    for (int i = 0; i < ARCS_PER_BLOCK; i++) {
      block[i] = new Arc();
    }
    return block;
  }

  /** {@code gb_virgin_arc()}: the next unused arc slot of the current graph. */
  public static Arc virginArc() {
    if (curGraph == DUMMY_GRAPH) {
      throw new IllegalStateException("no current graph: call gb_new_graph first");
    }
    if (curBlock == null || nextIndex == ARCS_PER_BLOCK) {
      curBlock = newBlock();
      curGraph.arcBlocks.add(curBlock);
      nextIndex = 1;
      return curBlock[0];
    }
    return curBlock[nextIndex++];
  }

  /** {@code gb_new_arc(u, v, len)}: a new arc from {@code u} to {@code v} in the current graph. */
  public static void newArc(Vertex u, Vertex v, long len) {
    Arc a = virginArc();
    a.tip = v;
    a.next = u.arcs;
    a.len = len;
    u.arcs = a;
    curGraph.m++;
  }

  /**
   * {@code gb_new_edge(u, v, len)}: a new undirected edge as two consecutive arcs. The arc from
   * the lower-indexed vertex occupies the first slot, as in C where the first arc was the one at
   * the lower address.
   */
  public static void newEdge(Vertex u, Vertex v, long len) {
    Arc a = virginArc();
    if (nextIndex == ARCS_PER_BLOCK) {
      throw new IllegalStateException(
          "gb_new_edge must not be mixed with an odd number of gb_new_arc calls");
    }
    Arc mate = curBlock[nextIndex++];
    if (u.index < v.index) {
      a.tip = v;
      a.next = u.arcs;
      mate.tip = u;
      mate.next = v.arcs;
      u.arcs = a;
      v.arcs = mate;
    } else {
      mate.tip = v;
      mate.next = u.arcs;
      u.arcs = mate;
      a.tip = u;
      a.next = v.arcs;
      v.arcs = a;
    }
    a.len = len;
    mate.len = len;
    a.mate = mate;
    mate.mate = a;
    curGraph.m += 2;
  }

  /** {@code switch_to_graph(g)}: makes {@code g} current, parking the old graph's arc cursor. */
  public static void switchToGraph(Graph g) {
    curGraph.ww.ref = curBlock == null ? null : new ArcCursor(curBlock, nextIndex);
    curGraph = g == null ? DUMMY_GRAPH : g;
    if (curGraph.ww.ref instanceof ArcCursor c) {
      curBlock = c.block();
      nextIndex = c.index();
    } else {
      curBlock = null;
      nextIndex = 0;
    }
    curGraph.ww.ref = null;
  }
```

Also make `reset()` set the dummy graph's `ww.ref` to null.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew spotlessApply && ./gradlew :lib:test --tests 'com.robsartin.jsgb.graph.*'`
Expected: all graph tests pass.

- [ ] **Step 5: Commit**

```bash
git add lib/src/main/java/com/robsartin/jsgb/graph/Gb.java lib/src/test/java/com/robsartin/jsgb/graph/GbTest.java
git commit -m "Port gb_new_arc, gb_new_edge, arc blocks and switch_to_graph

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: Compound ids (gb_graph part 3)

**Files:**
- Modify: `lib/src/main/java/com/robsartin/jsgb/graph/Gb.java`
- Modify: `lib/src/test/java/com/robsartin/jsgb/graph/GbTest.java`

**Interfaces:**
- Produces on `Gb`: `static void makeCompoundId(Graph g, String s1, Graph gg, String s2)`, `static void makeDoubleCompoundId(Graph g, String s1, Graph gg, String s2, Graph ggg, String s3)`.

- [ ] **Step 1: Add failing tests to `GbTest`**

```java
  @Test
  @DisplayName("make_compound_id concatenates when the inner id fits")
  void shouldConcatenateWhenInnerIdFits() {
    Graph gg = Gb.newGraph(1L);
    gg.id = "board(3,4,0,0,-1,0,0)";
    Graph g = Gb.newGraph(1L);
    Gb.makeCompoundId(g, "complement(", gg, ",1,1,0)");
    assertThat(g.id).isEqualTo("complement(board(3,4,0,0,-1,0,0),1,1,0)");
  }

  @Test
  @DisplayName("make_compound_id truncates the inner id with ...) to fit 160 characters")
  void shouldTruncateWhenInnerIdTooLong() {
    Graph gg = Gb.newGraph(1L);
    gg.id = "x".repeat(160);
    Graph g = Gb.newGraph(1L);
    Gb.makeCompoundId(g, "lines(", gg, ",0)");
    // avail = 161 - 6 - 3 = 152; inner keeps avail-5 = 147 chars, then "...)"
    assertThat(g.id).isEqualTo("lines(" + "x".repeat(147) + "...)" + ",0)");
    assertThat(g.id).hasSize(160);
  }

  @Test
  @DisplayName("make_double_compound_id concatenates when both inner ids fit")
  void shouldConcatenateWhenBothInnerIdsFit() {
    Graph gg = Gb.newGraph(1L);
    gg.id = "board(3,4,0,0,-1,0,0)";
    Graph ggg = Gb.newGraph(1L);
    ggg.id = "board(3,4,0,0,-2,0,0)";
    Graph g = Gb.newGraph(1L);
    Gb.makeDoubleCompoundId(g, "gunion(", gg, ",", ggg, ",0,0)");
    assertThat(g.id).isEqualTo("gunion(board(3,4,0,0,-1,0,0),board(3,4,0,0,-2,0,0),0,0)");
  }

  @Test
  @DisplayName("make_double_compound_id truncates both inner ids when they do not fit")
  void shouldTruncateBothWhenInnerIdsTooLong() {
    Graph gg = Gb.newGraph(1L);
    gg.id = "a".repeat(100);
    Graph ggg = Gb.newGraph(1L);
    ggg.id = "b".repeat(100);
    Graph g = Gb.newGraph(1L);
    Gb.makeDoubleCompoundId(g, "gunion(", gg, ",", ggg, ",0,0)");
    // avail = 161 - 7 - 1 - 5 = 148; first keeps 148/2-5 = 69, second keeps (148-9)/2 = 69
    assertThat(g.id)
        .isEqualTo("gunion(" + "a".repeat(69) + "...)" + "," + "b".repeat(69) + "...)" + ",0,0)");
  }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :lib:test --tests 'com.robsartin.jsgb.graph.GbTest'`
Expected: compilation FAILS, `cannot find symbol ... makeCompoundId`.

- [ ] **Step 3: Implement**

Add to `Gb`:
```java
  private static String prefix(String s, int max) {
    return s.length() <= max ? s : s.substring(0, Math.max(max, 0));
  }

  /** {@code make_compound_id(g, s1, gg, s2)}: {@code g.id = s1 + gg.id + s2}, truncated to fit. */
  public static void makeCompoundId(Graph g, String s1, Graph gg, String s2) {
    int avail = ID_FIELD_SIZE - s1.length() - s2.length();
    String tmp = gg.id;
    if (tmp.length() < avail) {
      g.id = s1 + tmp + s2;
    } else {
      g.id = s1 + prefix(tmp, avail - 5) + "...)" + s2;
    }
  }

  /** {@code make_double_compound_id}: {@code s1 + gg.id + s2 + ggg.id + s3}, truncated to fit. */
  public static void makeDoubleCompoundId(
      Graph g, String s1, Graph gg, String s2, Graph ggg, String s3) {
    int avail = ID_FIELD_SIZE - s1.length() - s2.length() - s3.length();
    if (gg.id.length() + ggg.id.length() < avail) {
      g.id = s1 + gg.id + s2 + ggg.id + s3;
    } else {
      g.id =
          s1
              + prefix(gg.id, avail / 2 - 5)
              + "...)"
              + s2
              + prefix(ggg.id, (avail - 9) / 2)
              + "...)"
              + s3;
    }
  }
```

- [ ] **Step 4: Run to verify pass, then commit**

Run: `./gradlew spotlessApply && ./gradlew :lib:test --tests 'com.robsartin.jsgb.graph.*'` — Expected: pass.

```bash
git add lib/src/main/java/com/robsartin/jsgb/graph/Gb.java lib/src/test/java/com/robsartin/jsgb/graph/GbTest.java
git commit -m "Port make_compound_id and make_double_compound_id

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: Name hash (gb_graph part 4)

**Files:**
- Modify: `lib/src/main/java/com/robsartin/jsgb/graph/Gb.java`
- Create: `lib/src/test/java/com/robsartin/jsgb/graph/HashTest.java`

**Interfaces:**
- Produces on `Gb`: `static void hashIn(Vertex v)`, `static Vertex hashOut(String s)`, `static void hashSetup(Graph g)`, `static Vertex hashLookup(String s, Graph g)`, and package-private `static int hashBucket(String s)` (the bucket index for the current graph, exposed for the oracle test). Slots: `hash_link` is `u.V`, `hash_head` is `v.V`.

Behaviour (`gb_graph.w` sections 44 to 48): `h` starts at 0; for each byte `t` of the name, `h += (h ^ (h >> 1)) + 314159 * (t & 0xff)`, then `while (h >= 516595003) h -= 516595003`; bucket vertex is `curGraph.vertices[h % curGraph.n]`. `hashIn(v)` pushes `v` on the bucket's `v.V` chain via `v.u.V`. `hashOut(s)` walks the chain comparing names with `equals`. `hashSetup(g)` temporarily makes `g` current, clears every `v.V` for the first `n` vertices, hashes them all in, and sets `utilTypes` positions 0 and 1 to `V`. `hashLookup(s, g)` temporarily makes `g` current and calls `hashOut`. Both return null (or do nothing) when `g` is null or `g.n <= 0`. "Temporarily current" means only `curGraph` is swapped and restored; the arc cursor is untouched (the C swaps only `cur_graph`).

- [ ] **Step 1: Write the failing test**

`HashTest.java`:
```java
package com.robsartin.jsgb.graph;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HashTest {

  @BeforeEach
  void resetKernel() {
    Gb.reset();
  }

  @Test
  @DisplayName("bucket index matches the C hash for known names in a 5757-vertex graph")
  void shouldMatchCBucketsWhenHashingKnownNames() {
    Gb.newGraph(5757L);
    assertThat(Gb.hashBucket("aargh")).isEqualTo(HASH_AARGH);
    assertThat(Gb.hashBucket("words")).isEqualTo(HASH_WORDS);
    assertThat(Gb.hashBucket("")).isZero();
  }

  @Test
  @DisplayName("hash_setup then hash_lookup finds every named vertex and nothing else")
  void shouldFindVerticesWhenLookedUpAfterSetup() {
    Graph g = Gb.newGraph(4L);
    String[] names = {"alpha", "beta", "gamma", "delta"};
    for (int i = 0; i < 4; i++) {
      g.vertices[i].name = names[i];
    }
    Gb.hashSetup(g);
    assertThat(g.utilTypes).isEqualTo("VVZZZZZZZZZZZZ");
    for (int i = 0; i < 4; i++) {
      assertThat(Gb.hashLookup(names[i], g)).isSameAs(g.vertices[i]);
    }
    assertThat(Gb.hashLookup("epsilon", g)).isNull();
  }

  @Test
  @DisplayName("hash_lookup on a null or empty graph returns null")
  void shouldReturnNullWhenGraphMissingOrEmpty() {
    assertThat(Gb.hashLookup("x", null)).isNull();
    Graph empty = Gb.newGraph(0L);
    assertThat(Gb.hashLookup("x", empty)).isNull();
  }

  @Test
  @DisplayName("hash_setup leaves the current graph unchanged")
  void shouldRestoreCurrentGraphWhenSetupFinishes() {
    Graph g = Gb.newGraph(2L);
    Graph other = Gb.newGraph(2L);
    Gb.hashSetup(g);
    assertThat(Gb.curGraph()).isSameAs(other);
  }

  // Values computed by the C hash (gb_graph.w section 45) with n = 5757.
  private static final int HASH_AARGH = 4279;
  private static final int HASH_WORDS = 1924;
}
```

- [ ] **Step 2: Provenance of the two oracle values**

`4279` and `1924` were produced by this C fragment, copied verbatim from `gb_graph.w` section 45, with `n = 5757`:

```c
#define HASH_MULT 314159
#define HASH_PRIME 516595003
long bucket(char*t,long n){long h;for(h=0;*t;t++){h+=(h^(h>>1))+HASH_MULT*(unsigned char)*t;while(h>=HASH_PRIME)h-=HASH_PRIME;}return h%n;}
/* bucket("aargh",5757) == 4279, bucket("words",5757) == 1924 */
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew :lib:test --tests 'com.robsartin.jsgb.graph.HashTest'`
Expected: compilation FAILS, `cannot find symbol ... hashBucket`.

- [ ] **Step 4: Implement**

Add to `Gb`:
```java
  private static final long HASH_MULT = 314159;
  private static final long HASH_PRIME = 516595003;

  /** The bucket index of {@code s} in the current graph (C section 45). Tests only. */
  static int hashBucket(String s) {
    long h = 0;
    for (byte t : s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)) {
      h += (h ^ (h >> 1)) + HASH_MULT * (t & 0xff);
      while (h >= HASH_PRIME) {
        h -= HASH_PRIME;
      }
    }
    return (int) (h % curGraph.n);
  }

  /** {@code hash_in(v)}: inserts {@code v} into the current graph's name table. */
  public static void hashIn(Vertex v) {
    Vertex u = curGraph.vertices[hashBucket(v.name)];
    v.u.V(u.v.V());
    u.v.V(v);
  }

  /** {@code hash_out(s)}: the vertex of the current graph named {@code s}, or null. */
  public static Vertex hashOut(String s) {
    Vertex u = curGraph.vertices[hashBucket(s)];
    for (u = u.v.V(); u != null; u = u.u.V()) {
      if (s.equals(u.name)) {
        return u;
      }
    }
    return null;
  }

  /** {@code hash_setup(g)}: builds the name table of {@code g} in slots {@code u} and {@code v}. */
  public static void hashSetup(Graph g) {
    if (g != null && g.n > 0) {
      Graph saved = curGraph;
      curGraph = g;
      for (int i = 0; i < g.n; i++) {
        g.vertices[i].v.V(null);
      }
      for (int i = 0; i < g.n; i++) {
        hashIn(g.vertices[i]);
      }
      g.utilTypes = "VV" + g.utilTypes.substring(2);
      curGraph = saved;
    }
  }

  /** {@code hash_lookup(s, g)}: the vertex of {@code g} named {@code s}, or null. */
  public static Vertex hashLookup(String s, Graph g) {
    if (g != null && g.n > 0) {
      Graph saved = curGraph;
      curGraph = g;
      Vertex v = hashOut(s);
      curGraph = saved;
      return v;
    }
    return null;
  }
```

- [ ] **Step 5: Run to verify pass, run the full gate, commit**

Run: `./gradlew spotlessApply && ./gradlew check` — Expected: BUILD SUCCESSFUL.

```bash
git add lib/src/main/java/com/robsartin/jsgb/graph/Gb.java lib/src/test/java/com/robsartin/jsgb/graph/HashTest.java
git commit -m "Port the gb_graph name hash

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 8: `LinkSort` (gb_sort)

**Files:**
- Create: `lib/src/main/java/com/robsartin/jsgb/sort/Sortable.java`, `lib/src/main/java/com/robsartin/jsgb/sort/LinkSort.java`, `lib/src/main/java/com/robsartin/jsgb/sort/package-info.java`
- Test: `lib/src/test/java/com/robsartin/jsgb/sort/LinkSortTest.java`

**Interfaces:**
- Consumes: `Flip.nextRand()`.
- Produces: `public interface Sortable { long key(); Sortable link(); void setLink(Sortable next); }` and `public final class LinkSort { public static final Sortable[] sorted = new Sortable[256]; public static void linksort(Sortable l); static void reset(); }`.

Behaviour (`gb_sort.w` sections 5 to 11): six distribution passes over 256 buckets, alternating between `alt` (private) and `sorted` (public). Pass 1: from the input list into `alt` by `nextRand() >> 23`. Pass 2: walk `alt` from bucket 255 down to 0, redistribute into `sorted` by `nextRand() >> 23`. Pass 3: walk `sorted` 255 down, into `alt` by `key & 0xff`. Pass 4: walk `alt` from 0 up to 255, into `sorted` by `(key >> 8) & 0xff`. Pass 5: walk `sorted` 255 down, into `alt` by `(key >> 16) & 0xff`. Pass 6: walk `alt` 0 up, into `sorted` by `(key >> 24) & 0xff`. Each distribution pushes at the head of the target bucket (`p.setLink(target[k]); target[k] = p`). Each target array is cleared before its pass. Reading `sorted[127]` down to `sorted[0]` yields keys in decreasing order with ties in random order.

- [ ] **Step 1: Write the failing test**

`LinkSortTest.java`:
```java
package com.robsartin.jsgb.sort;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.flip.Flip;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LinkSortTest {

  /** The simplest Sortable: the C's {@code node} struct plus an id for the oracle. */
  private static final class Node implements Sortable {
    final long key;
    final int id;
    Sortable link;

    Node(long key, int id) {
      this.key = key;
      this.id = id;
    }

    @Override
    public long key() {
      return key;
    }

    @Override
    public Sortable link() {
      return link;
    }

    @Override
    public void setLink(Sortable next) {
      link = next;
    }
  }

  @BeforeEach
  void resetState() {
    LinkSort.reset();
    Flip.initRand(1L);
  }

  private static Node chain(long... keys) {
    Node head = null;
    Node prev = null;
    for (int i = 0; i < keys.length; i++) {
      Node n = new Node(keys[i], i);
      if (prev == null) {
        head = n;
      } else {
        prev.link = n;
      }
      prev = n;
    }
    return head;
  }

  private static List<String> readAll() {
    List<String> out = new ArrayList<>();
    for (int j = 255; j >= 0; j--) {
      for (Sortable p = LinkSort.sorted[j]; p != null; p = p.link()) {
        Node n = (Node) p;
        out.add(j + ":" + n.key + "(n" + n.id + ")");
      }
    }
    return out;
  }

  @Test
  @DisplayName("gb_linksort with seed 1 reproduces the C library's bucket order, ties included")
  void shouldMatchCOrderWhenSortingTwentyKeysWithSeedOne() {
    Node head =
        chain(
            5, 3, 9, 3, 0x01000000L, 7, 3, 0x7fffffffL, 256, 255, 65536, 65535, 1, 2, 3, 4,
            0x00ff00ffL, 0x00ff00ffL, 0x10000000L, 6);
    LinkSort.linksort(head);
    assertThat(readAll())
        .containsExactly(
            "127:2147483647(n7)",
            "16:268435456(n18)",
            "1:16777216(n4)",
            "0:16711935(n17)",
            "0:16711935(n16)",
            "0:65536(n10)",
            "0:65535(n11)",
            "0:256(n8)",
            "0:255(n9)",
            "0:9(n2)",
            "0:7(n5)",
            "0:6(n19)",
            "0:5(n0)",
            "0:4(n15)",
            "0:3(n3)",
            "0:3(n6)",
            "0:3(n1)",
            "0:3(n14)",
            "0:2(n13)",
            "0:1(n12)");
    // The sort consumed exactly 40 random numbers (two per node); the next draw is this one.
    assertThat(Flip.nextRand()).isEqualTo(1963953515L);
  }

  @Test
  @DisplayName("sorting an empty list leaves every bucket empty")
  void shouldLeaveBucketsEmptyWhenListIsNull() {
    LinkSort.linksort(null);
    for (int j = 0; j < 256; j++) {
      assertThat(LinkSort.sorted[j]).isNull();
    }
  }

  @Test
  @DisplayName("a second sort discards the previous buckets")
  void shouldReplaceBucketsWhenSortedTwice() {
    LinkSort.linksort(chain(0x7fffffffL));
    assertThat(LinkSort.sorted[127]).isNotNull();
    LinkSort.linksort(chain(1));
    assertThat(LinkSort.sorted[127]).isNull();
    assertThat(LinkSort.sorted[0].key()).isEqualTo(1L);
  }
}
```

The oracle list and the trailing random value were produced by a C harness linking `gb_sort.o` and `gb_flip.o` with exactly these 20 keys after `gb_init_rand(1L)`.

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :lib:test --tests 'com.robsartin.jsgb.sort.LinkSortTest'`
Expected: compilation FAILS, `cannot find symbol ... Sortable`.

- [ ] **Step 3: Implement**

`Sortable.java`:
```java
package com.robsartin.jsgb.sort;

/**
 * The contract the C expresses as "the first field is {@code long key} and the second is
 * {@code struct ... *link}". Keys must be nonnegative and less than 2^31.
 */
public interface Sortable {
  long key();

  Sortable link();

  void setLink(Sortable next);
}
```

`LinkSort.java`:
```java
package com.robsartin.jsgb.sort;

import com.robsartin.jsgb.flip.Flip;

/**
 * Port of {@code gb_linksort}: a six-pass radix sort of a linked list into 256 buckets, with two
 * random passes first so that equal keys come out in random order. After sorting, read {@link
 * #sorted}[127] down to {@link #sorted}[0] for decreasing key order. The random generator must
 * be seeded first.
 */
public final class LinkSort {

  /** {@code gb_sorted}: bucket {@code j} holds keys in {@code [j * 2^24, (j + 1) * 2^24)}. */
  public static final Sortable[] sorted = new Sortable[256];

  private static final Sortable[] alt = new Sortable[256];

  private LinkSort() {}

  /** Clears both bucket arrays. Tests only. */
  static void reset() {
    java.util.Arrays.fill(sorted, null);
    java.util.Arrays.fill(alt, null);
  }

  private static void clear(Sortable[] buckets) {
    java.util.Arrays.fill(buckets, null);
  }

  private static void push(Sortable[] target, int k, Sortable p) {
    p.setLink(target[k]);
    target[k] = p;
  }

  /** {@code gb_linksort(l)}: sorts the list starting at {@code l} into {@link #sorted}. */
  public static void linksort(Sortable l) {
    Sortable p;
    Sortable q;
    // Pass 1: random distribution into alt.
    clear(alt);
    for (p = l; p != null; p = q) {
      int k = (int) (Flip.nextRand() >> 23);
      q = p.link();
      push(alt, k, p);
    }
    // Pass 2: random distribution from alt (255 down) into sorted.
    clear(sorted);
    for (int b = 255; b >= 0; b--) {
      for (p = alt[b]; p != null; p = q) {
        int k = (int) (Flip.nextRand() >> 23);
        q = p.link();
        push(sorted, k, p);
      }
    }
    // Pass 3: low byte, from sorted (255 down) into alt.
    clear(alt);
    for (int b = 255; b >= 0; b--) {
      for (p = sorted[b]; p != null; p = q) {
        int k = (int) (p.key() & 0xff);
        q = p.link();
        push(alt, k, p);
      }
    }
    // Pass 4: second byte, from alt (0 up) into sorted.
    clear(sorted);
    for (int b = 0; b < 256; b++) {
      for (p = alt[b]; p != null; p = q) {
        int k = (int) ((p.key() >> 8) & 0xff);
        q = p.link();
        push(sorted, k, p);
      }
    }
    // Pass 5: third byte, from sorted (255 down) into alt.
    clear(alt);
    for (int b = 255; b >= 0; b--) {
      for (p = sorted[b]; p != null; p = q) {
        int k = (int) ((p.key() >> 16) & 0xff);
        q = p.link();
        push(alt, k, p);
      }
    }
    // Pass 6: high byte, from alt (0 up) into sorted.
    clear(sorted);
    for (int b = 0; b < 256; b++) {
      for (p = alt[b]; p != null; p = q) {
        int k = (int) ((p.key() >> 24) & 0xff);
        q = p.link();
        push(sorted, k, p);
      }
    }
  }
}
```

`package-info.java` for `sort`: three sentences on why SGB sorts by weight with random tie-breaking, and that callers adapt their record type to `Sortable` where the C overlaid a `{key, link}` struct.

- [ ] **Step 4: Run to verify pass, run the full gate, commit**

Run: `./gradlew spotlessApply && ./gradlew check` — Expected: BUILD SUCCESSFUL. If the oracle order differs, check the walking direction of each pass against `gb_sort.w` sections 6 to 11 before anything else.

```bash
git add lib/src/main/java/com/robsartin/jsgb/sort lib/src/test/java/com/robsartin/jsgb/sort
git commit -m "Port gb_linksort

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 9: Architecture test and spec amendment

**Files:**
- Create: `lib/src/test/java/com/robsartin/jsgb/ArchitectureTest.java`
- Modify: `docs/superpowers/specs/2026-09-19-jsgb-design.md` (append an amendment; do not rewrite earlier text)

The spec says the kernel packages depend on no other jsgb package, but `gb_sort` calls `gb_next_rand`, so `sort` depends on `flip`. The C is the authority; the spec gets a dated amendment.

- [ ] **Step 1: Write the failing test**

`ArchitectureTest.java`:
```java
package com.robsartin.jsgb;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
    packages = "com.robsartin.jsgb",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String BASE = "com.robsartin.jsgb";

  @ArchTest
  static final ArchRule noCycles =
      SlicesRuleDefinition.slices().matching(BASE + ".(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule graphDependsOnNothing =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".graph..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(BASE + ".io..", BASE + ".flip..", BASE + ".sort..");

  @ArchTest
  static final ArchRule ioDependsOnNothing =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".io..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(BASE + ".graph..", BASE + ".flip..", BASE + ".sort..");

  @ArchTest
  static final ArchRule flipDependsOnNothing =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".flip..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(BASE + ".graph..", BASE + ".io..", BASE + ".sort..");

  @ArchTest
  static final ArchRule sortDependsOnlyOnFlip =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".sort..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(BASE + ".graph..", BASE + ".io..");
}
```

- [ ] **Step 2: Prove the rules can fail**

Temporarily add `private static final Object PROBE = com.robsartin.jsgb.flip.Flip.class;` to `Gb.java`, run `./gradlew :lib:test --tests 'com.robsartin.jsgb.ArchitectureTest'`, and confirm `graphDependsOnNothing` FAILS. Remove the probe line. Then run the test again and confirm all rules PASS.

- [ ] **Step 3: Amend the spec**

Append to the end of `docs/superpowers/specs/2026-09-19-jsgb-design.md`:

```markdown
## Amendments

### 2026-09-19: `sort` depends on `flip`

The Architecture tests section says the kernel packages `graph`, `io`, `flip`,
`sort` depend on no other jsgb package. That is wrong for `sort`: `gb_linksort`
draws random numbers from `gb_flip` for its two tie-breaking passes. The rule is
now: `graph`, `io`, `flip` depend on no other jsgb package; `sort` depends only
on `flip`. Found while porting `gb_sort` in increment 1.
```

- [ ] **Step 4: Run the full gate and commit**

Run: `./gradlew spotlessApply && ./gradlew check` — Expected: BUILD SUCCESSFUL.

```bash
git add lib/src/test/java/com/robsartin/jsgb/ArchitectureTest.java docs/superpowers/specs/2026-09-19-jsgb-design.md
git commit -m "Add ArchUnit layering rules and amend the spec for sort's use of flip

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Done criteria for increment 1

- `./gradlew clean check` is green on JDK 25.
- The translated C self-tests (`test_flip`, `test_io`, `test_graph`) pass as JUnit tests, plus the C-generated oracles for five RNG draws, the 20-key sort, and two hash buckets.
- All eleven data files open and close with clean checksums.
- ADR baseline plus the seven project ADRs are in `docs/adr/` and indexed.
- The branch is pushed and a PR to `main` is open (orchestrator's job after review).
