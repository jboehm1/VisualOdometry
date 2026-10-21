"""Prespecified research selection, then fresh subject evaluation; no APK promotion.

All previously inspected subjects remain development/regression data. IDs
15/16 are reserved before this timing experiment; their annotations are first accessed
only after the chosen algorithm and selection receipt have been frozen.
SIMUL annotates steps, not metre-scale trajectories or smartphone accuracy.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile

PDR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PDR))
import simul_compare as data

MODES = ("current", "smooth", "adaptive", "adaptive_event_time")
RESERVED = {
    "15": "data/by-person/15/awindaRecording_20230418131221.csv",
    "16": "data/by-person/16/awindaRecording_20230501202002.csv",
}
DEVELOPMENT = dict(data.COHORT, **{
    "13": "data/by-person/13/awindaRecording_20230320161307.csv",
    "14": "data/by-person/14/awindaRecording_20230418125909.csv",
})
POLICY = {"minimum_macro_f1_gain": 0., "maximum_trial_f1_loss": .04,
          "minimum_count_mae_reduction_pp": 0., "tie_break": list(MODES)}


def sha(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def assessment(rows, name):
    if not rows: raise ValueError("empty evaluation cohort")
    base=data.summary([r["metrics"]["current"] for r in rows])
    candidate=data.summary([r["metrics"][name] for r in rows])
    f1=candidate["mean_f1"]-base["mean_f1"]
    count=base["mean_abs_count_error_pct"]-candidate["mean_abs_count_error_pct"]
    worst=min(r["metrics"][name]["f1"]-r["metrics"]["current"]["f1"] for r in rows)
    return {"baseline":base, "candidate":candidate, "delta_f1":f1,
            "count_mae_reduction_pp":count, "worst_trial_delta_f1":worst,
            "nonregression":f1>=-1e-12 and count>=-1e-12 and worst>=-.04-1e-12,
            "strict_gain":f1>1e-12 or count>1e-12}


def select(rows):
    # No access to reserved rows. Per-person regressions cannot disappear in
    # a pooled event count or behind a slightly better average.
    scores={name:assessment(rows,name) for name in MODES}
    eligible=[name for name in MODES if scores[name]["nonregression"]]
    winner=max(eligible,key=lambda n:(scores[n]["delta_f1"],
                                     scores[n]["count_mae_reduction_pp"],-MODES.index(n)))
    return winner,scores


def compile_java(work):
    code=[data.ROOT/data.JAVA_REL/name for name in ("PdrEngine.java","StepPeakDetector.java")]
    code += list(Path(__file__).parent.glob("*.java"))
    out=work/"classes";out.mkdir()
    subprocess.run(["javac","-encoding","UTF-8","-d",str(out),*map(str,code)],check=True)
    return out, {str(p.relative_to(data.ROOT)):sha(p) for p in code}


def subject(work, out, classes, person, source, modes):
    folder=work/person;folder.mkdir()
    recording=folder/"recording.csv"
    identity=data.fetch(data.SOURCE_REPO,data.UPSTREAM,source,recording,80_000_000)
    times=data.imu_data(recording,folder)
    predictions={};delays={}
    for sensor,placement in data.PLACEMENTS.items():
        run=subprocess.run(["java","-cp",str(classes),
            "dev.jboehm.vectorvio.pdr.research.StepResearchTrace",str(folder/f"sensor_{sensor}.csv")],
            check=True,capture_output=True,text=True,timeout=120)
        parsed=json.loads(run.stdout)
        predictions[placement]={name:parsed[name] for name in modes}
        delay=parsed['event_confirmation_delay_ns']
        if len(delay)!=len(parsed['adaptive_event_time']) or any(x<0 for x in delay):
            raise ValueError('Invalid observed event confirmation delay')
        delays[placement]={'count':len(delay),'mean_ms':sum(delay)/len(delay)/1e6 if delay else None,
                           'max_ms':max(delay)/1e6 if delay else None}
        for name,prediction in predictions[placement].items():
            if any(b<=a for a,b in zip(prediction,prediction[1:])):raise ValueError('Events not monotonic')
    # The Java process receives only raw accelerometer CSV. It has now exited.
    annotation=folder/"annotations"
    ref_id=data.fetch(data.SOURCE_REPO,data.UPSTREAM,source+".stepNonoverlap",annotation,500_000)
    reference=data.truth_intervals(annotation,len(times))
    rows=[{"participant":person,"placement":placement,'event_confirmation_delay':delays[placement],
           "metrics":{name:data.metrics(pred,reference,times) for name,pred in estimates.items()}}
          for placement,estimates in predictions.items()]
    (out/f"subject-{person}.json").write_text(json.dumps({"source":identity,
        "annotation":ref_id,"samples":len(times),"trials":rows},indent=2)+"\n")
    return rows


def run(out):
    out.mkdir(parents=True,exist_ok=False)
    if set(RESERVED)&set(DEVELOPMENT):raise ValueError("subject split overlap")
    with tempfile.TemporaryDirectory() as td:
        work=Path(td);classes,source_hashes=compile_java(work)
        seen=[]
        for person,source in DEVELOPMENT.items():
            seen+=subject(work,out,classes,person,source,MODES)
        selected,development=select(seen)
        freeze={"selected":selected,"policy":POLICY,"source_sha256":source_hashes,
                "development_subjects":list(DEVELOPMENT),"reserved_subjects":list(RESERVED),
                "development":development,"development_trials":seen}
        frozen=json.dumps(freeze,indent=2,sort_keys=True)+"\n"
        (out/"selection-frozen-before-reserved.json").write_text(frozen)
        reserved=[]
        for person,source in (RESERVED.items() if selected!='current' else []):
            # Only the frozen selected challenger is scored on reserved people.
            # Harness computes causal unlabeled candidates, but no new selection
            # can inspect their reserved labels or feed them back to Android.
            reserved+=subject(work,out,classes,person,source,tuple(dict.fromkeys(("current",selected))))
        result=assessment(reserved,selected) if reserved else {'status':'NOT_RUN_NO_ELIGIBLE_CHALLENGER'}
        report={"schema":"vector-pdr-research-selection/1","dataset_sha":data.UPSTREAM,
            "engine":"exact Java PdrEngine baseline; isolated PeakCandidate challengers",
            "selection":selected,"selection_receipt_sha256":hashlib.sha256(frozen.encode()).hexdigest(),
            "reserved":result,"reserved_trials":reserved,
            "measured_step_gain":selected!="current" and result["nonregression"] and result["strict_gain"],
            "apk_changed":False,"metric_distance_or_position_validated":False,
            "limitations":["Step intervals only, no metric trajectory ground truth",
                "Two reserved participants do not establish population/S21 generalization",
                "Reserved IDs consumed only if actually scored; future tuning then requires new people",
                "Event-time mode dates a measured threshold crossing, decided later at a peak; no GT clock fit",
                "No trained body-heading/displacement model or automatic Android promotion"]}
        (out/"report.json").write_text(json.dumps(report,indent=2,allow_nan=False)+"\n")
        print(json.dumps(report,indent=2),flush=True)
        return report


if __name__=="__main__":
    p=argparse.ArgumentParser();p.add_argument("--out",type=Path,required=True)
    run(p.parse_args().out)
