#!/usr/bin/env python3
"""Make the exact nanosecond timestamp list expected by ORB-SLAM3 EuRoC."""
import argparse
import csv
import json
from pathlib import Path
import re


def make_times(staged: Path, output: Path):
    staged, output = Path(staged), Path(output)
    with (staged / "mav0/cam0/data.csv").open(newline="") as stream:
        rows = list(csv.reader(stream))
    timestamps = []
    for row in rows:
        if not row or row[0].lstrip().startswith("#"):
            continue
        if len(row) != 2:
            raise ValueError("invalid camera timestamp row")
        stamp = int(row[0])
        name = row[1].strip()
        if stamp <= 0 or not re.fullmatch(r"[0-9]+\.png", name) or name != f"{stamp}.png":
            raise ValueError("ORB-SLAM3 EuRoC runner requires timestamp-named PNGs")
        image = staged / "mav0/cam0/data" / name
        if image.is_symlink() or not image.is_file():
            raise ValueError("camera timestamp refers to missing/nonordinary image")
        if timestamps and stamp <= timestamps[-1]:
            raise ValueError("camera timestamps must be strictly increasing")
        timestamps.append(stamp)
    if len(timestamps) < 3:
        raise ValueError("too few camera frames")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("".join(f"{stamp}\n" for stamp in timestamps), encoding="ascii")
    return {"schema": "vector-orbslam3-euroc-times/1", "count": len(timestamps),
            "first_ns": timestamps[0], "last_ns": timestamps[-1], "unit": "nanoseconds",
            "source": "staged camera0 data.csv; ground truth not read"}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--staged", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--receipt", type=Path, required=True)
    args = parser.parse_args()
    result = make_times(args.staged, args.out)
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    args.receipt.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
