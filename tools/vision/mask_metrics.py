#!/usr/bin/env python3
"""Offline segmentation mask scoring; evaluates predictions, does NOT run a model.

Requires Pillow only when loading actual image masks. Pure standard-library
metric functions are exercised in CI. Training/truth files remain local.
"""
import argparse
import json
import math
from pathlib import Path


def score_pair(truth, prediction, positive_truth=(1,), positive_prediction=(1,), ignore=()):
    if len(truth)!=len(prediction) or len(truth)==0:
        raise ValueError("Masks must have equal nonzero pixel count")
    truth_ids=set(positive_truth);prediction_ids=set(positive_prediction);ignore_ids=set(ignore)
    tp=fp=tn=fn=ignored=0
    for gt,pred in zip(truth,prediction):
        if gt in ignore_ids:
            ignored+=1;continue
        is_gt=gt in truth_ids;is_pred=pred in prediction_ids
        if is_gt and is_pred:tp+=1
        elif is_gt:fn+=1
        elif is_pred:fp+=1
        else:tn+=1
    coverage=(len(truth)-ignored)/len(truth)
    return {"true_positive":tp,"false_positive":fp,"true_negative":tn,
            "false_negative":fn,"ignored":ignored,"evaluated_pixels":tp+fp+tn+fn,
            "coverage_fraction":round(coverage,6),
            "precision":round(tp/(tp+fp),6) if tp+fp else None,
            "recall":round(tp/(tp+fn),6) if tp+fn else None,
            "iou":round(tp/(tp+fp+fn),6) if tp+fp+fn else None,
            "f1":round(2*tp/(2*tp+fp+fn),6) if 2*tp+fp+fn else None}


def evaluate_manifest(path):
    """Manifest may reference only files under its own directory, no network."""
    base=Path(path).resolve().parent
    obj=json.loads(Path(path).read_text(encoding="utf-8"))
    entries=obj.get("entries")
    if not isinstance(entries,list) or not entries or len(entries)>4000:
        raise ValueError("Manifest must contain 1–4000 labelled mask pairs")
    if not isinstance(obj.get("dataset"),str) or not obj["dataset"].strip():
        raise ValueError("Dataset provenance name required")
    try:
        from PIL import Image
    except ImportError as exc:
        raise RuntimeError("Install Pillow to evaluate local PNG masks: pip install pillow") from exc
    total={"true_positive":0,"false_positive":0,"true_negative":0,"false_negative":0,"ignored":0}
    per_image=[]
    for item in entries:
        def safe_file(key):
            filename=item[key]
            if not isinstance(filename,str):raise ValueError("Invalid filename")
            source=(base/filename).resolve()
            if not source.is_relative_to(base) or not source.is_file():
                raise ValueError("Mask path is missing or outside manifest directory")
            return source
        with Image.open(safe_file("truth")) as t, Image.open(safe_file("prediction")) as p:
            if t.mode not in {"L","P","I","I;16"} or p.mode not in {"L","P","I","I;16"}:
                raise ValueError("Masks must contain discrete class IDs, not RGB colors")
            if t.size!=p.size or t.width*t.height>20_000_000:
                raise ValueError("Mask size mismatch or exceeds 20MP limit")
            raw=score_pair(list(t.getdata()),list(p.getdata()),
                item.get("positive_truth_ids",[1]),item.get("positive_prediction_ids",[1]),
                item.get("ignore_truth_ids",[]))
            for k in total:total[k]+=raw[k]
            per_image.append({"truth":item["truth"],"prediction":item["prediction"],"iou":raw["iou"],"f1":raw["f1"],"evaluated_pixels":raw["evaluated_pixels"]})
    aggregate=score_pair_from_counts(total)
    return {"status":"MASKS_SCORED_NO_INFERENCE", "dataset":obj["dataset"],
            "images":len(entries),"aggregate":aggregate,"per_image":per_image,
            "limitations":"Masks checked only; model latency/battery, trail topology, real-world walkability and geographic identity NOT measured."}


def score_pair_from_counts(counts):
    tp,fp,tn,fn,ignored=(counts[k] for k in ("true_positive","false_positive","true_negative","false_negative","ignored"))
    return {**counts,"iou":round(tp/(tp+fp+fn),6) if tp+fp+fn else None,
            "precision":round(tp/(tp+fp),6) if tp+fp else None,
            "recall":round(tp/(tp+fn),6) if tp+fn else None,
            "f1":round(2*tp/(2*tp+fp+fn),6) if 2*tp+fp+fn else None,
            "evaluated_pixels":tp+fp+tn+fn}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--manifest",type=Path,required=True)
    p.add_argument("--out",type=Path)
    args=p.parse_args();result=evaluate_manifest(args.manifest)
    encoded=json.dumps(result,indent=2,ensure_ascii=False,sort_keys=True)+"\n"
    if args.out:
        args.out.parent.mkdir(parents=True,exist_ok=True)
        args.out.write_text(encoded,encoding="utf-8")
    print(encoded,end="")

if __name__=="__main__":main()
