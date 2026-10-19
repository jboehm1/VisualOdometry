#!/usr/bin/env python3
"""Research-only Doppler-radar + gyroscope yaw odometry on ORIGINAL UPO data.

No published reference, LiDAR map or wheel odometry enters estimation.
Yaw integration uses gyroscope Z and the UNVERIFIED assumption radar/IMU axes
are upright with constant yaw offset. A final SE(2) posthoc fit is ONLY for
evaluation against the partially odometry-smoothed UPO pseudo-reference.
A green test DOES NOT mean navigation quality, smoke obstacle recall or SLAM.
"""
from __future__ import annotations
import argparse
from bisect import bisect_left
from collections import defaultdict
import json
import math
from pathlib import Path
import statistics

from upo_real import ros_stamp, quantile, sha256_file
from radar_map import project_hits

SOURCE_TOPICS = {
    "radar": "/ti_mmwave_left_radar/radar_scan",
    "gyro": "/os1_cloud_node/imu",
    "reference": "/ground_truth",
}
MAX_SOURCE_BYTES = 2_400_000_000
MIN_POINTS = 5
WINDOW_S = 0.5


def median(x):
    return statistics.median(x) if x else None


def solve_2d(samples):
    """Robust least-squares radar ego vx,vy from static-target radial Doppler.

    The radar publishes positive radial velocity for receding targets.
    Static world -> unit(x,y) dot ego_velocity = -target_radial_velocity.
    Require angular diversity (not simply many observations).
    """
    rows=[]
    for x,y,doppler in samples:
        if not all(map(math.isfinite,(x,y,doppler))):
            continue
        rng=math.hypot(x,y)
        if not 0.25<=rng<=12 or abs(doppler)>3.0:
            continue
        rows.append((x/rng,y/rng,-doppler))
    if len(rows)<MIN_POINTS:
        return None, "insufficient_returns"

    def least(weights):
        aa=ab=bb=ac=bc=0.0
        for (a,b,c),w in zip(rows,weights):
            aa+=w*a*a
            ab+=w*a*b
            bb+=w*b*b
            ac+=w*a*c
            bc+=w*b*c
        det=aa*bb-ab*ab
        trace=aa+bb
        if det<=1e-8 or trace<=0:
            return None
        # lambda_min / trace > 0.015 ; poorly spanning bins are UNSOLVABLE
        discriminant=math.sqrt(max(0,(aa-bb)**2+4*ab*ab))
        if (trace-discriminant)/(2*trace)<0.015:
            return None
        return ((ac*bb-bc*ab)/det,(bc*aa-ac*ab)/det)

    solution=least([1.]*len(rows))
    if solution is None:
        return None, "geometry_degenerate"
    for _ in range(4):
        residual=[a*solution[0]+b*solution[1]-c for a,b,c in rows]
        scale=max(0.10,1.4826*median([abs(r) for r in residual]))
        weights=[min(1., 1.5*scale/max(abs(r),1e-12)) for r in residual]
        revised=least(weights)
        if revised is None:
            return None, "geometry_degenerate"
        solution=revised
    errors=[abs(a*solution[0]+b*solution[1]-c) for a,b,c in rows]
    if math.hypot(*solution)>3.0 or median(errors)>0.7:
        return None, "inconsistent_doppler"
    return {"vx":solution[0],"vy":solution[1],
            "median_abs_radial_residual_mps":median(errors),
            "returns":len(rows)}, None


def integrate_gyro(samples):
    """Gyro raw angular Z (rad/s). No magnetic/absolute yaw and no drift correction."""
    ordered=sorted(samples)
    if len(ordered)<3:
        raise ValueError("No valid measured gyroscope samples")
    t=[ordered[0][0]]
    theta=[0.]
    for time,rate in ordered[1:]:
        previous=t[-1]
        if time<=previous:
            continue
        dt=time-previous
        if dt>0.20 or not math.isfinite(rate) or abs(rate)>8.:
            # This gap is unsafe for motion inference; fail rather than skip yaw.
            raise ValueError("Gyro time gap or invalid angular rate prevents yaw integration")
        theta.append(theta[-1]+rate*dt)
        t.append(time)
    # Absolute angular heading has arbitrary offset. z-axis sign/extrinsic TBD.
    return t,theta


def yaw_at(times, angles, t):
    idx=bisect_left(times,t)
    if idx==0:
        return angles[0] if abs(times[0]-t)<=.06 else None
    if idx==len(times):
        return angles[-1] if abs(times[-1]-t)<=.06 else None
    a,b=times[idx-1],times[idx]
    return angles[idx-1]+(angles[idx]-angles[idx-1])*(t-a)/(b-a)


def estimate(radar, gyro, bin_seconds=WINDOW_S):
    if not radar:
        raise ValueError("No original radar Doppler observations")
    times,angles=integrate_gyro(gyro)
    start=radar[0][0]
    bins=defaultdict(list)
    for t,x,y,doppler in radar:
        if t<times[0] or t>times[-1]:
            continue
        idx=int((t-start)/bin_seconds)
        bins[idx].append((t,x,y,doppler))
    output=[];reasons={};accepted=0;elapsed_valid=0.
    pose=(0.,0.)
    last_good=None
    min_bins=math.ceil(2.5/bin_seconds)
    for key in sorted(bins):
        observations=bins[key]
        t=median([a[0] for a in observations])
        yaw=yaw_at(times,angles,t)
        velocity,reason=solve_2d([(x,y,d) for _,x,y,d in observations])
        if yaw is None:
            reason="missing_gyro"
        if reason:
            reasons[reason]=reasons.get(reason,0)+1
            last_good=None
            continue
        if last_good is None:
            # New segment can never be silently stitched across rejected periods.
            last_good=t
            output.append({"t":t,"x":pose[0],"y":pose[1],"yaw":yaw,
                           "velocity":velocity,"new_segment":True})
            accepted+=1
            continue
        dt=t-last_good
        if dt<=0 or dt>bin_seconds*1.9:
            reasons["stale_speed_gap"]=reasons.get("stale_speed_gap",0)+1
            last_good=t
            output.append({"t":t,"x":pose[0],"y":pose[1],"yaw":yaw,
                           "velocity":velocity,"new_segment":True})
            accepted+=1
            continue
        # Radar frame used for translation, gyro Z used for yaw. Sensor
        # extrinsic offset to IMU is UNKNOWN; alignment may hide static rotation.
        vx,vy=velocity["vx"],velocity["vy"]
        dx=dt*(math.cos(yaw)*vx-math.sin(yaw)*vy)
        dy=dt*(math.sin(yaw)*vx+math.cos(yaw)*vy)
        pose=(pose[0]+dx,pose[1]+dy)
        output.append({"t":t,"x":pose[0],"y":pose[1],"yaw":yaw,
                       "velocity":velocity,"new_segment":False})
        last_good=t
        elapsed_valid+=dt
        accepted+=1
    if accepted<min_bins:
        raise ValueError("Too few non-degenerate measured Doppler bins to claim any odometry")
    return output,{"accepted_bins":accepted,"rejected_bins":reasons,
                   "received_bins":len(bins),"active_integrated_seconds":round(elapsed_valid,4),
                   "accepted_fraction":round(accepted/max(1,len(bins)),6),
                   "estimated_traveled_path_m":round(sum(
                       math.hypot(b["x"]-a["x"],b["y"]-a["y"])
                       for a,b in zip(output,output[1:]) if not b["new_segment"]),5),
                   "body_velocity_mps_p50":quantile(
                       [math.hypot(p["velocity"]["vx"],p["velocity"]["vy"]) for p in output], .5),
                   "statistical_method":"IRLS fixed Huber Doppler LS; no pose truth used",
                   "gyro_heading":"integrated measured IMU_z, unverified radar-IMU extrinsic"}


def rigid_2d_align_and_score(traj, reference, max_gap_s=.12):
    """After estimator freezes, align SE2 (NOT Sim2) and score *pseudo* GT."""
    time_ref=[p[0] for p in reference]
    paired=[]
    for p in traj:
        idx=bisect_left(time_ref,p["t"])
        candidates=[i for i in (idx-1,idx) if 0<=i<len(reference)]
        if not candidates: continue
        nearest=min(candidates,key=lambda i:abs(time_ref[i]-p["t"]))
        gt=reference[nearest]
        if abs(gt[0]-p["t"])<=max_gap_s:
            paired.append((p["x"],p["y"],gt[1],gt[2],p["t"],p["new_segment"]))
    if len(paired)<8:
        return {"status":"NO_TIME_MATCHED_REFERENCE","pairs":len(paired),
                "reference_warning":"No independent accurate truth matched"}
    mx=statistics.mean(p[0] for p in paired)
    my=statistics.mean(p[1] for p in paired)
    gx=statistics.mean(p[2] for p in paired)
    gy=statistics.mean(p[3] for p in paired)
    cosine=sum((x-mx)*(xx-gx)+(y-my)*(yy-gy) for x,y,xx,yy,_,_ in paired)
    sine=sum((x-mx)*(yy-gy)-(y-my)*(xx-gx) for x,y,xx,yy,_,_ in paired)
    rotation=math.atan2(sine,cosine) if math.hypot(cosine,sine)>1e-8 else 0.
    c,s=math.cos(rotation),math.sin(rotation)
    errors=[math.hypot((c*(x-mx)-s*(y-my)+gx)-xx,(s*(x-mx)+c*(y-my)+gy)-yy)
            for x,y,xx,yy,_,_ in paired]
    stationary_rmse=math.sqrt(sum((xx-gx)**2+(yy-gy)**2
                                   for _,_,xx,yy,_,_ in paired)/len(paired))
    rmse=math.sqrt(sum(e*e for e in errors)/len(errors))
    relative_to_static=rmse/stationary_rmse if stationary_rmse>1e-6 else None
    estimate_segment_path=sum(
        math.hypot(b[0]-a[0],b[1]-a[1])
        for a,b in zip(paired,paired[1:])
        if (b[4]-a[4])<=1.0 and not b[5]
    )
    pseudo_segment_path=sum(
        math.hypot(b[2]-a[2],b[3]-a[3])
        for a,b in zip(paired,paired[1:])
        if (b[4]-a[4])<=1.0 and not b[5]
    )
    return {
        "zero_motion_baseline_rmse_m":round(stationary_rmse,5),
        "ate_rmse_over_stationary_baseline":round(relative_to_static,5) if relative_to_static is not None else None,
        "pathlength_over_matched_contiguous_intervals_estimated_m":round(estimate_segment_path,5),
        "pathlength_over_matched_contiguous_intervals_pseudo_reference_m":round(pseudo_segment_path,5),
        "status":"SE2_ALIGNED_AGAINST_PARTIALLY_ODOMETRY_DERIVED_REFERENCE",
        "pairs":len(errors),
        "ate_rmse_m_vs_pseudo_reference":round(math.sqrt(sum(e*e for e in errors)/len(errors)),5),
        "ate_median_m_vs_pseudo_reference":round(median(errors),5),
        "ate_p95_m_vs_pseudo_reference":quantile(errors,.95),
        "reference_alignment_rotation_deg":round(math.degrees(rotation),3),
        "time_gap_gate_s":max_gap_s,
        "scale_fit":False,
        "used_reference_during_inference":False,
        "caveat":"Marker observations ±0.1m/0.1rad and interpolated odometry; SE2 is fitted POSTHOC on evaluation points. Not independent absolute accuracy.",
    }


def input_original(bag:Path,max_seconds=60.,clock_model=None):
    if bag.name!="experiment_7.bag" or not bag.is_file():
        raise ValueError("Require original UPO experiment_7.bag")
    if not 1e5<bag.stat().st_size<MAX_SOURCE_BYTES:
        raise ValueError("Wrong source size")
    from rosbags.highlevel import AnyReader
    data={"radar":[],"gyro":[],"reference":[]}
    stats={"bag_source_counts":{},"frame_ids":{},"recording_duration_selected_s":max_seconds,
           "clock_warning":"IMU device-uptime headers mismatch radar Unix epoch; IMU bag receipt time substituted. Latency not measured."}
    with AnyReader([bag]) as reader:
        connection={c.topic:c for c in reader.connections}
        if any(v not in connection for v in SOURCE_TOPICS.values()):
            raise ValueError("Missing required original radar Doppler, gyro, pseudo-reference")
        chosen=[connection[v] for v in SOURCE_TOPICS.values()]
        start=reader.start_time
        last={"radar":float("-inf"),"gyro":float("-inf"),"reference":float("-inf")}
        for conn,bag_time,raw in reader.messages(connections=chosen):
            if (bag_time-start)*1e-9>max_seconds:break
            kind=next(k for k,v in SOURCE_TOPICS.items() if v==conn.topic)
            obj=reader.deserialize(raw,conn.msgtype)
            if not hasattr(obj,"header"):raise ValueError("Missing original ROS header")
            stamp=ros_stamp(obj)
            stats["frame_ids"].setdefault(kind,set()).add(obj.header.frame_id)
            stats["bag_source_counts"][kind]=stats["bag_source_counts"].get(kind,0)+1
            # Non-monotonic samples can arise at scan-level repeated stamps:
            # gyro and reference strict, radar points may share exact time.
            if kind!="radar" and stamp<=last[kind]: continue
            if kind=="radar":
                for field in ("x","y","velocity"):
                    if not hasattr(obj,field):raise ValueError("Custom RadarScan Doppler not decoded")
                coords=(float(obj.x),float(obj.y),float(obj.velocity))
                if all(math.isfinite(v) for v in coords):
                    data["radar"].append((stamp,*coords))
            elif kind=="gyro":
                z=float(obj.angular_velocity.z)
                if math.isfinite(z):
                    # Ouster IMU ROS header stamps are device-uptime (~2788 s)
                    # while radar and GT are Unix epoch (~1.6e9 s).
                    # Use bag receipt time as a common *approximate* clock;
                    # this incurs non-characterized USB/ROS scheduling latency.
                    data["gyro"].append(((clock_model["intercept_s"]+clock_model["slope"]*stamp) if clock_model is not None else bag_time*1e-9,z))
            else:
                x=float(obj.pose.position.x);y=float(obj.pose.position.y)
                if math.isfinite(x) and math.isfinite(y):
                    data["reference"].append((stamp,x,y))
            last[kind]=stamp
    for k in stats["frame_ids"]:
        stats["frame_ids"][k]=sorted(stats["frame_ids"][k])
    return data,stats


def render_map(traj,path:Path,echo_cells=None,cell_size_m=.25):
    pts=[(p["x"],p["y"]) for p in traj]
    xs=[v[0] for v in pts];ys=[v[1] for v in pts]
    breaks=sum(1 for p in traj if p["new_segment"])
    echo_cells=echo_cells or {}
    for cx,cy in echo_cells:
        xs.append(cx*cell_size_m)
        ys.append(cy*cell_size_m)
    minx,maxx,miny,maxy=min(xs),max(xs),min(ys),max(ys)
    scale=650/max(maxx-minx,maxy-miny,0.5)
    coords=" ".join(f"{(x-minx)*scale+30:.1f},{690-(y-miny)*scale:.1f}" for x,y in pts)
    rects=[]
    for cx,cy in sorted(echo_cells):
        xx=(cx*cell_size_m-minx)*scale+30
        yy=690-((cy+1)*cell_size_m-miny)*scale
        size=cell_size_m*scale
        rects.append(f'<rect x="{xx:.1f}" y="{yy:.1f}" width="{size:.1f}" height="{size:.1f}" fill="#ffa46d" opacity=".40"/>')
    background="".join(rects)
    page=f"""<!DOCTYPE html><html lang="fr"><meta charset="utf-8"><title>UPO Doppler/IMU — recherche</title>
<style>body{{background:#0e1722;color:#e9eff4;font:17px system-ui;padding:2em}}.warn{{border:2px solid orange;padding:1em}}</style>
<h1>Radar Doppler + gyro — trajectoire de recherche</h1>
<p class="warn">NON VALIDÉ POUR LA NAVIGATION. Pose dans un repère ARBITRAIRE,
hypothèse extrinsèque non calibrée, pas de chemin de sortie. La courbe est uniquement
reconstruite à partir des retours Doppler et du gyroscope, pas depuis la référence de position.
Des interruptions d'observabilité peuvent créer des segments non connectés physiquement.</p>
<p>{len(traj)} poses, {breaks} débuts de segments, {len(echo_cells)} cellules de retours radar positifs observés.</p>
<p>Orange = observations radar positives, PAS obstacle confirmé ; les zones non observées sont INCONNUES, jamais libres.</p>
<svg viewBox="0 0 750 750" style="max-width:800px;background:#182838">
{background}
<polyline fill="none" stroke="#73e8e0" stroke-width="3" points="{coords}"/></svg></html>"""
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(page,encoding="utf-8")


def main(argv=None):
    ap=argparse.ArgumentParser(description="Experimental UPO radar-Doppler + gyro dead reckoning")
    ap.add_argument("--bag",type=Path,required=True)
    ap.add_argument("--out",type=Path,required=True)
    ap.add_argument("--html",type=Path,required=True)
    ap.add_argument("--max-seconds",type=float,default=60.)
    ap.add_argument("--clock-audit",type=Path,help="Optional sensor-only IMU uptime->host clock audit JSON. No GT.")
    args=ap.parse_args(argv)
    if not 10<=args.max_seconds<=168:raise ValueError("limit 10..168 sec")
    clock_data=json.loads(args.clock_audit.read_text(encoding="utf-8")) if args.clock_audit else None
    if clock_data is not None:
        if clock_data.get("ground_truth_accessed") is not False or clock_data.get("imu_uptime_to_unix",{}).get("reference_pose_used") is not False:
            raise ValueError("Reject clock transform if reference was used in calibration")
        if clock_data.get("sensor_frames_missing_base_link_tf") is None:
            raise ValueError("Invalid clock audit provenance")
    model=clock_data["imu_uptime_to_unix"] if clock_data else None
    samples,source=input_original(args.bag,args.max_seconds,clock_model=model)
    source["gyro_clock_mode"]="uptime_robust_sensor_only_affine" if model else "bag_receipt_time_unverified_delay"
    if model: source["gyro_clock_audit_summary"]={k:model[k] for k in ("reported_clock_drift_ppm","median_arrival_lag_ms","p95_minus_p05_arrival_delay_ms")}
    trajectory,flags=estimate(samples["radar"],samples["gyro"])
    # Exactly after estimate() returned — marker/odometry truth is only evaluation data.
    cells, map_health=project_hits(trajectory,samples["radar"])
    evals=rigid_2d_align_and_score(trajectory,samples["reference"])
    report={"dataset":"upo-smoke-exp7","source_license":"CC-BY-NC-SA-3.0",
            "source_sha256":sha256_file(args.bag),
            "clock_estimation_uses_reference":False,
            "mode":"MEASURED_RADAR_DOPPLER_PLUS_GYRO_UNCALIBRATED_EXTRINSICS",
            "actual_own_estimator_executed":True,"reference_used_during_estimation":False,
            "independent_accurate_ground_truth":False,"certified_navigation":False,
            "sensor_source":source,"motion_observability":flags,
            "sparse_positive_echo_map":map_health,
            "positive_evidence_grid": {"resolution_m":0.25,
                "cells": [[ix,iy,qty] for (ix,iy),qty in sorted(cells.items())],
                "source":"Real radar echoes projected using UNCALIBRATED radar-gyro estimate"},
            "pseudo_reference_posthoc_evaluation":evals,
            "trajectory":trajectory,
            "limits":["Unverified radar↔IMU rotation/frame_id: do not claim physical heading",
                      "Moving targets violate static-scatterer Doppler assumption",
                      "Ouster IMU header timestamps use uptime clock; approximate ROS bag arrival time instead",
                      "Gyro-only yaw accumulates drift; no gravity/lever arm compensation",
                      "Radar outages cause unknown unintegrated motion; plotted segments may join gaps",
                      "Reference is AR marker plus wheel-odometry-smoothed, not independent everywhere",
                      "No LiDAR or ground truth read by own estimator, no smoke obstacle classification",
                      "Radar ≠ photorealistic see-through-smoke, never guide a real firefighter"]}
    args.out.parent.mkdir(parents=True,exist_ok=True)
    args.out.write_text(json.dumps(report,indent=2,allow_nan=False),encoding="utf-8")
    render_map(trajectory,args.html,cells)
    print(json.dumps({"status":"RESEARCH","observability":flags,
                      "reference_score":evals,"source_sha256":report["source_sha256"],
                      "frames":len(trajectory),"html":str(args.html)},sort_keys=True))
    return 0


if __name__=="__main__":
    raise SystemExit(main())
