#!/usr/bin/env python3
"""Audit optional ARCore frame-luminance sidecar; NEVER infer universal lux limit.

Separately count dark track-success and lost-tracking frames, grouped by
TrackingFailureReason. This data cannot prove that illumination CAUSES a
tracking failure: camera motion, motion blur and featureless scenes confound.
"""
from __future__ import annotations
import argparse,csv,json,math
from collections import defaultdict
from pathlib import Path

HEADER={"frame_time_ns","tracking_state","failure_reason","p10_y","p50_y",
        "p90_y","dark_fraction","clipped_fraction","edge_fraction","exposure_ms","iso"}

def quantile(values,q):
    x=sorted(values)
    return x[min(len(x)-1,max(0,math.ceil(q*len(x))-1))] if x else None

def analyze(path):
    data=defaultdict(list)
    last=0;total=0;bad=0
    with Path(path).open(newline="",encoding="utf-8") as file:
        reader=csv.DictReader(file)
        if not reader.fieldnames or not HEADER.issubset(reader.fieldnames):
            raise ValueError("not a VECTOR VIO _arcore_luma.csv file")
        for row in reader:
            try:
                t=int(row["frame_time_ns"])
                p10,p50,p90=(int(row[k]) for k in ("p10_y","p50_y","p90_y"))
                dark=float(row["dark_fraction"])
                clipped=float(row["clipped_fraction"])
                edges=float(row["edge_fraction"])
                exp=float(row["exposure_ms"]);iso=int(row["iso"])
            except (ValueError,TypeError,KeyError) as e:raise ValueError("corrupt camera frame row") from e
            if (t<=last or p10<0 or p90>255 or not p10<=p50<=p90 or
                not 0<=dark<=1 or not 0<=clipped<=1 or not 0<=edges<=1 or
                not math.isfinite(exp) or exp<=0 or iso<1):
                raise ValueError("out-of-order, invalid or unmeasured frame data")
            last=t;total+=1
            state=row["tracking_state"].strip()
            reason=row["failure_reason"].strip()
            if state=="TRACKING":bucket="tracking"
            elif state in ("PAUSED","STOPPED"):bucket="not_tracking/"+reason
            else:raise ValueError("unknown ARCore tracking state")
            data[bucket].append((p50,dark,edges,exp,iso))
            if state!="TRACKING":bad+=1
    if total<2:raise ValueError("too few observations")
    groups={}
    for k,samples in data.items():
        groups[k]={
            "count":len(samples),
            "p50_y_p10":quantile([p[0] for p in samples],.1),
            "p50_y_median":quantile([p[0] for p in samples],.5),
            "p50_y_p90":quantile([p[0] for p in samples],.9),
            "dark_fraction_median":quantile([p[1] for p in samples],.5),
            "edge_fraction_median":quantile([p[2] for p in samples],.5),
            "exposure_ms_median":quantile([p[3] for p in samples],.5),
            "iso_median":quantile([p[4] for p in samples],.5)
        }
    return {"frames":total,"not_tracking_frames":bad,"sampling":"available YUV CPU image",
            "groups":groups,"minimum_valid_lux":None,
            "deployment_threshold":None,
            "confidence":"exploratory diagnostics; needs paired independent field recordings",
            "caveat":"Tracking and light can be confounded by motion/texture; CPU images missing in bad conditions bias data. Separate camera sessions may have different ISP."}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--csv",type=Path,required=True)
    p.add_argument("--out",type=Path,required=True)
    args=p.parse_args()
    result=analyze(args.csv)
    args.out.parent.mkdir(parents=True,exist_ok=True)
    args.out.write_text(json.dumps(result,indent=2,sort_keys=True)+"\n",encoding="utf-8")
    print(json.dumps(result,sort_keys=True))
if __name__=="__main__":main()
