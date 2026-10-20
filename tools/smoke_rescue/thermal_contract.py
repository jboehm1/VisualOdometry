#!/usr/bin/env python3
"""Vendor-neutral apparent LWIR thermal grid ingest: RESEARCH ONLY.

Accepts MLX90640 32x24, FLIR Lepton 160x120, or other calibrated-size
radiometric frames encoded as JSONL (timestamp + apparent temperature grid).
Outputs angular heat *cues* without person/victim detection, depth, traversable
space or metric radar fusion. Never assign a range to thermal pixels.
"""
import argparse
import json
import math
from pathlib import Path
import statistics


def finite(v,name):
    if isinstance(v,bool) or not isinstance(v,(float,int)) or not math.isfinite(v):
        raise ValueError(f"{name}: finite numeric value required")
    return float(v)


def parse_frame(row):
    if not isinstance(row,dict):
        raise ValueError("thermal frame must be JSON object")
    t=finite(row["timestamp_s"],"timestamp_s")
    w,h=int(row["width"]),int(row["height"])
    if not (8<=w<=640 and 8<=h<=512 and w*h<=327680):
        raise ValueError("invalid thermal pixel dimensions")
    hfov=finite(row["hfov_deg"],"hfov_deg")
    if not (15<hfov<180):
        raise ValueError("invalid horizontal FOV")
    source=row["source"]
    if not isinstance(source,str) or not 2<=len(source)<=80:
        raise ValueError("invalid source provenance")
    frame=row["sensor_frame"]
    if not isinstance(frame,str) or not frame or len(frame)>80:
        raise ValueError("missing sensor frame")
    clock=row["clock"]
    if clock not in ("unix_host","device_uptime_uncalibrated"):
        raise ValueError("unrecognized clock base")
    temperatures=row["apparent_temperature_c"]
    if not isinstance(temperatures,list) or len(temperatures)!=w*h:
        raise ValueError("incomplete thermal radiometric grid")
    # Hardware can report NaN on invalid pixels; encode them as null, never 0°C.
    validated=[]
    for v in temperatures:
        if v is None:
            validated.append(None)
        else:
            c=finite(v,"temperature pixel")
            if c < -273.15 or c>2000:
                raise ValueError("physically impossible thermal pixel")
            validated.append(c)
    return {"t":t,"w":w,"h":h,"hfov_rad":math.radians(hfov),
            "source":source,"frame":frame,"clock":clock,"temps":validated}


def heat_components(frame,threshold_c=60.,min_pixels=3):
    """4-neighbor connected hot-apparent-pixel regions, ANGLES ONLY.

    A hot region can be a wall, flame, reflection or person; classification is
    deliberately absent. Thermal auto-gain, emissivity and steam affect values.
    """
    threshold_c=finite(threshold_c,"heat threshold")
    w,h=frame["w"],frame["h"]
    temps=frame["temps"]
    good=[v is not None and v>=threshold_c for v in temps]
    visited=set()
    blobs=[]
    for idx,hot in enumerate(good):
        if not hot or idx in visited:
            continue
        stack=[idx];visited.add(idx);nodes=[]
        while stack:
            k=stack.pop();nodes.append(k)
            x,y=k%w,k//w
            for nx,ny in ((x-1,y),(x+1,y),(x,y-1),(x,y+1)):
                if not (0<=nx<w and 0<=ny<h):continue
                nxt=ny*w+nx
                if good[nxt] and nxt not in visited:
                    visited.add(nxt);stack.append(nxt)
        if len(nodes)<min_pixels:
            continue
        cols=[k%w for k in nodes];rows=[k//w for k in nodes]
        center_x=(min(cols)+max(cols)+1)/2.
        angle=(center_x/w-.5)*frame["hfov_rad"]
        blobs.append({
            "timestamp_s":frame["t"],
            "pixel_bbox_xyxy":[min(cols),min(rows),max(cols),max(rows)],
            "pixel_count":len(nodes),
            "azimuth_relative_sensor_rad":angle,
            "apparent_temperature_c_max":max(temps[k] for k in nodes),
            "distance_m":None,
            "object_class":"UNCLASSIFIED_HEAT_REGION",
            "safety":"No person/obstacle inference"
        })
    return blobs


def run_frames(lines,threshold_c=60):
    frames=[]
    last=float("-inf")
    for n,line in enumerate(lines,1):
        if not line.strip():continue
        try:
            frame=parse_frame(json.loads(line))
            if frame["t"]<=last:raise ValueError("Thermal timestamps not increasing")
            frames.append(frame);last=frame["t"]
        except (ValueError,KeyError,TypeError,json.JSONDecodeError) as exc:
            raise ValueError(f"Invalid thermal JSONL line {n}: {exc}") from exc
    if not frames:raise ValueError("No actual/synthetic declared thermal frames")
    hot=[]
    valid=[]
    for frame in frames:
        hot.extend(heat_components(frame,threshold_c))
        valid.append(sum(v is not None for v in frame["temps"])/len(frame["temps"]))
    modes=sorted({f["source"] for f in frames})
    clocks=sorted({f["clock"] for f in frames})
    return {
        "recording_kind":"USER_DECLARED_THERMAL_SAMPLES_NOT_INDEPENDENTLY_VERIFIED",
        "input_frames":len(frames),"source_strings":modes,"clock_bases":clocks,
        "min_valid_pixel_fraction":round(min(valid),6),
        "mean_valid_pixel_fraction":round(statistics.mean(valid),6),
        "heat_cue_regions":hot,
        "heat_regions_total":len(hot),
        "range_available":False,"calibrated_to_radar":False,
        "human_detection_implemented":False,
        "safety":"NOT_FIREFIGHTER_DECISION_SUPPORT",
        "limitations":[
            "Apparent surface temperature contrast, not air or tissue temperature.",
            "Thermal rays have no measured depth or target identity.",
            "Source metadata is user declared; no hardware, certification or smoke performance verified.",
            "Thermal saturation, high ambient, steam and smoke can hide surfaces."
        ]
    }


def main(argv=None):
    p=argparse.ArgumentParser()
    p.add_argument("--thermal-jsonl",type=Path,required=True)
    p.add_argument("--out",type=Path,required=True)
    p.add_argument("--heat-threshold-c",type=float,default=60.)
    a=p.parse_args(argv)
    report=run_frames(a.thermal_jsonl.read_text(encoding="utf-8").splitlines(),a.heat_threshold_c)
    a.out.parent.mkdir(parents=True,exist_ok=True)
    a.out.write_text(json.dumps(report,indent=2,sort_keys=True,allow_nan=False)+"\n",encoding="utf-8")
    print(json.dumps({"status":"RESEARCH","frames":report["input_frames"],
                      "heat_cues":report["heat_regions_total"],
                      "depth_available":False}))
    return 0


if __name__=="__main__":
    raise SystemExit(main())
