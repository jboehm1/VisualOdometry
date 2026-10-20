#!/usr/bin/env python3
"""Run a declared collection of local original-format PDR sequences.

Reject accidental subject leakage between train/validation/test; never infer
that synthetic schema fixtures constitute actual public corpora. No network,
credentials, global estimator, orientation truth, or hidden stride fitting.
See docs/PDR_DATASETS_PIPELINE.md for an exact manifest example.
"""
import argparse
import json
import math
from pathlib import Path
import re

from datasets import prepare_oxiod,prepare_ronin,checksum
from run_real import run

VALID_SPLITS=("train","validation","test","exploratory")

def load_manifest(file):
    file=Path(file).resolve()
    m=json.loads(file.read_text(encoding="utf-8"))
    if m.get("schema_version")!=1 or not isinstance(m.get("sequences"),list) or not m["sequences"]:
        raise ValueError("Batch manifest must define schema_version 1 and nonempty sequences")
    seen=set()
    subjects={}
    for i,item in enumerate(m["sequences"]):
        if not isinstance(item,dict):raise ValueError("Batch entry is not an object")
        name=item.get("id")
        dataset=item.get("dataset")
        split=item.get("split")
        subject=item.get("subject_id")
        if not isinstance(name,str) or re.fullmatch(r"[A-Za-z0-9_-]{1,90}",name) is None:
            raise ValueError("Unsafe/invalid sequence id at index %d"%i)
        if name in seen:raise ValueError("Duplicate sequence id "+name)
        seen.add(name)
        if dataset not in ("oxiod","ronin"):
            raise ValueError("Unsupported dataset "+str(dataset))
        if split not in VALID_SPLITS:
            raise ValueError("Split must be train/validation/test/exploratory")
        if subject is not None:
            if not isinstance(subject,str) or not subject.strip():
                raise ValueError("Invalid subject ID")
            key=(dataset,subject)
            if key in subjects and subjects[key]!=split:
                raise ValueError("Subject leakage across splits: %s"%subject)
            subjects[key]=split
        if split=="test" and not subject:
            raise ValueError("Test set requires explicit subject_id; no subject leakage claims otherwise")
        if "step_length_m" not in item:
            raise ValueError("Each sequence must declare fixed stride; never tune on its test truth")
        if not .35<=float(item["step_length_m"])<=1.8:
            raise ValueError("Step length outside range")
        if dataset=="oxiod":
            if not item.get("imu"):raise ValueError("OxIOD must specify original syn/imu*.csv")
            if bool(item.get("reference"))!=bool(item.get("reference_quality")):
                raise ValueError("Explicit OxIOD reference quality required")
        else:
            if not item.get("sequence"):raise ValueError("RoNIN requires source sequence directory")
            if item.get("reference_quality") not in (None,"visual_inertial_proxy"):
                raise ValueError("RoNIN uses Tango visual-inertial proxy, not optical truth")
        if item.get("score_polyline","none") not in ("none","xy","xyz"):
            raise ValueError("Invalid score_polyline")
    return m

def _path(name,base):
    p=Path(name).expanduser()
    return (p if p.is_absolute() else base/p).resolve()

def run_batch(manifest_path,out):
    manifest_path=Path(manifest_path).resolve()
    plan=load_manifest(manifest_path)
    out=Path(out).resolve()
    if out.exists():raise FileExistsError("Batch output already exists")
    out.mkdir(parents=True)
    results=[]
    for item in plan["sequences"]:
        name=item["id"];dataset=item["dataset"]
        dest=out/name;dest.mkdir()
        prepared=dest/"prepared"
        stride=float(item["step_length_m"])
        if dataset=="oxiod":
            reference=_path(item["reference"],manifest_path.parent) if item.get("reference") else None
            prepared_data=prepare_oxiod(_path(item["imu"],manifest_path.parent),
                prepared,reference,item.get("reference_quality"),stride)
        else:
            prepared_data=prepare_ronin(_path(item["sequence"],manifest_path.parent),
                prepared,item.get("reference_quality"),stride,item.get("subject_id"))
        report=run(prepared,dest/"java",item.get("score_polyline","none"))
        summary=dict(id=name,dataset=dataset,split=item["split"],
           subject_id=item.get("subject_id","unknown"),
           input_source_sha256=prepared_data["source_files_sha256"],
           steps=report["steps"],pdr_distance_m=report["distance_m"],
           reference_quality=report["reference_quality"],
           score_polyline=item.get("score_polyline","none"),
           distance_scored=report["distance_scored"],
           distance_error_pct=report.get("pdr_total_distance_error_pct"),
           status="JAVA_PDR_EVALUATED_ON_LOCAL_DATA",
           device_validated=False,
           source_is_original_corpus_verified=False)
        results.append(summary)
    scored=[r for r in results if r["distance_scored"]]
    aggregate={
        "schema_version":1,
        "pipeline":"OxIOD/RoNIN -> native adapter -> Android Java PdrReplay",
        "dataset_input":"operator supplied local files; no auto-download",
        "source_manifest_sha256":checksum(manifest_path),
        "sequence_count":len(results),
        "score_count":len(scored),
        "subjects_split_validated":all(r["subject_id"]!="unknown" for r in results),
        "actual_corpus_authenticity_verified":False,
        "accuracy_verified_on_device":False,
        "reference_metric":"unsmoothed XY/XYZ reference polyline; may be jitter-biased",
        "mean_abs_distance_error_pct":sum(abs(x["distance_error_pct"]) for x in scored)/len(scored) if scored else None,
        "results":results,
    }
    (out/"batch.json").write_text(json.dumps(aggregate,indent=2,sort_keys=True,allow_nan=False)+"\n")
    return aggregate

def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--manifest",type=Path,required=True)
    p.add_argument("--out",type=Path,required=True)
    args=p.parse_args(argv)
    print(json.dumps(run_batch(args.manifest,args.out),indent=2,sort_keys=True,allow_nan=False))

if __name__=="__main__":main()
