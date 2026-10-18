#!/usr/bin/env python3
"""Metric, gravity-aligned translation-only VIO evaluator (standard library only).

Inputs are estimates and *independent* position truth. This does not run an
estimator, align similarity scale, verify coordinate frames or certify GPS.
Align one constant yaw+translation per continuous segment. Segment-wise results
cannot establish globally correct multi-segment location or map continuity.
"""
import argparse
import csv
import json
import math
from bisect import bisect_left
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Sample:
    ns: int
    xyz: tuple
    segment: int = 1


def parse(path, fmt):
    samples = []
    with open(path, encoding="utf-8-sig", newline="") as stream:
        if fmt in {"tum", "euroc"}:
            for line in stream:
                line = line.strip()
                if not line or line.startswith("#"):
                    continue
                cols = line.replace(",", " ").split()
                if len(cols) < 4:
                    raise ValueError("Need time and x/y/z coordinates")
                timestamp = float(cols[0])
                ns = round(timestamp * 1e9) if fmt == "tum" else int(cols[0])
                xyz = tuple(float(v) for v in cols[1:4])
                samples.append(Sample(ns, xyz))
        elif fmt == "vector-motion":
            reader = csv.DictReader(stream)
            required = {"frame_time_ns", "metric_x", "metric_y", "metric_z"}
            if not required <= set(reader.fieldnames or []):
                raise ValueError("Missing VECTOR _motion.csv fields")
            segment = 1
            previous = None
            has_segments = "odometer_segment" in (reader.fieldnames or [])
            for row in reader:
                ns = int(row["frame_time_ns"])
                xyz = tuple(float(row[k]) for k in ["metric_x", "metric_y", "metric_z"])
                if has_segments:
                    segment = int(row["odometer_segment"])
                    if segment <= 0 or (samples and segment < samples[-1].segment):
                        raise ValueError("Invalid or decreasing odometer segment")
                elif previous is not None and ns - previous > 700_000_000:
                    segment += 1
                samples.append(Sample(ns, xyz, segment))
                previous = ns
        else:
            raise ValueError("Unsupported format")
    if len(samples) > 3_000_000:
        raise ValueError("Input too large")
    for a, b in zip(samples, samples[1:]):
        if b.ns <= a.ns:
            raise ValueError("Nonmonotonic/duplicate timestamps")
    if any(s.ns <= 0 or not all(math.isfinite(x) for x in s.xyz) for s in samples):
        raise ValueError("Invalid timestamp or coordinate")
    if not samples:
        raise ValueError("No trajectory samples")
    return samples


def _interp(gt, ns, upper, max_gap_ns):
    while upper < len(gt) and gt[upper].ns < ns:
        upper += 1
    if upper < len(gt) and gt[upper].ns == ns:
        return gt[upper].xyz, upper
    if upper == 0 or upper == len(gt):
        return None, upper
    a, b = gt[upper-1], gt[upper]
    if b.ns-a.ns > max_gap_ns:
        return None, upper
    t = (ns-a.ns) / (b.ns-a.ns)
    return tuple(a.xyz[j] + t*(b.xyz[j]-a.xyz[j]) for j in range(3)), upper


def match(est, truth, time_offset_ns=0, gt_gap_s=0.15):
    if not math.isfinite(gt_gap_s) or gt_gap_s <= 0:
        raise ValueError("Invalid truth gap tolerance")
    max_gap_ns = round(gt_gap_s*1e9)
    pairs = []
    upper = 0
    for sample in est:
        xyz, upper = _interp(truth, sample.ns + time_offset_ns, upper, max_gap_ns)
        if xyz is not None:
            pairs.append((sample, xyz))
    return pairs


def _axes(up):
    if up == "z":
        return (0, 1, 2)
    if up == "y":
        return (0, 2, 1)
    raise ValueError("up must be z or y")


def _transform(p, axes, yaw, t):
    h0,h1,up=axes
    arr=list(p)
    a,b=p[h0],p[h1]
    arr[h0]=math.cos(yaw)*a-math.sin(yaw)*b+t[0]
    arr[h1]=math.sin(yaw)*a+math.cos(yaw)*b+t[1]
    arr[up]+=t[2]
    return tuple(arr)


def _norm(p):
    return math.sqrt(sum(x*x for x in p))


def _diff(a,b):
    return tuple(x-y for x,y in zip(a,b))


def evaluate_segment(pairs, up="z", rpe_dt_s=1., min_baseline_m=0.5):
    if len(pairs) < 4:
        return {"status":"INSUFFICIENT_MATCHES", "matches":len(pairs)}
    axes = _axes(up)
    h0,h1,v=axes
    samples = [sample for sample,_ in pairs]
    truth = [coord for _,coord in pairs]
    est = [sample.xyz for sample in samples]
    origin = est[0]
    baseline = max(_norm(_diff(a,origin)) for a in est)
    if baseline < min_baseline_m:
        return {"status":"UNOBSERVABLE_BASELINE", "matches":len(pairs),"baseline_m":round(baseline,6)}
    em = [sum(a[axis] for a in est)/len(est) for axis in axes]
    gm = [sum(a[axis] for a in truth)/len(truth) for axis in axes]
    c = sum((a[h0]-em[0])*(b[h0]-gm[0])+(a[h1]-em[1])*(b[h1]-gm[1]) for a,b in zip(est,truth))
    s = sum((a[h0]-em[0])*(b[h1]-gm[1])-(a[h1]-em[1])*(b[h0]-gm[0]) for a,b in zip(est,truth))
    if math.hypot(c,s) < 1e-10:
        return {"status":"UNOBSERVABLE_YAW", "matches":len(pairs)}
    yaw = math.atan2(s,c)
    t = (gm[0]-math.cos(yaw)*em[0]+math.sin(yaw)*em[1],
         gm[1]-math.sin(yaw)*em[0]-math.cos(yaw)*em[1], gm[2]-em[2])
    projected = [_transform(a,axes,yaw,t) for a in est]
    errors = [_norm(_diff(a,b)) for a,b in zip(projected,truth)]
    rpe = []
    times = [sample.ns for sample in samples]
    for i,sample in enumerate(samples):
        target = sample.ns + round(rpe_dt_s*1e9)
        j = bisect_left(times, target, i+1)
        candidates=[k for k in (j-1,j) if k>i and k<len(samples)]
        if not candidates:
            continue
        k=min(candidates,key=lambda n:abs(samples[n].ns-target))
        if abs(samples[k].ns-target)>max(150_000_000,round(rpe_dt_s*0.2*1e9)):
            continue
        rpe.append(_norm(_diff(_diff(projected[k],projected[i]),_diff(truth[k],truth[i]))))
    return {"status":"METRIC_RIGID_YAW_ALIGNED", "matches":len(pairs),
            "baseline_m":round(baseline,5),"alignment_yaw_deg":round(math.degrees(yaw),5),
            "ate_rmse_m":round(math.sqrt(sum(e*e for e in errors)/len(errors)),6),
            "ate_median_m":round(sorted(errors)[len(errors)//2],6),
            "ate_p95_m":round(sorted(errors)[min(len(errors)-1,int(len(errors)*.95))],6),
            "rpe_dt_s":rpe_dt_s,"rpe_samples":len(rpe),
            "rpe_translation_rmse_m":round(math.sqrt(sum(e*e for e in rpe)/len(rpe)),6) if rpe else None,
            "metric_scale_fitted":False,"rotation_alignment":"yaw_only", "frame_up_axis":up}


def analyze(est,truth,up="z",time_offset_ns=0,gt_gap_s=.15,rpe_dt_s=1.):
    if rpe_dt_s<=0 or not math.isfinite(rpe_dt_s):
        raise ValueError("Invalid RPE horizon")
    paired = match(est,truth,time_offset_ns,gt_gap_s)
    by_segment = {}
    for p in paired:
        by_segment.setdefault(p[0].segment, []).append(p)
    segments=[]
    for n in sorted({s.segment for s in est}):
        segment_pairs=by_segment.get(n, [])
        row=evaluate_segment(segment_pairs, up, rpe_dt_s)
        row["segment"] = n
        segments.append(row)
    return {"status":"INDEPENDENT_GROUND_TRUTH_REQUIRED", "estimated_samples":len(est),
            "matched_samples":len(paired),"matched_coverage_fraction":round(len(paired)/len(est),5),
            "segments":segments,"global_metric_accuracy":"NOT_EVALUATED_ACROSS_SEGMENT_RESETS",
            "time_offset_ns":time_offset_ns,"estimated_trajectory_fitted_scale":False,
            "caution":"No GT interpolation across gaps. Constant yaw/translation removed per segment."
            " GT must be independent; GNSS alone can have correlated meter-scale bias."
            " Rotational accuracy, absolute geo-registration and failed tracking duration are not computed."}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--est",required=True,type=Path);p.add_argument("--truth",required=True,type=Path)
    p.add_argument("--est-format",choices=["tum","euroc","vector-motion"],required=True)
    p.add_argument("--truth-format",choices=["tum","euroc"],required=True)
    p.add_argument("--up",choices=["y","z"],default="z")
    p.add_argument("--time-offset-ns",type=int,default=0)
    p.add_argument("--gt-gap-s",type=float,default=.15)
    p.add_argument("--rpe-dt-s",type=float,default=1.)
    p.add_argument("--out",type=Path)
    args=p.parse_args()
    data=analyze(parse(args.est,args.est_format),parse(args.truth,args.truth_format),
                 args.up,args.time_offset_ns,args.gt_gap_s,args.rpe_dt_s)
    output=json.dumps(data,indent=2,sort_keys=True,ensure_ascii=False)+"\n"
    if args.out:
        args.out.parent.mkdir(parents=True,exist_ok=True)
        args.out.write_text(output,encoding="utf-8")
    print(output,end="")

if __name__=="__main__":main()
