#!/usr/bin/env python3
"""Derives the input datasets of the parity scenarios from the sample datasets in app/data/ASCII.

The bundled `tcatbal.txt` holds a zero TRAN-CAT-BAL in all 50 records and every account has a blank
ACCT-GROUP-ID, so a run over the sample data as it is computes 0.00 interest for every record and
only ever takes the DEFAULT disclosure group path. The `seeded` and `abend` scenarios therefore
derive their own copies of the input files, which both the COBOL program and the Java port are then
fed, byte for byte identically. The repository datasets are never modified.

Nothing here reimplements the program: it only writes input records.

    seed-datasets.py <sample-dir> <out-dir> <scenario>
"""

import sys
from decimal import Decimal
from pathlib import Path

POSITIVE = "{ABCDEFGHI"
NEGATIVE = "}JKLMNOPQR"

# Account group ids taken in turn, by account id modulo 5. Blank is what the sample data holds and
# makes 1200-GET-INTEREST-RATE fall back to the DEFAULT group; A000000000 and ZEROAPR are groups
# that exist in discgrp.txt, the second one with a 0.00 rate for every category.
GROUPS = ["A000000000", "", "", "ZEROAPR", ""]

# TRAN-CAT-BAL seeded per (account, category), chosen to cover a positive and a negative balance,
# balances whose monthly interest is a repeating decimal that has to be truncated, a balance small
# enough that the interest truncates to zero and a balance close to the PIC S9(09)V99 limit.
BALANCES = [
    "1234.57", "-2345.68", "0.05", "-0.05", "999999999.99",
    "100.01", "-100.09", "33333.33", "7.77", "-9999.99",
]


def put_decimal(value, length, scale=2):
    """PIC S9(n)V(scale) DISPLAY with a trailing zone overpunch sign."""
    scaled = Decimal(value).scaleb(scale).to_integral_value(rounding="ROUND_DOWN")
    negative = scaled < 0
    digits = str(abs(int(scaled))).rjust(length, "0")[-length:]
    zone = (NEGATIVE if negative else POSITIVE)[int(digits[-1])]
    return digits[:-1] + zone


def tcatbal_record(acct_id, type_cd, cat_cd, balance, filler):
    return f"{acct_id:011d}{type_cd}{cat_cd}{put_decimal(balance, 11)}{filler}"


def seed_tcatbal(sample, out, scenario):
    """One record per (account, category), with the categories of an account grouped together."""
    filler = "0" * 22
    records = []
    for line in sample.read_text(encoding="latin-1").splitlines():
        line = line.replace("\r", "")
        if not line:
            continue
        acct_id = int(line[0:11])
        categories = ["0001"]
        if acct_id % 3 == 0:
            categories.append("0002")
        if acct_id % 7 == 0:
            categories.append("0003")
        for index, cat_cd in enumerate(categories):
            balance = BALANCES[(acct_id + index) % len(BALANCES)]
            records.append(tcatbal_record(acct_id, "01", cat_cd, balance, filler))
    if scenario == "abend":
        # An account id that has no ACCTFILE record, first in key sequence, so 1100-GET-ACCT-DATA
        # takes its INVALID KEY path on the very first record read and the program abends.
        records.insert(0, tcatbal_record(0, "01", "0001", "1234.57", filler))
    out.write_text("\n".join(sorted(records)) + "\n", encoding="latin-1")


def seed_acctdata(sample, out):
    """Rewrites only ACCT-GROUP-ID (bytes 113-122) of every account record."""
    records = []
    for line in sample.read_text(encoding="latin-1").splitlines():
        line = line.replace("\r", "").ljust(300)[:300]
        acct_id = int(line[0:11])
        group = GROUPS[acct_id % len(GROUPS)].ljust(10)
        records.append(line[:112] + group + line[122:])
    out.write_text("\n".join(records) + "\n", encoding="latin-1")


def main():
    if len(sys.argv) != 4:
        print(__doc__)
        return 2
    sample_dir, out_dir, scenario = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3]
    out_dir.mkdir(parents=True, exist_ok=True)

    for name in ("tcatbal.txt", "cardxref.txt", "discgrp.txt", "acctdata.txt"):
        text = (sample_dir / name).read_text(encoding="latin-1").replace("\r", "")
        (out_dir / name).write_text(text, encoding="latin-1")

    if scenario == "sample":
        return 0
    if scenario not in ("seeded", "abend"):
        print(f"unknown scenario: {scenario}")
        return 2

    seed_tcatbal(sample_dir / "tcatbal.txt", out_dir / "tcatbal.txt", scenario)
    seed_acctdata(sample_dir / "acctdata.txt", out_dir / "acctdata.txt")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
