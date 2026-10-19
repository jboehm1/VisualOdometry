"""Offline contract regression for A/B replay: shared original signals and GT after inference."""
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import numpy as np

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import mobile_gvio_ab as ab
import mobile_gvio_raw as raw
from mobile_gvio_score import score


class PairwisePhoneBenchmarkTests(unittest.TestCase):
    def test_presets_are_frozen_and_lower_compute_uses_fewer_corners(self):
        self.assertEqual(raw.PROFILES["balanced"]["maxCorners"],750)
        self.assertLess(raw.PROFILES["low_compute"]["maxCorners"],
                        raw.PROFILES["balanced"]["maxCorners"])
        self.assertEqual(raw.PROFILES["low_compute"]["qualityLevel"],
                         raw.PROFILES["balanced"]["qualityLevel"])
        self.assertEqual(ab.ORDER,("balanced","low_compute","low_compute","balanced"))

    def test_comparison_keeps_truth_out_of_inference(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            for name in ["source.bag","calib.yaml","gt.tum"]:
                (root/name).write_bytes(b"independent evidence")
            calls=[]
            shared=([(i*1_000_000,np.zeros((10,10),np.uint8)) for i in range(30)],
                    [(i*500_000,np.array([0.,0.,.01])) for i in range(250)])
            def read_data(bag,seconds,skip):
                calls.append("read_real")
                return shared
            def inference(bag,calib,out,seconds,skip,*,profile,observations):
                self.assertIs(observations,shared)
                self.assertTrue(out.name.endswith(profile))
                calls.append("infer:"+profile)
                return {
                  "camera_images_and_imu_both_influence_pose":True,
                  "actual_images_processed":30,"gyro_integrated_samples":220,
                  "visual_essential_pose_updates":19,"visual_ransac_inliers":300,
                  "metric_translation_scale_known":False,
                  "frozen_tracking_profile":profile,
                  "estimator_wall_seconds":2. if profile=="balanced" else 1.5,
                  "estimator_cpu_seconds":1. if profile=="balanced" else .75,
                  "wall_ms_per_frame":66. if profile=="balanced" else 50.,
                }
            def scoring(path,truth,**kwargs):
                calls.append("gt_score")
                self.assertEqual(truth,root/"gt.tum")
                self.assertTrue(path.parent.name.endswith(
                    "balanced" if "balanced" in str(path) else "low_compute"))
                if kwargs.get("infer_clock"):
                    return {"posthoc_clock_shift_seconds":282.5,
                            "clock_shift_fitted_using_reference_after_inference":True,
                            "matched_pose_pairs":40,
                            "sim3_shape_ate_rmse_m":.4,"after_truth_similarity_scale":.2}
                self.assertEqual(kwargs["clock_offset_seconds"],282.5)
                return {"clock_shift_shared_from_reference_fitted_baseline":True,
                        "matched_pose_pairs":40,
                        "sim3_shape_ate_rmse_m":.5,"after_truth_similarity_scale":.3}
            with patch.object(ab,"read_real_imu_camera",side_effect=read_data),\
                 patch.object(ab,"infer",side_effect=inference),\
                 patch.object(ab,"score",side_effect=scoring):
                result=ab.compare(root/"source.bag",root/"calib.yaml",
                                  root/"gt.tum",root/"results")
            self.assertEqual(calls,["read_real","infer:balanced","infer:low_compute",
                                    "infer:low_compute","infer:balanced",
                                    "gt_score","gt_score"])
            self.assertFalse(result["reference_gt_used_for_inference"])
            self.assertFalse(result["metric_distance_accuracy_proven"])
            self.assertEqual(result["results"]["balanced"]["camera_frames"],30)
            self.assertAlmostEqual(result["balanced_over_low_compute_median_wall_time_ratio"],
                                   2/1.5,places=6)
            self.assertTrue((root/"results"/"comparison.json").is_file())

    def test_results_require_real_gyro_and_valid_elapsed_time(self):
        valid={
            "camera_images_and_imu_both_influence_pose":True,
            "actual_images_processed":40,"gyro_integrated_samples":100,
            "visual_essential_pose_updates":12,"visual_ransac_inliers":260,
            "metric_translation_scale_known":False,
            "frozen_tracking_profile":"balanced",
            "estimator_wall_seconds":.02,"estimator_cpu_seconds":.02,
            "wall_ms_per_frame":.5}
        with self.assertRaisesRegex(ValueError,"two inference repetitions"):
            ab._numeric_results("balanced",[valid])
        with self.assertRaisesRegex(ValueError,"Insufficient"):
            ab._numeric_results("balanced",[{**valid,"gyro_integrated_samples":5}]*2)
        with self.assertRaisesRegex(ValueError,"Invalid runtime"):
            ab._numeric_results("balanced",[{**valid,"estimator_wall_seconds":float("nan")}]*2)

    def test_shared_reference_fitted_clock_is_not_marked_independent(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            a=root/"estimate.tum";b=root/"reference.tum"
            times=np.arange(120)*.05+450.
            points=np.stack([np.sin(times),np.cos(times),times*.001],axis=1)
            for target,xyz in [(a,points),(b,points*3)]:
                target.write_text("".join(
                    f"{t:.8f} {p[0]:.8f} {p[1]:.8f} {p[2]:.8f} 0 0 0 1\\n"
                    for t,p in zip(times,xyz)))
            r=score(a,b,clock_offset_seconds=0.)
            self.assertTrue(r["clock_shift_shared_from_reference_fitted_baseline"])
            self.assertFalse(r["timestamp_calibration_independently_verified"])
            self.assertFalse(r["estimator_metric_scale_observed"])
            self.assertAlmostEqual(r["after_truth_similarity_scale"],3.,places=3)
            with self.assertRaisesRegex(ValueError,"simultaneously"):
                score(a,b,infer_clock=True,clock_offset_seconds=0.)


if __name__=="__main__":
    unittest.main()
