# CBACT04C (INTCALC) parity harness

Runs the **unmodified** `app/cbl/CBACT04C.cbl` under GnuCOBOL and the Java port in
`java/carddemo-intcalc` over byte-identical input datasets, compares every output record, every
counter and every job log line, and renders a visual report.

```
./scripts/intcalc-parity/run-parity.sh
# -> build/intcalc-parity/report.html  (open it)
# -> build/intcalc-parity/report.json
# exits non-zero on any disagreement, so it works as a CI gate
```

Requirements: `gnucobol` (`sudo apt-get install -y gnucobol`), a JDK 17+ and Maven.

## How the COBOL side runs without z/OS

| z/OS | Here |
| --- | --- |
| VSAM KSDS (`TCATBALF`, `XREFFILE`, `DISCGRP`, `ACCTFILE`) | GnuCOBOL `ORGANIZATION INDEXED`, loaded by `cobol/PTLOAD.cbl` from the flat datasets |
| QSAM `TRANSACT` | record `SEQUENTIAL`, dumped back to text by `cobol/PTDUMP.cbl` |
| DD statements of `app/jcl/INTCALC.jcl` | `DD_<ddname>` environment variables, so the COBOL source stays untouched |
| `PARM='2022071800'` on the EXEC card | first argument of `cobol/PTRUN.cbl` |
| Signed `DISPLAY` fields | `cobc -fsign=EBCDIC`; without it the zone overpunch signs are misread |

`CBACT04C` has a `PROCEDURE DIVISION USING` clause (the JCL PARM), which GnuCOBOL refuses to build
as a main program (`executable program requested but PROCEDURE/ENTRY has USING clause`). It is
therefore compiled unmodified as a module (`cobc -m`) and called by `cobol/PTRUN.cbl`, a job step
driver that passes the same halfword-length + `X(10)` date parameter the JCL passes.

## Scenarios

The bundled `tcatbal.txt` holds a zero `TRAN-CAT-BAL` in all 50 records and every account has a
blank `ACCT-GROUP-ID`, so a run over the sample data as it stands computes 0.00 interest for every
record, updates no balance and only ever takes the `DEFAULT` disclosure group path. Two further
scenarios therefore derive their own input datasets with `seed-datasets.py`; both sides are fed the
same derived files, and the datasets in `app/data/` are never modified.

| Scenario | Inputs | What it exercises |
| --- | --- | --- |
| `sample` | `app/data/ASCII` as it is (CR stripped) | the whole flow with a zero rate result, the `DEFAULT` group fallback, 50 records read, 50 transactions posted |
| `seeded` | derived `tcatbal.txt` (positive, negative, truncating and near-limit balances, 1-3 categories per account) and `acctdata.txt` (group ids `A000000000`, `ZEROAPR`, blank) | the interest arithmetic and truncation, interest totalled over several categories, the account balance update and cycle-total reset, the zero-rate skip, the direct disclosure group hit |
| `abend` | derived `tcatbal.txt` with a category balance for an account that has no `ACCTFILE` record | `1100-GET-ACCT-DATA` `INVALID KEY`, `9910-DISPLAY-IO-STATUS`, `9999-ABEND-PROGRAM` |

## What is compared

Per scenario: `tranfile.txt` (350 byte CVTRA05Y records, in write order) and `acctfile.txt`
(300 byte CVACT01Y records, dumped in KSDS key sequence) record by record, the two job logs line by
line, and five counters derived independently from each side's own output (records read,
transactions posted, accounts updated, total interest posted, return code).

### Excluded byte ranges, and why

| File | Bytes (1-based) | Why |
| --- | --- | --- |
| `tranfile.txt` | 279-304 (`TRAN-ORIG-TS`) | wall clock, from `FUNCTION CURRENT-DATE` at the moment the transaction is written |
| `tranfile.txt` | 305-330 (`TRAN-PROC-TS`) | wall clock, the same timestamp as `TRAN-ORIG-TS` |

Nothing else is masked — every other byte of every output record, including the `FILLER` areas, has
to match. Two further exclusions apply only to the `abend` scenario:

- job log lines after `ABENDING PROGRAM`: GnuCOBOL has no z/OS Language Environment, so the
  `CALL 'CEE3ABD'` of `9999-ABEND-PROGRAM` cannot resolve and the runtime appends its own
  diagnostics after the last `DISPLAY` of the program;
- the exit code of an abending run (1 from the GnuCOBOL runtime, 999 mod 256 from the JVM). That
  both sides abend, after the same `DISPLAY` lines, is compared.

## What is not exercised

- **The `WS-TRANID-SUFFIX` wrap at 1,000,000** transactions: it needs a million `TCATBALF` records.
- **`1400-COMPUTE-FEES`**, which is an empty "To be implemented" paragraph in the COBOL.
- **File status values other than 00 and 23.** The open failures (`0000-TCATBALF-OPEN` and friends),
  the `9000-*-CLOSE` failures and the `IO-STATUS-04` branch that formats a `9x` status as a byte
  value cannot be produced through the harness, because GnuCOBOL creates or opens the stand-in files
  successfully. The abend path they all share *is* exercised, through the account `INVALID KEY`.
- **A `TCATBALF` file that is not in account-id order**, and duplicate `XREF-ACCT-ID` alternate keys
  (the sample cross-reference has none); both are ruled out by the KSDS definitions.
- **A monthly interest larger than `PIC S9(09)V99`**: the largest rate in `discgrp.txt` is 2.50%, so
  even the near-limit balance of the `seeded` scenario stays inside the field. The high-order
  truncation of such a `MOVE` is covered by a unit test instead.
- **`ACCT-CURR-BAL` overflowing** `PIC S9(10)V99`, for the same reason.

## Negative test

The report has to be able to go red. Corrupt one byte of a copy of a Java output file and re-run the
comparison only:

```
cp -r build/intcalc-parity /tmp/negtest
python3 - <<'EOF'
from pathlib import Path
p = Path('/tmp/negtest/seeded/java/tranfile.txt')
lines = p.read_text().splitlines()
lines[0] = lines[0][:140] + '9' + lines[0][141:]
p.write_text("\n".join(lines) + "\n")
EOF
python3 scripts/intcalc-parity/report.py \
    --scenario seeded:/tmp/negtest/seeded/input:/tmp/negtest/seeded/cobol:/tmp/negtest/seeded/java \
    --report /tmp/negtest/report.html --json /tmp/negtest/report.json
# -> MISMATCH, exit 1, red banner
```
