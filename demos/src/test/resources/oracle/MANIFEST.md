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
