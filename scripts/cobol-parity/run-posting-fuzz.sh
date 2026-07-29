#!/usr/bin/env bash
#
# Randomised differential test for CBTRN02C.
#
# run-posting-parity.sh compares the COBOL and the Java on two fixed feeds: the 300 shipped
# records, and an adversarial feed built by hand to hit every reject reason and boundary.
# Both are inputs somebody chose, so they can only confirm behaviours somebody thought of.
#
# This generates feeds from the record grammar instead - random amounts, signs, dates, cards,
# categories, and random account limits, balances, cycle totals and expiry dates - and diffs
# every output file byte for byte on each one. Each feed comes from a seed, so a failing feed
# is reproducible exactly:
#
#   scripts/cobol-parity/run-posting-fuzz.sh                     # seeds 1..25, 400 tran each
#   scripts/cobol-parity/run-posting-fuzz.sh --seeds 200         # longer soak
#   scripts/cobol-parity/run-posting-fuzz.sh --from 137 --seeds 1  # just seed 137, kept on disk
#
# On a mismatch it stops, leaves that seed's inputs and both sides' outputs on disk, and prints
# the command to reproduce it.
#
# What this does NOT prove: GnuCOBOL is not IBM Enterprise COBOL, and a seed that passes only
# says the two agree on the records that seed happened to generate. See COBOL-PARITY.md.
#
# Requires: GnuCOBOL 3 with an indexed file handler (Debian/Ubuntu: gnucobol3), JDK 17, Maven.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SRC="$REPO_ROOT/scripts/cobol-parity"
DATA="$REPO_ROOT/app/data/ASCII"
OUT="$REPO_ROOT/target/cbtrn02c-fuzz"
BIN="$OUT/bin"

# shellcheck source=scripts/cobol-parity/posting-scenario.sh
source "$SRC/posting-scenario.sh"

SEEDS=25
FROM=1
TRANSACTIONS=400
ACCOUNTS=20
KEEP=false
while [ $# -gt 0 ]; do
    case "$1" in
        --seeds) SEEDS="$2"; shift 2 ;;
        --from) FROM="$2"; shift 2 ;;
        --transactions) TRANSACTIONS="$2"; shift 2 ;;
        --accounts) ACCOUNTS="$2"; shift 2 ;;
        --keep) KEEP=true; shift ;;
        *) die "unknown option: $1" ;;
    esac
done

say "Checking the toolchain"
posting_check_toolchain

rm -rf "$OUT"
say "Compiling app/cbl/CBTRN02C.cbl (unmodified) and the harness with GnuCOBOL"
posting_compile "$BIN"
echo "Compiled with no errors."

say "$SEEDS random feeds of $TRANSACTIONS transactions over $ACCOUNTS accounts"
printf '%s\n' "  each feed: COBOL and Java over identical inputs, all four outputs diffed byte for byte"
echo

TOTAL_TRANSACTIONS=0
TOTAL_POSTED=0
TOTAL_REJECTED=0
REASONS="$OUT/reasons.txt"
: > "$REASONS"

last=$((FROM + SEEDS - 1))
for seed in $(seq "$FROM" "$last"); do
    dir="$OUT/seed-$seed"
    python3 "$SRC/make-posting-fuzz.py" "$DATA" "$dir/in" \
        --seed "$seed" --transactions "$TRANSACTIONS" --accounts "$ACCOUNTS" > "$dir-gen.log"

    posting_run_scenario "$BIN" "$dir" \
        "$dir/in/dailytran.txt" "$dir/in/cardxref.txt" "$dir/in/acctdata.txt" "$DATA/tcatbal.txt"

    processed=$(grep -oP 'TRANSACTIONS PROCESSED :\K[0-9]+' "$dir/sysout.cobol" | tail -1)
    rejected=$(grep -oP 'TRANSACTIONS REJECTED  :\K[0-9]+' "$dir/sysout.cobol" | tail -1)
    posted=$((10#${processed:-0} - 10#${rejected:-0}))

    failed=false
    if [ "$POSTING_COBOL_RC" != "$POSTING_JAVA_RC" ]; then
        failed=true
        printf '  seed %-5s RETURN CODE MISMATCH: COBOL rc=%s Java rc=%s\n' \
            "$seed" "$POSTING_COBOL_RC" "$POSTING_JAVA_RC"
    fi
    if ! python3 "$SRC/compare-posting.py" "$dir" > "$dir/compare.log" 2>&1; then
        failed=true
        printf '  seed %-5s FILES DIFFER\n' "$seed"
        sed 's/^/    /' "$dir/compare.log"
    fi

    if [ "$failed" = true ]; then
        say "MISMATCH on seed $seed. Inputs and both sides' outputs are in:"
        echo "  $dir"
        echo
        echo "Reproduce just this feed with:"
        echo "  scripts/cobol-parity/run-posting-fuzz.sh --from $seed --seeds 1 \\"
        echo "      --transactions $TRANSACTIONS --accounts $ACCOUNTS"
        exit 1
    fi

    # DALYREJS is RECFM=F,LRECL=430 with no line endings: a 350 byte copy of the input record
    # then an 80 byte trailer whose first 4 bytes are the reason code.
    python3 -c "
import sys
from pathlib import Path
raw = Path(sys.argv[1]).read_bytes()
for start in range(0, len(raw), 430):
    print(raw[start + 350:start + 354].decode('latin-1'))
" "$dir/rejs.cobol" >> "$REASONS"
    TOTAL_TRANSACTIONS=$((TOTAL_TRANSACTIONS + 10#${processed:-0}))
    TOTAL_REJECTED=$((TOTAL_REJECTED + 10#${rejected:-0}))
    TOTAL_POSTED=$((TOTAL_POSTED + posted))
    printf '  seed %-5s %5s processed  %5s posted  %5s rejected  rc=%s  identical\n' \
        "$seed" "$((10#$processed))" "$posted" "$((10#$rejected))" "$POSTING_COBOL_RC"

    # Each seed's artifacts are ~200KB; a long soak would fill the disk, so passing seeds are
    # discarded unless asked for. Failing ones are always kept - see the exit above.
    if [ "$KEEP" = false ]; then
        rm -rf "$dir" "$dir-gen.log"
    fi
done

say "Totals across $SEEDS feeds"
printf '  %s transactions: %s posted, %s rejected\n' \
    "$TOTAL_TRANSACTIONS" "$TOTAL_POSTED" "$TOTAL_REJECTED"
echo "  reject reasons: $(sort "$REASONS" | uniq -c | tr '\n' ' ')"

say "NO DIVERGENCE: the COBOL and the Java agreed byte for byte on every one of $SEEDS random feeds"
echo "  This is evidence, not proof. It says the two agree on the records these seeds generated;"
echo "  it says nothing about IBM Enterprise COBOL, nor about the paths no feed can reach"
echo "  (a failed account rewrite, a duplicate transaction ID). See COBOL-PARITY.md."
