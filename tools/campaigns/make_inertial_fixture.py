#!/usr/bin/env python3
"""Generate a labeled SYNTHETIC walking-only inertial fixture, not GT evidence."""
import argparse
import csv
import json
import math
from pathlib import Path


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--out",type=Path,required=True)
    a=ap.parse_args()
    a.out.mkdir(parents=True,exist_ok=True)
    dest=a.out/"imu.csv"
    with dest.open("w",newline="",encoding="utf-8") as f:
        w=csv.writer(f)
        w.writerow(["t_ns","type","x","y","z"])
        for i in range(1500):
            t=1_700_000_000_000_000_000+i*10_000_000
            elapsed=i*0.01
            mag=9.80665+2.3*math.sin(elapsed*2*math.pi/0.65)
            w.writerow([t,"GYRO","0","0",format(0.04 if elapsed>=7 else 0,".8f")])
            w.writerow([t,"ACCEL","0","0",format(mag,".8f")])
    (a.out/"fixture.json").write_text(json.dumps({
        "type":"SYNTHETIC", "source":"programmatically generated",
        "duration_s":15,"imu_hz":100,"camera_images":0,
        "motions":"gait-period-0.65s + synthetic yaw rate in second half",
        "ground_truth":None,
        "caution":"NEVER cite this as real-world IMU accuracy"
    },indent=2)+"\n")
    print("Generated synthetic IMU-only fixture:",dest)


if __name__=="__main__":main()
