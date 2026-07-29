#!/usr/bin/env bash
#
# Differential test for CBTRN02C: run the REAL COBOL (app/cbl/CBTRN02C.cbl, compiled
# unmodified with GnuCOBOL) and the Java port over identical inputs, then diff every output
# file byte for byte - the posted transaction file, the reject file, the account master and
# the category balances - and report the return code from both sides.
#
# Two scenarios:
#   A. the 300 shipped records in app/data/ASCII, which only ever trip reject reason 0102
#   B. a generated adversarial feed that trips every reject reason and sits exactly on every
#      boundary the rule catalogue identifies, one unit either side
#
# In both, the Java runs with --bug-for-bug, because that is the mode that claims to be
# CBTRN02C. Its default mode deliberately behaves differently; Phase 3's JUnit tests cover
# that, and scenario B prints the difference for comparison.
#
# What this does NOT prove: GnuCOBOL is not IBM Enterprise COBOL. See COBOL-PARITY.md.
#
# Usage: scripts/cobol-parity/run-posting-parity.sh
# Requires: GnuCOBOL 3 with an indexed file handler (Debian/Ubuntu: gnucobol3), JDK 17, Maven.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SRC="$REPO_ROOT/scripts/cobol-parity"
DATA="$REPO_ROOT/app/data/ASCII"
OUT="$REPO_ROOT/target/cbtrn02c-parity"
BIN="$OUT/bin"
JAR="$REPO_ROOT/java/cbtrn02c/target/cbtrn02c-1.0.0-SNAPSHOT.jar"

say() { printf '\n=== %s\n' "$1"; }
die() { printf 'ERROR: %s\n' "$1" >&2; exit 1; }

say "Checking the toolchain"
command -v cobc >/dev/null 2>&1 || die "cobc (GnuCOBOL) not found: sudo apt-get install -y gnucobol3"
command -v java >/dev/null 2>&1 || die "java not found. See RUN-LOCALLY.md"
cobc --version | head -1
if cobc --info 2>/dev/null | grep -q "indexed file handler *: *disabled"; then
    die "This GnuCOBOL build has no indexed (ISAM) handler, so the VSAM KSDS inputs cannot be
       created. Ubuntu's gnucobol4 package is built without it; use gnucobol3 instead:
       sudo apt-get remove -y gnucobol4 && sudo apt-get install -y gnucobol3"
fi

if [ ! -f "$JAR" ]; then
    say "Building the Java port"
    (cd "$REPO_ROOT/java" && mvn -B -q package -DskipTests)
fi

rm -rf "$OUT"
mkdir -p "$BIN"

# -fsign=EBCDIC makes GnuCOBOL store the sign of a DISPLAY numeric as an overpunch in the
# last byte ('{' = +0 ... 'I' = +9, '}' = -0 ... 'R' = -9), which is what the EBCDIC-derived
# sample data uses. Without it every signed field GnuCOBOL writes differs.
say "Compiling app/cbl/CBTRN02C.cbl (unmodified) and the harness with GnuCOBOL"
cobc -x -fsign=EBCDIC -I "$REPO_ROOT/app/cpy" -o "$BIN/CBTRN02C" "$REPO_ROOT/app/cbl/CBTRN02C.cbl"
for prog in LOADPOST UNLDPOST; do
    cobc -x -fsign=EBCDIC -o "$BIN/$prog" "$SRC/$prog.cbl"
done
echo "Compiled with no errors."

FAILURES=0

run_scenario() {
    local dir="$1" label="$2" dalytran="$3" cardxref="$4" acctdata="$5" tcatbal="$6"

    mkdir -p "$dir"
    say "Scenario: $label"

    # DALYTRAN is ORGANIZATION SEQUENTIAL (RECFM=F), not line sequential: 350 byte records
    # butted up against each other with no line endings. Build that from the text file.
    python3 -c "
import sys
from pathlib import Path
src = Path(sys.argv[1]).read_text('latin-1').splitlines()
Path(sys.argv[2]).write_text(''.join(line.ljust(350)[:350] for line in src), 'latin-1')
print(f'DALYTRAN: {len(src)} records')
" "$dalytran" "$dir/DALYTRAN"

    # -- COBOL side: build the indexed files IDCAMS-style, run the POSTTRAN step, unload.
    # TRANFILE is deliberately not created here: CBTRN02C opens it OUTPUT (rule R22).
    DD_XREFTXT="$cardxref" DD_ACCTTXT="$acctdata" DD_TCATBALT="$tcatbal" \
    DD_XREFFILE="$dir/XREFFILE" DD_ACCTFILE="$dir/ACCTFILE" DD_TCATBALF="$dir/TCATBALF" \
        "$BIN/LOADPOST" > "$dir/load.log"

    set +e
    DD_DALYTRAN="$dir/DALYTRAN" DD_XREFFILE="$dir/XREFFILE" DD_ACCTFILE="$dir/ACCTFILE" \
    DD_TCATBALF="$dir/TCATBALF" DD_TRANFILE="$dir/TRANFILE" DD_DALYREJS="$dir/rejs.cobol" \
        "$BIN/CBTRN02C" > "$dir/sysout.cobol" 2>&1
    local cobol_rc=$?
    set -e

    DD_ACCTFILE="$dir/ACCTFILE" DD_TCATBALF="$dir/TCATBALF" DD_TRANFILE="$dir/TRANFILE" \
    DD_ACCTOUT="$dir/acct.cobol" DD_TCATBALOUT="$dir/tcat.cobol" DD_TRANOUT="$dir/tran.cobol" \
        "$BIN/UNLDPOST" >> "$dir/load.log"

    # -- Java side: identical inputs, its own copies of the two files opened I-O.
    cp "$acctdata" "$dir/acct.java"
    cp "$tcatbal" "$dir/tcat.java"
    set +e
    java -jar "$JAR" \
        --dalytran "$dalytran" \
        --xreffile "$cardxref" \
        --acctfile "$dir/acct.java" \
        --tcatbalf "$dir/tcat.java" \
        --tranfile "$dir/tran.java" \
        --dalyrejs "$dir/rejs.java" \
        --bug-for-bug > "$dir/sysout.java" 2>&1
    local java_rc=$?
    set -e

    printf '  COBOL rc=%s   Java rc=%s\n' "$cobol_rc" "$java_rc"
    grep -E 'TRANSACTIONS (PROCESSED|REJECTED)' "$dir/sysout.cobol" | sed 's/^/  COBOL /'
    grep -E 'TRANSACTIONS (PROCESSED|REJECTED)' "$dir/sysout.java" | sed 's/^/  Java  /'
    if [ "$cobol_rc" != "$java_rc" ]; then
        echo "  RETURN CODE MISMATCH"
        FAILURES=$((FAILURES + 1))
    fi

    python3 "$SRC/compare-posting.py" "$dir" || FAILURES=$((FAILURES + 1))
}

# Scenario A: exactly what ships in the repo, all 300 records.
run_scenario "$OUT/shipped" "the 300 shipped records in app/data/ASCII" \
    "$DATA/dailytran.txt" "$DATA/cardxref.txt" "$DATA/acctdata.txt" "$DATA/tcatbal.txt"

# Scenario B: every reject reason and every boundary, one unit either side.
say "Generating adversarial inputs"
python3 "$SRC/make-posting-adversarial.py" "$DATA" "$OUT/adversarial/in"
run_scenario "$OUT/adversarial" "adversarial feed: every reject reason and every boundary" \
    "$OUT/adversarial/in/dailytran.txt" "$OUT/adversarial/in/cardxref.txt" \
    "$OUT/adversarial/in/acctdata.txt" "$DATA/tcatbal.txt"

# For contrast only, not a parity check: the same adversarial feed with the corrected
# behaviour, to show what the flags actually change.
say "The same adversarial feed with the corrected behaviour (not a parity check)"
cp "$OUT/adversarial/in/acctdata.txt" "$OUT/adversarial/acct.corrected"
cp "$DATA/tcatbal.txt" "$OUT/adversarial/tcat.corrected"
set +e
java -jar "$JAR" \
    --dalytran "$OUT/adversarial/in/dailytran.txt" \
    --xreffile "$OUT/adversarial/in/cardxref.txt" \
    --acctfile "$OUT/adversarial/acct.corrected" \
    --tcatbalf "$OUT/adversarial/tcat.corrected" \
    --tranfile "$OUT/adversarial/tran.corrected" \
    --dalyrejs "$OUT/adversarial/rejs.corrected" > "$OUT/adversarial/sysout.corrected" 2>&1
echo "  Java rc=$?"
set -e
grep -E 'TRANSACTIONS' "$OUT/adversarial/sysout.corrected" | sed 's/^/  /'
echo "  reject reasons (Java, corrected): $(cut -c351-354 "$OUT/adversarial/rejs.corrected" \
    | sort | uniq -c | tr '\n' ' ')"

say "Artifacts"
echo "  $OUT/shipped       the 300 shipped records"
echo "  $OUT/adversarial   generated edge cases, with in/ holding the inputs used"
echo "  each holds tran/rejs/acct/tcat .cobol and .java, plus both SYSOUT logs"

if [ "$FAILURES" -eq 0 ]; then
    say "PARITY CONFIRMED: every output file matched byte for byte (TRAN-PROC-TS masked)"
else
    say "$FAILURES check(s) failed"
    exit 1
fi
