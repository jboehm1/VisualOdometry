#!/usr/bin/env python3
"""Emit a diagnostic side-by-side report for same-input EuRoC engine receipts.

These are first-pose-gauge research results, not phone qualification or a
claim of global best performance. No metric is recomputed from the receipts.
"""
import argparse
import json
import re
from pathlib import Path


def compare(paths):
    reports = [json.loads(Path(path).read_text(encoding="utf-8")) for path in paths]
    if len(reports) != 3:
        raise ValueError("exactly three engine score receipts are required")
    names = {r.get("engine") for r in reports}
    expected = {"actual upstream Basalt C++ visual-inertial estimator",
                "actual upstream ORB-SLAM3 stereo-inertial estimator",
                "actual upstream OpenVINS stereo-inertial estimator"}
    if names != expected:
        raise ValueError("reports must contain one measured Basalt, ORB-SLAM3, and OpenVINS run")
    for report in reports:
        if report.get("schema") != "vector-vio-host-real/1" or report.get("status") != "MEASURED":
            raise ValueError("unmeasured or unsupported score receipt")
        if report.get("truth_available_to_engine") is not False:
            raise ValueError("ground truth was available to the estimator")
        if not re.fullmatch(r"[0-9a-f]{40}", report.get("engine_source_sha", "")):
            raise ValueError("missing pinned engine source SHA")
        runtime = report.get("runtime", {})
        if runtime.get("network") != "none" or runtime.get("truth_mount") is not False:
            raise ValueError("inference receipt is not isolated from truth/network")
    shared = ("sensor_tree_sha256", "reference_sha256", "pose_frame", "metric_scale")
    for key in shared:
        if len({r.get(key) for r in reports}) != 1:
            raise ValueError("engine runs do not share " + key)
    rows = []
    for report in reports:
        diagnostic = report["first_reference_pose_gauge_diagnostic"]
        rows.append({
            "engine": report["engine"], "engine_source_sha": report["engine_source_sha"],
            "engine_config_sha256": report.get("engine_config_sha256"),
            "sensor_tree_sha256": report["sensor_tree_sha256"],
            "reference_sha256": report["reference_sha256"],
            "reference_matches": diagnostic.get("pairs"),
            "ate_rmse_m_first_pose_gauge": diagnostic.get("ate_rmse_m"),
            "rpe_translation_rmse_m": diagnostic.get("rpe_translation_rmse_m"),
            "coverage": diagnostic.get("tracking_coverage_time_fraction"),
            "wall_seconds_host": report["runtime"].get("wall_seconds"),
            "child_cpu_seconds_host": report["runtime"].get("child_cpu_seconds"),
            "child_peak_rss_kib_host": report["runtime"].get("child_peak_rss_kib",
                                      report["runtime"].get("child_peak_rss_kib_linux")),
        })
    return {"schema": "vector-euroc-engine-diagnostic/1", "status": "MEASURED_DIAGNOSTIC",
            "dataset": "EuRoC V1_01_easy staged first 1200 synchronized stereo frames + IMU",
            "alignment": "first matched reference pose fixed after inference; no scale fit",
            "ranking": "NOT_QUALIFIED", "engines": rows,
            "limits": ["not phone performance or battery", "V1_01 reference attitude caveat applies",
                       "first-pose gauge is a diagnostic, not independent surveyed alignment",
                       "cross-run compute metrics require matching hardware identity",
                       "no held-out dataset or low-light challenge included"]}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("scores", nargs=3, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    result = compare(args.scores)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(result, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
