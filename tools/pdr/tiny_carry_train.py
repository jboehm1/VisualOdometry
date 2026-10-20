#!/usr/bin/env python3
"""Train a genuinely lightweight real-IMU carrying-position DIAGNOSTIC model.

This classifies which SIMUL synchronized body sensor was used, NOT phone
orientation, gait events, position, or pedometry. Human-disjoint subjects:
train 08/17/24/25/12/31, validation 10/26, reserved untouched test 04/29.
Original SIMUL raw data are downloaded from SHA-pinned public sources;
license CC-BY-SA-4.0. Do not retune on the reserved test scores.

Java feature contract: InertialWindow.features (64x6->24 float),
MLP 24->16 ReLU->3 softmax. Written bytes use Java DataInputStream binary.
"""
from __future__ import annotations
import argparse
import base64
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import struct
import urllib.request

import numpy as np

SIMUL_SHA="ee7eb4e89bf33a17cafb4268207b802773427493"
SOURCES={
    "08":"data/by-person/8/awindaRecording_20230424133253.csv",
    "17":"data/by-person/17/awindaRecording_20230403173917.csv",
    "24":"data/by-person/24/awindaRecording_20230506184711.csv",
    "25":"data/by-person/25/awindaRecording_20230124150358.csv",
    "12":"data/by-person/12/awindaRecording_20230124153538.csv",
    "31":"data/by-person/31/awindaRecording_20230403142813.csv",
    "10":"data/by-person/10/awindaRecording_20230425130223.csv",
    "26":"data/by-person/26/awindaRecording_20230418172523.csv",
    "04":"data/by-person/4/awindaRecording_20230501194251.csv",
    "29":"data/by-person/29/awindaRecording_20230425114141.csv",
}
TRAIN=("08","17","24","25","12","31")
VALID=("10","26")
FROZEN=("04","29")
POS=(3,5,6)
POSITION_NAMES=("front_pocket_sensor","hand_sensor","back_pocket_sensor")
NFEATURES=24;HIDDEN=16;NOUT=3
FRAMES=64;HOP=16
MAX_SOURCE_BYTES=40_000_000

def fetch(subject,destination):
    path=SOURCES[subject]
    url=f"https://raw.githubusercontent.com/simpleLoc/SIMUL-dataset/{SIMUL_SHA}/{path}"
    req=urllib.request.Request(url,headers={"User-Agent":"VECTOR-PDR-tiny-carry/1"})
    digest=hashlib.sha256()
    count=0
    with urllib.request.urlopen(req,timeout=90) as resp, destination.open("wb") as out:
        if not resp.geturl().startswith("https://raw.githubusercontent.com/"):
            raise ValueError("untrusted redirected origin")
        while block:=resp.read(262144):
            count+=len(block)
            if count>MAX_SOURCE_BYTES:raise ValueError("source too large")
            digest.update(block);out.write(block)
    if count<30:raise ValueError("empty source")
    return {"source":path,"sha256":digest.hexdigest(),"bytes":count}

def parse(path):
    channels={i:[] for i in POS}
    timestamps=[]
    with path.open(newline="",encoding="utf-8-sig") as handle:
        reader=csv.reader(handle);header=next(reader)
        if len(header)!=98 or header[0].strip()!="Timestamp" or header[1].strip()!="Activity":
            raise ValueError("unexpected SIMUL header")
        for row in reader:
            if len(row)!=98:raise ValueError("unexpected SIMUL row")
            ts=float(row[0])
            if not math.isfinite(ts) or (timestamps and ts<=timestamps[-1]):
                raise ValueError("invalid source times")
            timestamps.append(ts)
            for i in POS:
                start=2+16*(i-1)
                raw=[float(v) for v in row[start:start+3]+row[start+6:start+9]]
                if not all(map(math.isfinite,raw)):raise ValueError("nonfinite sensor data")
                channels[i].append(raw)
    return np.asarray(timestamps,dtype=np.float64),{
        i:np.asarray(channels[i],dtype=np.float32) for i in POS}

def features(windows):
    """Vectorized port of exact Java 24-feature InertialWindow.features contract."""
    if windows.ndim!=3 or windows.shape[1:]!=(FRAMES,6):
        raise ValueError("wrong frame shape")
    d=windows.astype(np.float64)
    means=d.mean(axis=1)
    std=np.sqrt(np.maximum(0,np.mean(d*d,axis=1)-means*means))
    firstlast=d[:,-1,:]-d[:,0,:]
    norm=np.stack((np.linalg.norm(d[:,:,:3],axis=2),
                   np.linalg.norm(d[:,:,3:],axis=2)),axis=2)
    normmean=norm.mean(axis=1)
    normstd=np.sqrt(np.maximum(0,(norm*norm).mean(axis=1)-normmean*normmean))
    diff=d[:,1:,:]-d[:,:-1,:]
    jerk=np.stack((np.sqrt(np.mean(np.sum(diff[:,:,:3]**2,axis=2),axis=1)),
                   np.sqrt(np.mean(np.sum(diff[:,:,3:]**2,axis=2),axis=1))),axis=1)
    result=np.concatenate((means,std,firstlast,normmean,normstd,jerk),axis=1)
    if result.shape[1]!=NFEATURES or not np.all(np.isfinite(result)):
        raise ValueError("invalid features")
    return result.astype(np.float32)

def windows_from_one(times,channels):
    # Every other reading: source is typically ~100 Hz, target is <=50 Hz;
    # reject any actual window with non-continuous/implausible timestamps.
    data=channels[::2];ts=times[::2]
    windows=[]
    for end in range(FRAMES,len(data)+1,HOP):
        begin=end-FRAMES
        part=ts[begin:end]
        intervals=np.diff(part)
        if len(intervals)==0 or np.min(intervals)<.014 or np.max(intervals)>.055:
            continue
        windows.append(data[begin:end])
    if not windows:raise ValueError("no usable contiguous windows")
    return features(np.stack(windows))

def dataset(cache,subjects):
    feats=[];classes=[];identities=[];sources=[]
    for subject in subjects:
        original=cache/f"{subject}.csv"
        info=fetch(subject,original)
        times,sensors=parse(original)
        for idx,position in enumerate(POS):
            vec=windows_from_one(times,sensors[position])
            feats.append(vec)
            classes.append(np.full(len(vec),idx,dtype=np.int64))
            identities.extend([subject]*len(vec))
        sources.append({"subject":subject,**info})
        original.unlink()  # bounded workspace: no raw data in artifacts
    return np.concatenate(feats),np.concatenate(classes),identities,sources

def softmax(y):
    y=y-y.max(axis=1,keepdims=True)
    z=np.exp(y)
    return z/z.sum(axis=1,keepdims=True)

def infer(x,weights):
    w1,b1,w2,b2=weights
    return softmax(np.maximum(0,x@w1+b1)@w2+b2)

def training(x,y,x_valid,y_valid,epochs=80):
    rng=np.random.default_rng(20261019)
    w1=rng.normal(0,.14,(NFEATURES,HIDDEN)).astype(np.float64)
    b1=np.zeros(HIDDEN)
    w2=rng.normal(0,.14,(HIDDEN,NOUT)).astype(np.float64)
    b2=np.zeros(NOUT)
    ws=[w1,b1,w2,b2]
    m=[np.zeros_like(w) for w in ws];v=[np.zeros_like(w) for w in ws]
    epoch_best=float("inf");best=None;patience=0;step=0
    for epoch in range(epochs):
        for perm in np.array_split(rng.permutation(len(x)), max(1,math.ceil(len(x)/192))):
            xx=x[perm];yy=y[perm];n=len(perm)
            h=np.maximum(0,xx@ws[0]+ws[1])
            p=softmax(h@ws[2]+ws[3])
            dz=p;dz[np.arange(n),yy]-=1;dz/=n
            dw2=h.T@dz+.0008*ws[2]
            db2=dz.sum(axis=0)
            dh=(dz@ws[2].T)*(h>0)
            dw1=xx.T@dh+.0008*ws[0]
            db1=dh.sum(axis=0)
            step+=1
            for i,g in enumerate((dw1,db1,dw2,db2)):
                np.clip(g,-2,2,out=g)
                m[i]=.9*m[i]+.1*g
                v[i]=.999*v[i]+.001*g*g
                ws[i]-=.003*(m[i]/(1-.9**step))/(np.sqrt(v[i]/(1-.999**step))+1e-8)
        pred=infer(x_valid,ws)
        loss=-np.mean(np.log(pred[np.arange(len(y_valid)),y_valid]+1e-9))
        if loss<epoch_best-0.0002:
            epoch_best=loss;best=[u.copy() for u in ws];patience=0
        else:patience+=1
        if patience>=12:break
    return best,{"epochs_completed":epoch+1,"validation_cross_entropy":round(epoch_best,6)}

def evaluation(x,y,weights):
    probs=infer(x,weights)
    pred=probs.argmax(axis=1)
    conf=np.zeros((NOUT,NOUT),dtype=int)
    for actual,estimate in zip(y,pred):conf[int(actual),int(estimate)]+=1
    recall=[float(conf[i,i]/conf[i].sum()) if conf[i].sum() else 0 for i in range(NOUT)]
    precision=[float(conf[i,i]/conf[:,i].sum()) if conf[:,i].sum() else 0 for i in range(NOUT)]
    f1=[2*a*b/(a+b) if a+b else 0 for a,b in zip(recall,precision)]
    return {"windows":len(y),"confusion":conf.tolist(),"accuracy":round(float((pred==y).mean()),6),
            "balanced_accuracy":round(float(np.mean(recall)),6),
            "macro_f1":round(float(np.mean(f1)),6),
            "per_class_recall":[round(z,6) for z in recall]}

def write_utf(text):
    encoded=text.encode("ascii")
    return struct.pack(">H",len(encoded))+encoded

def export_model(path,mu,std,weights):
    w1,b1,w2,b2=weights
    data=b"".join((write_utf("PDR_TINY_CARRY_V1"),
         write_utf("simul-carry-proxy-20261019"),
         write_utf("CC-BY-SA-4.0"),
         write_utf("SIMUL@"+SIMUL_SHA),
         write_utf("SENSOR_PLACEMENT_PROXY_3_CLASSES"),
         struct.pack(">iii",NFEATURES,HIDDEN,NOUT),
         mu.astype(">f4").tobytes(),
         std.astype(">f4").tobytes(),
         w1.T.astype(">f4").tobytes(), b1.astype(">f4").tobytes(),
         w2.T.astype(">f4").tobytes(), b2.astype(">f4").tobytes()))
    path.write_bytes(data)
    return {"sha256":hashlib.sha256(data).hexdigest(),"bytes":len(data),
            "multiply_adds_per_inference":NFEATURES*HIDDEN+HIDDEN*NOUT,
            "weight_parameters":NFEATURES*HIDDEN+HIDDEN+HIDDEN*NOUT+NOUT,
            "window_float_bytes":FRAMES*6*4}

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--out",type=Path,required=True)
    args=p.parse_args()
    args.out.mkdir(parents=True,exist_ok=True)
    # Download and process train and validation first. Training code never
    # reads new subjects 04/29 until weights and selection are frozen.
    x,y,train_ids,train_provenance=dataset(args.out,TRAIN)
    xv,yv,_,valid_provenance=dataset(args.out,VALID)
    mean=x.astype(np.float64).mean(axis=0)
    scale=np.maximum(x.astype(np.float64).std(axis=0),.02)
    x_norm=(x.astype(np.float64)-mean)/scale
    valid_norm=(xv.astype(np.float64)-mean)/scale
    weights,meta=training(x_norm,y,valid_norm,yv)
    model_path=args.out/"carry-model-v1.bin"
    info=export_model(model_path,mean,scale,weights)
    train_result=evaluation(x_norm,y,weights)
    val_result=evaluation(valid_norm,yv,weights)
    # *** New participant test is conducted once, after the checkpoint is fixed. ***
    xt,yt,_,frozen_provenance=dataset(args.out,FROZEN)
    final_result=evaluation((xt.astype(np.float64)-mean)/scale,yt,weights)
    report={"task":"SIMUL_BODY_SENSOR_PLACEMENT_PROXY_DIAGNOSTIC_NOT_STEP_OR_ODOMETRY",
            "source_commit":SIMUL_SHA,"license":"CC-BY-SA-4.0",
            "feature_schema":"Java InertialWindow.features v1: 64x6->24",
            "classes":POSITION_NAMES,"train_subjects":TRAIN,"validation_subjects":VALID,
            "frozen_test_subjects":FROZEN,"training":meta,
            "train_result":train_result,"validation_result":val_result,
            "frozen_result":final_result,
            "provenance":{"train":train_provenance,"validation":valid_provenance,
                           "frozen":frozen_provenance},
            "model":info,
            "limits":["SIMUL body-worn simultaneous sensors, NOT different Android phone placements",
                      "not navigation position, metric stride or heading",
                      "no evidence of real S21 performance",
                      "frozen test has now been inspected, never use again for tuning",
                      "model carries CC-BY-SA-4.0 attribution requirements"]}
    (args.out/"training_report.json").write_text(json.dumps(report,indent=2)+"\n")
    print("TINYML_REPORT "+json.dumps({
        "train":train_result,"validation":val_result,"frozen":final_result,
        "model":info,"runtime_mac_per_2Hz":2*info["multiply_adds_per_inference"]},sort_keys=True),flush=True)
    # Text-encoded checkpoint for the optional GitHub-native source commit
    # ONLY after a reviewed measured result; no automatic release fusion.
    print("TINYML_CHECKPOINT_BASE64 "+base64.b64encode(model_path.read_bytes()).decode("ascii"),flush=True)

if __name__=="__main__":main()
