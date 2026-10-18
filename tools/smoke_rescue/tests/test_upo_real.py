"""Unit tests for REAL UPO sensor adapter internals; these are NOT recorded smoke data."""
import importlib.util
import math
from pathlib import Path
import struct
import unittest
from types import SimpleNamespace as Obj

P = Path(__file__).resolve().parents[1] / "upo_real.py"
spec = importlib.util.spec_from_file_location("upo_real", P)
real = importlib.util.module_from_spec(spec)
spec.loader.exec_module(real)


def cloud(samples, endian=False, padding=0):
    fields = [Obj(name=k, offset=i * 4, datatype=7, count=1)
              for i, k in enumerate("xyz")]
    stride = len(samples) * 12 + padding
    raw = b"".join(struct.pack(">fff" if endian else "<fff", *s) for s in samples)
    raw += b"\x00" * padding
    return Obj(fields=fields, width=len(samples), height=1, point_step=12,
               row_step=stride, data=raw, is_bigendian=endian)


class RawSensorDecodeTests(unittest.TestCase):
    def test_pointcloud2_xyz_both_endian(self):
        for big in (False, True):
            result, total, step = real.cloud_xyz(cloud([(1.2, 2.3, 3.4), (4., 0., 0.)], big, 2))
            self.assertEqual(total, 2)
            self.assertEqual(step, 1)
            self.assertAlmostEqual(result[0][0], 1.2, places=4)

    def test_pointcloud_xyz_invalid(self):
        frame = cloud([(1., 2., 3.)])
        frame.fields[0].datatype = 6
        with self.assertRaises(ValueError):
            real.cloud_xyz(frame)
        frame = cloud([(1., 2., 3.)])
        frame.row_step = 11
        with self.assertRaises(ValueError):
            real.cloud_xyz(frame)
        frame = cloud([(1., 2., 3.)])
        frame.data = b"\x00"
        with self.assertRaises(ValueError):
            real.cloud_xyz(frame)

    def test_nonfinite_point_is_dropped(self):
        result, total, _ = real.cloud_xyz(cloud([(math.nan, 2, 3), (1, 1, 1)]))
        self.assertEqual(total, 2)
        self.assertEqual(len(result), 1)

    def test_quantile_and_nearest_reference(self):
        self.assertEqual(real.quantile([1, 2, 3], 0.5), 2.0)
        self.assertEqual(real.nearest_gap([0.0, 2.0], 1.7), .30000000000000004)
        self.assertIsNone(real.nearest_gap([], 5.0))

    def test_no_fake_slam_score(self):
        data = [{"sampled_valid": 0, "advertised_points": 0,
                 "time_s": 10.0, "ranges_median": None},
                {"sampled_valid": 2, "advertised_points": 2,
                 "time_s": 11.0, "ranges_median": 2.0}]
        result = real.summarize_cloud(data)
        self.assertEqual(result["frames"], 2)
        self.assertEqual(result["zero_usable_return_frames"], 1)
        self.assertNotIn("ate_rmse_m", result)
        self.assertNotIn("obstacle_recall", result)


if __name__ == "__main__":
    unittest.main()
