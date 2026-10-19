#!/usr/bin/env python3
"""Score frozen radar-IMU trajectory on sparse fiducial-pose observations.

Reference is only opened after estimator output exists. SE2 alignment is fit on
early marker positions ONLY. The later marker positions are held out. The
source marker world poses may still depend on robot odometry and are not a
guaranteed independent ground truth. No scale or clock fitting is permitted.
"""
import argparse
from bisect import bisect_left
import json
import math
from pathlib import Path
import statistics
from upo_real import quantile


def fit_se2(src,dst):
    if len(src)!=len(dst) or len(src)<3:
        raise ValueError("At least 3 marker calibration pairs required")
    mx=statistics.mean(a for a,b in src)
    my=statistics.mean(b for a,b in src)
    gx=statistics.mean(a for a,b in dst)
    gy=statistics.mean(b for a,b in dst)
    dot=sum((x-mx)*(u-gx)+(y-my)*(v-gy) for (x,y),(u,v) in zip(src,dst))
    cross=sum((x-mx)*(v-gy)-(y-my)*(u-gx) for (x,y),(u,v) in zip(src,dst))
    yaw=math.atan2(cross,dot)
    c,s=math.cos(yaw),math.sin(yaw)
    tx=gx-c*mx+s*my
    ty=gy-s*mx-c*my
    return yaw,tx,ty


def apply_se2(p,fit):
    yaw,tx,ty=fit
    c,s=math.cos(yaw),math.sin(yaw)
    return (c*p[0]-s*p[1]+tx,s*p[0]+c*p[1]+ty)


def split_marker_score(estimated,markers,max_gap_s=.12,train_fraction=.25):
    """No truth reaches the odometry estimator; train alignment and held-out split."""
    if not estimated or not markers:
        return {"status":"NO_MARKER_TEST_DATA","heldout_pairs":0}
    ts=[x["t"] for x in estimated]
    paired=[]
    for t,x,y,variance in markers:
        if not all(map(math.isfinite,(t,x,y,variance))) or variance<0:
            continue
        k=bisect_left(ts,t)
        neighbors=[j for j in (k-1,k) if 0<=j<len(ts)]
        if not neighbors:continue
        j=min(neighbors,key=lambda j:abs(ts[j]-t))
        if abs(ts[j]-t)>max_gap_s:continue
        a=estimated[j]
        paired.append((t,(a["x"],a["y"]),(x,y),variance))
    paired.sort()
    if len(paired)<20:
        return {"status":"INSUFFICIENT_DIRECT_MARKER_MATCHES",
                "matched_pairs":len(paired),"heldout_pairs":0}
    n_train=max(5,min(len(paired)-5,math.ceil(len(paired)*train_fraction)))
    fit=fit_se2([r[1] for r in paired[:n_train]],[r[2] for r in paired[:n_train]])
    test=paired[n_train:]
    errors=[math.dist(apply_se2(r[1],fit),r[2]) for r in test]
    # Zero-motion baseline, fixed to early markers, not fitted to heldout.
    anchor=(statistics.mean(r[2][0] for r in paired[:n_train]),
            statistics.mean(r[2][1] for r in paired[:n_train]))
    baseline=[math.dist(anchor,r[2]) for r in test]
    return {
        "status":"MARKER_EARLY_SE2_CALIBRATION_LATER_HELDOUT",
        "matched_pairs":len(paired),"alignment_train_pairs":n_train,
        "heldout_pairs":len(test),
        "alignment_only_train_marker_fraction":round(n_train/len(paired),5),
        "train_time_range_s":[paired[0][0],paired[n_train-1][0]],
        "test_time_range_s":[test[0][0],test[-1][0]],
        "heldout_aligned_rmse_m":round(math.sqrt(statistics.mean(e*e for e in errors)),5),
        "heldout_median_m":round(statistics.median(errors),5),
        "heldout_p95_m":quantile(errors,.95),
        "stationary_baseline_heldout_rmse_m":round(math.sqrt(statistics.mean(e*e for e in baseline)),5),
        "alignment_scale_fit":False,
        "alignment_and_training_pose_not_used_during_estimation":True,
        "marker_covariance_xy_variance_p50":quantile([r[3] for r in test],.5),
        "reference_label":"fiducial_pose marker estimates; independence of robot odometry NOT verified",
    }


def load_markers(original,limit_s=168.):
    from rosbags.highlevel import AnyReader
    if original.name!="experiment_7.bag" or not original.is_file():
        raise ValueError("Use original UPO experiment_7.bag only")
    marker=[]
    with AnyReader([original]) as reader:
        chosen=[c for c in reader.connections if c.topic=="/fiducial_pose"]
        if len(chosen)!=1:
            raise ValueError("Source lacks original /fiducial_pose")
        start=reader.start_time
        for conn,bag_time,raw in reader.messages(connections=chosen):
            if (bag_time-start)*1e-9>limit_s:break
            msg=reader.deserialize(raw,conn.msgtype)
            if msg.header.frame_id!="map":
                raise ValueError("Unexpected marker frame; spatial transforms not verified")
            t=float(msg.header.stamp.sec)+float(msg.header.stamp.nanosec)*1e-9
            p=msg.pose.pose.position
            cov=msg.pose.covariance
            variance=max(0.,float(cov[0])+float(cov[7]))
            marker.append((t,float(p.x),float(p.y),variance))
    return marker


def main(argv=None):
    ap=argparse.ArgumentParser()
    ap.add_argument("--bag",required=True,type=Path)
    ap.add_argument("--trajectory-report",required=True,type=Path)
    ap.add_argument("--out",required=True,type=Path)
    ap.add_argument("--max-seconds",type=float,default=168.)
    a=ap.parse_args(argv)
    record=json.loads(a.trajectory_report.read_text(encoding="utf-8"))
    if record.get("reference_used_during_estimation") is not False:
        raise ValueError("Not a verified reference-free sensor estimator")
    if record.get("mode")!="MEASURED_RADAR_DOPPLER_PLUS_GYRO_UNCALIBRATED_EXTRINSICS":
        raise ValueError("Unexpected estimator engine")
    if not 10<=a.max_seconds<=168:
        raise ValueError("Invalid observation interval")
    m=load_markers(a.bag,a.max_seconds)
    score=split_marker_score(record["trajectory"],m)
    result={"mode":"POSTHOC_HELDOUT_FIDUCIAL_EVALUATOR_NOT_INDEPENDENT_ABSOLUTE_TRUTH",
            "raw_marker_messages":len(m),
            "source_estimator_sha256":record["source_sha256"],
            "score":score,"safety":"RESEARCH_ONLY_NOT_FOR_RESCUE"}
    a.out.parent.mkdir(parents=True,exist_ok=True)
    a.out.write_text(json.dumps(result,sort_keys=True,indent=2,allow_nan=False)+"\n",encoding="utf-8")
    print(json.dumps(result,sort_keys=True))
    return 0


if __name__=="__main__":
    raise SystemExit(main())
