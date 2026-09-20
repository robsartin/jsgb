---
status: Accepted
date: "2026-09-19"
topic: hand-rolled-demo-arguments
tags: [project, cli]
supersedes: []
related: [cli-conventions]
---
# 19. Hand-rolled demo argument parsing over picocli

## Context

The baseline CLI conventions ADR (12) documents general command-line hygiene: meaningful exit
codes, stdout/stderr separation, `--help` availability, and TTY-aware behaviour. jsgb's twelve
demo programs reproduce their C counterparts' argument parsing and printed usage messages
exactly, because demo output — including the usage and error text printed on bad arguments — is
itself part of the byte-exact oracle the port is checked against (captured stdout from the
reference C binaries). Three of the demos (`football`, `girth`, `multiply`) are interactive and
read stdin, as in C.

## Decision

Each demo's argument parsing is hand-written to match its C counterpart's parsing logic and
printed messages exactly, rather than delegated to a general-purpose CLI parsing library.

## Alternatives considered

- **picocli** (a widely used Java CLI framework offering annotated option parsing, generated
  `--help` text, and other conveniences the baseline CLI conventions ADR would otherwise
  recommend) — rejected: picocli generates its own usage and error text and follows its own
  conventions for option syntax and message formatting, which are not the C program's text.
  Since usage messages are themselves part of the observable, oracle-checked output — a wrong
  error message on bad input diverges from the captured C stdout exactly as a wrong computed
  answer would — no framework that generates its own message text can be dropped in without
  per-command overrides extensive enough to fight the framework more than they save.

## Consequences

Demo argument handling is more manual and verbose than the idiomatic approach the baseline CLI
conventions ADR (12) would otherwise recommend, and each new demo requires re-deriving its usage
text by hand from the C source rather than generating it. In exchange, demo behaviour, including
error paths and usage messages, can be byte-exact against captured C output, consistent with the
project's overall bit-exact commitment (13).
