#!/usr/bin/env python3
"""OxIOD synchronized CSV importer for camera-OFF PDR and truth-proxy scoring.

A real OxIOD sequence may include Vicon or Tango-derived 'vi*.csv' poses.
The importer never passes truth poses to the estimator. Do NOT equate Tango
reference with optical ground truth. Verify exact upstream column conventions.
"""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path


def load_rows(path):
    with Path(path).open(newline="",encoding="utf-8-sig") as f:
        for row in csv.reader(f):
            if not row or row[0].startswith("#"):continue
            try:
                vals=[float(x) for x in row]
            except ValueError:
                # OxIOD CSV often includes textual column headers.
                if len(row)>2 and any(ch.isalpha() for ch in row[0]):
                    continue
                raise ValueError(f"Unexpected nonnumeric row in {path}: {row[:4]}")
            yield vals


def convert(imu_path, out, reference=None, truth_quality=None):
    out=Path(out);out.mkdir(parents=True,exist_ok=True)
    imu_path=Path(imu_path)
    if truth_quality not in (None,"optical","visual_inertial_proxy"):
        raise ValueError("truth_quality must explicitly be optical or visual_inertial_proxy")
    if reference and truth_quality is None:
        raise ValueError("Reference origin required: --truth-quality optical or visual_inertial_proxy")
    gyro_count=0
    prev=None
    dst_imu=out/"imu.csv"
    with dst_imu.open("w",newline="",encoding="utf-8") as f:
        writer=csv.writer(f);writer.writerow(["t_ns","type","x","y","z"])
        for r in load_rows(imu_path):
            if len(r)<13:raise ValueError("OxIOD sensor CSV requires at least 13 columns")
            # Upstream structure indices: gyro 4:7, gravity 7:10,
            # user accel 10:13. Gravity and user acceleration in units g.
            ts=int(round(r[0]*1e9))
            if ts<=0 or (prev is not None and ts<=prev):
                raise ValueError("IMU timestamp not increasing; check units")
            prev=ts
            wx,wy,wz=r[4:7]
            gravity=r[7:10]
            user_accel=r[10:13]
            full_accel=[(g+a)*9.80665 for g,a in zip(gravity,user_accel)]
            if not all(math.isfinite(x) for x in (wx,wy,wz,*full_accel)):
                raise ValueError("Non-finite OxIOD IMU sample")
            writer.writerow([ts,"GYRO",wx,wy,wz])
            writer.writerow([ts,"ACCEL",*full_accel])
            gyro_count+=1
    if gyro_count<3:raise ValueError("Need at least 3 sensor frames")
    truth=None
    if reference:
        reference=Path(reference)
        target=out/"groundtruth.tum"
        samples=0;prev=None
        with target.open("w",encoding="utf-8") as f:
            for r in load_rows(reference):
                if len(r)<9:raise ValueError("OxIOD reference CSV needs at least 9 columns")
                t=r[0]
                if prev is not None and t<=prev:raise ValueError("Nonmonotonic reference time")
                prev=t
                position=r[2:5]
                quaternion=r[5:9]
                # Reference likely Tango-based; no source frame extrinsics applied.
                f.write(" ".join(f"{v:.9f}" for v in [t,*position,*quaternion])+"\n")
                samples+=1
        if samples<3:raise ValueError("Need at least 3 reference poses")
        truth=str(target.resolve())
    checksum=hashlib.sha256(imu_path.read_bytes()).hexdigest()
    result={
        "schema":1,"dataset_kind":"oxiod_syn",
        "source_imu":str(imu_path.resolve()),"imu_sha256":checksum,
        "frames":None,"frame_count":0,"imu":str(dst_imu.resolve()),
        "imu_count":2*gyro_count,"pose_frame":"device",
        "truth":truth,"truth_format":"tum" if truth else None,
        "truth_frame":"oxiod_reference_device_or_platform_unverified" if truth else None,
        "truth_quality":truth_quality if truth else "none",
        "units":"gyroscope rad/s, reconstructed full acceleration (gravity + userAcc) * 9.80665 m/s2",
        "warnings":[
            "verify native OxIOD time units and axis conventions against metadata",
            "RIDI/phone PDR and OxIOD may have arbitrary device orientation",
            "reference and estimated PDR frame extrinsics are not calibrated here",
            "do not evaluate as independent optical truth without source provenance",
            "fixed-stride step baseline remains a weak algorithm"
        ]
    }
    (out/"manifest.json").write_text(json.dumps(result,indent=2)+"\n")
    return result


def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--imu",type=Path,required=True)
    ap.add_argument("--reference",type=Path)
    ap.add_argument("--truth-quality",choices=["optical","visual_inertial_proxy"])
    ap.add_argument("--out",type=Path,required=True)
    a=ap.parse_args()
    print(json.dumps(convert(a.imu,a.out,a.reference,a.truth_quality),indent=2))


if __name__=="__main__":main()
