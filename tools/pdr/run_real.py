#!/usr/bin/env python3
"""Execute native dataset event stream through the real Android Java PdrEngine.

This is NOT the Python campaigns/imu_pdr.py estimator and uses NO ground truth
or Tango headings as engine inputs. Output is an auditable *distance baseline*,
not a neural inertial odometer or Android S21 accuracy measurement.
"""
import argparse
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[2]
ENGINE=ROOT/"app/src/main/java/dev/jboehm/vectorvio/pdr"
SOURCES=[ENGINE/"StepPeakDetector.java",ENGINE/"PdrEngine.java",
         ROOT/"tools/pdr/PdrReplay.java"]

def checksum(path):
    h=hashlib.sha256()
    with Path(path).open("rb") as f:
        for block in iter(lambda:f.read(1024*1024),b""):h.update(block)
    return h.hexdigest()

def command(args):
    return subprocess.run([str(x) for x in args],check=True,
                          text=True,capture_output=True)

def run(prepared,out,reference_polyline="none"):
    prepared=Path(prepared).resolve();out=Path(out).resolve()
    if reference_polyline not in ("none","xy","xyz"):
        raise ValueError("reference_polyline must be none, xy or xyz")
    m=json.loads((prepared/"manifest.json").read_text(encoding="utf-8"))
    if m.get("schema_version")!=2 or m.get("dataset") not in ("oxiod","ronin"):
        raise ValueError("Only native schema v2 OxIOD/RoNIN manifests supported")
    if m.get("heading_truth_leakage") is not False or m.get("input_reference_to_engine") is not False:
        raise ValueError("Reference isolation is not certified")
    if m.get("supplied_device_heading") is not False:
        raise ValueError("This pipeline supports accel-only replay; heading requires separate audit")
    input_events=prepared/"events.csv"
    raw=prepared/"raw_sensors.csv"
    for fname,file in (("events.csv",input_events),("raw_sensors.csv",raw)):
        if not file.is_file() or checksum(file)!=m["files_sha256"][fname]:
            raise ValueError("Missing/tampered input or checksum mismatch: "+fname)
    with input_events.open(newline="",encoding="utf-8") as f:
        reader=csv.DictReader(f)
        if not reader.fieldnames or set(("t_ns","type","x","y","z")).difference(reader.fieldnames):
            raise ValueError("Invalid PDR events schema")
        types={row["type"] for row in reader}
    if not {"START","STEP_LENGTH","ACCEL"}.issubset(types) or types.difference(
       {"START","STEP_LENGTH","ACCEL"}):
        raise ValueError("Unexpected estimator event types; possible GT/heading leakage")
    if out.exists():
        raise FileExistsError("Refusing to overwrite existing report "+str(out))
    if any(not p.is_file() for p in SOURCES):
        raise FileNotFoundError("PDR Java source missing")
    out.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".pdr-run-",dir=out.parent) as tmp:
        stage=Path(tmp)/"out";stage.mkdir()
        classes=Path(tmp)/"classes";classes.mkdir()
        command(["javac","-encoding","UTF-8","-d",classes,*SOURCES])
        replay=command(["java","-cp",classes,"dev.jboehm.vectorvio.pdr.PdrReplay",
                        input_events,stage/"path.csv"])
        summary=json.loads(replay.stdout)
        for key in ("steps","distance_m","steps_without_heading","rejected_rows"):
            if key not in summary:raise ValueError("Missing PDR engine result: "+key)
        if summary["rejected_rows"] or summary["steps_without_heading"]!=summary["steps"]:
            raise ValueError("Dataset accel-only replay unexpectedly rejected events / invented heading")
        report={
            "schema":2,
            "status":"REAL_JAVA_ENGINE_EXECUTED_ON_PROVIDED_SAMPLES",
            "dataset":m["dataset"],
            "engine":"Android Java PdrEngine/StepPeakDetector, same Java classes",
            "distance_m":summary["distance_m"],
            "steps":summary["steps"],
            "steps_without_heading":summary["steps_without_heading"],
            "metric_scope":"distance from detected steps; 2D trajectory is not observed",
            "truth_used_by_estimator":False,
            "reference_quality":m["reference_quality"],
            "reference_polyline_mode":reference_polyline,
            "distance_scored":False,
            "s21_device_test":False,
            "accuracy_verified":False,
            "warning":"This is a fixed-stride, heuristic step baseline, not RoNIN neural inference."
        }
        if reference_polyline!="none":
            gt=m.get("reference")
            if not gt or m.get("reference_quality")=="none":
                raise ValueError("Reference was not explicitly supplied and qualified")
            if gt.get("reference_coverage_time_fraction",0)<.98:
                raise ValueError("Reference timestamp coverage below 98%; do not score partial truth")
            from evaluate import distance_metrics
            ref=gt["reference_polyline_"+reference_polyline+"_m"]
            if not math.isfinite(ref) or ref<=0:
                raise ValueError("Invalid reference polyline distance")
            report.update(distance_metrics(summary["distance_m"],ref,summary))
            report["distance_scored"]=True
            report["reference_warning"]=(
                "Tango visual-inertial proxy; NOT independent optical ground truth."
                if m["reference_quality"]=="visual_inertial_proxy" else
                "Operator-declared optical reference; provenance and noise NOT independently verified.")
            report["distance_accuracy_is_phone_verified"]=False
        hashes={p.name:checksum(p) for p in SOURCES}
        provenance={
            "schema_version":2,"source_dataset":m["dataset"],
            "source_files_sha256":m["source_files_sha256"],
            "dataset_adapter_sha256":checksum(ROOT/"tools/pdr/datasets.py"),
            "estimator_source_sha256":hashes,
            "input_events_sha256":checksum(input_events),
            "input_manifest_sha256":checksum(prepared/"manifest.json"),
            "reference_file_sha256":checksum(prepared/"reference_xyz.csv")
                if m.get("reference_file") else None,
            "reference_not_passed_to_java":True,
            "compiler_version":command(["javac","-version"]).stderr.strip()
                or command(["javac","-version"]).stdout.strip(),
            "python_version":platform.python_version(),
            "replay_engine_stdout":replay.stdout.strip(),
            "note":"Provenance only: reference used solely in the evaluation stage"
        }
        for name,content in (("summary.json",summary),("report.json",report),
                             ("provenance.json",provenance)):
            (stage/name).write_text(json.dumps(content,indent=2,sort_keys=True,
                             allow_nan=False)+"\n",encoding="utf-8")
        os.replace(stage,out)
    return report

def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--prepared",type=Path,required=True)
    p.add_argument("--out",type=Path,required=True)
    p.add_argument("--reference-polyline",choices=("none","xy","xyz"),default="none",
                   help="EXPLICIT opt-in to approximate reference-arc-length score")
    a=p.parse_args(argv)
    print(json.dumps(run(a.prepared,a.out,a.reference_polyline),
                     indent=2,sort_keys=True,allow_nan=False))

if __name__=="__main__":main()
