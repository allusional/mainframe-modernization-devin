#!/usr/bin/env bash
# Produce the COBOL baseline ("golden") outputs for a parity scenario by
# compiling and running the unmodified app/cbl/CBTRN02C.cbl with GnuCOBOL.
#
#   run_cobol.sh <scenario-name>
#
# Inputs  : parity/data/<scenario>/{dailytran,cardxref,acctdata,tcatbal}.txt
# Outputs : parity/golden/<scenario>/{TRANSACT,ACCTDATA,TCATBAL,DALYREJS}.dat
#           parity/golden/<scenario>/stdout.txt , rc.txt
set -euo pipefail

SCENARIO="$1"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PARITY="$(dirname "$HERE")"
MODULE="$(dirname "$PARITY")"
REPO="$(cd "$MODULE/../.." && pwd)"

# Scenario "full" uses the datasets shipped with the repo; every other
# scenario has its own crafted dataset under parity/data/.
if [ "$SCENARIO" = "full" ]; then
  SRC="$REPO/app/data/ASCII"
else
  SRC="$PARITY/data/$SCENARIO"
fi
WORK="$PARITY/work/cobol/$SCENARIO"
GOLD="$PARITY/golden/$SCENARIO"
BIN="$PARITY/work/bin"

rm -rf "$WORK" "$GOLD"; mkdir -p "$WORK" "$GOLD" "$BIN"

echo "== compiling COBOL =="
# -fsign=EBCDIC selects the IBM overpunched trailing sign ({ A-I / } J-R)
# used by the CardDemo sample datasets; GnuCOBOL defaults to its own ASCII
# sign convention, which would misread them.
COBFLAGS=(-x -fsign=EBCDIC)
cobc "${COBFLAGS[@]}" -I "$REPO/app/cpy" -o "$BIN/CBTRN02C" "$REPO/app/cbl/CBTRN02C.cbl"
for p in LOADXREF LOADACCT LOADTCAT DUMPTRAN DUMPACCT DUMPTCAT; do
  cobc "${COBFLAGS[@]}" -o "$BIN/$p" "$PARITY/cobol/$p.cbl"
done

echo "== preparing fixed-length inputs =="
"$HERE/prep_inputs.sh" "$SRC" "$WORK" > /dev/null

echo "== loading indexed (KSDS-equivalent) files =="
export DD_XREFFLAT="$WORK/CARDXREF.dat" DD_XREFFILE="$WORK/XREFFILE.idx"
export DD_ACCTFLAT="$WORK/ACCTDATA.dat" DD_ACCTFILE="$WORK/ACCTFILE.idx"
export DD_TCATFLAT="$WORK/TCATBAL.dat"  DD_TCATBALF="$WORK/TCATBALF.idx"
"$BIN/LOADXREF"
"$BIN/LOADACCT"
"$BIN/LOADTCAT"

echo "== running CBTRN02C =="
export DD_DALYTRAN="$WORK/DALYTRAN.dat"
export DD_TRANFILE="$WORK/TRANFILE.idx"
export DD_DALYREJS="$WORK/DALYREJS.dat"
set +e
"$BIN/CBTRN02C" > "$GOLD/stdout.txt" 2>&1
RC=$?
set -e
echo "$RC" > "$GOLD/rc.txt"
cat "$GOLD/stdout.txt"
echo "RETURN-CODE=$RC"

echo "== dumping indexed outputs in key order =="
export DD_TRANFLAT="$GOLD/TRANSACT.dat"
export DD_ACCTFLATO="$GOLD/ACCTDATA.dat"
export DD_TCATFLATO="$GOLD/TCATBAL.dat"
"$BIN/DUMPTRAN"
"$BIN/DUMPACCT"
"$BIN/DUMPTCAT"
cp "$WORK/DALYREJS.dat" "$GOLD/DALYREJS.dat" 2>/dev/null || : > "$GOLD/DALYREJS.dat"

echo "== COBOL baseline written to $GOLD =="
ls -l "$GOLD"
