#!/usr/bin/env bash
# Run the Java port of CBTRN02C on a parity scenario, using the same DD name
# environment variables as the COBOL run.
#
#   run_java.sh <scenario-name>
#
# Outputs: parity/work/java/<scenario>/{TRANSACT,ACCTDATA,TCATBAL,DALYREJS}.dat
#          parity/work/java/<scenario>/{stdout.txt,rc.txt}
set -euo pipefail

SCENARIO="$1"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PARITY="$(dirname "$HERE")"
MODULE="$(dirname "$PARITY")"
REPO="$(cd "$MODULE/../.." && pwd)"

if [ "$SCENARIO" = "full" ]; then
  SRC="$REPO/app/data/ASCII"
else
  SRC="$PARITY/data/$SCENARIO"
fi
OUT="$PARITY/work/java/$SCENARIO"

rm -rf "$OUT"; mkdir -p "$OUT"

JAR="$MODULE/target/carddemo-cbtrn02c-1.0.0-SNAPSHOT.jar"
if [ ! -f "$JAR" ]; then
  (cd "$MODULE" && mvn -q -B -DskipTests package)
fi

"$HERE/prep_inputs.sh" "$SRC" "$OUT" > /dev/null
# ACCTDATA.dat and TCATBAL.dat are updated in place (OPEN I-O); TRANSACT.dat
# and DALYREJS.dat are created by the run (OPEN OUTPUT).

export DD_DALYTRAN="$OUT/DALYTRAN.dat"
export DD_XREFFILE="$OUT/CARDXREF.dat"
export DD_ACCTFILE="$OUT/ACCTDATA.dat"
export DD_TCATBALF="$OUT/TCATBAL.dat"
export DD_TRANFILE="$OUT/TRANSACT.dat"
export DD_DALYREJS="$OUT/DALYREJS.dat"

set +e
java -jar "$JAR" > "$OUT/stdout.txt" 2>&1
RC=$?
set -e
echo "$RC" > "$OUT/rc.txt"
cat "$OUT/stdout.txt"
echo "RETURN-CODE=$RC"
echo "== Java outputs written to $OUT =="
ls -l "$OUT"/*.dat
