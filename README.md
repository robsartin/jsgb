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
