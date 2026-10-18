"""Static offline dashboard and immutable test-run history.

No CDN, tracking, backend or network dependency. Escape all dataset-supplied text.
"""
from __future__ import annotations

import html
import json
from pathlib import Path

STYLE = """
:root{color-scheme:dark;font-family:Inter,system-ui,Arial,sans-serif;background:#0b1220;color:#edf3ff}
body{max-width:1230px;margin:auto;padding:35px 22px 100px;line-height:1.5}
h1{font-size:clamp(1.8rem,4vw,2.8rem);letter-spacing:-.05em;margin:0}
h2{font-size:1.3rem} a{color:#8cd6ff} a:hover{color:#d4f1ff}
header{display:flex;justify-content:space-between;align-items:center;gap:20px;flex-wrap:wrap}
.subtle{color:#9bacc9}.eyebrow{letter-spacing:.2em;text-transform:uppercase;color:#7ddac6;font-size:.78rem}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:14px;margin:24px 0}
.card,.run{border:1px solid #273957;background:#121f33;border-radius:13px;padding:20px}
.card strong{font-size:2rem;display:block} .card small{color:#a8bad4}
.badge{display:inline-block;padding:4px 12px;border-radius:99px;font-size:.77rem;font-weight:700}
.PASS{background:#164d42;color:#8bf0c3}.FAILED{background:#612735;color:#ffb8c3}
.SKIPPED{background:#524125;color:#ffe2a4}
.run{margin:16px 0}.run h3{margin:4px 0 10px}.runhead{display:flex;gap:12px;flex-wrap:wrap;align-items:center}
table{width:100%;border-collapse:collapse;font-size:.88rem;min-width:690px}
th,td{padding:12px;text-align:left;border-bottom:1px solid #2a3950;vertical-align:top}
th{color:#aebbd2} .scroll{overflow-x:auto} code{color:#c5d9ff;overflow-wrap:anywhere}
pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#091524;padding:12px;border-radius:9px;font-size:.77rem}
details{margin:8px 0}summary{cursor:pointer;color:#accce9}
footer{color:#90a2bd;margin-top:30px}
"""
def esc(value) -> str:
    return html.escape(str(value), quote=True)


def _metrics(case: dict) -> str:
    metrics = case.get("metrics") or {}
    fields = ("steps", "test_cases", "subjects", "frames_processed",
              "step_interval_f1_macro", "ate_rmse_m", "pairs",
              "original_sensor_timestamps", "distance_m")
    summary = [(k, metrics[k]) for k in fields if k in metrics]
    if not summary:
        summary = list(metrics.items())[:4]
    return "<br>".join(f"<code>{esc(k)}</code>: {esc(v)}" for k, v in summary) or "—"


def render_dashboard(root: Path) -> Path:
    runs = []
    for path in sorted((root / "runs").glob("*/summary.json"), reverse=True):
        try:
            value = json.loads(path.read_text(encoding="utf-8"))
            if not isinstance(value.get("cases"), list):
                continue
            runs.append(value)
        except (ValueError, OSError):
            continue
    latest = runs[0] if runs else None
    parts = [
        "<!doctype html><html lang='en'><head><meta charset='utf-8'>",
        "<meta name='viewport' content='width=device-width, initial-scale=1'>",
        "<meta name='description' content='Local reproducible odometry test campaign reports'>",
        "<title>VECTOR VIO | Test Evidence</title><style>", STYLE, "</style></head><body>",
        "<header><div><div class='eyebrow'>VECTOR VIO · Engineering evidence</div>",
        "<h1>Test Environment</h1><div class='subtle'>Verified engine identity · explicit reference quality · preserved execution history</div></div>",
        "<div class='badge'>LOCAL / OFFLINE REPORT</div></header>",
    ]
    if latest:
        count = latest.get("counts", {})
        totals = [("Last run", latest.get("status", "RUNNING")),
                  ("Passed", count.get("PASS", 0)), ("Failed", count.get("FAILED", 0)),
                  ("Skipped", count.get("SKIPPED", 0)), ("Runs retained", len(runs))]
        parts.append("<section class='grid'>")
        for label, value in totals:
            parts.append(f"<div class='card'><small>{esc(label)}</small><strong>{esc(value)}</strong></div>")
        parts.append("</section>")
    else:
        parts.append("<div class='card'>No executed tests yet. Run <code>python testenv.py run --suite ... --out ...</code>.</div>")
    parts.append("<section><h2>Execution history</h2>")
    for run in runs:
        rid = run.get("run_id", "")
        if not isinstance(rid, str) or "/" in rid or ".." in rid:
            continue
        state = run.get("status", "RUNNING")
        state_css = state if state in ("PASS", "FAILED", "SKIPPED") else "SKIPPED"
        parts += [
            "<article class='run'><div class='runhead'>",
            f"<span class='badge {state_css}'>{esc(state)}</span>",
            f"<h3>{esc(run.get('suite','?'))}</h3><code>{esc(rid)}</code></div>",
            f"<div class='subtle'>{esc(run.get('created_utc',''))} · Code SHA: ",
            f"<code>{esc((run.get('code_identity') or {}).get('git_sha') or 'local')[:15]}</code> · ",
            f"<a href='runs/{esc(rid)}/summary.json'>raw summary JSON</a></div>",
            "<div class='scroll'><table><thead><tr><th>Case</th><th>Dataset → engine</th>",
            "<th>Result</th><th>Actual measured metrics</th><th>Evidence</th></tr></thead><tbody>",
        ]
        for case in run.get("cases", []):
            cid = case.get("id", "")
            if not isinstance(cid, str) or "/" in cid or ".." in cid:
                continue
            state = case.get("status", "FAILED")
            css = state if state in ("PASS", "FAILED", "SKIPPED") else "FAILED"
            evidence = (f"<a href='runs/{esc(rid)}/cases/{esc(cid)}/report.json'>report</a> · "
                        if state == "PASS" else "")
            evidence += f"<a href='runs/{esc(rid)}/cases/{esc(cid)}/case.json'>case JSON</a>"
            error = case.get("error")
            if error:
                evidence += f"<details><summary>Failure</summary><pre>{esc(error)}</pre></details>"
            claim = case.get("accuracy_claim_level") or "not independently quantified"
            parts += [
                "<tr>", f"<td><strong>{esc(cid)}</strong><br>",
                f"<span class='subtle'>{esc(case.get('duration_s', '—'))} s</span></td>",
                f"<td>{esc(case.get('dataset','?'))} → <code>{esc(case.get('engine','?'))}</code>",
                f"<br><span class='subtle'>{esc(claim)}</span></td>",
                f"<td><span class='badge {css}'>{esc(state)}</span></td>",
                f"<td>{_metrics(case)}</td><td>{evidence}</td>", "</tr>"
            ]
        parts += ["</tbody></table></div></article>"]
    parts += ["</section><footer>PASS = adapter executed and declared checks passed, ",
              "not certification of an Android phone or distance accuracy. ",
              "Original raw recordings are not embedded in this dashboard. ",
              "The full run history is kept locally as JSON.</footer></body></html>"]
    dest = root / "index.html"
    tmp = dest.with_suffix(".html.tmp")
    tmp.write_text("".join(parts), encoding="utf-8")
    tmp.replace(dest)
    return dest
