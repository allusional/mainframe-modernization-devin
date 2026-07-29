#!/usr/bin/env bash
#
# Differential test: run the REAL COBOL (CBACT04C.cbl, compiled unmodified with GnuCOBOL)
# and the Java port over identical inputs, then diff their outputs byte for byte.
#
# Three scenarios are run:
#   A. the shipped sample data (all category balances 0.00)
#   B. the same data with every category balance set to 10,000.00, so interest actually
#      flows and the account master is really rewritten. Exactly one account is expected
#      to differ here: CBACT04C never updates the last account in the file (its end-of-file
#      branch is unreachable) and the Java port fixes that.
#   C. scenario B again with the Java run in --emulate-final-account-quirk mode, which
#      reproduces the defect on purpose, so every record must match.
#
# Outputs compared: the 350-byte SYSTRAN transaction file and the account master.
# The two DB2-format timestamp fields (offsets 279-330) are masked, because both programs
# stamp them from the system clock at the moment of execution.
#
# Usage: scripts/cobol-parity/run-parity.sh
# Requires: GnuCOBOL 3 with an indexed file handler (Debian/Ubuntu: gnucobol3), JDK 17, Maven.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SRC="$REPO_ROOT/scripts/cobol-parity"
DATA="$REPO_ROOT/app/data/ASCII"
OUT="$REPO_ROOT/target/cobol-parity"
BIN="$OUT/bin"
JAR="$REPO_ROOT/java/cbact04c/target/cbact04c-1.0.0-SNAPSHOT.jar"
PARM_DATE="2022071800"

say() { printf '\n=== %s\n' "$1"; }
die() { printf 'ERROR: %s\n' "$1" >&2; exit 1; }

# ---------------------------------------------------------------------------
# 0. Toolchain
# ---------------------------------------------------------------------------
say "Checking the toolchain"
command -v cobc >/dev/null 2>&1 || die "cobc (GnuCOBOL) not found. Install it: sudo apt-get install -y gnucobol3"
command -v java >/dev/null 2>&1 || die "java not found. See RUN-LOCALLY.md"

cobc --version | head -1
if ! cobc --info 2>/dev/null | grep -q "indexed file handler *: *\(BDB\|VBISAM\|ISAM\|disabled\)"; then
    : # older/newer builds word this differently; the OPEN below is the real test
fi
if cobc --info 2>/dev/null | grep -q "indexed file handler *: *disabled"; then
    die "This GnuCOBOL build has no indexed (ISAM) file handler, so the VSAM KSDS inputs
       cannot be created. On Ubuntu the 'gnucobol4' package is built without it; use
       'gnucobol3' instead: sudo apt-get remove -y gnucobol4 libcob5-dev libcob5 &&
       sudo apt-get install -y gnucobol3"
fi

if [ ! -f "$JAR" ]; then
    say "Building the Java port"
    (cd "$REPO_ROOT/java/cbact04c" && mvn -B -q package -DskipTests)
fi

rm -rf "$OUT"
mkdir -p "$BIN"

# ---------------------------------------------------------------------------
# 1. Compile
# ---------------------------------------------------------------------------
# -fsign=EBCDIC makes GnuCOBOL store the sign of DISPLAY numerics as the mainframe does
# (overpunch in the last byte: '{' = +0 ... 'I' = +9, '}' = -0 ... 'R' = -9). Without it
# GnuCOBOL uses its native ASCII convention and every signed field it writes differs from
# the sample data, which is EBCDIC-derived.
say "Compiling CBACT04C.cbl (unmodified) and the harness with GnuCOBOL"
cobc -m -fsign=EBCDIC -I "$REPO_ROOT/app/cpy" -o "$BIN/CBACT04C.so" "$REPO_ROOT/app/cbl/CBACT04C.cbl"
for prog in LOADVSAM UNLDACCT RUNINTC; do
    cobc -x -fsign=EBCDIC -o "$BIN/$prog" "$SRC/$prog.cbl"
done
echo "Compiled with no errors."

export COB_LIBRARY_PATH="$BIN"

# ---------------------------------------------------------------------------
# 2. One scenario = load VSAM, run COBOL, run Java, diff
# ---------------------------------------------------------------------------
FAILURES=0

run_scenario() {
    local dir="$1" tcatbal="$2" label="$3" java_extra="${4:-}" compare_extra="${5:-}"

    mkdir -p "$dir"
    say "Scenario: $label"

    # -- COBOL side: build the indexed files IDCAMS-style, then run the JCL step
    DD_TCATBALT="$tcatbal" \
    DD_XREFTXT="$DATA/cardxref.txt" \
    DD_ACCTTXT="$DATA/acctdata.txt" \
    DD_DISCTXT="$DATA/discgrp.txt" \
    DD_TCATBALF="$dir/TCATBALF" \
    DD_XREFFILE="$dir/XREFFILE" \
    DD_ACCTFILE="$dir/ACCTFILE" \
    DD_DISCGRP="$dir/DISCGRP" \
    "$BIN/LOADVSAM" > "$dir/load.log"

    DD_TCATBALF="$dir/TCATBALF" \
    DD_XREFFILE="$dir/XREFFILE" \
    DD_ACCTFILE="$dir/ACCTFILE" \
    DD_DISCGRP="$dir/DISCGRP" \
    DD_TRANSACT="$dir/SYSTRAN.cobol" \
    "$BIN/RUNINTC" > "$dir/sysout.cobol" 2>&1
    echo "COBOL rc=$?"

    DD_ACCTFILE="$dir/ACCTFILE" DD_ACCTOUT="$dir/acct.cobol.txt" "$BIN/UNLDACCT" >> "$dir/load.log"

    # -- Java side: same inputs, its own copy of the account master
    cp "$DATA/acctdata.txt" "$dir/acct.java.txt"
    # shellcheck disable=SC2086 # java_extra is an intentional word-split flag list
    java -jar "$JAR" \
        --parm "$PARM_DATE" \
        --tcatbal "$tcatbal" \
        --acct "$dir/acct.java.txt" \
        --xref "$DATA/cardxref.txt" \
        --discgrp "$DATA/discgrp.txt" \
        --out-transact "$dir/SYSTRAN.java" \
        $java_extra > "$dir/sysout.java" 2>&1
    echo "Java  rc=$?"

    # shellcheck disable=SC2086 # compare_extra is an intentional word-split flag list
    python3 "$SRC/compare.py" "$dir" "$DATA/acctdata.txt" $compare_extra || FAILURES=$((FAILURES + 1))
}

# Scenario A: exactly what ships in the repo.
run_scenario "$OUT/a" "$DATA/tcatbal.txt" "shipped sample data (all balances 0.00)"

# Scenario B: every balance 10,000.00, so there is interest to post and accounts change.
# 0000100000{ is 10000.00 as a signed DISPLAY field with a positive overpunch.
awk '{ print substr($0,1,17) "0000100000{" substr($0,29) }' "$DATA/tcatbal.txt" | tr -d '\r' > "$OUT/tcatbal-10k.txt"
run_scenario "$OUT/b" "$OUT/tcatbal-10k.txt" "every category balance set to 10,000.00" \
    "" "--expect-final-account-difference"

# Scenario C: same again, but ask the Java to reproduce the COBOL defect on purpose.
run_scenario "$OUT/c" "$OUT/tcatbal-10k.txt" \
    "10,000.00 balances, Java in bug-for-bug mode" "--emulate-final-account-quirk"

say "Artifacts"
echo "  $OUT/a  shipped data"
echo "  $OUT/b  10,000.00 balances"
echo "  $OUT/c  10,000.00 balances, Java bug-for-bug"
echo "  each holds SYSTRAN.cobol/.java, acct.cobol.txt/.java.txt and both SYSOUT logs"

if [ "$FAILURES" -eq 0 ]; then
    say "PARITY CONFIRMED: every scenario matched byte for byte (timestamps masked)"
else
    say "$FAILURES scenario(s) produced unexpected differences"
    exit 1
fi
