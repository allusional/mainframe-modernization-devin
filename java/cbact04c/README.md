# CBACT04C in Java

A plain Java (Maven, no framework) port of `app/cbl/CBACT04C.cbl`, the CardDemo monthly
interest calculator that runs as the `INTCALC` job. It reads and writes the same fixed
width records as the COBOL, so it can be pointed straight at the sample datasets in
`app/data/ASCII`.

A plain-English description of what the program does, and of the business rules it
implements, is in [`../../CBACT04C-EXPLAINED.md`](../../CBACT04C-EXPLAINED.md).

## Build and test

```bash
mvn verify
```

Requires JDK 17+. Tests include an end-to-end run over the shipped sample data
(`SampleDataTest`).

## Run

```bash
java -jar target/cbact04c-1.0.0-SNAPSHOT.jar \
  --parm 2022071800 \
  --tcatbal ../../app/data/ASCII/tcatbal.txt \
  --acct    /tmp/acctdata.txt \
  --xref    ../../app/data/ASCII/cardxref.txt \
  --discgrp ../../app/data/ASCII/discgrp.txt \
  --out-transact /tmp/systran.txt
```

`--acct` is updated in place (the COBOL opens `ACCTFILE` `I-O`); pass `--out-acct` to write
the updated master somewhere else and leave the input untouched. Copy the sample file first
if you do not want to modify it.

| CLI option | JCL DD name in `app/jcl/INTCALC.jcl` | Copybook |
| --- | --- | --- |
| `--tcatbal` | `TCATBALF` | `CVTRA01Y` |
| `--acct` / `--out-acct` | `ACCTFILE` | `CVACT01Y` |
| `--xref` | `XREFFILE` | `CVACT03Y` |
| `--discgrp` | `DISCGRP` | `CVTRA02Y` |
| `--out-transact` | `TRANSACT` | `CVTRA05Y` |
| `--parm` | `PARM='2022071800'` | — |

Exit code 0 on success, 12 on an abend (the COBOL abends with user code 999 via `CEE3ABD`).

## How the COBOL maps onto the Java

| COBOL paragraph | Java |
| --- | --- |
| `PROCEDURE DIVISION` main loop | `InterestCalculator.run` |
| `1000-TCATBALF-GET-NEXT` | iteration over the parsed `TranCatBalRecord` list |
| account control break (`:194-206`) | `startAccount` |
| `1050-UPDATE-ACCOUNT` | `AccountRecord.applyInterestAndCloseCycle` via `settleCurrentAccount` |
| `1100-GET-ACCT-DATA`, `1110-GET-XREF-DATA` | map lookups in `startAccount`, abend on a miss |
| `1200-GET-INTEREST-RATE` / `1200-A-GET-DEFAULT-INT-RATE` | `findRate` |
| `1300-COMPUTE-INTEREST` | `computeInterest` |
| `1300-B-WRITE-TX` | `buildTransaction` |
| `1400-COMPUTE-FEES` | `computeFees` (still an empty stub, as in the COBOL) |
| `Z-GET-DB2-FORMAT-TIMESTAMP` | `Db2Timestamp` |
| `9999-ABEND-PROGRAM` | `AbendException` |
| display/zoned numeric fields | `cobol.Zoned` |

Behaviour deliberately preserved:

- interest is `balance * annualRate / 1200`, **truncated** to two decimals, because the COBOL
  `COMPUTE` has no `ROUNDED` clause;
- a rate of zero means no interest and no transaction;
- a missing disclosure group row falls back to the group `DEFAULT`, and a missing `DEFAULT`
  row is fatal;
- a missing account or cross reference row is fatal (no reject file);
- generated transactions are always type `01` / category `0005` with source `System`, id
  `<parm date><6 digit counter>`, and timestamps taken from the system clock rather than
  from `--parm`;
- account settlement adds the accumulated interest to the balance and zeroes both
  cycle-to-date totals.

## Deviations from the COBOL

1. **The last account is settled.** In the COBOL, the `ELSE PERFORM 1050-UPDATE-ACCOUNT`
   branch is unreachable, so the final account in the balance file gets interest
   transactions but never has its master record updated. The Java settles it. Pass
   `--emulate-final-account-quirk` to reproduce the original behaviour; both paths are
   covered by tests.
2. **Files are read into memory** rather than accessed as VSAM KSDS records. The datasets are
   small (tens of records in the samples) and this keeps the port free of an indexed file
   library. Input order is preserved when the account master is written back.
3. **Records are line delimited.** The COBOL datasets are fixed length with no delimiter;
   the ASCII renderings in `app/data/ASCII` use newlines, and this port follows them,
   tolerating both LF and CRLF on input and writing LF.
4. **No `DISPLAY` of every input record.** The COBOL echoes each category balance to SYSOUT;
   the Java prints summary counts instead.

## A note about the sample data

Every account in `app/data/ASCII/acctdata.txt` has a **blank** `ACCT-GROUP-ID`: the value
that looks like a group id (`A000000000`) sits in the preceding `ACCT-ADDR-ZIP` field. So
with the shipped data every rate lookup misses and the `DEFAULT` disclosure group is what
actually applies. That is a property of the data, not of the port, and `SampleDataTest`
asserts it so the behaviour is visible rather than surprising. Also, every balance in
`tcatbal.txt` is zero, so a run over the untouched samples produces 50 transactions of
0.00 and leaves the account balances unchanged.
