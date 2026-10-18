"""The shared Test Environment V2 must recognize UPO only as REAL sensor audit.

Unit tests require no 2.18 GB raw recording and NEVER pretend to run one.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SUITE = ROOT / "tools/testenv/suites/smoke-upo7-real.json"


class CanonicalSuiteTests(unittest.TestCase):
    def test_canonical_dataset_register_and_plan(self):
        from tools.campaigns.unified import plan
        desc = plan("upo_smoke", "radar-sensor-audit")
        self.assertEqual(desc["dataset"], "upo_smoke")
        self.assertEqual(desc["accuracy_claim_level"],
                         "sensor_observability_only_no_position_or_obstacle_accuracy")
        with self.assertRaises(ValueError):
            plan("upo_smoke", "android-pdr")

    def test_v2_suite_declared_not_pretend_ready(self):
        from tools.testenv.contracts import load_suite, readiness
        suite = load_suite(SUITE)
        self.assertEqual(suite["id"], "smoke-upo7-real")
        self.assertEqual(len(suite["jobs"]), 1)
        case = suite["jobs"][0]
        self.assertEqual(case["dataset"], "upo_smoke")
        self.assertEqual(case["engine"], "radar-sensor-audit")
        self.assertFalse(readiness(case, SUITE)["ready"])
        checks = {x["field"]: x for x in case["checks"]}
        self.assertEqual(checks["actual_estimator_executed"]["equals"], False)
        self.assertEqual(checks["reference_used_to_score"]["equals"], False)
        self.assertEqual(checks["metric_distance_accuracy_proven"]["equals"], False)


if __name__ == "__main__":
    unittest.main()
