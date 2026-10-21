#!/usr/bin/env python3
"""Run BOTH native-schema synthetic fixtures through actual Java; never call real accuracy."""
import argparse
import json
from pathlib import Path
import shutil
import sys
import tempfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/"tools"/"pdr"))
sys.path.insert(0,str(ROOT/"tools"/"pdr"/"tests"))
import datasets
import run_real
from fixtures import create_oxiod,create_ronin

def run(out):
    out=Path(out)
    if out.exists():raise FileExistsError(out)
    out.mkdir(parents=True)
    sources=out/"source";sources.mkdir()
    imu,gt=create_oxiod(sources/"oxiod")
    ro=create_ronin(sources/"ronin")
    results={}
    for name in ("oxiod","ronin"):
        prepared=out/(name+"_prepared")
        if name=="oxiod":datasets.prepare_oxiod(imu,prepared,gt,"visual_inertial_proxy",.74)
        else:datasets.prepare_ronin(ro,prepared,"visual_inertial_proxy",.74,"fixture_only")
        scored=run_real.run(prepared,out/(name+"_java"),"xy")
        if not scored["distance_scored"] or scored["steps"]!=scored["steps_without_heading"]:
            raise AssertionError(name+" Java pipeline violated distance/heading contract")
        results[name]=dict(status="SCHEMA_FIXTURE_JAVA_REPLAY_PASS",
             rows=json.loads((out/(name+"_java")/"summary.json").read_text())["rows"],
             steps=scored["steps"],dataset_original_executed=False,
             actual_phone_qualfied=False,real_dataset_accuracy_scored=False)
    for p in (out/"source").rglob("*"):
        if p.is_file():p.unlink()
    for p in sorted((out/"source").rglob("*"),reverse=True):
        if p.is_dir():p.rmdir()
    (out/"source").rmdir()
    report=dict(suite="native-schema (SYNTHETIC) OxIOD + RoNIN to Android Java PdrReplay",
                original_datasets_downloaded=False,original_datasets_benchmarked=False,
                requires_independent_ground_truth_for_publication=True,
                passed=results)
    (out/"smoke-status.json").write_text(json.dumps(report,indent=2,sort_keys=True)+"\n")
    print(json.dumps(report,indent=2))
    return report

if __name__=="__main__":
    a=argparse.ArgumentParser()
    a.add_argument("--out",type=Path,required=True)
    run(a.parse_args().out)
