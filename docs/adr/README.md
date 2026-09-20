# Architecture Decision Records

## Project

- [13. Bit-exact port over behavioural equivalence](0013-bit-exact-port.md) — _Accepted_
  The Stanford GraphBase (SGB) is a benchmark platform, not just a graph library: its value comes from every implementation producing exactly the same output for the same inputs and seeds, so that results (including the published algorithm-performance numbers in Knuth's own work) are comparable across machines, compilers, and now languages.
  Related: [14. Static global state over a context object](0014-static-global-state.md), [15. Public mutable fields on Graph, Vertex, Arc, and Util](0015-public-mutable-fields.md), [16. Null return plus Gb.panicCode over a PanicException](0016-null-plus-panic-code.md), [17. Explicit ordering stand-ins over pointer arithmetic](0017-ordering-without-pointers.md)
- [14. Static global state over a context object](0014-static-global-state.md) — _Accepted_
  SGB's C implementation is built around global state: `panic_code`, `verbose`, `gb_trouble_code`, `extra_n`, `cur_graph`, the RNG array and cursor, `gb_sorted[]`, the open data file and `io_errors`, and several module-local globals such as the Dijkstra queue state, `chapters`/`chap_name[]`, and `risc_state[]`.
  Related: [16. Null return plus Gb.panicCode over a PanicException](0016-null-plus-panic-code.md)
- [15. Public mutable fields on Graph, Vertex, Arc, and Util](0015-public-mutable-fields.md) — _Accepted_
  The emitted baseline Java conventions ADR (11) recommends idiomatic modern Java, including `Optional` over returning null at boundaries and, more broadly, the encapsulated style that usually pairs with accessor methods rather than exposed fields.
  Related: [11. Java language conventions](0011-java-conventions.md)
- [16. Null return plus Gb.panicCode over a PanicException](0016-null-plus-panic-code.md) — _Accepted_
  SGB generators in C signal failure by setting the global `panic_code` and returning `NULL`; callers, including `test_sample`, inspect `panic_code` to decide what happened and print it as part of the program's normal, oracle-checked output.
  Related: [14. Static global state over a context object](0014-static-global-state.md), [11. Java language conventions](0011-java-conventions.md)
- [17. Explicit ordering stand-ins over pointer arithmetic](0017-ordering-without-pointers.md) — _Accepted_
  Three C behaviours depend on memory addresses that Java has no equivalent for.
- [18. Javadoc as the literate layer, not CWEB for Java](0018-javadoc-as-literate-layer.md) — _Accepted_
  SGB's C source is written in CWEB, Knuth's literate-programming system, which interleaves prose exposition and code and requires `weave`/`tangle` tooling to produce readable documentation and compilable source respectively.
- [19. Hand-rolled demo argument parsing over picocli](0019-hand-rolled-demo-arguments.md) — _Accepted_
  The baseline CLI conventions ADR (12) documents general command-line hygiene: meaningful exit codes, stdout/stderr separation, `--help` availability, and TTY-aware behaviour.
  Related: [12. CLI conventions](0012-cli-conventions.md)

## Universal

- [1. Record architecture decisions with ADRs](0001-record-architecture-decisions.md) — _Accepted_
  Architecturally significant decisions — choices that shape structure, dependencies, interfaces, or the way the team works — need a durable record.
  Related: [6. Keep developer and user documentation current](0006-keep-documentation-current.md)
- [2. Develop with Test-Driven Development](0002-use-test-driven-development.md) — _Accepted_
  We want a fast feedback loop, a regression safety net, executable documentation of behavior, and the freedom to refactor without fear.
- [3. Integrate via a PR-based trunk workflow](0003-pr-based-trunk-workflow.md) — _Accepted_
  We want `main` to stay releasable at all times, changes to be reviewable in coherent units, and history to be legible.
  Related: [4. Use the Mikado Method to keep the build green](0004-mikado-method-for-changes.md), [5. Make CI the merge gate](0005-ci-is-the-merge-gate.md), [6. Keep developer and user documentation current](0006-keep-documentation-current.md)
- [4. Use the Mikado Method to keep the build green](0004-mikado-method-for-changes.md) — _Accepted_
  Large refactorings, and changes that ripple across a codebase, tempt us into long stretches where nothing compiles and nothing is committable.
  Related: [3. Integrate via a PR-based trunk workflow](0003-pr-based-trunk-workflow.md)
- [5. Make CI the merge gate](0005-ci-is-the-merge-gate.md) — _Accepted_
  Standards that are not enforced erode.
  Related: [10. Enforce JVM quality gates and layered tests](0010-jvm-quality-and-tests.md)
- [6. Keep developer and user documentation current](0006-keep-documentation-current.md) — _Accepted_
  Documentation that lags the code is worse than none — it misleads.
  Related: [1. Record architecture decisions with ADRs](0001-record-architecture-decisions.md), [3. Integrate via a PR-based trunk workflow](0003-pr-based-trunk-workflow.md)
- [7. Declare an explicit license and copyright](0007-license-and-copyright.md) — _Accepted_
  A repository with no license is "all rights reserved" by default — others (and future us) have no clear terms for use, and intent is ambiguous.
- [8. Maintain a security baseline](0008-security-baseline.md) — _Accepted_
  Secrets committed to a repository are effectively public and permanent — history preserves them even after deletion.

## Language

- [9. Build JVM projects with Gradle](0009-jvm-build-with-gradle.md) — _Accepted_
  JVM projects need a consistent build tool, dependency management, and package organization so repositories are predictable to build and navigate, and so shared tooling (formatting, coverage, arch tests) can be applied the same way everywhere.
  Related: [10. Enforce JVM quality gates and layered tests](0010-jvm-quality-and-tests.md), [11. Java language conventions](0011-java-conventions.md)
- [10. Enforce JVM quality gates and layered tests](0010-jvm-quality-and-tests.md) — _Accepted_
  The universal CI-gate decision requires enforced formatting, tests, and coverage, and this project's baseline also calls for architecture tests and real-dependency integration tests.
  Related: [9. Build JVM projects with Gradle](0009-jvm-build-with-gradle.md), [11. Java language conventions](0011-java-conventions.md), [5. Make CI the merge gate](0005-ci-is-the-merge-gate.md)
- [11. Java language conventions](0011-java-conventions.md) — _Accepted_
  Java builds on the shared JVM baseline (Gradle, Spotless, JaCoCo, layered tests) and needs its language level and formatting standard pinned so Java repositories are consistent.
  Related: [9. Build JVM projects with Gradle](0009-jvm-build-with-gradle.md), [10. Enforce JVM quality gates and layered tests](0010-jvm-quality-and-tests.md)

## App shape

- [12. CLI conventions](0012-cli-conventions.md) — _Accepted_
  Command-line tools are used by people at a terminal and by scripts and CI.
