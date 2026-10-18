"""CLI: plan without network, execute with auditable artifacts, render offline history."""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from tools.testenv.contracts import load_suite, readiness
from tools.testenv.runner import execute
from tools.testenv.presentation import render_dashboard


def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest="action", required=True)
    plan = sub.add_parser("plan", help="Validate a suite and print readiness/limitations; no download")
    plan.add_argument("--suite", type=Path, required=True)
    run = sub.add_parser("run", help="Run cases with preserved provenance and history")
    run.add_argument("--suite", type=Path, required=True)
    run.add_argument("--out", type=Path, required=True)
    run.add_argument("--only", action="append", default=[], metavar="ID",
                     help="Run only matching case ID(s); repeatable")
    run.add_argument("--resume", action="store_true",
                     help="Reuse only exact hash-verified prior PASS reports")
    run.add_argument("--fail-fast", action="store_true",
                     help="Do not start subsequent cases after a failure")
    show = sub.add_parser("render", help="Regenerate offline HTML from retained summaries")
    show.add_argument("--out", type=Path, required=True)
    return p


def main() -> None:
    args = parser().parse_args()
    try:
        if args.action == "plan":
            suite = load_suite(args.suite)
            result = {"suite_id": suite["id"], "schema_version": 1,
                      "jobs": [readiness(job, args.suite.resolve()) for job in suite["jobs"]]}
            result["ready"] = all(job["ready"] for job in result["jobs"])
        elif args.action == "run":
            result = execute(args.suite, args.out, set(args.only) or None,
                             resume=args.resume, fail_fast=args.fail_fast)
        else:
            result = {"html": str(render_dashboard(args.out.resolve()))}
        print(json.dumps(result, sort_keys=True, indent=2, allow_nan=False))
        if result.get("status") == "FAILED":
            raise SystemExit(1)
        if result.get("ready") is False:
            raise SystemExit(2)
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError) as exc:
        print("TESTENV_ERROR: " + str(exc), file=sys.stderr)
        raise SystemExit(2) from exc


if __name__ == "__main__":
    main()
