# CardDemo CBACT04C — Java / Spring Boot Modernization

Java 17 + Spring Boot port of the COBOL batch program **`CBACT04C`** (the CardDemo
interest calculator, `app/cbl/CBACT04C.cbl`). It reads the Transaction Category
Balance file, computes monthly interest per account using the disclosure-group
interest rates, writes an interest transaction for every non-zero amount, and
posts the accumulated interest back to the account master.

## Layout

| Package | Responsibility |
| --- | --- |
| `model` | Java records for the COBOL copybook layouts (money as `BigDecimal`). |
| `io` | Fixed-width / zoned-decimal parsing and serialization (readers & writer). |
| `repository` | VSAM-style random-access lookups and the sequential transaction writer. |
| `service` | `InterestCalculatorService` — the business logic (PROCEDURE DIVISION). |
| `BatchRunner` | Thin CLI entry point mirroring the `INTCALC.jcl` step. |

## Build & test

```bash
cd java
mvn test
```

## Run

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="\
  --tcatbal=../app/data/ASCII/tcatbal.txt \
  --account=../app/data/ASCII/acctdata.txt \
  --xref=../app/data/ASCII/cardxref.txt \
  --discgrp=../app/data/ASCII/discgrp.txt \
  --output=/tmp/systran.out \
  --account-out=/tmp/acct.out \
  --parm-date=2022071800"
```

`--parm-date` corresponds to the JCL `PARM='2022071800'` and is used as the
transaction-id prefix. `--account-out` is optional; when supplied the (possibly
updated) account master is written back in the same fixed-width format.

## Faithfully reproduced behaviors

- **Truncation, not rounding.** Monthly interest = `(balance * rate) / 1200`
  truncated to 2 decimals (COBOL `COMPUTE` without `ROUNDED`).
- **DEFAULT disclosure group fallback.** A missing primary disclosure key
  (file status `23`) falls back to the `DEFAULT` group; a missing DEFAULT abends.
- **Abends as exceptions.** Missing account / xref / DEFAULT records raise
  `AbendException` (replacing `CALL 'CEE3ABD'`).
- **Last-account quirk.** The original program never posts the *last* account
  group's accumulated interest — its main-loop `ELSE PERFORM 1050-UPDATE-ACCOUNT`
  branch is unreachable dead code. This is preserved (and covered by a test).
- **Sample-data note.** In the bundled sample, `ACCT-GROUP-ID` (per `CVACT01Y`)
  is blank, so every interest lookup resolves through the `DEFAULT` group.
