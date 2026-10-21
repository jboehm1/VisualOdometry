import csv
import gzip
import json
import math
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import bench


def write_tum(path, samples):
    path.write_text("\n".join(" ".join(str(v) for v in row) for row in samples) + "\n")


class BenchmarkTests(unittest.TestCase):
    def test_vector_nonfinite_orientation_cannot_produce_a_position_only_success(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp)/'vector.csv'
            for invalid in ('nan', 'inf', '-inf', '1e200'):
                with self.subTest(quaternion_component=invalid):
                    path.write_text('event,tracking,frame_time_ns,x_m,y_m,z_m,qx,qy,qz,qw,segments\n'+''.join(
                        f'POSE,TRACKING,{1_000_000_000+i*100_000_000},0,0,0,{invalid},0,0,1,1\n'
                        for i in range(3)))
                    with self.assertRaisesRegex(ValueError, 'Invalid VECTOR pose'):
                        bench.parse_poses(path, 'vector')

    def test_euroc_wxyz_and_tum_xyzw_describe_the_same_nontrivial_pose(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); euroc = root/'euroc.csv'; tum = root/'tum.txt'
            # +90 degree yaw: wxyz in EuRoC, xyzw in TUM. Identity alone
            # cannot expose a mistaken ordering in translation-only metrics.
            q = math.sqrt(.5)
            euroc.write_text(''.join(f'{1_000_000_000+i*100_000_000},0,{i},0,{q},0,0,{q}\n' for i in range(20)))
            tum.write_text(''.join(f'{1+i*.1:.9f} 0 {i} 0 0 0 {q} {q}\n' for i in range(20)))
            a, b = bench.parse_poses(euroc,'euroc'), bench.parse_poses(tum,'tum')
            import numpy as np
            np.testing.assert_allclose(a[2],b[2],atol=1e-12)
            np.testing.assert_allclose(a[2][0] @ np.array([1,0,0]),[0,1,0],atol=1e-12)
            report = bench.measure(a,b,align='none',delta_seconds=.5)
            self.assertLess(report['rpe_translation_rmse_m'],1e-10)
            self.assertLess(report['rpe_rotation_rmse_deg'],1e-5)

    def test_perfect_rigid_alignment_and_metric_scale(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            truth, estimated = root / "gt.tum", root / "est.tum"
            gt, est = [], []
            a = math.radians(40)
            c, s = math.cos(a), math.sin(a)
            for i in range(101):
                t = i * 0.1
                xyz = (0.2 * i, math.sin(i/11), math.cos(i/13))
                ex = c*xyz[0]-s*xyz[1]+3
                ey = s*xyz[0]+c*xyz[1]-5
                gt.append((t, *xyz, 0, 0, 0, 1))
                # Translation + yaw-rotation; full orientation is also transformed.
                est.append((t, ex, ey, xyz[2]+1, 0, 0,
                            math.sin(a/2), math.cos(a/2)))
            write_tum(truth, gt)
            write_tum(estimated, est)
            g = bench.parse_poses(truth, "tum")
            e = bench.parse_poses(estimated, "tum")
            report = bench.measure(g, e)
            self.assertLess(report["ate_rmse_m"], 1e-6)
            self.assertLess(report["rpe_translation_rmse_m"], 1e-6)
            self.assertLess(report["rpe_rotation_rmse_deg"], 1e-4)
            self.assertAlmostEqual(report["distance_error_pct"], 0, places=5)
            self.assertAlmostEqual(report["tracking_coverage_time_fraction"], 1, places=5)
            self.assertEqual(report["estimated_scale_correction"], 1)
            self.assertTrue(report["metric_scale_preserved"])

    def test_scale_change_is_detected_without_hidden_sim3(self):
        with tempfile.TemporaryDirectory() as tmp:
            gt = Path(tmp) / "gt.tum"
            estimate = Path(tmp) / "est.tum"
            write_tum(gt, [(i*0.1, i*0.2, math.sin(i/9), 0, 0,0,0,1)
                           for i in range(61)])
            write_tum(estimate, [(i*0.1, i*0.24, math.sin(i/9)*1.2, 0, 0,0,0,1)
                                 for i in range(61)])
            g = bench.parse_poses(gt, "tum")
            e = bench.parse_poses(estimate, "tum")
            rigid = bench.measure(g, e)
            sim = bench.measure(g, e, align="sim3")
            self.assertGreater(rigid["ate_rmse_m"], 0.10)
            self.assertLess(sim["ate_rmse_m"], 1e-6)
            self.assertFalse(sim["metric_scale_preserved"])
            self.assertAlmostEqual(sim["estimated_scale_correction"], 1/1.2, places=5)

    def test_missing_association_fails_loudly(self):
        with tempfile.TemporaryDirectory() as tmp:
            gt, est = Path(tmp)/"gt.tum", Path(tmp)/"est.tum"
            write_tum(gt, [(i*0.1, i, 0, 0, 0,0,0,1) for i in range(30)])
            write_tum(est, [(100+i*0.1, i, 0, 0, 0,0,0,1) for i in range(30)])
            with self.assertRaisesRegex(ValueError, "time matches"):
                bench.measure(bench.parse_poses(gt, "tum"), bench.parse_poses(est, "tum"))

    def test_prepare_native_euroc_and_do_not_move_truth_into_engine_inputs(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "MH_01_easy" / "mav0"
            cam = root / "cam0"
            (cam / "data").mkdir(parents=True)
            (root / "imu0").mkdir()
            (root / "state_groundtruth_estimate0").mkdir()
            with (cam / "data.csv").open("w") as f:
                f.write("#timestamp [ns],filename\n")
                for i in range(4):
                    t = 1_000_000_000+i*50_000_000
                    f.write(f"{t},{t}.png\n")
                    (cam / "data" / f"{t}.png").write_bytes(b"dummy")
            with (root / "imu0" / "data.csv").open("w") as f:
                f.write("#timestamp [ns],w_x,w_y,w_z,a_x,a_y,a_z\n")
                for i in range(15):
                    f.write(f"{1_000_000_000+i*5_000_000},0,0,0,0,0,9.8\n")
            (root / "state_groundtruth_estimate0" / "data.csv").write_text(
                "#timestamp,xyz,qxyzw\n1000000000,0,0,0,0,0,0,1\n")
            result = bench.prepare_mav("euroc", root.parent, Path(tmp)/"prepared")
            self.assertEqual(result["frame_count"], 4)
            self.assertEqual(result["imu_count"], 15)
            self.assertEqual(len(list(bench.rows(Path(tmp)/"prepared"/"imu.csv"))), 31)
            self.assertEqual(result["truth_frame"], "imu")
            self.assertFalse((Path(tmp)/"prepared"/"truth.tum").exists())

    def test_capture_index_only_no_truth(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)/"capture_20261018_123000_000"
            root.mkdir()
            (root/"part000.mp4").write_bytes(b"video bytes placeholder")
            with gzip.open(root/"frames.csv.gz", "wt") as f:
                f.write("part,frame_number,sensor_time_ns,elapsed_ns,exposure_ns,rolling_shutter_ns,iso,focal_mm\n")
                f.write("part000.mp4,1,1000000000,1000000000,0,0,100,4\n")
            with gzip.open(root/"imu.csv.gz", "wt") as f:
                f.write("sensor_time_ns,type,x,y,z\n1000000000,ACCEL,0,0,9.81\n")
                f.write("1005000000,GYRO,0,0,0.02\n")
            result = bench.prepare_collection(root, Path(tmp)/"prepared")
            self.assertIsNone(result["truth"])
            self.assertEqual(result["frame_count"], 1)
            self.assertEqual(result["imu_count"], 2)

    def test_cli_rejects_mismatched_pose_frames(self):
        with self.assertRaisesRegex(ValueError, "frames differ"):
            bench.main(["evaluate","--truth","notfound","--estimate","notfound",
                        "--truth-frame","imu","--estimate-frame","camera",
                        "--out","ignored.json"])

    def test_euroc_timestamp_and_quaternion_are_parsed(self):
        with tempfile.TemporaryDirectory() as tmp:
            gt = Path(tmp)/"gt.csv"
            gt.write_text("#timestamp,p_x,p_y,p_z,q_w,q_x,q_y,q_z,vx\n" +
                          "\n".join(f"{1_000_000_000+i*100_000_000},{i},0,0,1,0,0,0,0"
                                    for i in range(4)))
            poses = bench.parse_poses(gt, "euroc")
            self.assertAlmostEqual(poses[0][-1]-poses[0][0], 0.3, places=6)

    def test_engine_command_is_argv_and_no_truth_placeholder(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root/"prepared").mkdir()
            bench.write_json(root/"prepared"/"manifest.json",
                             {"frames":"f.csv","imu":"i.csv",
                              "truth":"PRIVATE_TRUTH_SHOULD_NOT_BE_PASSED"})
            stub = "import sys,pathlib; pathlib.Path(sys.argv[1]).write_text('0 0 0 0 0 0 0 1\\n')"
            engine = root/"engine.json"
            bench.write_json(engine, {"name":"stub-not-odometry",
                                     "argv":[sys.executable,"-c",stub,"{output}"]})
            result = bench.execute_engine(engine, root/"prepared", root/"out")
            self.assertEqual(result["returncode"], 0)
            self.assertTrue((root/"out"/"trajectory.tum").exists())
            self.assertNotIn("PRIVATE_TRUTH", json.dumps(result))


    def test_generic_imu_only_with_magnetometer_and_barometer(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root/"gyro.csv").write_text("time_s,gx,gy,gz\n1.00,0,0,0.1\n1.02,0,0,0.2\n")
            (root/"accel.csv").write_text("time_s,ax,ay,az\n1.01,0,0,9.81\n1.03,0,0,9.80\n")
            (root/"baro.csv").write_text("time_s,pressure_hpa\n1.00,1013.25\n")
            cfg = {
                "pose_frame":"device",
                "sensors":[
                    {"csv":"gyro.csv","time_column":"time_s","time_unit":"s",
                     "type":"GYRO","columns":["gx","gy","gz"]},
                    {"csv":"accel.csv","time_column":"time_s","time_unit":"s",
                     "type":"ACCEL","columns":["ax","ay","az"]},
                    {"csv":"baro.csv","time_column":"time_s","time_unit":"s",
                     "type":"BARO","columns":["pressure_hpa"]}
                ]
            }
            bench.write_json(root/"adapter.json",cfg)
            result = bench.prepare_generic(root, root/"adapter.json", root/"out")
            self.assertEqual(result["frame_count"],0)
            self.assertEqual(result["imu_count"],5)
            entries = list(bench.rows(root/"out"/"imu.csv"))
            times = [int(row[0]) for row in entries[1:]]
            self.assertEqual(times, sorted(times))
            self.assertIn("BARO", [row[1] for row in entries[1:]])
            self.assertIsNone(result["truth"])

    def test_generic_blocks_resource_escape(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root/"frames.csv").write_text("time,image\n1.0,../private.png\n2.0,safe.png\n")
            (root/"safe.png").write_bytes(b"x")
            cfg = {"frames":{"csv":"frames.csv","time_column":"time",
                             "time_unit":"s","resource_column":"image",
                             "resource_root":".","camera":"cam0"}}
            bench.write_json(root/"adapter.json",cfg)
            with self.assertRaisesRegex(ValueError,"relative"):
                bench.prepare_generic(root, root/"adapter.json", root/"out")

if __name__ == "__main__":
    unittest.main()
