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
`raman`, `basic`, `rand`, `words`, `roget`, `miles`, `plane`. Algorithms:
`dijk`, `save`. Data files ship unmodified under
`lib/src/main/resources/sgb/`; a `jsgb.data.dir` system property names a
directory to search before the classpath, playing the role of the C
`DATA_DIRECTORY`.

`demos/` holds `TestSample`, the port of `test_sample`: it prints the salient
characteristics of a handful of generated graphs so the output can be
compared, stanza by stanza, against the SGB distribution's own
`sample.correct`. `lib`'s tests read the same oracle fixtures through the
extra test resource root declared in `lib/build.gradle.kts`, so
`demos/src/test/resources/oracle` stays the single canonical copy.

## Running the sample

`./gradlew :demos:run` runs the sample sequence ported so far, printing
eleven of the sixteen stanzas of `sample.correct` and writing `test.gb` in
the working directory. It is also exercised by `./gradlew :demos:test`. The
multi-demo launcher arrives in increment 4.

## Regenerating oracles

`scripts/regen-oracle.sh` rebuilds the C Stanford GraphBase and regenerates
the `demos/src/test/resources/oracle` fixtures (see that directory's
`MANIFEST.md` for provenance).

## Licence

MIT for the Java code. The Stanford GraphBase data files are redistributed
unmodified under their own terms; see `NOTICE`.
