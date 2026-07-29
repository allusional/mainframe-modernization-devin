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
# Both scenarios are fixed, so they can only confirm behaviours somebody thought of. For random
# feeds drawn from the record grammar, see run-posting-fuzz.sh.
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

# shellcheck source=scripts/cobol-parity/posting-scenario.sh
source "$SRC/posting-scenario.sh"

say "Checking the toolchain"
posting_check_toolchain

rm -rf "$OUT"

say "Compiling app/cbl/CBTRN02C.cbl (unmodified) and the harness with GnuCOBOL"
posting_compile "$BIN"
echo "Compiled with no errors."

FAILURES=0

run_scenario() {
    local dir="$1" label="$2" dalytran="$3" cardxref="$4" acctdata="$5" tcatbal="$6"

    say "Scenario: $label"
    posting_run_scenario "$BIN" "$dir" "$dalytran" "$cardxref" "$acctdata" "$tcatbal"
    local cobol_rc="$POSTING_COBOL_RC" java_rc="$POSTING_JAVA_RC"

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
