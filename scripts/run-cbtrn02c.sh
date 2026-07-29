#!/usr/bin/env bash
#
# One command, clean machine to finished report.
#
#   ./scripts/run-cbtrn02c.sh
#
# It installs what is missing, builds the Java port, runs its unit tests, runs the real COBOL
# and diffs the two byte for byte, and writes a static HTML page you can open from file://.
#
# Prerequisites installed automatically on Debian/Ubuntu (with sudo): JDK 17, Maven,
# GnuCOBOL 3. On macOS: brew install openjdk@17 maven gnu-cobol. Nothing here touches app/.
#
# Pass --skip-install if you have the toolchain already and do not want apt to run.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

REPORT="$REPO_ROOT/target/cbtrn02c-report/index.html"
SKIP_INSTALL=false
[ "${1:-}" = "--skip-install" ] && SKIP_INSTALL=true

step() { printf '\n\033[1m==> %s\033[0m\n' "$1"; }

step "1/5  Toolchain"
missing=()
command -v java  >/dev/null 2>&1 || missing+=("openjdk-17-jdk")
command -v mvn   >/dev/null 2>&1 || missing+=("maven")
command -v cobc  >/dev/null 2>&1 || missing+=("gnucobol3")
command -v python3 >/dev/null 2>&1 || missing+=("python3")

if [ ${#missing[@]} -gt 0 ]; then
    if [ "$SKIP_INSTALL" = true ]; then
        echo "Missing: ${missing[*]}. Install them, or drop --skip-install." >&2
        exit 1
    fi
    if ! command -v apt-get >/dev/null 2>&1; then
        echo "Missing: ${missing[*]}." >&2
        echo "On macOS: brew install openjdk@17 maven gnu-cobol" >&2
        exit 1
    fi
    echo "Installing: ${missing[*]}"
    sudo apt-get update -qq
    sudo apt-get install -y "${missing[@]}"
fi

# gnucobol4 is packaged without an indexed-file handler and cannot open the VSAM KSDS files.
if cobc --info 2>/dev/null | grep -q "indexed file handler *: *disabled"; then
    echo "This GnuCOBOL has no indexed-file handler. Ubuntu's gnucobol4 package is built" >&2
    echo "without one; use gnucobol3:" >&2
    echo "  sudo apt-get remove -y gnucobol4 && sudo apt-get install -y gnucobol3" >&2
    exit 1
fi
java -version 2>&1 | head -1
mvn -v | head -1
cobc --version | head -1

step "2/5  Building the Java port"
(cd java && mvn -B -q package -DskipTests)

step "3/5  Unit tests"
(cd java && mvn -B test)

step "4/5  Differential test against the real COBOL"
./scripts/cobol-parity/run-posting-parity.sh

step "5/5  Static report"
python3 scripts/report/build-posting-report.py \
    "$REPO_ROOT/target/cbtrn02c-parity/shipped" "$REPORT"

step "Done"
cat <<EOF
The Java port, the unit tests and a byte-for-byte comparison against the unmodified COBOL all
ran. Open the report - it is a single file, no server needed:

  file://$REPORT

Raw run artifacts, COBOL side and Java side of every output file:

  target/cbtrn02c-parity/shipped       the 300 records in app/data/ASCII
  target/cbtrn02c-parity/adversarial   generated edge cases
EOF

if command -v xdg-open >/dev/null 2>&1; then xdg-open "file://$REPORT" >/dev/null 2>&1 || true
elif command -v open >/dev/null 2>&1; then open "file://$REPORT" >/dev/null 2>&1 || true
fi
