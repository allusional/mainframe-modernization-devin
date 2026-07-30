# CBTRN02C (POSTTRAN) COBOL to Java parity report

**Verdict: PASS.** Every output record produced by the Java port is byte for byte identical to the
records produced by the original COBOL program `app/cbl/CBTRN02C.cbl` compiled and executed with
GnuCOBOL against the same input fixtures, and both runs end with `RETURN-CODE 4`.

| Output file | Layout | Records | Fields compared | Differences |
| --- | --- | --- | --- | --- |
| `transact.dat` (posted transactions) | CVTRA05Y, 350 bytes | 262 | 3,668 | 0 |
| `acctdata.dat` (updated accounts) | CVACT01Y, 300 bytes | 50 | 650 | 0 |
| `tcatbal.dat` (category balances) | CVTRA01Y, 50 bytes | 100 | 500 | 0 |
| `dalyrejs.dat` (rejects) | 350 + 80 bytes, 430 total | 38 | 114 | 0 |

| Counter / result | COBOL | Java |
| --- | --- | --- |
| `TRANSACTIONS PROCESSED` | 000000300 | 000000300 |
| `TRANSACTIONS REJECTED` | 000000038 | 000000038 |
| `RETURN-CODE` | 4 | 4 |
| `DISPLAY` output (all 54 lines, including the 50 `TCATBAL record not found ... Creating.` messages) | — | identical |

## 1. Inputs

The ASCII sample datasets in `app/data/ASCII`, used unmodified by both runs:

| DD name | File | Records | Record length |
| --- | --- | --- | --- |
| `DALYTRAN` | `dailytran.txt` | 300 | 350 (CVTRA06Y) |
| `XREFFILE` | `cardxref.txt` | 50 | 50 (CVACT03Y) |
| `ACCTFILE` | `acctdata.txt` | 50 | 300 (CVACT01Y) |
| `TCATBALF` | `tcatbal.txt` | 50 | 50 (CVTRA01Y) |

The fixtures are newline delimited and have their trailing `FILLER` bytes stripped (and `tcatbal.txt`
carries CRLF), so both sides normalise them the same way: strip the delimiter, right pad with spaces
to the record length.

## 2. Methodology

### 2.1 COBOL baseline (`cobol-baseline/run_baseline.sh`)

1. `CBTRN02C.cbl` is compiled **unmodified** with `cobc -x -std=mf -fsign=EBCDIC -I app/cpy`.
2. The three keyed (VSAM KSDS) inputs are loaded into GnuCOBOL indexed files by the small utility
   programs `LOADXREF` / `LOADACCT` / `LOADTCAT`; `DALYTRAN` is fed as a flat 350 byte record file.
   These utilities exist only to stand in for the IDCAMS `REPRO` steps of the mainframe job — the
   business logic program itself is untouched.
3. `CBTRN02C` runs with the DD names supplied as environment variables (`DALYTRAN`, `XREFFILE`,
   `ACCTFILE`, `TCATBALF`, `TRANFILE`, `DALYREJS`), mirroring `app/jcl/POSTTRAN.jcl`.
4. The indexed outputs are unloaded in key order by `UNLDTRAN` / `UNLDACCT` / `UNLDTCAT` into flat
   fixed length files; `DALYREJS` is already sequential. Those flat files plus the program's
   `DISPLAY` log and its return code are the golden baseline, committed under
   `cobol-baseline/baseline/`.

Two details are essential for a reproducible baseline:

* **`-fsign=EBCDIC`** — the fixtures carry the mainframe overpunch sign in the trailing digit of
  signed `DISPLAY` fields (`{`=+0, `A`-`I`=+1..+9, `}`=-0, `J`-`R`=-1..-9). Without the flag
  GnuCOBOL reads `0000005047G` as `504.70` instead of `504.77` and *every* amount is silently wrong.
* **`COB_CURRENT_DATE=2022071112000000`** — pins `FUNCTION CURRENT-DATE`, including the hundredths
  of a second, so `TRAN-PROC-TS` is deterministic. With the 14 digit form the hundredths still come
  from the real clock and the transaction file changes on every run.

### 2.2 Java port

* `PostTranBatch` is the port of the `PROCEDURE DIVISION`; method javadoc names the COBOL paragraph
  each method comes from (`1500-VALIDATE-TRAN`, `2000-POST-TRANSACTION`, `2500-WRITE-REJECT-REC`,
  `2700-UPDATE-TCATBAL`, `2800-UPDATE-ACCOUNT-REC`, `2900-WRITE-TRANSACTION-FILE`).
* Record layouts are ported field by field from the copybooks (`copybook/*.java`), with every offset
  and length taken from `app/cpy`. All monetary and decimal fields are `BigDecimal`; there is no
  `double` or `float` anywhere. `CobolField` implements `USAGE DISPLAY` parsing/formatting including
  the overpunch sign, and truncates results to the capacity of the target `PIC` clause the way a
  COBOL arithmetic statement without `ON SIZE ERROR` does.
* The keyed VSAM files become `TreeMap`s keyed by the COBOL record key, so unloading them in key
  order reproduces the KSDS unload order used by the baseline. The rejects file keeps arrival order.
* The processing timestamp is injectable (`Supplier<String>`, `Db2Timestamp.fixed(...)` /
  `Db2Timestamp.fromClock(...)`); the CLI option `--current-date=YYYYMMDDHHMMSShh` accepts the same
  value as GnuCOBOL's `COB_CURRENT_DATE`, which is what makes the byte for byte comparison possible.
  Left unset, the port reads the wall clock like the COBOL program.

### 2.3 Comparison harness

* `parity/ParityChecker` + `parity/Layouts` diff the two directories record by record and field by
  field, reporting differences as copybook field names rather than byte offsets.
* `CobolParityTest` (JUnit 5) runs the port over the fixtures, then asserts per file: identical
  record counts, zero field differences, byte for byte identical files, identical counters and
  return code, and an identical `DISPLAY` log. It fails loudly if the baseline is missing.
* `PostTranBatchTest` covers the business rules that the sample data does not exercise (see
  limitations), `CobolFieldTest` the sign/decimal semantics and `RecordLayoutTest` asserts that all
  450 fixture records survive parse + serialize unchanged, which validates every field offset.
* `parity/ParityReport` is the CLI form used in the demo recording, and `run_parity_demo.sh` runs
  the whole chain (COBOL run, Java run, `cmp`, parity report).

Reproduce with:

```bash
sudo apt-get install -y gnucobol
java/cbtrn02c/run_parity_demo.sh          # COBOL run + Java run + diff
cd java/cbtrn02c && mvn verify            # 44 tests, including the parity assertions
```

## 3. Results in detail

* **Posted transactions** — 262 of 300 daily transactions posted (300 read, 38 rejected). All 14
  fields of all 262 records match, including `TRAN-AMT` (overpunch sign preserved), the copied
  `TRAN-ORIG-TS`, the generated `TRAN-PROC-TS` and the 20 byte trailing `FILLER`.
* **Accounts** — all 50 accounts were touched by at least one transaction; `ACCT-CURR-BAL`,
  `ACCT-CURR-CYC-CREDIT` and `ACCT-CURR-CYC-DEBIT` match on every record, so the credit/debit
  bucketing (`IF DALYTRAN-AMT >= 0`) and the running balances agree exactly.
* **Category balances** — 50 pre-existing records updated and 50 created (the sample data has no
  type `03` category rows), all in the same order and with the same `TRAN-CAT-BAL` values. The
  `TCATBAL record not found for key : <17 byte key>.. Creating.` messages match one for one.
* **Rejects** — all 38 rejects are reason `0102 OVERLIMIT TRANSACTION`; the 350 byte reject payload
  is the daily transaction record verbatim and the 80 byte trailer (4 digit reason + 76 char
  description) is identical.
* **Return code** — both runs set `RETURN-CODE 4` because rejects occurred.

## 4. Deviations and limitations

No business logic deviations were found; the differences below are environmental or are faithful
reproductions of quirks in the original program.

1. **File access, not business logic, was re-platformed.** The COBOL program reads and writes VSAM
   KSDS files; the port uses keyed in-memory maps and flat output files, as requested. Ordering is
   preserved by keying the maps on the COBOL record keys.
2. **`INITIALIZE` does not clear `FILLER`.** In `2700-A-CREATE-TCATBAL-REC` a created category
   balance record inherits the `FILLER` bytes still present in the `TRAN-CAT-BAL-RECORD` working
   storage area from the last record read (zeroes, in this data) instead of being blanked. The port
   reproduces this (`TranCatBalRecord.create(..., recordAreaFiller)`); without it the 50 created
   records differ from the COBOL output in the last 22 bytes. Flagged rather than "fixed": it is
   original program behaviour.
3. **Both validations in `1500-B` run unconditionally.** A transaction that is over limit *and*
   received after account expiration is reported as `103`, because the expiration `MOVE` executes
   after the over limit one. The port keeps this ordering (covered by
   `expirationReasonOverwritesOverlimitWhenBothChecksFail`).
4. **Reason `109` is unreachable.** The `INVALID KEY` branch of the `REWRITE` in
   `2800-UPDATE-ACCOUNT-REC` cannot trigger, because the account record was just read on its key.
   It is documented in the port instead of being implemented as dead code.
5. **The sample data only exercises reject reason `102`.** No fixture transaction has an unknown
   card (`100`), a missing account (`101`) or an expired account (`103`), so those paths — and the
   boundary conditions `ACCT-CREDIT-LIMIT = WS-TEMP-BAL` and
   `ACCT-EXPIRAION-DATE = DALYTRAN-ORIG-TS(1:10)`, both of which must be accepted — are proven by
   unit tests derived from the COBOL source rather than by the baseline diff.
6. **GnuCOBOL, not z/OS.** The baseline was produced with GnuCOBOL 3.1.2 on Linux with ASCII data,
   which is what the repository ships (`app/data/ASCII`). It is not an IBM Enterprise COBOL run;
   `CEE3ABD` (`9999-ABEND-PROGRAM`) and the file status displays are never reached in this run, so
   the abend paths are not part of the comparison.
7. **Timestamp determinism is a test time construct.** Parity on `TRAN-PROC-TS` requires pinning the
   clock on both sides. In production the port reads the wall clock, exactly like
   `Z-GET-DB2-FORMAT-TIMESTAMP`.

## 5. Demo recording and executive dashboard

* Screen recording of the COBOL run, the Java run and the side by side diff:
  [`docs/cbtrn02c-parity-demo.mp4`](docs/cbtrn02c-parity-demo.mp4).
* One page, non technical summary of this report with the recording embedded:
  [`docs/parity-dashboard.html`](docs/parity-dashboard.html) (self contained, open it in a browser).
