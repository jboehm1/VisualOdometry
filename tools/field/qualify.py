#!/usr/bin/env python3
"""Fail-closed, offline Android VECTOR CSV route qualification with surveyed lengths.

This evaluates previously recorded on-device estimates. It CANNOT execute an
Android APK, measure energy, calibrate ARCore, or establish independent 6DoF truth.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import statistics
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from plot_session import load_samples


SCENARIOS={"straight","loop","stationary","rotation"}
MIN_REPEATS=3


def _finite(value, name: str, minimum=None, maximum=None):
    if isinstance(value,bool) or not isinstance(value,(int,float)) or not math.isfinite(value):
        raise ValueError(f"{name} must be finite numeric")
    if (minimum is not None and value<minimum) or (
        maximum is not None and value>maximum
    ):
        raise ValueError(f"{name} out of range")
    return float(value)


def validate_manifest(manifest: Path) -> dict:
    obj=json.loads(manifest.read_text(encoding="utf-8"))
    if not isinstance(obj,dict) or set(obj)-{"schema_version","trials","notes"} or obj.get("schema_version")!=1:
        raise ValueError("Unsupported field validation manifest")
    jobs=obj.get("trials")
    if not isinstance(jobs,list) or not jobs or len(jobs)>80:
        raise ValueError("Expected 1..80 field trials")
    found=set()
    for row in jobs:
        if not isinstance(row,dict) or set(row)-{
            "id","file","scenario","known_path_m",
            "battery_start_pct","battery_end_pct","temperature_start_c","temperature_end_c",
            "device","environment"
        } or not {"id","file","scenario","known_path_m"} <= row.keys():
            raise ValueError("Malformed trial schema")
        ident=row["id"]
        if not isinstance(ident,str) or not ident or len(ident)>48 or not all(c.isalnum() or c in "-_" for c in ident):
            raise ValueError("Trial ID must be short printable alphanumeric")
        if ident in found:raise ValueError("Duplicate trial ID")
        found.add(ident)
        if row["scenario"] not in SCENARIOS:raise ValueError("Unknown scenario")
        path=Path(row["file"])
        if path.is_absolute() or ".." in path.parts or path.suffix.lower()!=".csv":
            raise ValueError("Trial input must be a relative .csv path under manifest directory")
        source=(manifest.parent/path).resolve()
        if not source.is_relative_to(manifest.parent.resolve()) or not source.is_file():
            raise ValueError("Missing or escaped field input CSV")
        length=_finite(row["known_path_m"],"known_path_m",minimum=0,maximum=100000)
        if row["scenario"] in ("stationary","rotation") and length!=0:
            raise ValueError("Stationary and rotation trials must have 0m independently surveyed path")
        if row["scenario"] in ("straight","loop") and length<=0:
            raise ValueError("Moving trials require independent positive surveyed path")
        for prefix,limits in [
            ("battery_start_pct",(0,100)),("battery_end_pct",(0,100)),
            ("temperature_start_c",(-30,95)),("temperature_end_c",(-30,95))
        ]:
            if prefix in row:_finite(row[prefix],prefix,minimum=limits[0],maximum=limits[1])
        if ("battery_start_pct" in row)!=("battery_end_pct" in row):
            raise ValueError("Battery measurement requires both start/end readings")
        if ("temperature_start_c" in row)!=("temperature_end_c" in row):
            raise ValueError("Thermal measurement requires both start/end readings")
    return obj


def one_trial(row: dict, root: Path) -> dict:
    path=root/row["file"]
    samples,events=load_samples(path)
    if len(samples)<5:raise ValueError(f"{row['id']}: fewer than five recorded POSE rows")
    fields=("wall_time_ms","frame_time_ns","path_m","segments","gaps","outliers")
    for index,s in enumerate(samples):
        if not all(math.isfinite(s[k]) for k in fields):
            raise ValueError(f"{row['id']}: invalid numeric sensor/pose field {index}")
        if index and (s["frame_time_ns"]<=samples[index-1]["frame_time_ns"]
                      or s["wall_time_ms"]<samples[index-1]["wall_time_ms"]):
            raise ValueError(f"{row['id']}: backward or duplicated frame time")
        if index and s["path_m"]+1e-5<samples[index-1]["path_m"]:
            raise ValueError(f"{row['id']}: impossible decreasing path meter counter")
        if s["path_m"]<0 or s["gaps"]<0 or s["segments"]<0 or s["outliers"]<0:
            raise ValueError(f"{row['id']}: negative quality/path count")
    seconds=(samples[-1]["frame_time_ns"]-samples[0]["frame_time_ns"])*1e-9
    if not 0.1<=seconds<=43200:raise ValueError(f"{row['id']}: unrealistic measured duration")
    path_m=samples[-1]["path_m"]-samples[0]["path_m"]
    gt=float(row["known_path_m"])
    frame_deltas=[(b["frame_time_ns"]-a["frame_time_ns"])*1e-9 for a,b in zip(samples,samples[1:])]
    quality={
        "samples":len(samples),
        "source_csv_sha256":hashlib.sha256(path.read_bytes()).hexdigest(),
        "duration_s":seconds,
        "observed_pose_rate_hz":(len(samples)-1)/seconds,
        "max_interpose_gap_s":max(frame_deltas),
        "interpose_gaps_over_500ms":sum(t>.5 for t in frame_deltas),
        "recording_events":events,
        "pose_segments":int(samples[-1]["segments"]-samples[0]["segments"]),
        "tracking_gaps":int(samples[-1]["gaps"]-samples[0]["gaps"]),
        "rejected_outliers":int(samples[-1]["outliers"]-samples[0]["outliers"]),
        "independent_surveyed_path_m":gt,
        "app_estimated_distance_m":path_m,
        "signed_path_error_m":path_m-gt,
        "absolute_path_error_m":abs(path_m-gt),
        "app_estimates_are_ground_truth":False,
        "ground_truth_is_only_surveyed_path_length":True,
    }
    if row["scenario"] in ("stationary","rotation"):
        quality["false_path_m_per_min"]=path_m*60/seconds
        quality["relative_path_error_pct"]=None
    else:
        quality["relative_path_error_pct"]=100*abs(path_m-gt)/gt
        quality["false_path_m_per_min"]=None
    if "battery_start_pct" in row:
        quality["battery_drop_percentage_points_reported"]=(
            float(row["battery_start_pct"])-float(row["battery_end_pct"]))
        quality["battery_percent_readings_are_power_watts"]=False
    if "temperature_start_c" in row:
        quality["temperature_delta_c_reported"]=(
            float(row["temperature_end_c"])-float(row["temperature_start_c"]))
    return {"id":row["id"],"scenario":row["scenario"],"device":row.get("device"),
            "environment":row.get("environment"),"metrics":quality}


def _percentile(values: list[float],q: float):
    arr=sorted(values);p=q*(len(arr)-1);lo=math.floor(p);hi=math.ceil(p)
    return arr[lo]+(arr[hi]-arr[lo])*(p-lo)


def qualify(manifest: Path, out: Path) -> dict:
    manifest=manifest.resolve(strict=True)
    jobs=validate_manifest(manifest)["trials"]
    trials=[one_trial(row,manifest.parent) for row in jobs]
    straight=[t["metrics"]["relative_path_error_pct"] for t in trials if t["scenario"]=="straight"]
    stopped=[t["metrics"]["false_path_m_per_min"] for t in trials if t["scenario"]=="stationary"]
    gates={}
    for label,values,limit,statistic in [
        ("straight_median_error_pct",straight,5,"median"),
        ("straight_p90_error_pct",straight,10,"p90"),
        ("stationary_median_false_m_per_min",stopped,.5,"median")
    ]:
        if len(values)<MIN_REPEATS:
            gates[label]={"state":"INSUFFICIENT_EVIDENCE","n":len(values),
                          "required_trials":MIN_REPEATS,"target_less_than":limit}
        else:
            measured=(statistics.median(values) if statistic=="median" else _percentile(values,.9))
            gates[label]={"state":"PASS" if measured<limit else "FAILED",
                          "n":len(values),"measured":measured,"target_less_than":limit}
    state=("INSUFFICIENT_EVIDENCE" if any(g["state"]=="INSUFFICIENT_EVIDENCE" for g in gates.values())
           else "FAILED" if any(g["state"]=="FAILED" for g in gates.values()) else "PASS")
    result={"schema_version":1,"status":state,"trials":trials,"gates":gates,
            "metric_6dof_ground_truth_available":False,
            "physical_android_app_executed_by_this_script":False,
            "battery_percentage_is_not_joules":True,
            "reference_kind":"user_surveyed_route_length_only",
            "minimum_repeats_per_gate":MIN_REPEATS,
            "privacy_note":"Reports omit GPS coordinates and original pose traces; input CSV stays local"}
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(result,indent=2,sort_keys=True,allow_nan=False)+"\n",
                   encoding="utf-8")
    return result


if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--manifest",type=Path,required=True)
    p.add_argument("--out",type=Path,required=True)
    args=p.parse_args()
    result=qualify(args.manifest,args.out)
    print(json.dumps({"status":result["status"],"gates":result["gates"],
                      "trials":len(result["trials"])},indent=2))
    if result["status"]=="FAILED":raise SystemExit(1)
    if result["status"]=="INSUFFICIENT_EVIDENCE":raise SystemExit(2)
