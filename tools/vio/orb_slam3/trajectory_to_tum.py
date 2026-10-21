#!/usr/bin/env python3
"""Convert ORB-SLAM3 EuRoC output (ns, xyz, qxyzw) to timestamp-seconds TUM."""
import argparse
import math
from pathlib import Path


def convert(source: Path, output: Path):
    previous = -1.0
    count = 0
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with Path(source).open(encoding="utf-8") as src, output.open("w", encoding="utf-8") as dst:
        for line in src:
            if not line.strip():
                continue
            values = [float(value) for value in line.split()]
            if len(values) != 8 or not all(map(math.isfinite, values)):
                raise ValueError("ORB trajectory must contain 8 finite columns")
            timestamp = values[0] * 1e-9
            if timestamp <= previous:
                raise ValueError("ORB trajectory timestamps must increase")
            previous = timestamp
            dst.write(" ".join([f"{timestamp:.9f}"] + [f"{value:.9f}" for value in values[1:]]) + "\n")
            count += 1
    if count < 3:
        raise ValueError("too few tracked poses")
    return count


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    print(convert(args.source, args.out))


if __name__ == "__main__":
    main()
