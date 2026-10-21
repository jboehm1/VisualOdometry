#!/usr/bin/env python3
"""Camera-off pedestrian dead-reckoning baseline.

Input: benchmark canonical IMU CSV t_ns,type,x,y,z.
Assumes phone sensor Z is vertical/heading yaw axis, 9.81m/s2 gravity
magnitude, fixed stride and stationary bias parameters. This is intentionally
a weak, reproducible baseline, NOT calibrated 6DoF inertial SLAM.
"""
from __future__ import annotations
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path


def pdr(records, *, stride_m=0.7, threshold_ms2=0.9,
        min_interval_s=0.34, yaw_axis="z", gyro_bias=0.0,
        gravity_ms2=9.80665, smoothing_s=0.35):
    if stride_m<=0 or threshold_ms2<=0 or min_interval_s<=0:
        raise ValueError("stride/threshold/interval must be positive")
    axis={"x":0,"y":1,"z":2}[yaw_axis]
    heading=0.0
    pos_x=pos_z=0.0
    last_gyro=None
    last_accel=None
    last_t=None
    trend=None
    last_step=-1e100
    steps=0
    armed=True
    poses=[]
    sensor_counts={"GYRO":0,"ACCEL":0,"MAG":0,"BARO":0}
    for raw in records:
        t,kind,*values=raw
        t=float(t)
        if not math.isfinite(t):
            raise ValueError("Nonfinite IMU time")
        if last_t is not None and t<last_t:
            raise ValueError("Unsorted sensor observations")
        last_t=t
        if kind not in sensor_counts:
            continue
        v=[float(z) for z in values]
        if len(v)!=3 or not all(map(math.isfinite,v)):
            raise ValueError("Nonfinite/missing sensor axes")
        sensor_counts[kind]+=1
        if kind=="GYRO":
            if last_gyro is not None:
                dt=t-last_gyro
                if dt<=0:
                    raise ValueError("Duplicated gyro sample")
                if dt>0.5:
                    # Important: don't integrate across lost sensor intervals.
                    armed=False
                else:
                    heading+=(v[axis]-gyro_bias)*dt
            last_gyro=t
        elif kind=="ACCEL":
            if last_accel is not None:
                dt=t-last_accel
                if dt<=0:
                    raise ValueError("Duplicated accel sample")
                if dt>0.5:
                    trend=None;armed=False
            else:
                dt=0
            mag=math.sqrt(sum(a*a for a in v))
            if trend is None:
                trend=gravity_ms2
            else:
                alpha=math.exp(-dt/smoothing_s)
                trend=alpha*trend+(1-alpha)*mag
            delta=mag-trend
            # One positive peak per interval. Re-arm on return to baseline.
            if delta<threshold_ms2*0.25:
                armed=True
            if armed and delta>=threshold_ms2 and t-last_step>=min_interval_s:
                steps+=1
                pos_x+=stride_m*math.sin(heading)
                pos_z+=stride_m*math.cos(heading)
                last_step=t
                armed=False
            last_accel=t
        # Output at gyro cadence; distinct timestamps for aligned data.
        if kind=="GYRO" and len(poses)==0 or (kind=="GYRO" and t>poses[-1][0]):
            qy=math.sin(heading/2)
            qw=math.cos(heading/2)
            poses.append((t,pos_x,0.0,pos_z,0.0,qy,0.0,qw))
    if sensor_counts["GYRO"]<2 or sensor_counts["ACCEL"]<2:
        raise ValueError("Need at least 2 accelerometer and 2 gyro observations")
    return poses, {
        "algorithm":"fixed-stride acc-peak PDR / gyro heading baseline",
        "frames_processed":len(poses),"steps":steps,
        "path_m":round(steps*stride_m,6),
        "net_displacement_m":round(math.hypot(pos_x,pos_z),6),
        "end_heading_deg":round(math.degrees(heading),4),
        "sensor_counts":sensor_counts,
        "assumptions": [
            "phone orientation fixed with gyro heading along selected device axis",
            "gyro rate bias is manually specified (not estimated from ground truth)",
            "step length is a fixed metric prior, not learned/adapted per sequence",
            "head direction equals phone yaw; no map constraints or full attitude filter",
            "no source camera samples or ground-truth samples read",
            "suitable only for baseline/smoke; evaluate generalization on independent GT"
        ],
    }


def load_canonical(path):
    with Path(path).open(newline="",encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            yield (int(row["t_ns"])*1e-9,row["type"],
                   float(row["x"]),float(row["y"]),float(row["z"]))


def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--imu",type=Path,required=True)
    ap.add_argument("--out",type=Path,required=True)
    ap.add_argument("--stride-m",type=float,default=0.70)
    ap.add_argument("--threshold-ms2",type=float,default=0.9)
    ap.add_argument("--min-interval-s",type=float,default=0.34)
    ap.add_argument("--yaw-axis",choices=["x","y","z"],default="z")
    ap.add_argument("--gyro-bias-rad-s",type=float,default=0)
    a=ap.parse_args()
    poses,report=pdr(load_canonical(a.imu),stride_m=a.stride_m,
        threshold_ms2=a.threshold_ms2,min_interval_s=a.min_interval_s,
        yaw_axis=a.yaw_axis,gyro_bias=a.gyro_bias_rad_s)
    a.out.mkdir(parents=True,exist_ok=True)
    with (a.out/"trajectory.tum").open("w") as f:
        for p in poses:f.write(" ".join(format(x,".10f") for x in p)+"\n")
    report["input_sha256"]=hashlib.sha256(a.imu.read_bytes()).hexdigest()
    report["stride_prior_m"]=a.stride_m
    report["heading_axis"]=a.yaw_axis
    report["gyro_bias_rad_s"]=a.gyro_bias_rad_s
    (a.out/"report.json").write_text(json.dumps(report,indent=2)+"\n")
    print(json.dumps(report,indent=2))


if __name__=="__main__":
    main()
