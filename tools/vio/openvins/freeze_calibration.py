#!/usr/bin/env python3
"""Create a fixed-calibration OpenVINS config for the common-input benchmark."""
import argparse
from pathlib import Path


FLAGS = ("calib_cam_extrinsics", "calib_cam_intrinsics", "calib_cam_timeoffset",
         "calib_imu_intrinsics", "calib_imu_g_sensitivity")


def freeze(source: Path, output: Path):
    lines = Path(source).read_text(encoding="utf-8").splitlines()
    found = {key: 0 for key in FLAGS}
    updated = []
    for line in lines:
        stripped = line.lstrip()
        key = next((k for k in FLAGS if stripped.startswith(k + ":")), None)
        if key:
            found[key] += 1
            comment = "" if "#" not in line else " " + line[line.index("#"):]
            indent = line[:len(line) - len(stripped)]
            line = f"{indent}{key}: false{comment}"
        updated.append(line)
    if any(count != 1 for count in found.values()):
        raise ValueError("OpenVINS config calibration flags missing or duplicated")
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(updated) + "\n", encoding="utf-8")
    return {"source": str(source), "output": str(output), "flags": found,
            "all_online_calibration_disabled": True}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    print(freeze(args.source, args.out))


if __name__ == "__main__":
    main()
