#!/usr/bin/env python3
"""Post-inference similarity score; truth is NEVER given to the image+IMU engine.

Sim3 fits scale to held-out LiDAR-IMU reference. Thus score indicates
trajectory SHAPE only. It is not metric monocular odometry accuracy.
"""
import argparse,json
from pathlib import Path
import numpy as np

def poses(path):
    rows=[]
    for line in Path(path).read_text().splitlines():
        if not line.strip() or line.lstrip().startswith("#"):continue
        v=[float(x) for x in line.split()]
        if len(v)!=8:raise ValueError("Expected timestamp + xyz + xyzw pose columns")
        rows.append(v)
    a=np.asarray(rows,dtype=float)
    if len(a)<30 or not np.isfinite(a).all() or np.any(np.diff(a[:,0])<=0):
        raise ValueError("Missing, unsorted or invalid independent pose stream")
    return a

def score(estimate,reference):
    pred,truth=poses(estimate),poses(reference)
    x=[];y=[]
    for row in pred:
        i=np.searchsorted(truth[:,0],row[0])
        if not 0<i<len(truth):continue
        lo,hi=truth[i-1],truth[i]
        gap=hi[0]-lo[0]
        if not 0<gap<.4:continue
        t=(row[0]-lo[0])/gap
        x.append(row[1:4]);y.append(lo[1:4]*(1-t)+hi[1:4]*t)
    if len(x)<30:raise ValueError(f"Insufficient time-synchronized real position reference: matches={len(x)}, estimate_seconds=[{pred[0,0]:.6f},{pred[-1,0]:.6f}], truth_seconds=[{truth[0,0]:.6f},{truth[-1,0]:.6f}]")
    X,Y=np.asarray(x),np.asarray(y)
    xc,yc=X-X.mean(0),Y-Y.mean(0)
    U,S,Vt=np.linalg.svd(xc.T@yc)
    Q=U@np.diag([1,1,np.linalg.det(U@Vt)])@Vt
    scale=float(np.sum((xc@Q)*yc)/np.sum(xc**2))
    if not np.isfinite(scale) or not .001<abs(scale)<1e4:
        raise ValueError("Degenerate after-the-fact similarity alignment")
    residual=scale*(xc@Q)-yc
    return {
      "gt":"real Mobile-GVIO LiDAR-IMU Fast-LIO2 reference",
      "matched_pose_pairs":len(x),
      "after_truth_similarity_scale":scale,
      "sim3_shape_ate_rmse_m":float(np.sqrt(np.mean(np.sum(residual**2,axis=1)))),
      "scale_optimized_from_gt_only_during_scoring":True,
      "estimator_metric_scale_observed":False,
      "metric_distance_ate_rmse_m":None,
      "reference_pose_sensor_lever_arm_unverified":True,
      "not_Android_ARCore_accuracy":True,
    }

if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--estimate",type=Path,required=True)
    p.add_argument("--truth",type=Path,required=True)
    p.add_argument("--out",type=Path,required=True)
    a=p.parse_args()
    result=score(a.estimate,a.truth)
    a.out.parent.mkdir(parents=True,exist_ok=True)
    a.out.write_text(json.dumps(result,indent=2)+"\n")
    print("AFTER_INFERENCE_REAL_REFERENCE_SCORE",json.dumps(result),flush=True)
