#!/usr/bin/env python3
"""Experimental monocular visual+gyro baseline on real Mobile-GVIO sensor logs.

Images supply translation directions. Synchronized gyroscope supplies rotation.
Monocular translation scale is NOT metric. Not our Android ARCore estimator.
"""
import argparse,hashlib,json
from pathlib import Path
import numpy as np
import cv2
from rosbags.highlevel import AnyReader

def calibration(file):
    fs=cv2.FileStorage(str(file),cv2.FILE_STORAGE_READ)
    if not fs.isOpened(): raise ValueError("Missing calibrated camera")
    v=lambda k:fs.getNode(k).real()
    K=np.array([[v("Camera1.fx"),0,v("Camera1.cx")],
                [0,v("Camera1.fy"),v("Camera1.cy")],[0,0,1]],float)
    D=np.array([v("Camera1.k1"),v("Camera1.k2"),
                v("Camera1.p1"),v("Camera1.p2")],float)
    T=fs.getNode("IMU.T_b_c1").mat()
    fs.release()
    if T is None or T.shape!=(4,4):raise ValueError("IMU to camera extrinsic missing")
    R=T[:3,:3].astype(float)
    if np.linalg.norm(R.T@R-np.eye(3))>.03:raise ValueError("Bad IMU extrinsic")
    return K,D,R

def read_real_imu_camera(path,seconds=22,skip=8,every=5):
    frames=[];gyro=[];first=None;count=0
    with AnyReader([path]) as reader:
        conn=[c for c in reader.connections if c.topic in ("/cam0/image_raw","/imu0")]
        if len(conn)!=2:raise ValueError("Missing real camera or IMU topic")
        for c,t,b in reader.messages(connections=conn):
            if first is None:first=t
            dt=(t-first)/1e9
            if dt<skip:continue
            if dt>skip+seconds:break
            m=reader.deserialize(b,c.msgtype)
            if c.topic=="/imu0":
                gyro.append((t,np.array([m.angular_velocity.x,m.angular_velocity.y,m.angular_velocity.z],float)))
            else:
                count+=1
                if count%every:continue
                a=np.asarray(m.data,dtype=np.uint8)
                enc=str(m.encoding).lower()
                if enc in ("mono8","8uc1"):
                    image=a.reshape(m.height,m.step)[:,:m.width]
                elif enc in ("bgr8","rgb8"):
                    rgb=a.reshape(m.height,m.step)[:,:m.width*3].reshape(m.height,m.width,3)
                    image=cv2.cvtColor(rgb,cv2.COLOR_BGR2GRAY if enc=="bgr8" else cv2.COLOR_RGB2GRAY)
                else:raise ValueError("Unsupported camera encoding "+enc)
                frames.append((t,cv2.resize(image,None,fx=.5,fy=.5)))
    if len(frames)<25 or len(gyro)<220:raise ValueError("Too few genuine observations")
    return frames,gyro


def infer(bag,calib,out,seconds=22,skip=8):
    K,D,Rbc=calibration(calib)
    K[:2,:]*=.5
    images,gyro=read_real_imu_camera(bag,seconds,skip)
    Rimu=np.eye(3);Rfirst=None;last=None;j=0;used=0
    pos=np.zeros(3);old=None;good=0;bad=0;inliers=0;poses=[]
    for timestamp,image in images:
        while j<len(gyro) and gyro[j][0]<=timestamp:
            ts,w=gyro[j]
            if last is not None:
                dt=(ts-last)*1e-9
                if not 0<dt<.2:raise ValueError("IMU time gap or reversal")
                d,_=cv2.Rodrigues(w*dt)
                Rimu=Rimu@d
                used+=1
            last=ts;j+=1
        if Rfirst is None:Rfirst=Rimu.copy()
        Rcamera=Rbc.T@Rfirst.T@Rimu@Rbc
        if old is not None:
            p=cv2.goodFeaturesToTrack(old,maxCorners=750,qualityLevel=.018,minDistance=7)
            if p is not None and len(p)>30:
                q,mask,_=cv2.calcOpticalFlowPyrLK(old,image,p,None)
                if q is not None and mask is not None:
                    aa=p.reshape(-1,2)[mask.ravel()==1]
                    bb=q.reshape(-1,2)[mask.ravel()==1]
                    if len(aa)>30:
                        aa=cv2.undistortPoints(aa.reshape(-1,1,2),K,D,P=K).reshape(-1,2)
                        bb=cv2.undistortPoints(bb.reshape(-1,1,2),K,D,P=K).reshape(-1,2)
                        E,m=cv2.findEssentialMat(aa,bb,K,method=cv2.RANSAC,threshold=1.5)
                        if E is not None and E.shape==(3,3):
                            n,visR,tvec,_=cv2.recoverPose(E,aa,bb,K,mask=m)
                            if n>=12:
                                pos-=Rcamera@tvec.flatten()
                                good+=1;inliers+=int(n)
                            else:bad+=1
                        else:bad+=1
                    else:bad+=1
                else:bad+=1
            else:bad+=1
        poses.append((timestamp,pos.copy(),Rcamera.copy()))
        old=image
    if good<8 or inliers<160 or used<100:
        raise ValueError("Vision+IMU baseline did not get enough tracked data")
    from scipy.spatial.transform import Rotation
    out.mkdir(parents=True,exist_ok=True)
    poses_file=out/"estimate_unscaled.tum"
    with poses_file.open("w") as dest:
        for t,p,R in poses:
            q=Rotation.from_matrix(R).as_quat()
            dest.write(f"{t*1e-9:.9f} "+" ".join(f"{float(v):.9f}" for v in (*p,*q))+"\n")
    result={
      "engine":"research_monocular_visual_directions_with_gyro_orientation",
      "actual_images_processed":len(images),"raw_imu_gyro_samples":len(gyro),
      "gyro_integrated_samples":used,"visual_essential_pose_updates":good,
      "visual_ransac_inliers":inliers,"visual_rejections":bad,
      "calibration_sha256":hashlib.sha256(Path(calib).read_bytes()).hexdigest(),
      "metric_translation_scale_known":False,
      "camera_images_and_imu_both_influence_pose":True,
      "estimator":"research_baseline_not_ANDROID_ARCore_or_OpenVINS",
      "pose_output":str(poses_file),
    }
    (out/"inference.json").write_text(json.dumps(result,indent=2)+"\n")
    return result

if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--bag",type=Path,required=True)
    p.add_argument("--calib",type=Path,required=True)
    p.add_argument("--out",type=Path,required=True)
    p.add_argument("--seconds",type=float,default=22)
    p.add_argument("--skip",type=float,default=8)
    a=p.parse_args()
    result=infer(a.bag,a.calib,a.out,a.seconds,a.skip)
    print("ACTUAL_PHONE_CAMERA_PLUS_GYRO",json.dumps(result),flush=True)
