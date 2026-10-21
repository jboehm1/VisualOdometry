import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("vio_provenance", ROOT / "eval" / "provenance.py")
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)

SHA = "a" * 40
HASH = "b" * 64


def manifest(**changes):
    data = {
        "run_id": "euroc-mh01-run1",
        "engine": "arcore-android",
        "engine_commit": SHA,
        "dataset_id": "euroc",
        "config_id": "tracking-default",
        "evidence": "fixture",
        "truth_type": "synthetic-fixture",
        "timebase": "camera-ns",
        "alignment": "yaw-translation",
        "scale_fitted": False,
    }
    data.update(changes)
    return data


class ProvenanceTest(unittest.TestCase):
    def test_fixture_cannot_claim_independent_real_accuracy(self):
        result = module.build(manifest(), {"ate_rmse_m": 0.0}, HASH)
        self.assertEqual("DIAGNOSTIC_OR_SYNTHETIC_ONLY", result["evidence_tier"])
        self.assertFalse(result["qualified_for_safety_navigation"])
        self.assertTrue(result["provenance_is_self_reported"])
        self.assertTrue(any("Synthetic" in x for x in result["warnings"]))

    def test_real_reference_eligible_only_as_self_reported_review(self):
        m = manifest(
            evidence="real-offline",
            truth_type="independent-motion-capture",
            dataset_sha256=HASH,
            license_or_consent_reviewed=True,
        )
        result = module.build(m, {"ate_rmse_m": 0.31}, HASH)
        self.assertEqual("REAL_INDEPENDENT_TRUTH_REVIEW", result["evidence_tier"])
        self.assertFalse(result["qualified_for_safety_navigation"])

    def test_real_without_license_or_hash_is_rejected(self):
        with self.assertRaises(ValueError):
            module.build(manifest(evidence="real-offline", truth_type="independent-motion-capture"),
                         {"ate_rmse_m": 0.1})
        with self.assertRaises(ValueError):
            module.build(manifest(evidence="real-offline", truth_type="independent-motion-capture",
                                  dataset_sha256=HASH, license_or_consent_reviewed=False),
                         {"ate_rmse_m": 0.1})

    def test_physical_python_pdr_claim_rejected(self):
        m = manifest(engine="pdr-python-baseline", evidence="physical-phone",
                     truth_type="reference-ins", dataset_sha256=HASH,
                     license_or_consent_reviewed=True)
        with self.assertRaises(ValueError):
            module.build(m, {"distance_rmse_m": 0.1})

    def test_metric_scale_fitting_is_not_metric_scale_evidence(self):
        m = manifest(evidence="real-offline", truth_type="reference-ins",
                     dataset_sha256=HASH, license_or_consent_reviewed=True,
                     scale_fitted=True)
        report = module.build(m, {"ate_rmse_m": 0.1})
        self.assertEqual("DIAGNOSTIC_OR_SYNTHETIC_ONLY", report["evidence_tier"])
        self.assertTrue(any("Scale" in w for w in report["warnings"]))

    def test_unknown_engine_and_signed_metrics_validation(self):
        with self.assertRaises(ValueError):
            module.build(manifest(engine="pdr-unknown"), {"ate_rmse_m": 2})
        with self.assertRaises(ValueError):
            module.build(manifest(), {"ate_rmse_m": float("nan")})
        self.assertEqual(-45, module.build(manifest(), {"alignment_yaw_deg": -45})["metrics"]["alignment_yaw_deg"])

    def test_actual_segmented_nested_report_survives_without_claim_inflation(self):
        measurements = {
            "status": "INDEPENDENT_GROUND_TRUTH_REQUIRED",
            "segments": [
                {"segment": 1, "ate_rmse_m": 0.9, "rpe_translation_rmse_m": 0.3,
                 "rpe_samples": 20, "metric_scale_fitted": False},
                {"segment": 2, "status": "INSUFFICIENT_MATCHES", "matches": 2}
            ],
            "matched_coverage_fraction": 0.63,
            "global_metric_accuracy": "NOT_EVALUATED_ACROSS_SEGMENT_RESETS"
        }
        evidence = module.build(manifest(), measurements)
        self.assertEqual(measurements, evidence["metrics"])
        self.assertEqual("DIAGNOSTIC_OR_SYNTHETIC_ONLY", evidence["evidence_tier"])

    def test_nested_nonfinite_and_deep_structure_are_rejected(self):
        with self.assertRaises(ValueError):
            module.build(manifest(), {"segments": [{"ate": float("inf")}]})
        tree = {"leaf": 1}
        for _ in range(15):
            tree = {"child": tree}
        with self.assertRaises(ValueError):
            module.build(manifest(), tree)

    def test_negative_fixed_clock_offset_can_be_declared_but_not_fitted(self):
        m = manifest(time_offset_ns=-120_000_000)
        self.assertEqual("DIAGNOSTIC_OR_SYNTHETIC_ONLY", module.build(m, {})["evidence_tier"])
        with self.assertRaises(ValueError):
            module.build(manifest(time_offset_ns=999_999_999_999), {})


    def test_duplicate_json_keys_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "dup.json"
            path.write_text('{"engine":"foo","engine":"bar"}', encoding="utf8")
            with self.assertRaises(ValueError):
                module.load_json(path)

    def test_cli_outputs_reproducible_hash_and_explicit_warning(self):
        with tempfile.TemporaryDirectory() as d:
            base = Path(d)
            m = base / "manifest.json"
            metrics = base / "metrics.json"
            out = base / "result.json"
            m.write_text(json.dumps(manifest()), encoding="utf8")
            metrics.write_text(json.dumps({"ate_rmse_m": 0.0}), encoding="utf8")
            subprocess.run([sys.executable, str(ROOT / "eval" / "provenance.py"),
                            "--manifest", str(m), "--metrics", str(metrics),
                            "--out", str(out)], check=True, stdout=subprocess.DEVNULL)
            result = json.loads(out.read_text(encoding="utf8"))
            self.assertEqual(HASH != result["metrics_json_sha256"], True)
            self.assertEqual("DIAGNOSTIC_OR_SYNTHETIC_ONLY", result["evidence_tier"])


if __name__ == "__main__":
    unittest.main()
