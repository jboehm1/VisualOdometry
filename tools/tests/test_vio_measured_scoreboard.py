import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"vio"))
from compare_measured_runs import report


def run(name, *, ate=2, rpe=0.3, coverage=0.8, latency=20, energy=15):
    return {
        "engine":name,"engine_sha":"a"*40,
        "dataset_sha256":"b"*64,
        "hardware":"Galaxy-S21-fixed-config",
        "capture_protocol":"repeated_surveyed_route",
        "clock_alignment":"independent_pre_calibrated",
        "pose_alignment":"surveyed_fixed_se3",
        "truth_independent":True,"reference_frame_verified":True,
        "truth_source":"surveyed_path_v1",
        "pose_frame":"camera","truth_frame":"camera",
        "metric_scale":"estimated_online","has_gt_in_estimator":False,
        "report_source":"sha256:report-abc",
        "metrics":{"ate_rmse_m":ate,"rpe_translation_rmse_m":rpe,
            "coverage":coverage,"latency_p95_ms":latency,
            "cpu_core_seconds_per_s":0.5,"energy_j_per_min":energy,
            "frames_total":100,"frames_tracked":90,"reference_matches":80}
    }


class MeasuredVioScoreboardTest(unittest.TestCase):
    def test_reports_pareto_winners_without_blending_hardware(self):
        result=report({"runs":[run("slow",ate=4,rpe=.7,latency=50,energy=30),
            run("fast",ate=1,rpe=.2,latency=10,energy=8)]})
        scores=result["groups"][0]["scores"]
        self.assertFalse(scores[0]["pareto_efficient"])
        self.assertTrue(scores[1]["pareto_efficient"])
    def test_rejects_posthoc_scale_and_gt_fitted_timing(self):
        sample=run("bad")
        sample["metric_scale"]="sim3_fit_to_truth"
        with self.assertRaisesRegex(ValueError,"metric scale"):
            report({"runs":[sample]})
        sample=run("bad")
        sample["clock_alignment"]="fit_using_ground_truth"
        with self.assertRaisesRegex(ValueError,"clock"):
            report({"runs":[sample]})
    def test_rejects_invalid_coverage_or_no_reference(self):
        sample=run("bad",coverage=1.0)
        with self.assertRaisesRegex(ValueError,"coverage"):
            report({"runs":[sample]})
        sample=run("bad")
        sample["truth_independent"]=False
        with self.assertRaisesRegex(ValueError,"truth"):
            report({"runs":[sample]})
    def test_different_data_cannot_be_ranked_together(self):
        a=run("a",ate=1)
        b=run("b",ate=9)
        b["dataset_sha256"]="c"*64
        result=report({"runs":[a,b]})
        self.assertEqual(2,len(result["groups"]))
        self.assertTrue(all(g["scores"][0]["pareto_efficient"] for g in result["groups"]))
