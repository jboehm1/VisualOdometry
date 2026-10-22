#!/usr/bin/env python3
"""Run the pinned ORB-SLAM3 stereo-inertial EuRoC example without truth access."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import resource
import subprocess
import time

from euroc_times import make_times
from trajectory_to_tum import convert


def digest(path):
    h = hashlib.sha256()
    with Path(path).open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def run(sensors, out, timeout_s=900):
    sensors, out = Path(sensors), Path(out)
    out.mkdir(parents=True, exist_ok=True)
    times = out / "timestamps_ns.txt"
    times_receipt = make_times(sensors, times)
    command = ["xvfb-run", "-a", "-s", "-screen 0 1280x800x24", "/opt/orb_slam3/Examples/Stereo-Inertial/stereo_inertial_euroc",
               "/opt/orb_slam3/Vocabulary/ORBvoc.txt", "/opt/orb_slam3/Examples/Stereo-Inertial/EuRoC.yaml",
               str(sensors), str(times)]
    env = dict(os.environ)
    env["LIBGL_ALWAYS_SOFTWARE"] = "1"
    env["HOME"] = "/tmp/orb-home"
    env["XDG_CONFIG_HOME"] = "/tmp/orb-home/.config"
    env["LD_LIBRARY_PATH"] = "/opt/orb_slam3/lib:/opt/orb_slam3/Thirdparty/DBoW2/lib:/opt/orb_slam3/Thirdparty/g2o/lib:/usr/local/lib"
    before = resource.getrusage(resource.RUSAGE_CHILDREN)
    start = time.monotonic()
    with (out / "stdout.log").open("w", encoding="utf-8") as log:
        completed = subprocess.run(command, cwd=out, env=env, stdout=log, stderr=subprocess.STDOUT,
                                   timeout=timeout_s, check=False)
    wall = time.monotonic() - start
    after = resource.getrusage(resource.RUSAGE_CHILDREN)
    raw = out / "CameraTrajectory.txt"
    if not raw.is_file() or raw.stat().st_size == 0:
        raise RuntimeError("ORB-SLAM3 produced no camera trajectory")
    tum = out / "trajectory.tum"
    pose_count = convert(raw, tum)
    binary = Path(command[4])
    runtime = {
        "schema": "vector-orbslam3-host-runtime/1", "exit_code": completed.returncode,
        "wall_seconds": wall, "child_cpu_seconds": (after.ru_utime - before.ru_utime) + (after.ru_stime - before.ru_stime),
        "child_peak_rss_kib": after.ru_maxrss, "pose_count": pose_count,
        "binary_sha256": digest(binary), "network": "none", "truth_mount": False,
        "input_times": times_receipt, "trajectory_sha256": digest(tum),
        "raw_euroc_trajectory_sha256": digest(raw),
    }
    (out / "runtime.json").write_text(json.dumps(runtime, indent=2) + "\n", encoding="utf-8")
    if completed.returncode:
        raise RuntimeError(f"ORB-SLAM3 failed with exit code {completed.returncode}")
    return runtime


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sensors", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--timeout", type=int, default=900)
    args = parser.parse_args()
    print(json.dumps(run(args.sensors, args.out, args.timeout), indent=2))


if __name__ == "__main__":
    main()
