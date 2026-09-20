#!/usr/bin/env bash
# Rebuild the C Stanford GraphBase in a scratch directory and regenerate the
# increment-2 oracle files. Needs cweb (brew install cweb) and ~/code/sgb.
# Archives a commit pinned to 88fac2f (not master), so the C oracles stay
# reproducible even if ~/code/sgb's master moves on.
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
