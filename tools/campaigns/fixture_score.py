#!/usr/bin/env python3
"""Regression acceptance of PDR output, no accuracy claim on a synthetic fixture."""
import argparse
import json
from pathlib import Path


def main():
    p=argparse.ArgumentParser()
    p.add_argument("--path",type=Path,required=True)
    p.add_argument("--out",type=Path,required=True)
    a=p.parse_args()
    poses=[]
    with a.path.open(encoding="utf-8") as f:
        for row in f:
            parts=row.split()
            if len(parts)!=8:
                raise ValueError("PDR trajectory must be TUM 8-column rows")
            poses.append(tuple(map(float,parts)))
    if len(poses)<500 or not all(b[0]>q[0] for q,b in zip(poses,poses[1:])):
        raise ValueError("Insufficient or unordered camera-off output")
    report=json.loads((a.path.parent/"report.json").read_text())
    steps=report["steps"]
    if not 10<=steps<=35:
        raise ValueError(f"Unexpected number of synthetic gait detections: {steps}")
    if not report["path_m"]>0:
        raise ValueError("Expected synthetic walking, got no path")
    result={
        "camera_dependency":False,
        "synthetic_fixture_passed":True,
        "steps":steps,"algorithm":report["algorithm"],
        "real_world_accuracy":"NOT TESTED",
        "independent_ground_truth":None
    }
    a.out.write_text(json.dumps(result,indent=2)+"\n")
    print(json.dumps(result,indent=2))


if __name__=="__main__":main()
