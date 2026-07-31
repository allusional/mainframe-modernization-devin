#!/usr/bin/env bash
# Convert newline-delimited sample datasets into fixed-length record files
# (no record delimiters), which is the byte layout both CBTRN02C (COBOL,
# ORGANIZATION SEQUENTIAL) and the Java port read/write.
#
# Usage: prep_inputs.sh <scenario-dir> <out-dir>
#   <scenario-dir> must contain dailytran.txt, cardxref.txt, acctdata.txt,
#   tcatbal.txt with one record per line (short lines are space padded).
set -euo pipefail

SRC="$1"
OUT="$2"
mkdir -p "$OUT"

# Strips CR (app/data/ASCII/tcatbal.txt ships with CRLF endings, unlike the
# other datasets), space pads short records and rejects over-long ones.
pad() { # pad <infile> <len> <outfile>
  awk -v len="$2" -v src="$1" '
    { sub(/\r$/, "")
      if (length($0) > len) {
        printf "%s: record %d is %d bytes, expected at most %d\n", src, NR, length($0), len > "/dev/stderr"
        exit 1
      }
      printf "%-*s", len, $0 }' "$1" > "$3"
}

pad "$SRC/dailytran.txt" 350 "$OUT/DALYTRAN.dat"
pad "$SRC/cardxref.txt"   50 "$OUT/CARDXREF.dat"
pad "$SRC/acctdata.txt"  300 "$OUT/ACCTDATA.dat"
pad "$SRC/tcatbal.txt"    50 "$OUT/TCATBAL.dat"

echo "Prepared fixed-length inputs in $OUT:"
ls -l "$OUT"/*.dat
