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
