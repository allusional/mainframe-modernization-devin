#!/usr/bin/env python3
"""Generate a random feed for the CBTRN02C differential test.

The shipped data is one point in the input space and the adversarial feed is a handful of
points I chose, so between them they can only confirm behaviours somebody already thought of.
This generates feeds from the record grammar instead - random amounts, signs, dates, cards,
categories and account states - so the COBOL and the Java can be diffed on inputs nobody
designed. Every run is driven by a seed, so a failing feed is reproducible exactly.

Deliberately NOT generated:
  * duplicate transaction IDs. A duplicate makes CBTRN02C abend on the spot (finding D2), which
    would truncate the run and hide everything after it. IDs are unique by construction.
  * values large enough to overflow a field. The rules add the amount to balances held as
    S9(10)V99, so amounts stay inside +/- 100,000.00 and starting balances inside +/- 999,999.99.
    Overflow semantics are a real question, but a separate one from rule agreement.

Nothing under app/ is modified: the sample files are read and copies are written to the output
directory.

Usage: make-posting-fuzz.py <data-dir> <output-dir> --seed N [--transactions N] [--accounts N]
"""

import argparse
import random
import sys
from pathlib import Path

POSITIVE = "{ABCDEFGHI"
NEGATIVE = "}JKLMNOPQR"

# Categories the feed draws from. tcatbal.txt ships buckets for type 01, so these mix buckets
# that already exist with ones the run has to create (rule R21).
CATEGORIES = [("01", "0001"), ("01", "0002"), ("02", "0001"), ("05", "0100"), ("99", "9999")]


def signed(amount: str, digits: int) -> str:
    """A COBOL signed DISPLAY field: sign as an overpunch in the last byte."""
    negative = amount.startswith("-")
    text = amount.lstrip("-").replace(".", "").rjust(digits, "0")
    if len(text) != digits:
        raise ValueError(f"{amount} does not fit in {digits} digits")
    table = NEGATIVE if negative else POSITIVE
    return text[:-1] + table[int(text[-1])]


def money(rng: random.Random, low: float, high: float) -> str:
    return f"{rng.randint(int(low * 100), int(high * 100)) / 100:.2f}"


def account(record: str, *, limit, cycle_credit, cycle_debit, balance, expires) -> str:
    """Rewrite the fields of a 300 byte CVACT01Y record that the rules look at."""
    for (start, end), value in {
        (12, 24): balance,
        (24, 36): limit,
        (78, 90): cycle_credit,
        (90, 102): cycle_debit,
    }.items():
        record = record[:start] + signed(value, end - start) + record[end:]
    return record[:58] + expires.ljust(10) + record[68:]


def transaction(tran_id, card, amount, origin, type_code, category) -> str:
    record = (
        tran_id.ljust(16)
        + type_code.ljust(2)
        + category.ljust(4)
        + "POS TERM".ljust(10)
        + f"FUZZ {tran_id}".ljust(100)
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


def origin_timestamp(rng: random.Random) -> str:
    """A DB2 timestamp somewhere in a four year window, so expiry dates get straddled."""
    year = rng.randint(2022, 2026)
    month, day = rng.randint(1, 12), rng.randint(1, 28)
    return (f"{year:04d}-{month:02d}-{day:02d}-"
            f"{rng.randint(0, 23):02d}.{rng.randint(0, 59):02d}.{rng.randint(0, 59):02d}."
            f"{rng.randrange(1000000):06d}")


def expiry(rng: random.Random) -> str:
    """Mostly ahead of the feed's date window, sometimes inside it.

    If every card were equally likely to be expired, reason 0103 would swamp the run and the
    posting path would barely be exercised, so a third of accounts get an expiry date inside
    the window the origin timestamps are drawn from and the rest sit beyond it.
    """
    year = rng.randint(2022, 2026) if rng.random() < 0.34 else rng.randint(2027, 2032)
    return f"{year:04d}-{rng.randint(1, 12):02d}-{rng.randint(1, 28):02d}"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("data")
    parser.add_argument("out")
    parser.add_argument("--seed", type=int, required=True)
    parser.add_argument("--transactions", type=int, default=400)
    parser.add_argument("--accounts", type=int, default=20)
    args = parser.parse_args()

    rng = random.Random(args.seed)
    data, out = Path(args.data), Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    template = [line.ljust(300)[:300] for line in
                data.joinpath("acctdata.txt").read_text("latin-1").splitlines()]
    xref_template = [line.ljust(50)[:50] for line in
                     data.joinpath("cardxref.txt").read_text("latin-1").splitlines()]

    # Reshape real account records into random states: some with headroom, some already over
    # their limit, some expired, some carrying a refund in the cycle credit field.
    accounts, xrefs, cards = [], [], []
    for position in range(min(args.accounts, len(xref_template))):
        xref = xref_template[position]
        account_id = int(xref[25:36])
        source = next(r for r in template if int(r[:11]) == account_id)
        accounts.append(account(
            source,
            balance=money(rng, -999999.99, 999999.99),
            limit=money(rng, 500.00, 50000.00),
            # A negative cycle credit is a net refund position, which is the D4 case.
            cycle_credit=money(rng, -5000.00, 20000.00),
            cycle_debit=money(rng, -5000.00, 5000.00),
            expires=expiry(rng),
        ))
        xrefs.append(xref)
        cards.append(xref[:16])

    # A card the cross reference knows but the account master does not, so reason 0101 is
    # reachable, and a pool of cards in no cross reference record at all, for reason 0100.
    orphan = "5555444433332222"
    xrefs.append(orphan.ljust(16) + "999999999" + "99999999999" + " " * 14)
    xrefs.sort()
    unknown = [f"{rng.randrange(10 ** 16):016d}" for _ in range(4)]

    feed = []
    for sequence in range(args.transactions):
        draw = rng.random()
        if draw < 0.04:
            card = rng.choice(unknown)
        elif draw < 0.08:
            card = orphan
        else:
            card = rng.choice(cards)
        type_code, category = rng.choice(CATEGORIES)
        # A quarter refunds, a few exact zeros, the rest charges. Amounts are drawn on a log
        # scale so cents and near-limit values both come up often.
        if rng.random() < 0.03:
            amount = "0.00"
        else:
            # Log scale, so cents and near-limit amounts both come up often. A tenth of the
            # feed reaches far past any limit, which is what makes reason 0102 fire.
            exponent = rng.uniform(0, 7) if rng.random() < 0.1 else rng.uniform(0, 5.7)
            magnitude = f"{10 ** exponent / 100:.2f}"
            amount = ("-" if rng.random() < 0.25 else "") + magnitude
        feed.append(transaction(
            f"{args.seed:06d}{sequence:010d}", card, amount,
            origin_timestamp(rng), type_code, category))

    out.joinpath("acctdata.txt").write_text("\n".join(accounts) + "\n", "latin-1")
    out.joinpath("cardxref.txt").write_text("\n".join(xrefs) + "\n", "latin-1")
    out.joinpath("dailytran.txt").write_text("\n".join(feed) + "\n", "latin-1")
    print(f"seed {args.seed}: {len(feed)} transactions, {len(accounts)} accounts, "
          f"{len(xrefs)} cross reference records")
    return 0


if __name__ == "__main__":
    sys.exit(main())
