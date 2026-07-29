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
