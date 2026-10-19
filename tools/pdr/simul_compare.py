#!/usr/bin/env python3
"""Paired REAL SIMUL step benchmark against pinned previous Java engine.

Sensor recordings are public and fixed by Git SHA. Step reference is fetched
only after both Java engines have emitted predictions. No GT is fed to them.
Evaluates annotated STEP INTERVALS, not metric position / odometry accuracy.
The 4 previously reported participants are a "seen" regression cohort;
the additional 2 participant IDs are frozen in the script as an external
candidate test, NOT an untouched benchmark if someone later tunes to them.
"""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import statistics
import subprocess
import tempfile
import urllib.request

ROOT=Path(__file__).resolve().parents[2]
UPSTREAM="ee7eb4e89bf33a17cafb4268207b802773427493"
BASELINE="f8228aa7acbc453001d623c537bd8d7f0a9bc176"
SOURCE_REPO="simpleLoc/SIMUL-dataset"
BASE_REPO="jboehm1/VisualOdometry"
JAVA_REL="app/src/main/java/dev/jboehm/vectorvio/pdr/"
COHORT={
    "08":"data/by-person/8/awindaRecording_20230424133253.csv",
    "17":"data/by-person/17/awindaRecording_20230403173917.csv",
    "24":"data/by-person/24/awindaRecording_20230506184711.csv",
    "25":"data/by-person/25/awindaRecording_20230124150358.csv",
    "12":"data/by-person/12/awindaRecording_20230124153538.csv",
    "31":"data/by-person/31/awindaRecording_20230403142813.csv",
}
PLACEMENTS={3:"front_pocket",5:"hand",6:"back_pocket"}
MAX_RAW_BYTES=42_000_000

def fetch(repo,commit,path,dest,limit):
    url=f"https://raw.githubusercontent.com/{repo}/{commit}/{path}"
    req=urllib.request.Request(url,headers={"User-Agent":"VECTOR-PDR-step-real-regression/1"})
    digest=hashlib.sha256();total=0
    dest.parent.mkdir(parents=True,exist_ok=True)
    with urllib.request.urlopen(req,timeout=90) as response, dest.open("wb") as out:
        if not response.geturl().startswith("https://raw.githubusercontent.com/"):
            raise ValueError("unexpected download origin")
        while True:
            block=response.read(262144)
            if not block:break
            total+=len(block)
            if total>limit:raise ValueError("source exceeds size limit")
            digest.update(block);out.write(block)
    if total<30:raise ValueError("empty source")
    return {"sha256":digest.hexdigest(),"bytes":total,"url":url}

def compile_engines(work):
    src=(ROOT/JAVA_REL)
    harness=ROOT/"tools/pdr/SimulStepTrace.java"
    classes={}
    for name in ("baseline","candidate"):
        out=work/("classes_"+name);out.mkdir()
        if name=="baseline":
            code=[]
            for f in ("StepPeakDetector.java","PdrEngine.java"):
                dest=work/"baseline_src"/f
                fetch(BASE_REPO,BASELINE,JAVA_REL+f,dest,100_000)
                code.append(str(dest))
        else:
            code=[str(src/f) for f in ("StepPeakDetector.java","PdrEngine.java")]
        subprocess.run(["javac","-encoding","UTF-8","-d",str(out),
                        *code,str(harness)],check=True,capture_output=True,text=True)
        classes[name]=out
    return classes

def imu_data(src,folder):
    files={index:(folder/f"sensor_{index}.csv").open("w",newline="") for index in PLACEMENTS}
    writers={i:csv.writer(f) for i,f in files.items()}
    for writer in writers.values():writer.writerow(("t_ns","ax","ay","az"))
    times=[];prev=-1;first=None;median_sample={i:[] for i in PLACEMENTS}
    try:
        with src.open(newline="",encoding="utf-8-sig") as fp:
            reader=csv.reader(fp);header=next(reader)
            if len(header)!=98 or header[0].strip()!="Timestamp" or header[1].strip()!="Activity":
                raise ValueError("invalid SIMUL schema")
            for row in reader:
                if len(row)!=98:raise ValueError("invalid SIMUL sample width")
                t=float(row[0])
                if not math.isfinite(t):raise ValueError("nonfinite time")
                if first is None:first=t
                ns=1_000_000_000+round((t-first)*1e9)
                if ns<=prev:raise ValueError("duplicate/unsorted sensor timestamp")
                prev=ns;times.append(ns)
                for i in PLACEMENTS:
                    idx=2+16*(i-1)
                    v=[float(s) for s in row[idx:idx+3]]
                    if not all(map(math.isfinite,v)):raise ValueError("invalid accel")
                    writers[i].writerow((ns,*v))
                    if len(median_sample[i])<500:median_sample[i].append(math.sqrt(sum(c*c for c in v)))
    finally:
        for f in files.values():f.close()
    if len(times)<500:raise ValueError("too few samples")
    for i,v in median_sample.items():
        median=statistics.median(v)
        if not 5<=median<=16:raise ValueError(f"unexpected gravity {i}: {median}")
    return times

def infer(classes,canonical):
    all_pred={}
    for name,build in classes.items():
        predictions={}
        for i in PLACEMENTS:
            p=subprocess.run(["java","-cp",str(build),"dev.jboehm.vectorvio.pdr.SimulStepTrace",
                              str(canonical/f"sensor_{i}.csv")],
                             check=True,capture_output=True,text=True)
            stamps=[int(x) for x in p.stdout.splitlines()]
            if any(b<=a for a,b in zip(stamps,stamps[1:])):raise ValueError("nonstrict steps")
            predictions[i] = stamps
        all_pred[name]=predictions
    return all_pred

def truth_intervals(path,n):
    intervals=[];last=0
    with path.open(newline="") as fp:
        for row in csv.reader(fp):
            if len(row)!=2:raise ValueError("invalid annotation row")
            a,b=map(int,row)
            if a<=last or b<a or a<1 or b>n:raise ValueError("annotation not ordered")
            last=b;intervals.append((a,b))
    if not intervals:raise ValueError("empty annotations")
    return intervals

def metrics(steps,annotated,ts):
    """One-to-one ordered matching within foot-step intervals +/-80ms."""
    match=0;cursor=0;tol=80_000_000
    for a,b in annotated:
        start=ts[a-1]-tol;end=ts[b-1]+tol
        while cursor<len(steps) and steps[cursor]<start:cursor+=1
        if cursor<len(steps) and steps[cursor]<=end:
            cursor+=1;match+=1
    ref=len(annotated);cnt=len(steps)
    return {"count":cnt,"reference":ref,"matched":match,
            "false_positives":cnt-match,"missed_steps":ref-match,
            "count_error_pct":round(100*(cnt-ref)/ref,3),
            "precision":round(match/cnt,6) if cnt else 0,
            "recall":round(match/ref,6),
            "f1":round(2*match/(cnt+ref),6) if cnt+ref else 0}

def summary(rows):
    # Macro F1 ensures short recordings and hard placements remain visible.
    return {
        "trials":len(rows),
        "mean_f1":round(statistics.mean(x["f1"] for x in rows),6),
        "mean_abs_count_error_pct":round(statistics.mean(abs(x["count_error_pct"]) for x in rows),3),
        "total_predicted":sum(x["count"] for x in rows),
        "total_reference":sum(x["reference"] for x in rows),
        "total_false_positives":sum(x["false_positives"] for x in rows),
        "total_missed":sum(x["missed_steps"] for x in rows),
    }

def run(out,cohort):
    out.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory() as td:
        tmp=Path(td)
        classes=compile_engines(tmp)
        results=[]
        for participant in cohort:
            seq=COHORT[participant]
            local=tmp/participant;local.mkdir()
            raw=local/"recording.csv";annot=local/"steps.stepNonoverlap"
            info=fetch(SOURCE_REPO,UPSTREAM,seq,raw,MAX_RAW_BYTES)
            times=imu_data(raw,local)
            estimates=infer(classes,local)
            # Only now load annotated steps: never provided to either engine.
            annotation=fetch(SOURCE_REPO,UPSTREAM,seq+".stepNonoverlap",annot,500_000)
            reference=truth_intervals(annot,len(times))
            for i,placement in PLACEMENTS.items():
                a=metrics(estimates["baseline"][i],reference,times)
                b=metrics(estimates["candidate"][i],reference,times)
                row={"participant":participant,"cohort":"previously_seen" if participant in ("08","17","24","25") else "additional_frozen",
                     "placement":placement,"baseline":a,"candidate":b}
                results.append(row)
                print(f"SIMUL_REAL {participant} {placement} ref={len(reference)} "+
                    f"old={a['count']} f1={a['f1']:.3f} new={b['count']} f1={b['f1']:.3f}",flush=True)
            # attach dataset provenance only once per source
            (out/f"source_{participant}.json").write_text(json.dumps({
                "participant":participant,"recording":info,"annotation":annotation,
                "samples":len(times),"reference_steps":len(reference)},indent=2)+"\n")
        old=summary([r["baseline"] for r in results])
        current=summary([r["candidate"] for r in results])
        seen=[r for r in results if r["cohort"]=="previously_seen"]
        additional=[r for r in results if r["cohort"]=="additional_frozen"]
        report={"schema":1,"dataset":"REAL SIMUL","dataset_sha":UPSTREAM,
                "baseline_java_sha":BASELINE,"candidate_engine":"checked-out Java PdrEngine.java + StepPeakDetector.java",
                "candidate_note":"Record exact GitHub Actions head SHA in run metadata",
                "ground_truth":"annotated foot-IMU step intervals ONLY, no metric trajectories",
                "annotation_access":"AFTER both estimators",
                "seen_baseline":{"old":summary([r["baseline"] for r in seen]),
                                 "candidate":summary([r["candidate"] for r in seen])},
                "additional_frozen":{"old":summary([r["baseline"] for r in additional]),
                                     "candidate":summary([r["candidate"] for r in additional])},
                "overall":{"old":old,"candidate":current,
                           "delta_mean_f1":round(current["mean_f1"]-old["mean_f1"],6),
                           "delta_mean_abs_count_error_pct":round(
                             current["mean_abs_count_error_pct"]-old["mean_abs_count_error_pct"],3)},
                "trials":results,
                "limitations":["not a Galaxy S21 or native STEP_DETECTOR trial",
                   "not 2D, stride or metric position ground truth",
                   "subjects 08,17,24,25 have already been exposed as regression results",
                   "extra 12,31 fixed before these candidate evaluation results; not general population",
                   "interval F1 is not exact heel-strike timing precision"]}
        (out/"paired_report.json").write_text(json.dumps(report,indent=2)+"\n")
        print("SIMUL_SUMMARY "+json.dumps(report["overall"],sort_keys=True),flush=True)
        print("SIMUL_SEEN "+json.dumps(report["seen_baseline"],sort_keys=True),flush=True)
        print("SIMUL_ADDITIONAL "+json.dumps(report["additional_frozen"],sort_keys=True),flush=True)
        return report

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--out",type=Path,required=True)
    parser.add_argument("--cohort",nargs="*",choices=COHORT.keys(),default=list(COHORT))
    args=parser.parse_args();run(args.out,args.cohort)

if __name__=="__main__":main()
