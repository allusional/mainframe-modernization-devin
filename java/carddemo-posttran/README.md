# carddemo-posttran — Java port of CBTRN02C (POSTTRAN)

Java 17 / Maven port of the CardDemo daily transaction poster `app/cbl/CBTRN02C.cbl`.
The business logic is preserved as-is; only the I/O layer differs — the six VSAM/sequential
files are abstracted behind repository interfaces so the batch can run and be tested
without a mainframe.

```
mvn test
```

## Running it against the sample datasets

`com.carddemo.posttran.files` adds a flat-file I/O layer over the same repository interfaces, so the
batch can be run against `app/data/ASCII` and produce the four output files as text:

```
mvn -q package
java -cp target/classes com.carddemo.posttran.files.PostTranBatchRunner ../../app/data/ASCII /tmp/out
```

`scripts/posttran-parity/run-parity.sh` runs this alongside the unmodified COBOL program under
GnuCOBOL and compares every record both write; see
[scripts/posttran-parity/README.md](../../scripts/posttran-parity/README.md).

## COBOL paragraph → Java mapping

| COBOL | Java |
| --- | --- |
| `PROCEDURE DIVISION` main loop (lines 202-234) | `PostTransactionBatch.run()` |
| `1000-DALYTRAN-GET-NEXT` | `DailyTransactionReader.next()` (empty `Optional` = EOF) |
| `1500-VALIDATE-TRAN`, `1500-A-LOOKUP-XREF` | `PostTransactionBatch.validate(...)` |
| `1500-B-LOOKUP-ACCT` | `PostTransactionBatch.lookupAccount(...)` |
| `2000-POST-TRANSACTION` | `PostTransactionBatch.postTransaction(...)` |
| `2500-WRITE-REJECT-REC` | `PostTransactionBatch.writeReject(...)` → `RejectWriter` |
| `2700-UPDATE-TCATBAL`, `2700-A`, `2700-B` | `PostTransactionBatch.updateTranCatBalance(...)` |
| `2800-UPDATE-ACCOUNT-REC` | `PostTransactionBatch.updateAccount(...)` |
| `2900-WRITE-TRANSACTION-FILE` | `TransactionWriter.write(...)` |
| `Z-GET-DB2-FORMAT-TIMESTAMP` (lines 692-705) | `Db2Timestamp.now()` (`Clock` injected for tests) |
| `WS-TRANSACTION-COUNT` / `WS-REJECT-COUNT` / `RETURN-CODE` | `getTransactionCount()` / `getRejectCount()` / return value of `run()` (4 when anything rejected) |
| open/close paragraphs (`0000`-`0500`, `9000`-`9500`), `9910`, `9999-ABEND-PROGRAM` | not ported — file lifecycle and file-status/abend handling are the responsibility of the repository implementations |

## Record layouts

| Copybook | Java |
| --- | --- |
| `CVTRA06Y` `DALYTRAN-RECORD` | `DailyTransaction` |
| `CVTRA05Y` `TRAN-RECORD` | `Transaction` |
| `CVACT03Y` `CARD-XREF-RECORD` | `CardXref` |
| `CVACT01Y` `ACCOUNT-RECORD` | `Account` |
| `CVTRA01Y` `TRAN-CAT-BAL-RECORD` | `TranCatBalance` + `TranCatBalanceKey` |

`PIC S9(n)V99` fields are `BigDecimal` with scale 2 (truncating, matching a COBOL `MOVE` into a
`V99` field); `PIC X(n)` fields are `String`; `PIC 9(11)` account ids are `long`.

## Files → repositories

| COBOL file | Interface | In-memory implementation |
| --- | --- | --- |
| `DALYTRAN` (sequential in) | `DailyTransactionReader` | `InMemoryDailyTransactionReader` |
| `XREFFILE` (KSDS) | `XrefRepository` | `InMemoryXrefRepository` |
| `ACCTFILE` (KSDS) | `AccountRepository` | `InMemoryAccountRepository` |
| `TCATBALF` (KSDS) | `TranCatBalanceRepository` | `InMemoryTranCatBalanceRepository` |
| `TRANFILE` (out) | `TransactionWriter` | `InMemoryTransactionWriter` |
| `DALYREJS` (sequential out) | `RejectWriter` | `InMemoryRejectWriter` |

## Validation rules preserved

| Reason | Description | Condition |
| --- | --- | --- |
| `100` | `INVALID CARD NUMBER FOUND` | no xref record for `DALYTRAN-CARD-NUM` |
| `101` | `ACCOUNT RECORD NOT FOUND` | no account record for `XREF-ACCT-ID` |
| `102` | `OVERLIMIT TRANSACTION` | `ACCT-CREDIT-LIMIT < ACCT-CURR-CYC-CREDIT - ACCT-CURR-CYC-DEBIT + DALYTRAN-AMT` |
| `103` | `TRANSACTION RECEIVED AFTER ACCT EXPIRATION` | `ACCT-EXPIRAION-DATE < DALYTRAN-ORIG-TS(1:10)` |

As in the COBOL, both checks run against a found account and the expiration check is evaluated
last, so an expired *and* overlimit transaction is reported as `103`.

## Timestamp format

`Z-GET-DB2-FORMAT-TIMESTAMP` builds `DB2-FORMAT-TS PIC X(26)` from `FUNCTION CURRENT-DATE`,
whose `COB-MIL` field is only two digits (hundredths of a second) followed by a literal `'0000'`.
`Db2Timestamp.now()` reproduces that exactly: `yyyy-MM-dd-HH.mm.ss.SS` + `0000`, e.g.
`2024-03-15-10.20.30.450000` — 26 characters.
