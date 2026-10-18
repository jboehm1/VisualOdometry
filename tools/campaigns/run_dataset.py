#!/usr/bin/env python3
"""Run campaigns on data pre-synced from Google Drive or from any local disk.

No network access, credentials or external services in this orchestration.
Outputs small status.json; raw camera/sensor data never uploaded by this script.
"""
import argparse
import json
import subprocess
import sys
from pathlib import Path

HERE=Path(__file__).resolve().parents[2]


def run(args):
    target=args.out.resolve()
    target.mkdir(parents=True,exist_ok=True)
    status={"campaign":args.campaign,"dataset_kind":args.kind,
            "status":"STARTED","real_dataset_provided":True,
            "ground_truth_scored":False}
    (target/"status.json").write_text(json.dumps(status,indent=2)+"\n")
    try:
        if args.campaign=="A":
            if args.kind not in ("euroc","tumvi"):
                raise ValueError("Campaign A needs EuRoC or TUM VI native MAV sequence")
            prepared=target/"prepared"
            command=[sys.executable,str(HERE/"tools/benchmark/bench.py"),"prepare",
                     "--dataset",args.kind,"--root",str(args.root),"--out",str(prepared)]
            subprocess.run(command,check=True)
            manifest=json.loads((prepared/"manifest.json").read_text())
            status["sensor_frame_count"]=manifest["frame_count"]
            status["sensor_imu_count"]=manifest["imu_count"]
            status["status"]="REAL_CAMERA_IMU_VALIDATED"
            if args.estimate:
                truth=args.truth or (Path(manifest["truth"]) if manifest["truth"] else None)
                if not truth:raise ValueError("Cannot score A without GT")
                cmd=[sys.executable,str(HERE/"tools/benchmark/bench.py"),"evaluate",
                     "--truth",str(truth),"--truth-format","euroc",
                     "--truth-frame","imu","--estimate",str(args.estimate),
                     "--estimate-format","tum","--estimate-frame","imu",
                     "--align","se3","--out",str(target/"metrics.json")]
                subprocess.run(cmd,check=True)
                status["status"]="REAL_EXTERNAL_VIO_ESTIMATE_SCORED"
                status["ground_truth_scored"]=True
                status["warning"]="External estimated IMU-frame poses; not Android ARCore estimation"
        elif args.campaign=="B":
            if args.kind not in ("oxiod","canonical"):
                raise ValueError("Campaign B needs OxIOD or canonical IMU CSV")
            if args.kind=="oxiod":
                if not args.input:raise ValueError("Need --input OxIOD synchronized imu CSV")
                sub=[sys.executable,str(HERE/"tools/campaigns/oxiod.py"),"--imu",str(args.input),
                     "--out",str(target/"prepared")]
                if args.truth:
                    if not args.truth_quality:
                        raise ValueError("Explicit --truth-quality required for OxIOD")
                    sub+=["--reference",str(args.truth),"--truth-quality",args.truth_quality]
                subprocess.run(sub,check=True)
                canonical=target/"prepared"/"imu.csv"
            else:
                if not args.input:raise ValueError("Need --input canonical IMU CSV")
                canonical=args.input
            sub=[sys.executable,str(HERE/"tools/campaigns/imu_pdr.py"),
                 "--imu",str(canonical),"--out",str(target/"pdr"),
                 "--stride-m",str(args.stride_m),
                 "--yaw-axis",args.yaw_axis]
            subprocess.run(sub,check=True)
            metrics=json.loads((target/"pdr"/"report.json").read_text())
            status["status"]="REAL_IMU_ONLY_PDR_EXECUTED"
            status["samples"]=metrics["sensor_counts"]
            status["steps"]=metrics["steps"]
            status["prior_stride_m"]=args.stride_m
            status["warning"]="No camera. PDR uses manually assumed stride and fixed phone axis; performance on independent GT NOT proven."
            if args.truth:
                status["status"]="REAL_IMU_ONLY_PDR_WITH_REFERENCE_UNSCORED"
                status["warning"]+=" Reference available but camera/body transform and clock synchronization must be verified before ATE/RPE."
        elif args.campaign=="C":
            if args.kind!="phone_csv":raise ValueError("Campaign C needs an exported VECTOR CSV")
            if not args.input:raise ValueError("Need --input phone CSV")
            sys.path.insert(0,str(HERE/"tools"))
            from plot_session import load_samples,summarize
            samples,events=load_samples(args.input)
            output=summarize(samples,events,args.known_distance)
            output["app_reference"]="ARCore estimates, not independent pose ground truth"
            output["capture_kind"]="live or replay (see CSV events)"
            (target/"phone_report.json").write_text(json.dumps(output,indent=2)+"\n")
            status["status"]="REAL_PHONE_EXPORTED_TRAJECTORY_AUDITED"
            status["estimated_distance_m"]=output["estimated_path_m"]
            status["independent_reference_available"]=args.known_distance is not None
            status["warning"]="Only independent measured path length provides distance error; no full 6DoF truth or live APK-in-cloud test."
        else:
            raise ValueError(f"Unknown campaign {args.campaign}")
    except BaseException as exc:
        status["status"]="FAILED"
        status["error"]=str(exc)
        (target/"status.json").write_text(json.dumps(status,indent=2)+"\n")
        raise
    (target/"status.json").write_text(json.dumps(status,indent=2)+"\n")
    return status


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--campaign",choices=["A","B","C"],required=True)
    p.add_argument("--kind",choices=["euroc","tumvi","oxiod","canonical","phone_csv"],required=True)
    p.add_argument("--root",type=Path,default=Path("."))
    p.add_argument("--input",type=Path)
    p.add_argument("--estimate",type=Path)
    p.add_argument("--truth",type=Path)
    p.add_argument("--truth-quality",choices=["optical","visual_inertial_proxy"])
    p.add_argument("--stride-m",type=float,default=0.70)
    p.add_argument("--yaw-axis",choices=["x","y","z"],default="z")
    p.add_argument("--known-distance",type=float)
    p.add_argument("--out",type=Path,required=True)
    args=p.parse_args()
    print(json.dumps(run(args),indent=2))


if __name__=="__main__":main()
