import io
import csv
import sys
import tempfile
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from quality_report import analyze


class QualityReportTests(unittest.TestCase):
    def write(self, rows):
        f = tempfile.NamedTemporaryFile(suffix=".csv", delete=False, mode="w", encoding="utf8", newline="")
        with f:
            w = csv.writer(f)
            w.writerow(["wall_time_ms", "frame_time_ns", "tracking_state", "tracking_reason", "pose_hz", "segment", "recording", "gps_accuracy_m"])
            for r in rows:
                w.writerow([0, *r, 1, ""])
        self.addCleanup(lambda: Path(f.name).unlink(missing_ok=True))
        return f.name

    def test_counts_tracking_pauses_without_claiming_ground_truth(self):
        p = self.write([(1_000_000_000, "TRACKING", "NONE", 29.9, 1),
                        (2_000_000_000, "PAUSED", "INSUFFICIENT_FEATURES", 0, 1),
                        (4_500_000_000, "TRACKING", "NONE", 24.2, 2)])
        r = analyze(p, "forest_ground")
        self.assertEqual("forest_ground", r["scene"])
        self.assertEqual(2, r["distinct_segments"])
        self.assertEqual(1, r["intervals_over_2s"])
        self.assertEqual("NOT_AVAILABLE", r["accuracy_ground_truth"])
        self.assertAlmostEqual(27.05, r["median_tracked_pose_hz"])

    def test_rejects_invalid_monotonicity(self):
        p = self.write([(2, "TRACKING", "NONE", 5, 1), (1, "TRACKING", "NONE", 5, 1)])
        with self.assertRaises(ValueError):
            analyze(p)

    def test_motion_has_subsecond_gap_diagnostics_and_explicit_segments(self):
        p=self.write([(1_000_000_000,"TRACKING","NONE",29.5,1),
                      (2_000_000_000,"TRACKING","NONE",28.0,2)])
        with tempfile.NamedTemporaryFile(mode="w",suffix=".csv",delete=False) as tmp:
            tmp.write("frame_time_ns,metric_x,metric_y,metric_z,odometer_segment\n"
                      "1000000000,0,0,0,1\n1040000000,1,0,0,1\n"
                      "1250000000,2,0,0,2\n")
            name=tmp.name
        self.addCleanup(lambda:Path(name).unlink(missing_ok=True))
        motion=analyze(p,"under_trees",name)["motion"]
        self.assertEqual("TRACKED_POSES_ONLY",motion["status"])
        self.assertEqual(3,motion["pose_samples"])
        self.assertEqual(1,motion["intervals_over_100ms"])
        self.assertEqual(2,motion["segment_count"])
        self.assertEqual(.21,motion["max_frame_interval_s"])
    def test_motion_rejects_wrong_segment_or_timestamp(self):
        with tempfile.NamedTemporaryFile(mode="w",suffix=".csv",delete=False) as tmp:
            tmp.write("frame_time_ns,metric_x,metric_y,metric_z,odometer_segment\n"
                      "1000000000,0,0,0,2\n1000000000,1,0,0,1\n")
            name=tmp.name
        self.addCleanup(lambda:Path(name).unlink(missing_ok=True))
        from quality_report import analyze_motion
        with self.assertRaises(ValueError):analyze_motion(name)
    def test_empty_file_is_not_success_claim(self):
        p = self.write([])
        self.assertEqual("NO_DATA", analyze(p)["status"])


if __name__ == "__main__":
    unittest.main()
