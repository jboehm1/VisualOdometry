#!/usr/bin/env python3
"""Fail-closed, engine-agnostic comparable-run scoreboard.

This DOES NOT run estimators, derive truth, synchronize clocks or compute
ATE/RPE. It accepts ONLY measured, independently scored run reports.
Do not use scores fitted with end-of-run truth for production claims.
"""
import argparse
import json
import math
import re
from pathlib import Path

REQUIRED = ("engine", "engine_sha", "dataset_sha256", "hardware",
            "capture_protocol", "clock_alignment", "pose_alignment",
            "truth_independent", "reference_frame_verified", "metrics",
            "truth_source", "pose_frame", "truth_frame", "has_gt_in_estimator",
            "report_source", "evaluation_protocol_sha256")
METRICS = ("ate_rmse_m", "rpe_translation_rmse_m", "coverage", "latency_p95_ms",
           "cpu_core_seconds_per_s", "energy_j_per_min")
CAPTURE = ("same_raw", "repeated_surveyed_route")


def _number(value, name, *, allow_zero=True):
    if type(value) not in (int, float) or not math.isfinite(value):
        raise ValueError(f"{name} must be a finite measured number")
    if value < 0 or (not allow_zero and value == 0):
        raise ValueError(f"{name} cannot be negative/zero")
    return float(value)


def validate_run(run):
    if not isinstance(run, dict):
        raise ValueError("each run must be an object")
    for key in REQUIRED:
        if key not in run:
            raise ValueError("missing " + key)
    identity=("engine", "engine_sha", "dataset_sha256", "hardware", "truth_source",
              "pose_frame", "truth_frame", "report_source", "evaluation_protocol_sha256")
    if any(not isinstance(run[k],str) or not run[k].strip() for k in identity):
        raise ValueError("missing identity")
    for key, pattern in (("engine_sha",r"[0-9a-f]{40}"),
                         ("dataset_sha256",r"[0-9a-f]{64}"),
                         ("evaluation_protocol_sha256",r"[0-9a-f]{64}"),
                         ("report_source",r"sha256:[0-9a-f]{64}")):
        if not re.fullmatch(pattern,run[key]):
            raise ValueError("invalid immutable identity " + key)
    if run["capture_protocol"] not in CAPTURE:
        raise ValueError("unknown capture protocol")
    if run["capture_protocol"] == "repeated_surveyed_route":
        cohort=run.get("comparison_group_sha256")
        if not isinstance(cohort,str) or not re.fullmatch(r"[0-9a-f]{64}",cohort):
            raise ValueError("repeated routes require pinned comparison_group_sha256")
    if run["clock_alignment"] != "independent_pre_calibrated":
        raise ValueError("truth-assisted or unknown clock alignment")
    if run["pose_alignment"] not in ("identity", "surveyed_fixed_se3"):
        raise ValueError("truth-fit alignment not allowed")
    if run["truth_independent"] is not True or run["reference_frame_verified"] is not True:
        raise ValueError("no verified independent ground truth / frame")
    m=run["metrics"]
    if not isinstance(m,dict):
        raise ValueError("invalid metrics")
    for key in METRICS:
        if key not in m:
            raise ValueError("missing metric " + key)
        m[key]=_number(m[key],key,allow_zero=key not in ("latency_p95_ms","energy_j_per_min"))
    if not 0 <= m["coverage"] <= 1:
        raise ValueError("coverage must be [0,1]")
    for count in ("frames_total", "frames_tracked", "reference_matches"):
        if count not in m or type(m[count]) is not int or m[count] < 0:
            raise ValueError("missing measured count " + count)
    if m["frames_tracked"] > m["frames_total"] or m["reference_matches"] > m["frames_tracked"]:
        raise ValueError("invalid match/frame count")
    if m["frames_total"] == 0 or m["reference_matches"] < 30:
        raise ValueError("insufficient actual frames and matched truth")
    # Prohibit misleading impression that partial high-confidence track = 100%.
    if m["coverage"] > m["frames_tracked"]/m["frames_total"] + 1e-12:
        raise ValueError("coverage conflicts with tracked counts")
    if run.get("metric_scale") != "estimated_online":
        raise ValueError("metric scale not estimated online; shape score cannot masquerade as odometry")
    if run["has_gt_in_estimator"] is not False:
        raise ValueError("estimator ground truth access must be explicitly false")


def comparable(run_a, run_b):
    fields=("hardware","capture_protocol","truth_source", "evaluation_protocol_sha256",
            "pose_frame","truth_frame","clock_alignment","pose_alignment")
    fields+=(("dataset_sha256",) if run_a["capture_protocol"]=="same_raw"
             else ("comparison_group_sha256",))
    return all(run_a.get(k) is not None and run_a.get(k)==run_b.get(k) for k in fields)


def dominates(a,b):
    if not comparable(a,b):
        return False
    ma,mb=a["metrics"],b["metrics"]
    direction={"ate_rmse_m":-1,"rpe_translation_rmse_m":-1,
               "coverage":1,"latency_p95_ms":-1,"energy_j_per_min":-1,
               "cpu_core_seconds_per_s":-1}
    okay=all((ma[k]-mb[k])*sign>=0 for k,sign in direction.items())
    better=any((ma[k]-mb[k])*sign>0 for k,sign in direction.items())
    return okay and better


def report(data):
    if not isinstance(data,dict):
        raise ValueError("report must be an object")
    runs=data.get("runs")
    if not isinstance(runs,list) or not runs:
        raise ValueError("runs must be nonempty")
    # Never mutate source dataset. Validate normalized, detached records.
    runs=json.loads(json.dumps(runs))
    for r in runs:
        validate_run(r)
    groups=[]
    remaining=list(range(len(runs)))
    while remaining:
        seed=remaining.pop(0)
        group=[seed]
        not_group=[]
        for i in remaining:
            if comparable(runs[seed],runs[i]):group.append(i)
            else:not_group.append(i)
        groups.append(group)
        remaining=not_group
    return {"schema":2,"scope":"independent measured metric VIO; not data generation",
            "groups":[{"hardware":runs[g[0]]["hardware"],
                "dataset_sha256":runs[g[0]]["dataset_sha256"] if runs[g[0]]["capture_protocol"]=="same_raw" else None,
                "source_dataset_sha256":sorted({runs[i]["dataset_sha256"] for i in g}),
                "evaluation_protocol_sha256":runs[g[0]]["evaluation_protocol_sha256"],
                "comparison_group_sha256":runs[g[0]].get("comparison_group_sha256"),
                "capture_protocol":runs[g[0]]["capture_protocol"],
                "scores":[{"engine":runs[i]["engine"],"engine_sha":runs[i]["engine_sha"],
                    "dataset_sha256":runs[i]["dataset_sha256"],
                    "report_source":runs[i]["report_source"],
                    "metrics":runs[i]["metrics"],"pareto_efficient":not any(
                        dominates(runs[j],runs[i]) for j in g if i!=j)}
                    for i in g]} for g in groups],
            "warnings":["Never compare different dataset/hardware/capture groups as if identical.",
                        "This checks report structure, not source bytes, truth assertions or statistical significance.",
                        "Evaluation-protocol hash must pin interval, RPE horizons, coverage definition, scoring and resource methods.",
                        "Repeated-route cohort must pin surveyed section, device configuration and paired/randomized trial design.",
                        "ARCore vs Camera2 cannot track one rear camera simultaneously; use separate paired runs."]}


def main():
    p=argparse.ArgumentParser()
    p.add_argument("measured_runs",type=Path)
    p.add_argument("--out",required=True,type=Path)
    args=p.parse_args()
    result=report(json.loads(args.measured_runs.read_text(encoding="utf-8")))
    args.out.parent.mkdir(parents=True,exist_ok=True)
    args.out.write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
