#!/usr/bin/env python3
"""Build a sensor-only ROS bag and execute pinned upstream OpenVINS."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import resource
import shlex
import subprocess
import time

try:  # package import for tests; local import for container script execution
    from .euroc_rosbag import write_bag
    from .freeze_calibration import freeze
except ImportError:
    from euroc_rosbag import write_bag
    from freeze_calibration import freeze


def digest(path):
    h = hashlib.sha256()
    with Path(path).open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def run(sensors, source_root, out, timeout_s=300):
    sensors, source_root, out = map(Path, (sensors, source_root, out))
    out.mkdir(parents=True, exist_ok=True)
    bag = out / "openvins_euroc.bag"
    bag_receipt = out / "bag-receipt.json"
    write_bag(sensors, bag, bag_receipt)

    upstream_cfg = source_root / "config/euroc_mav/estimator_config.yaml"
    cfg = out / "config/euroc_mav/estimator_config.yaml"
    cfg.parent.mkdir(parents=True, exist_ok=True)
    for name in ("kalibr_imu_chain.yaml", "kalibr_imucam_chain.yaml"):
        (cfg.parent / name).write_bytes((source_root / "config/euroc_mav" / name).read_bytes())
    calibration_receipt = freeze(upstream_cfg, cfg)

    command = ["roslaunch", "ov_msckf", "subscribe.launch", "config:=euroc_mav",
               f"config_path:={cfg}", "max_cameras:=2", "use_stereo:=true",
               "dobag:=true", f"bag:={bag}", "dosave:=true", "dotime:=true",
               f"path_est:={out / 'trajectory_raw.txt'}", f"path_time:={out / 'timing.txt'}"]
    before = resource.getrusage(resource.RUSAGE_CHILDREN)
    start = time.monotonic()
    env = dict(os.environ)
    env["ROS_HOME"] = str(out / "ros_home")
    env["HOME"] = str(out / "home")
    Path(env["ROS_HOME"]).mkdir(parents=True, exist_ok=True)
    Path(env["HOME"]).mkdir(parents=True, exist_ok=True)
    launch_cmd = ["bash", "-lc", "source /opt/ros/noetic/setup.bash && source /ws/devel/setup.bash && exec " +
                  shlex.join(command)]
    completed = subprocess.run(launch_cmd, env=env, timeout=timeout_s, check=False)
    wall = time.monotonic() - start
    after = resource.getrusage(resource.RUSAGE_CHILDREN)
    binary = Path("/ws/devel/lib/ov_msckf/run_subscribe_msckf")
    raw = out / "trajectory_raw.txt"
    if not raw.is_file() or raw.stat().st_size == 0:
        raise RuntimeError("OpenVINS produced no pose trajectory")
    trajectory = out / "trajectory.tum"
    count = 0
    with raw.open(encoding="utf-8") as src, trajectory.open("w", encoding="utf-8") as dst:
        for line in src:
            fields = line.split()
            if not fields:
                continue
            if len(fields) != 8:
                raise ValueError("unexpected OpenVINS pose record; expected TUM 8-column format")
            fields[0] = f"{float(fields[0]):.9f}"
            dst.write(" ".join(fields) + "\n")
            count += 1
    if count < 3:
        raise RuntimeError("OpenVINS returned too few pose samples")
    runtime = {
        "schema": "vector-openvins-host-runtime/1", "exit_code": completed.returncode,
        "wall_seconds": wall, "child_cpu_seconds": (after.ru_utime - before.ru_utime) + (after.ru_stime - before.ru_stime),
        "child_peak_rss_kib": after.ru_maxrss, "pose_count": count,
        "binary_sha256": digest(binary), "network": "none", "truth_mount": False,
        "bag_sha256": digest(bag), "trajectory_sha256": digest(trajectory),
        "calibration": calibration_receipt,
    }
    (out / "runtime.json").write_text(json.dumps(runtime, indent=2) + "\n", encoding="utf-8")
    if completed.returncode:
        raise RuntimeError(f"OpenVINS failed with exit code {completed.returncode}")
    return runtime


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sensors", type=Path, required=True)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args()
    print(json.dumps(run(args.sensors, args.source, args.out, args.timeout), indent=2))


if __name__ == "__main__":
    main()
