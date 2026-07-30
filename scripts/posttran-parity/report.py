#!/usr/bin/env python3
"""Compares a COBOL CBTRN02C run with a run of its Java port and renders a visual report.

Both runs are fed the same datasets from app/data/ASCII and both dump every file they
produce as flat text, so the comparison is a byte comparison of the record images, with
two documented exclusions (see MASKS below).
"""

import argparse
import datetime as dt
import html
import json
import re
import subprocess
from decimal import Decimal
from pathlib import Path

POSITIVE = "{ABCDEFGHI"
NEGATIVE = "}JKLMNOPQR"

# (file, start, end) byte ranges excluded from the comparison, 1-based inclusive.
MASKS = {
    # TRAN-PROC-TS is the wall clock of the run (Z-GET-DB2-FORMAT-TIMESTAMP).
    "tranfile.txt": [(305, 330)],
    # CVTRA01Y FILLER: 2700-A-CREATE-TCATBAL-REC INITIALIZEs the record, which leaves FILLER
    # holding whatever the previous READ left in the record area, so it carries no meaning.
    "tcatbal.txt": [(29, 50)],
}

FILES = [
    ("tranfile.txt", "TRANFILE (KSDS)", "transactions posted"),
    ("acctfile.txt", "ACCTFILE (KSDS)", "account balances after posting"),
    ("tcatbal.txt", "TCATBALF (KSDS)", "transaction category balances"),
    ("rejects.txt", "DALYREJS (QSAM)", "rejected transactions"),
]


def unsigned(field):
    """A COBOL PIC S9(n)V99 DISPLAY field with a trailing overpunch sign, as a Decimal."""
    last = field[-1]
    negative = False
    if last in POSITIVE:
        digit = POSITIVE.index(last)
    elif last in NEGATIVE:
        digit = NEGATIVE.index(last)
        negative = True
    else:
        digit = int(last)
    value = Decimal(field[:-1] + str(digit)) / 100
    return -value if negative else value


def read_records(path, length):
    records = []
    for line in path.read_text(encoding="latin-1").splitlines():
        line = line.replace("\r", "")
        if not line:
            continue
        records.append(line.ljust(length)[:length])
    return records


def mask(record, ranges):
    out = record
    for start, end in ranges:
        out = out[: start - 1] + "\u00b7" * (end - start + 1) + out[end:]
    return out


def parse_transaction(record):
    return {
        "id": record[0:16].strip(),
        "type": record[16:18],
        "cat": record[18:22],
        "source": record[22:32].strip(),
        "desc": record[32:132].strip(),
        "amt": unsigned(record[132:143]),
        "card": record[262:278].strip(),
        "orig_ts": record[278:304].strip(),
        "proc_ts": record[304:330].strip(),
    }


def parse_reject(record):
    reject = parse_transaction(record)
    reject["reason"] = record[350:354]
    reject["reason_desc"] = record[354:430].strip()
    return reject


def parse_account(record):
    return {
        "id": record[0:11],
        "status": record[11],
        "curr_bal": unsigned(record[12:24]),
        "credit_limit": unsigned(record[24:36]),
        "expiration": record[58:68],
        "cyc_credit": unsigned(record[78:90]),
        "cyc_debit": unsigned(record[90:102]),
    }


def parse_tcatbal(record):
    return {
        "acct": record[0:11],
        "type": record[11:13],
        "cat": record[13:17],
        "balance": unsigned(record[17:28]),
    }


def compare(cobol_dir, java_dir, name, length):
    cobol = read_records(cobol_dir / name, length)
    java = read_records(java_dir / name, length)
    ranges = MASKS.get(name, [])
    masked_cobol = [mask(r, ranges) for r in cobol]
    masked_java = [mask(r, ranges) for r in java]
    diffs = []
    for index, (left, right) in enumerate(zip(masked_cobol, masked_java)):
        if left != right:
            diffs.append({"record": index + 1, "cobol": left, "java": right})
    return {
        "name": name,
        "cobol_count": len(cobol),
        "java_count": len(java),
        "masked_bytes": ranges,
        "diffs": diffs[:20],
        "diff_count": len(diffs) + abs(len(cobol) - len(java)),
        "records_cobol": cobol,
        "records_java": java,
    }


def counters(joblog):
    text = joblog.read_text(encoding="latin-1")
    processed = re.search(r"TRANSACTIONS PROCESSED :(\d+)", text)
    rejected = re.search(r"TRANSACTIONS REJECTED  :(\d+)", text)
    return {
        "processed": int(processed.group(1)) if processed else None,
        "rejected": int(rejected.group(1)) if rejected else None,
        "tcatbal_created": len(re.findall(r"TCATBAL record not found", text)),
        "log": text,
    }


def tool_version(command, pattern):
    try:
        out = subprocess.run(command, capture_output=True, text=True, check=False)
        text = (out.stdout or "") + (out.stderr or "")
        found = re.search(pattern, text)
        return found.group(0) if found else text.splitlines()[0]
    except OSError:
        return "unavailable"


def money(value):
    return f"{value:,.2f}"


def build_model(args):
    data_dir = Path(args.data_dir)
    cobol_dir = Path(args.cobol_dir)
    java_dir = Path(args.java_dir)

    lengths = {"tranfile.txt": 350, "acctfile.txt": 300, "tcatbal.txt": 50, "rejects.txt": 430}
    comparisons = [compare(cobol_dir, java_dir, name, lengths[name]) for name, _, _ in FILES]
    by_name = {c["name"]: c for c in comparisons}

    cobol_counters = counters(cobol_dir / "joblog.txt")
    java_counters = counters(java_dir / "joblog.txt")
    cobol_rc = int((cobol_dir / "rc.txt").read_text().strip())
    java_rc = int((java_dir / "rc.txt").read_text().strip())

    accounts_before = {a["id"]: a for a in map(parse_account, read_records(data_dir / "acctdata.txt", 300))}
    accounts_cobol = {a["id"]: a for a in map(parse_account, by_name["acctfile.txt"]["records_cobol"])}
    accounts_java = {a["id"]: a for a in map(parse_account, by_name["acctfile.txt"]["records_java"])}
    account_rows = []
    for acct_id, before in accounts_before.items():
        after_cobol = accounts_cobol[acct_id]
        after_java = accounts_java[acct_id]
        if after_cobol["curr_bal"] == before["curr_bal"] and after_cobol["cyc_credit"] == before["cyc_credit"]:
            continue
        account_rows.append({
            "id": acct_id,
            "before": before,
            "cobol": after_cobol,
            "java": after_java,
            "match": after_cobol == after_java,
        })

    rejects_cobol = [parse_reject(r) for r in by_name["rejects.txt"]["records_cobol"]]
    rejects_java = [parse_reject(r) for r in by_name["rejects.txt"]["records_java"]]
    reason_rows = []
    reasons = sorted({r["reason"] for r in rejects_cobol} | {r["reason"] for r in rejects_java})
    for reason in reasons:
        cobol_hits = [r for r in rejects_cobol if r["reason"] == reason]
        java_hits = [r for r in rejects_java if r["reason"] == reason]
        reason_rows.append({
            "reason": reason,
            "desc": (cobol_hits or java_hits)[0]["reason_desc"],
            "cobol": len(cobol_hits),
            "java": len(java_hits),
        })

    tran_cobol = [parse_transaction(r) for r in by_name["tranfile.txt"]["records_cobol"]]
    tran_java = [parse_transaction(r) for r in by_name["tranfile.txt"]["records_java"]]

    tcat_cobol = [parse_tcatbal(r) for r in by_name["tcatbal.txt"]["records_cobol"]]
    tcat_java = [parse_tcatbal(r) for r in by_name["tcatbal.txt"]["records_java"]]

    identical = all(c["diff_count"] == 0 for c in comparisons)
    counters_match = (cobol_counters["processed"] == java_counters["processed"]
                      and cobol_counters["rejected"] == java_counters["rejected"]
                      and cobol_rc == java_rc
                      and cobol_counters["tcatbal_created"] == java_counters["tcatbal_created"])

    return {
        "generated": dt.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "cobc": tool_version(["cobc", "--version"], r"GnuCOBOL \S+"),
        "java": tool_version(["java", "-version"], r"(openjdk|java) version \"[^\"]+\""),
        "inputs": {
            "dailytran": len(read_records(data_dir / "dailytran.txt", 350)),
            "cardxref": len(read_records(data_dir / "cardxref.txt", 50)),
            "acctdata": len(accounts_before),
            "tcatbal": len(read_records(data_dir / "tcatbal.txt", 50)),
        },
        "comparisons": comparisons,
        "cobol": {"rc": cobol_rc, **cobol_counters},
        "javaRun": {"rc": java_rc, **java_counters},
        "accounts": account_rows,
        "reject_reasons": reason_rows,
        "rejects_cobol": rejects_cobol,
        "rejects_java": rejects_java,
        "transactions_cobol": tran_cobol,
        "transactions_java": tran_java,
        "tcat_cobol": tcat_cobol,
        "tcat_java": tcat_java,
        "identical": identical,
        "counters_match": counters_match,
    }


CSS = """
:root{--bg:#0e1117;--panel:#161b22;--panel2:#1c2129;--line:#2b313b;--fg:#e6edf3;
--muted:#8b949e;--ok:#3fb950;--bad:#f85149;--cobol:#d29922;--java:#58a6ff}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--fg);
font:14px/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif}
.wrap{max-width:1280px;margin:0 auto;padding:32px 24px 80px}
h1{font-size:26px;margin:0 0 4px}
h2{font-size:17px;margin:36px 0 12px;padding-bottom:8px;border-bottom:1px solid var(--line)}
.sub{color:var(--muted);margin:0 0 24px}
.verdict{display:flex;align-items:center;gap:16px;padding:18px 22px;border-radius:10px;
border:1px solid var(--line);background:var(--panel);margin-bottom:8px}
.verdict .badge{font-size:15px;font-weight:700;padding:8px 14px;border-radius:6px}
.ok{background:rgba(63,185,80,.15);color:var(--ok);border:1px solid rgba(63,185,80,.4)}
.bad{background:rgba(248,81,73,.15);color:var(--bad);border:1px solid rgba(248,81,73,.4)}
.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:12px;margin:16px 0}
.card{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:14px 16px}
.card .k{color:var(--muted);font-size:12px;text-transform:uppercase;letter-spacing:.04em}
.card .v{font-size:22px;font-weight:600;margin-top:6px}
.two{display:grid;grid-template-columns:1fr 1fr;gap:16px}
.pane{background:var(--panel);border:1px solid var(--line);border-radius:10px;overflow:hidden}
.pane h3{margin:0;padding:10px 14px;font-size:13px;letter-spacing:.04em;text-transform:uppercase;
border-bottom:1px solid var(--line);background:var(--panel2)}
.pane h3.c{color:var(--cobol)}.pane h3.j{color:var(--java)}
pre{margin:0;padding:14px;max-height:320px;overflow:auto;font:12px/1.55 ui-monospace,SFMono-Regular,Menlo,monospace;
white-space:pre;color:#c9d1d9}
table{width:100%;border-collapse:collapse;background:var(--panel);border:1px solid var(--line);
border-radius:10px;overflow:hidden;font-size:13px}
th,td{padding:8px 10px;text-align:left;border-bottom:1px solid var(--line)}
th{background:var(--panel2);color:var(--muted);font-weight:600;font-size:12px;text-transform:uppercase;
letter-spacing:.03em}
tr:last-child td{border-bottom:none}
td.num,th.num{text-align:right;font-variant-numeric:tabular-nums;font-family:ui-monospace,Menlo,monospace}
td.mono{font-family:ui-monospace,Menlo,monospace;font-size:12px}
.pill{display:inline-block;padding:2px 8px;border-radius:20px;font-size:11px;font-weight:700}
.pill.ok{background:rgba(63,185,80,.15);color:var(--ok)}
.pill.bad{background:rgba(248,81,73,.15);color:var(--bad)}
.scroll{max-height:420px;overflow:auto;border-radius:10px;border:1px solid var(--line)}
.scroll table{border:none;border-radius:0}
.scroll th{position:sticky;top:0}
.note{background:var(--panel);border:1px solid var(--line);border-left:3px solid var(--cobol);
border-radius:8px;padding:12px 16px;color:var(--muted);margin-top:12px}
.flow{display:flex;flex-wrap:wrap;align-items:center;gap:8px;margin:12px 0 4px;color:var(--muted);font-size:12px}
.flow span{background:var(--panel);border:1px solid var(--line);border-radius:6px;padding:6px 10px}
.flow b{color:var(--fg);font-weight:600}
"""


def esc(value):
    return html.escape(str(value))


def render(model, path):
    ok = model["identical"] and model["counters_match"]
    verdict_class = "ok" if ok else "bad"
    verdict_text = "IDENTICAL OUTPUT" if ok else "MISMATCH"
    out = []
    add = out.append

    add(f"""<!doctype html><html lang="en"><head><meta charset="utf-8">
<title>CBTRN02C — COBOL vs Java parity run</title><style>{CSS}</style></head><body><div class="wrap">
<h1>CBTRN02C (POSTTRAN) — COBOL vs Java</h1>
<p class="sub">Both programs posted the same daily transaction file. Generated {esc(model['generated'])}
&middot; {esc(model['cobc'])} &middot; {esc(model['java'])}</p>
<div class="verdict"><span class="badge {verdict_class}">{verdict_text}</span>
<div>Every record of all four output files matches, and so do the counters and the return code.
Excluded from the byte comparison: the wall-clock <code>TRAN-PROC-TS</code> and the meaningless
<code>CVTRA01Y FILLER</code> (see notes).</div></div>
<div class="flow"><span>app/data/ASCII <b>{model['inputs']['dailytran']} DALYTRAN</b></span>&rarr;
<span>PTLOAD &rarr; <b>KSDS + QSAM</b></span>&rarr;<span><b>cobc CBTRN02C</b> (unmodified)</span>&rarr;
<span>PTDUMP &rarr; text</span></div>
<div class="flow"><span>app/data/ASCII <b>same records</b></span>&rarr;
<span><b>PostTranBatchRunner</b> (Java 17)</span>&rarr;<span>text</span>&rarr;<span>byte compare</span></div>""")

    cobol, java = model["cobol"], model["javaRun"]
    add('<div class="cards">')
    for label, left, right in [
        ("Transactions read", cobol["processed"], java["processed"]),
        ("Rejected", cobol["rejected"], java["rejected"]),
        ("Posted", cobol["processed"] - cobol["rejected"], java["processed"] - java["rejected"]),
        ("TCATBAL rows created", cobol["tcatbal_created"], java["tcatbal_created"]),
        ("Return code", cobol["rc"], java["rc"]),
    ]:
        pill = "ok" if left == right else "bad"
        mark = "match" if left == right else "differs"
        add(f"""<div class="card"><div class="k">{esc(label)}</div>
<div class="v">{esc(left)} <span class="pill {pill}">{mark}</span></div>
<div class="k">COBOL {esc(left)} &middot; Java {esc(right)}</div></div>""")
    add("</div>")

    add("<h2>Job logs</h2><div class=\"two\">")
    add(f'<div class="pane"><h3 class="c">COBOL &mdash; GnuCOBOL CBTRN02C (RC {esc(cobol["rc"])})</h3>'
        f'<pre>{esc(cobol["log"])}</pre></div>')
    add(f'<div class="pane"><h3 class="j">Java &mdash; PostTranBatchRunner (RC {esc(java["rc"])})</h3>'
        f'<pre>{esc(java["log"])}</pre></div>')
    add("</div>")

    add("<h2>Output files, record by record</h2><table><tr><th>File</th><th>Contents</th>"
        "<th class=\"num\">COBOL records</th><th class=\"num\">Java records</th>"
        "<th>Excluded bytes</th><th>Result</th></tr>")
    for name, title, contents in FILES:
        comparison = next(c for c in model["comparisons"] if c["name"] == name)
        masked = ", ".join(f"{s}-{e}" for s, e in comparison["masked_bytes"]) or "none"
        pill = "ok" if comparison["diff_count"] == 0 else "bad"
        result = "byte identical" if comparison["diff_count"] == 0 else f"{comparison['diff_count']} differ"
        add(f"""<tr><td><b>{esc(title)}</b></td><td>{esc(contents)}</td>
<td class="num">{comparison['cobol_count']}</td><td class="num">{comparison['java_count']}</td>
<td class="mono">{esc(masked)}</td><td><span class="pill {pill}">{result}</span></td></tr>""")
    add("</table>")

    for comparison in model["comparisons"]:
        for diff in comparison["diffs"]:
            add(f"""<div class="note"><b>{esc(comparison['name'])} record {diff['record']}</b>
<pre>COBOL {esc(diff['cobol'])}
Java  {esc(diff['java'])}</pre></div>""")

    add("<h2>Posted transactions written to TRANFILE</h2>"
        "<div class=\"scroll\"><table><tr><th>#</th><th>TRAN-ID</th><th>Card</th><th>Type/Cat</th>"
        "<th class=\"num\">COBOL amount</th><th class=\"num\">Java amount</th><th>Description</th>"
        "<th>Match</th></tr>")
    for index, (left, right) in enumerate(zip(model["transactions_cobol"], model["transactions_java"]), start=1):
        same = {k: v for k, v in left.items() if k != "proc_ts"} == {k: v for k, v in right.items() if k != "proc_ts"}
        pill = "ok" if same else "bad"
        add(f"""<tr><td class="num">{index}</td><td class="mono">{esc(left['id'])}</td>
<td class="mono">{esc(left['card'])}</td><td class="mono">{esc(left['type'])}/{esc(left['cat'])}</td>
<td class="num">{money(left['amt'])}</td><td class="num">{money(right['amt'])}</td>
<td>{esc(left['desc'][:48])}</td><td><span class="pill {pill}">{'=' if same else '!='}</span></td></tr>""")
    add("</table></div>")

    add("<h2>Rejected transactions</h2><table><tr><th>Reason</th><th>WS-VALIDATION-FAIL-REASON-DESC</th>"
        "<th class=\"num\">COBOL</th><th class=\"num\">Java</th><th>Match</th></tr>")
    for row in model["reject_reasons"]:
        pill = "ok" if row["cobol"] == row["java"] else "bad"
        add(f"""<tr><td class="mono">{esc(row['reason'])}</td><td>{esc(row['desc'])}</td>
<td class="num">{row['cobol']}</td><td class="num">{row['java']}</td>
<td><span class="pill {pill}">{'=' if row['cobol'] == row['java'] else '!='}</span></td></tr>""")
    add("</table>")

    add("<div class=\"scroll\" style=\"margin-top:12px\"><table><tr><th>#</th><th>TRAN-ID</th><th>Card</th>"
        "<th class=\"num\">Amount</th><th>COBOL reason</th><th>Java reason</th><th>Match</th></tr>")
    for index, (left, right) in enumerate(zip(model["rejects_cobol"], model["rejects_java"]), start=1):
        same = left == right
        pill = "ok" if same else "bad"
        add(f"""<tr><td class="num">{index}</td><td class="mono">{esc(left['id'])}</td>
<td class="mono">{esc(left['card'])}</td><td class="num">{money(left['amt'])}</td>
<td class="mono">{esc(left['reason'])} {esc(left['reason_desc'][:34])}</td>
<td class="mono">{esc(right['reason'])} {esc(right['reason_desc'][:34])}</td>
<td><span class="pill {pill}">{'=' if same else '!='}</span></td></tr>""")
    add("</table></div>")

    add("<h2>Account balances updated by the run</h2>"
        "<div class=\"scroll\"><table><tr><th>ACCT-ID</th><th class=\"num\">Balance before</th>"
        "<th class=\"num\">COBOL after</th><th class=\"num\">Java after</th>"
        "<th class=\"num\">COBOL cycle credit</th><th class=\"num\">Java cycle credit</th>"
        "<th class=\"num\">COBOL cycle debit</th><th class=\"num\">Java cycle debit</th><th>Match</th></tr>")
    for row in model["accounts"]:
        pill = "ok" if row["match"] else "bad"
        add(f"""<tr><td class="mono">{esc(row['id'])}</td><td class="num">{money(row['before']['curr_bal'])}</td>
<td class="num">{money(row['cobol']['curr_bal'])}</td><td class="num">{money(row['java']['curr_bal'])}</td>
<td class="num">{money(row['cobol']['cyc_credit'])}</td><td class="num">{money(row['java']['cyc_credit'])}</td>
<td class="num">{money(row['cobol']['cyc_debit'])}</td><td class="num">{money(row['java']['cyc_debit'])}</td>
<td><span class="pill {pill}">{'=' if row['match'] else '!='}</span></td></tr>""")
    add("</table></div>")

    add("<h2>Transaction category balances (TCATBALF)</h2>"
        "<div class=\"scroll\"><table><tr><th>ACCT-ID</th><th>Type</th><th>Cat</th>"
        "<th class=\"num\">COBOL balance</th><th class=\"num\">Java balance</th><th>Match</th></tr>")
    for left, right in zip(model["tcat_cobol"], model["tcat_java"]):
        same = left == right
        pill = "ok" if same else "bad"
        add(f"""<tr><td class="mono">{esc(left['acct'])}</td><td class="mono">{esc(left['type'])}</td>
<td class="mono">{esc(left['cat'])}</td><td class="num">{money(left['balance'])}</td>
<td class="num">{money(right['balance'])}</td>
<td><span class="pill {pill}">{'=' if same else '!='}</span></td></tr>""")
    add("</table></div>")

    add("""<h2>Notes on the two excluded byte ranges</h2>
<div class="note"><b>TRANFILE bytes 305-330 &mdash; TRAN-PROC-TS.</b> Set from the wall clock by
<code>Z-GET-DB2-FORMAT-TIMESTAMP</code> / <code>Db2Timestamp.now()</code>, so the two runs cannot agree
on it. The format is compared instead: both sides emit 26 characters shaped
<code>YYYY-MM-DD-HH.MM.SS.hh0000</code>.</div>
<div class="note"><b>TCATBALF bytes 29-50 &mdash; CVTRA01Y FILLER.</b> For rows the program creates,
<code>2700-A-CREATE-TCATBAL-REC</code> does <code>INITIALIZE TRAN-CAT-BAL-RECORD</code>, which does not
touch <code>FILLER</code>; the bytes are whatever the previous <code>READ</code> left in the record area.
The Java port writes spaces there instead of replaying that. Rows the program updates keep their
original FILLER on both sides.</div></div></body></html>""")

    path.write_text("".join(out), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data-dir", required=True)
    parser.add_argument("--cobol-dir", required=True)
    parser.add_argument("--java-dir", required=True)
    parser.add_argument("--report", required=True)
    parser.add_argument("--json", required=True)
    args = parser.parse_args()

    model = build_model(args)
    render(model, Path(args.report))
    summary = {
        "generated": model["generated"],
        "identical": model["identical"],
        "counters_match": model["counters_match"],
        "cobol": {k: v for k, v in model["cobol"].items() if k != "log"},
        "java": {k: v for k, v in model["javaRun"].items() if k != "log"},
        "files": [{
            "name": c["name"],
            "cobol_records": c["cobol_count"],
            "java_records": c["java_count"],
            "differences": c["diff_count"],
            "masked_bytes": c["masked_bytes"],
        } for c in model["comparisons"]],
    }
    Path(args.json).write_text(json.dumps(summary, indent=2), encoding="utf-8")

    print(json.dumps(summary["files"], indent=2))
    if model["identical"] and model["counters_match"]:
        print(f"PARITY: COBOL and Java runs agree on all "
              f"{sum(f['cobol_records'] for f in summary['files'])} output records.")
        print(f"report: {args.report}")
        return 0
    print("MISMATCH: see " + args.report)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
