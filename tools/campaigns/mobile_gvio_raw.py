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
