#!/usr/bin/env python3
"""Real UPO smoke radar-vs-LiDAR *geometric co-observation* audit, never recall.

Uses recorded TF, matches independent simultaneous radar echoes to nearby
decimated LiDAR XY sample locations. The LiDAR is not reliable ground truth
through smoke; no obstacle labels. No pose truth enters the calculation.
"""
import argparse
from bisect import bisect_left
import json
import math
from pathlib import Path

from calibration_audit import tf_lookup
from upo_real import cloud_xyz, ros_stamp, quantile, sha256_file

TOPICS={
    "left":"/ti_mmwave_left_radar/radar_scan_pcl",
    "right":"/ti_mmwave_right_radar/radar_scan_pcl",
    "lidar":"/os1_cloud_node/points_non_dense",
}
RESOLUTION=.20


def to_base(pose,point):
    x,y,z=point
    a=pose["yaw_rad"]
    ox,oy,oz=pose["xyz_m"]
    return (ox+x*math.cos(a)-y*math.sin(a),
            oy+x*math.sin(a)+y*math.cos(a),
            oz+z)


def cell_index(p):
    return (math.floor(p[0]/RESOLUTION),math.floor(p[1]/RESOLUTION))


def min_xy_proximity(point,grid,max_m=1.2):
    ix,iy=cell_index(point)
    radius=math.ceil(max_m/RESOLUTION)
    best=float("inf")
    for dx in range(-radius,radius+1):
        for dy in range(-radius,radius+1):
            if (ix+dx,iy+dy) not in grid:continue
            for qx,qy in grid[(ix+dx,iy+dy)]:
                d=math.hypot(qx-point[0],qy-point[1])
                if d<best:best=d
    return best if best<=max_m else None


def summarize_matches(rows):
    if not rows:return {"status":"NO_MATCHED_RADAR_AND_LIDAR_ECHOES"}
    near=[r for r in rows if r["nearby_lidar_xy_m"] is not None]
    return {
        "evaluated_radar_returns":len(rows),
        "nearby_lidar_within_0_5m":sum(r["nearby_lidar_xy_m"] is not None and r["nearby_lidar_xy_m"]<=.5 for r in rows),
        "fraction_lidar_xy_within_0_5m":round(sum(r["nearby_lidar_xy_m"] is not None and r["nearby_lidar_xy_m"]<=.5 for r in rows)/len(rows),6),
        "xy_distance_median_m_conditional_on_within_1_2m":quantile([r["nearby_lidar_xy_m"] for r in near],.5),
        "xy_distance_p95_m_conditional_on_within_1_2m":quantile([r["nearby_lidar_xy_m"] for r in near],.95),
        "lidar_timestamp_gap_ms_p95":quantile([r["time_gap_ms"] for r in rows],.95),
        "semantics":"radar echo to sampled LiDAR XY proximity, NOT independent obstacle labels or detection recall",
    }


def compare_original(bag,tf_path,limit_s=168.):
    from rosbags.highlevel import AnyReader
    cfg=json.loads(tf_path.read_text(encoding="utf-8"))
    if cfg.get("ground_truth_accessed") is not False:
        raise ValueError("TF audit must be reference-blind")
    tree=cfg["tf_tree"]
    frames={
        "left":tf_lookup(tree,"ti_mmwave_left_radar"),
        "right":tf_lookup(tree,"ti_mmwave_right_radar"),
        "lidar":tf_lookup(tree,"os1_lidar"),
    }
    if any(v is None for v in frames.values()):
        raise ValueError("Insufficient TF calibration; no guessing sensor frame alignments")
    cloud_rows={"left":[],"right":[],"lidar":[]}
    samples={"left":0,"right":0,"lidar":0}
    with AnyReader([bag]) as reader:
        conns={c.topic:c for c in reader.connections}
        if any(t not in conns for t in TOPICS.values()):
            raise ValueError("Missing full original radar/LiDAR topic")
        start=reader.start_time
        for c,bag_time,raw in reader.messages(connections=[conns[t] for t in TOPICS.values()]):
            if (bag_time-start)*1e-9>limit_s:break
            name=next(k for k,t in TOPICS.items() if t==c.topic)
            msg=reader.deserialize(raw,c.msgtype)
            t=ros_stamp(msg)
            frame=msg.header.frame_id
            if frame != {"left":"ti_mmwave_left_radar","right":"ti_mmwave_right_radar","lidar":"os1_lidar"}[name]:
                raise ValueError("Unexpected source coordinate frame")
            points,advertised,stride=cloud_xyz(msg,max_points=1200 if name=="lidar" else 3000)
            pts=[to_base(frames[name],p) for p in points]
            # Planar proximity only, z ignored due sparse elevation radar.
            pts=[p for p in pts if .25 <= math.hypot(p[0],p[1]) <= 15]
            cloud_rows[name].append((t,pts))
            samples[name]+=1
    reference=sorted(cloud_rows["lidar"])
    if len(reference)<20:
        raise ValueError("LiDAR source unexpectedly empty")
    rt=[x[0] for x in reference]
    result={}
    for which in ("left","right"):
        rows=[];missed=0
        # Pre-index the LiDAR cloud lazily for each matching timestamp.
        cached={}
        for t,points in cloud_rows[which]:
            idx=bisect_left(rt,t)
            cand=[i for i in (idx-1,idx) if 0<=i<len(rt)]
            if not cand:continue
            n=min(cand,key=lambda i:abs(rt[i]-t))
            lag=abs(rt[n]-t)
            if lag>.08:
                missed+=len(points)
                continue
            if n not in cached:
                grid={}
                for x,y,z in reference[n][1]:
                    cell=cell_index((x,y))
                    grid.setdefault(cell,[]).append((x,y))
                cached[n]=grid
            grid=cached[n]
            for point in points:
                nearest=min_xy_proximity(point,grid,max_m=1.2)
                rows.append({"nearby_lidar_xy_m":nearest,
                             "range_m":math.hypot(point[0],point[1]),
                             "time_gap_ms":lag*1000})
        result[which]=summarize_matches(rows)
        result[which]["radar_echoes_without_close_time_lidar"]=missed
        result[which]["range_stratified_0_5m_fraction"]={}
        for lo,hi in ((.25,1),(1,2),(2,4),(4,8),(8,15)):
            sub=[v for v in rows if lo<=v["range_m"]<hi]
            result[which]["range_stratified_0_5m_fraction"][f"{lo}-{hi}m"]=(
                round(sum(v["nearby_lidar_xy_m"] is not None and v["nearby_lidar_xy_m"]<=.5 for v in sub)/len(sub),5) if sub else None)
    return {
        "type":"CO_OBSERVATION_GEOMETRY_NOT_GROUND_TRUTH",
        "original_sha256":sha256_file(bag),
        "sensor_frame_from_tf":frames,
        "source_frames_decoded":samples,
        "sampling":"LiDAR PointCloud2 deterministic ≤1200 xyz / frame; radar up to 3000",
        "lidar_in_smoke_is_not_a_ground_truth_surface":True,
        "reference_pose_used":False,
        "relative_sensor_observability":result,
        "limitations":[
            "Sparse and differently sampled point clouds, independently degraded by smoke",
            "2D nearest sampled LiDAR XY (no depth/height/visibility line-of-sight match)",
            "Within 0.5m is a PROXIMITY score, not object recall or free-space proof",
            "Purely static TF used; dynamic scanner calibration and timing have residual error"
        ],
        "safety":"RESEARCH_ONLY_NOT_FIRE_SERVICE",
    }


def main():
    p=argparse.ArgumentParser()
    p.add_argument("--bag",type=Path,required=True)
    p.add_argument("--tf-clock",type=Path,required=True)
    p.add_argument("--out",type=Path,required=True)
    p.add_argument("--max-seconds",type=float,default=168.)
    a=p.parse_args()
    if not 10<=a.max_seconds<=168:raise ValueError("Select 10..168 seconds")
    data=compare_original(a.bag,a.tf_clock,a.max_seconds)
    a.out.parent.mkdir(parents=True,exist_ok=True)
    a.out.write_text(json.dumps(data,indent=2,allow_nan=False,sort_keys=True)+"\n",encoding="utf-8")
    print(json.dumps({"status":"CO_OBSERVATION_ONLY","metrics":data["relative_sensor_observability"]},sort_keys=True))
    return 0


if __name__=="__main__":
    main()
