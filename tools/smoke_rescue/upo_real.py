#!/usr/bin/env python3
"""UPO experiment 7 real ROS1 sensor AUDIT (not odometry or slam).

All metrics require decoded ORIGINAL bag sensor messages. Pose reference is
used ONLY for standalone descriptive statistics after sensor processing, never
as a motion-estimator input, never as a depth/extrinsic substitute.
Requires pinned rosbags>=0.11 dependency in real job. No ROS installation.
"""
from __future__ import annotations

import argparse
from bisect import bisect_left
import hashlib
import json
import math
from pathlib import Path
import statistics
import struct
import sys

TOPICS = {
    "radar_left": "/ti_mmwave_left_radar/radar_scan_pcl",
    "radar_right": "/ti_mmwave_right_radar/radar_scan_pcl",
    "lidar": "/os1_cloud_node/points_non_dense",
    "reference": "/ground_truth",
}
EXPECTED = {
    "radar_left": "sensor_msgs/msg/PointCloud2",
    "radar_right": "sensor_msgs/msg/PointCloud2",
    "lidar": "sensor_msgs/msg/PointCloud2",
    "reference": "geometry_msgs/msg/PoseStamped",
}
HARD_MAX_BAG_BYTES = 2_400_000_000


def sha256_file(filename: Path):
    digest = hashlib.sha256()
    with filename.open("rb") as inp:
        for chunk in iter(lambda: inp.read(4 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def ros_stamp(msg):
    h = msg.header.stamp
    nanos = getattr(h, "nanosec", getattr(h, "nsec", None))
    if nanos is None or not (0 <= nanos < 1_000_000_000):
        raise ValueError("Invalid ROS message capture timestamp")
    return float(h.sec) + float(nanos) * 1e-9


def cloud_xyz(message, max_points=3000):
    """Read PointCloud2 xyz from explicit per-field offsets and point_step.

    Supports big/little endian and row padding; nonfinite points skipped. Return
    a deterministic decimation and exact advertised dimensions. No TF assumed.
    """
    fields = {f.name: f for f in message.fields}
    if any(axis not in fields for axis in ("x", "y", "z")):
        raise ValueError("PointCloud2 lacks x,y,z fields")
    if any(int(fields[a].datatype) != 7 or int(fields[a].count) != 1 for a in ("x", "y", "z")):
        raise ValueError("PointCloud2 xyz must be float32 scalars")
    width, height = int(message.width), int(message.height)
    step, row_step = int(message.point_step), int(message.row_step)
    if not (0 <= width <= 2_000_000 and 0 <= height <= 30000 and 12 <= step <= 512):
        raise ValueError("Malformed PointCloud2 shape")
    if row_step < width * step:
        raise ValueError("Malformed PointCloud2 row stride")
    payload = message.data
    if len(payload) < row_step * height:
        raise ValueError("Truncated PointCloud2 payload")
    offsets = [int(fields[a].offset) for a in ("x", "y", "z")]
    if any(off < 0 or off + 4 > step for off in offsets):
        raise ValueError("Bad PointCloud2 float32 offset")
    total = width * height
    every = max(1, (total + max_points - 1) // max_points)
    byteorder = ">" if message.is_bigendian else "<"
    coords, valid_count = [], 0
    for flat_idx in range(0, total, every):
        row, col = divmod(flat_idx, width)
        base = row * row_step + col * step
        xyz = tuple(struct.unpack_from(byteorder + "f", payload, base + off)[0]
                    for off in offsets)
        if all(math.isfinite(k) for k in xyz):
            valid_count += 1
            coords.append(xyz)
    return coords, total, every


def quantile(values, q):
    if not values:
        return None
    ordered = sorted(values)
    x = (len(ordered) - 1) * q
    lower = int(x)
    return round(ordered[lower] + (ordered[min(lower + 1, len(ordered) - 1)] -
                                  ordered[lower]) * (x - lower), 6)


def summarize_cloud(rows):
    if not rows:
        return {"frames": 0, "status": "NO_FRAMES"}
    points = [row["sampled_valid"] for row in rows]
    total = [row["advertised_points"] for row in rows]
    times = [row["time_s"] for row in rows]
    positive = [row["ranges_median"] for row in rows if row["ranges_median"] is not None]
    duration = times[-1] - times[0] if len(times) >= 2 else 0
    return {
        "frames": len(rows),
        "duration_s": round(duration, 5),
        "sampled_valid_points_total": sum(points),
        "advertised_points_total": sum(total),
        "zero_usable_return_frames": sum(v == 0 for v in points),
        "zero_usable_return_fraction": round(sum(v == 0 for v in points) / len(rows), 6),
        "frame_rate_from_capture_header_hz": round((len(rows) - 1) / duration, 4)
        if duration > 0 else None,
        "advertised_points_per_frame_p50": quantile(total, 0.5),
        "advertised_points_per_frame_p95": quantile(total, 0.95),
        "sampled_valid_points_per_frame_p50": quantile(points, 0.5),
        "radial_range_median_m_p50_across_frames": quantile(positive, 0.5),
        "sampled_point_decimation": "deterministic stride, max 3000 per frame",
    }


def nearest_gap(times, value):
    if not times:
        return None
    ix = bisect_left(times, value)
    return min(abs(times[j] - value) for j in (ix - 1, ix) if 0 <= j < len(times))


def audit_bag(bag: Path, max_seconds=60.0):
    if bag.name != "experiment_7.bag":
        raise ValueError("Expected original UPO experiment_7.bag")
    if not bag.is_file() or not (100_000 <= bag.stat().st_size <= HARD_MAX_BAG_BYTES):
        raise ValueError("Missing/incorrect ROS bag byte size")
    if not (1 <= max_seconds <= 169):
        raise ValueError("Choose capture interval 1..169 seconds")
    try:
        from rosbags.highlevel import AnyReader
    except ImportError as exc:
        raise RuntimeError("Install the documented rosbags==0.11.4 for REAL decoding") from exc

    records = {k: [] for k in TOPICS}
    bad_frames = {k: 0 for k in TOPICS}
    ros_count = {k: 0 for k in TOPICS}
    capture_first = {}
    with AnyReader([bag]) as reader:
        bytopic = {c.topic: c for c in reader.connections}
        for name, topic in TOPICS.items():
            if topic not in bytopic:
                raise ValueError("Real bag missing required ROS topic " + topic)
            if bytopic[topic].msgtype != EXPECTED[name]:
                raise ValueError("Unexpected ROS message type: " + name +
                                 " " + bytopic[topic].msgtype)
        chosen = [bytopic[topic] for topic in TOPICS.values()]
        bag_start_ns = reader.start_time
        for conn, bag_time, raw in reader.messages(connections=chosen):
            if (bag_time - bag_start_ns) * 1e-9 > max_seconds:
                break
            name = next(key for key, topic in TOPICS.items() if topic == conn.topic)
            msg = reader.deserialize(raw, conn.msgtype)
            ros_count[name] += 1
            t = ros_stamp(msg)
            if name in capture_first and t <= records[name][-1]["time_s"]:
                bad_frames[name] += 1
                continue
            capture_first.setdefault(name, t)
            if name == "reference":
                p = msg.pose.position
                x, y = float(p.x), float(p.y)
                if not (math.isfinite(x) and math.isfinite(y)):
                    bad_frames[name] += 1
                    continue
                records[name].append({"time_s": t, "x_m": x, "y_m": y})
            else:
                xyz, advertised, every = cloud_xyz(msg)
                if name != "lidar":
                    ranges = [math.hypot(x, y) for x, y, z in xyz if math.hypot(x, y) >= .05]
                else:
                    ranges = [math.hypot(x, y) for x, y, z in xyz if math.hypot(x, y) >= .05]
                records[name].append({
                    "time_s": t, "advertised_points": advertised,
                    "sampled_valid": len(xyz),
                    "ranges_median": quantile(ranges, .5),
                })
    if any(len(v) < 2 for v in records.values()):
        raise ValueError("Missing real radar/LiDAR/reference captures in chosen interval")
    reference = records["reference"]
    path_length = sum(math.hypot(b["x_m"] - a["x_m"], b["y_m"] - a["y_m"])
                      for a, b in zip(reference, reference[1:]))
    time_ref = [x["time_s"] for x in reference]
    temporal = {}
    for name in ("radar_left", "radar_right", "lidar"):
        gaps = [nearest_gap(time_ref, row["time_s"]) for row in records[name]]
        temporal[name] = {
            "frames_with_reference_time_within_0_1s": sum(g <= 0.1 for g in gaps),
            "fractions_within_0_1s": round(sum(g <= .1 for g in gaps) / len(gaps), 6),
            "nearest_ref_time_gap_ms_p95": quantile([g * 1000 for g in gaps], .95),
        }
    report = {
        "dataset_id": "upo-smoke-exp7",
        "mode": "DECODED_REAL_ROS1_BAG",
        "input_bytes": bag.stat().st_size,
        "input_sha256": sha256_file(bag),
        "source_license": "CC-BY-NC-SA-3.0",
        "source_url": "https://robotics.upo.es/datasets/smoke/",
        "processing_seconds_requested": max_seconds,
        "real_sensor_records_seen": ros_count,
        "nonmonotonic_header_dropped": bad_frames,
        "metrics": {name: summarize_cloud(records[name]) for name in ("radar_left", "radar_right", "lidar")},
        "temporal_coverage_vs_published_reference": temporal,
        "reference_info_not_independent_pose_accuracy": {
            "frames": len(reference),
            "reported_xy_path_m": round(path_length, 5),
            "reported_duration_s": round(reference[-1]["time_s"] - reference[0]["time_s"], 5),
            "caveat": "Published poses are AR marker estimates with wheel odometry smoothing; NOT independent in all segments",
        },
        "engine": "pointcloud_sensor_audit_no_radar_slam_no_pose_estimator",
        "actual_estimator_executed": False,
        "reference_used_as_estimator_input": False,
        "reference_used_for_position_accuracy_score": False,
        "metric_distance_accuracy_proven": False,
        "radar_obstacle_recall_measured": False,
        "safety": "RESEARCH_ONLY_NOT_FIRE_RESCUE",
        "interpretation": [
            "Original sensor cloud returns and temporal coverage only.",
            "No sensor extrinsics validated; radar and lidar detections cannot be directly spatially matched.",
            "No independent smoke density/thermal/ground truth obstacle labels.",
            "Reported xy marker/odometry trajectory is NOT a localization accuracy score.",
            "Radar empty returns do not demonstrate free space.",
        ],
    }
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(description="REAL smoke ROS bag sensor audit, not radar-SLAM")
    parser.add_argument("--bag", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--max-seconds", type=float, default=60)
    a = parser.parse_args(argv)
    report = audit_bag(a.bag, a.max_seconds)
    a.out.parent.mkdir(parents=True, exist_ok=True)
    a.out.write_text(json.dumps(report, sort_keys=True, indent=2, allow_nan=False) + "\n",
                     encoding="utf-8")
    print(json.dumps({"status": "PASS", "mode": report["mode"], "metrics": report["metrics"],
                      "safety": report["safety"], "out": str(a.out)}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
