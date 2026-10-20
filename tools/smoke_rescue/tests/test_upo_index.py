"""Contract tests on REAL-LAYOUT index fixture, not original sensor recordings."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "upo_index.py"
spec = importlib.util.spec_from_file_location("upo_index", SCRIPT)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

# Text format follows official published experiment_7.txt; counts copied from it.
# Fixture may omit other source topics and is NOT an actual ROS bag download.
INFO = """path:        experiment_7.bag
version:     2.0
duration:    2:48s (168s)
messages:    244206
topics:      /ground_truth                               1569 msgs    : geometry_msgs/PoseStamped
             /os1_cloud_node/points_non_dense            1681 msgs    : sensor_msgs/PointCloud2
             /ti_mmwave_left_radar/radar_scan_pcl        2769 msgs    : sensor_msgs/PointCloud2
             /ti_mmwave_right_radar/radar_scan_pcl       2706 msgs    : sensor_msgs/PointCloud2
"""


class UpOIndexTests(unittest.TestCase):
    def test_dataset_index(self):
        d = mod.parse_rosbag_info(INFO)
        self.assertEqual(d["duration_s"], 168)
        self.assertEqual(d["advertised_messages"], 244206)
        self.assertEqual(d["topics"]["/ti_mmwave_left_radar/radar_scan_pcl"]["messages"], 2769)
        self.assertFalse(d["recording_downloaded"])
        self.assertEqual(d["actual_sensor_messages_decoded"], 0)
        self.assertFalse(d["ground_truth_validated"])

    def test_no_fabricated_topics_or_gt(self):
        with self.assertRaisesRegex(ValueError, "missing"):
            mod.parse_rosbag_info(INFO.replace("/ground_truth", "/ignored_topic"))
        with self.assertRaisesRegex(ValueError, "duplicate"):
            mod.parse_rosbag_info(INFO + " /ground_truth 99 msgs : geometry_msgs/PoseStamped\n")

    def test_bounds(self):
        with self.assertRaises(ValueError):
            mod.parse_rosbag_info(INFO * 1000)
        with self.assertRaises(ValueError):
            mod.parse_rosbag_info(INFO.replace("2:48s (168s)", "invalid"))
        with self.assertRaises(ValueError):
            mod.parse_rosbag_info(INFO.replace("experiment_7.bag", "../escape.bag"))

    def test_source_allowlist(self):
        for url in ("http://robotics.upo.es/datasets/smoke/experiment_7.txt",
                    "https://evil.com/datasets/smoke/experiment_7.txt",
                    "https://robotics.upo.es/datasets/smoke/experiment_7.bag"):
            with self.assertRaises(ValueError):
                mod.fetch_index(url)

    def test_cli_file_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / "experiment_7.txt"
            output = Path(tmp) / "verified.json"
            p.write_text(INFO, encoding="utf-8")
            self.assertEqual(mod.main(["--input", str(p), "--out", str(output)]), 0)
            obj = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(obj["safety"], "INDEX_METADATA_ONLY")


if __name__ == "__main__":
    unittest.main()
