# carddemo-intcalc — Java port of CBACT04C (INTCALC)

Java 17 / Maven port of `app/cbl/CBACT04C.cbl`, the CardDemo monthly interest calculator run by
`app/jcl/INTCALC.jcl` (STEP15, `PARM='2022071800'`). The port preserves the program's behaviour
paragraph for paragraph, including its defects; equivalence is proved by
[`scripts/intcalc-parity/run-parity.sh`](../../scripts/intcalc-parity/README.md), which runs the
unmodified COBOL under GnuCOBOL and this port over the same datasets and compares every output
record.

```
mvn verify                                   # unit tests
mvn -q package
java -cp target/classes com.carddemo.intcalc.files.IntCalcBatchRunner \
    ../../app/data/ASCII /tmp/intcalc-out 2022071800
```

## Files

| COBOL DD (`ASSIGN TO`) | Dataset | Java |
| --- | --- | --- |
| `TCATBALF` (KSDS, read sequentially) | `app/data/ASCII/tcatbal.txt` | `TranCatBalanceReader` / `FlatFileTranCatBalanceReader` |
| `XREFFILE` (KSDS, random read on the `FD-XREF-ACCT-ID` alternate key) | `app/data/ASCII/cardxref.txt` | `XrefRepository` / `FlatFileXrefRepository` |
| `DISCGRP` (KSDS, random read) | `app/data/ASCII/discgrp.txt` | `DiscGroupRepository` / `FlatFileDiscGroupRepository` |
| `ACCTFILE` (KSDS, I-O: read + rewrite) | `app/data/ASCII/acctdata.txt` | `AccountRepository` / `FlatFileAccountRepository` |
| `TRANSACT` (QSAM, output) | written to `tranfile.txt` | `TransactionWriter` / `FlatFileTransactionWriter` |

One class per copybook record layout: `TranCatBalance` (CVTRA01Y), `CardXref` (CVACT03Y),
`DiscGroup` + `DiscGroupKey` (CVTRA02Y), `Account` (CVACT01Y), `Transaction` (CVTRA05Y).
`InterestCalculationBatch` holds the PROCEDURE DIVISION and no I/O: the files are behind the
interfaces above and every `DISPLAY` goes to a `Consumer<String>` sink.

## COBOL paragraph → Java method

| CBACT04C paragraph | `InterestCalculationBatch` method |
| --- | --- |
| main PROCEDURE DIVISION loop, `0000-TCATBALF-OPEN` … `9000-*-CLOSE` | `run()` (opening and closing files is the flat-file layer's job) |
| `1000-TCATBALF-GET-NEXT` | `getNextTranCatBalance()` |
| `1050-UPDATE-ACCOUNT` | `updateAccount()` |
| `1100-GET-ACCT-DATA` | `getAcctData(long)` |
| `1110-GET-XREF-DATA` | `getXrefData(long)` |
| `1200-GET-INTEREST-RATE` | `getInterestRate(DiscGroupKey)` |
| `1200-A-GET-DEFAULT-INT-RATE` | `getDefaultInterestRate(DiscGroupKey)` |
| `1300-COMPUTE-INTEREST` | `computeInterest(TranCatBalance)` |
| `1300-B-WRITE-TX` | `writeTransaction()` |
| `1400-COMPUTE-FEES` | `computeFees()` (empty in the COBOL: "To be implemented") |
| `Z-GET-DB2-FORMAT-TIMESTAMP` | `Db2Timestamp.now()` |
| `9910-DISPLAY-IO-STATUS`, `9999-ABEND-PROGRAM` | `abend(String)` / `AbendException` |

## COBOL semantics reproduced

- **Truncation, not rounding.** `COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200` has
  no `ROUNDED` phrase, so the result is truncated towards zero to the two decimals of
  `PIC S9(09)V99`; `Cobol.amount` also drops high-order digits that do not fit, as a `MOVE` does.
  Monetary fields are `BigDecimal` with scale 2.
- **Signed `DISPLAY` overpunch.** Signed `PIC S9(n)V99 DISPLAY` fields carry the sign in the zone of
  the last digit (`{`=+0, `A`-`I`=+1..9, `}`=-0, `J`-`R`=-1..9). `Cobol` decodes and encodes it, and
  `IntCalcBatchRunner` asserts at startup that every input record survives a decode/encode round
  trip byte for byte, so a codec bug cannot be mistaken for parity.
- **The last account on the file is never updated.** The main loop reads a record, and only the
  `NOT AT END` branch can set `END-OF-FILE`; the `ELSE PERFORM 1050-UPDATE-ACCOUNT` branch that
  would settle the final account is therefore unreachable. Its interest is written to TRANFILE but
  never added to `ACCT-CURR-BAL`, and its cycle totals are not reset. Reproduced deliberately, and
  pinned down by `neverUpdatesTheAccountOfTheLastRecordOnTheFile`.
- **Interest of a zero rate is skipped entirely.** `IF DIS-INT-RATE = 0` skips both the computation
  and the transaction, but the account is still rewritten at the account break, so its cycle totals
  are reset.
- **A missing disclosure group falls back to `DEFAULT`**, and a missing `DEFAULT` record abends: the
  fallback read has no `INVALID KEY` clause, so a status 23 goes to `9910-DISPLAY-IO-STATUS` and
  `9999-ABEND-PROGRAM`.
- **`WS-TRANID-SUFFIX` is `PIC 9(06)`**, so the transaction counter wraps at 1,000,000.
- **`FILLER` is preserved.** The 178 byte `FILLER` of CVACT01Y is written back unchanged by a
  rewrite, so `FlatFileAccountRepository` keeps the bytes it read.
- **The account is only re-read when the account id changes**, which is why a `TCATBALF` file that
  is not in account order would post interest against the wrong account — the program relies on the
  KSDS key sequence.

## Coverage

`mvn verify` runs 31 tests. Branches the sample data in `app/data/ASCII` cannot reach are covered by
unit tests instead: the interest computation with a non-zero rate, the zero-rate skip, the account
balance update, the account/XREF/disclosure-group `INVALID KEY` abends, and truncation of a negative
amount. What no test or run exercises is listed in
[the harness README](../../scripts/intcalc-parity/README.md#what-is-not-exercised).
