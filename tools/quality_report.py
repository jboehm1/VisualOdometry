#!/usr/bin/env python3
"""Deterministic diagnosis of VECTOR VIO _tracking_quality.csv.

No independent reference => no ATE, RPE, absolute position or safety claims.
CSV emitted by MainActivity at most once per second while recording.
Usage: python3 tools/quality_report.py session_tracking_quality.csv --scene forest_ground
"""
import argparse
import csv
import json
import math
import statistics
from pathlib import Path


def analyze(file_path, scene="unspecified"):
    rows = []
    with Path(file_path).open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        required = {"wall_time_ms", "frame_time_ns", "tracking_state", "pose_hz", "segment"}
        if not required.issubset(reader.fieldnames or []):
            raise ValueError("Missing quality telemetry columns: " + str(sorted(required - set(reader.fieldnames or []))))
        for row in reader:
            if len(rows) >= 1_000_000:
                raise ValueError("Refusing more than one million quality rows")
            try:
                t = int(row["frame_time_ns"])
                hz = float(row["pose_hz"])
                segment = int(row["segment"])
                if t <= 0 or segment < 0 or not math.isfinite(hz) or hz < 0 or hz > 1000:
                    raise ValueError("Invalid timestamp/segment/hz")
            except (TypeError, ValueError) as exc:
                raise ValueError("Invalid quality telemetry row") from exc
            state = row["tracking_state"].strip().upper()
            if state not in {"TRACKING", "PAUSED", "STOPPED"}:
                raise ValueError("Unexpected tracking state " + state)
            if rows and t <= rows[-1]["ns"]:
                raise ValueError("Non-monotonic tracking telemetry")
            rows.append({"ns": t, "hz": hz, "seg": segment, "state": state,
                         "failure": row.get("tracking_reason", "")})
    if not rows:
        return {"scene": scene, "samples": 0, "status": "NO_DATA", "accuracy_ground_truth": "NOT_AVAILABLE"}
    intervals = [(b["ns"] - a["ns"]) / 1e9 for a, b in zip(rows, rows[1:])]
    tracked = [r for r in rows if r["state"] == "TRACKING"]
    frames_hz = [r["hz"] for r in tracked if r["hz"] > 0]
    return {"scene": scene, "samples": len(rows), "status": "DIAGNOSTIC_ONLY",
            "tracking_sample_fraction": round(len(tracked) / len(rows), 5),
            "observed_seconds": round((rows[-1]["ns"] - rows[0]["ns"]) / 1e9, 3),
            "max_telemetry_interval_s": round(max(intervals, default=0), 3),
            "intervals_over_2s": sum(dt > 2 for dt in intervals),
            "distinct_segments": len({r["seg"] for r in rows}),
            "median_tracked_pose_hz": round(statistics.median(frames_hz), 2) if frames_hz else None,
            "failure_reason_counts": {state: sum(r["failure"] == state for r in rows)
                                      for state in sorted({r["failure"] for r in rows if r["failure"]})},
            "accuracy_ground_truth": "NOT_AVAILABLE",
            "limitations": "1Hz diagnostic sampling misses shorter dropouts. GNSS accuracy and ARCore tracking states are not independent truth."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", type=Path)
    parser.add_argument("--scene", default="unspecified")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    summary = json.dumps(analyze(args.csv, args.scene), indent=2, ensure_ascii=False, sort_keys=True)
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(summary + "\n", encoding="utf-8")
    print(summary)


if __name__ == "__main__":
    main()
