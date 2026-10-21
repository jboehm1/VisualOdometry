#!/usr/bin/env python3
"""Post-inference similarity score; truth is NEVER given to the image+IMU engine.

Sim3 fits scale to held-out LiDAR-IMU reference. Thus score indicates
trajectory SHAPE only. It is not metric monocular odometry accuracy.
"""
import argparse,json,math
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


def rotation_speed_clock_offset(estimate,truth):
    """Estimate clock shift AFTER inference from rotation-invariant angular speeds.

    This uses reference ORIENTATIONS and is therefore exploratory clock fitting,
    NOT an independent time calibration or a held-out accuracy measurement.
    """
    from scipy.spatial.transform import Rotation
    def angular_rates(rows):
        r=Rotation.from_quat(rows[:,4:8])
        dt=np.diff(rows[:,0])
        if np.any(dt<=0):raise ValueError("Non-monotonic orientation timestamps")
        omega=np.linalg.norm((r[:-1].inv()*r[1:]).as_rotvec(),axis=1)/dt
        return (rows[:-1,0]+rows[1:,0])*.5,omega
    tp,wp=angular_rates(estimate)
    tg,wg=angular_rates(truth)
    lower=float(tg[0]-tp[-1])
    upper=float(tg[-1]-tp[0])
    if upper-lower>1000:raise ValueError("Clock ranges too disjoint")
    best=(-2.0,None,0)
    for offset in np.linspace(lower,upper,1101):
        t=tp+offset
        mask=(t>=tg[0]) & (t<=tg[-1])
        if mask.sum()<30:continue
        a=wp[mask]
        b=np.interp(t[mask],tg,wg)
        sa=np.std(a);sb=np.std(b)
        if sa<.002 or sb<.002:continue
        corr=float(np.mean(((a-a.mean())/sa)*((b-b.mean())/sb)))
        if corr>best[0]:best=(corr,float(offset),int(mask.sum()))
    if best[1] is None or best[0]<.4:
        raise ValueError("Camera/IMU and LiDAR clocks cannot be matched reliably from rotation")
    return best[1],best[0],best[2]

def score(estimate,reference,infer_clock=False,clock_offset_seconds=None):
    if clock_offset_seconds is not None and infer_clock:
        raise ValueError("Cannot fit clock from GT and supply a fixed clock offset simultaneously")
    if clock_offset_seconds is not None and (
        isinstance(clock_offset_seconds,bool) or not math.isfinite(clock_offset_seconds)
    ):
        raise ValueError("Shared clock offset must be a finite number")
    pred,truth=poses(estimate),poses(reference)
    shift,corr,n=(rotation_speed_clock_offset(pred,truth) if infer_clock else
                  (float(clock_offset_seconds) if clock_offset_seconds is not None else 0.0,None,0))
    x=[];y=[]
    for row in pred:
        i=np.searchsorted(truth[:,0],row[0]+shift)
        if not 0<i<len(truth):continue
        lo,hi=truth[i-1],truth[i]
        gap=hi[0]-lo[0]
        if not 0<gap<.4:continue
        t=(row[0]+shift-lo[0])/gap
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
      "posthoc_clock_shift_seconds":shift,
      "posthoc_clock_angular_speed_corr":corr,
      "clock_shift_fitted_using_reference_after_inference":infer_clock,
      "clock_shift_shared_from_reference_fitted_baseline":clock_offset_seconds is not None,
      "timestamp_calibration_independently_verified":not infer_clock and clock_offset_seconds is None,
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
    p.add_argument("--infer-clock-from-reference-rotations",action="store_true")
    p.add_argument("--clock-offset-seconds",type=float)
    a=p.parse_args()
    result=score(a.estimate,a.truth,a.infer_clock_from_reference_rotations,a.clock_offset_seconds)
    a.out.parent.mkdir(parents=True,exist_ok=True)
    a.out.write_text(json.dumps(result,indent=2)+"\n")
    print("AFTER_INFERENCE_REAL_REFERENCE_SCORE",json.dumps(result),flush=True)
