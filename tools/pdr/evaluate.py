#!/usr/bin/env python3
"""Evaluate PDR against independently measured local EN ground truth.

No similarity transform, yaw optimization or scale fitting is allowed. Only
subtract the first matched truth origin. Requires SAME timebase. Usage:
  python3 tools/pdr/evaluate.py --pred path.csv --truth independent.csv \
      --reference-distance-m 100 --out results.json
Truth CSV columns: t_ns,east_m,north_m; timestamps from same clock origin.
Only evaluate when a complete, independently surveyed reference exists.
"""
import argparse
import bisect
import csv
import json
import math
from pathlib import Path


def load(path):
    with open(path, newline="", encoding="utf8") as f:
        data = [(int(row["t_ns"]), float(row["east_m"]), float(row["north_m"]))
                for row in csv.DictReader(f)]
    if len(data) < 2 or any(t1 >= t2 for (t1, *_), (t2, *_) in zip(data, data[1:])):
        raise ValueError("At least two points, with strictly increasing timestamps, required")
    if not all(math.isfinite(n) for row in data for n in row[1:]):
        raise ValueError("Nonfinite position")
    return data


def interpolate(known, t):
    ts = [v[0] for v in known]
    i = bisect.bisect_left(ts, t)
    if i == 0:
        return known[0][1:] if t == ts[0] else None
    if i >= len(known):
        return known[-1][1:] if t == ts[-1] else None
    if ts[i] == t:
        return known[i][1:]
    a, b = known[i-1], known[i]
    f = (t-a[0])/(b[0]-a[0])
    return (a[1]+f*(b[1]-a[1]), a[2]+f*(b[2]-a[2]))


def score(pred, truth, reference_distance_m=None):
    if pred[0][0] < truth[0][0] or pred[-1][0] > truth[-1][0]:
        raise ValueError("Truth must span entire predicted time window")
    truth_start = interpolate(truth, pred[0][0])
    px0, py0 = pred[0][1:]
    sq = []
    matched = 0
    final_e = None
    for t, x, y in pred:
        target = interpolate(truth, t)
        if target is None:
            continue
        dx = (x-px0)-(target[0]-truth_start[0])
        dy = (y-py0)-(target[1]-truth_start[1])
        d = math.hypot(dx, dy)
        sq.append(d*d)
        final_e = d
        matched += 1
    if matched != len(pred):
        raise ValueError("Incomplete matched trajectory")
    # Path distance of pred from sampled 2D trajectory is not the PDR distance
    # if some steps had no heading. Pass an independent declared distance to
    # calculate absolute distance error.
    travelled_pred = sum(math.hypot(x2-x1,y2-y1)
                         for (_,x1,y1),(_,x2,y2) in zip(pred,pred[1:]))
    result = {
        "sample_count": matched,
        "rmse_m_unaligned": math.sqrt(sum(sq)/len(sq)),
        "final_error_m": final_e,
        "charted_path_distance_m": travelled_pred,
        "warning": "No rotation or scale fit; assumes east/north heading is already correct."
    }
    if reference_distance_m is not None:
        if reference_distance_m <= 0:
            raise ValueError("reference distance must be positive")
        result["charted_path_distance_error_pct"] = 100*(travelled_pred/reference_distance_m-1)
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--pred",type=Path,required=True)
    parser.add_argument("--truth",type=Path,required=True)
    parser.add_argument("--reference-distance-m",type=float)
    parser.add_argument("--out",type=Path)
    args = parser.parse_args()
    report = score(load(args.pred),load(args.truth),args.reference_distance_m)
    body = json.dumps(report,indent=2,sort_keys=True)
    if args.out:
        args.out.parent.mkdir(parents=True,exist_ok=True)
        args.out.write_text(body+"\n")
    print(body)


if __name__ == "__main__":
    main()
