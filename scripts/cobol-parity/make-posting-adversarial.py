#!/usr/bin/env python3
"""Build adversarial inputs for the CBTRN02C differential test.

The shipped sample data only ever trips one reject reason (0102), so on its own it proves
very little about the other rules. This generates a small feed that hits every reject reason
and sits exactly on every boundary the rule catalogue identifies, plus the two behaviours
Phase 2 put behind flags, and the account and cross reference files those cases need.

Nothing under app/ is modified: the sample files are read and copies are written to the
scenario directory given on the command line.

Usage: make-posting-adversarial.py <data-dir> <output-dir>
"""

import sys
from pathlib import Path

POSITIVE = "{ABCDEFGHI"
NEGATIVE = "}JKLMNOPQR"


def signed(amount: str, digits: int) -> str:
    """A COBOL signed DISPLAY field: sign as an overpunch in the last byte."""
    negative = amount.startswith("-")
    text = amount.lstrip("-").replace(".", "").rjust(digits, "0")
    if len(text) != digits:
        raise ValueError(f"{amount} does not fit in {digits} digits")
    table = NEGATIVE if negative else POSITIVE
    return text[:-1] + table[int(text[-1])]


def account(record: str, *, limit=None, cycle_credit=None, cycle_debit=None,
            balance=None, expires=None) -> str:
    """Rewrite the fields of a 300 byte CVACT01Y record that the rules look at."""
    fields = {
        (12, 24): balance,
        (24, 36): limit,
        (78, 90): cycle_credit,
        (90, 102): cycle_debit,
    }
    for (start, end), value in fields.items():
        if value is not None:
            record = record[:start] + signed(value, end - start) + record[end:]
    if expires is not None:
        record = record[:58] + expires.ljust(10) + record[68:]
    return record


def transaction(tran_id: str, card: str, amount: str, origin: str,
                type_code="01", category="0001", description="ADVERSARIAL CASE") -> str:
    record = (
        tran_id.ljust(16)
        + type_code.ljust(2)
        + category.ljust(4)
        + "POS TERM".ljust(10)
        + description.ljust(100)
        + signed(amount, 11)
        + "123456789"
        + "A MERCHANT".ljust(50)
        + "A CITY".ljust(50)
        + "12345".ljust(10)
        + card.ljust(16)
        + origin.ljust(26)
        + " " * 26
        + " " * 20
    )
    assert len(record) == 350, len(record)
    return record


def xref(card: str, customer: int, account_id: int) -> str:
    return card.ljust(16) + f"{customer:09d}" + f"{account_id:011d}" + " " * 14


def main() -> int:
    data, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)

    accounts = [line.ljust(300)[:300] for line in
                data.joinpath("acctdata.txt").read_text("latin-1").splitlines()]
    xrefs = [line.ljust(50)[:50] for line in
             data.joinpath("cardxref.txt").read_text("latin-1").splitlines()]

    # Each boundary gets its own account, because posting a transaction changes the very
    # cycle totals the next credit limit test would measure against.
    #
    #   limit[0..2]  1000.00 limit, 400.00 used this cycle, so exactly 600.00 of headroom
    #   dated        1000.00 limit and nothing used, expires 2024-06-15
    #   both         500.00 limit and expired in 2020, so both rules fail at once (D8)
    #   refunded     1000.00 limit, 900.00 charged and 500.00 refunded this cycle (D4)
    cards = [record[:16] for record in xrefs[:6]]
    account_ids = [int(record[25:36]) for record in xrefs[:6]]
    by_id = {int(record[:11]): index for index, record in enumerate(accounts)}

    def reshape(position, **changes):
        index = by_id[account_ids[position]]
        accounts[index] = account(accounts[index], **changes)

    for position in (0, 1, 2):
        reshape(position, limit="1000.00", balance="0.00", cycle_credit="400.00",
                cycle_debit="0.00", expires="2099-12-31")
    reshape(3, limit="1000.00", balance="0.00", cycle_credit="0.00", cycle_debit="0.00",
            expires="2024-06-15")
    reshape(4, limit="500.00", balance="0.00", cycle_credit="0.00", cycle_debit="0.00",
            expires="2020-01-01")
    reshape(5, limit="1000.00", balance="0.00", cycle_credit="900.00", cycle_debit="-500.00",
            expires="2099-12-31")

    # A card the cross reference knows about but the account master does not: reason 0101.
    orphan_card = "5555444433332222"
    xrefs.append(xref(orphan_card, 999999999, 99999999999))
    xrefs.sort()

    feed = [
        # 0100 - a card number that is in no cross reference record at all
        ("0000000000000101", "0000000000000000", "10.00", "2024-06-01-00.00.00.000000"),
        # 0101 - the cross reference points at an account the master does not have
        ("0000000000000102", orphan_card, "10.00", "2024-06-01-00.00.00.000000"),
        # 0102 boundary: 600.00 of headroom, one cent under, exactly at it, one cent over
        ("0000000000000201", cards[0], "599.99", "2024-06-01-00.00.00.000000"),
        ("0000000000000202", cards[1], "600.00", "2024-06-01-00.00.00.000000"),
        ("0000000000000203", cards[2], "600.01", "2024-06-01-00.00.00.000000"),
        # 0103 boundary: the day before expiry, the day of expiry, the day after
        ("0000000000000301", cards[3], "0.01", "2024-06-14-23.59.59.999999"),
        ("0000000000000302", cards[3], "0.01", "2024-06-15-00.00.00.000000"),
        ("0000000000000303", cards[3], "0.01", "2024-06-16-00.00.00.000000"),
        # D8 - over the limit *and* after expiry, so which reason gets reported is the test
        ("0000000000000401", cards[4], "600.00", "2024-06-16-00.00.00.000000"),
        # D4 - 600.00 fits only if the 500.00 refund freed the limit back up
        ("0000000000000501", cards[5], "600.00", "2024-06-01-00.00.00.000000"),
        # a brand new category bucket, and an existing one, for the same account
        ("0000000000000601", cards[3], "1.00", "2024-06-01-00.00.00.000000", "99", "9999"),
        ("0000000000000602", cards[3], "1.00", "2024-06-01-00.00.00.000000", "01", "0001"),
        # a refund, and a zero: R17 puts zero in the cycle *credit* field, not the debit one
        ("0000000000000701", cards[3], "-25.00", "2024-06-01-00.00.00.000000"),
        ("0000000000000702", cards[3], "0.00", "2024-06-01-00.00.00.000000"),
    ]

    out.joinpath("acctdata.txt").write_text("\n".join(accounts) + "\n", "latin-1")
    out.joinpath("cardxref.txt").write_text("\n".join(xrefs) + "\n", "latin-1")
    out.joinpath("dailytran.txt").write_text(
        "\n".join(transaction(*case) for case in feed) + "\n", "latin-1")
    print(f"Wrote {len(feed)} adversarial transactions, {len(accounts)} accounts, "
          f"{len(xrefs)} cross reference records to {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
