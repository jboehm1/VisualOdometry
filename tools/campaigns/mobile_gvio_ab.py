#!/usr/bin/env python3
"""Controlled A/B of real smartphone monocular+gyro algorithms.

One original decoded Mobile-GVIO recording is reused for both frozen profiles.
No reference is available to the image/gyro estimator; reference scoring is
performed ONLY after ALL pose inferences finish. Metric scale is NOT recovered.

This is an experiment runner, never a full metric VIO or a phone APK benchmark.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import platform
import statistics
from pathlib import Path

from mobile_gvio_raw import PROFILES, infer, read_real_imu_camera
from mobile_gvio_score import score


ORDER = ("balanced", "low_compute", "low_compute", "balanced")


def _sha256(path: Path) -> str:
    d = hashlib.sha256()
    with path.open("rb") as f:
        for buf in iter(lambda: f.read(1024 * 1024), b""):
            d.update(buf)
    return d.hexdigest()


def _numeric_results(name: str, samples: list[dict]) -> dict:
    if len(samples) != 2:
        raise ValueError("Need two inference repetitions for each algorithm")
    for trial in samples:
        if not trial["camera_images_and_imu_both_influence_pose"]:
            raise ValueError("Vision or gyro missing in one profile")
        if trial["actual_images_processed"] < 25 or trial["gyro_integrated_samples"] < 100:
            raise ValueError("Insufficient actual smartphone observations")
        if trial["visual_essential_pose_updates"] < 8:
            raise ValueError("Insufficient visual pose updates")
        if trial["metric_translation_scale_known"] is not False:
            raise ValueError("Uncalibrated monocular estimator falsely declared metric")
        if trial["frozen_tracking_profile"] != name:
            raise ValueError("Wrong tracking profile")
    values = [float(x["estimator_wall_seconds"]) for x in samples]
    cpu = [float(x["estimator_cpu_seconds"]) for x in samples]
    if not all(math.isfinite(x) and x > 0 for x in values + cpu):
        raise ValueError("Invalid runtime measurements")
    return {
        "profile": name,
        "tracking_parameters": dict(PROFILES[name]),
        "camera_frames": samples[0]["actual_images_processed"],
        "gyro_integrations": samples[0]["gyro_integrated_samples"],
        "good_visual_pose_updates": samples[0]["visual_essential_pose_updates"],
        "optical_inliers": samples[0]["visual_ransac_inliers"],
        "wall_seconds_each": values,
        "cpu_seconds_each": cpu,
        "median_wall_seconds": float(statistics.median(values)),
        "median_cpu_seconds": float(statistics.median(cpu)),
        "median_wall_ms_per_frame": float(statistics.median(
            [x["wall_ms_per_frame"] for x in samples])),
    }


def compare(bag: Path, calib: Path, truth: Path, out: Path, seconds=22.0, skip=8.0):
    if not (1 <= seconds <= 120) or not (0 <= skip <= 1000):
        raise ValueError("Bounded measured window required")
    for name,path in [("bag",bag),("camera/IMU calibration",calib),("external reference",truth)]:
        if not path.is_file():
            raise FileNotFoundError(f"Missing {name}: {path}")
    out.mkdir(parents=True, exist_ok=True)

    # Only access synchronized original camera + measured gyro before inference.
    # The reference path is NOT passed to either image/gyro estimator.
    observations = read_real_imu_camera(bag, seconds=seconds, skip=skip)
    times = [t for t,_ in observations[0]]
    if times[-1] <= times[0]:
        raise ValueError("Degenerate camera time interval")
    traces: dict[str,list[dict]] = {name: [] for name in PROFILES}
    for index,name in enumerate(ORDER):
        folder = out / f"trial_{index}_{name}"
        report = infer(bag, calib, folder, seconds=seconds, skip=skip,
                       profile=name, observations=observations)
        traces[name].append(report)
    del observations  # Release raw camera pixel data before scoring.

    first = {name: out / f"trial_{ORDER.index(name)}_{name}" / "estimate_unscaled.tum"
             for name in PROFILES}

    # Only now the scorer accesses external LiDAR+IMU poses. One shared clock
    # shift fitted to balanced's GT-oriented angular rates, reused for the fast
    # profile; each posthoc Sim3 fit is per estimator, so SHAPE errors only.
    base_score = score(first["balanced"], truth, infer_clock=True)
    offset = base_score["posthoc_clock_shift_seconds"]
    fast_score = score(first["low_compute"], truth, clock_offset_seconds=offset)
    if not base_score["clock_shift_fitted_using_reference_after_inference"] or (
        not fast_score["clock_shift_shared_from_reference_fitted_baseline"]
    ):
        raise ValueError("Shared posthoc clock calibration failed")
    if not all(s["matched_pose_pairs"] >= 30 for s in (base_score,fast_score)):
        raise ValueError("Insufficient jointly comparable reference samples")
    values = {name:_numeric_results(name,traces[name]) for name in PROFILES}
    if (values["balanced"]["camera_frames"] != values["low_compute"]["camera_frames"]
            or values["balanced"]["gyro_integrations"] != values["low_compute"]["gyro_integrations"]):
        raise ValueError("Algorithm A/B did not consume the identical sensor sampling")
    for name,s in [("balanced",base_score),("low_compute",fast_score)]:
        values[name]["shape_score"] = {
            "posthoc_sim3_rmse_m":s["sim3_shape_ate_rmse_m"],
            "matched_pose_pairs":s["matched_pose_pairs"],
            "posthoc_sim3_scale":s["after_truth_similarity_scale"],
            "metric_accuracy":False,
        }
    v_b,v_l=values["balanced"],values["low_compute"]
    result = {
        "status":"PASS",
        "dataset":"Mobile-GVIO Indoor-2",
        "source":"https://zenodo.org/records/20525157",
        "comparison_kind":"identical_original_frames_and_gyro_frozen_optical_flow_A_B",
        "estimator_is_ANDROID_ARCORE":False,
        "full_six_axis_metric_vio":False,
        "metric_distance_accuracy_proven":False,
        "sensor_source":"original_device_header_timestamp_real_phone_camera_and_gyro",
        "reference_gt_used_for_inference":False,
        "reference_gt_used_after_inference_to_fit_clock_and_each_sim3_scale":True,
        "reference_kind":"original_FastLIO2_LiDAR_IMU",
        "reference_clock_offset_shared_from_balanced_s":offset,
        "absolute_groundtruth_clock_calibrated_independently":False,
        "accuracy_comparison":"posthoc_Sim3_shape_only_not_metric_or_cross_dataset",
        "benchmark_repetitions":list(ORDER),
        "reading_shared_sensor_data_once":True,
        "seconds_window":seconds,"skip_seconds":skip,
        "original_camera_time_span_s":(times[-1]-times[0])*1e-9,
        "original_calibration_sha256":_sha256(calib),
        "external_reference_sha256":_sha256(truth),
        "platform":platform.platform(),
        "python_version":platform.python_version(),
        "results":values,
        "balanced_over_low_compute_median_wall_time_ratio":(
            v_b["median_wall_seconds"]/v_l["median_wall_seconds"]),
        "limitations":[
            "The GT-shape RMSE refits metric scale to the reference, not calibrated metric translation",
            "A shared clock shift is fitted from GT angular speeds AFTER inference, not independent sync",
            "Two ABBA repetitions on one short window do not prove speed on Samsung S21/Android",
            "Computer OpenCV wall times include Python/OS jitter; do not extrapolate thermal or power",
            "Experiments use the SAME frames and gyro but are not different full metric VIO backends",
        ],
    }
    target=out/"comparison.json"
    target.write_text(json.dumps(result,indent=2,sort_keys=True,allow_nan=False)+"\n",
                      encoding="utf-8")
    print("REAL_PHONE_SAME_SIGNAL_ALGORITHM_AB",json.dumps({
        "status":result["status"],
        "balanced_median_s":v_b["median_wall_seconds"],
        "low_compute_median_s":v_l["median_wall_seconds"],
        "wall_speed_ratio":result["balanced_over_low_compute_median_wall_time_ratio"],
        "balanced_shape_rmse_m":base_score["sim3_shape_ate_rmse_m"],
        "low_compute_shape_rmse_m":fast_score["sim3_shape_ate_rmse_m"],
        "reference_clock_shared":offset,
        "both_not_metric":True,
    }),flush=True)
    return result


if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--bag",type=Path,required=True)
    p.add_argument("--calib",type=Path,required=True)
    p.add_argument("--truth",type=Path,required=True)
    p.add_argument("--out",type=Path,required=True)
    p.add_argument("--seconds",type=float,default=22.0)
    p.add_argument("--skip",type=float,default=8.0)
    a=p.parse_args()
    compare(a.bag,a.calib,a.truth,a.out,a.seconds,a.skip)
