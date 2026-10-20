#!/usr/bin/env python3
"""Model-neutral PDR 2D outage evaluation in an explicit WORLD frame.

Do not infer velocities from ground truth after initialization. Model predictions
are increments, not absolute position. GT is used strictly for scoring. Evaluating
on a split is NOT itself a claim the model trained without target leakage.
"""
from __future__ import annotations
import argparse, csv, json, math
from pathlib import Path

def load_csv(path, required):
    with Path(path).open(newline="", encoding="utf-8") as f:
        rows=list(csv.DictReader(f))
    if len(rows)<2 or any(not set(required).issubset(r) for r in rows):
        raise ValueError("missing required fields or fewer than two rows")
    values=[]
    last=-1
    for row in rows:
        try:
            t=int(row["time_ns"])
            data={k:float(row[k]) for k in required if k!="time_ns"}
        except (ValueError,KeyError,TypeError) as e:
            raise ValueError("invalid numeric row") from e
        if t<=last or not all(map(math.isfinite,data.values())):
            raise ValueError("out of order timestamps or non-finite model / truth")
        last=t
        values.append((t,data))
    return values

def evaluate(model_rows, truth_rows, model_name="model", split="test"):
    """Exact timestamps and 2D coordinates; comparison in world metres."""
    if len(model_rows)!=len(truth_rows):raise ValueError("sample counts differ")
    if any(m[0]!=g[0] for m,g in zip(model_rows,truth_rows)):
        raise ValueError("model and truth timestamps differ: no unverified interpolation")
    start=model_rows[0][0]
    if model_rows[0][1]["dx_world_m"]!=0 or model_rows[0][1]["dz_world_m"]!=0:
        raise ValueError("first model row must be anchor zero-delta")
    x=truth_rows[0][1]["x_world_m"];z=truth_rows[0][1]["z_world_m"]
    errors=[0.0];track_length=0.0;pred_path=0.0
    elapsed=0.0
    for i in range(1,len(model_rows)):
        t,p=model_rows[i];pt,truth=truth_rows[i]
        dt=(t-model_rows[i-1][0])/1e9
        if dt<=0 or dt>2:raise ValueError("IMU prediction gap over two seconds")
        dx=p["dx_world_m"];dz=p["dz_world_m"]
        if math.hypot(dx,dz)>25*dt+.15:raise ValueError("implausible delta > bicycle limit")
        x+=dx;z+=dz;pred_path+=math.hypot(dx,dz)
        a=truth_rows[i-1][1];b=truth
        track_length+=math.hypot(b["x_world_m"]-a["x_world_m"],
                                b["z_world_m"]-a["z_world_m"])
        errors.append(math.hypot(x-b["x_world_m"],z-b["z_world_m"]))
    elapsed=(model_rows[-1][0]-start)/1e9
    rms=math.sqrt(sum(v*v for v in errors)/len(errors))
    s=sorted(errors);p95=s[math.ceil(.95*len(s))-1]
    return {"model":model_name,"split":split,"coordinate_frame":"world_2d_m",
            "anchor":"truth_position_first_row_only",
            "duration_s":round(elapsed,5),"n":len(model_rows),
            "truth_distance_m":round(track_length,6),
            "predicted_distance_m":round(pred_path,6),
            "rmse_m":round(rms,6),"endpoint_error_m":round(errors[-1],6),
            "p95_position_error_m":round(p95,6),
            "endpoint_drift_percent_of_distance":round(100*errors[-1]/track_length,6)
                if track_length>0 else None}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--prediction",required=True,help="CSV time_ns,dx_world_m,dz_world_m; 1st delta 0")
    p.add_argument("--truth",required=True,help="CSV time_ns,x_world_m,z_world_m; evaluation-only")
    p.add_argument("--model",required=True)
    p.add_argument("--split",choices=("train","val","test"),required=True)
    p.add_argument("--out",required=True)
    args=p.parse_args()
    pred=load_csv(args.prediction,("time_ns","dx_world_m","dz_world_m"))
    truth=load_csv(args.truth,("time_ns","x_world_m","z_world_m"))
    score=evaluate(pred,truth,args.model,args.split)
    path=Path(args.out);path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(score,indent=2,sort_keys=True)+"\n",encoding="utf-8")
    print(json.dumps(score,sort_keys=True))

if __name__=="__main__":main()
