#!/usr/bin/env bash
#
# Shared plumbing for the CBTRN02C differential tests: check the toolchain, compile the real
# COBOL, and run one scenario through both sides. Sourced by run-posting-parity.sh (fixed
# scenarios) and run-posting-fuzz.sh (random feeds). Not meant to be run on its own.

POSTING_SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POSTING_REPO_ROOT="$(cd "$POSTING_SRC/../.." && pwd)"
POSTING_JAR="$POSTING_REPO_ROOT/java/cbtrn02c/target/cbtrn02c-1.0.0-SNAPSHOT.jar"

say() { printf '\n=== %s\n' "$1"; }
die() { printf 'ERROR: %s\n' "$1" >&2; exit 1; }

posting_check_toolchain() {
    command -v cobc >/dev/null 2>&1 || die "cobc (GnuCOBOL) not found: sudo apt-get install -y gnucobol3"
    command -v java >/dev/null 2>&1 || die "java not found. See RUN-LOCALLY.md"
    cobc --version | head -1
    if cobc --info 2>/dev/null | grep -q "indexed file handler *: *disabled"; then
        die "This GnuCOBOL build has no indexed (ISAM) handler, so the VSAM KSDS inputs cannot be
       created. Ubuntu's gnucobol4 package is built without it; use gnucobol3 instead:
       sudo apt-get remove -y gnucobol4 && sudo apt-get install -y gnucobol3"
    fi
    if [ ! -f "$POSTING_JAR" ]; then
        say "Building the Java port"
        (cd "$POSTING_REPO_ROOT/java" && mvn -B -q package -DskipTests)
    fi
}

# -fsign=EBCDIC makes GnuCOBOL store the sign of a DISPLAY numeric as an overpunch in the last
# byte ('{' = +0 ... 'I' = +9, '}' = -0 ... 'R' = -9), which is what the EBCDIC-derived sample
# data uses. Without it every signed field GnuCOBOL writes differs.
posting_compile() {
    local bin="$1"
    mkdir -p "$bin"
    cobc -x -fsign=EBCDIC -I "$POSTING_REPO_ROOT/app/cpy" \
        -o "$bin/CBTRN02C" "$POSTING_REPO_ROOT/app/cbl/CBTRN02C.cbl"
    for prog in LOADPOST UNLDPOST; do
        cobc -x -fsign=EBCDIC -o "$bin/$prog" "$POSTING_SRC/$prog.cbl"
    done
}

# posting_run_scenario <bin> <dir> <dalytran> <cardxref> <acctdata> <tcatbal>
#
# Runs both sides over identical inputs and leaves every output in <dir>. Sets
# POSTING_COBOL_RC and POSTING_JAVA_RC. Says nothing about whether they match: call
# compare-posting.py for that.
posting_run_scenario() {
    local bin="$1" dir="$2" dalytran="$3" cardxref="$4" acctdata="$5" tcatbal="$6"
    mkdir -p "$dir"

    # DALYTRAN is ORGANIZATION SEQUENTIAL (RECFM=F), not line sequential: 350 byte records
    # butted up against each other with no line endings. Build that from the text file.
    python3 -c "
import sys
from pathlib import Path
src = Path(sys.argv[1]).read_text('latin-1').splitlines()
Path(sys.argv[2]).write_text(''.join(line.ljust(350)[:350] for line in src), 'latin-1')
" "$dalytran" "$dir/DALYTRAN"

    # -- COBOL side: build the indexed files IDCAMS-style, run the POSTTRAN step, unload.
    # TRANFILE is deliberately not created here: CBTRN02C opens it OUTPUT (rule R22).
    DD_XREFTXT="$cardxref" DD_ACCTTXT="$acctdata" DD_TCATBALT="$tcatbal" \
    DD_XREFFILE="$dir/XREFFILE" DD_ACCTFILE="$dir/ACCTFILE" DD_TCATBALF="$dir/TCATBALF" \
        "$bin/LOADPOST" > "$dir/load.log"

    set +e
    DD_DALYTRAN="$dir/DALYTRAN" DD_XREFFILE="$dir/XREFFILE" DD_ACCTFILE="$dir/ACCTFILE" \
    DD_TCATBALF="$dir/TCATBALF" DD_TRANFILE="$dir/TRANFILE" DD_DALYREJS="$dir/rejs.cobol" \
        "$bin/CBTRN02C" > "$dir/sysout.cobol" 2>&1
    POSTING_COBOL_RC=$?
    set -e

    DD_ACCTFILE="$dir/ACCTFILE" DD_TCATBALF="$dir/TCATBALF" DD_TRANFILE="$dir/TRANFILE" \
    DD_ACCTOUT="$dir/acct.cobol" DD_TCATBALOUT="$dir/tcat.cobol" DD_TRANOUT="$dir/tran.cobol" \
        "$bin/UNLDPOST" >> "$dir/load.log"

    # -- Java side: identical inputs, its own copies of the two files opened I-O.
    cp "$acctdata" "$dir/acct.java"
    cp "$tcatbal" "$dir/tcat.java"
    set +e
    java -jar "$POSTING_JAR" \
        --dalytran "$dalytran" \
        --xreffile "$cardxref" \
        --acctfile "$dir/acct.java" \
        --tcatbalf "$dir/tcat.java" \
        --tranfile "$dir/tran.java" \
        --dalyrejs "$dir/rejs.java" \
        --bug-for-bug > "$dir/sysout.java" 2>&1
    POSTING_JAVA_RC=$?
    set -e
}
