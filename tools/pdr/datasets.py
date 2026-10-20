#!/usr/bin/env python3
"""Native OxIOD synchronized CSV / RoNIN HDF5 -> exact Java PdrReplay events.

Estimator inputs are acceleration and manually configured stride ONLY.
Device gyro/magnetometer are captured separately. Ground truth is NEVER
copied into events. No external download, model, camera, or GPS is required.
"""
import argparse
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import tempfile

G=9.80665
HEAD=("t_ns","type","x","y","z","bearing_accuracy_deg")
RAW=("t_ns","type","x","y","z","w","accuracy")
TRUTH=("t_ns","x_m","y_m","z_m")

def checksum(file):
    h=hashlib.sha256()
    with Path(file).open("rb") as f:
        for chunk in iter(lambda:f.read(1024*1024),b""):h.update(chunk)
    return h.hexdigest()

def number(value, label):
    try: v=float(value)
    except (ValueError,TypeError) as exc:raise ValueError("Invalid "+label) from exc
    if not math.isfinite(v):raise ValueError("Nonfinite "+label)
    return v

def stamp(raw, origin, last=None):
    t=1_000_000_000+round((raw-origin)*1e9)
    if t<=0 or last is not None and t<=last:raise ValueError("Non-increasing timestamps")
    return t

def record(w,t,kind,values):
    w.writerow([t,kind,*[format(x,".12g") for x in values],""])

def diagnostics(w,t,kind,values):
    w.writerow([t,kind,*[format(x,".12g") for x in values],"",""])

def oxrows(path,width):
    with Path(path).open(newline="",encoding="utf-8-sig") as f:
        first=True
        for lineno,row in enumerate(csv.reader(f),1):
            if not row or not any(s.strip() for s in row) or row[0].lstrip().startswith("#"):continue
            if first:
                first=False
                try:float(row[0])
                except ValueError:continue # sole permitted header line
            if len(row)<width:raise ValueError("OxIOD line %d has fewer than %d columns"%(lineno,width))
            yield [number(x,"OxIOD row %d col %d"%(lineno,i)) for i,x in enumerate(row[:width])]

def validate_stride(step_length):
    value=number(step_length,"stride")
    if not .35<=value<=1.8:raise ValueError("Step length outside [.35,1.8]m")
    return value

def write_truth(target,points):
    count=0;first=last=None;prev=None;xy=xyz=0.0
    with target.open("w",newline="",encoding="utf-8") as f:
        w=csv.writer(f);w.writerow(TRUTH)
        for t,x,y,z in points:
            w.writerow([t,x,y,z])
            if prev is not None:
                dx,dy,dz=x-prev[0],y-prev[1],z-prev[2]
                xy+=math.hypot(dx,dy);xyz+=math.sqrt(dx*dx+dy*dy+dz*dz)
            if first is None:first=t
            last=t;prev=(x,y,z);count+=1
    if count<3:raise ValueError("Reference needs >=3 poses")
    return dict(reference_pose_count=count,reference_start_ns=first,
                reference_end_ns=last,reference_polyline_xy_m=xy,
                reference_polyline_xyz_m=xyz,
                reference_distance_kind="unsmoothed reference pose polyline; may be noise-biased")

def atomic_destination(out, converter):
    out=Path(out)
    out.parent.mkdir(parents=True,exist_ok=True)
    if out.exists():raise FileExistsError("Refusing to overwrite "+str(out))
    with tempfile.TemporaryDirectory(prefix=".pdr-stage-",dir=out.parent) as d:
        stage=Path(d)/"result";stage.mkdir()
        manifest=converter(stage)
        (stage/"manifest.json").write_text(json.dumps(manifest,indent=2,
                                  sort_keys=True,allow_nan=False)+"\n",encoding="utf-8")
        os.replace(stage,out)
    return manifest

def metadata(dataset,stage,source_files,steps,first,last,stride,quality,reference,other):
    m=dict(schema_version=2,dataset=dataset,
       engine="Java PdrEngine and StepPeakDetector, not Python imu_pdr",
       input_events="events.csv",
       input_acceleration="full 3-axis acceleration including gravity m/s^2",
       input_reference_to_engine=False,heading_truth_leakage=False,
       supplied_device_heading=False,
       source_files_sha256={name:checksum(path) for name,path in source_files.items()},
       files_sha256={name:checksum(stage/name) for name in ("events.csv","raw_sensors.csv")},
       imu_samples=steps,first_event_ns=first,last_imu_ns=last,
       timebase="source seconds relative to first included IMU sample plus 1 second",
       step_length_m=stride,step_length_provenance="manual uncalibrated assumption",
       reference_quality=quality or "none",reference_file="reference_xyz.csv" if reference else None,
       reference=reference,
       ground_truth_coordinate_frame="source native XYZ, not magnetic EN",
       limits=["Only step-peak distance; cannot report a full 2D trajectory",
               "Reference never used for heading, calibration or estimator input",
               "Reference polyline length is unsmoothed and may exaggerate real distance",
               "No S21 measurements or real corpus benchmark executed by this adapter"])
    m.update(other);return m

def prepare_oxiod(imu,out,reference=None,quality=None,stride=.74):
    imu=Path(imu);reference=Path(reference) if reference else None
    stride=validate_stride(stride)
    if bool(reference)!=bool(quality):raise ValueError("Reference path and explicit quality required together")
    if quality not in (None,"optical","visual_inertial_proxy"):raise ValueError("Invalid reference quality")
    if not imu.is_file():raise FileNotFoundError(imu)
    if reference is not None and not reference.is_file():raise FileNotFoundError(reference)
    def convert(stage):
        n=0;origin=last=None
        with (stage/"events.csv").open("w",newline="",encoding="utf-8") as ev, \
             (stage/"raw_sensors.csv").open("w",newline="",encoding="utf-8") as raw:
            ew=csv.writer(ev);rw=csv.writer(raw);ew.writerow(HEAD);rw.writerow(RAW)
            for row in oxrows(imu,16):
                if origin is None:origin=row[0]
                t=stamp(row[0],origin,last)
                if n==0:
                    record(ew,t,"START",(0,0,0))
                    record(ew,t,"STEP_LENGTH",(stride,0,0))
                accel=[(g+a)*G for g,a in zip(row[7:10],row[10:13])]
                record(ew,t,"ACCEL",accel)
                diagnostics(rw,t,"RAW_ACCEL",accel)
                diagnostics(rw,t,"RAW_GYRO",row[4:7])
                diagnostics(rw,t,"RAW_MAG",row[13:16])
                n+=1;last=t
        if n<10:raise ValueError("Too few OxIOD IMU samples")
        reference_stats=None
        if reference is not None:
            def refs():
                prev=None
                for row in oxrows(reference,9):
                    # Ground-truth logger may have started before the phone IMU.
                    # Skip pre-IMU poses without rejecting a harmless negative
                    # relative timestamp or leaking them into the estimator.
                    if row[0] < origin:
                        continue
                    t=stamp(row[0],origin,prev)
                    prev=t
                    if t>last:break
                    yield (t,*row[2:5])
            reference_stats=write_truth(stage/"reference_xyz.csv",refs())
            reference_stats["reference_coverage_time_fraction"]=(
                 (reference_stats["reference_end_ns"]-reference_stats["reference_start_ns"])/
                 (last-1_000_000_000))
        files={"imu":imu}
        if reference is not None:files["reference"]=reference
        return metadata("oxiod",stage,files,n,1_000_000_000,last,stride,
                        quality,reference_stats,{"source_schema":"OxIOD native syn CSV: time, attitude3,gyro3,gravity3 g,user_acc3 g,mag3 uT",
                        "diagnostic_counts":{"ACCEL":n,"GYRO":n,"MAG":n},
                        "truth_provenance_requires_operator_audit":reference is not None})
    return atomic_destination(out,convert)

def prepare_ronin(sequence,out,quality=None,stride=.74,subject_id=None):
    sequence=Path(sequence);stride=validate_stride(stride)
    h5=sequence/"data.hdf5";infofile=sequence/"info.json"
    if not h5.is_file() or not infofile.is_file():
        raise FileNotFoundError("RoNIN requires data.hdf5 and info.json in a sequence directory")
    if quality not in (None,"visual_inertial_proxy"):
        raise ValueError("RoNIN Tango is visual-inertial reference, never optical truth")
    try:import h5py
    except ImportError as e:
        raise ImportError("Install -r tools/pdr/requirements-datasets.txt") from e
    info=json.loads(infofile.read_text(encoding="utf-8"))
    if not isinstance(info,dict):raise ValueError("Invalid RoNIN info.json object")
    start=info.get("start_frame",0)
    if type(start) is not int or start<0:raise ValueError("Invalid RoNIN start_frame")
    def convert(stage):
        with h5py.File(h5,"r") as src:
            required=["synced/time","synced/acce","synced/gyro"]
            for key in required:
                if key not in src:raise ValueError("Missing HDF5 key "+key)
            time=src["synced/time"];acc=src["synced/acce"];gyro=src["synced/gyro"];n=len(time)
            if n<start+10 or tuple(acc.shape)!=(n,3) or tuple(gyro.shape)!=(n,3):
                raise ValueError("RoNIN synced time/acceleration/gyro shape mismatch")
            mag=src["synced/magnet"] if "synced/magnet" in src else None
            if mag is not None and tuple(mag.shape)!=(n,3):
                raise ValueError("RoNIN synced/magnet shape mismatch")
            pose=src["pose/tango_pos"] if quality is not None and "pose/tango_pos" in src else None
            if quality is not None and (pose is None or tuple(pose.shape)!=(n,3)):
                raise ValueError("Requested RoNIN Tango reference missing/misaligned")
            first=number(time[start],"RoNIN start time")
            last=None;count=0
            with (stage/"events.csv").open("w",newline="",encoding="utf-8") as ev, \
                 (stage/"raw_sensors.csv").open("w",newline="",encoding="utf-8") as raw:
                ew=csv.writer(ev);rw=csv.writer(raw);ew.writerow(HEAD);rw.writerow(RAW)
                for i in range(start,n):
                    t=stamp(number(time[i],"RoNIN time"),first,last)
                    a=[number(v,"RoNIN raw acceleration") for v in acc[i]]
                    g=[number(v,"RoNIN gyro") for v in gyro[i]]
                    if count==0:
                        record(ew,t,"START",(0,0,0))
                        record(ew,t,"STEP_LENGTH",(stride,0,0))
                    record(ew,t,"ACCEL",a);diagnostics(rw,t,"RAW_ACCEL",a)
                    diagnostics(rw,t,"RAW_GYRO",g)
                    if mag is not None:
                        diagnostics(rw,t,"RAW_MAG",[number(v,"RoNIN mag") for v in mag[i]])
                    last=t;count+=1
            reference_stats=None
            if pose is not None:
                def refs():
                    for i in range(start,n):
                        t=stamp(number(time[i],"RoNIN ref time"),first)
                        yield (t,*[number(v,"Tango pose") for v in pose[i]])
                reference_stats=write_truth(stage/"reference_xyz.csv",refs())
                reference_stats["reference_coverage_time_fraction"]=1.0
            return metadata("ronin",stage,{"data.hdf5":h5,"info.json":infofile},
                 count,1_000_000_000,last,stride,quality,reference_stats,{
                 "source_schema":"RoNIN synced/time seconds, synced/acce m/s2, synced/gyro rad/s",
                 "start_frame":start,"subject_id":subject_id or "unknown; split not verified",
                 "device":info.get("device","unknown"),
                 "diagnostic_counts":{"ACCEL":count,"GYRO":count,"MAG":count if mag is not None else 0},
                 "original_imu_calibration_applied":False,
                 "source_ground_truth_is_body_tango_proxy":quality is not None})
    return atomic_destination(out,convert)

def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__)
    sub=p.add_subparsers(dest="dataset",required=True)
    a=sub.add_parser("oxiod")
    a.add_argument("--imu",type=Path,required=True)
    a.add_argument("--reference",type=Path)
    a.add_argument("--reference-quality",choices=("optical","visual_inertial_proxy"))
    b=sub.add_parser("ronin")
    b.add_argument("--sequence",type=Path,required=True)
    b.add_argument("--reference-quality",choices=("visual_inertial_proxy",))
    b.add_argument("--subject-id")
    for s in (a,b):
        s.add_argument("--out",type=Path,required=True)
        s.add_argument("--step-length-m",type=float,default=.74)
    args=p.parse_args(argv)
    if args.dataset=="oxiod":
        result=prepare_oxiod(args.imu,args.out,args.reference,
                             args.reference_quality,args.step_length_m)
    else:
        result=prepare_ronin(args.sequence,args.out,args.reference_quality,
                             args.step_length_m,args.subject_id)
    print(json.dumps(result,indent=2,sort_keys=True,allow_nan=False))

if __name__=="__main__":main()
