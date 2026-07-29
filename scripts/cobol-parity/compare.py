#!/usr/bin/env python3
"""Byte-for-byte comparison of the COBOL and Java output for one parity scenario.

Called by run-parity.sh with the scenario directory and the pristine account master.

The COBOL writes RECFM=F records with no separators, the Java writes the same records
newline-delimited, and the COBOL unload is LINE SEQUENTIAL (trailing blanks stripped), so
both sides are normalised to fixed-length records before comparing. The two DB2-format
timestamps in each transaction record come from the system clock in both programs, so they
are masked; their format is checked separately.
"""
import re
import sys

TRAN_LEN = 350
ACCT_LEN = 300
TS_START, TS_END = 278, 330          # TRAN-ORIG-TS + TRAN-PROC-TS
TS_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2}-\d{2}\.\d{2}\.\d{2}\.\d{6}$")

FIELDS = [                            # CVTRA05Y, for reporting a readable first difference
    ("TRAN-ID", 0, 16), ("TRAN-TYPE-CD", 16, 18), ("TRAN-CAT-CD", 18, 22),
    ("TRAN-SOURCE", 22, 32), ("TRAN-DESC", 32, 132), ("TRAN-AMT", 132, 143),
    ("TRAN-MERCHANT-ID", 143, 152), ("TRAN-MERCHANT-NAME", 152, 202),
    ("TRAN-MERCHANT-CITY", 202, 252), ("TRAN-MERCHANT-ZIP", 252, 262),
    ("TRAN-CARD-NUM", 262, 278), ("TRAN-ORIG-TS", 278, 304),
    ("TRAN-PROC-TS", 304, 330), ("FILLER", 330, 350),
]
ACCT_FIELDS = [
    ("ACCT-ID", 0, 11), ("ACCT-ACTIVE-STATUS", 11, 12), ("ACCT-CURR-BAL", 12, 24),
    ("ACCT-CREDIT-LIMIT", 24, 36), ("ACCT-CASH-CREDIT-LIMIT", 36, 48),
    ("ACCT-OPEN-DATE", 48, 58), ("ACCT-EXPIRAION-DATE", 58, 68),
    ("ACCT-REISSUE-DATE", 68, 78), ("ACCT-CURR-CYC-CREDIT", 78, 90),
    ("ACCT-CURR-CYC-DEBIT", 90, 102), ("ACCT-ADDR-ZIP", 102, 112),
    ("ACCT-GROUP-ID", 112, 122),
]


def fixed_records(path, length):
    """Read a file that is either a stream of fixed-length records or one record per line."""
    with open(path, "rb") as handle:
        raw = handle.read().decode("latin-1")
    if "\n" in raw:
        return [line.ljust(length)[:length] for line in raw.split("\n") if line.strip() != ""]
    if len(raw) % length != 0:
        raise SystemExit(f"{path}: {len(raw)} bytes is not a multiple of {length}")
    return [raw[i:i + length] for i in range(0, len(raw), length)]


def first_field_difference(fields, left, right):
    for name, start, end in fields:
        if left[start:end] != right[start:end]:
            return f"{name}: COBOL {left[start:end]!r} vs Java {right[start:end]!r}"
    return "records differ outside the known fields"


def compare(kind, fields, cobol, java, mask=None, expect_last_only=False):
    print(f"{kind}: {len(cobol)} COBOL records, {len(java)} Java records")
    if len(cobol) != len(java):
        print(f"  MISMATCH: record counts differ")
        return False

    def prepare(record):
        if mask is None:
            return record
        start, end = mask
        return record[:start] + "*" * (end - start) + record[end:]

    differing = [i for i, (c, j) in enumerate(zip(cobol, java)) if prepare(c) != prepare(j)]
    if not differing:
        print(f"  IDENTICAL: all {len(cobol)} records match byte for byte"
              + (" (timestamps masked)" if mask else ""))
        return True
    expected = expect_last_only and differing == [len(cobol) - 1]
    print(f"  {'EXPECTED DIFFERENCE' if expected else 'MISMATCH'}:"
          f" {len(differing)} of {len(cobol)} records differ")
    for index in differing[:5]:
        print(f"    record {index + 1}: {first_field_difference(fields, cobol[index], java[index])}")
    if len(differing) > 5:
        print(f"    ... and {len(differing) - 5} more")
    if expected:
        print("    ^ the documented CBACT04C defect: its end-of-file branch is unreachable,"
              " so the last account keeps its interest transactions but never gets its"
              " balance updated. The Java fixes it; run the Java with"
              " --emulate-final-account-quirk for bug-for-bug parity.")
    return expected


def main():
    directory, pristine_accounts = sys.argv[1], sys.argv[2]
    expect_last_only = "--expect-final-account-difference" in sys.argv[3:]

    cobol_tran = fixed_records(f"{directory}/SYSTRAN.cobol", TRAN_LEN)
    java_tran = fixed_records(f"{directory}/SYSTRAN.java", TRAN_LEN)
    ok = compare("SYSTRAN (transactions)", FIELDS, cobol_tran, java_tran, mask=(TS_START, TS_END))

    bad_timestamps = [
        i + 1 for i, record in enumerate(cobol_tran + java_tran)
        if not TS_PATTERN.match(record[278:304]) or record[278:304] != record[304:330]
    ]
    if bad_timestamps:
        print(f"  MISMATCH: {len(bad_timestamps)} record(s) have a malformed timestamp"
              " or ORIG != PROC")
        ok = False
    else:
        print("  timestamps: both sides write a well-formed DB2 timestamp, ORIG == PROC")

    cobol_acct = fixed_records(f"{directory}/acct.cobol.txt", ACCT_LEN)
    java_acct = fixed_records(f"{directory}/acct.java.txt", ACCT_LEN)
    ok = compare("ACCTFILE (account master)", ACCT_FIELDS, cobol_acct, java_acct,
                 expect_last_only=expect_last_only) and ok

    original = fixed_records(pristine_accounts, ACCT_LEN)
    print(f"  accounts actually changed: COBOL {sum(1 for a, o in zip(cobol_acct, original) if a != o)}"
          f", Java {sum(1 for a, o in zip(java_acct, original) if a != o)}"
          f" (of {len(original)})")

    print("  RESULT: AS EXPECTED" if ok else "  RESULT: UNEXPECTED DIFFERENCES")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
