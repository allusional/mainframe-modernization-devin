#!/usr/bin/env bash
#
# End to end demo of the Java port of CBACT04C (the INTCALC interest calculation job).
#
#   1. checks the toolchain
#   2. builds the module and runs its tests
#   3. runs the program against the sample data in app/data/ASCII
#   4. runs it again against the same data with a few non zero balances, so there is
#      actual interest to look at
#   5. prints the generated transactions and the before/after account balances
#
# Usage: scripts/run-java-demo.sh
# See RUN-LOCALLY.md for how to install the toolchain from scratch.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODULE="$REPO_ROOT/java/cbact04c"
DATA="$REPO_ROOT/app/data/ASCII"
WORK="$MODULE/target/demo"
PARM_DATE="2022071800"

heading() {
    printf '\n\033[1m== %s\033[0m\n' "$1"
}

fail() {
    printf '\n\033[31m%s\033[0m\n' "$1" >&2
    exit 1
}

# Decodes a COBOL signed display (zoned decimal) field: the last character carries the
# sign as an overpunch, {=+0..I=+9 and }=-0..R=-9. Used only to make the output readable.
write_decoder() {
    cat > "$1" <<'AWK'
function decode(field, scale,   last, digits, sign, pos, neg, index_of, value) {
    pos = "{ABCDEFGHI"; neg = "}JKLMNOPQR"
    last = substr(field, length(field), 1)
    digits = substr(field, 1, length(field) - 1)
    sign = ""
    index_of = index(pos, last)
    if (index_of > 0) {
        digits = digits (index_of - 1)
    } else {
        index_of = index(neg, last)
        if (index_of > 0) {
            digits = digits (index_of - 1); sign = "-"
        } else {
            digits = digits last
        }
    }
    value = substr(digits, 1, length(digits) - scale) "." substr(digits, length(digits) - scale + 1)
    sub(/^0+/, "", value)
    if (substr(value, 1, 1) == ".") {
        value = "0" value
    }
    return sign value
}
AWK
}

heading "1/5 Checking the toolchain"
command -v java >/dev/null 2>&1 || fail "java not found. See RUN-LOCALLY.md for install instructions."
command -v mvn  >/dev/null 2>&1 || fail "mvn not found. See RUN-LOCALLY.md for install instructions."
java -version 2>&1 | sed 's/^/  /'
mvn -version 2>&1 | head -1 | sed 's/^/  /'

java_major="$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
if [ "$java_major" -lt 17 ]; then
    fail "JDK 17 or newer is required, found $java_major. See RUN-LOCALLY.md."
fi

heading "2/5 Building and testing"
(cd "$MODULE" && mvn -B verify)
JAR="$MODULE/target/cbact04c-1.0.0-SNAPSHOT.jar"
[ -f "$JAR" ] || fail "Build did not produce $JAR"

rm -rf "$WORK"
mkdir -p "$WORK"

heading "3/5 Running against the untouched sample data (app/data/ASCII)"
cp "$DATA/acctdata.txt" "$WORK/acctdata-sample.txt"
java -jar "$JAR" \
    --parm "$PARM_DATE" \
    --tcatbal "$DATA/tcatbal.txt" \
    --acct "$WORK/acctdata-sample.txt" \
    --xref "$DATA/cardxref.txt" \
    --discgrp "$DATA/discgrp.txt" \
    --out-transact "$WORK/systran-sample.txt"
echo "  Every balance in the shipped tcatbal.txt is zero, so the amounts below are 0.00."

heading "4/5 Running again with non zero balances"
# Give the first three accounts a 10,000.00 balance in transaction category 01/0001.
awk 'NR <= 3 { print substr($0, 1, 17) "0000100000{" substr($0, 29) }' \
    "$DATA/tcatbal.txt" | tr -d '\r' > "$WORK/tcatbal-demo.txt"
cp "$DATA/acctdata.txt" "$WORK/acctdata-demo.txt"
java -jar "$JAR" \
    --parm "$PARM_DATE" \
    --tcatbal "$WORK/tcatbal-demo.txt" \
    --acct "$WORK/acctdata-demo.txt" \
    --xref "$DATA/cardxref.txt" \
    --discgrp "$DATA/discgrp.txt" \
    --out-transact "$WORK/systran-demo.txt"

heading "5/5 Results"

AWK_DIR="$WORK/.awk"
mkdir -p "$AWK_DIR"

write_decoder "$AWK_DIR/transactions.awk"
cat >> "$AWK_DIR/transactions.awk" <<'AWK'
{
    printf "  %-16s %-4s %-6s %-12s %-16s %s\n",
        substr($0, 1, 16), substr($0, 17, 2), substr($0, 19, 4),
        decode(substr($0, 133, 11), 2), substr($0, 263, 16), substr($0, 33, 24)
}
AWK

write_decoder "$AWK_DIR/accounts.awk"
cat >> "$AWK_DIR/accounts.awk" <<'AWK'
NR == FNR { before[substr($0, 1, 11)] = decode(substr($0, 13, 12), 2); next }
{
    id = substr($0, 1, 11)
    after = decode(substr($0, 13, 12), 2)
    if (before[id] != after) {
        printf "  %-12s %-14s %-14s %.2f\n", id, before[id], after, (after + 0) - (before[id] + 0)
    }
}
AWK

echo "Interest transactions generated (copybook CVTRA05Y, 350 bytes each):"
printf '  %-16s %-4s %-6s %-12s %-16s %s\n' "TRAN ID" "TYPE" "CAT" "AMOUNT" "CARD" "DESCRIPTION"
awk -f "$AWK_DIR/transactions.awk" "$WORK/systran-demo.txt"

echo
echo "Account master, before and after (copybook CVACT01Y):"
printf '  %-12s %-14s %-14s %s\n' "ACCOUNT" "BALANCE BEFORE" "BALANCE AFTER" "INTEREST POSTED"
awk -f "$AWK_DIR/accounts.awk" "$DATA/acctdata.txt" "$WORK/acctdata-demo.txt"

echo
echo "Rate applied: the sample account master has a blank ACCT-GROUP-ID, so every rate"
echo "lookup misses and the DEFAULT disclosure group (15.00% for 01/0001) is used:"
echo "  10000.00 * 15.00 / 1200 = 125.00 per category per month."

echo
echo "Files written to $WORK:"
find "$WORK" -maxdepth 1 -type f -exec basename {} \; | sort | sed 's/^/  /'
