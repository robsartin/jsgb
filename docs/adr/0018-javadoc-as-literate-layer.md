---
status: Accepted
date: "2026-09-19"
topic: javadoc-as-literate-layer
tags: [project, documentation]
supersedes: []
related: []
---
# 18. Javadoc as the literate layer, not CWEB for Java

## Context

SGB's C source is written in CWEB, Knuth's literate-programming system, which interleaves prose
exposition and code and requires `weave`/`tangle` tooling to produce readable documentation and
compilable source respectively. jsgb's non-goals explicitly exclude porting this documentation
layer as literate source.

## Decision

jsgb carries its exposition in `package-info.java` (one per module, each holding that module's
introduction in prose) and in ordinary Javadoc on classes and methods, including each module's
util-slot claims documented to match the C `#define` macros one for one. There is no CWEB-style
tangle step anywhere in the jsgb build.

## Alternatives considered

- **CWEB for Java** (an analogous literate-programming toolchain, weaving prose and Java source
  together as Knuth did for C) — rejected: standard Java tooling — Gradle, IDEs (autocomplete,
  refactoring, jump-to-definition), and JUnit test discovery — all expect ordinary `.java` files
  on disk. A CWEB-style tangle step would insert a translation layer between every edit and the
  compiler, IDE, and test runner, and CI would need to run `ctangle` before every build and test
  run, fighting the tooling this project already depends on (google-java-format, ArchUnit,
  JaCoCo) for a Java audience that does not read CWEB in the first place.

## Consequences

SGB's literate exposition is not preserved as literate source in jsgb; a reader who wants the
original prose-interleaved-with-code experience reads the C `.w` files directly. jsgb's own
documentation lives as ordinary Javadoc, trading away CWEB's single-document prose-and-code
interleaving for standard Java tooling support: a generated Javadoc site, IDE hover documentation,
and no separate build step.
