#!/usr/bin/env python3
"""Generic research-dataset adapter for accelerometer-only distance benchmark.

The source's axes, units, timestamp scale and ground-truth independence must
be verified by the operator. No fabricated heading or Android step sensor
events. Each acceleration row maps to ACCEL in PdrReplay.
"""
import argparse
import csv
import math
from pathlib import Path

def convert(source, output, t_col, xyz, time_unit="s", accel_unit="mps2", skip=0):
    scales = {"s": 1e9, "ms": 1e6, "us": 1e3, "ns": 1}
    if time_unit not in scales: raise ValueError("time unit")
    if accel_unit not in {"mps2", "g"}: raise ValueError("accel unit")
    with open(source, newline="", encoding="utf-8-sig") as s, open(output,"w",newline="",encoding="utf8") as o:
        reader=csv.reader(s); writer=csv.writer(o)
        writer.writerow(["t_ns","type","x","y","z"])
        for _ in range(skip):next(reader,None)
        first=None;last=-1;written=0
        for i,row in enumerate(reader):
            if not row or row[0].lstrip().startswith("#"):continue
            try:
                raw_t=float(row[t_col])
                values=[float(row[index])*(9.80665 if accel_unit=="g" else 1.)
                        for index in xyz]
            except (ValueError,IndexError) as exc:
                raise ValueError(f"Invalid accelerometer at row {i+1+skip}: {row}") from exc
            if not all(math.isfinite(x) for x in [raw_t,*values]):
                raise ValueError(f"Nonfinite at row {i}")
            if first is None:first=raw_t
            t=1+round((raw_t-first)*scales[time_unit])
            if t<=last:raise ValueError(f"Duplicate/out of order timestamp at row {i}")
            writer.writerow([t,"ACCEL",*values]);last=t;written+=1
        if written==0:raise ValueError("No rows converted")
        return written

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("source",type=Path)
    p.add_argument("output",type=Path)
    p.add_argument("--time-column",type=int,default=0)
    p.add_argument("--xyz-columns",nargs=3,type=int,default=[1,2,3])
    p.add_argument("--time-unit",choices=["s","ms","us","ns"],default="s")
    p.add_argument("--accel-unit",choices=["g","mps2"],default="mps2")
    p.add_argument("--skip",type=int,default=0,help="CSV header lines, if any")
    a=p.parse_args();a.output.parent.mkdir(parents=True,exist_ok=True)
    n=convert(a.source,a.output,a.time_column,a.xyz_columns,a.time_unit,a.accel_unit,a.skip)
    print(f"Converted {n} acceleration samples. NO heading or step labels added.")
if __name__=="__main__":main()
