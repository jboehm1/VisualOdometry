#!/usr/bin/env python3
"""Experimentally calibrated DUAL FMCW radar + gyro dead reckoning (no loop closures).

Inputs: genuine UPO original left/right RadarScan Doppler, Ouster IMU uptime
stamps, physically recorded /tf and /tf_static sensor-only calibration report.
Pose truth, robot wheels and LiDAR are NOT read at any point. This is NOT
firefighter navigation, certifiable obstacle mapping or a complete SLAM engine.
"""
import argparse
from bisect import bisect_left
from collections import defaultdict
import json
import math
from pathlib import Path

from calibration_audit import rotation_yaw
from doppler_odom import solve_2d, integrate_gyro, yaw_at
from radar_map import project_hits
from upo_real import sha256_file, quantile

TOPICS={
    "left":"/ti_mmwave_left_radar/radar_scan",
    "right":"/ti_mmwave_right_radar/radar_scan",
    "imu":"/os1_cloud_node/imu",
}
SENSOR_MAP={"left":"radar_left","right":"radar_right","imu":"imu"}


def validate_calibration(cal):
    if cal.get("ground_truth_accessed") is not False:
        raise ValueError("Calibration provenance must be sensor-only")
    if cal.get("sensor_frames_missing_base_link_tf"):
        raise ValueError("Cannot fuse radars without all measured radar/IMU extrinsics")
    paths=cal["base_link_paths"]
    for k in ("radar_left","radar_right","imu"):
        v=paths.get(k)
        if not v or v["root"]!="base_link" or not all(
            math.isfinite(n) for n in [*v["xyz_m"],v["yaw_rad"]]):
            raise ValueError("Sensor TF transform missing: "+k)
    conflicting=set(cal.get("potentially_dynamic_extrinsics",[]))
    for name in cal["named_sensor_frames"].values():
        if name in conflicting:
            raise ValueError("Sensor extrinsic geometry changes during test: "+name)
    clock=cal["imu_uptime_to_unix"]
    if clock["reference_pose_used"] is not False or not .999<=clock["slope"]<=1.001:
        raise ValueError("Invalid sensor-only hardware clock fit")
    if clock.get("p95_minus_p05_arrival_delay_ms",float("inf"))>250:
        raise ValueError("Recorder timestamp scatter too high")
    return {k:paths[SENSOR_MAP[k]] for k in ("left","right","imu")},clock


def sensor_point_in_base(sensor_pose,x,y):
    a=sensor_pose["yaw_rad"]
    rx,ry=sensor_pose["xyz_m"][:2]
    return (rx+math.cos(a)*x-math.sin(a)*y,
            ry+math.sin(a)*x+math.cos(a)*y)


def radial_corrected_for_gyro(sensor_pose,x,y,doppler,omega_z):
    """Transform UPO sensor xyz into base_frame and subtract v_rot(lever arm).

    d = -u . (v_base + omega×lever_arm), stationary-scatterer assumption.
    Thus d_corrected = d + u . (omega×lever_arm).
    """
    distance=math.hypot(x,y)
    if not .25<=distance<=12. or not all(map(math.isfinite,[x,y,doppler,omega_z])):
        return None
    theta=sensor_pose["yaw_rad"]
    ux=(math.cos(theta)*x-math.sin(theta)*y)/distance
    uy=(math.sin(theta)*x+math.cos(theta)*y)/distance
    rx,ry=sensor_pose["xyz_m"][:2]
    effective=doppler+omega_z*(-ux*ry+uy*rx)
    return (ux*distance,uy*distance,effective)


def closest_rate(samples,t,max_gap=.045):
    if not samples:return None
    times=[v[0] for v in samples] if False else None
    # A pre-sorted list is passed to avoid scanning 16k IMU samples per echo.
    ordered,clock=samples
    i=bisect_left(clock,t)
    choices=[j for j in (i-1,i) if 0<=j<len(clock)]
    if not choices:return None
    pick=min(choices,key=lambda j:abs(clock[j]-t))
    return ordered[pick][1] if abs(clock[pick]-t)<=max_gap else None


def read_sensor_messages(bag:Path,clock):
    from rosbags.highlevel import AnyReader
    if not __import__("re").fullmatch(r"experiment_[1-7]\.bag",bag.name) or not (100_000<bag.stat().st_size<3_600_000_000):
        raise ValueError("Original UPO experiment_[1-7].bag required")
    radars=[]
    gyro=[]
    bysensor={"left":0,"right":0}
    nonfinite=0
    with AnyReader([bag]) as reader:
        conns={c.topic:c for c in reader.connections}
        if any(topic not in conns for topic in TOPICS.values()):
            raise ValueError("Missing real radar/IMU topic")
        for conn,bag_time,raw in reader.messages(connections=[conns[t] for t in TOPICS.values()]):
            obj=reader.deserialize(raw,conn.msgtype)
            if conn.topic==TOPICS["imu"]:
                uptime=obj.header.stamp.sec+obj.header.stamp.nanosec*1e-9
                t=clock["intercept_s"]+clock["slope"]*uptime
                rate=float(obj.angular_velocity.z)
                if math.isfinite(t) and math.isfinite(rate):
                    gyro.append((t,rate))
            else:
                label="left" if conn.topic==TOPICS["left"] else "right"
                # Original RadarScan has invalid range/bearing fields; use xyz.
                stamp=obj.header.stamp
                t=stamp.sec+stamp.nanosec*1e-9
                coords=(float(obj.x),float(obj.y),float(obj.velocity))
                if all(math.isfinite(v) for v in (t,*coords)):
                    radars.append((t,label,*coords))
                    bysensor[label]+=1
                else:nonfinite+=1
    radars.sort()
    gyro.sort()
    if len(radars)<100 or len(gyro)<500:
        raise ValueError("Too few real sensor observations")
    return radars,gyro,{"total_points":len(radars),"gyro_rows":len(gyro),
                       "by_radar":bysensor,"nonfinite_dropped":nonfinite,
                       "gyro_timestamp":"sensor_only_clock_fit_from_imu_uptime",
                       "radar_timestamp":"original_ros_message_unix_header",
                       "no_reference_input":True}


def estimate_two_radar(radar,gyro,extrinsics,bin_seconds=.5):
    if not .2<=bin_seconds<=1.:
        raise ValueError("Unexpected Doppler window duration")
    gyro,gyro_t=sorted(gyro),[p[0] for p in sorted(gyro)]
    if any(t1<=t0 for t0,t1 in zip(gyro_t,gyro_t[1:])):
        # Allow duplicate hardware stamps but do not silently interpolate them.
        raise ValueError("IMU timestamps not strictly increasing")
    yaw_t,yaw=integrate_gyro(gyro)
    radar_begin=radar[0][0]
    windows=defaultdict(list)
    missing_gyro=0
    mapped_points=[]
    for t,which,x,y,d in radar:
        rate=closest_rate((gyro,gyro_t),t)
        if rate is None:
            missing_gyro+=1
            continue
        corrected=radial_corrected_for_gyro(extrinsics[which],x,y,d,rate)
        if corrected is None:continue
        windows[int((t-radar_begin)/bin_seconds)].append((t,which,corrected))
        world_xy=sensor_point_in_base(extrinsics[which],x,y)
        mapped_points.append((t,*world_xy,d))
    pos=(0.,0.)
    track=[]
    reasons={}
    last=None
    integrated=0.
    per_bin={}
    for key in sorted(windows):
        batch=windows[key]
        t=sorted(v[0] for v in batch)[len(batch)//2]
        heading=yaw_at(yaw_t,yaw,t)
        if heading is None:
            reason="missing_gyro_heading"
            solution=None
        else:
            solution,reason=solve_2d([v[2] for v in batch])
        if reason:
            reasons[reason]=reasons.get(reason,0)+1
            last=None
            continue
        bx,by=solution["vx"],solution["vy"]
        source_counts={"left":sum(z[1]=="left" for z in batch),
                       "right":sum(z[1]=="right" for z in batch)}
        if last is None or (t-last)>1.9*bin_seconds or t<=last:
            if last is not None:reasons["discontinuous_motion"]=reasons.get("discontinuous_motion",0)+1
            track.append({"t":t,"x":pos[0],"y":pos[1],"yaw":heading,
                          "new_segment":True,"velocity_mps":[bx,by],
                          "doppler_median_abs_radial_residual_mps":solution["median_abs_radial_residual_mps"],
                          "radar_contributors":source_counts})
            last=t
            continue
        dt=t-last
        dx=dt*(math.cos(heading)*bx-math.sin(heading)*by)
        dy=dt*(math.sin(heading)*bx+math.cos(heading)*by)
        pos=(pos[0]+dx,pos[1]+dy)
        integrated+=dt
        track.append({"t":t,"x":pos[0],"y":pos[1],"yaw":heading,
                      "new_segment":False,"velocity_mps":[bx,by],
                      "doppler_median_abs_radial_residual_mps":solution["median_abs_radial_residual_mps"],
                      "radar_contributors":source_counts})
        last=t
    if len(track)<8:
        raise ValueError("Not enough nondegenerate 2-radar velocity windows")
    cells,mapping=project_hits(track,mapped_points,.25,.3)
    travel=sum(math.dist((a["x"],a["y"]),(b["x"],b["y"]))
               for a,b in zip(track,track[1:]) if not b["new_segment"])
    return track,cells,{
        "window_duration_s":bin_seconds,
        "received_windows_with_valid_gyro":len(windows),
        "accepted_windows":len(track),
        "accepted_fraction":round(len(track)/len(windows),5) if windows else 0.,
        "reasons_for_rejection":reasons,
        "radar_points_discarded_no_gyro":missing_gyro,
        "integrated_seconds":round(integrated,4),
        "integrated_path_m":round(travel,5),
        "velocity_median_mps":quantile([math.hypot(*p["velocity_mps"]) for p in track],.5),
        "positive_echo_map":mapping,
    }


def main(argv=None):
    p=argparse.ArgumentParser(description="Real calibrated TF dual radar Doppler plus IMU; no reference inputs.")
    p.add_argument("--bag",type=Path,required=True)
    p.add_argument("--tf-clock",type=Path,required=True)
    p.add_argument("--out",type=Path,required=True)
    a=p.parse_args(argv)
    cal=json.loads(a.tf_clock.read_text(encoding="utf-8"))
    sensors,clock=validate_calibration(cal)
    radar,gyro,source=read_sensor_messages(a.bag,clock)
    track,cells,health=estimate_two_radar(radar,gyro,sensors)
    report={
        "mode":"DUAL_RADAR_CALIBRATED_TF_SENSOR_CLOCK",
        "actual_own_estimator_executed":True,
        "reference_used_during_estimation":False,
        "source_sha256":sha256_file(a.bag),
        "source_license":"CC-BY-NC-SA-3.0",
        "reported_input_frames":source,
        "calibration":{"extrinsics_to_base":sensors,
                       "imu_clock_sensor_only":clock,
                       "unknown_hardware_latency":True,
                       "roll_and_pitch_handling":"planar approx only",
                       "calibration_reference_pose_used":False},
        "motion_observability":health,
        "trajectory":track,
        "positive_evidence_grid":{"resolution_m":.25,
                                  "cells":[[ix,iy,n] for (ix,iy),n in sorted(cells.items())],
                                  "interpretation":"positive radar echoes only, NOT verified obstacles; unknown space not free"},
        "limitations":[
            "This is radar velocity dead reckoning, not SLAM (no feature matching or loop closure).",
            "Device clock is correlated to receipt time, fixed latency unknown; TF reported but not surveyed.",
            "Pure gyro-z yaw integration accumulates unknown bias/drift, no inclination model.",
            "Static-scatterer assumption, moving targets and reflections can bias Doppler.",
            "Original corpus is robot-mounted hardware, not firefighter helmet/body movement.",
            "Reference, wheel odometry, LiDAR and thermal images are NOT estimator inputs.",
            "No independent smoke obstacle recall, route safety, or human-fire suitability."
        ],
        "safety":"RESEARCH_ONLY_NOT_A_RESPONDER_TOOL",
    }
    a.out.parent.mkdir(parents=True,exist_ok=True)
    a.out.write_text(json.dumps(report,indent=2,sort_keys=True,allow_nan=False)+"\n",encoding="utf-8")
    print(json.dumps({"status":"RESEARCH_ONLY","source":report["source_sha256"],
                      "accepted_windows":len(track),
                      "integrated_path_m":health["integrated_path_m"],
                      "integrated_seconds":health["integrated_seconds"],
                      "source_frames":source["by_radar"],
                      "mapped_observed_cells":health["positive_echo_map"]["occupied_evidence_cells"]}))
    return 0


if __name__=="__main__":
    raise SystemExit(main())
