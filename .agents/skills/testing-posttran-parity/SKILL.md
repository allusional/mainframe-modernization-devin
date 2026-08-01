---
name: testing-posttran-parity
description: How to run and visually verify the COBOL-vs-Java parity harnesses in this repo (e.g. scripts/posttran-parity for CBTRN02C/POSTTRAN), including a negative test that proves the generated HTML report really compares output.
---

# Testing COBOL ↔ Java parity harnesses in this repo

## What exists
- `scripts/<program>-parity/run-parity.sh` (today: `scripts/posttran-parity/`) compiles the
  **unmodified** COBOL from `app/cbl/` with GnuCOBOL, loads `app/data/ASCII/*.txt` into KSDS/QSAM via
  a small `PTLOAD.cbl` harness, runs the program, dumps the output files with `PTDUMP.cbl`, then
  builds and runs the Java port and diffs every record.
- Output lands in `build/<program>-parity/`: `report.html` (the visual artifact), `report.json`,
  `cobol/`, `java/`, plus `joblog.txt`/`rc.txt` per side. The script `rm -rf`s that dir on each run,
  so copy anything you want to keep (including your own test report) elsewhere, or regenerate.

## How to run it
```bash
cd <repo root>
./scripts/posttran-parity/run-parity.sh    # ~2s; exits 0 only on full parity
```
Pass criteria: exit 0 and the line `PARITY: COBOL and Java runs agree on all N output records.`
Requirements: `cobc` (GnuCOBOL; `sudo apt-get install -y gnucobol` if missing — the repo blueprint
does not install it), JDK 17, Maven. Signed DISPLAY fields need `cobc -fsign=EBCDIC`; without it the
zone overpunch signs (`{`, `A-I`, `}`, `J-R`) in the sample data are misread.

## Visual verification (there is no web app — the artifact is a static file)
Open `file:///<repo>/build/posttran-parity/report.html` in Chrome. Type the URL with the colon; if a
`file:` URL ends up as a Google search, re-focus the omnibox, `ctrl+a` and retype, pressing Return as
a separate keystroke.

Page order: verdict banner → counter cards → side-by-side job logs → output-file table → posted
transactions → rejects → account balances → TCATBALF → notes on excluded byte ranges. The long
tables and the log panes are **nested scroll regions**: scrolling with the cursor over them scrolls
the inner frame, not the page. Put the cursor in the far-right page margin (e.g. x≈1000) to scroll
the page itself, and over a table to walk its rows.

## Make the test adversarial
A green report alone proves little. Corrupt a copy and re-render, never touching the real output:
```bash
cp -r build/posttran-parity/{java,cobol} /tmp/negtest/
# flip one byte in /tmp/negtest/java/tranfile.txt
python3 scripts/posttran-parity/report.py --data-dir app/data/ASCII \
  --cobol-dir /tmp/negtest/cobol --java-dir /tmp/negtest/java \
  --report /tmp/negtest/report.html --json /tmp/negtest/report.json
```
Expect exit 1, `MISMATCH: ...`, a red MISMATCH badge, an `N differ` pill on the affected file, a diff
block for that record and a `!=` pill in the per-record table. Then reopen the real report.

To corroborate that no mismatch hides inside an unphotographed scroll region:
`grep -c 'pill bad' build/posttran-parity/report.html` should be `0`.

## Known caveats to check, not assume
- The bundled `app/data/ASCII/dailytran.txt` currently yields rejects with reason **0102 OVERLIMIT
  only**. Do not claim other validation branches (0100 account not found, 0103 expired card, …) are
  proven by this run.
- Timestamp/FILLER byte ranges are deliberately excluded from the byte compare (see the report's
  notes section). Verify the exclusions are explained rather than silently masking real drift.
- The verdict banner's explanatory prose is static and still reads "Every record ... matches" even in
  the MISMATCH render; judge the badge and pills, not the sentence.

## Devin Secrets Needed
None — everything runs locally offline (Maven may need the mirror already configured in the blueprint).
