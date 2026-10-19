"""Offline regression tests; synthetic fixtures only, NOT real-fire proof."""
import importlib.util
import math
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "replay.py"
spec = importlib.util.spec_from_file_location("smoke_replay", SCRIPT)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


class SmokeReplayTest(unittest.TestCase):
    def test_demo_occupancy_and_html(self):
        frames = mod.parse_frames(mod.demo_frames())
        self.assertEqual(len(frames), 11)
        cells, poses, thermal = mod.make_map(frames)
        self.assertTrue(any(value > 1.0 for value in cells.values()))
        self.assertTrue(any(value < -0.65 for value in cells.values()))
        self.assertEqual(len(thermal), 2)
        text = mod.render_html(cells, poses, thermal, 0.25, {"synthetic"})
        self.assertIn("RECHERCHE UNIQUEMENT", text)
        self.assertIn("synthetic", text)
        self.assertIn("<svg", text)
        self.assertNotIn("__CELLS__", text)

    def test_cli_writes_page(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / "map.html"
            self.assertEqual(mod.main(["--demo", "--out", str(p)]), 0)
            self.assertIn("RECHERCHE UNIQUEMENT", p.read_text(encoding="utf-8"))

    def test_no_empty_or_nonmonotonic_time(self):
        with self.assertRaises(ValueError):
            mod.parse_frames([])
        lines = mod.demo_frames()
        with self.assertRaisesRegex(ValueError, "strictly increasing"):
            mod.parse_frames([lines[0], lines[0]])

    def test_no_silent_pose_or_degrees(self):
        import json
        example = json.loads(mod.demo_frames()[0])
        example["pose"].pop("source")
        with self.assertRaises(ValueError):
            mod.parse_frames([json.dumps(example)])
        example["pose"]["source"] = "unverified_estimate"
        example["pose"]["yaw_rad"] = 180
        with self.assertRaises(ValueError):
            mod.parse_frames([json.dumps(example)])

    def test_nonfinite_and_outofrange_rejected(self):
        import json
        example = json.loads(mod.demo_frames()[0])
        example["radar"][0]["range_m"] = float("nan")
        with self.assertRaises(ValueError):
            mod.parse_frames([json.dumps(example)])
        example["radar"][0]["range_m"] = 30
        with self.assertRaises(ValueError):
            mod.parse_frames([json.dumps(example)])
        example["radar"][0]["range_m"] = 1
        example["radar"][0]["azimuth_rad"] = math.pi * 2
        with self.assertRaises(ValueError):
            mod.parse_frames([json.dumps(example)])

    def test_no_return_is_not_free_space(self):
        import json
        example = json.loads(mod.demo_frames()[0])
        example["radar"] = []
        cells, poses, thermal = mod.make_map(mod.parse_frames([json.dumps(example)]))
        self.assertEqual(cells, {})
        self.assertEqual(poses, [(0.0, 0.0)])
        self.assertEqual(thermal, [])

    def test_line_cells_all_octants(self):
        for dest in [(3, 1), (1, 3), (-3, 1), (-1, 3), (-3, -1), (1, -3), (3, -1)]:
            ray = list(mod.line_cells((0, 0), dest))
            self.assertEqual(ray[0], (0, 0))
            self.assertEqual(ray[-1], dest)
            self.assertLessEqual(len(ray), 5)

    def test_thermal_is_not_obstacle(self):
        import json
        example = json.loads(mod.demo_frames()[0])
        example["radar"] = []
        example["thermal_bearings"] = [0.1]
        cells, _, bearings = mod.make_map(mod.parse_frames([json.dumps(example)]))
        self.assertEqual(cells, {})
        self.assertEqual(len(bearings), 1)


if __name__ == "__main__":
    unittest.main()
