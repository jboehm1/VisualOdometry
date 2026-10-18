#!/usr/bin/env python3
"""Audit-only evidence envelope for VECTOR VIO benchmark JSON.

A manifest is a declaration, NOT independent proof of precision. A successful
fixture, synthetic trajectory or GNSS-as-truth run never becomes a phone accuracy
claim. Never pool results from different estimator engines or trajectories.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
from pathlib import Path

ENGINES = frozenset({
    "arcore-android", "pdr-java-android", "pdr-python-baseline",
    "orb-slam3", "openvins", "vector-experimental-vo"
})
EVIDENCE = frozenset({"fixture", "synthetic", "real-offline", "physical-phone"})
TRUTH = frozenset({
    "none", "synthetic-fixture", "gnss-consumer", "surveyed-control-points",
    "independent-motion-capture", "reference-ins"
})
ALIGNMENT = frozenset({"none", "se3-rigid", "yaw-translation", "translation-only"})
TIMEBASE = frozenset({"elapsed-realtime-ns", "camera-ns", "unix-ns", "relative-s"})
MAX_BYTES = 5_000_000


def load_json(path: Path):
    if not path.is_file() or path.stat().st_size > MAX_BYTES:
        raise ValueError("Missing/oversized JSON input")
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError("Duplicate JSON key: " + key)
            result[key] = value
        return result
    data = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique,
                      parse_constant=lambda x: (_ for _ in ()).throw(ValueError("Nonfinite JSON: " + x)))
    if not isinstance(data, dict):
        raise ValueError("Top-level JSON must be object")
    return data


def _key(d, key, cls):
    x = d.get(key)
    if isinstance(x, bool) and cls is int:
        raise ValueError(key + ": bool is not integer")
    if not isinstance(x, cls):
        raise ValueError(key + ": expected " + str(cls))
    return x


def _optional_nonnegative(obj, key):
    x = obj.get(key)
    if x is None:
        return
    if type(x) not in (float, int) or not math.isfinite(x) or x < 0:
        raise ValueError(key + ": finite nonnegative number required")


def validate_manifest(m):
    _key(m, "run_id", str)
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{2,99}", m["run_id"]):
        raise ValueError("run_id: restricted identifier")
    if _key(m, "engine", str) not in ENGINES:
        raise ValueError("Unknown estimator engine")
    if not re.fullmatch(r"[a-f0-9]{40}", _key(m, "engine_commit", str)):
        raise ValueError("engine_commit must be a full git SHA")
    if _key(m, "evidence", str) not in EVIDENCE:
        raise ValueError("Unknown evidence level")
    if _key(m, "truth_type", str) not in TRUTH:
        raise ValueError("Unknown truth_type")
    if _key(m, "timebase", str) not in TIMEBASE:
        raise ValueError("Unknown timebase")
    if _key(m, "alignment", str) not in ALIGNMENT:
        raise ValueError("Unknown alignment")
    if type(_key(m, "scale_fitted", bool)) is not bool:
        raise ValueError("scale_fitted must be bool")
    if not _key(m, "dataset_id", str).strip():
        raise ValueError("Missing dataset ID")
    if not _key(m, "config_id", str).strip():
        raise ValueError("Missing estimator configuration identifier")
    _optional_nonnegative(m, "time_offset_ns")
    if m["evidence"] in {"real-offline", "physical-phone"}:
        if not _key(m, "license_or_consent_reviewed", bool):
            raise ValueError("Consent/license status must be reviewed before real claims")
        if not m.get("dataset_sha256") or not re.fullmatch(r"[a-f0-9]{64}", m["dataset_sha256"]):
            raise ValueError("Real data requires dataset SHA-256")
    elif "dataset_sha256" in m and not re.fullmatch(r"[a-f0-9]{64}", m["dataset_sha256"]):
        raise ValueError("Invalid dataset SHA-256")

    # This cannot confirm true independence; it only enforces explicit declaration.
    independent = m["truth_type"] in {
        "surveyed-control-points", "independent-motion-capture", "reference-ins"
    }
    benchmark = m["evidence"] in {"real-offline", "physical-phone"}
    if m["engine"] == "pdr-python-baseline":
        # The Python baseline is never a physical Android PDR test.
        phone_engine = False
    else:
        phone_engine = m["engine"] in {"arcore-android", "pdr-java-android"}
    if m["evidence"] == "physical-phone" and not phone_engine:
        raise ValueError("Physical-phone evidence requires actual Android engine")
    if m["alignment"] == "se3-rigid" and m["engine"] == "pdr-python-baseline":
        raise ValueError("Python planar baseline cannot claim full SE(3)")
    return independent, benchmark


def _check_metrics(metrics):
    if not isinstance(metrics, dict):
        raise ValueError("Metrics object required")
    # Flat key-value numeric metrics only, bounded and nonnegative.
    for key, value in metrics.items():
        if not re.fullmatch(r"[a-z][a-z0-9_]{0,79}", key):
            raise ValueError("Metric key malformed: " + str(key))
        if value is None:
            continue
        if type(value) not in (int, float, bool):
            raise ValueError("Metric " + key + " must be scalar")
        if type(value) is not bool and (not math.isfinite(value) or value < 0):
            # Signed yaw offset can be reported only under signed key.
            if key not in {"alignment_yaw_deg", "yaw_error_deg", "time_offset_ns"} or not math.isfinite(value):
                raise ValueError("Nonfinite or negative metric: " + key)


def build(manifest, metrics, source_sha256=None):
    independent, real = validate_manifest(manifest)
    _check_metrics(metrics)
    if source_sha256 is not None and not re.fullmatch(r"[a-f0-9]{64}", source_sha256):
        raise ValueError("Invalid metrics hash")
    warnings = []
    if not independent:
        warnings.append("Truth is absent, synthetic, or not independent: never claim surveyed accuracy.")
    if manifest["scale_fitted"]:
        warnings.append("Scale has been fitted; results cannot substantiate metric-scale accuracy.")
    if manifest["alignment"] != "none":
        warnings.append("Alignment removes global pose offset/rotation; absolute geography is not tested.")
    if manifest["engine"] == "pdr-python-baseline":
        warnings.append("Python baseline output does not validate Android Java PDR engine.")
    if manifest["evidence"] in {"fixture", "synthetic"}:
        warnings.append("Synthetic/fixture data does not validate physical-device performance.")
    if manifest["truth_type"] == "gnss-consumer":
        warnings.append("Consumer GNSS can have correlated bias; it is not independent cm-level truth.")
    # "READY_FOR_REVIEW" is not production qualification or certified navigation.
    tier = ("REAL_INDEPENDENT_TRUTH_REVIEW" if independent and real and not manifest["scale_fitted"]
            else "DIAGNOSTIC_OR_SYNTHETIC_ONLY")
    return {
        "schema": "vector-vio-benchmark-evidence/1",
        "run_id": manifest["run_id"],
        "engine": manifest["engine"],
        "engine_commit": manifest["engine_commit"],
        "dataset_id": manifest["dataset_id"],
        "config_id": manifest["config_id"],
        "timebase": manifest["timebase"],
        "alignment": manifest["alignment"],
        "scale_fitted": manifest["scale_fitted"],
        "evidence": manifest["evidence"],
        "truth_type": manifest["truth_type"],
        "dataset_sha256": manifest.get("dataset_sha256"),
        "metrics_json_sha256": source_sha256,
        "metrics": metrics,
        "evidence_tier": tier,
        "warnings": warnings,
        "provenance_is_self_reported": True,
        "qualified_for_safety_navigation": False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--metrics", type=Path, required=True)
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    manifest = load_json(args.manifest)
    metrics = load_json(args.metrics)
    sha = hashlib.sha256(args.metrics.read_bytes()).hexdigest()
    result = build(manifest, metrics, sha)
    output = json.dumps(result, sort_keys=True, indent=2, ensure_ascii=False, allow_nan=False) + "\n"
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(output, encoding="utf8")
    print(output, end="")


if __name__ == "__main__":
    main()
