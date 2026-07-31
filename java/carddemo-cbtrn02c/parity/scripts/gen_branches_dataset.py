#!/usr/bin/env python3
"""Generate the crafted 'branches' parity dataset.

Every CBTRN02C validation branch and both TCATBAL paths are exercised.
Records are written one per line (line == fixed-width record content);
prep_inputs.sh turns them into delimiter-free fixed-length record files.

Output: parity/data/branches/{dailytran,cardxref,acctdata,tcatbal}.txt
"""
import os
from decimal import Decimal

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(HERE), "data", "branches")

POS = "{ABCDEFGHI"
NEG = "}JKLMNOPQR"


def zoned(value, int_digits, dec_digits=2):
    """COBOL S9(int)V(dec) DISPLAY with trailing overpunched sign."""
    d = Decimal(value).scaleb(dec_digits).to_integral_value()
    digits = str(abs(int(d))).rjust(int_digits + dec_digits, "0")
    assert len(digits) == int_digits + dec_digits, f"{value} overflows PIC"
    table = POS if d >= 0 else NEG
    return digits[:-1] + table[int(digits[-1])]


def unsigned(value, digits):
    return str(value).rjust(digits, "0")


def text(value, width):
    assert len(value) <= width, value
    return value.ljust(width)


def account(acct_id, curr_bal, credit_limit, cyc_credit, cyc_debit, expiration):
    return (
        unsigned(acct_id, 11)
        + "Y"
        + zoned(curr_bal, 10)
        + zoned(credit_limit, 10)
        + zoned(credit_limit, 10)      # cash credit limit
        + "2015-01-01"                 # open date
        + expiration
        + "2015-01-01"                 # reissue date
        + zoned(cyc_credit, 10)
        + zoned(cyc_debit, 10)
        + text("12345", 10)            # addr zip
        + text("DEFAULT", 10)          # group id
        + text("", 178)
    )


def xref(card_num, cust_id, acct_id):
    return text(card_num, 16) + unsigned(cust_id, 9) + unsigned(acct_id, 11) + text("", 14)


def tcatbal(acct_id, type_cd, cat_cd, balance):
    return (
        unsigned(acct_id, 11)
        + text(type_cd, 2)
        + unsigned(cat_cd, 4)
        + zoned(balance, 9)
        + text("", 22)
    )


def dalytran(tran_id, type_cd, cat_cd, amount, card_num, orig_ts, desc):
    return (
        text(tran_id, 16)
        + text(type_cd, 2)
        + unsigned(cat_cd, 4)
        + text("POS TERM", 10)         # source
        + text(desc, 100)
        + zoned(amount, 9)
        + unsigned(123456789, 9)       # merchant id
        + text("Test Merchant", 50)
        + text("Testville", 50)
        + text("12345", 10)
        + text(card_num, 16)
        + text(orig_ts, 26)
        + text("", 26)                 # proc ts (set by the program)
        + text("", 20)
    )


CARD_OK = "4000000000000001"   # -> account 1
CARD_EXPIRED = "4000000000000002"  # -> account 2 (expired)
CARD_SMALL = "4000000000000003"   # -> account 3 (low credit limit)
CARD_SMALL_EXP = "4000000000000004"  # -> account 4 (low limit AND expired)
CARD_NO_ACCT = "4000000000000005"   # -> account 99999999999 (absent)
CARD_UNKNOWN = "4000000000009999"   # not in the xref at all

TS = "2023-05-01 10:00:00.000000"

accounts = [
    account(1, "250.00", "1000.00", "100.00", "50.00", "2099-12-31"),
    account(2, "500.00", "5000.00", "0.00", "0.00", "2020-01-01"),
    account(3, "0.00", "100.00", "0.00", "0.00", "2099-12-31"),
    account(4, "0.00", "10.00", "0.00", "0.00", "2019-01-01"),
]

xrefs = [
    xref(CARD_OK, 1, 1),
    xref(CARD_EXPIRED, 2, 2),
    xref(CARD_SMALL, 3, 3),
    xref(CARD_SMALL_EXP, 4, 4),
    xref(CARD_NO_ACCT, 5, 99999999999),
]

# Only account 1 / type 01 / category 0001 pre-exists -> update path;
# every other posted key exercises the create path.
tcatbals = [
    tcatbal(1, "01", 1, "25.00"),
    tcatbal(2, "01", 1, "10.00"),
]

trans = [
    # 1: valid post, positive amount, existing TCATBAL key -> update path
    dalytran("TRAN000000000001", "01", 1, "100.00", CARD_OK, TS, "valid purchase, tcatbal update"),
    # 2: valid post, negative amount -> cycle debit, new TCATBAL key -> create path
    dalytran("TRAN000000000002", "05", 2, "-40.00", CARD_OK, TS, "refund, tcatbal create, cycle debit"),
    # 3: card not present in the xref -> reason 100
    dalytran("TRAN000000000003", "01", 1, "10.00", CARD_UNKNOWN, TS, "invalid card number"),
    # 4: xref resolves but the account is missing -> reason 101
    dalytran("TRAN000000000004", "01", 1, "10.00", CARD_NO_ACCT, TS, "account not found"),
    # 5: projected balance above the credit limit -> reason 102
    dalytran("TRAN000000000005", "01", 1, "100.01", CARD_SMALL, TS, "overlimit transaction"),
    # 6: transaction dated after account expiration -> reason 103
    dalytran("TRAN000000000006", "01", 1, "10.00", CARD_EXPIRED, TS, "expired account"),
    # 7: overlimit AND expired -> 103 wins (expiration check runs last)
    dalytran("TRAN000000000007", "01", 1, "999.00", CARD_SMALL_EXP, TS, "overlimit and expired"),
    # 8: projected balance exactly equal to the credit limit -> accepted
    dalytran("TRAN000000000008", "02", 3, "100.00", CARD_SMALL, TS, "exactly at credit limit"),
    # 9: second posting on the key created/updated by tran 1 -> cumulative balance
    dalytran("TRAN000000000009", "01", 1, "5.25", CARD_OK, TS, "second posting on same tcatbal key"),
    # 10: zero amount -> treated as credit (>= 0)
    dalytran("TRAN000000000010", "03", 4, "0.00", CARD_OK, TS, "zero amount posting"),
]


def write(name, rows):
    path = os.path.join(OUT, name)
    with open(path, "w", newline="\n") as fh:
        for row in rows:
            fh.write(row + "\n")
    print(f"wrote {path} ({len(rows)} records of {len(rows[0])} bytes)")


os.makedirs(OUT, exist_ok=True)
write("dailytran.txt", trans)
write("cardxref.txt", xrefs)
write("acctdata.txt", accounts)
write("tcatbal.txt", tcatbals)
