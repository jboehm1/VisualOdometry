#!/usr/bin/env python3
"""Adapter: canonical IMU -> the *actual separate-branch Android Java PdrReplay*.

The Java engine uses accelerometer peak detection, optional phone attitude
heading and barometer. It does NOT consume raw gyro directly. Gyro remains
counted in provenance and is not secretly reinterpreted as heading. Never use
ground-truth yaw, position or distance as an estimator input.
"""
from __future__ import annotations
import argparse
import csv
import hashlib
import heapq
import json
import math
from pathlib import Path

COLUMNS=("t_ns","type","x","y","z")


def sha256(path):
    h=hashlib.sha256()
    with Path(path).open("rb") as f:
        for chunk in iter(lambda:f.read(1024*1024),b""):
            h.update(chunk)
    return h.hexdigest()


def samples(path):
    previous=-1
    with Path(path).open(newline="",encoding="utf-8-sig") as f:
        reader=csv.DictReader(f)
        if not reader.fieldnames or not set(COLUMNS).issubset(reader.fieldnames):
            raise ValueError("canonical IMU CSV must contain t_ns,type,x,y,z")
        for row in reader:
            try:
                t=int(row["t_ns"])
                kind=row["type"].strip().upper()
                values=[float(row[k]) for k in ("x","y","z")]
            except (ValueError,KeyError,TypeError) as exc:
                raise ValueError("Invalid IMU sample") from exc
            if t<=0 or t<previous:raise ValueError("Nonmonotonic IMU timestamps")
            if not all(map(math.isfinite,values)):raise ValueError("Nonfinite IMU sample")
            previous=t
            yield t,kind,*values


def headings(path):
    previous=0
    with Path(path).open(newline="",encoding="utf-8-sig") as f:
        reader=csv.DictReader(f)
        if not reader.fieldnames or not {"t_ns","heading_rad"}.issubset(reader.fieldnames):
            raise ValueError("heading file requires t_ns,heading_rad")
        for row in reader:
            try:
                t=int(row["t_ns"]); angle=float(row["heading_rad"])
            except (ValueError,TypeError,KeyError) as exc:
                raise ValueError("Invalid heading row") from exc
            if t<=previous or not math.isfinite(angle):
                raise ValueError("Unordered or nonfinite device heading")
            previous=t
            yield t,"HEADING",angle,0.,0.


def convert(imu, out, heading_csv=None, heading_source=None,
            constant_heading_rad=None, fixture_only=False):
    imu=Path(imu)
    out=Path(out)
    if heading_csv and constant_heading_rad is not None:
        raise ValueError("Specify either heading CSV or constant test prior")
    if heading_csv and not heading_source:
        raise ValueError("Specify heading source/provenance for measured heading CSV")
    if not heading_csv and heading_source:
        raise ValueError("Heading source given without heading file")
    if constant_heading_rad is not None and not fixture_only:
        raise ValueError("Constant synthetic heading is forbidden for real-data evaluation")
    if constant_heading_rad is not None and not math.isfinite(constant_heading_rad):
        raise ValueError("Nonfinite heading prior")
    if not imu.is_file():raise FileNotFoundError(imu)
    out.parent.mkdir(parents=True,exist_ok=True)
    count={"ACCEL":0,"GYRO":0,"MAG":0,"BARO":0,"HEADING":0,"IGNORED":0}
    # Accelerometer first at timestamp ties; the Java engine consumes events
    # sequentially and has no direct raw gyroscope input.
    def stream_imu():
        for t,kind,x,y,z in samples(imu):
            if kind=="ACCEL":
                count[kind]+=1
                yield t,1,"ACCEL",x,y,z
            elif kind=="BARO":
                count[kind]+=1
                yield t,2,"PRESSURE",x,0.,0.
            elif kind in count:
                count[kind]+=1
            else:
                count["IGNORED"]+=1
    stream=stream_imu()
    if heading_csv:
        extra=((t,0,kind,x,y,z) for t,kind,x,y,z in headings(heading_csv))
        joined=heapq.merge(stream,extra,key=lambda z:(z[0],z[1]))
    else:
        joined=stream
    n=0
    with out.open("w",newline="",encoding="utf-8") as f:
        writer=csv.writer(f)
        writer.writerow(COLUMNS)
        for t,_,kind,x,y,z in joined:
            if constant_heading_rad is not None and kind=="ACCEL":
                writer.writerow((t,"HEADING",format(constant_heading_rad,".12g"),0,0))
                count["HEADING"]+=1;n+=1
            elif kind=="HEADING":
                count["HEADING"]+=1
            writer.writerow((t,kind,x,y,z));n+=1
    if count["ACCEL"]<3:
        raise ValueError("Android PDR needs >=3 actual accelerometer samples")
    metadata={
        "schema":1,"adapter":"canonical_to_android_java_pdr",
        "algorithm":"Java PdrEngine/StepPeakDetector (NOT Python imu_pdr.py)",
        "sensor_input_sha256":sha256(imu),
        "heading_input_sha256":sha256(heading_csv) if heading_csv else None,
        "replay_input_sha256":sha256(out),
        "input_counts":count,"replay_event_count":n,
        "heading_provenance":(
            "synthetic_constant_for_fixtures_ONLY" if constant_heading_rad is not None else
            heading_source if heading_csv else "none"
        ),
        "independent_reference":"not supplied to engine",
        "validation_level":"synthetic_fixture" if fixture_only else "raw_data_adapter",
        "caveats":[
          "Java PdrEngine uses Android STEP_DETECTOR or accelerometer fallback",
          "raw gyro cannot substitute for a calibrated device-heading measurement",
          "distance uses fixed manual stride; motion heading requires measured heading",
          "magnetic heading of phone is not necessarily human travel direction",
          "without heading the Java engine outputs distance, not a full 2D trajectory",
          "this adapter does not supply ground truth or GPS-derived trajectory"
        ],
    }
    (out.parent/(out.stem+".manifest.json")).write_text(
        json.dumps(metadata,indent=2,sort_keys=True)+"\n",encoding="utf-8")
    return metadata


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--imu",type=Path,required=True)
    p.add_argument("--out",type=Path,required=True)
    p.add_argument("--heading-csv",type=Path)
    p.add_argument("--heading-source",type=str)
    p.add_argument("--fixture-only",action="store_true")
    p.add_argument("--constant-heading-rad",type=float)
    args=p.parse_args()
    print(json.dumps(convert(args.imu,args.out,args.heading_csv,args.heading_source,
                             args.constant_heading_rad,args.fixture_only),indent=2))


if __name__=="__main__":
    main()
