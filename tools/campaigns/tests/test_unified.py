"""Unit tests for the unified dataset/engine capability contract."""
import json
import sys
import tempfile
import unittest
from pathlib import Path
from argparse import Namespace
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import unified


def options(root, dataset="canonical", engine="python-pdr", **kwargs):
    fields=dict(out=root/"result",dataset=dataset,engine=engine,input=None,
                root=None,estimate=None,truth=None,heading_csv=None,
                heading_source=None,known_distance=None,stride_m=.70,
                yaw_axis="z",timeout_s=90)
    fields.update(kwargs)
    return Namespace(**fields)


class UnifiedTests(unittest.TestCase):
    def test_catalog_and_capability_matrix(self):
        names={x["id"]:x for x in unified.available_catalog()}
        self.assertEqual(names["simul"]["run_status"],"verified_real_public_pipeline")
        self.assertEqual(names["euroc_external"]["run_status"],"verified_real_public_pipeline")
        self.assertEqual(names["ronin"]["supported_engines"],[])
        self.assertEqual(names["tumvi"]["supported_engines"],["sensor-index"])

    def test_unsupported_datasets_and_arcore_fail_closed(self):
        with self.assertRaisesRegex(ValueError,"not runnable"):
            unified.plan("ronin","android-pdr")
        with self.assertRaisesRegex(ValueError,"incompatible"):
            unified.plan("euroc","android-arcore")
        with self.assertRaisesRegex(ValueError,"incompatible"):
            unified.plan("simul","external-vins")
        self.assertEqual(unified.plan("simul","java-pdr")["requested_engine"],"android-pdr")
        with tempfile.TemporaryDirectory() as d:
            cfg=options(Path(d),"ronin","android-pdr")
            with self.assertRaises(ValueError):
                unified.run(cfg)
            failed=json.loads((cfg.out/"status.json").read_text())
            self.assertEqual(failed["status"],"FAILED")
            self.assertFalse(failed["actual_estimator_executed"])
            self.assertEqual(failed["metrics"],{})

    def test_real_simul_declares_java_and_python_as_separate_engines(self):
        with tempfile.TemporaryDirectory() as d:
            cfg=options(Path(d),"simul","android-pdr")
            def fake_invoke(command, out, *, timeout_s):
                p=out/"public_data"
                p.mkdir()
                (p/"chat_proof_receipt.json").write_text(
                    '{"real_sample_timestamps":1000}')
                sample=dict(java_step_count=dict(count=20,step_count_error_percent=0),
                            python_step_count=dict(count=21,step_count_error_percent=5),
                            java_timed_step_score=dict(f1=.9))
                (p/"real_step_report.json").write_text(json.dumps(dict(
                    dataset_upstream_commit="source_sha",java_upstream_commit="java_sha",
                    sequences=[dict(sequence="fixture",reference_steps=20,
                                    data_source=dict(sha256="raw"),
                                    annotation_source=dict(sha256="truth"),
                                    positions=[sample])])))
            with patch.object(unified,"_invoke",side_effect=fake_invoke):
                result=unified.run(cfg)
            self.assertEqual(result["measured_engine"],"actual_pinned_Java_PdrEngine")
            self.assertEqual(result["additional_engine_executed"],"python-pdr")
            self.assertTrue(result["reference_used_to_score"])
            self.assertEqual(result["reference_kind"],"annotated_step_intervals")
            self.assertNotIn("ate_rmse_m",result["metrics"])

    def test_euroc_published_estimate_is_never_android_estimator(self):
        with tempfile.TemporaryDirectory() as d:
            cfg=options(Path(d),"euroc_external","external-vins")
            def fake_invoke(command,out,*,timeout_s):
                p=out/"published";p.mkdir()
                (p/"source_manifest.json").write_text(json.dumps(
                    dict(upstream_commit="sha",downloaded_files=[])))
                (p/"metrics.json").write_text('{"pairs":3600,"ate_rmse_m":0.2}')
            with patch.object(unified,"_invoke",side_effect=fake_invoke):
                report=unified.run(cfg)
            self.assertFalse(report["actual_estimator_executed"])
            self.assertTrue(report["evaluator_executed"])
            self.assertEqual(report["metrics"]["ate_rmse_m"],.2)

    def test_phone_surveyed_distance_must_be_positive(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);csv=root/"session.csv";csv.write_text("dummy")
            cfg=options(root,"phone_csv","csv-audit",input=csv,known_distance=-1)
            with self.assertRaisesRegex(ValueError,"positive finite"):
                unified.run(cfg)


if __name__=="__main__":
    unittest.main()
