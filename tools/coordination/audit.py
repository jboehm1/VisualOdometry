#!/usr/bin/env python3
"""Snapshot local remote refs and dry-run pairwise merges; never checkout/merge/push.
Fetch explicitly first. merge-tree writes temporary Git tree objects, no refs/index.
"""
from __future__ import annotations
import argparse
import datetime as dt
import itertools
import json
import subprocess
from collections import defaultdict
from pathlib import Path

DEFAULT_BRANCHES = ["origin/feature/camera-free-pdr", "origin/work/slam-roadmap-b1-b2-b8", "origin/feature/three-validation-campaigns"]


def git(repo, *args, allowed=(0,)):
    p = subprocess.run(["git", "-C", str(repo), *args], text=True, capture_output=True)
    if p.returncode not in allowed:
        raise RuntimeError(f"git {args[0]} failed: {p.stderr.strip() or p.stdout.strip()}")
    return p


def resolve(repo, ref):
    return git(repo, "rev-parse", "--verify", "--end-of-options", ref + "^{commit}").stdout.strip()


def merge_probe(repo, left, right):
    p = git(repo, "merge-tree", "--write-tree", "--name-only", left, right, allowed=(0, 1))
    lines = p.stdout.splitlines()
    paths = []
    if p.returncode:
        for line in lines[1:]:
            if not line:
                break
            paths.append(line)
    return {"clean": p.returncode == 0, "tree": lines[0], "conflict_paths": paths}


def audit(repo, base_ref, branch_refs):
    # Resolve every ref once: another chat may push/fetch while this command runs.
    base = resolve(repo, base_ref)
    heads = {ref: resolve(repo, ref) for ref in branch_refs}
    data = {"schema_version": 1, "observed_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
            "base": {"ref": base_ref, "sha": base}, "branches": [], "shared_paths": {}, "merges": [],
            "limits": ["Snapshot of local refs; run git fetch explicitly first", "Clean text merge is not runtime validation", "No CI, dataset or physical-device success inferred"]}
    owners = defaultdict(list)
    for ref, sha in heads.items():
        common = git(repo, "merge-base", base, sha).stdout.strip()
        behind, ahead = map(int, git(repo, "rev-list", "--left-right", "--count", base + "..." + sha).stdout.split())
        paths = git(repo, "diff", "--name-only", common, sha).stdout.splitlines()
        data["branches"].append({"ref": ref, "sha": sha, "common_base": common, "ahead": ahead, "behind": behind, "changed_files": paths})
        for path in paths:
            owners[path].append(ref)
    data["shared_paths"] = {p: refs for p, refs in sorted(owners.items()) if len(refs) > 1}
    comparisons = [(base_ref, ref) for ref in heads] + list(itertools.combinations(heads, 2))
    resolved = {base_ref: base, **heads}
    for a, b in comparisons:
        data["merges"].append({"left": a, "right": b, **merge_probe(repo, resolved[a], resolved[b])})
    return data


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--repo", type=Path, default=Path.cwd())
    p.add_argument("--base", default="origin/feature/slam-explorer")
    p.add_argument("--branch", action="append", help="Repeat to override the three default active branches")
    p.add_argument("--out", type=Path, help="JSON output; stdout when omitted")
    args = p.parse_args()
    try:
        data = audit(args.repo, args.base, args.branch or DEFAULT_BRANCHES)
    except (RuntimeError, OSError) as e:
        p.exit(2, f"Audit failed: {e}\n")
    output = json.dumps(data, indent=2, ensure_ascii=False) + "\n"
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(output, encoding="utf-8")
    else:
        print(output, end="")


if __name__ == "__main__":
    main()
