# CBACT04C — Plain-English Explanation

**Source:** `app/cbl/CBACT04C.cbl` (CardDemo sample credit-card system)
**Type:** Batch program, run from `app/jcl/INTCALC.jcl` as job `INTCALC`
**Header comment says:** "This is a interest calculator program."

---

## 1. Business function in one paragraph

Once per billing cycle, a credit-card issuer has to charge interest on the balances a
cardholder is carrying. CBACT04C is the job that does that. It walks through a file that
holds, for every account, the balance broken down by *category of spending* (regular
purchases, cash advances, convenience checks, etc.). For each of those category balances
it looks up the interest rate that applies to that customer's pricing group and that
spending category, works out one month's interest, and writes an "Interest Amount"
transaction record for it. When it has finished all the categories for an account it adds
the total interest to the account's current balance and resets the account's
cycle-to-date credit and debit totals to zero — i.e. it closes off the billing cycle.

The job produces two results:

1. **A new transaction file** containing one interest transaction per account/category with
   a non-zero rate. (This file is a new GDG generation, `AWS.M2.CARDDEMO.SYSTRAN(+1)`, and is
   later merged with the daily transactions by the `COMBTRAN` job and used to produce
   statements by `CREASTMT`.)
2. **Updated account master records** — balance increased by the interest, cycle totals zeroed.

The program name suggests fees as well as interest, and there is a paragraph called
`1400-COMPUTE-FEES`, but its entire body is the comment `* To be implemented`. **No fees are
ever calculated.** (`app/cbl/CBACT04C.cbl:518-520`)

---

## 2. The data it uses

| File (JCL DD name) | Copybook | How it's used | What it holds |
| --- | --- | --- | --- |
| `TCATBALF` — Transaction Category Balance | `CVTRA01Y` | **Read only**, front to back in key order | The driving file. One row per *account + transaction type + transaction category*, carrying the balance in that bucket. Key = account id (11 digits) + type code (2 chars) + category code (4 digits). |
| `ACCTFILE` — Account master | `CVACT01Y` | **Read and updated** (opened `I-O`) | Account balance, credit limits, open/expiry dates, cycle-to-date credit and debit, and the account's **group id** (its pricing/disclosure group). Looked up by account id. |
| `XREFFILE` — Card cross-reference | `CVACT03Y` | **Read only**, looked up by account id via an alternate index | Maps card number ↔ customer ↔ account. Used only to get *a* card number to stamp on the interest transaction. |
| `DISCGRP` — Disclosure group | `CVTRA02Y` | **Read only**, looked up by key | The rate card: for a given (account group, transaction type, transaction category) it gives the **annual** interest rate, e.g. `1500` with two implied decimals = 15.00%. Sample data in `app/data/ASCII/discgrp.txt` includes a group literally named `DEFAULT`. |
| `TRANSACT` — output transactions | `CVTRA05Y` | **Written** (opened `OUTPUT`, so it is created fresh each run) | The interest transactions this job generates, 350 bytes each. |

It also receives one **run parameter** from the JCL: a 10-character date string
(`PARM='2022071800'` in `INTCALC.jcl`). See rule R9 — it is used only to build transaction ids.

---

## 3. The business rules, in the order the program applies them

**R1 — Records are processed in account order, and "account changes" is the trigger for
settlement.** The category-balance file is read sequentially in key sequence, so all rows
for one account arrive together. The program remembers the account id of the previous row;
when a different account id appears, it treats the previous account as finished, posts its
accumulated interest (R7), resets the running total to zero, and loads the new account's
master record and cross-reference record. (`:188-206`)

**R2 — The account must exist, and so must a card cross-reference.** If the account id from
the balance file is not in the account master, or has no cross-reference entry, the program
prints "ACCOUNT NOT FOUND" and then **abends the whole job** with code 999 — it does not skip
the record and continue. (`:372-413`, `:628-632`)

**R3 — The applicable rate is looked up per spending category, not per account.** The lookup
key is the account's group id (from the account master) plus the transaction type code and
transaction category code taken from the balance row. So different kinds of balance on the
same account can be charged different rates. (`:210-213`)

**R4 — If the customer's own group has no rate on file, fall back to the group `DEFAULT`.**
A "record not found" (file status 23) on the disclosure group file is not an error: the
program prints "DISCLOSURE GROUP RECORD MISSING / TRY WITH DEFAULT GROUP CODE", substitutes
the literal group id `DEFAULT`, and re-reads. If the `DEFAULT` row is also missing, *that*
is fatal and the job abends. (`:415-460`)

**R5 — A zero rate means do nothing at all.** If the rate found is 0, no interest is
computed and no transaction is written for that balance row. (Since fees are unimplemented,
this also means the fee step is skipped, but that has no effect today.) (`:214-217`)

**R6 — Monthly interest = balance × annual rate ÷ 1200.** Dividing by 1200 is "divide by 100
to turn the rate into a fraction, then by 12 to get one month". There is no proration by
days, no compounding, and no minimum or maximum interest charge. (`:462-467`)

**R7 — When an account is finished: add the interest to the balance and close the cycle.**
The total interest for all of that account's category rows is added to `ACCT-CURR-BAL`, and
`ACCT-CURR-CYC-CREDIT` and `ACCT-CURR-CYC-DEBIT` are both set to zero. The account record is
rewritten. (`:350-370`)

**R8 — One interest transaction is written per category, with fixed classification.** Each
generated transaction is stamped: transaction type `01`, category `0005` (which is
"Interest Amount" in `app/data/ASCII/trancatg.txt`), source `System`, description
`Int. for a/c <account id>`, amount = that category's monthly interest, merchant id 0 and
merchant name/city/zip blank, card number = the card found on the cross-reference record,
and both the original and processed timestamps set to the **current system date/time** in
Db2 timestamp format. (`:473-515`, `:613-626`)

**R9 — Transaction ids are the run parameter date plus a counter.** The 10-character date
passed in via JCL PARM is concatenated with a 6-digit sequence number that starts at 0 each
run and increments once per interest transaction, giving a 16-character id such as
`2022071800000001`. (`:474-480`)

**R10 — Any unexpected I/O problem stops the job immediately.** Every open, read, write,
rewrite and close checks the file status; anything other than success (and other than the
two tolerated cases: end-of-file on the driving file, and "not found" on the disclosure
group) prints an error, prints the file status, and abends with code 999 via `CEE3ABD`.
There is no reject file and no "continue with the next record" path. (`:628-648`)

**R11 — The run is not restartable in place.** The output transaction file is opened
`OUTPUT` (and defined in the JCL as a new GDG generation), so a rerun creates a fresh file;
but the account master is updated in place as the job goes, so a job that abends halfway
leaves some accounts already updated. Nothing in the program detects or prevents
double-charging on a rerun.

---

## 4. Rules the code implies but never documents

These are behaviours a reader of the code has to infer. None of them is written down in the
program comments, the JCL, or the repo README. They are listed strongest-evidence-first.

1. **The last account in the file never gets its account record updated.** The main loop
   only calls `1050-UPDATE-ACCOUNT` when it sees a *change* of account id. When the read hits
   end-of-file the loop simply ends. There is an `ELSE PERFORM 1050-UPDATE-ACCOUNT` branch
   (`:219-221`), but it can never execute, because the loop's own `UNTIL END-OF-FILE = 'Y'`
   test has already ended the loop by then. Net effect: for the highest-numbered account,
   the interest **transactions are written** but the balance is **not** increased and the
   cycle totals are **not** reset. I am confident about the control flow; I am *not* certain
   whether this is a known/accepted quirk of the sample application rather than a defect —
   it looks like a genuine bug.
2. **The balance file must be in account-id order.** Grouping is done purely by "the account
   id changed since the last record". This holds because the file is a KSDS read in key
   sequence, but nothing validates it. If the file were ever supplied unsorted, an account
   appearing in two separate runs of records would be settled twice and its interest total
   would be split.
3. **Interest is truncated, not rounded.** The `COMPUTE` has no `ROUNDED` clause, so the
   third decimal place and beyond is discarded (toward zero) on every single category
   calculation. Systematically slightly favours the cardholder.
4. **Negative (credit) balances produce negative interest.** Balances are signed and there is
   no test for sign, so a customer in credit gets an "Interest Amount" transaction that
   reduces their balance. Whether that is intended is not stated anywhere.
5. **Account status is ignored.** `ACCT-ACTIVE-STATUS` is never examined — closed, blocked or
   inactive accounts are charged interest exactly like active ones. Likewise the credit
   limit is never consulted, so interest can push a balance past the limit silently.
6. **The interest posting date and the interest amount use two different clocks.** The
   run-parameter date only ever appears inside the transaction id; the transaction's
   original and processed timestamps come from the machine clock at the moment the record is
   written. Rerunning the same business date on a different day produces transactions dated
   with the rerun day.
7. **Transaction-id uniqueness depends on the operator passing a distinct date each run.**
   The counter restarts at zero every execution, so two runs with the same PARM value emit
   colliding ids. There is also no overflow check: past 999,999 interest transactions the
   6-digit counter wraps back to 000000 and ids repeat within the same run.
8. **"Which card" is arbitrary when an account has more than one card.** The cross-reference
   is read by the account-id alternate index and only the first matching record is taken, so
   the interest transaction is attributed to whichever card the index returns first.
9. **A missing rate row is treated as a pricing decision, but a missing `DEFAULT` row is
   treated as data corruption.** The asymmetry (R4) is deliberate in the code but the reason
   is never stated.
10. **Zeroing the cycle credit/debit totals makes this job the de facto end-of-cycle marker.**
    Nothing in the program says so, but any other process that relies on those cycle
    accumulators must run *before* INTCALC.
11. **The program assumes it is run exactly once per month per account.** The ÷12 in the
    formula is the only place the monthly assumption exists; there is no check of a
    last-interest-charged date, so running the job twice charges interest twice.
12. **Every category-balance record is echoed to the job log** (`DISPLAY TRAN-CAT-BAL-RECORD`,
    `:193`). On a production-sized file this is a very large SYSOUT volume; it reads like
    leftover debugging rather than an intentional audit trail.
13. **`WS-RECORD-COUNT` is incremented and never used** (`:192`) — no control total is
    printed or checked, so there is no record-count reconciliation despite the counter
    existing.
14. **The account id comparison mixes a numeric field with an alphanumeric one.**
    `WS-LAST-ACCT-NUM` is `PIC X(11)` initialised to spaces while `TRANCAT-ACCT-ID` is
    `PIC 9(11)`. It works in practice because the ids are zero-filled digits, but it is not
    a like-for-like comparison and is fragile.

---

## 5. Things I am unsure about / could not verify

- **Item 1 above (last account not updated).** I traced the control flow carefully and
  believe the `ELSE` branch is unreachable, but I did not compile or run the program to
  prove it. Worth confirming with a test run before treating it as a defect to fix.
- **Whether `1400-COMPUTE-FEES` was ever implemented elsewhere.** In this repository it is an
  empty stub. I did not find any other program that computes the fees the JCL comment
  ("compute interest and fees") promises, but I did not exhaustively search every program.
- **The intended meaning of the `PARM` value.** `'2022071800'` looks like `YYYYMMDD` plus two
  extra characters (possibly a cycle or run number). Nothing in the code or JCL documents its
  format, and the code treats it as an opaque 10-character string.
- **Whether rerunning INTCALC is operationally guarded.** The program itself has no guard; I
  did not review the Control-M / CA-7 scheduling definitions
  (`app/scheduler/`) closely enough to say whether the scheduler prevents a double run.
- **Rounding expectations.** I state that truncation happens (that is what the COBOL does),
  but I do not know whether the business requirement was rounding — that would be a
  requirements question, not a code question.
- **Real-world correctness of the ÷1200 formula.** It matches a simple "annual nominal rate,
  monthly charge" convention. Whether the issuer's disclosures require average-daily-balance
  or daily-periodic-rate maths instead is outside what the code can tell us.
