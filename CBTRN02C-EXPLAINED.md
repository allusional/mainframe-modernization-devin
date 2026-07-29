# CBTRN02C — Plain-English Explanation

**Source:** `app/cbl/CBTRN02C.cbl` (CardDemo sample credit-card system)
**Type:** Batch program, run from `app/jcl/POSTTRAN.jcl` as job `POSTTRAN`, step `STEP15`
**Header comment says:** "Post the records from daily transaction file." (`app/cbl/CBTRN02C.cbl:5`)

Everything below cites a line of source. Where a claim comes from reading the shipped
sample data rather than from the program text, it says so. Where I could not establish
something from the repository alone, it is in §7 (uncertainties) rather than stated as fact.

Line references of the form `:NNN` are lines in `app/cbl/CBTRN02C.cbl` unless another file
is named.

---

## 1. What this job does, in plain English

Every night, a credit-card issuer receives a file of the day's card activity — the
purchases, refunds and payments that came in from point-of-sale terminals, call-centre
operators and so on. Those are just *claims* at that point: a card number, an amount, a
merchant, a timestamp. Nothing has hit the customer's account yet.

CBTRN02C is the program that turns those claims into money movements. For each transaction
in the daily feed it:

1. **Finds out whose account it is.** The feed carries a card number, not an account
   number, so the program looks the card up in the card cross-reference file to get the
   account id (`:380-392`).
2. **Checks the transaction is allowed.** The account must exist, the transaction must not
   push the account past its credit limit, and the account must not have expired
   (`:393-421`).
3. **If it passes, it posts it** — three files change: the customer's running balance goes
   up or down, the "how much has this customer spent in this category this cycle" bucket
   moves by the same amount, and a permanent record of the transaction is written to the
   transaction master (`:424-444`).
4. **If it fails, it rejects it** — the whole 350-byte input record is copied verbatim to a
   rejects file with an 80-byte trailer saying why (`:446-465`). Nothing else changes for
   that transaction. The money never moves.

At the end it prints how many transactions it read and how many it rejected, and sets a
return code of 4 if anything was rejected (`:227-231`).

**What it is not:** it is not authorisation (that already happened at the terminal), it is
not fraud screening, it is not statement production, and it does not calculate interest or
fees. It is the accounting step that makes the day's activity real.

### Where it sits in the nightly batch

The CA-7 schedule in `app/scheduler/CardDemo.ca7` triggers the jobs in this order
(`CardDemo.ca7:18-135`):

```
CLOSEFIL  →  CBPAUP0J  →  POSTTRAN  →  WAITSTEP  →  OPENFIL
```

`CLOSEFIL` quiesces the VSAM files in the CICS region so batch can have them; `OPENFIL`
gives them back. POSTTRAN runs in that batch window.

The operator script `scripts/run_posting.sh:13-34` shows the fuller sequence actually used
for a demo run, and one step of it matters a great deal for understanding CBTRN02C:

```
CLOSEFIL → ACCTFILE → TCATBALF → TRANBKP → POSTTRAN → TRANIDX → OPENFIL
```

`TRANBKP` (`app/jcl/TRANBKP.jcl:23-31`) copies the current transaction master to a new
backup generation `AWS.M2.CARDDEMO.TRANSACT.BKUP(+1)` and then **deletes and re-defines the
transaction master empty** (`TRANBKP.jcl:36-70`). CBTRN02C then opens that now-empty master
`OUTPUT` and fills it with today's postings. Later, `COMBTRAN` (`app/jcl/COMBTRAN.jcl:22-48`)
sorts the backup together with the interest transactions from `CBACT04C`/`INTCALC` and
REPROs the result back into the master. See R22 and U1 — the fact that CBTRN02C opens an
indexed master `OUTPUT` is only safe because an external job emptied it first.

---

## 2. Every file the program touches

DD names are from `app/jcl/POSTTRAN.jcl:24-42`. Record lengths are the sum of the copybook
PIC clauses; I checked each one adds up.

| DD name | Dataset | Copybook | Len | Organisation / key | Open mode | What the program does to it |
| --- | --- | --- | --- | --- | --- | --- |
| `DALYTRAN` | `AWS.M2.CARDDEMO.DALYTRAN.PS` | `CVTRA06Y` | 350 | Physical sequential (`:29-32`) | `INPUT` (`:238`) | **Read only**, front to back. The driving file: one record = one claimed transaction. |
| `XREFFILE` | `AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS` | `CVACT03Y` | 50 | VSAM KSDS, `ACCESS RANDOM`, key = `FD-XREF-CARD-NUM` `PIC X(16)` at offset 0 (`:40-44`) | `INPUT` (`:275`) | **Read only**, one keyed read per transaction, to turn a card number into an account id. |
| `ACCTFILE` | `AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS` | `CVACT01Y` | 300 | VSAM KSDS, `ACCESS RANDOM`, key = `FD-ACCT-ID` `PIC 9(11)` at offset 0 (`:51-55`) | `I-O` (`:311`) | **Read and rewritten in place.** Three fields change per posted transaction: current balance, cycle-to-date credit, cycle-to-date debit (`:547-554`). |
| `TCATBALF` | `AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS` | `CVTRA01Y` | 50 | VSAM KSDS, `ACCESS RANDOM`, key = account id `9(11)` + type code `X(02)` + category code `9(04)`, 17 bytes at offset 0 (`:57-61`) | `I-O` (`:329`) | **Read, rewritten, and inserted into.** One bucket per account+type+category; the transaction amount is added to it. If the bucket does not exist yet the program creates it (`:495-541`). |
| `TRANFILE` | `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS` | `CVTRA05Y` | 350 | VSAM KSDS, `ACCESS RANDOM`, key = `FD-TRANS-ID` `PIC X(16)` at offset 0 (`:34-38`) | **`OUTPUT`** (`:256`) | **Written only.** One record per *posted* transaction. Because it is opened `OUTPUT`, not `I-O` or `EXTEND`, the file is created fresh — see R22 and U1. |
| `DALYREJS` | `AWS.M2.CARDDEMO.DALYREJS(+1)`, a GDG generation, `RECFM=F,LRECL=430` (`POSTTRAN.jcl:32-37`) | none (declared inline, `:81-84`) | 430 | Physical sequential (`:46-49`) | `OUTPUT` (`:293`) | **Written only.** One record per *rejected* transaction: the 350-byte input record unchanged + an 80-byte trailer. |

There is **no PARM** on the EXEC card (`POSTTRAN.jcl:23`) and no `PROCEDURE DIVISION USING`
(`:193`), so the program takes no run-date and no parameters at all. Everything it needs is
in the files, and every timestamp it writes comes from the system clock (`:692-705`).

### The daily transaction record (`app/cpy/CVTRA06Y.cpy`)

| Field | PIC | Offset (0-based) | Notes |
| --- | --- | --- | --- |
| `DALYTRAN-ID` | `X(16)` | 0 | Becomes the key of the transaction master record. |
| `DALYTRAN-TYPE-CD` | `X(02)` | 16 | Part of the category-balance key. `app/data/ASCII/trantype.txt` lists `01 Purchase`, `02 Payment`, `03 Credit`, … The program never validates it against that file. |
| `DALYTRAN-CAT-CD` | `9(04)` | 18 | Part of the category-balance key. Reference data is `app/data/ASCII/trancatg.txt`; again never validated. |
| `DALYTRAN-SOURCE` | `X(10)` | 22 | e.g. `POS TERM`, `OPERATOR`. Carried through, never examined. |
| `DALYTRAN-DESC` | `X(100)` | 32 | |
| `DALYTRAN-AMT` | `S9(09)V99` | 132, 11 bytes | **Signed DISPLAY.** The sign lives as an overpunch in the last byte: `{`=+0 … `I`=+9, `}`=-0 … `R`=-9. Positive = a charge that increases what the customer owes; negative = a refund/credit. This sign convention drives R8 and R17. |
| `DALYTRAN-MERCHANT-ID` | `9(09)` | 143 | |
| `DALYTRAN-MERCHANT-NAME` | `X(50)` | 152 | |
| `DALYTRAN-MERCHANT-CITY` | `X(50)` | 202 | |
| `DALYTRAN-MERCHANT-ZIP` | `X(10)` | 252 | |
| `DALYTRAN-CARD-NUM` | `X(16)` | 262 | The only link to a customer. |
| `DALYTRAN-ORIG-TS` | `X(26)` | 278 | When the transaction happened. **A character field, not a date field** — the program compares its first 10 characters as text (R9). |
| `DALYTRAN-PROC-TS` | `X(26)` | 304 | Present in the input layout but **never read and never used**; the program stamps its own (R12). |
| `FILLER` | `X(20)` | 330 | |

16+2+4+10+100+11+9+50+50+10+16+26+26+20 = **350**, matching the copybook comment and the
`FD` (`:66-69`).

---

## 3. The business rules, numbered, in the order they apply

### Reading the feed

**R1 — One pass, front to back, no sort, no restart logic.** The daily file is read
sequentially until end of file (`:202-219`, `:345-369`). There is no checkpoint, no commit
point and no restart key. See D7.

**R2 — Every record read is counted, whether or not it posts.** `WS-TRANSACTION-COUNT` is
incremented immediately after a successful read, before any validation (`:206`). The label
the operator sees is `TRANSACTIONS PROCESSED`, which therefore means *records read*, not
*records posted* (`:227`). Posted = processed − rejected.

**R3 — A read error that is not end-of-file abends the job.** File status `00` continues,
`10` ends the loop, anything else displays `ERROR READING DALYTRAN FILE`, prints the status
and abends (`:346-368`).

**R4 — Validation state is reset per record.** The reason code and its description are
cleared to `0` and spaces before each record is validated (`:208-209`). This is what makes
R18's silent failure silent.

### Validation (`1500-VALIDATE-TRAN`, `:370-421`)

**R5 — Validation stops at the first failing *stage*, but not at the first failing *check*.**
`1500-A-LOOKUP-XREF` runs first; `1500-B-LOOKUP-ACCT` runs only if the first left the reason
code at zero (`:371-376`). Inside `1500-B`, however, the credit-limit check and the expiry
check both run unconditionally, one after the other (`:407-420`). See D8.

**R6 — Reject 0100, `INVALID CARD NUMBER FOUND`.** The card number from the feed is used as
the key into the cross-reference KSDS. If there is no such card, reason `100` is set with
that exact text (`:382-388`). The comment `* ADD MORE VALIDATIONS HERE` at `:377` is the
author acknowledging the ruleset is incomplete.

**R7 — Reject 0101, `ACCOUNT RECORD NOT FOUND`.** The account id taken from the
cross-reference record is used as the key into the account master. If there is no such
account, reason `101` is set with that text (`:394-399`). This means the cross-reference and
the account master disagree — a data-integrity failure, not a customer error.

**R8 — Reject 0102, `OVERLIMIT TRANSACTION`.** The program computes

```
WS-TEMP-BAL = ACCT-CURR-CYC-CREDIT − ACCT-CURR-CYC-DEBIT + DALYTRAN-AMT      (:403-405)
```

and rejects if `ACCT-CREDIT-LIMIT >= WS-TEMP-BAL` is false, i.e. if the computed figure
exceeds the credit limit (`:407-413`). Three things about this are worth stating plainly:

- **`ACCT-CURR-BAL` — the customer's actual outstanding balance — is not in the formula.**
  The test is only about this cycle's activity. See D3.
- Because the account master is rewritten as each transaction posts (R17), the cycle
  figures grow during the run, so **the outcome of this check depends on the order of the
  records in the file**. The same set of transactions in a different order can produce a
  different set of rejects.
- `WS-TEMP-BAL` is `PIC S9(09)V99` (`:187`) while `ACCT-CREDIT-LIMIT` and the two cycle
  fields are `PIC S9(10)V99` (`app/cpy/CVACT01Y.cpy:8,13-14`). The `COMPUTE` has no
  `ON SIZE ERROR`. See D5.

**R9 — Reject 0103, `TRANSACTION RECEIVED AFTER ACCT EXPIRATION`.** Rejects if
`ACCT-EXPIRAION-DATE >= DALYTRAN-ORIG-TS (1:10)` is false (`:414-420`). Both sides are
`PIC X` — `ACCT-EXPIRAION-DATE` is `X(10)` (`CVACT01Y.cpy:11`) and the reference is the first
10 characters of a `X(26)` field (`CVTRA06Y.cpy:16`). **This is a character comparison, not a
date comparison.** It gives the right answer only because both are `YYYY-MM-DD`, which sorts
correctly as text. It is inclusive: a transaction dated exactly on the expiry date is
accepted. Note the misspelling `EXPIRAION` is in the production copybook, not a typo here.

**R10 — Any non-zero reason code means reject; zero means post.** The mainline tests only
for `WS-VALIDATION-FAIL-REASON = 0` (`:211`).

### Posting a good transaction (`2000-POST-TRANSACTION`, `:424-444`)

**R11 — The transaction record is built by copying the daily record field for field.**
All twelve business fields are moved across unchanged, including `DALYTRAN-ORIG-TS`
(`:425-436`). The layouts `CVTRA06Y` and `CVTRA05Y` are field-for-field identical, so this
is a straight copy.

**R12 — `TRAN-PROC-TS` is stamped from the system clock, in DB2 format.**
`Z-GET-DB2-FORMAT-TIMESTAMP` (`:692-705`) builds `YYYY-MM-DD-HH.MM.SS.hh0000` from
`FUNCTION CURRENT-DATE`: the last four digits are the literal `'0000'` (`:701`) because
COBOL's `CURRENT-DATE` only resolves to hundredths of a second. Note the resulting
transaction master holds **two different timestamp formats**: `TRAN-ORIG-TS` in the feed's
format (`2022-06-10 19:27:53.000000` in `app/data/ASCII/dailytran.txt`) and `TRAN-PROC-TS` in
DB2 format with hyphens and dots. See U3.

**R13 — Three files are updated, in this order: category balance, then account, then
transaction master** (`:440-442`). The order matters for what a failure leaves behind — see
D1 and D7.

**R14 — The category balance bucket is found or created.** The key is
`XREF-ACCT-ID + DALYTRAN-TYPE-CD + DALYTRAN-CAT-CD` (`:469-471`). A keyed read is attempted;
`INVALID KEY` sets a create flag and **displays a line to SYSOUT**:
`TCATBAL record not found for key : <17-byte key>.. Creating.` (`:474-479`). File status `00`
or `23` (not found) are both acceptable; anything else abends (`:481-493`).

**R15 — Creating a bucket: `INITIALIZE`, set the key, add the amount, `WRITE`** (`:503-524`).
`INITIALIZE` zeroes the balance and blanks the filler, so a brand-new bucket starts at
exactly the transaction amount.

**R16 — Updating a bucket: add the amount, `REWRITE`** (`:526-542`). Signed addition, so a
refund reduces the bucket.

**R17 — The account is updated with an inverted-looking sign convention.** (`:545-554`)

```
ACCT-CURR-BAL := ACCT-CURR-BAL + DALYTRAN-AMT
IF DALYTRAN-AMT >= 0  →  ACCT-CURR-CYC-CREDIT := ACCT-CURR-CYC-CREDIT + DALYTRAN-AMT
                 ELSE →  ACCT-CURR-CYC-DEBIT  := ACCT-CURR-CYC-DEBIT  + DALYTRAN-AMT
```

In the shipped feed, type `01` (`Purchase`, per `app/data/ASCII/trantype.txt:1`) carries
positive amounts and type `03` (`Credit`, i.e. a refund, `trantype.txt:3`) carries negative
ones. So **purchases accumulate into the field called CYC-CREDIT and refunds into the field
called CYC-DEBIT**, and because refunds are negative, `CYC-DEBIT` accumulates *negative*
numbers. The boundary is `>= 0`, so a zero-amount transaction goes to `CYC-CREDIT`. See D4.

**R18 — Reject 0109, `ACCOUNT RECORD NOT FOUND`, is set but never acted on.** If the account
`REWRITE` hits `INVALID KEY`, reason `109` is moved into `WS-VALIDATION-FAIL-REASON`
(`:555-559`). But this happens *inside* posting, after validation has already passed; the
mainline only inspects the reason code before deciding to post (`:211`), and clears it again
at the top of the next record (`:208`). **Nothing reads it.** No reject record is written, no
counter moves, the return code is unaffected, and the transaction is still written to the
transaction master by `2900` (`:442`) with the category balance already updated. See D1.

**R19 — Writing the transaction master is fatal on any non-`00` status.** Unlike the account
rewrite, `2900-WRITE-TRANSACTION-FILE` checks the file status and abends if it is not `00`
(`:562-579`). A duplicate `TRAN-ID` (status `22`) therefore **abends the job** rather than
rejecting the record. See D2.

### Rejecting a bad transaction (`2500-WRITE-REJECT-REC`, `:446-465`)

**R20 — The reject record is the input record verbatim plus an 80-byte trailer.**
`REJECT-TRAN-DATA` is the whole 350-byte `DALYTRAN-RECORD` (`:447`) and `VALIDATION-TRAILER`
is `WS-VALIDATION-TRAILER` (`:448`), which is `WS-VALIDATION-FAIL-REASON PIC 9(04)` followed
by `WS-VALIDATION-FAIL-REASON-DESC PIC X(76)` (`:180-182`). 350 + 80 = **430**, exactly the
`LRECL=430` on the DD (`POSTTRAN.jcl:34`). Because the reason is `PIC 9(04)`, it appears in
the file as `0100`, `0101`, `0102`, `0103` — zero-padded, not `100`.

The four trailers that can actually be produced are, byte for byte (reason, then the
description space-padded to 76):

| Bytes 351-354 | Bytes 355-430 |
| --- | --- |
| `0100` | `INVALID CARD NUMBER FOUND` |
| `0101` | `ACCOUNT RECORD NOT FOUND` |
| `0102` | `OVERLIMIT TRANSACTION` |
| `0103` | `TRANSACTION RECEIVED AFTER ACCT EXPIRATION` |

`0109` (`ACCOUNT RECORD NOT FOUND`) exists in the source but per R18 can never reach the
file.

**R21 — Nothing else changes for a rejected transaction.** No account update, no category
balance, no transaction master record. The money simply does not move.

### Files, open and close

**R22 — The transaction master is opened `OUTPUT`, which recreates it.** (`:256`) On z/OS
this only makes sense because `TRANBKP` has already copied the master away and re-defined it
empty (`run_posting.sh:23-26`, `TRANBKP.jcl:23-70`). Run POSTTRAN without that step and the
previous contents of the transaction master are at risk. See U1 for what I could and could
not establish about the exact VSAM behaviour.

**R23 — Any open or close failure abends the job.** Every one of the six opens (`:236-343`)
and six closes (`:582-690`) follows the same shape: status must be `00`, otherwise display a
message, display the status via `9910-DISPLAY-IO-STATUS` (`:714-727`), and call `CEE3ABD`
with abend code 999 (`:707-711`).

**R24 — There is no unit of work.** No `SYNCPOINT`, no `COMMIT`, no rollback. Each `WRITE`
and `REWRITE` stands on its own.

---

## 4. The operational contract

### What the operator sees on SYSOUT

| Line | When | Source |
| --- | --- | --- |
| `START OF EXECUTION OF PROGRAM CBTRN02C` | Always, first | `:194` |
| `TCATBAL record not found for key : <17 bytes>.. Creating.` | Once per category bucket created | `:476-477` |
| `TRANSACTIONS PROCESSED :<9 digits>` | Always, after all files are closed | `:227` |
| `TRANSACTIONS REJECTED  :<9 digits>` | Always | `:228` |
| `END OF EXECUTION OF PROGRAM CBTRN02C` | Always, last | `:232` |
| `ERROR OPENING …` / `ERROR READING …` / `ERROR WRITING …` / `ERROR CLOSING …` then `FILE STATUS IS: NNNN` then `ABENDING PROGRAM` | Any I/O failure | `:236-343`, `:562-579`, `:582-690`, `:707-727` |

The counters are `PIC 9(09)` (`:185-186`), so they display as nine zero-padded digits with
no separator after the colon: `TRANSACTIONS PROCESSED :000000300`.

### Return code

| RC | Meaning | Source |
| --- | --- | --- |
| `0` | Ran to completion, nothing rejected | default; never explicitly set |
| `4` | Ran to completion, **at least one transaction was rejected** | `:229-231` |
| U999 abend | Any I/O error on any of the six files | `:707-711` |

The return code is set *after* every file has been closed (`:221-231`), so an RC of 4 always
means a clean, complete run — the outputs are all valid, some transactions just did not
post. There is no distinct return code for "many rejects" or for the R18 silent failure.

### What is expected of the operator and downstream jobs

- **RC=4 is not a failure.** Nothing in the repository conditions on it: `POSTTRAN.jcl` has
  no `COND` parameter on the step, and neither do the jobs the schedule runs next
  (`TRANIDX`, `OPENFIL`). The batch stream continues regardless.
- **`TRANFILE` is consumed downstream.** `TRANIDX` builds the alternate index over it
  (`run_posting.sh:30`), `OPENFIL` hands it back to CICS, and `COMBTRAN` merges it with
  the interest transactions and the previous backup (`COMBTRAN.jcl:22-48`).
- **`DALYREJS` is consumed by nobody.** I grepped every `.jcl`, `.JCL` and `.cbl` under
  `app/`: the only references to `DALYREJS` are the GDG definition (`app/jcl/DALYREJS.jcl`),
  the DD in `POSTTRAN.jcl`, and CBTRN02C itself. There is no report, no re-drive job, no
  reconciliation step. The GDG is defined `LIMIT(5) SCRATCH` (`DALYREJS.jcl:23-28`), so **the
  rejects are silently discarded after five nightly runs**. Whatever process exists to chase
  rejected transactions is manual and outside this repository. See D6.
- **`ACCTFILE` and `TCATBALF` are updated in place**, so the next job and the CICS online
  screens see the new balances as soon as `OPENFIL` runs.

---

## 5. Rules the code implies but never states

These are real behaviours a business analyst would want written down, that no comment,
copybook or JCL in the repository mentions.

**I1 — Posting is order-dependent.** Because the credit-limit test reads account fields that
earlier transactions in the same run have already changed (R8, R17), the *sequence* of
records in `DALYTRAN` is part of the business logic. Two files with the same transactions in
a different order can post different numbers of them. Nothing sorts the daily file before
`POSTTRAN` runs (`run_posting.sh:13-34`).

**I2 — "Credit limit" here means "spend in this cycle", not "total exposure".** R8's formula
excludes `ACCT-CURR-BAL`. An account already deep over its limit will accept new charges as
long as the current cycle's activity is small. See D3.

**I3 — Refunds can never be rejected for being over the limit.** A negative amount reduces
`WS-TEMP-BAL`, so R8 always passes for a credit. That is almost certainly desirable, but it
is emergent, not stated.

**I4 — A transaction dated on the expiry date is accepted; one dated the next day is not.**
R9 uses `>=`. The boundary is inclusive.

**I5 — The account's status is never checked.** `ACCT-ACTIVE-STATUS` (`CVACT01Y.cpy:6`) is
read into working storage and never referenced. Closed, frozen and blocked accounts post
exactly like live ones.

**I6 — The card itself is never checked.** `CARDDAT` is not a DD on this step. A cancelled
or expired *card* whose cross-reference row still exists will post.

**I7 — Type and category codes are never validated.** `trantype.txt` and `trancatg.txt` are
not inputs to this step. A transaction with a type code of `ZZ` will happily create a new
category-balance bucket keyed `ZZ`.

**I8 — There is no duplicate detection.** The only thing that stops the same transaction
posting twice is the KSDS key on the transaction master, and hitting it abends the job
rather than rejecting the record (R19, D2).

**I9 — Zero-amount transactions post normally**, adding nothing to the balance but creating
a transaction-master record and (if new) a category bucket. `>= 0` in R17 sends them to
`CYC-CREDIT`.

**I10 — Numeric fields are trusted.** `DALYTRAN-CAT-CD` `9(04)` and `DALYTRAN-MERCHANT-ID`
`9(09)` are never tested with `IS NUMERIC`. On IBM Enterprise COBOL, non-numeric content in
a `PIC 9` field used as part of a key produces implementation-defined behaviour rather than
a clean reject. See U2.

**I11 — The program has no idea what day it is.** No PARM, no run date. "Today's file" is
whatever is on the `DALYTRAN` DD. Re-running the job with yesterday's file would re-post
yesterday's transactions (and then abend on the first duplicate transaction id, per R19).

---

## 6. Findings: what looks like a defect, what looks intentional

Severity is my judgement of business impact, not of how hard it is to fix.

### D1 — A failed account update is silently swallowed. **Severity: high. This is a defect.**

**Evidence:** `:555-559` sets reason `109` on `INVALID KEY`; the only reader of that field is
`:211`, which has already run; `:208` clears it before the next record. Posting continues to
`2900-WRITE-TRANSACTION-FILE` (`:442`) regardless, and the category balance was already
updated at `:440`.

**Business impact:** a customer's transaction appears on their statement and in their
category totals, but their account balance is never changed — the bank has recorded the
purchase and forgotten to charge for it — and no operator ever finds out, because the
counters, the SYSOUT and the return code are all unaffected.

Note this is genuinely unreachable in normal operation: the account was read successfully
seconds earlier at `:395`, so `INVALID KEY` on the rewrite means the record vanished
mid-run. The defect is not that it happens often; it is that when it happens the job says
everything is fine.

### D2 — A duplicate transaction id abends the job mid-file. **Severity: high. Defect.**

**Evidence:** `2900` treats any status other than `00` as fatal (`:566-578`); a duplicate key
on a KSDS `WRITE` gives status `22`. Contrast `2700-UPDATE-TCATBAL`, which explicitly
tolerates the not-found status `23` (`:481`) — the author knew how to accept an expected
status and did not do so here.

**Business impact:** one duplicated record in an upstream feed takes the whole nightly
posting run down partway through, at which point the account master and category balances
have been updated for every transaction so far but the run cannot simply be restarted
(D7) — so the overnight batch misses its window and needs manual recovery.

### D3 — The credit-limit check ignores the customer's actual balance. **Severity: high. I cannot tell whether this is a defect or a deliberate simplification.**

**Evidence:** `:403-405` uses only `ACCT-CURR-CYC-CREDIT`, `ACCT-CURR-CYC-DEBIT` and the
transaction amount. `ACCT-CURR-BAL` (`CVACT01Y.cpy:7`) is updated at `:547` but never
consulted.

**Business impact:** an account carrying a balance at or over its credit limit will still
accept new charges every night, as long as that night's activity is under the limit — the
credit limit stops controlling exposure.

**Why I cannot call it:** CardDemo is a demonstration application, and CBACT04C zeroes both
cycle fields at the end of each billing cycle (`CBACT04C-EXPLAINED.md`, rule R7), which is
consistent with an intentional "authorise against this cycle's spend" design. I have found
no comment or document in the repository stating the intended rule either way.

### D4 — `CYC-CREDIT` and `CYC-DEBIT` are populated by the sign of the amount, which inverts their names. **Severity: medium. Probably intentional, poorly named.**

**Evidence:** `:548-552` splits on `DALYTRAN-AMT >= 0`. The shipped feed has purchases
(type `01`) positive and credits (type `03`) negative (`app/data/ASCII/dailytran.txt`,
`trantype.txt:1,3`), so purchases land in `CYC-CREDIT`. Because refunds are negative,
`CYC-DEBIT` accumulates negative values, and R8's `CYC-CREDIT − CYC-DEBIT` therefore *adds*
the magnitude of refunds to the over-limit figure.

**Business impact:** a customer who returns goods has the refund counted **against** their
available limit rather than restoring it, making them more likely to be rejected for
over-limit on the same night.

That last consequence looks wrong to me, but the split itself (by sign, at zero) is a
consistent design; I have flagged it as medium and separated the naming from the arithmetic.

### D5 — The over-limit working field is one digit narrower than the fields feeding it. **Severity: medium. Defect.**

**Evidence:** `WS-TEMP-BAL PIC S9(09)V99` (`:187`) versus `PIC S9(10)V99` for the credit
limit and both cycle fields (`CVACT01Y.cpy:8,13,14`). `COMPUTE` at `:403` has no
`ON SIZE ERROR`, so a result of a billion or more is truncated on the left rather than
raising an error.

**Business impact:** on a commercial account with a limit of a billion or more, a
transaction that should be rejected as over-limit can be silently accepted (the truncated
figure compares small). Not reachable with the shipped sample data, whose limits are five
figures.

### D6 — Rejects are written and then thrown away. **Severity: medium. Looks like an incomplete implementation, not a coding defect.**

**Evidence:** no consumer of `DALYREJS` anywhere under `app/` (grepped); GDG limit 5 with
`SCRATCH` (`DALYREJS.jcl:23-28`).

**Business impact:** a transaction the customer believes went through is dropped, and the
only trace disappears after five nights. Note that on a real installation an operator
process outside this repository may well pick these up.

### D7 — The job is not restartable. **Severity: high. Defect (of design).**

**Evidence:** no commit point or checkpoint anywhere (R24); the three updates per
transaction happen in three separate unprotected I/O operations (`:440-442`); the
transaction master is opened `OUTPUT` (`:256`) so a restart from record 1 would begin by
discarding the records already written, while the account master and category balances keep
the updates already applied.

**Business impact:** if the job abends partway (see D2), simply resubmitting it double-counts
every transaction that had already posted. Recovery requires restoring the account master
and category balance files from backup first — which is exactly what `ACCTFILE` and
`TCATBALF` refresh jobs do in `run_posting.sh:17-20`, so the recovery procedure exists, but
nothing in the program or the JCL enforces it.

### D8 — Only one reject reason survives when several apply. **Severity: low. Defect, but cosmetic.**

**Evidence:** `:407-420` — the expiry check overwrites `WS-VALIDATION-FAIL-REASON`
unconditionally, so a transaction that is both over-limit and past expiry is reported as
`0103` only.

**Business impact:** reject analysis undercounts over-limit rejections. No money is affected.

### D9 — `9300-DALYREJS-CLOSE` reports the wrong file status. **Severity: low. Defect, copy-paste.**

**Evidence:** `:649` moves `XREFFILE-STATUS` to `IO-STATUS` inside the *rejects* file close
error path; every sibling paragraph moves its own status (`:594`, `:612`, `:631`, `:667`,
`:686`).

**Business impact:** none to customers. If closing the rejects file ever fails, the operator
is shown a misleading — probably `00` — file status while diagnosing an abend.

### D10 — `TRANSACTIONS PROCESSED` counts records read, not records posted. **Severity: low. Intentional, but the label misleads.**

**Evidence:** `:206` versus `:227`. See R2.

### Intentional, for the record

- Opening the transaction master `OUTPUT` (R22) — intentional, given `TRANBKP` runs first,
  though fragile (U1).
- Tolerating file status `23` on the category balance read (`:481`) — intentional, that is
  how new buckets get created.
- `>=` in the expiry test (R9) and `>= 0` in the sign split (R17) — deliberate boundary
  choices; both are defensible.
- No PARM (`POSTTRAN.jcl:23`) — intentional; the program is driven entirely by its input file.

---

## 7. Things I am not sure about

**U1 — What `OPEN OUTPUT` actually does to a non-empty VSAM KSDS on z/OS.** For a cluster
defined with `REUSE` it resets the file to empty; without `REUSE` the behaviour differs
between "reset" and "open failure" depending on the access method and share options, and I
do not have a mainframe to test on. `TRANFILE.jcl:49-63` defines the cluster **without**
`REUSE` and with `SHAREOPTIONS(2 3)`. Since `TRANBKP` always re-defines the master empty
immediately before POSTTRAN, the question may never arise in practice — but I cannot state
what happens if someone runs POSTTRAN on its own. **This needs a mainframe to settle.**

**U2 — What IBM Enterprise COBOL does with non-numeric data in a `PIC 9` field** used as a
key component (I10). GnuCOBOL and Enterprise COBOL differ here, so anything my differential
harness (Phase 4) shows about it is evidence about GnuCOBOL, not about production.

**U3 — Whether the mixed timestamp formats in the transaction master are intended.**
`TRAN-ORIG-TS` keeps the feed's `YYYY-MM-DD HH:MM:SS.ffffff` and `TRAN-PROC-TS` is written
as DB2 `YYYY-MM-DD-HH.MM.SS.hh0000` (R12). Whether downstream consumers cope is outside this
program; I have not traced every reader of the transaction master.

**U4 — Whether `CBPAUP0J`, which the schedule runs immediately before POSTTRAN
(`CardDemo.ca7:43-70`), prepares the daily transaction file.** There is no `CBPAUP0J` member under
`app/jcl/`, so I cannot say what CBTRN02C's input actually is at run time.

**U5 — Whether an operator process outside this repository consumes `DALYREJS`** (D6). The
absence of a consumer in the repository is not proof that none exists on the installation.

**U6 — Exact counts for the shipped sample data.** I have *not* run the program at the time
of writing this document. A script I wrote that re-implements R6–R9 over
`app/data/ASCII/dailytran.txt` predicts 262 posted, 38 rejected, all with reason `0102`, and
therefore RC=4. That is a prediction from my own reading of the rules, not an observation.
Phase 4 of this exercise compiles the real COBOL with GnuCOBOL and reports what it actually
does; treat those numbers, not these, as evidence.

> **Resolved in Phase 4.** The unmodified COBOL, compiled with GnuCOBOL 3 and run over all 300
> records, reports `TRANSACTIONS PROCESSED :000000300`, `TRANSACTIONS REJECTED :000000038` and
> return code 4, with all 38 rejects carrying reason `0102`. The prediction was right, but it is
> the run that is the evidence. See [COBOL-PARITY.md](COBOL-PARITY.md#cbtrn02c-the-nightly-posting-job).

**U7 — The trailing `FILLER` of a newly created category-balance bucket (severity: low, found
in Phase 4).** `2700-A-CREATE-TCATBAL-REC` (`app/cbl/CBTRN02C.cbl:503-510`) does
`INITIALIZE TRAN-CAT-BAL-RECORD`, and `INITIALIZE` leaves `FILLER` untouched by definition, so
the last 22 bytes of `CVTRA01Y` in a brand-new bucket are whatever the record area last held —
in practice the filler of the previous bucket read. No program reads the field, so no customer
is affected, but the bytes written to a production dataset are compiler- and runtime-dependent
and I cannot say from the repository what IBM Enterprise COBOL puts there. Under GnuCOBOL they
come out as zeros, matching the shipped data.

---

## 8. Quick reference

**Reject reason codes**

| Code (as written) | Text (76 bytes, space-padded) | Trigger | Source |
| --- | --- | --- | --- |
| `0100` | `INVALID CARD NUMBER FOUND` | Card not in `XREFFILE` | `:385-387` |
| `0101` | `ACCOUNT RECORD NOT FOUND` | Account id from the xref not in `ACCTFILE` | `:397-399` |
| `0102` | `OVERLIMIT TRANSACTION` | `ACCT-CREDIT-LIMIT < CYC-CREDIT − CYC-DEBIT + AMT` | `:410-412` |
| `0103` | `TRANSACTION RECEIVED AFTER ACCT EXPIRATION` | `ACCT-EXPIRAION-DATE < ORIG-TS(1:10)` as text | `:417-419` |
| `0109` | `ACCOUNT RECORD NOT FOUND` | Account `REWRITE` failed — **never written to the file**, see D1 | `:556-558` |

**Effect on each file, per outcome**

| | `TRANFILE` | `ACCTFILE` | `TCATBALF` | `DALYREJS` |
| --- | --- | --- | --- | --- |
| Posted | record written | balance + cycle field updated | bucket updated or created | — |
| Rejected | — | — | — | 430-byte record written |
| Account rewrite failed (D1) | record written | **not updated** | bucket updated or created | — |
