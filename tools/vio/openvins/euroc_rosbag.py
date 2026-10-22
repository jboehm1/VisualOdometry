#!/usr/bin/env python3
"""Convert the sensor-only staged EuRoC tree into an OpenVINS ROS1 bag.

Run inside a pinned ROS1/Noetic environment. Ground truth is deliberately not
read or copied. The bag contains only cam0, cam1 and imu0 on upstream topics.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import re


TOPICS = {"cam0": "/cam0/image_raw", "cam1": "/cam1/image_raw", "imu0": "/imu0"}
SAFE_NAME = re.compile(r"^[A-Za-z0-9_.-]+$")


def _digest_file(path: Path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _csv_rows(path: Path):
    with path.open(newline="", encoding="utf-8") as stream:
        for row in csv.reader(stream):
            if not row or row[0].lstrip().startswith("#"):
                continue
            yield row


def read_sensor_records(root: Path):
    """Return timestamped source records; validates parity and bounds first."""
    root = Path(root)
    cameras = []
    for camera in ("cam0", "cam1"):
        folder = root / "mav0" / camera
        records = []
        previous = -1
        for row in _csv_rows(folder / "data.csv"):
            if len(row) != 2:
                raise ValueError(f"invalid {camera} CSV row")
            stamp = int(row[0])
            name = row[1].strip()
            if stamp <= previous or not SAFE_NAME.fullmatch(name) or Path(name).name != name:
                raise ValueError(f"invalid or nonmonotonic {camera} record")
            image = folder / "data" / name
            if image.is_symlink() or not image.is_file():
                raise ValueError(f"missing ordinary {camera} image: {name}")
            previous = stamp
            records.append((stamp, image))
        if len(records) < 3:
            raise ValueError(f"too few {camera} images")
        cameras.append(records)
    if [t for t, _ in cameras[0]] != [t for t, _ in cameras[1]]:
        raise ValueError("OpenVINS stereo requires identical camera timestamps")

    imu_path = root / "mav0" / "imu0" / "data.csv"
    imu = []
    previous = -1
    start, end = cameras[0][0][0], cameras[0][-1][0]
    for row in _csv_rows(imu_path):
        if len(row) != 7:
            raise ValueError("invalid IMU CSV row")
        stamp = int(row[0])
        values = tuple(float(x) for x in row[1:])
        if stamp <= previous or not all(math.isfinite(x) for x in values):
            raise ValueError("invalid or nonmonotonic IMU record")
        previous = stamp
        if start <= stamp <= end:
            imu.append((stamp, values))
    if len(imu) < 3 or imu[0][0] > start or imu[-1][0] < end:
        raise ValueError("IMU does not cover the camera interval")
    return cameras, imu


def _ros_stamp(rospy, stamp_ns):
    secs, nsecs = divmod(int(stamp_ns), 1_000_000_000)
    return rospy.Time(secs, nsecs)


def write_bag(root: Path, output: Path, receipt_path: Path):
    """Write deterministic sensor ordering and a provenance receipt."""
    import cv2
    import numpy as np
    import rosbag
    import rospy
    from cv_bridge import CvBridge
    from sensor_msgs.msg import Image, Imu

    root, output = Path(root), Path(output)
    if output.exists():
        raise FileExistsError(output)
    cameras, imu_records = read_sensor_records(root)
    bridge = CvBridge()
    image_messages = []
    image_hashes = []
    for index, records in enumerate(cameras):
        topic = TOPICS[f"cam{index}"]
        for stamp_ns, source in records:
            encoded = source.read_bytes()
            digest = hashlib.sha256(encoded).hexdigest()
            image_hashes.append(digest)
            image = cv2.imdecode(np.frombuffer(encoded, dtype=np.uint8), cv2.IMREAD_UNCHANGED)
            if image is None or image.ndim != 2 or image.dtype.name != "uint8":
                raise ValueError("expected raw 8-bit grayscale EuRoC PNG")
            msg = bridge.cv2_to_imgmsg(image, encoding="mono8")
            msg.header.stamp = _ros_stamp(rospy, stamp_ns)
            msg.header.frame_id = f"cam{index}"
            image_messages.append((stamp_ns, topic, msg))

    imu_messages = []
    imu_hash = hashlib.sha256((root / "mav0" / "imu0" / "data.csv").read_bytes()).hexdigest()
    for stamp_ns, values in imu_records:
        wx, wy, wz, ax, ay, az = values
        msg = Imu()
        msg.header.stamp = _ros_stamp(rospy, stamp_ns)
        msg.header.frame_id = "imu0"
        msg.orientation_covariance[0] = -1.0
        msg.angular_velocity.x, msg.angular_velocity.y, msg.angular_velocity.z = wx, wy, wz
        msg.linear_acceleration.x, msg.linear_acceleration.y, msg.linear_acceleration.z = ax, ay, az
        imu_messages.append((stamp_ns, TOPICS["imu0"], msg))

    ordered = sorted(image_messages + imu_messages, key=lambda item: (item[0], item[1]))
    output.parent.mkdir(parents=True, exist_ok=True)
    with rosbag.Bag(str(output), "w") as bag:
        for stamp_ns, topic, message in ordered:
            bag.write(topic, message, t=_ros_stamp(rospy, stamp_ns))

    receipt = {
        "schema": "vector-openvins-euroc-sensor-bag/1",
        "bag_sha256": _digest_file(output),
        "camera0_frames": len(cameras[0]), "camera1_frames": len(cameras[1]),
        "imu_samples": len(imu_records), "t0_ns": cameras[0][0][0],
        "t1_ns": cameras[0][-1][0], "topics": TOPICS,
        "stereo_timestamp_parity": True,
        "truth_read": False,
        "image_sources_sha256": image_hashes,
        "imu_csv_sha256": imu_hash,
        "time_order": "ascending timestamp; lexical topic order for ties",
    }
    Path(receipt_path).write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    return receipt


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sensors", type=Path, required=True)
    parser.add_argument("--bag", type=Path, required=True)
    parser.add_argument("--receipt", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(write_bag(args.sensors, args.bag, args.receipt), indent=2))


if __name__ == "__main__":
    main()
