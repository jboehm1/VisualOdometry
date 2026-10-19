"""Clock calibration and TF graph research-only deterministic tests."""
import importlib.util
import math
from pathlib import Path
import sys
import unittest
P=Path(__file__).resolve().parents[1]/"calibration_audit.py"
spec=importlib.util.spec_from_file_location("calibration_audit",P)
m=importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class SensorOnlyCalibration(unittest.TestCase):
    def test_drifting_device_clock(self):
        base=1_606_476_260.
        records=[]
        for i in range(2000):
            device=1234+i*.01
            # Non-negative event time jitter, fixed unknown delay and 20ppm drift.
            host=base+1.000020*device+.012+(i%17)*.002
            records.append((device,host))
        result=m.clock_fit(records)
        self.assertLess(abs(result["reported_clock_drift_ppm"]-20),3)
        self.assertEqual(result["reference_pose_used"],False)
        self.assertGreater(result["p95_minus_p05_arrival_delay_ms"],10)

    def test_bad_clock_is_rejected(self):
        with self.assertRaises(ValueError):
            m.clock_fit([(0.,0.)])
        with self.assertRaises(ValueError):
            m.clock_fit([(i*.02,1e6+1.02*i*.02) for i in range(2000)])

    def test_nested_tf_translation_yaw(self):
        q=[0,0,math.sin(math.pi/4),math.cos(math.pi/4)]
        g={"imu":{"parent":"base_link","xyz":[1,0,.2],"q":q},
           "radar":{"parent":"imu","xyz":[1,0,.1],"q":[0,0,0,1]}}
        result=m.tf_lookup(g,"radar")
        self.assertAlmostEqual(result["xyz_m"][0],1.,places=5)
        self.assertAlmostEqual(result["xyz_m"][1],1.,places=5)
        self.assertAlmostEqual(result["yaw_rad"],math.pi/2,places=5)
        self.assertAlmostEqual(result["xyz_m"][2],.3,places=5)

    def test_unknown_extrinsics_fail_closed(self):
        self.assertIsNone(m.tf_lookup({},"unknown"))
        self.assertIsNone(m.tf_lookup({"a":{"parent":"b","xyz":[0,0,0],"q":[0,0,0,1]},
                                      "b":{"parent":"a","xyz":[0,0,0],"q":[0,0,0,1]}},"a"))

    def test_bad_quaternion(self):
        with self.assertRaises(ValueError):
            m.rotation_y([0,0,0,0])


if __name__=="__main__":
    unittest.main()
