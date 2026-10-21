#!/usr/bin/env python3
"""Audit downloaded dataset layouts against a curated license-aware catalog.

No downloads by default, no ground-truth leakage, no VIO run or accuracy claims.
"""
import argparse
import json
from pathlib import Path


def catalog(path):
    data = json.loads(Path(path).read_text(encoding="utf8"))
    if data.get("version") != 1 or not isinstance(data.get("entries"), list):
        raise ValueError("Unknown catalog version")
    ids = [x["id"] for x in data["entries"]]
    if len(ids) != len(set(ids)):
        raise ValueError("Duplicate dataset id")
    return data


def inspect(catalog_path, dataset_id, root):
    data = catalog(catalog_path)
    entry = next((x for x in data["entries"] if x["id"] == dataset_id), None)
    if entry is None:
        raise ValueError("Unknown dataset: " + dataset_id)
    folder = Path(root)
    checks = {}
    for pattern in entry["layout_globs"]:
        if pattern.startswith("/") or ".." in pattern or len(pattern) > 120:
            raise ValueError("Unsafe layout glob")
        matches = []
        if folder.is_dir():
            for p in folder.glob(pattern):
                if p.is_file() and p.resolve().is_relative_to(folder.resolve()):
                    matches.append(str(p.relative_to(folder)))
                    break
        checks[pattern] = matches
    ready = bool(checks) and all(checks.values())
    return {"dataset": dataset_id, "status": "LAYOUT_FOUND_NOT_BENCHMARKED" if ready else "MISSING_DATA",
            "checks": checks, "source": entry["official_data_url"],
            "license": entry["license"], "commercial_restriction": entry["commercial_restriction"],
            "ground_truth": entry["ground_truth"], "evaluated_ate": None,
            "warning": "No ARCore replay possible from generic EuRoC/TUM files; GT must remain separate from input pipeline."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dataset", help="Dataset ID in catalog.json")
    parser.add_argument("root", type=Path, help="Already downloaded data root")
    parser.add_argument("--catalog", type=Path, default=Path(__file__).with_name("catalog.json"))
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    report = json.dumps(inspect(args.catalog, args.dataset, args.root), ensure_ascii=False, indent=2)
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(report+"\n", encoding="utf8")
    print(report)


if __name__ == "__main__":
    main()
