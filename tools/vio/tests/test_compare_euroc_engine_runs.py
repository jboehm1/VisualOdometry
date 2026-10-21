import json
import tempfile
import unittest
from pathlib import Path

from tools.vio.compare_euroc_engine_runs import compare


class EuRoCEngineCompareTests(unittest.TestCase):
    def _reports(self):
        names = ["actual upstream Basalt C++ visual-inertial estimator",
                 "actual upstream ORB-SLAM3 stereo-inertial estimator",
                 "actual upstream OpenVINS stereo-inertial estimator"]
        reports = []
        for i, name in enumerate(names):
            reports.append({
                "schema": "vector-vio-host-real/1", "status": "MEASURED", "engine": name,
                "engine_source_sha": f"{i + 1:040x}", "sensor_tree_sha256": "a" * 64,
                "reference_sha256": "b" * 64, "pose_frame": "IMU/body",
                "metric_scale": "estimated_online; no reference scale fitted",
                "truth_available_to_engine": False,
                "runtime": {"network": "none", "truth_mount": False, "binary_sha256": "c" * 64,
                            "wall_seconds": 1.0, "child_cpu_seconds": 1.0, "child_peak_rss_kib": 100},
                "first_reference_pose_gauge_diagnostic": {
                    "ate_rmse_m": 0.1 + i, "rpe_translation_rmse_m": 0.01,
                    "tracking_coverage_time_fraction": 0.9, "pairs": 1200},
                "trajectory_sha256": "d" * 64,
            })
        return reports

    def test_requires_exactly_the_three_isolated_engine_receipts(self):
        with tempfile.TemporaryDirectory() as temp:
            paths = []
            for i, report in enumerate(self._reports()):
                path = Path(temp) / f"{i}.json"
                path.write_text(json.dumps(report))
                paths.append(path)
            result = compare(paths)
            self.assertEqual("NOT_QUALIFIED", result["ranking"])
            self.assertEqual(3, len(result["engines"]))

    def test_rejects_different_sensor_or_reference_inputs(self):
        with tempfile.TemporaryDirectory() as temp:
            reports = self._reports()
            reports[2]["sensor_tree_sha256"] = "e" * 64
            paths = []
            for i, report in enumerate(reports):
                path = Path(temp) / f"{i}.json"
                path.write_text(json.dumps(report))
                paths.append(path)
            with self.assertRaisesRegex(ValueError, "do not share sensor_tree_sha256"):
                compare(paths)


if __name__ == "__main__":
    unittest.main()
