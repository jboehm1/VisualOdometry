#!/usr/bin/env python3
"""Train a strictly explicit 24->32->4 portable PDR research model.

CSV must contain independent supervised labels as DEVICE_XY_METRES over 64-frame
causal windows, never absolute geographic/world positions or a GT-derived yaw
available only on the held-out test. Columns: sequence_id, person_id, split,
f00..f23, dx_device_m, dy_device_m. Split is exactly train/val/test; no subject
may occur in multiple splits. Inference reads only f00..f23.

Produces Java DataInputStream-compatible PDR_AI_MLP_V1 checkpoint. The learned
sigma outputs are regression diagnostics, NOT empirically calibrated covariance.
This does not rebrand RoNIN/TLIO checkpoints or claim equivalent accuracy.
"""
import argparse,csv,hashlib,json,math,struct
from collections import defaultdict
from pathlib import Path

COLUMNS=["f%02d"%i for i in range(24)]
def read_data(path):
    out=defaultdict(list);people=defaultdict(set);sequence_splits=defaultdict(set)
    with Path(path).open(newline="",encoding="utf-8") as handle:
        reader=csv.DictReader(handle)
        mandatory={"sequence_id","person_id","split","dx_device_m","dy_device_m",*COLUMNS}
        if reader.fieldnames is None or not mandatory.issubset(reader.fieldnames):
            raise ValueError("missing required columns")
        for index,row in enumerate(reader):
            split=row["split"];person=row["person_id"];seq=row["sequence_id"]
            if split not in {"train","val","test"} or not person or not seq:
                raise ValueError("invalid split/subject/sequence")
            sequence_splits[seq].add(split);people[split].add(person)
            values=[float(row[k]) for k in COLUMNS+["dx_device_m","dy_device_m"]]
            if not all(math.isfinite(v) for v in values) or any(abs(v)>1_000 for v in values):
                raise ValueError("invalid feature/label values")
            out[split].append(values)
    if any(len(out[s])<10 for s in ("train","val","test")):
        raise ValueError("need >= 10 windows on each of train, val, test")
    if len(set.union(*people.values()))!=sum(map(len,people.values())):
        raise ValueError("person leaked across train/val/test")
    if any(len(v)!=1 for v in sequence_splits.values()):
        raise ValueError("sequence leaked across train/val/test")
    return out,people,sequence_splits

def fit(data,epochs=130,seed=21):
    import numpy as np
    np.random.seed(seed)
    arr={s:np.asarray(data[s],dtype=np.float64) for s in ("train","val","test")}
    mu=arr["train"][:,:24].mean(0);sigma=arr["train"][:,:24].std(0)
    sigma=np.maximum(sigma,1e-4)
    xs={s:np.clip((a[:,:24]-mu)/sigma,-12,12) for s,a in arr.items()}
    ys={s:a[:,24:26] for s,a in arr.items()}
    w1=np.random.randn(24,32)*math.sqrt(2/24)
    b1=np.zeros(32);w2=np.random.randn(32,4)*math.sqrt(1/32)
    b2=np.array([0.,0.,math.log(.5),math.log(.5)])
    params=[w1,b1,w2,b2]
    adam_m=[np.zeros_like(p) for p in params]
    adam_v=[np.zeros_like(p) for p in params]
    best=None;best_val=float("inf");bad=0;timestep=0
    def forward(x):
        h=np.maximum(0,x@params[0]+params[1])
        out=h@params[2]+params[3]
        return h,out
    def metric(split):
        _,v=forward(xs[split]);return float(np.mean(np.sum((v[:,:2]-ys[split])**2,axis=1)))
    for epoch in range(epochs):
        indices=np.random.permutation(len(xs["train"]))
        for batch_idx in np.array_split(indices,max(1,math.ceil(len(indices)/128))):
            if len(batch_idx)==0:continue
            x=xs["train"][batch_idx];target=ys["train"][batch_idx]
            hidden,y=forward(x);sigma_logits=np.clip(y[:,2:],-5,3)
            residual=y[:,:2]-target
            inv_var=np.exp(-2*sigma_logits)
            # Gradient of gaussian negative log-likelihood.
            gy=np.zeros_like(y);gy[:,:2]=residual*inv_var
            gy[:,2:]=1-residual**2*inv_var
            gy/=len(batch_idx)
            gw2=hidden.T@gy+0.0001*params[2];gb2=gy.sum(0)
            gh=(gy@params[2].T)*(hidden>0)
            gw1=x.T@gh+0.0001*params[0];gb1=gh.sum(0)
            grads=[gw1,gb1,gw2,gb2]
            total=math.sqrt(sum(float(np.sum(g*g)) for g in grads))
            if total>10:grads=[g*(10/total) for g in grads]
            timestep+=1
            for j,(p,g) in enumerate(zip(params,grads)):
                adam_m[j]=.9*adam_m[j]+.1*g
                adam_v[j]=.999*adam_v[j]+.001*(g*g)
                p-=.001*(adam_m[j]/(1-.9**timestep))/(np.sqrt(adam_v[j]/(1-.999**timestep))+1e-8)
        val=metric("val")
        if val<best_val-1e-8:
            best_val=val;best=[p.copy() for p in params];bad=0
        else:bad+=1
        if bad>20:break
    params=best;w1,b1,w2,b2=params
    scores={}
    for split in ("train","val","test"):
        pred=np.maximum(0,xs[split]@w1+b1)@w2+b2
        error=np.linalg.norm(pred[:,:2]-ys[split],axis=1)
        baseline=np.linalg.norm(ys[split],axis=1)
        scores[split]={"n":int(len(error)),"rmse_vector_m":float(np.sqrt(np.mean(error**2))),
                       "zero_delta_rmse_m":float(np.sqrt(np.mean(baseline**2)))}
    return mu,sigma,w1,b1,w2,b2,scores

def utf(s):
    data=s.encode("utf-8")
    if len(data)>65535 or "\x00" in s:raise ValueError("invalid UTF field")
    return struct.pack(">H",len(data))+data

def save_checkpoint(target,model_id,license_id,dataset_id,arrays):
    if not all((s and len(s)<=100) for s in (model_id,license_id,dataset_id)):
        raise ValueError("provenance required")
    mu,sigma,w1,b1,w2,b2=arrays
    parts=[utf("PDR_AI_MLP_V1"),utf(model_id),utf(license_id),utf(dataset_id),
           utf("DEVICE_XY_METRES"),struct.pack(">iii",24,32,4)]
    # Java's storage W1 is hidden x feature; numpy uses feature x hidden.
    import numpy as np
    for arr in (mu,sigma,w1.T,b1,w2.T,b2):
        value=np.asarray(arr,dtype=">f4").ravel(order="C")
        if not np.all(np.isfinite(value)):raise ValueError("nonfinite model")
        parts.append(value.tobytes())
    binary=b"".join(parts)
    if len(binary)>=100_000:raise ValueError("model too large")
    path=Path(target);path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(binary)
    return {"bytes":len(binary),"sha256":hashlib.sha256(binary).hexdigest(),"path":str(path)}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--csv",type=Path,required=True)
    parser.add_argument("--out",type=Path,required=True)
    parser.add_argument("--id",required=True)
    parser.add_argument("--license",required=True,help="SPDX/research license of training samples")
    parser.add_argument("--dataset",required=True)
    opts=parser.parse_args()
    data,subjects,sequences=read_data(opts.csv)
    trained=fit(data)
    ck=save_checkpoint(opts.out,opts.id,opts.license,opts.dataset,trained[:6])
    result={"schema":"PDR_AI_MLP_V1","output_frame":"DEVICE_XY_METRES",
            "input_sha256":hashlib.sha256(opts.csv.read_bytes()).hexdigest(),
            "splits":{k:sorted(v) for k,v in subjects.items()},
            "sequence_counts":{s:sum(s in splits for splits in sequences.values())
                               for s in ("train","val","test")},
            "holdout_warning":"Research-only. Test split evaluated exactly once; do not retune on test.",
            "not_a_calibrated_covariance":True,
            "scores":trained[6],"checkpoint":ck}
    report=opts.out.with_suffix(".json");report.write_text(json.dumps(result,indent=2)+"\n")
    print(json.dumps(result,sort_keys=True))
if __name__=="__main__":main()
