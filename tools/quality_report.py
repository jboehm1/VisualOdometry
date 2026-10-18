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


def analyze_motion(path):
    """High-cadence *tracked* poses; gaps are missing observations, not zero motion."""
    count=0;intervals=[];last_ns=None;segments=set();has_explicit_segments=False
    with Path(path).open(encoding="utf-8-sig",newline="") as handle:
        reader=csv.DictReader(handle)
        if not {"frame_time_ns","metric_x","metric_y","metric_z"}.issubset(reader.fieldnames or []):
            raise ValueError("Missing _motion.csv timestamps/metric coordinates")
        has_explicit_segments="odometer_segment" in reader.fieldnames
        last_seg=0
        for row in reader:
            if count>=1_000_000:raise ValueError("Motion CSV too long for summary")
            try:
                ns=int(row["frame_time_ns"])
                if ns<=0 or (last_ns is not None and ns<=last_ns):
                    raise ValueError("Nonmonotonic frame timestamps")
                xyz=tuple(float(row[k]) for k in ("metric_x","metric_y","metric_z"))
                if not all(math.isfinite(v) for v in xyz):raise ValueError("Nonfinite pose")
                if has_explicit_segments:
                    seg=int(row["odometer_segment"])
                    if seg<=0 or seg<last_seg:raise ValueError("Invalid segment transition")
                    segments.add(seg)
                    last_seg=seg
            except (ValueError,TypeError) as exc:
                raise ValueError("Invalid motion row") from exc
            if last_ns is not None:intervals.append((ns-last_ns)/1e9)
            last_ns=ns;count+=1
    return {"status":"TRACKED_POSES_ONLY" if count else "NO_DATA",
            "pose_samples":count,
            "max_frame_interval_s":round(max(intervals,default=0),4),
            "intervals_over_100ms":sum(v>.1 for v in intervals),
            "intervals_over_700ms":sum(v>.7 for v in intervals),
            "median_interval_ms":round(statistics.median(intervals)*1000,3) if intervals else None,
            "explicit_segment_ids_available":has_explicit_segments,
            "segment_count":len(segments) if has_explicit_segments else None,
            "limitations":"Only successful tracked and logged pose samples; missing frames can be caused by processing stalls or tracking loss. No accuracy or battery measurement."}


def analyze(file_path, scene="unspecified", motion_csv=None):
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
        result={"scene": scene, "samples": 0, "status": "NO_DATA", "accuracy_ground_truth": "NOT_AVAILABLE"}
        if motion_csv is not None:result["motion"]=analyze_motion(motion_csv)
        return result
    intervals = [(b["ns"] - a["ns"]) / 1e9 for a, b in zip(rows, rows[1:])]
    tracked = [r for r in rows if r["state"] == "TRACKING"]
    frames_hz = [r["hz"] for r in tracked if r["hz"] > 0]
    result={"scene": scene, "samples": len(rows), "status": "DIAGNOSTIC_ONLY",
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
    if motion_csv is not None:result["motion"]=analyze_motion(motion_csv)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", type=Path)
    parser.add_argument("--scene", default="unspecified")
    parser.add_argument("--out", type=Path)
    parser.add_argument("--motion", type=Path,help="Optional high-rate _motion.csv diagnostics")
    args = parser.parse_args()
    summary = json.dumps(analyze(args.csv, args.scene, args.motion), indent=2, ensure_ascii=False, sort_keys=True)
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(summary + "\n", encoding="utf-8")
    print(summary)


if __name__ == "__main__":
    main()
