#!/usr/bin/env python3
"""Inspect original ROS1 radar Doppler + IMU + TF message structure, not a SLAM estimator."""
import argparse
import json
from pathlib import Path

TOPICS = ("/ti_mmwave_left_radar/radar_scan", "/ti_mmwave_right_radar/radar_scan",
          "/os1_cloud_node/imu", "/tf_static", "/tf",
          "/ti_mmwave_left_radar/radar_scan_pcl", "/ground_truth", "/fiducial_pose")


def probe(path: Path):
    from rosbags.highlevel import AnyReader
    result = {"source_kind": "ORIGINAL_ROS1_SENSOR_HEADER_PROBE", "topics": {},
              "sample_limit": 3, "ground_truth_not_used_as_estimator_input": True}
    with AnyReader([path]) as reader:
        conn = {c.topic: c for c in reader.connections if c.topic in TOPICS}
        for key, val in conn.items():
            result["topics"][key] = {"type": val.msgtype, "samples": []}
        for connection, bag_time, raw in reader.messages(connections=list(conn.values())):
            row = result["topics"][connection.topic]
            if len(row["samples"]) >= 3:
                continue
            msg = reader.deserialize(raw, connection.msgtype)
            fields = [str(k) for k in vars(msg).keys() if not str(k).startswith("_")]
            sample = {"bag_time_ns": bag_time, "fields": fields}
            if hasattr(msg, "header"):
                sample["header_frame_id"] = str(msg.header.frame_id)
                sample["header_time_s"] = float(msg.header.stamp.sec) + float(getattr(msg.header.stamp, "nanosec", 0)) * 1e-9
            for name in ("x", "y", "z", "range", "velocity", "bearing", "intensity", "point_id"):
                if hasattr(msg, name):
                    sample[name] = float(getattr(msg, name))
            if hasattr(msg, "angular_velocity"):
                v = msg.angular_velocity
                sample["gyro_xyz"] = [float(v.x), float(v.y), float(v.z)]
            if hasattr(msg, "fields"):
                sample["cloud_fields"] = [(str(f.name), int(f.offset), int(f.datatype)) for f in msg.fields]
            if hasattr(msg, "transforms"):
                sample["transforms"] = [
                    {"parent": t.header.frame_id, "child": t.child_frame_id,
                     "q": [float(t.transform.rotation.x), float(t.transform.rotation.y),
                           float(t.transform.rotation.z), float(t.transform.rotation.w)],
                     "xyz": [float(t.transform.translation.x), float(t.transform.translation.y),
                             float(t.transform.translation.z)]}
                    for t in msg.transforms[:30]]
            row["samples"].append(sample)
            if all(len(v["samples"]) >= 3 for v in result["topics"].values()):
                break
    return result


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--bag", required=True, type=Path)
    p.add_argument("--out", required=True, type=Path)
    args = p.parse_args()
    result = probe(args.bag)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(result, indent=2, sort_keys=True, allow_nan=False), encoding="utf-8")
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
