# Proving the Java matches the COBOL

The unit tests in `java/cbact04c` prove the Java does what *we read* CBACT04C to do. That is not the
same as proving it does what CBACT04C *does*. This harness closes that gap: it compiles the real
`app/cbl/CBACT04C.cbl` — unmodified — with [GnuCOBOL](https://gnucobol.sourceforge.io/), runs both
programs over identical inputs, and diffs their output files byte for byte.

```bash
sudo apt-get install -y gnucobol3      # see "Toolchain" below
./scripts/cobol-parity/run-parity.sh
```

## Result

```
Scenario: shipped sample data (all balances 0.00)
  SYSTRAN (transactions):    IDENTICAL: all 50 records match byte for byte (timestamps masked)
  ACCTFILE (account master): IDENTICAL: all 50 records match byte for byte

Scenario: every category balance set to 10,000.00
  SYSTRAN (transactions):    IDENTICAL: all 50 records match byte for byte (timestamps masked)
  ACCTFILE (account master): EXPECTED DIFFERENCE: 1 of 50 records differ
    record 50: ACCT-CURR-BAL: COBOL '00000004920{' vs Java '00000006170{'

Scenario: 10,000.00 balances, Java in bug-for-bug mode
  SYSTRAN (transactions):    IDENTICAL: all 50 records match byte for byte (timestamps masked)
  ACCTFILE (account master): IDENTICAL: all 50 records match byte for byte
```

Two things worth pulling out.

**The 350-byte transaction records are byte-identical**, including the zoned-decimal amounts with
their EBCDIC-derived sign overpunches, the `9(11)` account ids, the generated `TRAN-ID`
(`2022071800` + a 6-digit sequence), and the DB2-format timestamps. That is now a measured fact, not
a claim about the code.

**The one difference is the defect we flagged, now demonstrated rather than asserted.**
[`CBACT04C-EXPLAINED.md`](CBACT04C-EXPLAINED.md) noted that the `ELSE PERFORM 1050-UPDATE-ACCOUNT`
branch at `app/cbl/CBACT04C.cbl:219-221` looks unreachable, so the last account in the file would get
its interest transactions written but never get its balance updated. The real COBOL leaves account
`00000000050` at `492.00`; the Java posts the interest and leaves it at `617.00`. The COBOL updated 49
of 50 accounts, the Java 50 of 50 — 125.00 of interest that the mainframe bills the customer for and
never adds to the balance. Scenario C runs the Java with `--emulate-final-account-quirk`, which
reproduces the defect deliberately, and then all 50 records match. So the harness proves the port is
faithful *and* isolates the single intentional deviation.

## What the harness does

| Step | File | Notes |
| --- | --- | --- |
| Load the flat samples into indexed (VSAM-equivalent) files | `LOADVSAM.cbl` | Stands in for the `IDCAMS REPRO` in `app/jcl/TCATBALF.jcl` etc. Uses the production copybook layouts, so the keys match CBACT04C's `SELECT` clauses exactly. |
| Call CBACT04C with the JCL's PARM | `RUNINTC.cbl` | `//STEP15 EXEC PGM=CBACT04C,PARM='2022071800'`. CBACT04C has `PROCEDURE DIVISION USING`, so it is compiled as a module and called, exactly as z/OS invokes it. |
| Unload the rewritten account master | `UNLDACCT.cbl` | So the file CBACT04C `REWRITE`s can be diffed. |
| Compare | `compare.py` | Normalises record framing, masks the timestamps, and reports the first differing field by copybook name. |

Nothing in `app/` is touched. The three helper programs live in `scripts/cobol-parity/` and are test
scaffolding, not part of CardDemo.

## The two things that had to be settled to get a clean diff

**Sign representation.** The first run showed all 50 transactions and 49 of 50 accounts differing —
in every case only in the last byte of a signed numeric field. GnuCOBOL defaults to its native ASCII
sign convention, while the sample data in `app/data/ASCII` was converted from EBCDIC datasets and
carries the mainframe overpunch (`{` = +0 … `I` = +9, `}` = -0 … `R` = -9). Compiling with
`-fsign=EBCDIC` makes GnuCOBOL store signs the way z/OS does, and the difference disappears. This is
a property of the compiler on Linux, not of the two programs — but it is exactly the class of bug
this harness exists to catch, and it is worth noticing that the Java had the host convention right.

**Timestamps.** Both programs stamp `TRAN-ORIG-TS` and `TRAN-PROC-TS` from the system clock at run
time (`FUNCTION CURRENT-DATE` in the COBOL), so those 52 bytes can never match across two runs. They
are masked in the diff and checked separately: both sides must produce a well-formed
`YYYY-MM-DD-HH.MM.SS.mmm000` value, and `ORIG` must equal `PROC`.

## Toolchain

GnuCOBOL 3 with an indexed-file handler is required, because four of the five files are VSAM KSDS.

```bash
sudo apt-get install -y gnucobol3
cobc --info | grep "indexed file handler"     # must not say "disabled"
```

On Ubuntu, the `gnucobol4` package is built **without** the indexed handler
(`indexed file handler: disabled`) and cannot run this harness; `gnucobol3` is built with BDB. If
`gnucobol4` is already installed the two conflict:

```bash
sudo apt-get remove -y gnucobol4 libcob5-dev libcob5
sudo apt-get install -y gnucobol3
```

macOS: `brew install gnu-cobol` (Homebrew's build includes an indexed handler).

CI runs the harness on every PR in the `COBOL/Java differential test` job, pinned to `ubuntu-22.04`
for the `gnucobol3` package, and uploads the compared files as the `cobol-parity-output` artifact.

## Honest limits

- GnuCOBOL is not IBM Enterprise COBOL. It is a very good stand-in for `COMPUTE`/`MOVE`/`PIC`
  semantics on a batch program with no CICS or DB2, and the `-fsign=EBCDIC` result above shows the
  representation is being modelled faithfully — but a genuine z/OS baseline run is still the gold
  standard, and this is not that. What the harness removes is the risk of *misreading* the COBOL.
- Data is ASCII-converted here; a real baseline would compare against EBCDIC datasets.
- Coverage is only as good as the input data. Two scenarios (50 accounts, one rate) exercise the
  DEFAULT-group fallback, the control break and the account rewrite, but not negative balances,
  missing accounts, or a populated `ACCT-GROUP-ID`. Generating adversarial inputs and running both
  sides over them is the natural next step.
- `SYSOUT` is not compared. The COBOL `DISPLAY`s each input record; the Java prints a summary. Only
  the data files are held to byte equality.

---

# CBTRN02C: the nightly posting job

Same idea, four output files instead of two, and this time with adversarial inputs as well as the
shipped data.

```bash
sudo apt-get install -y gnucobol3
./scripts/cobol-parity/run-posting-parity.sh
```

## Result

```
=== Scenario: the 300 shipped records in app/data/ASCII
  COBOL rc=4   Java rc=4
  COBOL TRANSACTIONS PROCESSED :000000300
  COBOL TRANSACTIONS REJECTED  :000000038
  Java  TRANSACTIONS PROCESSED :000000300
  Java  TRANSACTIONS REJECTED  :000000038
  TRANFILE: 262 records identical (TRAN-PROC-TS masked, format verified on both sides)
  DALYREJS: 38 records identical
  ACCTFILE: 50 records identical
  TCATBALF: 100 records identical
  reject reasons (COBOL): 0102 x38

=== Scenario: adversarial feed: every reject reason and every boundary
  COBOL rc=4   Java rc=4
  COBOL TRANSACTIONS PROCESSED :000000014
  COBOL TRANSACTIONS REJECTED  :000000006
  Java  TRANSACTIONS PROCESSED :000000014
  Java  TRANSACTIONS REJECTED  :000000006
  TRANFILE: 8 records identical (TRAN-PROC-TS masked, format verified on both sides)
  DALYREJS: 6 records identical
  ACCTFILE: 50 records identical
  TCATBALF: 51 records identical
  reject reasons (COBOL): 0100 x1, 0101 x1, 0102 x2, 0103 x2
```

**The real numbers for the shipped data**, from the unmodified COBOL rather than from a reading of
it: 300 transactions read, **262 posted, 38 rejected, every one of them reason `0102`
(`OVERLIMIT TRANSACTION`), return code 4**. The account master keeps its 50 records; the category
balance file grows from 50 buckets to 100, because none of the 50 accounts had a type-`03` bucket
before tonight.

Every one of the four output files matches byte for byte on both sides, and both programs return the
same return code. The Java runs with `--bug-for-bug`, which is the mode that claims to be CBTRN02C;
its default mode corrects the defects and is expected to differ. The harness prints that difference
too, for contrast: on the adversarial feed the corrected mode rejects 5 instead of 6.

## The adversarial feed

The shipped data only ever trips one reject reason, so on its own it proves very little about the
other rules. `make-posting-adversarial.py` reads the sample files and writes modified **copies**
(nothing in `app/` is touched) that put each boundary on its own account — necessary, because posting
a transaction changes the very cycle totals the next credit-limit test would measure against.

| Case | Input | COBOL and Java both |
| --- | --- | --- |
| `0100` | a card number in no cross-reference record | reject, `INVALID CARD NUMBER FOUND` |
| `0101` | a cross-reference row pointing at account `99999999999` | reject, `ACCOUNT RECORD NOT FOUND` |
| `0102` boundary | 600.00 of headroom: `599.99` / `600.00` / `600.01` | post, post, reject |
| `0103` boundary | expiry `2024-06-15`: dated the 14th / 15th / 16th | post, post, reject |
| D8 | over the limit *and* after expiry at once | reject as `0103`, the expiry check overwriting the over-limit code |
| D4 | 600.00 against a cycle of 900.00 charged and 500.00 refunded | reject as `0102` |
| R14 | a type/category pair with no existing bucket | create the bucket, 50 → 51 records |
| R17 | a `-25.00` refund and a `0.00` transaction | refund to cycle debit, zero to cycle *credit* |

D4 and D8 are the two the Java corrects by default, and the run shows the COBOL and the emulation
agreeing on the original behaviour while the corrected mode gives a different, defensible answer.

## What had to be settled to get a clean diff

**`DALYTRAN` is `ORGANIZATION SEQUENTIAL`, not line sequential** (`app/cbl/CBTRN02C.cbl:29-32`) — the
mainframe `RECFM=F` layout, 350-byte records butted together with no line endings. The harness builds
that from the text file before running the COBOL. `DALYREJS` comes back out the same way, 430 bytes
per record with no separators, which `compare-posting.py` chunks rather than splits on newlines.

**The 22-byte `FILLER` of a newly created category-balance bucket is not initialised.**
`2700-A-CREATE-TCATBAL-REC` does `INITIALIZE TRAN-CAT-BAL-RECORD`, and `INITIALIZE` leaves `FILLER`
alone by definition, so those bytes are whatever the record area last held — in practice the filler
of the last bucket that was read successfully. The first Java run wrote spaces there and 50 of the
100 records differed. The port now carries the previous record's filler forward, which is what the
COBOL is actually doing, and the file matches. **On z/OS those bytes are equally undefined**; they
happen to be zeros here because every bucket in `app/data/ASCII/tcatbal.txt` has zeros in its filler.
Nothing reads the field, so nothing depends on it — but a byte-for-byte comparison does, and this is
the sort of thing only a differential run finds.

**Only `TRAN-PROC-TS` is masked** (offset 305-330 of a posted transaction), because each side stamps
it from its own clock. It is not ignored: both sides must produce a well-formed
`YYYY-MM-DD-HH.MM.SS.mmm000`, checked separately in `compare-posting.py`. `TRAN-ORIG-TS` is *not*
masked — it is copied from the input record and must match exactly, and it does.

## What this does not prove

Everything in "Honest limits" above applies unchanged, and in particular **GnuCOBOL is not IBM
Enterprise COBOL**. Specific to this program:

- **Nothing here says what happens when a file operation fails.** Findings D1 (an account rewrite
  that returns `INVALID KEY`) and D2 (a duplicate `TRAN-ID`) are the two most serious things in
  `CBTRN02C-EXPLAINED.md`, and neither can be provoked through a normal run of either side. They are
  covered by unit tests against a stubbed file, not by this harness. Confirming what z/OS actually
  returns in those cases needs a mainframe.
- **The `OPEN OUTPUT` on the transaction master (R22) behaves differently here.** GnuCOBOL happily
  creates the indexed file; on z/OS the behaviour depends on how the KSDS was defined and on
  `TRANBKP` having emptied it first. The harness cannot test the interaction with the preceding job.
- **`SYSOUT` is not compared**, though both sides emit the same `TRANSACTIONS PROCESSED` and
  `TRANSACTIONS REJECTED` lines and the run prints both for inspection.
- **Storage-layout accidents may not carry over.** The `FILLER` finding above is a real example: the
  bytes matched because GnuCOBOL happens to keep the record area between operations the way the
  COBOL assumes. IBM's runtime is under no obligation to agree.
