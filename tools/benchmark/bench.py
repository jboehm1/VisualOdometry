#!/usr/bin/env python3
"""Dataset-neutral odometry benchmark. Requires Python >=3.10 and numpy.

No ground-truth information is sent to an estimator. Running this harness does
NOT test ARCore: ARCore datasets need official Android Recording/Playback.
"""
from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import json
import math
import subprocess
import sys
import time
from pathlib import Path

import numpy as np


CATALOG = Path(__file__).with_name("catalog.json")


def load_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def write_json(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def rows(path):
    path = Path(path)
    opener = gzip.open if path.suffix == ".gz" else open
    with opener(path, "rt", newline="", encoding="utf-8-sig") as stream:
        yield from csv.reader(stream)


def check_monotonic(values, label):
    if not values or any(a >= b for a, b in zip(values, values[1:])):
        raise ValueError(f"{label}: empty, duplicated or non-increasing timestamps")


def clean_int(value):
    return int(str(value).strip())


def prepare_mav(kind, root, target, truth_override=None):
    if kind not in ("euroc", "tumvi"):
        raise ValueError("not a MAV-format dataset")
    mav = root / "mav0" if (root / "mav0").is_dir() else root
    camera = mav / "cam0" / "data.csv"
    imu = mav / "imu0" / "data.csv"
    for path in (camera, imu):
        if not path.is_file():
            raise FileNotFoundError(f"Missing native dataset input {path}")
    target.mkdir(parents=True, exist_ok=True)
    frames_out = target / "frames.csv"
    imu_out = target / "imu.csv"
    count_frames = count_imu = 0
    first_frame = last_frame = first_imu = last_imu = None
    with frames_out.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(("t_ns", "camera", "path"))
        for row in rows(camera):
            if not row or row[0].startswith("#"):
                continue
            if len(row) < 2:
                raise ValueError(f"Malformed camera index row: {row}")
            t = clean_int(row[0])
            filename = row[1].strip()
            if "/" in filename or "\\" in filename or filename.startswith("."):
                raise ValueError(f"Invalid frame filename: {filename}")
            if last_frame is not None and t <= last_frame:
                raise ValueError("Non-increasing camera timestamps")
            image = mav / "cam0" / "data" / filename
            if not image.is_file():
                raise FileNotFoundError(f"Camera frame missing: {image}")
            writer.writerow((t, "cam0", str(image.resolve())))
            first_frame = t if first_frame is None else first_frame
            last_frame = t
            count_frames += 1
    with imu_out.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(("t_ns", "type", "x", "y", "z"))
        for row in rows(imu):
            if not row or row[0].startswith("#"):
                continue
            if len(row) < 7:
                raise ValueError(f"Malformed IMU row: {row}")
            t = clean_int(row[0])
            if last_imu is not None and t <= last_imu:
                raise ValueError("Non-increasing IMU timestamps")
            vals = [float(v) for v in row[1:7]]
            if not all(math.isfinite(v) for v in vals):
                raise ValueError("Non-finite IMU sample")
            writer.writerow((t, "GYRO", *vals[:3]))
            writer.writerow((t, "ACCEL", *vals[3:]))
            first_imu = t if first_imu is None else first_imu
            last_imu = t
            count_imu += 1
    if count_frames < 2 or count_imu < 2:
        raise ValueError("Insufficient real sensor data")
    candidates = [mav / "state_groundtruth_estimate0" / "data.csv",
                  mav / "mocap0" / "data.csv"]
    gt = Path(truth_override).resolve() if truth_override else next(
        (p for p in candidates if p.is_file()), None)
    gt_format = "euroc" if gt is not None else None
    calibrations = [str(p.resolve()) for p in
                    (mav / "cam0" / "sensor.yaml", mav / "imu0" / "sensor.yaml")
                    if p.is_file()]
    summary = {
        "schema": 1, "dataset_kind": kind, "source_root": str(root.resolve()),
        "frames": str(frames_out.resolve()), "imu": str(imu_out.resolve()),
        "frame_count": count_frames, "imu_count": count_imu,
        "t0_ns": first_frame, "t1_ns": last_frame,
        "imu_t0_ns": first_imu, "imu_t1_ns": last_imu,
        "calibration_files": calibrations,
        "truth": str(gt.resolve()) if gt else None,
        "truth_format": gt_format, "truth_frame": "imu",
        "pose_frame": "imu",
        "warnings": [
            "cam0 raw image coordinates; camera/IMU calibration is not applied by this indexer",
            "ground truth describes the IMU/body frame; convert camera poses using calibrated extrinsics",
            "TUM VI truth may cover only the motion-capture region; never extrapolate",
        ],
    }
    write_json(target / "manifest.json", summary)
    return summary


def prepare_collection(root, target):
    """Capture-only input; records video and IMU, never claims a pose truth."""
    frames = root / "frames.csv.gz"
    imu = root / "imu.csv.gz"
    if not frames.is_file() or not imu.is_file():
        raise FileNotFoundError("Expected frames.csv.gz and imu.csv.gz in capture directory")
    target.mkdir(parents=True, exist_ok=True)
    count_frames = count_imu = 0
    known_videos = {p.name for p in root.glob("*.mp4")}
    out_frames, out_imu = target / "frames.csv", target / "imu.csv"
    last_frame = last_imu = None
    with out_frames.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(("t_ns", "camera", "path"))
        for row in rows(frames):
            if not row or row[0] == "part":
                continue
            if len(row) < 3:
                raise ValueError("Malformed collection frame index")
            part, timestamp = row[0], clean_int(row[2])
            if part not in known_videos:
                raise FileNotFoundError(f"Missing video part {part}")
            if last_frame is not None and timestamp <= last_frame:
                raise ValueError("Non-monotonic frame times")
            writer.writerow((timestamp, "video", str((root / part).resolve())))
            last_frame = timestamp
            count_frames += 1
    with out_imu.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(("t_ns", "type", "x", "y", "z"))
        for row in rows(imu):
            if not row or row[0] == "sensor_time_ns":
                continue
            if len(row) < 5:
                raise ValueError("Malformed collection IMU row")
            timestamp = clean_int(row[0])
            if last_imu is not None and timestamp < last_imu:
                raise ValueError("Non-monotonic collection IMU time")
            if row[1] not in ("ACCEL", "GYRO"):
                continue
            values = [float(x) for x in row[2:5]]
            if not all(math.isfinite(v) for v in values):
                raise ValueError("Non-finite sensor value")
            writer.writerow((timestamp, row[1], *values))
            last_imu = timestamp
            count_imu += 1
    if not count_frames or not count_imu:
        raise ValueError("No recorded camera/IMU samples")
    summary = {
        "schema": 1, "dataset_kind": "vector_capture",
        "source_root": str(root.resolve()), "frames": str(out_frames.resolve()),
        "imu": str(out_imu.resolve()), "frame_count": count_frames,
        "imu_count": count_imu, "truth": None, "truth_format": None,
        "calibration_files": [], "pose_frame": "unknown",
        "warnings": [
            "frames.csv contains MP4 references, not extracted video frames",
            "Camera2 video presentation times and sensor times require measured synchronization",
            "camera-IMU extrinsics, rolling shutter and sensor biases are not calibrated",
            "no independent truth: accuracy metrics are prohibited",
        ],
    }
    write_json(target / "manifest.json", summary)
    return summary


def quat_matrix(q):
    x, y, z, w = np.asarray(q, dtype=float)
    n = x*x + y*y + z*z + w*w
    if n < 1e-12:
        raise ValueError("Degenerate quaternion")
    s = 2.0 / n
    return np.array([
        [1-s*(y*y+z*z), s*(x*y-z*w), s*(x*z+y*w)],
        [s*(x*y+z*w), 1-s*(x*x+z*z), s*(y*z-x*w)],
        [s*(x*z-y*w), s*(y*z+x*w), 1-s*(x*x+y*y)],
    ])


def parse_poses(path, fmt):
    times, coords, rots, segments = [], [], [], []
    path = Path(path)
    if fmt in ("tum", "euroc"):
        with path.open(encoding="utf-8-sig") as f:
            for row in f:
                line = row.strip()
                if not line or line.startswith("#"):
                    continue
                parts = line.replace(",", " ").split()
                if len(parts) < 8:
                    raise ValueError(f"{path}: expected timestamp + xyz + xyzw quaternion")
                vals = [float(v) for v in parts[:8]]
                if not all(map(math.isfinite, vals)):
                    raise ValueError("Nonfinite trajectory pose")
                times.append(vals[0] * (1e-9 if fmt == "euroc" else 1.0))
                coords.append(vals[1:4])
                rots.append(quat_matrix(vals[4:8]))
                segments.append(1)
    elif fmt == "vector":
        with path.open(encoding="utf-8-sig", newline="") as f:
            for row in csv.DictReader(f):
                if row.get("event") != "POSE":
                    continue
                if row.get("tracking") != "TRACKING":
                    continue
                try:
                    times.append(int(row["frame_time_ns"]) * 1e-9)
                    coords.append([float(row[k]) for k in ("x_m","y_m","z_m")])
                    rots.append(quat_matrix([float(row[k]) for k in ("qx","qy","qz","qw")]))
                    segments.append(int(row["segments"]))
                except (KeyError, ValueError) as e:
                    raise ValueError("Invalid VECTOR pose row") from e
    else:
        raise ValueError(f"Unsupported pose format {fmt}")
    check_monotonic(times, str(path))
    if len(times) < 3:
        raise ValueError("At least three poses are needed for trajectory evaluation")
    if not np.isfinite(np.asarray(coords)).all():
        raise ValueError("Nonfinite position")
    return (np.array(times), np.array(coords), np.array(rots),
            np.array(segments, dtype=int))


def pair_by_time(gt_times, est_times, tolerance):
    """One-to-one nearest timestamp associations, no interpolation or tuned offset."""
    i = j = 0
    pairs = []
    while i < len(gt_times) and j < len(est_times):
        delta = est_times[j] - gt_times[i]
        if abs(delta) <= tolerance:
            # Advance GT only when a strictly better same-estimate match exists.
            if i + 1 < len(gt_times) and abs(est_times[j]-gt_times[i+1]) < abs(delta):
                i += 1
                continue
            pairs.append((i, j))
            i += 1
            j += 1
        elif delta > 0:
            i += 1
        else:
            j += 1
    return np.array(pairs, dtype=int).reshape(-1, 2)


def alignment(points, truth, kind):
    if kind == "none":
        return np.eye(3), np.zeros(3), 1.0
    if kind == "origin":
        return np.eye(3), truth[0] - points[0], 1.0
    if len(points) < 3:
        raise ValueError("Insufficient points for rigid alignment")
    a, b = points.mean(axis=0), truth.mean(axis=0)
    x, y = points-a, truth-b
    u, singular, vt = np.linalg.svd(x.T @ y)
    d = np.eye(3)
    d[-1, -1] = np.linalg.det(u @ vt)
    rotation = u @ d @ vt  # row-vector convention
    scale = 1.0
    if kind == "sim3":
        denom = (x*x).sum()
        if denom < 1e-12:
            raise ValueError("Cannot fit similarity to stationary estimate")
        scale = float(np.sum(singular * np.diag(d)) / denom)
        if scale <= 0:
            raise ValueError("Invalid fitted scale")
    elif kind != "se3":
        raise ValueError(f"Unsupported alignment {kind}")
    return rotation.T, b - scale * (a @ rotation), scale


def rmse(values):
    return float(np.sqrt(np.mean(np.square(values)))) if len(values) else None


def measure(gt, est, tolerance=0.02, delta_seconds=1.0,
            max_segment_gap=0.55, align="se3", min_pairs=3):
    gt_t, gt_p, gt_r, _ = gt
    est_t, est_p, est_r, est_s = est
    matches = pair_by_time(gt_t, est_t, tolerance)
    if len(matches) < min_pairs:
        raise ValueError(f"Only {len(matches)} time matches; check clocks, offsets and timestamp units")
    gi, ei = matches[:,0], matches[:,1]
    g, e = gt_p[gi], est_p[ei]
    r, t, scale = alignment(e, g, align)
    aligned = scale * (e @ r.T) + t
    err = np.linalg.norm(aligned-g, axis=1)
    # Count only contiguous tracked time, never bridge tracking loss.
    valid_steps = ((np.diff(est_t[ei]) <= max_segment_gap) &
                   (np.diff(est_s[ei]) == 0) &
                   (np.diff(gt_t[gi]) <= max_segment_gap))
    seconds = np.diff(gt_t[gi])
    covered = float(np.sum(seconds[valid_steps]))
    truth_span = float(gt_t[-1]-gt_t[0])
    coverage = min(1.0, covered / truth_span) if truth_span > 0 else 0.0
    # Distances computed at a fixed sparse sample rate to reduce GT noise,
    # not from raw 200Hz ground-truth jitter.
    selected = [0]
    for k in range(1, len(gi)):
        if (gt_t[gi[k]]-gt_t[gi[selected[-1]]]) >= 0.2:
            selected.append(k)
    d_gt = d_est = 0.0
    for a_idx, b_idx in zip(selected, selected[1:]):
        if est_s[ei[a_idx]] != est_s[ei[b_idx]]:
            continue
        if gt_t[gi[b_idx]]-gt_t[gi[a_idx]] > max_segment_gap:
            continue
        d_gt += float(np.linalg.norm(g[b_idx]-g[a_idx]))
        d_est += float(np.linalg.norm(aligned[b_idx]-aligned[a_idx]))
    rpe_translation, rpe_rotation_deg = [], []
    # 1-second displacement and relative orientation; do not bridge gaps.
    for i in range(len(gi)):
        goal = gt_t[gi[i]] + delta_seconds
        j = int(np.searchsorted(gt_t[gi], goal, side="left"))
        if j >= len(gi) or abs(gt_t[gi[j]]-goal) > max(tolerance*2, 0.1):
            continue
        if est_s[ei[i]] != est_s[ei[j]]:
            continue
        if not bool(np.all(valid_steps[i:j])):
            continue
        # Row-vector alignment maps estimated world frame into truth world.
        ri = r @ est_r[ei[i]]
        rj = r @ est_r[ei[j]]
        true_i, true_j = gt_r[gi[i]], gt_r[gi[j]]
        pe_local = ri.T @ (aligned[j]-aligned[i])
        pg_local = true_i.T @ (g[j]-g[i])
        rpe_translation.append(float(np.linalg.norm(pe_local-pg_local)))
        rel_e = ri.T @ rj
        rel_g = true_i.T @ true_j
        delta_r = rel_e.T @ rel_g
        c = np.clip((np.trace(delta_r)-1)/2, -1, 1)
        rpe_rotation_deg.append(float(np.degrees(np.arccos(c))))
    return {
        "pairs": int(len(matches)),
        "time_tolerance_s": tolerance, "window_s": delta_seconds,
        "truth_duration_s": truth_span,
        "tracking_coverage_time_fraction": coverage,
        "ate_rmse_m": rmse(err),
        "ate_median_m": float(np.median(err)),
        "ate_p95_m": float(np.percentile(err, 95)),
        "rpe_translation_rmse_m": rmse(rpe_translation),
        "rpe_rotation_rmse_deg": rmse(rpe_rotation_deg),
        "rpe_pairs": len(rpe_translation),
        "distance_truth_m": d_gt,
        "distance_estimated_m": d_est,
        "distance_error_pct": (100 * (d_est-d_gt)/d_gt) if d_gt > 1e-6 else None,
        "distance_coverage_warning": "Only observed, contiguous path portions evaluated; missing motion is not reconstructed",
        "alignment": align, "estimated_scale_correction": scale,
        "metric_scale_preserved": align != "sim3",
    }


def execute_engine(engine_path, prepared_path, output, timeout_override=None):
    """Execute a locally installed, trusted external estimator; not bundled here."""
    engine = load_json(engine_path)
    prepared = load_json(Path(prepared_path) / "manifest.json")
    if not isinstance(engine.get("argv"), list) or not engine["argv"]:
        raise ValueError("Engine JSON requires an argv list (no shell)")
    output = Path(output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    variables = {
        "frames": prepared["frames"], "imu": prepared["imu"],
        "output": str(output / "trajectory.tum"),
        "workdir": str(output),
    }
    command = [arg.format_map(variables) for arg in engine["argv"]]
    if any(not isinstance(arg, str) for arg in command):
        raise ValueError("Engine argv must contain strings")
    timeout = timeout_override or int(engine.get("timeout_s", 3600))
    start = time.monotonic()
    completed = subprocess.run(command, capture_output=True, text=True,
                               timeout=timeout, check=False)
    (output / "engine.stdout.log").write_text(completed.stdout[-200000:], encoding="utf-8")
    (output / "engine.stderr.log").write_text(completed.stderr[-200000:], encoding="utf-8")
    provenance = {
        "engine": engine.get("name", "external"),
        "engine_config_sha256": sha256(engine_path),
        "input_manifest_sha256": sha256(Path(prepared_path) / "manifest.json"),
        "command": command, "returncode": completed.returncode,
        "elapsed_seconds": round(time.monotonic()-start, 2),
    }
    write_json(output / "run.json", provenance)
    if completed.returncode != 0 or not (output / "trajectory.tum").is_file():
        raise RuntimeError("Engine failed or did not write trajectory.tum; see engine stderr log")
    return provenance


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    sub = ap.add_subparsers(dest="action", required=True)
    sub.add_parser("catalog")
    prep = sub.add_parser("prepare")
    prep.add_argument("--dataset", choices=("euroc", "tumvi", "vector_capture"), required=True)
    prep.add_argument("--root", type=Path, required=True)
    prep.add_argument("--out", type=Path, required=True)
    prep.add_argument("--truth", type=Path)
    ev = sub.add_parser("evaluate")
    ev.add_argument("--truth", type=Path, required=True)
    ev.add_argument("--estimate", type=Path, required=True)
    ev.add_argument("--truth-format", choices=("tum", "euroc"), default="euroc")
    ev.add_argument("--estimate-format", choices=("tum", "euroc", "vector"), default="tum")
    ev.add_argument("--truth-frame", required=True, help="e.g. imu (mandatory provenance)")
    ev.add_argument("--estimate-frame", required=True, help="must match truth-frame; extrinsics are NOT inferred")
    ev.add_argument("--align", choices=("se3", "origin", "none", "sim3"), default="se3")
    ev.add_argument("--max-dt", type=float, default=0.02)
    ev.add_argument("--horizon", type=float, default=1.0)
    ev.add_argument("--max-gap", type=float, default=0.55)
    ev.add_argument("--min-coverage", type=float, default=0.0)
    ev.add_argument("--out", type=Path, required=True)
    run = sub.add_parser("run-engine")
    run.add_argument("--prepared", type=Path, required=True)
    run.add_argument("--engine", type=Path, required=True)
    run.add_argument("--out", type=Path, required=True)
    args = ap.parse_args(argv)
    if args.action == "catalog":
        print(CATALOG.read_text(encoding="utf-8"))
        return 0
    if args.action == "prepare":
        if args.dataset == "vector_capture":
            result = prepare_collection(args.root, args.out)
        else:
            result = prepare_mav(args.dataset, args.root, args.out, args.truth)
    elif args.action == "run-engine":
        result = execute_engine(args.engine, args.prepared, args.out)
    else:
        if args.truth_frame != args.estimate_frame:
            raise ValueError("Pose coordinate frames differ. Apply calibrated camera-to-IMU transform before evaluation.")
        if args.max_dt <= 0 or args.horizon <= 0 or args.max_gap <= 0:
            raise ValueError("All time tolerances must be positive")
        result = measure(parse_poses(args.truth, args.truth_format),
                         parse_poses(args.estimate, args.estimate_format),
                         tolerance=args.max_dt, delta_seconds=args.horizon,
                         max_segment_gap=args.max_gap, align=args.align)
        result["truth_sha256"] = sha256(args.truth)
        result["estimate_sha256"] = sha256(args.estimate)
        write_json(args.out, result)
        if result["tracking_coverage_time_fraction"] < args.min_coverage:
            print(json.dumps(result, indent=2))
            raise ValueError("Coverage below --min-coverage acceptance gate")
    print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired) as exc:
        print(f"Benchmark error: {exc}", file=sys.stderr)
        sys.exit(2)
