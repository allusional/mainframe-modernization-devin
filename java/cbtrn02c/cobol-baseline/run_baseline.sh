#!/usr/bin/env bash
# Compile and run the original COBOL CBTRN02C (POSTTRAN) under GnuCOBOL against
# the ASCII sample fixtures, and unload its output files to flat fixed length
# files that form the golden baseline for the Java port.
#
# Requires GnuCOBOL (cobc) with an indexed file handler: sudo apt-get install -y gnucobol
#
# The processing timestamp is pinned via COB_CURRENT_DATE so that TRAN-PROC-TS
# is deterministic and can be reproduced byte for byte by the Java port.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../../.." && pwd)"
CPY="$REPO/app/cpy"
DATA="$REPO/app/data/ASCII"
WORK="${WORK_DIR:-$HERE/work}"
OUT="${BASELINE_DIR:-$HERE/baseline}"

# Pinned run time as YYYYMMDDHHMMSShh (hundredths included, otherwise GnuCOBOL
# takes them from the real clock and TRAN-PROC-TS is not reproducible).
# Must match the --timestamp passed to the Java port.
export COB_CURRENT_DATE="${COB_CURRENT_DATE:-2022071112000000}"

rm -rf "$WORK"
mkdir -p "$WORK/bin" "$WORK/data" "$OUT"

echo "== compiling CBTRN02C and the load/unload utilities"
# -fsign=EBCDIC: the ASCII fixtures carry the mainframe overpunch sign in the
# trailing digit of signed DISPLAY fields ({=+0, A-I=+1..9, }=-0, J-R=-1..9).
# Without it GnuCOBOL reads 'G' as 0 and every amount is silently truncated.
COBC_FLAGS=(-x -std=mf -fsign=EBCDIC)
cobc "${COBC_FLAGS[@]}" -I "$CPY" -o "$WORK/bin/CBTRN02C" "$REPO/app/cbl/CBTRN02C.cbl"
for p in LOADXREF LOADACCT LOADTCAT UNLDTRAN UNLDACCT UNLDTCAT; do
  cobc "${COBC_FLAGS[@]}" -o "$WORK/bin/$p" "$HERE/$p.cbl"
done

echo "== normalizing ASCII fixtures to fixed length records"
python3 "$HERE/normalize_fixture.py" "$DATA/dailytran.txt" "$WORK/data/dailytran.dat" 350
python3 "$HERE/normalize_fixture.py" "$DATA/cardxref.txt"  "$WORK/data/cardxref.dat"  50
python3 "$HERE/normalize_fixture.py" "$DATA/acctdata.txt"  "$WORK/data/acctdata.dat"  300
python3 "$HERE/normalize_fixture.py" "$DATA/tcatbal.txt"   "$WORK/data/tcatbal.dat"   50

echo "== loading the keyed (indexed) files"
INFILE="$WORK/data/cardxref.dat" OUTFILE="$WORK/data/CARDXREF.KSDS" "$WORK/bin/LOADXREF"
INFILE="$WORK/data/acctdata.dat" OUTFILE="$WORK/data/ACCTDATA.KSDS" "$WORK/bin/LOADACCT"
INFILE="$WORK/data/tcatbal.dat"  OUTFILE="$WORK/data/TCATBALF.KSDS" "$WORK/bin/LOADTCAT"

echo "== running COBOL CBTRN02C (COB_CURRENT_DATE=$COB_CURRENT_DATE)"
set +e
env DALYTRAN="$WORK/data/dailytran.dat" \
    XREFFILE="$WORK/data/CARDXREF.KSDS" \
    ACCTFILE="$WORK/data/ACCTDATA.KSDS" \
    TCATBALF="$WORK/data/TCATBALF.KSDS" \
    TRANFILE="$WORK/data/TRANSACT.KSDS" \
    DALYREJS="$WORK/data/dalyrejs.dat" \
    "$WORK/bin/CBTRN02C" | tee "$OUT/cobol-run.log"
rc=${PIPESTATUS[0]}
set -e
echo "COBOL RETURN-CODE: $rc" | tee -a "$OUT/cobol-run.log"

echo "== unloading the COBOL output files in key order"
INFILE="$WORK/data/TRANSACT.KSDS" OUTFILE="$OUT/transact.dat" "$WORK/bin/UNLDTRAN"
INFILE="$WORK/data/ACCTDATA.KSDS" OUTFILE="$OUT/acctdata.dat" "$WORK/bin/UNLDACCT"
INFILE="$WORK/data/TCATBALF.KSDS" OUTFILE="$OUT/tcatbal.dat"  "$WORK/bin/UNLDTCAT"
cp "$WORK/data/dalyrejs.dat" "$OUT/dalyrejs.dat"
printf '%s\n' "$rc" > "$OUT/return-code.txt"

echo
echo "baseline written to $OUT:"
ls -l "$OUT"
