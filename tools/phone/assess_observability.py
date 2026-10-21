#!/usr/bin/env python3
"""Analyze **actual** _visual_observability.csv exports without inventing ATE.

Printed fractions are device-reported state coverage on the recorded sampled
windows. They are NOT accuracy, confidence, probability or independent GT.
"""
from __future__ import annotations
import argparse
import csv
import json
import math
from collections import Counter
from pathlib import Path

STATES = {"VISUAL_OBSERVED", "VISUAL_CAUTION", "IMU_ONLY_NO_POSITION", "POSITION_UNKNOWN"}
EXPECTED = {"frame_time_ns", "elapsed_ns", "arcore_tracking", "imu_fresh",
            "camera_quality", "gate_state", "arcore_pose_available", "inertial_translation",
            "visual_age_ms", "reason"}


class InputError(ValueError):
    pass


def analyze(path: Path, *, source: str = "PHONE_UNVERIFIED") -> dict:
    if not path.is_file() or path.stat().st_size > 50_000_000:
        raise InputError("Missing or oversized visibility CSV")
    if source not in ("PHONE_UNVERIFIED", "SYNTHETIC_FIXTURE", "ARCORE_REPLAY"):
        raise InputError("Specify actual phone, synthetic fixture or real ARCore replay provenance")
    count = Counter()
    reasons = Counter()
    first = last = None
    coverage_ns = Counter()
    prev = None
    samples = 0
    with path.open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        if not reader.fieldnames or not EXPECTED.issubset(reader.fieldnames):
            raise InputError("Missing required observability columns")
        for row in reader:
            samples += 1
            if samples > 200_000:
                raise InputError("Too many samples")
            try:
                t = int(row["elapsed_ns"])
                ft = int(row["frame_time_ns"])
                age = int(row["visual_age_ms"])
            except (ValueError, TypeError) as exc:
                raise InputError("Invalid integer timestamp") from exc
            state = row["gate_state"]
            if t <= 0 or ft <= 0 or state not in STATES:
                raise InputError("Unrecognized state or nonpositive clock")
            if last is not None and t <= last:
                raise InputError("Nonmonotonic elapsed time: cannot calculate coverage")
            if row["inertial_translation"].lower() != "false":
                raise InputError("Invariant violation: inertial-only position forbidden")
            visual_pose = row["arcore_pose_available"].lower() == "true"
            if visual_pose != (state in {"VISUAL_OBSERVED", "VISUAL_CAUTION"}):
                raise InputError("ARCore pose availability inconsistent with gate state")
            if age < 0:
                raise InputError("Negative age")
            count[state] += 1
            reasons[row["reason"]] += 1
            # For 1Hz logs, only integrate *adjacent* reliable time windows;
            # do not assume tracking continued across app pauses / missing rows.
            if prev is not None:
                dt = t - last
                if 0 < dt <= 2_000_000_000:
                    coverage_ns[prev] += dt
            if first is None:
                first = t
            last = t
            prev = state
    if samples == 0:
        raise InputError("No samples")
    total_time = sum(coverage_ns.values())
    return {
        "schema": 1,
        "engine": "ARCORE_VISUAL_OBSERVABILITY_DIAGNOSTIC",
        "source_provenance": source,
        "independent_ground_truth": False,
        "pose_accuracy_metric": None,
        "ate_m": None,
        "rpe_m": None,
        "time_coverage_definition": "sum consecutive delta elapsed_ns <=2s credited to preceding gate state; gaps excluded",
        "sample_count": samples,
        "observed_elapsed_span_s": round((last - first) / 1e9, 3),
        "covered_elapsed_s": round(total_time / 1e9, 3),
        "sample_states": dict(count),
        "reasons": dict(reasons),
        "state_seconds": {key: round(coverage_ns[key] / 1e9, 3) for key in sorted(STATES)},
        "visual_observed_fraction_of_covered_time":
            round(coverage_ns["VISUAL_OBSERVED"] / total_time, 6) if total_time else None,
        "caution": "Tracking availability is NOT metric pose truth, independent GT, navigation safety or smoke penetration.",
    }


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("csv", type=Path, help="Real phone export _visual_observability.csv")
    p.add_argument("--source", choices=["PHONE_UNVERIFIED", "SYNTHETIC_FIXTURE", "ARCORE_REPLAY"],
                   default="PHONE_UNVERIFIED")
    p.add_argument("--output", type=Path, help="Optional result JSON destination")
    args = p.parse_args()
    result = analyze(args.csv, source=args.source)
    text = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text, encoding="utf-8")
    else:
        print(text, end="")


if __name__ == "__main__":
    main()
