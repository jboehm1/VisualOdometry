#!/usr/bin/env python3
"""Strict, camera-independent PDR evaluator.

The estimator's canonical *total walked distance* is PdrEngine.distanceM,
from Java PdrReplay summary.json; it is NOT a path length reconstructed from
a partially observed heading trace. No rotation, scale, time shift, or SE(3)
alignment is fit using the truth trajectory.

Examples:
  bash tools/pdr/run.sh session/events.csv > session/summary.json
  python3 tools/pdr/evaluate.py --summary session/summary.json \
      --reference-distance-m 100
  # When local magnetic EN truth and a synchronized monotonic clock exist:
  java ... PdrReplay session/events.csv /tmp/pdr_path.csv > /tmp/summary.json
  python3 tools/pdr/evaluate.py --pred /tmp/pdr_path.csv \
      --truth /independent/truth.csv --summary /tmp/summary.json \
      --reference-distance-m 100 --out /tmp/results.json
"""
import argparse
import bisect
import csv
import json
import math
from pathlib import Path


def _finite_float(value, field):
    try:
        numeric = float(value)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"Invalid {field}: {value!r}") from exc
    if not math.isfinite(numeric):
        raise ValueError(f"Nonfinite {field}")
    return numeric


def _bool_gap(value):
    # Explicitly reject malformed truth/estimate rows rather than silently
    # connecting a discontinuous track.
    if value is None or value.strip().lower() in ("", "false", "0"):
        return False
    if value.strip().lower() in ("true", "1"):
        return True
    raise ValueError(f"Unrecognized gap_before value: {value!r}")


def load(path):
    """Read either t_ns,east_m,north_m or path with optional gap_before.

    Coordinates are local magnetic East/North metres, not WGS84 GPS positions.
    Do not normalize timestamps: the two files must share a documented clock.
    """
    with open(path, newline="", encoding="utf-8-sig") as stream:
        reader = csv.DictReader(stream)
        if not reader.fieldnames or not {"t_ns", "east_m", "north_m"}.issubset(reader.fieldnames):
            raise ValueError("CSV must contain t_ns,east_m,north_m")
        rows = []
        last_ns = None
        for index, row in enumerate(reader, 2):
            try:
                stamp = int(row["t_ns"])
            except (TypeError, ValueError) as exc:
                raise ValueError(f"Invalid timestamp at CSV line {index}") from exc
            if stamp <= 0 or (last_ns is not None and stamp <= last_ns):
                raise ValueError(f"Timestamps must be positive and strictly increasing (line {index})")
            east = _finite_float(row["east_m"], f"east_m at line {index}")
            north = _finite_float(row["north_m"], f"north_m at line {index}")
            rows.append((stamp, east, north, _bool_gap(row.get("gap_before"))))
            last_ns = stamp
    if len(rows) < 2:
        raise ValueError("At least two position samples required")
    return rows


def _validate_points(points, label):
    if len(points) < 2:
        raise ValueError(f"{label} requires two or more points")
    last = None
    for p in points:
        if len(p) < 3 or type(p[0]) is not int or p[0] <= 0:
            raise ValueError(f"{label} has invalid timestamp")
        if last is not None and p[0] <= last:
            raise ValueError(f"{label} must have strictly increasing timestamps")
        if not math.isfinite(p[1]) or not math.isfinite(p[2]):
            raise ValueError(f"{label} contains nonfinite positions")
        last = p[0]


def _sample(known, t, stamps):
    i = bisect.bisect_left(stamps, t)
    if i >= len(stamps):
        return known[-1][1:3] if t == stamps[-1] else None
    if stamps[i] == t:
        return known[i][1:3]
    if i == 0:
        return None
    a, b = known[i - 1], known[i]
    fraction = (t - a[0]) / (b[0] - a[0])
    return (a[1] + fraction * (b[1] - a[1]),
            a[2] + fraction * (b[2] - a[2]))


def interpolate(known, t):
    """Retained for existing callers; no truth extrapolation is allowed."""
    return _sample(known, t, [p[0] for p in known])


def distance_metrics(distance_m, reference_m, summary=None):
    """Distance-only experiment; no map, heading or full trajectory required."""
    if distance_m < 0 or not math.isfinite(distance_m):
        raise ValueError("Estimated distance must be finite and nonnegative")
    if reference_m <= 0 or not math.isfinite(reference_m):
        raise ValueError("Independent reference distance must be finite and positive")
    diff = distance_m - reference_m
    result = {
        "pdr_total_distance_m": distance_m,
        "reference_total_distance_m": reference_m,
        "pdr_total_distance_error_m": diff,
        "pdr_total_distance_error_pct": 100 * diff / reference_m,
        "distance_source": "PdrEngine.distanceM (accepted steps x calibrated stride)",
        "reference_source": "operator-specified independent distance, NOT estimator input",
    }
    if summary is not None:
        for key in ("steps", "steps_without_heading", "rows", "rejected_rows"):
            if key in summary:
                value = summary[key]
                if type(value) is not int or value < 0:
                    raise ValueError("Invalid summary field: " + key)
                result[key] = value
        if (result.get("steps_without_heading", 0) >
                result.get("steps", float("inf"))):
            raise ValueError("steps_without_heading cannot exceed steps")
    return result


def score(pred, truth, reference_distance_m=None, total_distance_m=None):
    """Evaluate a local magnetic frame against independent synchronized truth.

    Unlike a path-only distance comparison, PDR total distance is available
    even when heading fails. Any heading gap is flagged, not interpolated.
    """
    _validate_points(pred, "Prediction")
    _validate_points(truth, "Truth")
    if pred[0][0] < truth[0][0] or pred[-1][0] > truth[-1][0]:
        raise ValueError("Truth must span entire predicted time window")
    truth_t = [p[0] for p in truth]
    initial_truth = _sample(truth, pred[0][0], truth_t)
    origin_x, origin_y = pred[0][1:3]
    squared = []
    charted_distance = 0.0
    broken_connections = 0
    for i, point in enumerate(pred):
        t, x, y = point[:3]
        gap = len(point) > 3 and bool(point[3])
        target = _sample(truth, t, truth_t)
        if target is None:
            raise ValueError("Cannot extrapolate ground truth")
        dx = (x - origin_x) - (target[0] - initial_truth[0])
        dy = (y - origin_y) - (target[1] - initial_truth[1])
        squared.append(math.hypot(dx, dy) ** 2)
        if i > 0:
            if gap:
                broken_connections += 1
            else:
                charted_distance += math.hypot(x - pred[i-1][1], y - pred[i-1][2])
    result = {
        "sample_count": len(pred),
        "rmse_m_unaligned": math.sqrt(sum(squared) / len(squared)),
        "final_error_m": math.sqrt(squared[-1]),
        "charted_path_distance_m": charted_distance,
        "path_breaks_due_to_missing_heading": broken_connections,
        "heading_gaps_present": broken_connections > 0,
        "alignment": "subtract initial translation ONLY; no heading, time, or scale fit",
        "warning": "Trajectory can be partial; distance from path.csv is NOT PDR walked distance."
    }
    if reference_distance_m is not None:
        if reference_distance_m <= 0 or not math.isfinite(reference_distance_m):
            raise ValueError("Reference distance must be finite and positive")
        # Preserve the old explicit charted metric only for fully connected paths.
        # This is NOT the total PDR odometer error; prefer the independent summary.
        if broken_connections == 0:
            result["charted_path_distance_error_pct"] = 100 * (
                charted_distance / reference_distance_m - 1)
        if total_distance_m is not None:
            result.update(distance_metrics(total_distance_m, reference_distance_m))
    elif total_distance_m is not None:
        raise ValueError("total_distance_m requires independent reference_distance_m")
    return result


def _summary(path):
    with open(path, encoding="utf-8") as stream:
        data = json.load(stream)
    if not isinstance(data, dict) or "distance_m" not in data:
        raise ValueError("Summary must contain canonical distance_m from PdrReplay")
    data["distance_m"] = _finite_float(data["distance_m"], "distance_m")
    return data


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pred", type=Path, help="Path CSV exported or replayed by the Java engine")
    parser.add_argument("--truth", type=Path, help="Independent synchronized magnetic EN reference CSV")
    parser.add_argument("--summary", type=Path, help="Canonical Java PdrReplay summary.json")
    parser.add_argument("--reference-distance-m", type=float, help="Independently measured course distance")
    parser.add_argument("--out", type=Path, help="Machine-readable report output JSON")
    args = parser.parse_args(argv)
    if bool(args.pred) != bool(args.truth):
        parser.error("--pred and --truth must be supplied together")
    if args.reference_distance_m is not None and args.summary is None:
        parser.error("Distance accuracy requires --summary; chart path length is not the PDR odometer")
    if not (args.pred and args.truth) and not (
            args.summary is not None and args.reference_distance_m is not None):
        parser.error("Provide (--pred and --truth) or (--summary and --reference-distance-m)")
    try:
        summary = _summary(args.summary) if args.summary else None
        total_distance = summary["distance_m"] if summary else None
        if args.pred:
            result = score(load(args.pred), load(args.truth),
                           args.reference_distance_m, total_distance)
        else:
            result = distance_metrics(total_distance, args.reference_distance_m, summary)
        if summary is not None and args.pred:
            result["engine"] = "Java PdrEngine via PdrReplay"
            result["replay_rows"] = summary.get("rows")
            result["steps_without_heading"] = summary.get("steps_without_heading")
    except (ValueError, OSError, json.JSONDecodeError) as exc:
        parser.error(str(exc))
    body = json.dumps(result, indent=2, sort_keys=True, allow_nan=False) + "\n"
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(body, encoding="utf-8")
    print(body, end="")
    return result


if __name__ == "__main__":
    main()
