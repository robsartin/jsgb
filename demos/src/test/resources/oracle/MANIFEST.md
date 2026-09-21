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
| `inc3a/oracle_inc3a.out` | stdout of `scripts/oracle/oracle_inc3a.c` linked against `libgb.a` |
| `inc3b/oracle_inc3b.out` | stdout of `scripts/oracle/oracle_inc3b.c` linked against `libgb.a` |

`oracle_inc2.out` and `inc3a/oracle_inc3a.out` are each a sequence of cases; every case begins
with a line `==name` (or `==name=returnvalue`) followed by exactly what `print_sample` printed for
that case, except that `oracle_inc3a.out`'s `find_word`, `delaunay` and `dijkstra_*` cases are
printed by custom code in the harness instead of `print_sample`, and the `dijkstra_heuristic` and
`dijkstra_verbose_plain` cases toggle `verbose` on around the call.

`inc3b/oracle_inc3b.out` follows the same `==name`/`==name=returnvalue` convention. Its
`book_chapters` case begins with a custom `chapters=... first=... last=...` line before the
`print_sample` output; `lisa_matrix`, `lisa_window`, `risc2_eval`, `run_risc_mult`, `run_risc_div`
and `risc2_gates` are printed entirely by custom harness code instead of `print_sample`; `prod22`
and `partial_prod33`/`partial_risc_stanza4` print custom text (`print_gates`, or a
`partial_gates`-filled buffer) before their `print_sample` output.

## Demo captures (`demos/`)

`demos/<demo>/` holds one case per program invocation of the C demo of that name, produced by
`scripts/oracle/capture-demos.sh` (which `scripts/regen-oracle.sh` runs after building the demos).
The script's header comment defines the per-case files (`.args`, `.in`, `.out`, `.err`, `.exit`,
`.file.<name>`, `.seed.<name>`); the script itself is the record of every command line and stdin.
The programs run with `argv[0]` equal to the bare demo name, so usage messages read exactly as the
Java launcher prints them, and exit statuses are the C return values as the shell saw them
(modulo 256).
