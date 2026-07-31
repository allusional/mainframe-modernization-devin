#!/usr/bin/env bash
# End to end parity run for one scenario: COBOL baseline, Java port, diff.
#
#   run_parity.sh [scenario ...]      (default: branches full)
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PARITY="$(dirname "$HERE")"
MODULE="$(dirname "$PARITY")"
JAR="$MODULE/target/carddemo-cbtrn02c-1.0.0-SNAPSHOT.jar"

SCENARIOS=("$@")
if [ ${#SCENARIOS[@]} -eq 0 ]; then
  SCENARIOS=(branches full)
fi

(cd "$MODULE" && mvn -q -B -DskipTests package)

STATUS=0
for scenario in "${SCENARIOS[@]}"; do
  echo
  echo "########## scenario: $scenario ##########"
  "$HERE/run_cobol.sh" "$scenario" | grep -E 'TRANSACTIONS|RETURN-CODE|DUMPED|=='
  "$HERE/run_java.sh"  "$scenario" | grep -E 'TRANSACTIONS|RETURN-CODE|=='

  echo "---------- counters and return code ----------"
  diff <(grep -E 'TRANSACTIONS (PROCESSED|REJECTED)' "$PARITY/golden/$scenario/stdout.txt") \
       <(grep -E 'TRANSACTIONS (PROCESSED|REJECTED)' "$PARITY/work/java/$scenario/stdout.txt") \
    && echo "counters match: $(grep -E 'TRANSACTIONS' "$PARITY/golden/$scenario/stdout.txt" | tr '\n' ' ')"
  diff "$PARITY/golden/$scenario/rc.txt" "$PARITY/work/java/$scenario/rc.txt" \
    && echo "return code matches: $(cat "$PARITY/golden/$scenario/rc.txt")"

  echo "---------- output record comparison ----------"
  java -cp "$JAR" com.carddemo.cbtrn02c.parity.ParityCompare \
      "$PARITY/golden/$scenario" "$PARITY/work/java/$scenario" || STATUS=1
done

exit $STATUS
