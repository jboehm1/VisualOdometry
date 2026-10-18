#!/usr/bin/env python3
"""ADVIO-09: public raw video+gyro audit and INDEPENDENTLY LABELLED external Pixel ARCore score.

Real iPhone camera/gyro are decoded and measured. Position benchmark executes
ONLY on recorded Google Pixel ARCore poses supplied by the authors; not an
estimate from our APK or our raw video + gyro. Truth is post-inference only.
"""
from __future__ import annotations
import argparse,csv,hashlib,json,urllib.request,zipfile
from pathlib import Path
import numpy as np
import cv2

URL="https://zenodo.org/api/records/1476931"
MD5="md5:a5fedb98e2f50954c670e61d04642286"
SIZE=70695364

def read_csv(z,name):
    with z.open("advio-09/"+name) as f:
        return np.asarray([[float(x) for x in row] for row in
                           csv.reader(line.decode("utf8") for line in f)
                           if row],dtype=float)

def download(out):
    out.mkdir(parents=True,exist_ok=True)
    with urllib.request.urlopen(URL,timeout=30) as r: record=json.load(r)
    item=next(f for f in record["files"] if f["key"]=="advio-09.zip")
    if item["checksum"]!=MD5 or item["size"]!=SIZE:
        raise ValueError("ADVIO upstream fingerprint mismatch")
    dest=out/"advio-09.zip";hash_=hashlib.md5();n=0
    with urllib.request.urlopen(item["links"]["self"],timeout=100) as src,dest.open("wb") as f:
        while block:=src.read(1024*1024):
            hash_.update(block);n+=len(block)
            if n>SIZE:raise ValueError("Archive exceeded pinned length")
            f.write(block)
    if n!=SIZE or "md5:"+hash_.hexdigest()!=MD5:
        raise ValueError("Original ADVIO bytes did not match pinned checksum")
    return dest

def read_recording(zip_path,output):
    with zipfile.ZipFile(zip_path) as z:
        gyro=read_csv(z,"iphone/gyro.csv")
        cam=read_csv(z,"iphone/frames.csv")
        reference=read_csv(z,"ground-truth/pose.csv")
        external=read_csv(z,"pixel/arcore.csv")
        for name,a,minimum,cols in [
            ("gyro",gyro,500,4),("frames",cam,300,2),
            ("truth",reference,300,8),("external_arcore",external,50,8)]:
            if len(a)<minimum or a.shape[1]!=cols or not np.isfinite(a).all():
                raise ValueError("Invalid original "+name+" sensor/pose CSV")
            if not np.all(np.diff(a[:,0])>0):raise ValueError(name+" timestamps not increasing")
        with z.open("advio-09/iphone/frames.mov") as src:
            video=output/"frames.mov"
            with video.open("wb") as dst:
                while block:=src.read(1024*1024):dst.write(block)
    return gyro,cam,reference,external,video

def audit_camera_and_gyro(video,frame_t,gyro):
    capture=cv2.VideoCapture(str(video))
    if not capture.isOpened():raise ValueError("Recorded 60 FPS camera video cannot be decoded")
    previous=None;decoded=0;with_features=0;tracked=0
    while decoded<500:
        ok,image=capture.read()
        if not ok:break
        if decoded%15==0:
            gray=cv2.cvtColor(cv2.resize(image,(360,640)),cv2.COLOR_BGR2GRAY)
            if previous is not None:
                pts=cv2.goodFeaturesToTrack(previous,400,.015,8)
                if pts is not None and len(pts)>=20:
                    _,mask,_=cv2.calcOpticalFlowPyrLK(previous,gray,pts,None)
                    n=int(np.count_nonzero(mask)) if mask is not None else 0
                    if n>=15:
                        with_features+=1;tracked+=n
            previous=gray
        decoded+=1
    capture.release()
    if decoded<100 or with_features<4 or tracked<100:
        raise ValueError("Raw camera feature tracking failed")
    end=float(frame_t[min(decoded-1,len(frame_t)-1),0])
    present=gyro[(gyro[:,0]>=frame_t[0,0]) & (gyro[:,0]<=end)]
    if len(present)<100:raise ValueError("Raw gyroscope is not synchronized with camera")
    dts=np.diff(present[:,0])
    angular_speed=np.linalg.norm(present[:-1,1:4],axis=1)
    gyro_radians=float(np.sum(dts*angular_speed))
    return {"raw_video_frames_decoded":decoded,
            "raw_camera_feature_pairs":with_features,
            "tracked_optical_flow_features":tracked,
            "real_gyro_samples_in_camera_window":len(present),
            "integrated_gyro_rotation_magnitude_rad":gyro_radians,
            "raw_camera_and_gyro_processed":True,
            "estimated_pose_from_these_raw_signals":False}


def evaluate_external_arcore(arcore,gt):
    X=[];Y=[]
    for row in arcore:
        j=np.searchsorted(gt[:,0],row[0])
        if not 0<j<len(gt):continue
        left,right=gt[j-1],gt[j]
        dt=right[0]-left[0]
        if not 0<dt<.16:continue
        fraction=(row[0]-left[0])/dt
        X.append(row[1:4]);Y.append(left[1:4]*(1-fraction)+right[1:4]*fraction)
    if len(X)<40:raise ValueError("No independently matched external ARCore reference")
    a=np.asarray(X);b=np.asarray(Y)
    xc=a-a.mean(axis=0);yc=b-b.mean(axis=0)
    U,s,Vt=np.linalg.svd(xc.T@yc)
    R=U@np.diag([1,1,np.linalg.det(U@Vt)])@Vt
    delta=xc@R-yc
    rmse=float(np.sqrt(np.mean(np.sum(delta*delta,axis=1))))
    return {"external_engine":"recorded_Google_Pixel_ARCore",
            "actual_app_VECT0R_estimator_executed":False,
            "matched_external_phone_pose_pairs":len(a),
            "ate_rmse_m_rigid_SE3_no_scale_fitting":rmse,
            "absolute_metric_scale_preserved":True,
            "reference_kind":"ADVIO fixation_aided_inertial_trajectory",
            "truth_independence_note":"Reference was reconstructed from a separate inertial trajectory with visual fixation points; not fully independent optical tracking",
            "not_our_ARCore_accuracy":True}


def main():
    p=argparse.ArgumentParser()
    p.add_argument("--out",type=Path,required=True)
    args=p.parse_args()
    args.out.mkdir(parents=True,exist_ok=True)
    zipped=download(args.out)
    gyro,frames,truth,external,video=read_recording(zipped,args.out)
    camera=audit_camera_and_gyro(video,frames,gyro)
    arcore=evaluate_external_arcore(external,truth)
    report={"status":"PASS","dataset":"ADVIO-09",
            "source_record":"https://doi.org/10.5281/zenodo.1476931",
            "source_zip_md5":MD5,
            "license":"CC BY-NC 4.0",
            "raw_multimodal_audit":camera,
            "external_vision_imu_estimator_score":arcore,
            "ours_java_pdr_not_tested_on_this_sequence":True,
            "external_pose_is_not_our_android_app":True}
    (args.out/"report.json").write_text(json.dumps(report,indent=2)+"\\n")
    # Raw personal video is not published or kept in CI outputs.
    video.unlink(missing_ok=True)
    zipped.unlink(missing_ok=True)
    print("REAL_ADVIO_PHONE_VIDEO_IMU",json.dumps(report),flush=True)


if __name__=="__main__":
    main()
