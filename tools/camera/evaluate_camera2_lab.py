#!/usr/bin/env python3
"""Audit *real exported* Camera2 lab telemetry; do not pretend it scores VIO.

The Camera2 lab has no ARCore pose, IMU/camera hardware time-offset calibration
or independent trajectory ground truth, so this report only evaluates measured
shutter / ISO constraints, image brightness and gyroscope smear.
"""
from __future__ import annotations
import argparse,csv,json,math,statistics
from pathlib import Path

REQUIRED={"time_elapsed_ns","profile","auto","p10","p50","p90",
          "gyro_rad_s","actual_exposure_ns","actual_iso","shutter_cap_ns",
          "selected_exposure_ns","selected_iso","iso_ceiling",
          "clipped_fraction","coherent_edge_fraction","feature_median"}

def summarize(path):
    series=[]
    with Path(path).open(newline="",encoding="utf-8") as stream:
        reader=csv.DictReader(stream)
        if not reader.fieldnames or not REQUIRED.issubset(reader.fieldnames):
            raise ValueError("Expected VECTOR VIO Camera2 quality trace, missing columns")
        last=-1
        for row in reader:
            t=int(row["time_elapsed_ns"])
            p10,p50,p90=[int(row[k]) for k in ("p10","p50","p90")]
            exp=int(row["actual_exposure_ns"]);iso=int(row["actual_iso"])
            chosen_exp=int(row["selected_exposure_ns"])
            chosen_iso=int(row["selected_iso"])
            iso_max=int(row["iso_ceiling"])
            cap=int(row["shutter_cap_ns"])
            smooth=float(row["coherent_edge_fraction"])
            clipped=float(row["clipped_fraction"])
            speed=float(row["gyro_rad_s"]) if row["gyro_rad_s"] else None
            if (t<=last or not 0<=p10<=p50<=p90<=255 or
                exp<=0 or iso<=0 or chosen_exp<=0 or chosen_iso<=0 or
                iso_max<=0 or not 0<=smooth<=1 or not 0<=clipped<=1 or
                speed is not None and (speed<0 or not math.isfinite(speed))):
                raise ValueError("Invalid timestamp or image/exposure field")
            last=t
            series.append(dict(t=t,profile=row["profile"],auto=row["auto"]=="true",
                p50=p50,exp=exp,iso=iso,cap=cap,requested=chosen_exp,
                requested_iso=chosen_iso,iso_max=iso_max,smooth=smooth,
                clipped=clipped,speed=speed))
    if len(series)<3:raise ValueError("Need >=3 camera observations")
    active=[s for s in series if s["auto"] and s["cap"]>0]
    by_mode={}
    for mode in ("WALK","BIKE"):
        sample=[s for s in active if s["profile"]==mode]
        if not sample:continue
        measured=[s["exp"]/1e6 for s in sample]
        gyro=[math.degrees(s["speed"]*s["exp"]*1e-9)
              for s in sample if s["speed"] is not None]
        by_mode[mode]={
            "n_auto_samples":len(sample),
            "median_actual_shutter_ms":round(statistics.median(measured),4),
            "p90_actual_shutter_ms":round(sorted(measured)[math.ceil(.9*len(sample))-1],4),
            "median_actual_iso":round(statistics.median(s["iso"] for s in sample)),
            "median_image_luminance_0_255":round(statistics.median(s["p50"] for s in sample)),
            "median_coherent_gradient_fraction":round(statistics.median(s["smooth"] for s in sample),4),
            "median_angular_smear_deg":round(statistics.median(gyro),4) if gyro else None,
            "fraction_actual_shutter_above_gyro_cap":round(
                sum(s["exp"]>s["cap"]*1.15 for s in sample)/len(sample),4),
            "fraction_actual_iso_above_user_cap":round(
                sum(s["iso"]>s["iso_max"]*1.02 for s in sample)/len(sample),4),
            "fraction_very_dark_p50_below_48":round(
                sum(s["p50"]<48 for s in sample)/len(sample),4)
        }
    return {
        "n_samples":len(series),
        "n_auto_samples":len(active),
        "by_mode":by_mode,
        "photometric_limit_lux":None,
        "visual_odometry_accuracy_verified":False,
        "note":"Camera2 lab only, no ARCore VIO during shutter control; do not infer metric navigation accuracy or universal ISO cap. Actual capture results lag requests by camera pipeline frames."
    }

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--csv",type=Path,required=True)
    parser.add_argument("--out",type=Path,required=True)
    args=parser.parse_args()
    result=summarize(args.csv)
    args.out.parent.mkdir(parents=True,exist_ok=True)
    args.out.write_text(json.dumps(result,indent=2,sort_keys=True)+"\n",encoding="utf-8")
    print(json.dumps(result,sort_keys=True))
if __name__=="__main__":main()
