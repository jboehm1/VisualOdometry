#!/usr/bin/env python3
"""UPO smoke original TF + sensor clock audit. Research only, no GT fitting.

Derives static parent->child transforms from original /tf and /tf_static,
and robust host↔IMU uptime clock relationship from IMU header and recorder
timestamps only. Never consults ground truth, LiDAR map or robot odometry.
"""
import argparse
from collections import defaultdict
import json
import math
from pathlib import Path
import statistics


def qnorm(q):
    n=math.sqrt(sum(v*v for v in q))
    if n<1e-6 or not math.isfinite(n):
        raise ValueError("invalid quaternion")
    return tuple(v/n for v in q)


def rotation_yaw(q):
    x,y,z,w=qnorm(q)
    return math.atan2(2*(w*z+x*y),1-2*(y*y+z*z))


def clock_fit(pairs, window_s=5.0):
    """Clock model from independent (imu_uptime_header, bag_arrival_epoch).

    Offset per window is robust lower envelope to reduce variable recorder
    delays. Slope estimated from min-offset windows, bounded to oscillator
    drift of ±1000 ppm. Uncharacterized fixed pipeline latency remains.
    """
    if len(pairs)<200:
        raise ValueError("Insufficient real IMU clock pairs")
    sorted_pairs=sorted((float(device),float(receipt)) for device,receipt in pairs)
    if any(not math.isfinite(t) or not math.isfinite(r) for t,r in sorted_pairs):
        raise ValueError("Nonfinite times")
    if sorted_pairs[-1][0]-sorted_pairs[0][0]<2.:
        raise ValueError("Too short for clock correction")
    buckets=defaultdict(list)
    first=sorted_pairs[0][0]
    for device,receipt in sorted_pairs:
        buckets[int((device-first)/window_s)].append((device,receipt))
    anchors=[]
    for bucket in sorted(buckets):
        entries=buckets[bucket]
        if len(entries)<3:continue
        entries.sort(key=lambda pair: pair[1]-pair[0])
        n=max(1,int(len(entries)*.10))
        good=entries[:n]
        anchors.append((statistics.median(x for x,y in good),
                        statistics.median(y for x,y in good)))
    if len(anchors)<2:
        raise ValueError("Insufficient clock anchor windows")
    # Robust median slopes spanning separated pairs (>=half period)
    slopes=[]
    for i,(x0,y0) in enumerate(anchors):
        for x1,y1 in anchors[i+1:]:
            if x1-x0>=max(3.,(anchors[-1][0]-anchors[0][0])/2):
                slopes.append((y1-y0)/(x1-x0))
    slope=statistics.median(slopes)
    if not (0.999<=slope<=1.001):
        raise ValueError("Sensor clock drift outside 1000ppm; clock calibration rejected")
    intercept=statistics.median(y-slope*x for x,y in anchors)
    jitter=[(receipt-(intercept+slope*device))*1000 for device,receipt in sorted_pairs]
    median_delay=statistics.median(jitter)
    arr=sorted(jitter)
    return {
        "slope":slope,"intercept_s":intercept,
        "reported_clock_drift_ppm":round((slope-1)*1e6,3),
        "median_arrival_lag_ms":round(median_delay,3),
        "p95_minus_p05_arrival_delay_ms":round(arr[int(.95*(len(arr)-1))]-arr[int(.05*(len(arr)-1))],3),
        "pair_count":len(pairs),"anchor_windows":len(anchors),
        "reference_pose_used":False,
        "limitations":"Unknown fixed path delay; clock maps header uptime to ROS bag receipt with unverified offset",
    }


def tf_lookup(transforms, child, root="base_link"):
    """Resolve static transforms root->child. Return yaw/xyz; detect missing path.

    Edge stores transform from child frame to parent. Does not infer unrecorded
    extrinsics or silently assume frame aliases.
    """
    current=child
    visited=set()
    x=y=z=yaw=0.
    while current!=root:
        if current in visited or current not in transforms:
            return None
        visited.add(current)
        edge=transforms[current]
        theta=rotation_yaw(edge["q"])
        dx,dy,dz=edge["xyz"]
        # Compose parent <- child, in 2D:
        x,y=dx+math.cos(theta)*x-math.sin(theta)*y,dy+math.sin(theta)*x+math.cos(theta)*y
        z+=dz
        yaw+=theta
        current=edge["parent"]
        if len(visited)>100:raise ValueError("TF cycle")
    return {"root":root,"frame":child,"xyz_m":[x,y,z],"yaw_rad":yaw,
            "planar_only":True,"roll_pitch_ignored":True}


def inspect_original(bag:Path):
    from rosbags.highlevel import AnyReader
    if bag.name!="experiment_7.bag" or not bag.exists():raise ValueError("Original UPO bag required")
    frames={"radar_left":"ti_mmwave_left_radar","radar_right":"ti_mmwave_right_radar","imu":"os1_imu"}
    first_edge={}
    conflicting=[]
    pairs=[]
    tf_stats=defaultdict(int)
    with AnyReader([bag]) as reader:
        chosen=[c for c in reader.connections if c.topic in ("/tf","/tf_static","/os1_cloud_node/imu")]
        if len(chosen)<2:raise ValueError("Missing required TF/IMU topics")
        for conn,bag_time,raw in reader.messages(connections=chosen):
            msg=reader.deserialize(raw,conn.msgtype)
            if conn.topic=="/os1_cloud_node/imu":
                header=msg.header.stamp
                uptime=float(header.sec)+float(header.nanosec)*1e-9
                if math.isfinite(uptime):
                    pairs.append((uptime,bag_time/1e9))
            else:
                for t in msg.transforms:
                    child=str(t.child_frame_id)
                    edge={"parent":str(t.header.frame_id),
                          "xyz":[float(t.transform.translation.x),float(t.transform.translation.y),float(t.transform.translation.z)],
                          "q":[float(t.transform.rotation.x),float(t.transform.rotation.y),float(t.transform.rotation.z),float(t.transform.rotation.w)]}
                    if child not in first_edge:first_edge[child]=edge
                    else:
                        prior=first_edge[child]
                        delta=max(abs(a-b) for a,b in zip(prior["xyz"],edge["xyz"]))
                        if prior["parent"]!=edge["parent"] or delta>0.005:
                            if child not in conflicting:conflicting.append(child)
                    tf_stats[conn.topic]+=1
    found={name:tf_lookup(first_edge,frame) for name,frame in frames.items()}
    missing=[k for k,v in found.items() if v is None]
    clock=clock_fit(pairs)
    return {"source":"ORIGINAL_UPO_BAG", "tf_tree":first_edge,
            "all_children":sorted(first_edge),
            "tf_message_edges_by_topic":dict(tf_stats),
            "named_sensor_frames":frames,
            "base_link_paths":found,
            "sensor_frames_missing_base_link_tf":missing,
            "potentially_dynamic_extrinsics":sorted(conflicting),
            "imu_uptime_to_unix":clock,
            "ground_truth_accessed":False,
            "safety":"RESEARCH_ONLY_NO_NAVIGATION"}


def main():
    p=argparse.ArgumentParser()
    p.add_argument("--bag",type=Path,required=True)
    p.add_argument("--out",type=Path,required=True)
    a=p.parse_args()
    result=inspect_original(a.bag)
    a.out.parent.mkdir(parents=True,exist_ok=True)
    a.out.write_text(json.dumps(result,indent=2,sort_keys=True,allow_nan=False)+"\n")
    print(json.dumps({"missing":result["sensor_frames_missing_base_link_tf"],
                      "clock":result["imu_uptime_to_unix"],"tf_frames":result["all_children"]},
                     sort_keys=True))
    return 0


if __name__=="__main__":
    raise SystemExit(main())
