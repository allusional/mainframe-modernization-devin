#!/usr/bin/env bash
#
# Runs the unmodified COBOL CBACT04C (GnuCOBOL) and its Java port over the same input
# datasets, compares every file and every job log line both produce record by record and
# renders a visual report at build/intcalc-parity/report.html.
#
# Three scenarios are run, each feeding both sides byte-identical inputs:
#   sample  the sample datasets of app/data/ASCII exactly as they are in the repository
#   seeded  the same datasets with non-zero TRAN-CAT-BAL amounts and account group ids, so the
#           interest arithmetic, the account balance update and the zero-rate skip are exercised
#   abend   a category balance for an account that has no ACCTFILE record, so both sides take
#           the INVALID KEY / 9999-ABEND-PROGRAM path
#
# Requirements: gnucobol (cobc), a JDK 17+ and Maven.
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
HARNESS="$REPO_ROOT/scripts/intcalc-parity"
DATA_DIR="$REPO_ROOT/app/data/ASCII"
OUT_ROOT="${INTCALC_PARITY_OUT:-$REPO_ROOT/build/intcalc-parity}"
BUILD_DIR="$OUT_ROOT/bin"
# PARM of STEP15 in app/jcl/INTCALC.jcl; the first ten bytes of every TRAN-ID.
PARM_DATE="${INTCALC_PARM_DATE:-2022071800}"
SCENARIOS=(sample seeded abend)

rm -rf "$OUT_ROOT"
mkdir -p "$BUILD_DIR"

echo "==> Compiling COBOL (unmodified app/cbl/CBACT04C.cbl) and the harness programs"
# -fsign=EBCDIC gives the trailing zone overpunch sign ({, A-I, }, J-R) that the sample datasets
# and z/OS COBOL use for signed PIC S9(n)V99 DISPLAY fields.
COBC_FLAGS=(-fsign=EBCDIC)
# CBACT04C has a PROCEDURE DIVISION USING clause (the JCL PARM), which GnuCOBOL will not build as a
# main program, so it is compiled as a module and called by the PTRUN job step driver.
cobc -m "${COBC_FLAGS[@]}" -I "$REPO_ROOT/app/cpy" -o "$BUILD_DIR/CBACT04C.so" "$REPO_ROOT/app/cbl/CBACT04C.cbl"
cobc -x "${COBC_FLAGS[@]}" -o "$BUILD_DIR/PTRUN" "$HARNESS/cobol/PTRUN.cbl"
cobc -x "${COBC_FLAGS[@]}" -o "$BUILD_DIR/PTLOAD" "$HARNESS/cobol/PTLOAD.cbl"
cobc -x "${COBC_FLAGS[@]}" -o "$BUILD_DIR/PTDUMP" "$HARNESS/cobol/PTDUMP.cbl"
export COB_LIBRARY_PATH="$BUILD_DIR"

echo "==> Building the Java port"
(cd "$REPO_ROOT/java/carddemo-intcalc" && mvn -q -B package -DskipTests)

REPORT_ARGS=()
for scenario in "${SCENARIOS[@]}"; do
    SCEN_DIR="$OUT_ROOT/$scenario"
    INPUT_DIR="$SCEN_DIR/input"
    WORK_DIR="$SCEN_DIR/work"
    COBOL_OUT="$SCEN_DIR/cobol"
    JAVA_OUT="$SCEN_DIR/java"
    mkdir -p "$INPUT_DIR" "$WORK_DIR" "$COBOL_OUT" "$JAVA_OUT"

    echo "==> [$scenario] Preparing the input datasets"
    python3 "$HARNESS/seed-datasets.py" "$DATA_DIR" "$INPUT_DIR" "$scenario"

    echo "==> [$scenario] Loading the input datasets into KSDS files"
    export DD_TCATIN="$INPUT_DIR/tcatbal.txt"
    export DD_XREFIN="$INPUT_DIR/cardxref.txt"
    export DD_DISCIN="$INPUT_DIR/discgrp.txt"
    export DD_ACCTIN="$INPUT_DIR/acctdata.txt"
    export DD_TCATBALF="$WORK_DIR/TCATBALF"
    export DD_XREFFILE="$WORK_DIR/XREFFILE"
    export DD_DISCGRP="$WORK_DIR/DISCGRP"
    export DD_ACCTFILE="$WORK_DIR/ACCTFILE"
    export DD_TRANSACT="$WORK_DIR/TRANSACT"
    "$BUILD_DIR/PTLOAD" > "$SCEN_DIR/ptload.log"

    echo "==> [$scenario] Running COBOL CBACT04C"
    set +e
    "$BUILD_DIR/PTRUN" "$PARM_DATE" > "$COBOL_OUT/joblog.txt" 2>&1
    echo $? > "$COBOL_OUT/rc.txt"
    set -e
    echo "    RC=$(cat "$COBOL_OUT/rc.txt")"

    echo "==> [$scenario] Dumping the files CBACT04C wrote"
    export DD_TRANOUT="$COBOL_OUT/tranfile.txt"
    export DD_ACCTOUT="$COBOL_OUT/acctfile.txt"
    "$BUILD_DIR/PTDUMP" > "$SCEN_DIR/ptdump.log"

    echo "==> [$scenario] Running the Java port"
    set +e
    java -cp "$REPO_ROOT/java/carddemo-intcalc/target/classes" \
        com.carddemo.intcalc.files.IntCalcBatchRunner "$INPUT_DIR" "$JAVA_OUT" "$PARM_DATE" \
        > "$JAVA_OUT/joblog.txt" 2>&1
    echo $? > "$JAVA_OUT/rc.txt"
    set -e
    echo "    RC=$(cat "$JAVA_OUT/rc.txt")"

    REPORT_ARGS+=(--scenario "$scenario:$INPUT_DIR:$COBOL_OUT:$JAVA_OUT")
done

echo "==> Comparing the runs and rendering the report"
python3 "$HARNESS/report.py" \
    "${REPORT_ARGS[@]}" \
    --parm-date "$PARM_DATE" \
    --report "$OUT_ROOT/report.html" \
    --json "$OUT_ROOT/report.json"
