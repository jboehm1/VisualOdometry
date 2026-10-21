import csv
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from plot_session import load_samples, summarize


class PlotSessionTests(unittest.TestCase):
    def test_parses_pose_and_ignores_events(self):
        keys = [
            "event","wall_time_ms","frame_time_ns","tracking","x_m","y_m","z_m",
            "qx","qy","qz","qw","path_m","horizontal_m","displacement_m",
            "vertical_m","speed_mps","segments","gaps","outliers",
        ]
        with tempfile.TemporaryDirectory() as tmp:
            file = Path(tmp) / "run.csv"
            with file.open("w", newline="", encoding="utf-8") as f:
                writer = csv.DictWriter(f, fieldnames=keys)
                writer.writeheader()
                writer.writerow({"event": "START", "wall_time_ms": "1000"})
                writer.writerow({
                    "event":"POSE","wall_time_ms":"1100","frame_time_ns":"5000000000",
                    "tracking":"TRACKING","x_m":"0","y_m":"0","z_m":"0",
                    "path_m":"0","horizontal_m":"0","displacement_m":"0",
                    "vertical_m":"0","speed_mps":"0","segments":"1",
                    "gaps":"0","outliers":"0"})
                writer.writerow({
                    "event":"POSE","wall_time_ms":"2100","frame_time_ns":"6000000000",
                    "tracking":"TRACKING","x_m":"3","y_m":"0","z_m":"4",
                    "path_m":"5","horizontal_m":"5","displacement_m":"5",
                    "vertical_m":"0","speed_mps":"1","segments":"1",
                    "gaps":"0","outliers":"0"})
            samples, events = load_samples(file)
            self.assertEqual((len(samples), events), (2,1))
            result = summarize(samples, events, 5.0)
            self.assertEqual(result["relative_path_error_pct"], 0)
            self.assertEqual(result["estimated_path_m"], 5)

    def test_reference_must_be_positive(self):
        with self.assertRaises(ValueError):
            summarize([{
                "wall_time_ms":0,"path_m":1,"horizontal_m":1,"displacement_m":1,
                "vertical_m":0,"segments":1,"gaps":0,"outliers":0,"speed_mps":1
            }], 0, 0)

    def test_empty_session_is_error(self):
        with self.assertRaises(ValueError):
            summarize([], 0)


if __name__ == "__main__":
    unittest.main()
