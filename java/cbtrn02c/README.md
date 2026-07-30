# CBTRN02C (POSTTRAN) — Java port

Java port of the CardDemo batch COBOL program [`app/cbl/CBTRN02C.cbl`](../../app/cbl/CBTRN02C.cbl),
the daily transaction posting job (`app/jcl/POSTTRAN.jcl`), with a harness that proves record level
parity against the original program running under GnuCOBOL.

Parity results: [`PARITY_REPORT.md`](PARITY_REPORT.md), with a one page executive summary in
[`docs/parity-dashboard.html`](docs/parity-dashboard.html) and a recording of the demo in
[`docs/cbtrn02c-parity-demo.mp4`](docs/cbtrn02c-parity-demo.mp4).

## Layout

```
src/main/java/com/carddemo/cbtrn02c/
  PostTranBatch.java        the port of the PROCEDURE DIVISION (validation, posting, rejects)
  Cbtrn02c.java             CLI entry point, stands in for the POSTTRAN job step DD statements
  Db2Timestamp.java         Z-GET-DB2-FORMAT-TIMESTAMP, injectable for deterministic runs
  copybook/                 fixed width record layouts (CVTRA06Y, CVTRA05Y, CVACT01Y, CVACT03Y, CVTRA01Y)
  io/FixedWidthFile.java    fixed length record reader/writer
  parity/                   field by field comparison harness and its CLI
cobol-baseline/             GnuCOBOL baseline: load/unload utilities, run script, golden output
```

Monetary and decimal fields are `BigDecimal` throughout, parsed from and written back to COBOL
`USAGE DISPLAY` images including the trailing overpunch sign, and truncated to the capacity of the
`PIC` clause like COBOL arithmetic without `ON SIZE ERROR`.

## Build and test

```bash
mvn verify
```

The parity tests need the COBOL baseline that is committed under `cobol-baseline/baseline/`; to
regenerate it (requires `sudo apt-get install -y gnucobol`):

```bash
cobol-baseline/run_baseline.sh
```

## Run the port

```bash
mvn -DskipTests package
java -jar target/cbtrn02c-1.0.0-SNAPSHOT.jar \
     --dalytran=../../app/data/ASCII/dailytran.txt \
     --xref=../../app/data/ASCII/cardxref.txt \
     --acct=../../app/data/ASCII/acctdata.txt \
     --tcatbal=../../app/data/ASCII/tcatbal.txt \
     --out-dir=target/java-output \
     --current-date=2022071112000000     # optional: pins TRAN-PROC-TS, as COB_CURRENT_DATE does
```

Outputs `transact.dat`, `acctdata.dat`, `tcatbal.dat` and `dalyrejs.dat` into `--out-dir`, and exits
with the COBOL `RETURN-CODE` (4 when transactions were rejected).

## Full parity demo

```bash
./run_parity_demo.sh
```

Compiles and runs the COBOL program, builds and runs the Java port over the same fixtures with the
same pinned timestamp, then `cmp`s the four output files and prints the field level parity report.
