#!/usr/bin/env python3
"""Parse external ROS1 rosbag info text safely; NO bag data decoded.

This parser cannot prove smoke penetration, timestamps, 3D mapping or GT.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import urlopen

MAX_BYTES = 100_000
DURATION = re.compile(r"^duration:\s+\S+\s+\((\d+)s\)$")
TOPIC = re.compile(r"^\s*(/\S+)\s+(\d+)\s+msgs?\s+:\s+(\S+)\s*$")
EXPECTED = {
    "/ti_mmwave_left_radar/radar_scan_pcl": "sensor_msgs/PointCloud2",
    "/ti_mmwave_right_radar/radar_scan_pcl": "sensor_msgs/PointCloud2",
    "/os1_cloud_node/points_non_dense": "sensor_msgs/PointCloud2",
    "/ground_truth": "geometry_msgs/PoseStamped",
}


def parse_rosbag_info(source: str, origin: str = "local_fixture"):
    if len(source.encode("utf-8")) > MAX_BYTES:
        raise ValueError("ROS index too large")
    if not isinstance(origin, str) or len(origin) > 250:
        raise ValueError("invalid origin")
    found = {}
    duration = None
    path = None
    total_messages = None
    for line in source.splitlines():
        stripped = line.strip()
        if stripped.startswith("path:"):
            path = stripped.partition(":")[2].strip()
            if not re.fullmatch(r"[a-zA-Z0-9_\-]+\.bag", path):
                raise ValueError("unexpected bag filename")
        elif stripped.startswith("messages:"):
            v = stripped.partition(":")[2].strip()
            if not v.isdigit():
                raise ValueError("invalid message count")
            total_messages = int(v)
        elif stripped.startswith("duration:"):
            match = DURATION.fullmatch(stripped)
            if not match:
                raise ValueError("bad ROS duration")
            duration = int(match.group(1))
        else:
            match = TOPIC.fullmatch(line)
            if match:
                name, count, kind = match.groups()
                if name in found:
                    raise ValueError("duplicate ROS topic " + name)
                found[name] = {"messages": int(count), "type": kind}
    if not path or duration is None or duration <= 0 or not found:
        raise ValueError("missing ROS bag metadata")
    for name, kind in EXPECTED.items():
        if name not in found or found[name]["type"] != kind:
            raise ValueError(f"missing or wrong type for required topic: {name}")
        if found[name]["messages"] <= 0:
            raise ValueError("empty required topic: " + name)
    return {
        "source": origin,
        "bag": path,
        "duration_s": duration,
        "advertised_messages": total_messages,
        "topics": found,
        "source_text_sha256": hashlib.sha256(source.encode("utf-8")).hexdigest(),
        "recording_downloaded": False,
        "actual_sensor_messages_decoded": 0,
        "ground_truth_validated": False,
        "safety": "INDEX_METADATA_ONLY",
        "reference_warning": "Marker-assisted poses partly smoothed with robot odometry; do not claim fully independent GT.",
    }


def fetch_index(url: str, timeout: float = 8) -> str:
    parsed = urlparse(url)
    if (
        parsed.scheme != "https"
        or parsed.hostname != "robotics.upo.es"
        or not re.fullmatch(r"/datasets/smoke/experiment_[1-7]\.txt", parsed.path)
        or parsed.query or parsed.fragment or parsed.username or parsed.password
    ):
        raise ValueError("only official UPO smoke index URLs are allowed")
    with urlopen(url, timeout=timeout) as response:
        effective = response.geturl()
        if effective != url:
            raise ValueError("index redirects are not permitted")
        data = response.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise ValueError("index exceeds safe size cap")
    return data.decode("utf-8", errors="strict")


def main(argv=None):
    parser = argparse.ArgumentParser(description="Verify official UPO smoke ROS index metadata, not bag contents")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--input", type=Path, help="Local rosbag info file")
    group.add_argument("--fetch-upo", action="store_true", help="Explicit opt-in remote index fetch only (~3KB), not 2GB bag")
    parser.add_argument("--out", type=Path, help="Write checked index metadata JSON")
    args = parser.parse_args(argv)
    url = "https://robotics.upo.es/datasets/smoke/experiment_7.txt"
    content = fetch_index(url) if args.fetch_upo else args.input.read_text(encoding="utf-8")
    obj = parse_rosbag_info(content, origin=url if args.fetch_upo else str(args.input))
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(obj, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(obj, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
