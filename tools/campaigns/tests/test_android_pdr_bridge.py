import csv
import json
import math
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import android_pdr_bridge


class AndroidPdrBridgeTests(unittest.TestCase):
    def make_imu(self, root, *, bad_order=False):
        path = root / "imu.csv"
        with path.open("w", newline="", encoding="utf-8") as file:
            w = csv.writer(file)
            w.writerow(["t_ns", "type", "x", "y", "z"])
            t0 = 1_700_000_000_000_000_000
            times = [t0 + i * 10_000_000 for i in range(5)]
            if bad_order:
                times[3] = times[1]
            for t in times:
                w.writerow([t, "GYRO", 0, 0, 0.03])
                w.writerow([t, "ACCEL", 0, 0, 9.80665])
            w.writerow([t0 + 50_000_000, "BARO", 1013.5, 0, 0])
        return path

    def test_camera_free_conversion_preserves_measured_accel_without_inventing_heading(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            imu = self.make_imu(root)
            out = root / "java" / "events.csv"
            result = android_pdr_bridge.convert(imu, out)
            rows = list(csv.DictReader(out.open(newline="", encoding="utf-8")))
            self.assertEqual(result["algorithm"], "Java PdrEngine/StepPeakDetector (NOT Python imu_pdr.py)")
            self.assertEqual(result["heading_provenance"], "none")
            self.assertEqual(result["input_counts"]["ACCEL"], 5)
            self.assertEqual(result["input_counts"]["GYRO"], 5)
            self.assertEqual(result["input_counts"]["BARO"], 1)
            self.assertEqual(result["input_counts"]["HEADING"], 0)
            self.assertEqual(len(rows), 6)
            self.assertEqual([r["type"] for r in rows].count("ACCEL"), 5)
            self.assertEqual([r["type"] for r in rows].count("PRESSURE"), 1)
            self.assertNotIn("GYRO", {r["type"] for r in rows})
            self.assertNotIn("HEADING", {r["type"] for r in rows})
            self.assertEqual(result["independent_reference"], "not supplied to engine")
            manifest = json.loads((out.parent / "events.manifest.json").read_text())
            self.assertEqual(manifest["replay_input_sha256"], android_pdr_bridge.sha256(out))

    def test_measured_heading_is_time_merged_and_declares_provenance(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            imu = self.make_imu(root)
            heading = root / "heading.csv"
            t0 = 1_700_000_000_000_000_000
            with heading.open("w", newline="", encoding="utf-8") as f:
                w = csv.writer(f)
                w.writerow(["t_ns", "heading_rad"])
                w.writerow([t0 + 5_000_000, 0.4])
                w.writerow([t0 + 30_000_000, -0.2])
            out = root / "events.csv"
            result = android_pdr_bridge.convert(
                imu, out, heading_csv=heading, heading_source="phone_rotation_vector_logged")
            data = list(csv.DictReader(out.open(newline="", encoding="utf-8")))
            timestamps = [int(r["t_ns"]) for r in data]
            self.assertEqual(timestamps, sorted(timestamps))
            self.assertEqual(result["input_counts"]["HEADING"], 2)
            self.assertEqual(result["heading_provenance"], "phone_rotation_vector_logged")
            self.assertEqual([r["type"] for r in data].count("HEADING"), 2)

    def test_synthetic_fixed_heading_is_explicitly_fixture_only(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            imu = self.make_imu(root)
            dest = root / "events.csv"
            with self.assertRaisesRegex(ValueError, "forbidden for real-data"):
                android_pdr_bridge.convert(imu, dest, constant_heading_rad=0.5)
            result = android_pdr_bridge.convert(
                imu, dest, constant_heading_rad=0.5, fixture_only=True)
            self.assertEqual(result["validation_level"], "synthetic_fixture")
            self.assertEqual(result["input_counts"]["HEADING"], 5)

    def test_nonnumeric_and_unsorted_inputs_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            imu = self.make_imu(root, bad_order=True)
            with self.assertRaisesRegex(ValueError, "Nonmonotonic"):
                android_pdr_bridge.convert(imu, root / "output.csv")
            self.make_imu(root)
            with imu.open("a", encoding="utf-8") as file:
                file.write("1700000000070000000,ACCEL,nan,0,9.81\n")
            with self.assertRaisesRegex(ValueError, "Nonfinite"):
                android_pdr_bridge.convert(imu, root / "output.csv")

    def test_missing_heading_provenance_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            imu = self.make_imu(root)
            heading = root / "heading.csv"
            heading.write_text("t_ns,heading_rad\n1700000000000000001,0.2\n")
            with self.assertRaisesRegex(ValueError, "heading source"):
                android_pdr_bridge.convert(imu, root / "events.csv", heading_csv=heading)


if __name__ == "__main__":
    unittest.main()
