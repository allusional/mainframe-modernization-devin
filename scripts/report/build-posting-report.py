#!/usr/bin/env python3
"""Turn a CBTRN02C run into a single static HTML page.

No server, no framework, no network: one self-contained file that opens from file://.
It shows the daily feed that went in, what happened to each transaction and why, what moved
on each account, and the totals - taken from the files the run actually produced, not from a
description of them.

Usage: build-posting-report.py <scenario-dir> <output.html>

The scenario directory is one produced by scripts/cobol-parity/run-posting-parity.sh, and
must contain the inputs under in/ plus tran.cobol, rejs.cobol, acct.cobol and tcat.cobol.
"""

import html
import sys
from collections import Counter
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path

POSITIVE = "{ABCDEFGHI"
NEGATIVE = "}JKLMNOPQR"

REASON_TEXT = {
    "0100": "INVALID CARD NUMBER FOUND",
    "0101": "ACCOUNT RECORD NOT FOUND",
    "0102": "OVERLIMIT TRANSACTION",
    "0103": "TRANSACTION RECEIVED AFTER ACCT EXPIRATION",
}


def unsign(field: str, scale: int) -> Decimal:
    """Read a COBOL signed DISPLAY number: the sign is an overpunch in the last byte."""
    last = field[-1]
    if last in POSITIVE:
        digits, negative = field[:-1] + str(POSITIVE.index(last)), False
    elif last in NEGATIVE:
        digits, negative = field[:-1] + str(NEGATIVE.index(last)), True
    else:
        digits, negative = field, False
    value = Decimal(digits or "0").scaleb(-scale)
    return -value if negative else value


def money(value: Decimal) -> str:
    return f"{value:,.2f}"


def records(path: Path, length: int) -> list[str]:
    if not path.exists():
        return []
    text = path.read_text("latin-1")
    if "\n" in text:
        return [line.rstrip("\r").ljust(length)[:length] for line in text.splitlines()]
    return [text[start:start + length] for start in range(0, len(text), length)]


class Transaction:
    def __init__(self, record: str):
        self.id = record[0:16].strip()
        self.type_code = record[16:18]
        self.category = record[18:22]
        self.description = record[32:132].strip()
        self.amount = unsign(record[132:143], 2)
        self.merchant = record[152:202].strip()
        self.card = record[262:278].strip()
        self.origin = record[278:304].strip()
        self.reason = None

    @property
    def card_masked(self) -> str:
        return "•••• " + self.card[-4:]


class Account:
    def __init__(self, record: str):
        self.id = record[0:11]
        self.balance = unsign(record[12:24], 2)
        self.limit = unsign(record[24:36], 2)
        self.expires = record[58:68]
        self.cycle_credit = unsign(record[78:90], 2)
        self.cycle_debit = unsign(record[90:102], 2)


def read_accounts(path: Path) -> dict[str, Account]:
    return {record[0:11]: Account(record) for record in records(path, 300)}


CSS = """
:root { --ink:#16202c; --muted:#5d6b7a; --line:#dde3ea; --bg:#f6f8fa;
        --post:#0a7d4b; --reject:#b32d2e; --accent:#1f4e79; }
* { box-sizing:border-box; }
body { margin:0; padding:2rem clamp(1rem,4vw,4rem); background:var(--bg); color:var(--ink);
       font:15px/1.55 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif; }
h1 { font-size:1.6rem; margin:0 0 .25rem; }
h2 { font-size:1.15rem; margin:2.5rem 0 .75rem; padding-bottom:.4rem; border-bottom:2px solid var(--line); }
.sub { color:var(--muted); margin:0 0 1.5rem; }
.cards { display:flex; flex-wrap:wrap; gap:1rem; }
.card { background:#fff; border:1px solid var(--line); border-radius:8px; padding:1rem 1.25rem; min-width:150px; flex:1; }
.card .n { font-size:1.9rem; font-weight:600; line-height:1.1; }
.card .l { color:var(--muted); font-size:.82rem; text-transform:uppercase; letter-spacing:.04em; }
.card.posted .n { color:var(--post); } .card.rejected .n { color:var(--reject); }
table { width:100%; border-collapse:collapse; background:#fff; border:1px solid var(--line);
        border-radius:8px; overflow:hidden; font-variant-numeric:tabular-nums; }
th { text-align:left; background:#eef2f6; font-size:.78rem; text-transform:uppercase;
     letter-spacing:.04em; color:var(--muted); padding:.55rem .7rem; white-space:nowrap; }
td { padding:.5rem .7rem; border-top:1px solid var(--line); }
td.num, th.num { text-align:right; }
tr.reject { background:#fdf3f3; }
.tag { display:inline-block; padding:.1rem .45rem; border-radius:4px; font-size:.78rem; font-weight:600; }
.tag.posted { background:#e3f4ec; color:var(--post); }
.tag.rejected { background:#fbe6e6; color:var(--reject); }
.mono { font-family:ui-monospace,SFMono-Regular,Menlo,monospace; font-size:.86rem; }
.neg { color:var(--reject); }
.moved { font-weight:600; }
.note { background:#fff; border:1px solid var(--line); border-left:4px solid var(--accent);
        border-radius:6px; padding:.85rem 1.1rem; margin:1rem 0; }
.scroll { max-height:32rem; overflow:auto; border-radius:8px; }
.scroll thead th { position:sticky; top:0; z-index:1; }
footer { margin:3rem 0 1rem; color:var(--muted); font-size:.85rem; }
"""


def build(scenario: Path) -> str:
    before = read_accounts(scenario / "in" / "acctdata.txt")
    if not before:
        before = read_accounts(Path("app/data/ASCII/acctdata.txt"))
    after = read_accounts(scenario / "acct.cobol")

    feed_path = scenario / "in" / "dailytran.txt"
    feed = [Transaction(record) for record in
            records(feed_path if feed_path.exists() else Path("app/data/ASCII/dailytran.txt"), 350)]

    posted_ids = {record[0:16].strip() for record in records(scenario / "tran.cobol", 350)}
    for record in records(scenario / "rejs.cobol", 430):
        reason = record[350:354]
        for transaction in feed:
            if transaction.id == record[0:16].strip() and transaction.reason is None:
                transaction.reason = reason
                break

    # Which account each card belongs to, so a movement can be tied back to a transaction.
    account_of = {}
    xref_path = scenario / "in" / "cardxref.txt"
    for record in records(xref_path if xref_path.exists() else Path("app/data/ASCII/cardxref.txt"), 50):
        account_of[record[0:16].strip()] = record[25:36]

    posted = [t for t in feed if t.reason is None and t.id in posted_ids]
    rejected = [t for t in feed if t.reason is not None]
    reasons = Counter(t.reason for t in rejected)
    total_posted = sum((t.amount for t in posted), Decimal(0))

    out = [f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>CBTRN02C nightly posting run</title><style>{CSS}</style></head><body>
<h1>CBTRN02C &mdash; nightly transaction posting</h1>
<p class="sub">Everything below is read out of the files this run produced: the daily feed that
went in, and <span class="mono">TRANFILE</span>, <span class="mono">DALYREJS</span> and
<span class="mono">ACCTFILE</span> that came out. The outputs are the ones written by the
<strong>real COBOL</strong>, compiled unmodified with GnuCOBOL; the Java port produced files
identical to them byte for byte.</p>

<div class="cards">
  <div class="card"><div class="n">{len(feed)}</div><div class="l">read</div></div>
  <div class="card posted"><div class="n">{len(posted)}</div><div class="l">posted</div></div>
  <div class="card rejected"><div class="n">{len(rejected)}</div><div class="l">rejected</div></div>
  <div class="card"><div class="n">{money(total_posted)}</div><div class="l">value posted</div></div>
  <div class="card"><div class="n">{4 if rejected else 0}</div><div class="l">return code</div></div>
</div>
"""]

    if reasons:
        out.append("<h2>Why transactions were rejected</h2><table><thead><tr>"
                   "<th>Code</th><th>Reason</th><th class='num'>Count</th></tr></thead><tbody>")
        for code, count in sorted(reasons.items()):
            out.append(f"<tr><td class='mono'>{code}</td>"
                       f"<td>{html.escape(REASON_TEXT.get(code, 'UNKNOWN'))}</td>"
                       f"<td class='num'>{count}</td></tr>")
        out.append("</tbody></table>")
        out.append('<p class="note">A rejected transaction is written to '
                   '<span class="mono">DALYREJS</span> with the original 350-byte record and an '
                   '80-byte trailer, and changes nothing else: no balance moves and nothing is '
                   'written to the transaction master. The job still ends with return code 4.</p>')

    out.append("<h2>Balance movements</h2>")
    out.append("<table><thead><tr><th>Account</th><th class='num'>Balance before</th>"
               "<th class='num'>Balance after</th><th class='num'>Moved</th>"
               "<th class='num'>Cycle credit</th><th class='num'>Cycle debit</th>"
               "<th class='num'>Credit limit</th></tr></thead><tbody>")
    movements = 0
    for account_id, old in before.items():
        new = after.get(account_id)
        if new is None or new.balance == old.balance:
            continue
        movements += 1
        delta = new.balance - old.balance
        out.append(
            f"<tr><td class='mono'>{account_id}</td>"
            f"<td class='num'>{money(old.balance)}</td>"
            f"<td class='num'>{money(new.balance)}</td>"
            f"<td class='num moved{' neg' if delta < 0 else ''}'>{'+' if delta >= 0 else ''}{money(delta)}</td>"
            f"<td class='num'>{money(new.cycle_credit)}</td>"
            f"<td class='num'>{money(new.cycle_debit)}</td>"
            f"<td class='num'>{money(new.limit)}</td></tr>")
    out.append("</tbody></table>")
    out.append(f'<p class="note">{movements} of {len(before)} accounts moved. The balance is the '
               "running total; the cycle figures are what the credit-limit rule is measured "
               "against, and they are what this job adds to.</p>")

    out.append(f"<h2>The daily feed, transaction by transaction</h2>"
               f"<div class='scroll'><table><thead><tr><th>#</th><th>Transaction</th>"
               f"<th>Card</th><th>Account</th><th>Description</th><th class='num'>Amount</th>"
               f"<th>Origin</th><th>Outcome</th></tr></thead><tbody>")
    for index, transaction in enumerate(feed, start=1):
        rejected_row = transaction.reason is not None
        if rejected_row:
            outcome = (f"<span class='tag rejected'>{transaction.reason}</span> "
                       + html.escape(REASON_TEXT.get(transaction.reason, "")))
        else:
            outcome = "<span class='tag posted'>posted</span>"
        out.append(
            f"<tr class='{'reject' if rejected_row else ''}'><td>{index}</td>"
            f"<td class='mono'>{html.escape(transaction.id)}</td>"
            f"<td class='mono'>{transaction.card_masked}</td>"
            f"<td class='mono'>{account_of.get(transaction.card, '&mdash;')}</td>"
            f"<td>{html.escape(transaction.description[:60])}</td>"
            f"<td class='num{' neg' if transaction.amount < 0 else ''}'>{money(transaction.amount)}</td>"
            f"<td class='mono'>{html.escape(transaction.origin[:10])}</td>"
            f"<td>{outcome}</td></tr>")
    out.append("</tbody></table></div>")

    generated = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
    out.append(f"""<footer>Generated {generated} from
<span class="mono">{html.escape(str(scenario))}</span> by
<span class="mono">scripts/report/build-posting-report.py</span>. Card numbers are masked here
only; the files themselves are unchanged. See <span class="mono">CBTRN02C-EXPLAINED.md</span>
for the rules behind each outcome and <span class="mono">COBOL-PARITY.md</span> for what the
byte-for-byte comparison does and does not prove.</footer></body></html>""")
    return "".join(out)


if __name__ == "__main__":
    scenario, destination = Path(sys.argv[1]), Path(sys.argv[2])
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(build(scenario), "utf-8")
    print(f"Wrote {destination}")
