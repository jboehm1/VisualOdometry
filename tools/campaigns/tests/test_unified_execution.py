"""Actual fixture execution and Java replay command construction regression."""
import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path
from argparse import Namespace
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import unified


def fixture(folder):
    p=folder/"imu.csv"
    with p.open("w",newline="") as f:
        w=csv.writer(f)
        w.writerow(("t_ns","type","x","y","z"))
        for i in range(500):
            t=1_000_000_000+i*10_000_000
            w.writerow((t,"GYRO",0,0,0))
            w.writerow((t,"ACCEL",0,0,9.80665+(2.2 if i%50 in (3,4) else 0)))
    return p


def opts(folder, engine, imu):
    return Namespace(dataset="canonical",engine=engine,out=folder/"results",
                     input=imu,root=None,estimate=None,truth=None,
                     heading_csv=None,heading_source=None,known_distance=None,
                     stride_m=.70,yaw_axis="z",timeout_s=120)


class ExecutableTests(unittest.TestCase):
    def test_python_real_engine_on_deterministic_imu_fixture(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            a=opts(root,"python-pdr",fixture(root))
            result=unified.run(a)
            self.assertEqual(result["status"],"PASS")
            self.assertEqual(result["measured_engine"],"Python_fixed_stride_peak_baseline")
            self.assertTrue(result["actual_estimator_executed"])
            self.assertFalse(result["reference_used_to_score"])
            self.assertFalse(result["metric_distance_accuracy_proven"])
            self.assertGreater(result["metrics"]["frames_processed"],100)
            self.assertNotIn("ate_rmse_m",result["metrics"])

    def test_java_receives_measured_heading_and_stride_not_default(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            a=opts(root,"android-pdr",fixture(root))
            a.stride_m=.76
            heading=root/"device_heading.csv"
            heading.write_text("t_ns,heading_rad\n1000000000,0.2\n1500000000,0.4\n")
            a.heading_csv=heading
            a.heading_source="phone_rotation_vector_logged"
            def fake_compile(dst):
                return root/"fake_classes", [{"source":"pinned Java"}]
            def fake_java(cmd,dst,*,timeout_s):
                self.assertEqual(cmd[0],"java")
                self.assertIn("PdrReplay",cmd[3])
                (dst/"engine_stdout.log").write_text(
                    '{"steps":7,"distance_m":5.32,"rows":503,"rejected_rows":0}')
                (dst/"engine_stderr.log").write_text("")
            with patch("simul_real.compile_java",side_effect=fake_compile):
                with patch.object(unified,"_invoke",side_effect=fake_java):
                    result=unified.run(a)
            self.assertEqual(result["status"],"PASS")
            self.assertEqual(result["metrics"]["distance_m"],5.32)
            self.assertEqual(result["provenance"]["configured_stride_m"],.76)
            self.assertEqual(result["provenance"]["bridge_manifest"]["heading_provenance"],
                             "phone_rotation_vector_logged")
            with (a.out/"java_configured_events.csv").open(newline="") as f:
                rows=list(csv.DictReader(f))
            self.assertEqual(rows[0]["type"],"STEP_LENGTH")
            self.assertAlmostEqual(float(rows[0]["x"]),.76)
            self.assertEqual(sum(r["type"]=="HEADING" for r in rows),2)

    def test_python_rejects_a_heading_file_it_cannot_consume(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            a=opts(root,"python-pdr",fixture(root))
            a.heading_source="phone_device_heading"
            with self.assertRaisesRegex(ValueError,"not a measured heading"):
                unified.run(a)

    def test_truth_cannot_enter_java_estimator(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            a=opts(root,"android-pdr",fixture(root))
            a.truth=root/"gt.csv"
            with self.assertRaisesRegex(ValueError,"does NOT score"):
                unified.run(a)


if __name__=="__main__":
    unittest.main()
