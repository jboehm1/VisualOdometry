#!/usr/bin/env python3
"""Run REAL, annotated pedestrian IMU through TWO DISTINCT estimators.

Public SIMUL (six body locations; optical step annotation from foot IMUs):
https://github.com/simpleLoc/SIMUL-dataset

The ground-truth .stepNonoverlap files are read only AFTER the Java/Python
estimators have finished. This measures STEP COUNT, not metric distance or 6DoF.
Do not claim this is an evaluation of ARCore or of calibrated phone PDR.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import subprocess
import sys
import urllib.request

HERE = Path(__file__).resolve().parents[2]
UPSTREAM_REPO = "simpleLoc/SIMUL-dataset"
UPSTREAM_SHA = "ee7eb4e89bf33a17cafb4268207b802773427493"
# Pinned separate Android Java PDR branch; not present in the integration APK.
JAVA_REPO = "jboehm1/VisualOdometry"
JAVA_SHA = "f8228aa7acbc453001d623c537bd8d7f0a9bc176"
SEQUENCES = {
    "subject24_short": "data/by-person/24/awindaRecording_20230506184711.csv",
    "subject25_short": "data/by-person/25/awindaRecording_20230124150358.csv",
}
JAVA_SOURCES = (
    "app/src/main/java/dev/jboehm/vectorvio/pdr/StepPeakDetector.java",
    "app/src/main/java/dev/jboehm/vectorvio/pdr/PdrEngine.java",
    "tools/pdr/PdrReplay.java",
)
LOCATIONS = {3: "left_front_pocket", 5: "handheld", 6: "back_pocket"}
MAX_DOWNLOAD = 38 * 1024 * 1024
DATA_LICENSE = "CC-BY-SA-4.0"


def pull(repo, sha, name, destination, limit=MAX_DOWNLOAD):
    """Fetch an exactly pinned public source without authentication or redirects to HTTP."""
    url = f"https://raw.githubusercontent.com/{repo}/{sha}/{name}"
    req = urllib.request.Request(url, headers={"User-Agent": "VECTOR-real-campaign/1.0"})
    destination.parent.mkdir(parents=True, exist_ok=True)
    total = 0
    digest = hashlib.sha256()
    try:
        with urllib.request.urlopen(req, timeout=90) as resp, destination.open("wb") as dst:
            if not resp.geturl().startswith("https://raw.githubusercontent.com/"):
                raise ValueError("Unexpected download origin")
            while block := resp.read(256 * 1024):
                total += len(block)
                if total > limit:
                    raise ValueError(f"Source download exceeds {limit} bytes")
                dst.write(block)
                digest.update(block)
    except BaseException:
        destination.unlink(missing_ok=True)
        raise
    if total < 30:
        raise ValueError("Unexpectedly empty real input: " + url)
    return {"url": url, "source_commit": sha, "bytes": total,
            "sha256": digest.hexdigest(), "file": name}


def ingest_simul(source, root, positions):
    """Source sensor frame -> canonical samples. No annotation is inspected here."""
    target = root / "canonical"
    target.mkdir(parents=True, exist_ok=True)
    streams = {}
    writers = {}
    count = 0
    activity_counts: dict[str, int] = {}
    previous = -1
    first_ts = None
    last_ts = None
    acceleration_median_samples: dict[int, list[float]] = {n: [] for n in positions}
    try:
        for index in positions:
            f = (target / f"imu_{index}.csv").open("w", newline="", encoding="utf-8")
            streams[index] = f
            w = csv.writer(f)
            w.writerow(["t_ns", "type", "x", "y", "z"])
            writers[index] = w
        with source.open(newline="", encoding="utf-8-sig") as input_file:
            reader = csv.reader(input_file)
            header = next(reader)
            if len(header) != 98 or header[0].strip() != "Timestamp" or header[1].strip() != "Activity":
                raise ValueError(f"Unexpected SIMUL CSV schema ({len(header)} columns)")
            for row in reader:
                if len(row) != 98:
                    raise ValueError(f"Unexpected channel count at sample #{count}")
                # Source timestamps are seconds, not nanoseconds. Shift to a
                # positive relative epoch for Java replay; never use truth clock.
                timestamp = float(row[0])
                if not math.isfinite(timestamp):
                    raise ValueError("Invalid time sample")
                if first_ts is None:
                    first_ts = timestamp
                t_ns = 1_000_000_000 + round((timestamp - first_ts) * 1e9)
                if t_ns <= previous:
                    raise ValueError("Duplicate/unsorted SIMUL sensor time")
                previous = t_ns
                last_ts = timestamp
                activity = row[1].strip()
                activity_counts[activity] = activity_counts.get(activity, 0) + 1
                for index in positions:
                    start = 2 + 16 * (index - 1)
                    acceleration = tuple(float(z) for z in row[start:start+3])
                    gyro = tuple(float(z) for z in row[start+6:start+9])
                    if not all(map(math.isfinite, (*acceleration, *gyro))):
                        raise ValueError("Invalid inertial measurement")
                    writers[index].writerow((t_ns, "GYRO", *gyro))
                    writers[index].writerow((t_ns, "ACCEL", *acceleration))
                    if len(acceleration_median_samples[index]) < 1200:
                        acceleration_median_samples[index].append(
                            math.sqrt(sum(v*v for v in acceleration)))
                count += 1
    finally:
        for f in streams.values():
            f.close()
    if count < 500 or last_ts is None:
        raise ValueError("Too few original SIMUL samples")
    # SIMUL accelerometer is expected m/s2 (gravity inclusive), not normalized g.
    # Fail rather than reporting nonsense if the input interpretation is wrong.
    import statistics
    gravity_med = {str(n): statistics.median(acceleration_median_samples[n]) for n in positions}
    for n, median in gravity_med.items():
        if not 5.0 <= median <= 16.0:
            raise ValueError(f"Unexpected accel magnitude for sensor {n}: {median:.3f}; check units")
    return {"sample_count": count, "duration_s": last_ts-first_ts,
            "gravity_median_first_1200_ms2": gravity_med,
            "activity_counts": activity_counts}


def compile_java(root):
    sources = []
    provenance = []
    for path in JAVA_SOURCES:
        dest = root / "java_source" / path
        provenance.append(pull(JAVA_REPO, JAVA_SHA, path, dest, limit=100_000))
        sources.append(str(dest))
    build = root / "java_classes"
    build.mkdir(exist_ok=True)
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(build), *sources],
                   check=True, capture_output=True, text=True)
    return build, provenance


def run_java(java_build, canonical, output_path):
    from android_pdr_bridge import convert
    replay = canonical.parent / (canonical.stem+".events.csv")
    meta = convert(canonical, replay)
    proc = subprocess.run([
        "java", "-cp", str(java_build),
        "dev.jboehm.vectorvio.pdr.PdrReplay",
        str(replay), str(output_path)
    ], check=True, capture_output=True, text=True)
    report = json.loads(proc.stdout.strip().splitlines()[-1])
    if report["rejected_rows"]:
        raise ValueError(f"Java replay rejected {report['rejected_rows']} records")
    return {"engine":"ANDROID_JAVA_PdrEngine_STEP_FALLBACK",
            "engine_git_sha":JAVA_SHA,
            "results":report,
            "bridge":meta}


def run_python(canonical, output_dir):
    cmd = [sys.executable, str(HERE / "tools/campaigns/imu_pdr.py"),
           "--imu", str(canonical), "--out", str(output_dir)]
    subprocess.run(cmd, check=True, capture_output=True, text=True)
    return {"engine":"PYTHON_FIXED_STRIDE_PDR_BASELINE",
            "results":json.loads((output_dir/"report.json").read_text())}


def read_step_reference(annotation):
    """Source annotated non-overlapping [first_index,last_index] intervals."""
    steps = []
    previous_end = -1
    with annotation.open(newline="", encoding="utf-8") as f:
        for row in csv.reader(f):
            if len(row) != 2:
                raise ValueError("Expected SIMUL .stepNonoverlap with two indices")
            start,end = (int(v) for v in row)
            if start < 0 or end < start or start <= previous_end:
                raise ValueError("Invalid or overlapping step annotation")
            steps.append((start,end))
            previous_end = end
    if not steps:
        raise ValueError("Empty step reference")
    return steps


def evaluate_count(predicted, reference_steps):
    return {"count":predicted,
            "annotated_steps":reference_steps,
            "step_count_error":predicted-reference_steps,
            "step_count_error_percent":100*(predicted-reference_steps)/reference_steps,
            "metric_distance_error":None,
            "step_detection_precision_recall":None,
            "note":"count-only across whole recording; no per-step timing matching"}


def campaign(out, names, positions):
    out=out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    java_build, java_files = compile_java(out)
    results=[]
    for name in names:
        folder=out/name
        folder.mkdir(exist_ok=True)
        path=SEQUENCES[name]
        raw=folder/"original.csv"
        ann=folder/"original.stepNonoverlap"
        raw_provenance=pull(UPSTREAM_REPO,UPSTREAM_SHA,path,raw)
        sensor=ingest_simul(raw,folder,positions)
        sequence_runs=[]
        for sensor_id in positions:
            imu = folder/"canonical"/f"imu_{sensor_id}.csv"
            java=run_java(java_build, imu, folder/f"java_{sensor_id}.csv")
            python=run_python(imu,folder/f"python_{sensor_id}")
            # CRITICAL anti-leakage: reference annotation fetched only AFTER
            # BOTH algorithms have generated their outputs.
            sequence_runs.append({
                "position_id": sensor_id,"position":LOCATIONS[sensor_id],
                "java":java,"python":python,
                "sensor_sha256":hashlib.sha256(imu.read_bytes()).hexdigest()
            })
        ann_provenance=pull(UPSTREAM_REPO,UPSTREAM_SHA,path+".stepNonoverlap",ann,limit=500_000)
        reference=read_step_reference(ann)
        for run in sequence_runs:
            run["java_step_count"]=evaluate_count(run["java"]["results"]["steps"],len(reference))
            run["python_step_count"]=evaluate_count(run["python"]["results"]["steps"],len(reference))
        results.append({"sequence":name,"data_source":raw_provenance,
                        "annotation_source":ann_provenance,
                        "measurement":sensor,"reference_steps":len(reference),
                        "positions":sequence_runs})
        # Do not upload/share private or large sensor data in artifacts: only summary.
    report={
      "schema":1,"campaign":"B-real-IMU-step-count",
      "dataset":"SIMUL six-body-position walking recordings, genuine public data",
      "dataset_upstream_commit":UPSTREAM_SHA,"license":DATA_LICENSE,
      "java_upstream_commit":JAVA_SHA,"java_source_files":java_files,
      "truth":"manually corrected step intervals, not metric position truth",
      "heldout_tuning":"none; defaults fixed before seeing annotation",
      "comparison":"actual Android Java fallback step detector versus separate Python heuristic",
      "limits":[
        "Sensor units checked empirically; no phone orientation-heading available",
        "Java detector does not use raw gyro; it uses accelerator fallback",
        "Step count is not metric distance accuracy or ATE",
        "Ground-truth annotated intervals may not cover all stationary activity",
        "Annotated steps derived from synchronized foot IMUs, not surveyed 2D track",
        "Java sources pinned from parallel branch; NOT a claim that APK combined these sources"
      ],
      "sequences":results
    }
    (out/"real_step_report.json").write_text(json.dumps(report,indent=2)+"\n")
    for r in results:
        for p in r["positions"]:
            score=p["java_step_count"]
            print(f"REAL {r['sequence']} {p['position']} Java {score['count']} "
                  f"reference {score['annotated_steps']} "
                  f"error {score['step_count_error_percent']:+.1f}% "
                  f"Python {p['python_step_count']['count']}",flush=True)
    return report


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--out",type=Path,required=True)
    p.add_argument("--sequences",nargs="+",choices=sorted(SEQUENCES),default=sorted(SEQUENCES))
    p.add_argument("--positions",nargs="+",type=int,choices=sorted(LOCATIONS),default=sorted(LOCATIONS))
    args=p.parse_args()
    campaign(args.out,args.sequences,args.positions)


if __name__=="__main__":
    main()
