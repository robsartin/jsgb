#!/usr/bin/env bash
# Rebuild the C Stanford GraphBase in a scratch directory and regenerate the
# increment 2 and 3a oracle files. Needs cweb (brew install cweb) and
# ~/code/sgb. Archives a commit pinned to 88fac2f (not master), so the C
# oracles stay reproducible even if ~/code/sgb's master moves on. A later
# increment's harness is added in the same pattern: copy its oracle_incNN.c
# in, compile and run it, then copy its oracle_incNN.out to the matching
# demos/src/test/resources/oracle/incNN directory.
set -euo pipefail
SGB="${SGB:-$HOME/code/sgb}"
HERE="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
git -C "$SGB" archive 88fac2f051f445d68521dbe6cb756a43e5d53e8a | tar -x -C "$WORK"
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

cp "$HERE/scripts/oracle/oracle_inc3a.c" .
cc -w -I. oracle_inc3a.c -L. -lgb -o oracle_inc3a
./oracle_inc3a > oracle_inc3a.out
OUT3A="$HERE/demos/src/test/resources/oracle/inc3a"
cp oracle_inc3a.out "$OUT3A/"
echo "regenerated into $OUT3A (scratch: $WORK)"

cp "$HERE/scripts/oracle/oracle_inc3b.c" .
cc -w -I. oracle_inc3b.c -L. -lgb -o oracle_inc3b
./oracle_inc3b > oracle_inc3b.out
OUT3B="$HERE/demos/src/test/resources/oracle/inc3b"
cp oracle_inc3b.out "$OUT3B/"
echo "regenerated into $OUT3B (scratch: $WORK)"

# ---- demo programs: build the twelve C demos and capture every case
make assign_lisa book_components econ_order football girth ladders miles_span multiply queen roget_components take_risc word_components \
  CFLAGS="-g -I. -DSYSV -Wno-implicit-int -Wno-deprecated-non-prototype -Wno-implicit-function-declaration" >/dev/null
"$HERE/scripts/oracle/capture-demos.sh" "$WORK" "$HERE/demos/src/test/resources/oracle/demos"
echo "regenerated demo captures into $HERE/demos/src/test/resources/oracle/demos (scratch: $WORK)"
