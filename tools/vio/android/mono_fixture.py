"""Stage real EuRoC cam0+IMU for Android JNI replay; never reads reference poses.

Rectify with the published camera calibration because this adapter currently
supports a pinhole model. No exposure measurement is present: use explicit zero
unknown exposure, not an invented shutter time. Fixture stays on the CI runner.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import struct
import cv2
import numpy as np
import yaml


def prepare(source, output, count=400):
    mav=source/'mav0' if (source/'mav0').is_dir() else source
    cam=yaml.safe_load((mav/'cam0/sensor.yaml').read_text())
    imu=yaml.safe_load((mav/'imu0/sensor.yaml').read_text())
    if cam['camera_model']!='pinhole' or cam['distortion_model']!='radial-tangential':
        raise ValueError('Unsupported real camera model')
    width,height=map(int,cam['resolution'])
    k=np.array([[cam['intrinsics'][0],0,cam['intrinsics'][2]],
                [0,cam['intrinsics'][1],cam['intrinsics'][3]],[0,0,1]],dtype=np.float64)
    transform=np.linalg.inv(np.array(imu['T_BS']['data']).reshape(4,4))@np.array(cam['T_BS']['data']).reshape(4,4)
    p=[width,height,*cam['intrinsics'],*transform.ravel(),imu['gyroscope_noise_density'],
       imu['accelerometer_noise_density'],imu['gyroscope_random_walk'],imu['accelerometer_random_walk'],imu['rate_hz']]
    if len(p)!=27 or not np.isfinite(p).all():raise ValueError('Invalid published calibration')
    frames=[]
    with (mav/'cam0/data.csv').open(newline='') as f:
        for row in csv.reader(f):
            if row and not row[0].startswith('#'):
                t=int(row[0]);name=row[1].strip()
                if Path(name).name!=name:raise ValueError('Unsafe camera path')
                frames.append((t,mav/'cam0/data'/name))
                if len(frames)==count:break
    if len(frames)!=count:raise ValueError('Incomplete real window')
    if any(a[0]>=b[0] for a,b in zip(frames,frames[1:])):raise ValueError('Camera order')
    samples=[]
    with (mav/'imu0/data.csv').open(newline='') as f:
        for row in csv.reader(f):
            if not row or row[0].startswith('#'):continue
            t=int(row[0]);v=list(map(float,row[1:]));
            if frames[0][0]-50_000_000<=t<=frames[-1][0]+50_000_000:samples.append((t,[*v[3:],*v[:3]]))
    if not samples or samples[0][0]>=frames[0][0] or samples[-1][0]<=frames[-1][0]:raise ValueError('IMU bracket')
    if any(a[0]>=b[0] for a,b in zip(samples,samples[1:])):raise ValueError('IMU order')
    output.parent.mkdir(parents=True,exist_ok=True)
    maps=cv2.initUndistortRectifyMap(k,np.array(cam['distortion_coefficients']),np.eye(3),k,(width,height),cv2.CV_32FC1)
    with output.open('wb') as f:
        f.write(b'VBMONO1\n');f.write(struct.pack('>iii27di',width,height,len(samples),*p,len(frames)))
        for t,v in samples:f.write(struct.pack('>q6d',t,*v))
        for t,path in frames:
            image=cv2.imread(str(path),cv2.IMREAD_GRAYSCALE)
            if image is None or image.shape!=(height,width):raise ValueError('Invalid real image')
            rectified=cv2.remap(image,*maps,cv2.INTER_LINEAR)
            f.write(struct.pack('>q',t));f.write(rectified.tobytes())
    return {'dataset':'EuRoC V1_01 cam0 mono', 'frames':len(frames),'imu_samples':len(samples),
            'fixture_sha256':hashlib.sha256(output.read_bytes()).hexdigest(),
            'truth_read':False,'undistortion':'published radial-tangential calibration, same pinhole K',
            'exposure':'UNAVAILABLE_ZERO_SENTINEL_NOT_MEASURED','purpose':'Android JNI inference smoke, not phone accuracy or ARCore comparison'}


if __name__=='__main__':
    a=argparse.ArgumentParser();a.add_argument('--source',type=Path,required=True);a.add_argument('--out',type=Path,required=True)
    a.add_argument('--receipt',type=Path,required=True);args=a.parse_args()
    receipt=prepare(args.source,args.out);args.receipt.parent.mkdir(parents=True,exist_ok=True)
    args.receipt.write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt))
