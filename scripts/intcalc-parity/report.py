#!/usr/bin/env python3
"""Compares a COBOL CBACT04C run with a run of its Java port and renders the parity report.

Called by run-parity.sh once per scenario with the input datasets both sides were fed, the files
dumped from the COBOL run and the files the Java run wrote:

    report.py --scenario <name>:<input-dir>:<cobol-dir>:<java-dir> [...] \
              --parm-date 2022071800 --report report.html --json report.json

Exits non-zero if anything disagrees, so the harness works as a CI gate.
"""

import argparse
import html
import json
import sys
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path

POSITIVE = "{ABCDEFGHI"
NEGATIVE = "}JKLMNOPQR"

# Byte ranges excluded from the record comparison, 1-based and inclusive, with the reason. Nothing
# else is masked: every other byte of every output record has to match exactly.
MASKS = {
    "tranfile.txt": [
        (279, 304, "TRAN-ORIG-TS: wall clock, set from FUNCTION CURRENT-DATE per transaction"),
        (305, 330, "TRAN-PROC-TS: wall clock, same timestamp as TRAN-ORIG-TS"),
    ],
    "acctfile.txt": [],
}

FILES = [
    ("tranfile.txt", "TRANFILE (TRANSACT, QSAM sequential, 350 bytes)"),
    ("acctfile.txt", "ACCTFILE (KSDS, dumped in key sequence, 300 bytes)"),
]

# The only job log lines excluded, and only in a scenario where the program abends: GnuCOBOL has no
# z/OS Language Environment, so the CALL 'CEE3ABD' of 9999-ABEND-PROGRAM cannot resolve and the
# runtime appends its own diagnostics after the last DISPLAY of the program. Everything up to and
# including 'ABENDING PROGRAM' is compared, the runtime's trailer is not.
ABEND_LINE = "ABENDING PROGRAM"


def compared_log(log):
    """The job log lines that are compared: the program's own DISPLAY output."""
    if ABEND_LINE in log:
        return log[:log.index(ABEND_LINE) + 1]
    return log


def decode(field):
    """Decodes a signed PIC S9(n)V99 DISPLAY field with a trailing zone overpunch."""
    zone = field[-1]
    if zone in POSITIVE:
        digits, sign = field[:-1] + str(POSITIVE.index(zone)), 1
    elif zone in NEGATIVE:
        digits, sign = field[:-1] + str(NEGATIVE.index(zone)), -1
    else:
        digits, sign = field, 1
    return sign * Decimal(digits) / 100


def read_records(path, length):
    if not path.exists():
        return []
    records = []
    for line in path.read_text(encoding="latin-1").splitlines():
        line = line.replace("\r", "")
        if not line:
            continue
        records.append(line.ljust(length)[:length])
    return records


def read_log(path):
    if not path.exists():
        return []
    return [line.replace("\r", "").rstrip() for line in path.read_text(encoding="latin-1").splitlines()]


def masked(record, masks):
    chars = list(record)
    for start, end, _ in masks:
        for i in range(start - 1, min(end, len(chars))):
            chars[i] = "\u00b7"
    return "".join(chars)


def compare_records(cobol, java, masks):
    """Compares two files record by record and returns the mismatching record numbers."""
    mismatches = []
    for index in range(max(len(cobol), len(java))):
        left = masked(cobol[index], masks) if index < len(cobol) else None
        right = masked(java[index], masks) if index < len(java) else None
        if left != right:
            mismatches.append({"record": index + 1, "cobol": left, "java": right})
    return mismatches


def account_view(records):
    """ACCT-ID -> the fields CBACT04C changes, from a CVACT01Y record image."""
    return {
        record[0:11]: {
            "balance": str(decode(record[12:24])),
            "cycle_credit": str(decode(record[78:90])),
            "cycle_debit": str(decode(record[90:102])),
            "group_id": record[112:122].rstrip(),
        }
        for record in records
    }


def transaction_view(records):
    """The business fields of the CVTRA05Y records written to TRANFILE."""
    return [
        {
            "id": record[0:16],
            "type": record[16:18],
            "category": record[18:22],
            "description": record[32:132].rstrip(),
            "amount": str(decode(record[132:143])),
            "card": record[262:278],
            "orig_ts": record[278:304],
        }
        for record in records
    ]


def counters(log, transactions, accounts_before, accounts_after, rc):
    """The counters of the run, derived from each side's own output only."""
    read = sum(1 for line in log if len(line) == 50 and line[0:11].isdigit() and line[11:17].isdigit())
    changed = sum(1 for key, after in accounts_after.items() if accounts_before.get(key) != after)
    return {
        "TCATBALF records read": read,
        "TRANFILE transactions posted": len(transactions),
        "ACCTFILE records updated": changed,
        "Interest posted": str(sum((Decimal(t["amount"]) for t in transactions), Decimal(0))),
        "RETURN-CODE / exit code": rc,
    }


def compare_scenario(name, input_dir, cobol_dir, java_dir):
    input_dir, cobol_dir, java_dir = Path(input_dir), Path(cobol_dir), Path(java_dir)
    accounts_before = account_view(read_records(input_dir / "acctdata.txt", 300))

    cobol_log = read_log(cobol_dir / "joblog.txt")
    java_log = read_log(java_dir / "joblog.txt")
    cobol_rc = int((cobol_dir / "rc.txt").read_text().strip())
    java_rc = int((java_dir / "rc.txt").read_text().strip())

    abended = ABEND_LINE in cobol_log
    cobol_log_compared = compared_log(cobol_log)
    java_log_compared = compared_log(java_log)
    log_mismatches = [
        {"line": i + 1,
         "cobol": cobol_log_compared[i] if i < len(cobol_log_compared) else None,
         "java": java_log_compared[i] if i < len(java_log_compared) else None}
        for i in range(max(len(cobol_log_compared), len(java_log_compared)))
        if (cobol_log_compared[i] if i < len(cobol_log_compared) else None)
        != (java_log_compared[i] if i < len(java_log_compared) else None)
    ]

    files = []
    records_compared = 0
    for filename, title in FILES:
        length = 350 if filename == "tranfile.txt" else 300
        cobol_records = read_records(cobol_dir / filename, length)
        java_records = read_records(java_dir / filename, length)
        mismatches = compare_records(cobol_records, java_records, MASKS[filename])
        records_compared += max(len(cobol_records), len(java_records))
        files.append({
            "file": filename,
            "title": title,
            "cobol_records": len(cobol_records),
            "java_records": len(java_records),
            "masks": [{"from": start, "to": end, "why": why} for start, end, why in MASKS[filename]],
            "mismatches": mismatches,
            "match": not mismatches,
        })

    cobol_transactions = transaction_view(read_records(cobol_dir / "tranfile.txt", 350))
    java_transactions = transaction_view(read_records(java_dir / "tranfile.txt", 350))
    cobol_accounts = account_view(read_records(cobol_dir / "acctfile.txt", 300))
    java_accounts = account_view(read_records(java_dir / "acctfile.txt", 300))

    cobol_counters = counters(cobol_log, cobol_transactions, accounts_before, cobol_accounts, cobol_rc)
    java_counters = counters(java_log, java_transactions, accounts_before, java_accounts, java_rc)
    # In an abend scenario the exit code is an artifact of the runtime, not of the program: GnuCOBOL
    # ends the run with 1 when CEE3ABD cannot be resolved, the Java runner exits with 999 mod 256.
    counter_keys = [key for key in cobol_counters
                    if not (abended and key == "RETURN-CODE / exit code")]
    counter_mismatches = [key for key in counter_keys if cobol_counters[key] != java_counters[key]]

    return {
        "scenario": name,
        "input_dir": str(input_dir),
        "abended": abended,
        "cobol_log": cobol_log,
        "java_log": java_log,
        "log_mismatches": log_mismatches,
        "counters": {"cobol": cobol_counters, "java": java_counters},
        "counter_mismatches": counter_mismatches,
        "files": files,
        "records_compared": records_compared,
        "transactions": {"cobol": cobol_transactions, "java": java_transactions},
        "accounts": {"before": accounts_before, "cobol": cobol_accounts, "java": java_accounts},
        "match": not log_mismatches and not counter_mismatches and all(f["match"] for f in files),
    }


def pill(match):
    return ('<span class="pill ok">match</span>' if match
            else '<span class="pill bad">differs</span>')


def esc(value):
    return html.escape("" if value is None else str(value))


def counter_cards(scenario):
    cobol, java = scenario["counters"]["cobol"], scenario["counters"]["java"]
    cards = []
    for key in cobol:
        excluded = scenario["abended"] and key == "RETURN-CODE / exit code"
        match = excluded or cobol[key] == java[key]
        cards.append(f"""
        <div class="card {'ok' if match else 'bad'}">
          <div class="card-label">{esc(key)}{' <em>(not compared)</em>' if excluded else ''}</div>
          <div class="card-values"><span>COBOL <b>{esc(cobol[key])}</b></span>
            <span>Java <b>{esc(java[key])}</b></span></div>
          <div>{pill(match)}</div>
        </div>""")
    return "".join(cards)


def transaction_rows(scenario):
    cobol = scenario["transactions"]["cobol"]
    java = scenario["transactions"]["java"]
    rows = []
    for index in range(max(len(cobol), len(java))):
        left = cobol[index] if index < len(cobol) else {}
        right = java[index] if index < len(java) else {}
        compared = {k: v for k, v in left.items() if k != "orig_ts"}
        compared_right = {k: v for k, v in right.items() if k != "orig_ts"}
        match = compared == compared_right
        rows.append(f"""
        <tr class="{'' if match else 'bad-row'}">
          <td>{index + 1}</td><td class="mono">{esc(left.get('id'))}</td>
          <td class="mono">{esc(right.get('id'))}</td>
          <td class="mono">{esc(left.get('type'))}/{esc(left.get('category'))}</td>
          <td class="mono num">{esc(left.get('amount'))}</td>
          <td class="mono num">{esc(right.get('amount'))}</td>
          <td class="mono">{esc(left.get('card'))}</td>
          <td>{esc(left.get('description'))}</td>
          <td class="mono">{esc(left.get('orig_ts'))}</td>
          <td>{pill(match)}</td>
        </tr>""")
    return "".join(rows) or '<tr><td colspan="10">No transactions posted.</td></tr>'


def account_rows(scenario):
    before = scenario["accounts"]["before"]
    cobol = scenario["accounts"]["cobol"]
    java = scenario["accounts"]["java"]
    rows = []
    for key in sorted(set(cobol) | set(java)):
        left, right, was = cobol.get(key, {}), java.get(key, {}), before.get(key, {})
        match = left == right
        changed = left != was
        if not changed and match:
            continue
        rows.append(f"""
        <tr class="{'' if match else 'bad-row'}">
          <td class="mono">{esc(key)}</td><td class="mono">{esc(was.get('group_id'))}</td>
          <td class="mono num">{esc(was.get('balance'))}</td>
          <td class="mono num">{esc(left.get('balance'))}</td>
          <td class="mono num">{esc(right.get('balance'))}</td>
          <td class="mono num">{esc(was.get('cycle_credit'))} / {esc(was.get('cycle_debit'))}</td>
          <td class="mono num">{esc(left.get('cycle_credit'))} / {esc(left.get('cycle_debit'))}</td>
          <td class="mono num">{esc(right.get('cycle_credit'))} / {esc(right.get('cycle_debit'))}</td>
          <td>{pill(match)}</td>
        </tr>""")
    return "".join(rows) or '<tr><td colspan="9">No account record was updated.</td></tr>'


def file_rows(scenario):
    rows = []
    for entry in scenario["files"]:
        masks = "<br>".join(f"{m['from']}-{m['to']}: {esc(m['why'])}" for m in entry["masks"]) or "none"
        mismatches = "".join(
            f"<div class=\"mismatch\">record {m['record']}<br>COBOL <span class=\"mono\">{esc(m['cobol'])}</span>"
            f"<br>Java&nbsp; <span class=\"mono\">{esc(m['java'])}</span></div>"
            for m in entry["mismatches"][:10])
        rows.append(f"""
        <tr class="{'' if entry['match'] else 'bad-row'}">
          <td>{esc(entry['title'])}</td>
          <td class="num">{entry['cobol_records']}</td>
          <td class="num">{entry['java_records']}</td>
          <td class="masks">{masks}</td>
          <td>{pill(entry['match'])}{mismatches}</td>
        </tr>""")
    return "".join(rows)


def log_table(scenario):
    cobol, java = scenario["cobol_log"], scenario["java_log"]
    rows = []
    for index in range(max(len(cobol), len(java))):
        left = cobol[index] if index < len(cobol) else ""
        right = java[index] if index < len(java) else ""
        skipped = index >= len(compared_log(cobol))
        bad = not skipped and left.rstrip() != right.rstrip()
        rows.append(f'<tr class="{"bad-row" if bad else "skip-row" if skipped else ""}">'
                    f'<td class="num">{index + 1}</td>'
                    f'<td class="mono">{esc(left)}</td><td class="mono">{esc(right)}</td></tr>')
    return "".join(rows)


STYLE = """
body { font-family: -apple-system, Segoe UI, Roboto, Helvetica, sans-serif; margin: 0;
       background: #f4f6f8; color: #1b2733; }
header { padding: 28px 32px; background: #10243b; color: #fff; }
header h1 { margin: 0 0 6px; font-size: 24px; }
header p { margin: 0; opacity: .8; font-size: 14px; }
.banner { padding: 20px 32px; font-size: 22px; font-weight: 700; color: #fff; }
.banner.ok { background: #1a7f47; } .banner.bad { background: #b3261e; }
main { padding: 24px 32px 60px; }
section { background: #fff; border: 1px solid #dbe2ea; border-radius: 10px; padding: 20px 22px;
          margin-bottom: 22px; }
section h2 { margin: 0 0 4px; font-size: 19px; }
section h3 { margin: 22px 0 8px; font-size: 15px; text-transform: uppercase; letter-spacing: .06em;
             color: #5a6b7d; }
.sub { color: #5a6b7d; font-size: 13px; margin: 0 0 8px; }
.cards { display: flex; flex-wrap: wrap; gap: 12px; }
.card { flex: 1 1 190px; border: 1px solid #dbe2ea; border-left-width: 5px; border-radius: 8px;
        padding: 10px 12px; background: #fbfcfd; }
.card.ok { border-left-color: #1a7f47; } .card.bad { border-left-color: #b3261e; }
.card-label { font-size: 12px; color: #5a6b7d; text-transform: uppercase; letter-spacing: .04em; }
.card-values { display: flex; gap: 14px; font-size: 13px; margin: 6px 0; }
table { border-collapse: collapse; width: 100%; font-size: 13px; }
th, td { border-bottom: 1px solid #e6ebf1; padding: 6px 8px; text-align: left; vertical-align: top; }
th { background: #eef2f6; font-size: 12px; text-transform: uppercase; letter-spacing: .04em; }
td.num, th.num { text-align: right; }
.mono { font-family: SFMono-Regular, Consolas, monospace; font-size: 12px; }
.pill { display: inline-block; padding: 2px 9px; border-radius: 999px; font-size: 11px;
        font-weight: 700; text-transform: uppercase; }
.pill.ok { background: #d8f0e2; color: #14653a; } .pill.bad { background: #fadcd9; color: #8c1d18; }
.bad-row { background: #fff5f4; }
.skip-row { background: #f4f6f8; color: #93a1b1; }
.mismatch { margin-top: 6px; font-size: 12px; color: #8c1d18; }
.masks { max-width: 460px; font-size: 12px; color: #44546a; }
.logs { max-height: 420px; overflow: auto; border: 1px solid #e6ebf1; border-radius: 8px; }
.exclusions li { font-size: 13px; margin-bottom: 4px; }
"""


def render(scenarios, parm_date, records_compared):
    overall = all(s["match"] for s in scenarios)
    sections = []
    for scenario in scenarios:
        sections.append(f"""
    <section>
      <h2>Scenario: {esc(scenario['scenario'])} {pill(scenario['match'])}</h2>
      <p class="sub">Inputs both sides were fed: <span class="mono">{esc(scenario['input_dir'])}</span>
         &middot; {'the program abends in this scenario' if scenario['abended'] else 'the program runs to completion'}</p>
      <h3>Counters</h3>
      <div class="cards">{counter_cards(scenario)}</div>
      <h3>Output files compared record by record</h3>
      <table><thead><tr><th>File</th><th class="num">COBOL records</th><th class="num">Java records</th>
        <th>Excluded byte ranges</th><th>Verdict</th></tr></thead>
        <tbody>{file_rows(scenario)}</tbody></table>
      <h3>Posted interest transactions</h3>
      <table><thead><tr><th>#</th><th>COBOL TRAN-ID</th><th>Java TRAN-ID</th><th>Type/Cat</th>
        <th class="num">COBOL amount</th><th class="num">Java amount</th><th>Card</th>
        <th>Description</th><th>COBOL TRAN-ORIG-TS (excluded)</th><th>Verdict</th></tr></thead>
        <tbody>{transaction_rows(scenario)}</tbody></table>
      <h3>Account balances before and after</h3>
      <table><thead><tr><th>ACCT-ID</th><th>Group</th><th class="num">Balance before</th>
        <th class="num">COBOL after</th><th class="num">Java after</th>
        <th class="num">Cycle cr/dr before</th><th class="num">COBOL after</th>
        <th class="num">Java after</th><th>Verdict</th></tr></thead>
        <tbody>{account_rows(scenario)}</tbody></table>
      <h3>Job logs side by side</h3>
      <div class="logs"><table><thead><tr><th class="num">#</th><th>COBOL CBACT04C</th>
        <th>Java port</th></tr></thead><tbody>{log_table(scenario)}</tbody></table></div>
    </section>""")

    exclusions = "".join(
        f"<li><span class=\"mono\">{esc(name)}</span> bytes {m[0]}-{m[1]}: {esc(m[2])}</li>"
        for name, masks in MASKS.items() for m in masks)
    return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<title>CBACT04C (INTCALC) COBOL vs Java parity report</title>
<style>{STYLE}</style></head>
<body>
  <header>
    <h1>CBACT04C (INTCALC) &mdash; COBOL vs Java parity</h1>
    <p>Unmodified <span class="mono">app/cbl/CBACT04C.cbl</span> under GnuCOBOL against
       <span class="mono">java/carddemo-intcalc</span>, PARM <span class="mono">{esc(parm_date)}</span>,
       generated {datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M:%S UTC')}</p>
  </header>
  <div class="banner {'ok' if overall else 'bad'}">
    {'PARITY: every output record, counter and job log line matches' if overall
      else 'MISMATCH: the two runs disagree'} &middot; {records_compared} records compared
    across {len(scenarios)} scenarios
  </div>
  <main>
    <section>
      <h2>What is compared</h2>
      <p class="sub">Both sides read the same input datasets. The COBOL side runs the program
        unchanged as a GnuCOBOL module called by the PTRUN job step driver, its VSAM stand-ins loaded
        by PTLOAD and dumped by PTDUMP; the Java side runs
        <span class="mono">com.carddemo.intcalc.files.IntCalcBatchRunner</span> over the same flat
        files. Every byte of every output record is compared except the ranges listed below.</p>
      <h3>Excluded byte ranges, and why</h3>
      <ul class="exclusions">{exclusions}
        <li>Job log lines after <span class="mono">ABENDING PROGRAM</span> in the abend scenario
          (shown greyed out below): GnuCOBOL has no z/OS Language Environment, so the
          <span class="mono">CALL 'CEE3ABD'</span> of 9999-ABEND-PROGRAM cannot resolve and the
          runtime appends its own diagnostics after the last DISPLAY of the program.</li>
        <li>The exit code of an abending run: 1 from the GnuCOBOL runtime, 999 mod 256 from the JVM.
          Both sides reach 9999-ABEND-PROGRAM after the same DISPLAY lines, which is what is
          compared.</li>
      </ul>
    </section>
    {''.join(sections)}
  </main>
</body></html>
"""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--scenario", action="append", required=True,
                        help="name:input-dir:cobol-dir:java-dir")
    parser.add_argument("--parm-date", default="2022071800")
    parser.add_argument("--report", required=True)
    parser.add_argument("--json", required=True)
    args = parser.parse_args()

    scenarios = [compare_scenario(*spec.split(":")) for spec in args.scenario]
    records_compared = sum(s["records_compared"] for s in scenarios)
    overall = all(s["match"] for s in scenarios)

    Path(args.report).parent.mkdir(parents=True, exist_ok=True)
    Path(args.report).write_text(render(scenarios, args.parm_date, records_compared), encoding="utf-8")
    Path(args.json).write_text(json.dumps({
        "program": "CBACT04C",
        "parm_date": args.parm_date,
        "generated": datetime.now(timezone.utc).isoformat(),
        "records_compared": records_compared,
        "parity": overall,
        "scenarios": scenarios,
    }, indent=2, default=str), encoding="utf-8")

    for scenario in scenarios:
        print(f"[{scenario['scenario']}] "
              f"{'PARITY' if scenario['match'] else 'MISMATCH'}: "
              f"{scenario['records_compared']} output records compared, "
              f"{len(scenario['cobol_log'])} COBOL job log lines, "
              f"{len(scenario['log_mismatches'])} log line differences, "
              f"{len(scenario['counter_mismatches'])} counter differences")
        for entry in scenario["files"]:
            if not entry["match"]:
                print(f"    {entry['file']}: {len(entry['mismatches'])} mismatching records, "
                      f"first at record {entry['mismatches'][0]['record']}")
        for mismatch in scenario["log_mismatches"][:5]:
            print(f"    job log line {mismatch['line']}: COBOL [{mismatch['cobol']}] "
                  f"!= Java [{mismatch['java']}]")

    print(f"PARITY: {records_compared} output records compared across {len(scenarios)} scenarios, "
          f"{'all match' if overall else 'MISMATCHES FOUND'}")
    print(f"Report: {args.report}")
    return 0 if overall else 1


if __name__ == "__main__":
    sys.exit(main())
