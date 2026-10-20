#!/usr/bin/env python3
"""Deterministic evidence preflight for EXISTING VECTOR testenv suites.

Reuses tools.testenv suite/engine validation and its source fingerprint. Does
not download data, run estimators, create reference truth, or bless a license.
Local files (and complete directory trees) are SHA-256 locked. Public-runner
downloads without local bytes stay explicitly UNVERIFIED_REMOTE, even if an
upstream downloader claims a pinned checksum; not 'locked' source data.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

from tools.testenv.contracts import job_inputs, load_suite
from tools.testenv.runner import code_identity

MAX_FILES = 200_000
MAX_BYTES = 50 * 1024 ** 3


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    before = path.stat()
    with path.open("rb") as stream:
        for piece in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(piece)
    after = path.stat()
    if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
        raise ValueError("Input changed during hashing: " + str(path))
    return digest.hexdigest()


def _entry(path: Path, relative_path: str) -> dict:
    if path.is_symlink():
        raise ValueError("Symlink input is forbidden: " + str(path))
    if not path.is_file():
        raise ValueError("Expected an ordinary input file: " + str(path))
    return {"relative_path": relative_path,
            "bytes": path.stat().st_size,
            "sha256": sha256(path)}


def lock_input(path: Path, max_bytes: int = MAX_BYTES) -> dict:
    """Hash file or directory content without saving private absolute paths."""
    if path.is_symlink() or not path.exists():
        raise ValueError("Missing input or symlink root: " + str(path))
    if path.is_file():
        one = _entry(path, ".")
        if one["bytes"] > max_bytes:
            raise ValueError("Input exceeds byte cap")
        return {"type": "file", "entries": [one], "total_bytes": one["bytes"]}
    if not path.is_dir():
        raise ValueError("Unsupported input type")
    result = []
    total = 0
    for candidate in sorted(path.rglob("*"), key=lambda p: p.relative_to(path).as_posix()):
        if candidate.is_symlink():
            raise ValueError("Symlink in source tree: " + str(candidate))
        if candidate.is_dir():
            continue
        if len(result) >= MAX_FILES:
            raise ValueError("Too many files in dataset tree")
        size = candidate.stat().st_size
        total += size
        if total > max_bytes:
            raise ValueError("Dataset exceeds byte cap")
        relative = candidate.relative_to(path).as_posix()
        result.append(_entry(candidate, relative))
    if not result:
        raise ValueError("Empty dataset input directory")
    return {"type": "directory", "entries": result, "total_bytes": total}


def lock_suite(path: Path, max_bytes: int = MAX_BYTES) -> dict:
    suite_path = path.resolve(strict=True)
    if suite_path.is_symlink():
        raise ValueError("Symlink suite not allowed")
    suite = load_suite(suite_path)
    source_id = code_identity()
    # Include this lock tool and relevant stand-alone converters/contracts,
    # which are not all covered by the historical testenv source list.
    for extra in ("tools/datasets/repro_manifest.py", "tools/pdr/datasets.py",
                  "native/vio/VioEngine.hpp", "tools/vio/compare_measured_runs.py"):
        extra_path = ROOT / extra
        if extra_path.is_file():
            source_id["source_sha256"].setdefault(extra, sha256(extra_path))
    jobs = []
    for job in suite["jobs"]:
        # job_inputs() canonicalizes paths. Check the literal suite path first:
        # resolving a symlink would otherwise hide the original indirection.
        for value in job.get("inputs", {}).values():
            raw = suite_path.parent / value
            if raw.is_symlink():
                raise ValueError("Symlink input declared in suite: " + value)
        declared_inputs = job_inputs(job, suite_path)
        hashes = {key: lock_input(value, max_bytes)
                  for key, value in sorted(declared_inputs.items())}
        if hashes:
            evidence = "LOCAL_CONTENT_SHA256"
        elif job.get("fixture") == "canonical_imu":
            evidence = "SYNTHETIC_FIXTURE_NOT_REAL_DATA"
        else:
            evidence = "UNVERIFIED_REMOTE_BYTES_AT_PREFLIGHT"
        jobs.append({"id": job["id"], "dataset": job["dataset"],
                     "engine": job["engine"], "input_status": evidence,
                     "inputs": hashes})
    return {"schema_version": 1,
            "kind": "vector_dataset_repro_preflight",
            "suite_id": suite["id"], "suite_sha256": sha256(suite_path),
            "source_git_sha": source_id["git_sha"],
            "source_file_sha256": source_id["source_sha256"],
            "jobs": jobs,
            "warning": "Input lock is not a VIO/PDR score. Remote data unverified; "
                       "runtime report, clock alignment, truth provenance, "
                       "licenses and estimator identity remain separate."}


def _stable(value: dict) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False,
                      allow_nan=False)


def save_once(out: Path, value: dict) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("x", encoding="utf-8") as file:
        file.write(json.dumps(value, indent=2, sort_keys=True,
                              ensure_ascii=False, allow_nan=False) + "\n")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("lock", "verify"))
    parser.add_argument("--suite", type=Path, required=True)
    parser.add_argument("--receipt", type=Path, required=True)
    parser.add_argument("--max-bytes", type=int, default=MAX_BYTES)
    parser.add_argument("--require-real-local", action="store_true",
                        help="Fail if a job only has remote/unverified or fixture inputs")
    args = parser.parse_args()
    if args.max_bytes <= 0 or args.max_bytes > MAX_BYTES:
        parser.error("max-bytes must be between 1 and 50 GiB")
    try:
        actual = lock_suite(args.suite, args.max_bytes)
        incomplete = [j["id"] for j in actual["jobs"]
                      if j["input_status"] != "LOCAL_CONTENT_SHA256"]
        if args.require_real_local and incomplete:
            raise ValueError("Cannot call these cases locally locked real datasets: "
                             + ", ".join(incomplete))
        if args.mode == "lock":
            save_once(args.receipt, actual)
            print("LOCKED_RECEIPT " + str(args.receipt))
        else:
            if args.receipt.stat().st_size > 64 * 1024 * 1024:
                raise ValueError("Receipt too large")
            expected = json.loads(args.receipt.read_text(encoding="utf-8"))
            if _stable(expected) != _stable(actual):
                raise ValueError("Reproducibility mismatch: suite/code/input data changed")
            print("VERIFIED_RECEIPT " + str(args.receipt))
        if incomplete:
            print("WARNING: no locally hashed REAL dataset for: "+", ".join(incomplete),
                  file=sys.stderr)
        return 0
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as e:
        print("DATASET_REPRO_ERROR: " + str(e), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
