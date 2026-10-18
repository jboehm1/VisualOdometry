"""Declarative, fail-closed benchmark suite contracts.

This module does not acquire datasets, invoke estimators or read reference truth.
It consumes the existing truthful dataset/engine registry from unified.py.
"""
from __future__ import annotations

import hashlib
import json
import math
import re
from pathlib import Path

from tools.campaigns import unified

ID = re.compile(r"^[a-z][a-z0-9_-]{0,47}$")
INPUTS = {"input", "root", "estimate", "truth", "heading_csv"}
PARAMS = {"stride_m", "yaw_axis", "known_distance", "heading_source"}
JOB_KEYS = {"id", "dataset", "engine", "inputs", "params", "fixture", "checks", "timeout_s"}
SUITE_KEYS = {"schema_version", "id", "description", "jobs"}
CHECK_KEYS = {"field", "min", "max", "equals"}


def digest(payload: object) -> str:
    return hashlib.sha256(json.dumps(payload, sort_keys=True, allow_nan=False,
                                     separators=(",", ":")).encode("utf-8")).hexdigest()


def _dict(value, name):
    if not isinstance(value, dict):
        raise ValueError(f"{name} must be an object")
    return value


def _id(value, name):
    if not isinstance(value, str) or ID.fullmatch(value) is None:
        raise ValueError(f"{name} must match {ID.pattern}")
    return value


def load_suite(path: Path) -> dict:
    path = path.resolve(strict=True)
    suite = _dict(json.loads(path.read_text(encoding="utf-8")), "suite")
    if suite.keys() - SUITE_KEYS or suite.get("schema_version") != 1:
        raise ValueError("Unsupported suite version or unknown suite keys")
    _id(suite.get("id"), "suite.id")
    if not isinstance(suite.get("description", ""), str):
        raise ValueError("suite.description must be text")
    jobs = suite.get("jobs")
    if not isinstance(jobs, list) or not jobs or len(jobs) > 100:
        raise ValueError("suite.jobs must contain 1 to 100 cases")
    seen = set()
    for job in jobs:
        _dict(job, "job")
        if job.keys() - JOB_KEYS:
            raise ValueError(f"Unknown job keys: {sorted(job.keys() - JOB_KEYS)}")
        name = _id(job.get("id"), "job.id")
        if name in seen:
            raise ValueError(f"Duplicate test case {name}")
        seen.add(name)
        dataset, engine = job.get("dataset"), job.get("engine")
        if not isinstance(dataset, str) or not isinstance(engine, str):
            raise ValueError(f"{name}: dataset and engine must be strings")
        # The original benchmark's registry remains the only capability authority.
        unified.plan(dataset, engine)
        inputs = _dict(job.get("inputs", {}), "inputs")
        if inputs.keys() - INPUTS:
            raise ValueError(f"{name}: unknown inputs {sorted(inputs.keys() - INPUTS)}")
        for key, value in inputs.items():
            if not isinstance(value, str) or not value.strip():
                raise ValueError(f"{name}: {key} must be a nonempty path")
        params = _dict(job.get("params", {}), "params")
        if params.keys() - PARAMS:
            raise ValueError(f"{name}: unknown parameters {sorted(params.keys() - PARAMS)}")
        for key, value in params.items():
            if key in ("stride_m", "known_distance"):
                if isinstance(value, bool) or not isinstance(value, (float, int)) or not math.isfinite(value) or value <= 0:
                    raise ValueError(f"{name}: {key} must be a finite positive number")
            elif key == "yaw_axis":
                if value not in ("x", "y", "z"):
                    raise ValueError(f"{name}: yaw_axis must be x, y or z")
            elif not isinstance(value, str) or not value or len(value) > 160:
                raise ValueError(f"{name}: heading_source must be declared")
        fixture = job.get("fixture")
        if fixture is not None:
            if fixture != "canonical_imu" or dataset != "canonical" or "input" in inputs:
                raise ValueError(f"{name}: only canonical IMU without an input accepts a fixture")
        if fixture is None and not inputs and unified.RUNNERS[dataset]["input"] != "pinned_public":
            raise ValueError(f"{name}: local dataset requires inputs or declared fixture")
        if "heading_csv" in inputs and not params.get("heading_source"):
            raise ValueError(f"{name}: measured heading file needs heading_source provenance")
        if params.get("heading_source") and "heading_csv" not in inputs:
            raise ValueError(f"{name}: heading_source without measured heading CSV")
        if "known_distance" in params and dataset != "phone_csv":
            raise ValueError(f"{name}: surveyed distance only permitted for phone_csv")
        checks = job.get("checks", [])
        if not isinstance(checks, list) or len(checks) > 30:
            raise ValueError(f"{name}: checks must be a list (max 30)")
        for check in checks:
            _dict(check, "check")
            if check.keys() - CHECK_KEYS or not isinstance(check.get("field"), str):
                raise ValueError(f"{name}: malformed check")
            if (not re.fullmatch(r"[A-Za-z_][A-Za-z_0-9]*(?:\.[A-Za-z_][A-Za-z_0-9]*){0,5}", check["field"])
                    or any(part.startswith("_") for part in check["field"].split("."))):
                raise ValueError(f"{name}: invalid check path")
            ops = set(check) - {"field"}
            if not ops or ("equals" in ops and len(ops) > 1):
                raise ValueError(f"{name}: use equals OR min/max")
            if "equals" in ops:
                if not isinstance(check["equals"], (bool, int, float, str)) or (
                    isinstance(check["equals"], float) and not math.isfinite(check["equals"])):
                    raise ValueError(f"{name}: invalid equals check")
            for op in ("min", "max"):
                if op in check and (isinstance(check[op], bool) or
                                    not isinstance(check[op], (float, int)) or
                                    not math.isfinite(check[op])):
                    raise ValueError(f"{name}: {op} requires a finite number")
        timeout = job.get("timeout_s", 900)
        if isinstance(timeout, bool) or not isinstance(timeout, int) or not 1 <= timeout <= 7200:
            raise ValueError(f"{name}: timeout_s must be 1..7200")
    return suite


def job_inputs(job: dict, suite_path: Path) -> dict[str, Path]:
    # Paths are resolved against the suite file, never the caller's cwd.
    return {name: (suite_path.parent / value).resolve()
            for name, value in job.get("inputs", {}).items()}


def readiness(job: dict, suite_path: Path) -> dict:
    files = job_inputs(job, suite_path)
    missing = [name for name, path in files.items()
               if not (path.is_dir() if name == "root" else path.is_file())]
    spec = unified.plan(job["dataset"], job["engine"])
    return {"id": job["id"], "dataset": job["dataset"], "engine": spec["requested_engine"],
            "capability": spec["capability"], "reference_kind": spec["reference_kind"],
            "accuracy_claim_level": spec["accuracy_claim_level"],
            "fixture": job.get("fixture"), "ready": not missing,
            "missing_inputs": missing, "limitations": spec["limitations"]}
