#!/usr/bin/env python3
"""Reproduce an evaluation on ACTUAL published VIO poses from EuRoC MH_01.

Upstream OpenVINS ships a GT and VINS-Mono estimate from a known EuRoC run.
This verifies metric scoring on public real trajectories; it does NOT execute
the Android ARCore estimator or run camera/IMU input through OpenVINS.
"""
import argparse
import hashlib
import json
import subprocess
import sys
import urllib.request
from pathlib import Path

UPSTREAM_SHA="69488123ed9362dd44b6f28e7f4680abbff1442b"
PREFIX=f"https://raw.githubusercontent.com/rpng/open_vins/{UPSTREAM_SHA}/ov_eval/example/"
FILES=("stamped_groundtruth.txt","stamped_traj_estimate.txt")


def download(name, folder):
    url=PREFIX+name
    path=folder/name
    req=urllib.request.Request(url,headers={"User-Agent":"SLAM-BENCHMARK-public-example/1"})
    with urllib.request.urlopen(req,timeout=80) as response, path.open("wb") as out:
        if response.geturl().split(":")[0]!="https":
            raise ValueError("Unsafe transport downgrade")
        size=0
        while data:=response.read(1024*1024):
            size+=len(data)
            if size>30*1024*1024:
                raise ValueError("Unexpectedly large public trajectory")
            out.write(data)
    digest=hashlib.sha256(path.read_bytes()).hexdigest()
    if size < 1000:
        raise ValueError(f"Data sample {name} unexpectedly short")
    return {"file":name,"source_url":url,"sha256":digest,"bytes":size}


def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out",type=Path,required=True)
    a=ap.parse_args()
    out=a.out.resolve()
    out.mkdir(parents=True,exist_ok=True)
    manifest={
        "campaign":"A","origin":"rpng/open_vins ov_eval example",
        "upstream_commit":UPSTREAM_SHA,
        "dataset":"EuRoC MH_01 (OpenVINS example from VINS-Mono)",
        "estimator":"published external VINS-Mono trajectory",
        "measured_engine":"external VINS-Mono, NOT VisualOdometry Android or native OpenVINS",
        "truth_provenance":"bundled rpg_trajectory_evaluation reference",
        "truth_independent_of_our_app":True,
        "truth_and_estimate_pose_frame":"as exported by OpenVINS example; external camera/body transform unverified",
        "notes":[
            "real public trajectory data, not generated synthetic positions",
            "pipeline evaluates supplied poses; no camera/IMU estimator executed here",
            "rigid SE3 alignment only (no post-hoc metric scale rescaling)",
            "pose-frame/extrinsics convention must be audited for publication claims"
        ]
    }
    manifest["downloaded_files"]=[download(name,out) for name in FILES]
    report=out/"metrics.json"
    args=[sys.executable,"tools/benchmark/bench.py","evaluate",
          "--truth",str(out/FILES[0]),"--truth-format","tum",
          "--truth-frame","published_example",
          "--estimate",str(out/FILES[1]),"--estimate-format","tum",
          "--estimate-frame","published_example",
          "--align","se3","--max-dt","0.02","--horizon","1",
          "--min-coverage","0.01","--out",str(report)]
    subprocess.run(args,check=True)
    metrics=json.loads(report.read_text())
    for field in ("ate_rmse_m","tracking_coverage_time_fraction","pairs"):
        if metrics[field] is None:
            raise ValueError(f"No useful {field} measurement")
    if metrics["pairs"]<30:
        raise ValueError("Too few synchronized poses in external real recording")
    (out/"source_manifest.json").write_text(json.dumps(manifest,indent=2)+"\n")
    print(json.dumps({"real_trajectory_score":metrics,
                      "not_android_app_accuracy":True},indent=2))


if __name__=="__main__":
    main()
