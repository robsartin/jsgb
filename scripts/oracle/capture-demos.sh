#!/bin/bash
# Captures the reference output of every SGB demo program from the C binaries.
#
# usage: capture-demos.sh <sgb-build-dir> <out-dir>
#
# <sgb-build-dir> is a built SGB tree (the demos and the .dat files in one
# directory, as scripts/regen-oracle.sh leaves it). For every case this writes
# under <out-dir>/<demo>/:
#   <case>.args   the arguments, one per line (absent when there are none)
#   <case>.in     the stdin fed to the program (absent when none)
#   <case>.out    its stdout
#   <case>.err    its stderr (absent when empty)
#   <case>.exit   its exit status as the shell saw it (C return value mod 256)
#   <case>.file.<name>  a file the program wrote (queen.gb, lisa.eps)
#   <case>.seed.<name>  a file that must exist in the working directory before the run
# The programs run with argv[0] equal to the bare demo name, so usage messages
# read "Usage: ladders ..." exactly as the Java launcher prints them.
set -euo pipefail
BUILD=$1
OUT=$2
cd "$BUILD"
export PATH=".:$PATH"
rm -rf "$OUT"
mkdir -p "$OUT"

# run <demo> <case> [args...]  (stdin from $STDIN, empty means /dev/null)
run() {
  local demo=$1 name=$2
  shift 2
  local dir="$OUT/$demo"
  mkdir -p "$dir"
  if [ $# -gt 0 ]; then printf '%s\n' "$@" > "$dir/$name.args"; fi
  if [ -n "${STDIN:-}" ]; then printf '%s' "$STDIN" > "$dir/$name.in"; fi
  set +e
  if [ -n "${STDIN:-}" ]; then
    printf '%s' "$STDIN" | "$demo" "$@" > "$dir/$name.out" 2> "$dir/$name.err"
  else
    "$demo" "$@" < /dev/null > "$dir/$name.out" 2> "$dir/$name.err"
  fi
  local code=$?
  set -e
  printf '%s\n' "$code" > "$dir/$name.exit"
  [ -s "$dir/$name.err" ] || rm -f "$dir/$name.err"
  echo "$demo/$name: exit $code"
}

# ---- queen (writes queen.gb; later cases restore it)
rm -f queen.gb
STDIN= run queen default
cp queen.gb "$OUT/queen/default.file.queen.gb"
mkdir -p "$OUT/roget_components" "$OUT/book_components"
cp queen.gb "$OUT/roget_components/restored.seed.queen.gb"
cp queen.gb "$OUT/book_components/restored.seed.queen.gb"

# ---- ladders
LADDER_IN=$'words\ngraph\nflour\nbread\n\n'
STDIN=$LADDER_IN run ladders plain
STDIN=$LADDER_IN run ladders alpha_heur_verbose -a -h -v
STDIN=$LADDER_IN run ladders freq_verbose -f -v
STDIN=$LADDER_IN run ladders hamming_heur -h -v
STDIN=$LADDER_IN run ladders random200 -r200 -s7 -v
STDIN=$'there\nthese\n\n' run ladders n50_verbose -n50 -v
STDIN=$'hello\nWORLD\nworld\nhelpo\nab\n\n' run ladders echo_badword -e
STDIN= run ladders eof_at_start
STDIN= run ladders usage -x

# ---- word_components
STDIN= run word_components default

# ---- roget_components
STDIN= run roget_components default
STDIN= run roget_components small -n100 -d2 -p5000 -s3
STDIN= run roget_components restored -gqueen.gb
STDIN= run roget_components usage -q

# ---- miles_span
STDIN= run miles_span default
STDIN= run miles_span verbose50 -n50 -v
STDIN= run miles_span repeat -r3 -n30 -s2
STDIN= run miles_span weights -n40 -N1 -W2 -P3 -d5 -v
STDIN= run miles_span usage -z

# ---- book_components
STDIN= run book_components default
STDIN= run book_components homer_v -thomer -v
STDIN= run book_components jean_V -tjean -V -n40
STDIN= run book_components david_window -tdavid -f3 -l10 -i2 -o1 -s5
STDIN= run book_components restored -gqueen.gb
STDIN= run book_components usage -y

# ---- econ_order
STDIN= run econ_order default
STDIN= run econ_order small_v -n20 -r3 -t7 -v
STDIN= run econ_order greedy_V -g -V -n10 -t1
STDIN= run econ_order usage -q

# ---- girth
STDIN=$'3\n7\n2\n17\n5\n3\n2\n5\n7\n11\n3\n5\n4\n5\nx\n' run girth session
STDIN= run girth eof

# ---- multiply
STDIN=$'2\n3\n7\n8\n3\n5\n\n' run multiply plain 2 3
STDIN=$'12\n0\n15\n\n' run multiply seeded 4 4 1
STDIN=$'1000\n3000\n\n' run multiply big 10 12
STDIN=$'12a\n5\n6\n\n' run multiply bad_digits 3 3
STDIN="$(printf '1%.0s' $(seq 1 302))"$'\n5\n6\n\n' run multiply too_big 3 3
STDIN= run multiply usage
STDIN= run multiply precision 1000 2
STDIN= run multiply negative -3 -3

# ---- take_risc
STDIN=$'3\n4\n' run take_risc plain
STDIN=$'3\n4\n' run take_risc trace x
STDIN=$'0\n5\n99999\n40000\n7\n0\n' run take_risc errors
STDIN=$'12345\n999\nabc\n' run take_risc big_then_bad

# ---- assign_lisa (writes lisa.eps with -P)
STDIN= run assign_lisa small_p m=10 n=10 -p
STDIN= run assign_lisa rect_v m=5 n=8 -p -v
STDIN= run assign_lisa transposed_V_c m=8 n=5 -p -V -c
STDIN= run assign_lisa heur_v -h m=6 n=6 -v
STDIN= run assign_lisa window m=12 n=12 m0=100 m1=140 n0=80 n1=120 d=100 -p
rm -f lisa.eps
STDIN= run assign_lisa eps m=4 n=4 -P
cp lisa.eps "$OUT/assign_lisa/eps.file.lisa.eps"
STDIN= run assign_lisa smile -s
STDIN= run assign_lisa eyes_v -e -v
STDIN= run assign_lisa bad_window m0=10 m1=10
STDIN= run assign_lisa usage -q

# ---- football
STDIN=$'Stanford\nMichigan\n\n' run football greedy
STDIN=$'Stanford\nMichigan\n\n' run football width5 5
STDIN=$'Ohio State\nMichigan\n\n' run football width3_v 3 -v
STDIN=$'Nowhere U\nStanford\nStanford\n\n\n' run football unknown_and_same
STDIN= run football usage 1 2

echo "captured into $OUT"
