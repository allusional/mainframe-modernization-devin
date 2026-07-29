#!/usr/bin/env python3
"""Diff the four files CBTRN02C writes, COBOL side against Java side, byte for byte.

  TRANFILE  350 bytes  the posted transactions
  DALYREJS  430 bytes  the rejected transactions and why
  ACCTFILE  300 bytes  the account master, rewritten in place
  TCATBALF   50 bytes  the transaction category balances

Only one field is masked: TRAN-PROC-TS at offset 305-330 of a posted transaction, which each
side stamps from its own system clock at the moment it runs and so can never match across two
runs. It is not ignored - its format is checked separately, on both sides.

Usage: compare-posting.py <scenario-dir>
Exit status is 0 when every file matches.
"""

import re
import sys
from collections import Counter
from pathlib import Path

# TRAN-PROC-TS, PIC X(26) at offset 304 (0 based) of CVTRA05Y.
PROC_TS = slice(304, 330)
DB2_TIMESTAMP = re.compile(r"\d{4}-\d{2}-\d{2}-\d{2}\.\d{2}\.\d{2}\.\d{6}")

FILES = [
    ("TRANFILE", "tran.cobol", "tran.java", 350, True),
    ("DALYREJS", "rejs.cobol", "rejs.java", 430, False),
    ("ACCTFILE", "acct.cobol", "acct.java", 300, False),
    ("TCATBALF", "tcat.cobol", "tcat.java", 50, False),
]


def records(path: Path, length: int) -> list[str]:
    """Read a file as fixed length records, whether or not it has line endings.

    The COBOL writes DALYREJS as RECFM=F with no separators; the unload programs and the
    Java port write line sequential files. Both are normalised to padded records here so the
    comparison is of record content, not of line ending conventions.
    """
    if not path.exists():
        return []
    text = path.read_text("latin-1")
    if "\n" in text:
        return [line.rstrip("\r").ljust(length)[:length] for line in text.splitlines()]
    return [text[start:start + length] for start in range(0, len(text), length)]


def field_of(offset: int, length: int) -> str:
    return f"bytes {offset + 1}-{offset + length}"


def first_difference(left: str, right: str) -> str:
    for index, (a, b) in enumerate(zip(left, right)):
        if a != b:
            return f"first difference at {field_of(index, 1)}: {a!r} vs {b!r}"
    return "one record is longer than the other"


def compare(directory: Path) -> int:
    failures = 0
    for name, cobol_file, java_file, length, mask_timestamp in FILES:
        cobol = records(directory / cobol_file, length)
        java = records(directory / java_file, length)

        if mask_timestamp:
            for side, rows in (("COBOL", cobol), ("Java", java)):
                bad = [row[PROC_TS] for row in rows if not DB2_TIMESTAMP.fullmatch(row[PROC_TS])]
                if bad:
                    print(f"  {name}: {side} wrote {len(bad)} malformed TRAN-PROC-TS, "
                          f"first was {bad[0]!r}")
                    failures += 1
            cobol = [row[:PROC_TS.start] + "*" * 26 + row[PROC_TS.stop:] for row in cobol]
            java = [row[:PROC_TS.start] + "*" * 26 + row[PROC_TS.stop:] for row in java]

        if len(cobol) != len(java):
            print(f"  {name}: FAIL - COBOL wrote {len(cobol)} records, Java wrote {len(java)}")
            failures += 1
            continue

        differences = [index for index, (a, b) in enumerate(zip(cobol, java)) if a != b]
        if differences:
            index = differences[0]
            print(f"  {name}: FAIL - {len(differences)} of {len(cobol)} records differ")
            print(f"    record {index + 1}: {first_difference(cobol[index], java[index])}")
            print(f"      COBOL: {cobol[index]!r}")
            print(f"      Java : {java[index]!r}")
            failures += 1
        else:
            suffix = " (TRAN-PROC-TS masked, format verified on both sides)" if mask_timestamp else ""
            print(f"  {name}: {len(cobol)} records identical{suffix}")

    rejects = records(directory / "rejs.cobol", 430)
    if rejects:
        reasons = Counter(row[350:354] for row in rejects)
        print("  reject reasons (COBOL): "
              + ", ".join(f"{code} x{count}" for code, count in sorted(reasons.items())))
    return failures


if __name__ == "__main__":
    sys.exit(1 if compare(Path(sys.argv[1])) else 0)
