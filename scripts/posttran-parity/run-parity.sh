#!/usr/bin/env bash
#
# Runs the unmodified COBOL CBTRN02C (GnuCOBOL) and its Java port over the same
# sample datasets from app/data/ASCII, compares every file both produce record by
# record and renders a visual report at build/posttran-parity/report.html.
#
# Requirements: gnucobol (cobc), a JDK 17+ and Maven.
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DATA_DIR="$REPO_ROOT/app/data/ASCII"
OUT_ROOT="${POSTTRAN_PARITY_OUT:-$REPO_ROOT/build/posttran-parity}"
BUILD_DIR="$OUT_ROOT/bin"
WORK_DIR="$OUT_ROOT/work"
COBOL_OUT="$OUT_ROOT/cobol"
JAVA_OUT="$OUT_ROOT/java"

rm -rf "$OUT_ROOT"
mkdir -p "$BUILD_DIR" "$WORK_DIR" "$COBOL_OUT" "$JAVA_OUT"

echo "==> Compiling COBOL (unmodified app/cbl/CBTRN02C.cbl) and the load/dump harness"
# -fsign=EBCDIC gives the trailing zone overpunch sign ({, A-I, }, J-R) that the
# sample datasets and z/OS COBOL use for signed PIC S9(n)V99 DISPLAY fields.
COBC_FLAGS=(-x -fsign=EBCDIC)
cobc "${COBC_FLAGS[@]}" -I "$REPO_ROOT/app/cpy" -o "$BUILD_DIR/CBTRN02C" "$REPO_ROOT/app/cbl/CBTRN02C.cbl"
cobc "${COBC_FLAGS[@]}" -o "$BUILD_DIR/PTLOAD" "$REPO_ROOT/scripts/posttran-parity/cobol/PTLOAD.cbl"
cobc "${COBC_FLAGS[@]}" -o "$BUILD_DIR/PTDUMP" "$REPO_ROOT/scripts/posttran-parity/cobol/PTDUMP.cbl"

echo "==> Loading the sample datasets into QSAM/KSDS files"
for name in cardxref acctdata tcatbal dailytran; do
    tr -d '\r' < "$DATA_DIR/$name.txt" > "$WORK_DIR/$name.in"
done

export DD_XREFIN="$WORK_DIR/cardxref.in"
export DD_ACCTIN="$WORK_DIR/acctdata.in"
export DD_TCATIN="$WORK_DIR/tcatbal.in"
export DD_DTRANIN="$WORK_DIR/dailytran.in"
export DD_XREFFILE="$WORK_DIR/XREFFILE"
export DD_ACCTFILE="$WORK_DIR/ACCTFILE"
export DD_TCATBALF="$WORK_DIR/TCATBALF"
export DD_DALYTRAN="$WORK_DIR/DALYTRAN"
export DD_TRANFILE="$WORK_DIR/TRANFILE"
export DD_DALYREJS="$WORK_DIR/DALYREJS"
"$BUILD_DIR/PTLOAD" > "$OUT_ROOT/ptload.log"

echo "==> Running COBOL CBTRN02C"
set +e
"$BUILD_DIR/CBTRN02C" > "$COBOL_OUT/joblog.txt" 2>&1
echo $? > "$COBOL_OUT/rc.txt"
set -e
echo "    RC=$(cat "$COBOL_OUT/rc.txt")"

echo "==> Dumping the files CBTRN02C wrote"
export DD_TRANOUT="$COBOL_OUT/tranfile.txt"
export DD_ACCTOUT="$COBOL_OUT/acctfile.txt"
export DD_TCATOUT="$COBOL_OUT/tcatbal.txt"
export DD_REJSOUT="$COBOL_OUT/rejects.txt"
"$BUILD_DIR/PTDUMP" > "$OUT_ROOT/ptdump.log"

echo "==> Building and running the Java port"
(cd "$REPO_ROOT/java/carddemo-posttran" && mvn -q -B package)
set +e
java -cp "$REPO_ROOT/java/carddemo-posttran/target/classes" \
    com.carddemo.posttran.files.PostTranBatchRunner "$DATA_DIR" "$JAVA_OUT" \
    > "$JAVA_OUT/joblog.txt" 2>&1
echo $? > "$JAVA_OUT/rc.txt"
set -e
echo "    RC=$(cat "$JAVA_OUT/rc.txt")"

echo "==> Comparing the two runs and rendering the report"
python3 "$REPO_ROOT/scripts/posttran-parity/report.py" \
    --data-dir "$DATA_DIR" \
    --cobol-dir "$COBOL_OUT" \
    --java-dir "$JAVA_OUT" \
    --report "$OUT_ROOT/report.html" \
    --json "$OUT_ROOT/report.json"
