#!/usr/bin/env python3
"""Independent, reproducible chat-triggered proof on 4 REAL pedestrian recordings.

Runs 4 distinct humans x 3 carrying positions using the *actual* pinned Java
PdrEngine and independent foot-IMU step labels. No user Drive secrets needed:
sources are publicly hosted; a separately verified connector transfer deposits
the small report on the user's private Drive afterwards.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
from simul_real import SEQUENCES, LOCATIONS, JAVA_SHA, UPSTREAM_SHA, campaign

ADDITIONAL_HOLDOUT = "data/by-person/8/awindaRecording_20230424133253.csv"


def run(out: Path) -> dict:
    SEQUENCES["subject08_independent"] = ADDITIONAL_HOLDOUT
    cases = ["subject08_independent", "subject17_short", "subject24_short", "subject25_short"]
    report = campaign(out, cases, [3, 5, 6])
    if len(report["sequences"]) != 4:
        raise AssertionError("Expected four real people")
    samples = 0
    cases_verified = 0
    for seq in report["sequences"]:
        if seq["reference_steps"] < 20:
            raise AssertionError("Not enough independently annotated reference steps")
        samples += seq["measurement"]["sample_count"]
        for pos in seq["positions"]:
            assert pos["java"]["engine"] == "ANDROID_JAVA_PdrEngine_STEP_FALLBACK"
            assert pos["python"]["engine"] == "PYTHON_FIXED_STRIDE_PDR_BASELINE"
            assert pos["java"]["bridge"]["heading_provenance"] == "none"
            assert pos["java"]["results"]["steps"] > 0
            assert 0 <= pos["java_timed_step_score"]["f1"] <= 1
            cases_verified += 1
    receipt = {
        "proof": "GITHUB_ACTIONS_EXECUTED_REAL_SENSOR_ESTIMATOR",
        "dataset": "SIMUL, real synchronized IMUs and independent foot-step annotations",
        "source_commit": UPSTREAM_SHA,
        "actual_android_java_algorithm_from_parallel_branch_commit": JAVA_SHA,
        "independent_new_participant": "subject08_independent",
        "participants": len(report["sequences"]),
        "sensor_placements_per_participant": len(LOCATIONS),
        "actual_Java_test_cases": cases_verified,
        "real_sample_timestamps": samples,
        "unmodified_java_step_detector": True,
        "ground_truth_was_not_estimator_input": True,
        "physical_android_test": False,
        "absolute_metric_distance_accuracy_established": False,
        "data_residency": "Public dataset downloaded into ephemeral cloud runner; output report only",
    }
    if cases_verified != 12:
        raise AssertionError("Not all 12 real Java trials finished")
    (out / "chat_proof_receipt.json").write_text(json.dumps(receipt, indent=2)+"\n")
    print("CHAT_REAL_DATA_PROOF " + json.dumps(receipt, sort_keys=True), flush=True)
    return receipt


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--out",type=Path,required=True)
    args=p.parse_args()
    run(args.out)

if __name__=="__main__":
    main()
