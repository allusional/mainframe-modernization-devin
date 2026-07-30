# CBTRN02C parity harness — COBOL vs the Java port

Runs the **unmodified** `app/cbl/CBTRN02C.cbl` under GnuCOBOL and the Java port in
`java/carddemo-posttran` over the same sample datasets from `app/data/ASCII`, compares every
record of every file both write, and renders a visual report.

```
sudo apt-get install -y gnucobol      # cobc, one time
./scripts/posttran-parity/run-parity.sh
xdg-open build/posttran-parity/report.html
```

The script exits non-zero if the two runs disagree, so it works as a CI gate.

## What it does

| Step | |
| --- | --- |
| `cobc -x -fsign=EBCDIC` | compiles `CBTRN02C.cbl` as it is in the repo, plus the two harness programs below |
| `PTLOAD.cbl` | loads `cardxref/acctdata/tcatbal` into INDEXED files (stand-ins for the VSAM KSDS) and `dailytran` into a record SEQUENTIAL file (QSAM) |
| `CBTRN02C` | posts the 300 daily transactions, updating ACCTFILE and TCATBALF and writing TRANFILE and DALYREJS |
| `PTDUMP.cbl` | dumps TRANFILE, ACCTFILE, TCATBALF and DALYREJS back to flat text |
| `PostTranBatchRunner` | runs the Java port on the same input files and writes the same four files as text |
| `report.py` | compares the record images and renders `build/posttran-parity/report.html` + `report.json` |

`-fsign=EBCDIC` makes GnuCOBOL use the trailing zone overpunch sign (`{`, `A`-`I`, `}`, `J`-`R`)
for signed `PIC S9(n)V99 DISPLAY` fields, which is what the sample datasets contain and what
z/OS COBOL produces; without it the amounts in the files would not round-trip.

## Byte ranges excluded from the comparison

* **TRANFILE 305-330 — `TRAN-PROC-TS`**: the wall clock of the run.
* **TCATBALF 29-50 — `CVTRA01Y FILLER`**: `2700-A-CREATE-TCATBAL-REC` does
  `INITIALIZE TRAN-CAT-BAL-RECORD`, which leaves `FILLER` holding whatever the previous `READ`
  left in the record area; the Java port writes spaces there. Records the program *updates*
  keep their original FILLER on both sides and are compared in full.

Everything else — 262 posted transactions, 50 account records, 100 category balances,
38 rejects with their reason codes, the job log and the return code — is compared byte for byte.
