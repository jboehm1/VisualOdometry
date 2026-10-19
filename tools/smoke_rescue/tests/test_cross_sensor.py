"""Real source cross-sensor audit: deterministic mathematical tests only."""
import importlib.util
from pathlib import Path
import sys
import unittest
P=Path(__file__).resolve().parents[1]/"cross_sensor.py"
sys.path.insert(0,str(P.parent))
spec=importlib.util.spec_from_file_location("cross_sensor",P)
m=importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class CrossSensorMathTests(unittest.TestCase):
    def test_grid_proximity(self):
        points={(5,5):[(1.0,1.0)],(15,0):[(3.,0.)]}
        self.assertAlmostEqual(m.min_xy_proximity((1.02,1.03),points),.0360555,places=5)
        self.assertIsNone(m.min_xy_proximity((0,0),points,.1))

    def test_empty_cross_sensor_never_means_free(self):
        report=m.summarize_matches([])
        self.assertEqual(report["status"],"NO_MATCHED_RADAR_AND_LIDAR_ECHOES")
        report=m.summarize_matches([
            {"nearby_lidar_xy_m":None,"time_gap_ms":2},
            {"nearby_lidar_xy_m":.4,"time_gap_ms":8}
        ])
        self.assertEqual(report["fraction_lidar_xy_within_0_5m"],.5)
        self.assertIn("NOT independent obstacle",report["semantics"])

    def test_transform_coordinates(self):
        tf={"yaw_rad":0.,"xyz_m":[.1,.2,.3]}
        self.assertEqual(m.to_base(tf,(1.,2.,3.)),(1.1,2.2,3.3))


if __name__=="__main__":
    unittest.main()
