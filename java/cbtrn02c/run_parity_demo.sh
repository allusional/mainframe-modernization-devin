#!/usr/bin/env bash
# End to end parity demo: run the COBOL program under GnuCOBOL, run the Java port over the same
# ASCII fixtures with the same pinned timestamp, then diff the two sets of output records.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
DATA="$REPO/app/data/ASCII"
BASELINE="$HERE/cobol-baseline/baseline"
JAVA_OUT="$HERE/target/java-output"
CURRENT_DATE="${COB_CURRENT_DATE:-2022071112000000}"

echo "############ 1/4  COBOL: compile and run CBTRN02C under GnuCOBOL"
COB_CURRENT_DATE="$CURRENT_DATE" "$HERE/cobol-baseline/run_baseline.sh" | grep -vE "^TCATBAL record not found"

echo
echo "############ 2/4  JAVA: build the port"
(cd "$HERE" && mvn -B -q -DskipTests package)

echo
echo "############ 3/4  JAVA: run the port over the same fixtures"
rm -rf "$JAVA_OUT"
set +e
java -jar "$HERE/target/cbtrn02c-1.0.0-SNAPSHOT.jar" \
     --dalytran="$DATA/dailytran.txt" \
     --xref="$DATA/cardxref.txt" \
     --acct="$DATA/acctdata.txt" \
     --tcatbal="$DATA/tcatbal.txt" \
     --out-dir="$JAVA_OUT" \
     --current-date="$CURRENT_DATE" | grep -vE "^TCATBAL record not found"
java_rc=${PIPESTATUS[0]}
set -e
echo "JAVA RETURN-CODE: $java_rc  (COBOL RETURN-CODE: $(cat "$BASELINE/return-code.txt"))"

echo
echo "############ 4/4  PARITY: compare every output record field by field"
for f in transact.dat acctdata.dat tcatbal.dat dalyrejs.dat; do
  if cmp -s "$BASELINE/$f" "$JAVA_OUT/$f"; then
    printf 'cmp  %-14s identical (%s bytes)\n' "$f" "$(stat -c%s "$JAVA_OUT/$f")"
  else
    printf 'cmp  %-14s DIFFERS\n' "$f"
  fi
done
echo
java -cp "$HERE/target/classes" com.carddemo.cbtrn02c.parity.ParityReport "$BASELINE" "$JAVA_OUT"
