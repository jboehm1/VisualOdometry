#!/usr/bin/env python3
"""Strict posthoc comparison for independently frozen true sensor-only trajectory.

The UPO /ground_truth topic is a marker+wheel-odometry-smoothed pseudo
reference. Its accuracy is not independent; SE(2) registration is after
inference and may hide bias. This reports exploratory score, not map safety.
"""
import argparse
import json
from pathlib import Path
from rosbags.highlevel import AnyReader
from doppler_odom import rigid_2d_align_and_score


def load_pseudo(bag):
    if bag.name!="experiment_7.bag":
        raise ValueError("Not the UPO original expected source")
    reference=[]
    with AnyReader([bag]) as reader:
        cs=[c for c in reader.connections if c.topic=="/ground_truth"]
        if len(cs)!=1:raise ValueError("Missing one pseudo-reference topic")
        for conn,stamp,raw in reader.messages(connections=cs):
            msg=reader.deserialize(raw,conn.msgtype)
            if msg.header.frame_id!="map":raise ValueError("Unexpected reference frame")
            t=msg.header.stamp.sec+msg.header.stamp.nanosec*1e-9
            p=msg.pose.position
            reference.append((t,float(p.x),float(p.y)))
    return reference


def main(argv=None):
    p=argparse.ArgumentParser()
    p.add_argument("--bag",type=Path,required=True)
    p.add_argument("--trajectory-report",type=Path,required=True)
    p.add_argument("--out",type=Path,required=True)
    a=p.parse_args(argv)
    estimate=json.loads(a.trajectory_report.read_text(encoding="utf-8"))
    if estimate.get("mode")!="DUAL_RADAR_CALIBRATED_TF_SENSOR_CLOCK":
        raise ValueError("Not real calibrated dual radar engine")
    if estimate.get("reference_used_during_estimation") is not False:
        raise ValueError("GT leak during estimation")
    reference=load_pseudo(a.bag)
    score=rigid_2d_align_and_score(estimate["trajectory"],reference)
    report={"engine":"DUAL_RADAR_CALIBRATED_TF_SENSOR_CLOCK",
            "reference_kind":"MARKER_AND_ROBOT_ODOMETRY_SMOOTHED_PSEUDO",
            "source_original_sha256":estimate["source_sha256"],
            "reference_seen_after_estimation":True,
            "pseudo_reference_frames":len(reference),
            "pseudo_reference_posthoc_evaluation":score,
            "does_not_prove_independent_accuracy":True,
            "safety":"NOT_FOR_RESPONDER_USE"}
    a.out.parent.mkdir(parents=True,exist_ok=True)
    a.out.write_text(json.dumps(report,sort_keys=True,indent=2,allow_nan=False)+"\n",encoding="utf-8")
    print(json.dumps(report,sort_keys=True))
    return 0


if __name__=="__main__":
    raise SystemExit(main())
