#!/usr/bin/env python3
"""Convert independently sourced OxIOD synchronized IMU + Vicon truth to causal
64x6 (50 Hz) smartphone-window training pairs for PDR_AI_MLP_V1.

Input: JSON {sequences:[{imu:"...",truth:"...",person_id:"...",split:"train|val|test"}]}.
Explicit IMU paths and subject IDs mandatory; no auto-subject guessing.
GT used ONLY for device-frame regression labels and offline evaluation, never
for Android features. Assumes OxIOD syn quaternion rotates phone/body to world;
verify this orientation on source metadata before trusting results.
"""
import argparse,csv,json,math
from pathlib import Path
import numpy as np
from datasets import oxrows,G
from tiny_carry_train import features

def quaternion_inverse_rotate(q,v):
    """R(q).T @ v, scalar-first unit quaternion."""
    w,x,y,z=map(float,q)
    n=math.sqrt(w*w+x*x+y*y+z*z)
    if n<.9 or n>1.1:raise ValueError("OxIOD quaternion not near unit length")
    w/=n;x/=n;y/=n;z/=n
    # v' = v - 2*w*(qvec cross v) + 2*qvec cross(qvec cross v)
    u=np.array([x,y,z]);v=np.asarray(v,dtype=np.float64)
    temp=np.cross(u,v)
    return v-2*w*temp+2*np.cross(u,temp)

def sequence_features(imu_file,gt_file):
    imu=np.asarray(list(oxrows(imu_file,16)),dtype=np.float64)
    gt=np.asarray(list(oxrows(gt_file,9)),dtype=np.float64)
    if len(imu)<100 or len(gt)<100:raise ValueError("not enough OxIOD synchronized samples")
    if len(imu)!=len(gt):raise ValueError("OxIOD sync sample count differs")
    if np.max(np.abs(imu[:,0]-gt[:,0]))>.025:
        raise ValueError("IMU and optical GT not synchronized within 25ms")
    if not np.all(np.diff(imu[:,0])>0) or not np.all(np.isfinite(imu)):
        raise ValueError("nonfinite or non-monotonic IMU")
    # OxIOD device gyro [4:7], gravity [7:10] and user acceleration [10:13].
    # Both acceleration inputs are in g, so restore actual Android accel m/s².
    acc=(imu[:,7:10]+imu[:,10:13])*G
    gyro=imu[:,4:7]
    raw=np.hstack((acc,gyro))
    poses=gt[:,2:5]
    # OxIOD quaternion is in columns x/y/z/w (5:9), world_from_device.
    qs=gt[:,[8,5,6,7]]
    rows=[]
    indices=np.arange(0,len(imu),2)
    for offset in range(0,len(indices)-64,32):
        idx=indices[offset:offset+64]
        dt=np.diff(imu[idx,0])
        if np.min(dt)<.014 or np.max(dt)>.055:continue
        if np.max(np.linalg.norm(acc[idx],axis=1))>70 or np.max(np.linalg.norm(gyro[idx],axis=1))>30:continue
        start,end=int(idx[0]),int(idx[-1])
        diff=poses[end]-poses[start]
        local=quaternion_inverse_rotate(qs[start],diff)
        # World vertical can leak to phone device XY under pitch: this is the
        # exact phone-axes displacement convention, not horizontal ENU.
        if np.linalg.norm(local)>25:continue
        feat=features(raw[idx].reshape(1,64,6))[0].astype(float)
        rows.append([float(v) for v in feat]+[float(local[0]),float(local[1])])
    if len(rows)<10:raise ValueError("insufficient contiguous 64-frame windows")
    return rows

def create(manifest,out):
    cfg=json.loads(Path(manifest).read_text())
    entries=cfg.get("sequences",[])
    if not isinstance(entries,list) or not entries:raise ValueError("empty sequence manifest")
    output=Path(out);output.parent.mkdir(parents=True,exist_ok=True)
    if output.exists():raise FileExistsError("refuse overwrite")
    columns=["sequence_id","person_id","split"]+["f%02d"%i for i in range(24)]+["dx_device_m","dy_device_m"]
    seen=set();rows=0;metadata=[]
    with output.open("w",newline="",encoding="utf-8") as f:
        writer=csv.writer(f);writer.writerow(columns)
        try:
            for item in entries:
                a,b=Path(item["imu"]),Path(item["truth"])
                person,split=item["person_id"],item["split"]
                if not person or split not in ("train","val","test"):
                    raise ValueError("invalid subject or split")
                key=str(a.resolve())
                if key in seen:raise ValueError("repeated source sequence")
                seen.add(key)
                example=sequence_features(a,b)
                for features_and_label in example:
                    writer.writerow([a.stem+"_"+a.parent.parent.name,person,split,
                                     *["%.9g"%v for v in features_and_label]])
                metadata.append({"subject":person,"split":split,"imu":str(a),"truth":str(b),
                                 "windows":len(example)})
                rows+=len(example)
        except Exception:
            output.unlink(missing_ok=True)
            raise
    return {"windows":rows,"sequence_stats":metadata,"schema":"PDR_AI_MLP_V1",
            "input_features":"exact tiny_carry_train.features same as InertialWindow.java",
            "expected_frames":"DEVICE_XY_METRES",
            "truth_is_oxiod_optical":True}

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest",required=True)
    parser.add_argument("--out",required=True)
    options=parser.parse_args();print(json.dumps(create(options.manifest,options.out),indent=2))
