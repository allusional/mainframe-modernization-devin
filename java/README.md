# CardDemo — CBTRN02C modernized (Java 17 / Spring Boot)

Java/Spring Boot modernization of the COBOL batch program **`app/cbl/CBTRN02C.cbl`**
(*Post the records from the daily transaction file*) from the AWS CardDemo
mainframe application.

The original COBOL is untouched; this module is a faithful re-implementation of its
business logic.

## What CBTRN02C does

For each record in the daily transaction file (DALYTRAN) the program:

1. **Validates** the transaction:
   - looks up the card in the card cross-reference (XREF) — reject reason **100**
     (`INVALID CARD NUMBER FOUND`) if missing;
   - looks up the account (ACCT) via the xref account id — reject reason **101**
     (`ACCOUNT RECORD NOT FOUND`) if missing;
   - credit-limit check: `ACCT-CREDIT-LIMIT >= CURR-CYC-CREDIT − CURR-CYC-DEBIT + TRAN-AMT`,
     otherwise reject reason **102** (`OVERLIMIT TRANSACTION`);
   - expiration check: `ACCT-EXPIRAION-DATE >= TRAN-ORIG-TS(1:10)`, otherwise reject
     reason **103** (`TRANSACTION RECEIVED AFTER ACCT EXPIRATION`).
     When both 102 and 103 fail, 103 is the surviving reason (COBOL evaluates the
     checks in sequence).
2. **Posts** valid transactions:
   - updates the transaction category balance (TCATBAL), creating the record if the
     key does not yet exist;
   - updates the account: `CURR-BAL += amount`; positive amounts add to
     `CURR-CYC-CREDIT`, negative amounts add to `CURR-CYC-DEBIT`;
   - writes the posted transaction record (TRANSACT).
3. **Rejects** invalid transactions to DALYREJS (raw 350-byte record + 80-byte trailer).

At end of job, `RETURN-CODE` is set to **4** when any transaction was rejected.

## COBOL → Java mapping

| COBOL element | Java |
| --- | --- |
| `CVTRA06Y` DALYTRAN-RECORD | `model.DailyTransactionRecord` |
| `CVTRA05Y` TRAN-RECORD | `model.TransactionRecord` |
| `CVACT01Y` ACCOUNT-RECORD | `model.AccountRecord` |
| `CVACT03Y` CARD-XREF-RECORD | `model.CardXrefRecord` |
| `CVTRA01Y` TRAN-CAT-BAL-RECORD | `model.TranCatBalRecord` |
| Indexed VSAM files (XREF/ACCT/TCATBAL) | `repository.*Repository` (in-memory keyed maps) |
| Zoned decimal / signed overpunch + implied decimal | `io.ZonedDecimal` |
| `1500-VALIDATE-TRAN` / `1500-A/B` | `service.TransactionPostingService#process` / `validateAgainstAccount` |
| `2000-POST-TRANSACTION` | `TransactionPostingService#postTransaction` |
| `2700-UPDATE-TCATBAL` (+ 2700-A/B) | `TransactionPostingService#updateTransactionCategoryBalance` |
| `2800-UPDATE-ACCOUNT-REC` | `TransactionPostingService#updateAccount` |
| `2900-WRITE-TRANSACTION-FILE` / `2500-WRITE-REJECT-REC` | `io.RecordFiles#writeSummary`, `PostingOutcome#toRejectRecord` |
| `Z-GET-DB2-FORMAT-TIMESTAMP` | `service.ProcessingTimestampProvider` |
| `9999-ABEND-PROGRAM` | `service.BatchAbendException` |
| PROCEDURE DIVISION main loop | `service.DailyTransactionPostingJob` |

Money fields use `java.math.BigDecimal` (never `double`) to preserve exact
packed/zoned-decimal arithmetic.

## Build & test

```bash
cd java
mvn test        # runs the JUnit 5 suite (24 tests)
mvn package     # builds the runnable Spring Boot jar
```

## Run against the sample data

```bash
cd java
mvn spring-boot:run -Dspring-boot.run.arguments="\
  --dalytran=../app/data/ASCII/dailytran.txt \
  --xref=../app/data/ASCII/cardxref.txt \
  --acct=../app/data/ASCII/acctdata.txt \
  --tcatbal=../app/data/ASCII/tcatbal.txt \
  --tranfile=target/TRANSACT.OUT \
  --rejects=target/DALYREJS.OUT"
```
