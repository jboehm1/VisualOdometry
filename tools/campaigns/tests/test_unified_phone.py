"""Real-phone dataset capabilities, no fabricated Android metrics; offline test doubles."""
import json
import sys
import tempfile
import unittest
from argparse import Namespace
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import unified


def options(folder,dataset,engine):
    return Namespace(out=folder/"out", dataset=dataset, engine=engine,
                     input=None, truth=None, estimate=None, root=None,
                     heading_csv=None, heading_source=None, known_distance=None,
                     stride_m=.70,yaw_axis="z",timeout_s=30)


class RealPhoneUnification(unittest.TestCase):
    def test_capabilities_are_truthfully_separated(self):
        items={row["id"]:row for row in unified.available_catalog()}
        self.assertEqual(items["advio09"]["supported_engines"],["external-pixel-arcore"])
        self.assertEqual(items["mobile_gvio"]["supported_engines"],["research-mono-gyro"])
        self.assertIn("research_shape_only",unified.plan("mobile_gvio","research-mono-gyro")["accuracy_claim_level"])
        self.assertEqual(unified.plan("advio09","external-pixel-arcore")["input_policy"],"pinned_public")
        for dataset,engine in [("advio09","android-arcore"),("mobile_gvio","external-pixel-arcore"),
                               ("euroc","research-mono-gyro"),("advio09","research-mono-gyro")]:
            with self.subTest(dataset=dataset,engine=engine),self.assertRaisesRegex(ValueError,"incompatible"):
                unified.plan(dataset,engine)

    def test_pinned_source_override_is_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            a=options(Path(d),"advio09","external-pixel-arcore")
            a.truth=Path("fake_truth.csv")
            with self.assertRaisesRegex(ValueError,"cannot be overridden"):
                unified.run(a)
            self.assertEqual(json.loads((a.out/"report.json").read_text())["status"],"FAILED")
            b=options(Path(d),"mobile_gvio","research-mono-gyro")
            b.input=Path("fake.csv")
            with self.assertRaisesRegex(ValueError,"cannot be overridden"):
                unified.run(b)

    def test_advio_is_only_external_estimator_score(self):
        with tempfile.TemporaryDirectory() as d:
            a=options(Path(d),"advio09","external-pixel-arcore")
            def record(cmd,out,*,timeout_s):
                folder=out/"advio";folder.mkdir()
                raw={"status":"PASS","source_record":"zenodo","source_zip_md5":"md5:pinned",
                     "license":"CC BY-NC 4.0",
                     "raw_multimodal_audit":{"raw_camera_and_gyro_processed":True,
                     "estimated_pose_from_these_raw_signals":False,
                     "raw_video_frames_decoded":500,"real_gyro_samples_in_camera_window":831,
                     "tracked_optical_flow_features":11903},
                     "external_vision_imu_estimator_score":{
                       "actual_app_VECT0R_estimator_executed":False,
                       "matched_external_phone_pose_pairs":2687,
                       "ate_rmse_m_rigid_SE3_no_scale_fitting":2.07,
                       "reference_kind":"fixation_aided_inertial_trajectory",
                       "truth_independence_note":"not fully optical independent"},
                     "external_pose_is_not_our_android_app":True}
                (folder/"report.json").write_text(json.dumps(raw))
            with patch.object(unified,"_invoke",side_effect=record):
                got=unified.run(a)
            self.assertFalse(got["actual_estimator_executed"])
            self.assertTrue(got["reference_used_to_score"])
            self.assertFalse(got["metric_distance_accuracy_proven"])
            self.assertNotIn("ate_rmse_m",got["metrics"])
            self.assertEqual(got["metrics"]["external_pose_pairs"],2687)

    def test_mobile_run_must_process_camera_and_imu_before_ground_truth(self):
        with tempfile.TemporaryDirectory() as d:
            a=options(Path(d),"mobile_gvio","research-mono-gyro")
            commands=[]
            def record(cmd,out,*,timeout_s):
                commands.append(cmd[1])
                if cmd[1].endswith("mobile_gvio_fetch.py"):
                    root=out/"mobile_dataset";root.mkdir()
                    (root/"provenance.json").write_text(json.dumps({"dataset":"genuine","archives":{}}))
                elif cmd[1].endswith("mobile_gvio_raw.py"):
                    self.assertNotIn("--truth",cmd)
                    folder=out/"inferred";folder.mkdir()
                    (folder/"inference.json").write_text(json.dumps({
                        "camera_images_and_imu_both_influence_pose":True,
                        "actual_images_processed":131,
                        "gyro_integrated_samples":2185,
                        "visual_essential_pose_updates":130,
                        "calibration_sha256":"sha256",
                        "clock_basis":"ROS_msg_header.stamp_original_device_capture_time"}))
                elif cmd[1].endswith("mobile_gvio_score.py"):
                    self.assertIn("--truth",cmd)
                    folder=out/"inferred"
                    (folder/"score.json").write_text(json.dumps({
                        "matched_pose_pairs":124,
                        "estimator_metric_scale_observed":False,
                        "scale_optimized_from_gt_only_during_scoring":True,
                        "clock_shift_fitted_using_reference_after_inference":True,
                        "timestamp_calibration_independently_verified":False,
                        "sim3_shape_ate_rmse_m":.401,
                        "posthoc_clock_shift_seconds":283,
                        "after_truth_similarity_scale":.21}))
                else:raise AssertionError(cmd)
            with patch.object(unified,"_invoke",side_effect=record):
                got=unified.run(a)
            self.assertTrue(got["actual_estimator_executed"])
            self.assertFalse(got["metric_distance_accuracy_proven"])
            self.assertEqual(len(commands),3)
            self.assertIn("posthoc_aligned_shape_rmse_m",got["metrics"])
            self.assertNotIn("ate_rmse_m",got["metrics"])

    def test_phone_suite_declares_distinct_references_and_is_ready_for_download(self):
        from tools.testenv.contracts import load_suite,readiness
        source=Path(__file__).resolve().parents[2]/"testenv/suites/real-phone.json"
        suite=load_suite(source)
        self.assertEqual(len(suite["jobs"]),2)
        plans=[readiness(job,source) for job in suite["jobs"]]
        self.assertTrue(all(p["ready"] for p in plans))
        self.assertNotEqual(plans[0]["accuracy_claim_level"],plans[1]["accuracy_claim_level"])


if __name__=="__main__":unittest.main()
