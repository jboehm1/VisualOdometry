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
        "capture_protocol":"same_raw",
        "clock_alignment":"independent_pre_calibrated",
        "pose_alignment":"surveyed_fixed_se3",
        "truth_independent":True,"reference_frame_verified":True,
        "truth_source":"surveyed_path_v1",
        "pose_frame":"camera","truth_frame":"camera",
        "metric_scale":"estimated_online","has_gt_in_estimator":False,
        "report_source":"sha256:"+"c"*64,
        "evaluation_protocol_sha256":"d"*64,
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

    def test_missing_or_nonboolean_truth_access_must_fail_closed(self):
        for value in (None, 0, "false", [], {}):
            sample=run("bad")
            sample["has_gt_in_estimator"]=value
            with self.subTest(value=value), self.assertRaises(ValueError):
                report({"runs":[sample]})
        sample=run("bad"); del sample["has_gt_in_estimator"]
        with self.assertRaises(ValueError): report({"runs":[sample]})

    def test_identity_and_comparison_contract_cannot_be_missing_or_fake(self):
        for key in ("engine","engine_sha","dataset_sha256","hardware","truth_source",
                    "pose_frame","truth_frame","evaluation_protocol_sha256","report_source"):
            for value in (None, [], {}, "", " "):
                sample=run("bad"); sample[key]=value
                with self.subTest(key=key,value=value), self.assertRaises(ValueError):
                    report({"runs":[sample]})
        for key in ("engine_sha","dataset_sha256","evaluation_protocol_sha256","report_source"):
            sample=run("bad");sample[key]="unversioned"
            with self.subTest(key=key), self.assertRaises(ValueError): report({"runs":[sample]})

    def test_reference_counts_cannot_exceed_tracking_or_hide_coverage(self):
        for tracked, matches, coverage in ((0,30,0),(40,80,.4),(90,80,.94)):
            sample=run("bad",coverage=coverage)
            sample["metrics"].update(frames_tracked=tracked,reference_matches=matches)
            with self.subTest(tracked=tracked), self.assertRaises(ValueError):
                report({"runs":[sample]})

    def test_cpu_tradeoff_is_preserved_in_pareto(self):
        a=run("faster",ate=1,latency=10)
        b=run("efficient",ate=2,latency=20)
        a["metrics"]["cpu_core_seconds_per_s"]=2
        b["metrics"]["cpu_core_seconds_per_s"]=.2
        scores=report({"runs":[a,b]})["groups"][0]["scores"]
        self.assertTrue(all(s["pareto_efficient"] for s in scores))

    def test_different_evaluation_windows_or_rpe_horizons_are_separate(self):
        a=run("a");b=run("b");b["evaluation_protocol_sha256"]="e"*64
        self.assertEqual(2,len(report({"runs":[a,b]})["groups"]))

    def test_repeated_route_uses_explicit_paired_design_not_identical_raw_bytes(self):
        a=run("arcore");b=run("native")
        for sample in (a,b):
            sample["capture_protocol"]="repeated_surveyed_route"
            sample["comparison_group_sha256"]="f"*64
        b["dataset_sha256"]="e"*64
        result=report({"runs":[a,b]})
        self.assertEqual(1,len(result["groups"]))
        self.assertEqual(2,len(result["groups"][0]["source_dataset_sha256"]))
        b["comparison_group_sha256"]="a"*64
        self.assertEqual(2,len(report({"runs":[a,b]})["groups"]))
        del b["comparison_group_sha256"]
        with self.assertRaises(ValueError): report({"runs":[a,b]})

    def test_report_does_not_mutate_inputs_and_preserves_source_identity(self):
        import copy
        sample=run("a");before=copy.deepcopy(sample)
        score=report({"runs":[sample]})["groups"][0]["scores"][0]
        self.assertEqual(before,sample)
        self.assertEqual(sample["dataset_sha256"],score["dataset_sha256"])
        self.assertEqual(sample["report_source"],score["report_source"])

    def test_nonobject_run_and_top_level_raise_validation_error(self):
        for data in ([], None, {"runs":[None]}, {"runs":[[]]}):
            with self.subTest(data=data), self.assertRaises(ValueError): report(data)
