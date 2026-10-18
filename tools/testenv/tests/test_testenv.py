"""Isolated executor and evidence contracts (no network; actual smoke in CI)."""
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[3]))
from tools.testenv import contracts, runner, presentation

VALID = {
    "schema_version": 1, "id": "fixture-validation",
    "jobs": [{"id": "walk", "dataset": "canonical", "engine": "python-pdr",
              "fixture": "canonical_imu", "checks": [
                  {"field": "metrics.frames_processed", "min": 500},
                  {"field": "reference_used_to_score", "equals": False}]}]
}


class SuiteContracts(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.suite = self.root / "suite.json"

    def store(self, value=VALID):
        self.suite.write_text(json.dumps(value), encoding="utf-8")
        return self.suite

    def test_manifest_plan_and_registry_fail_closed(self):
        self.store()
        suite = contracts.load_suite(self.suite)
        ready = contracts.readiness(suite["jobs"][0], self.suite)
        self.assertTrue(ready["ready"])
        self.assertEqual(ready["accuracy_claim_level"], "uncalibrated_stride_and_heading")
        bad = json.loads(json.dumps(VALID))
        bad["jobs"][0]["dataset"] = "ronin"
        with self.assertRaisesRegex(ValueError, "not runnable"):
            contracts.load_suite(self.store(bad))
        bad["jobs"][0]["dataset"] = "euroc"
        with self.assertRaisesRegex(ValueError, "incompatible"):
            contracts.load_suite(self.store(bad))

    def test_bad_suite_is_rejected_before_any_engine(self):
        for replacement in (
                {"id": "../test"},
                {"timeout_s": -5},
                {"inputs": {"input": ""}},
                {"fixture": "ignored"},
                {"params": {"arbitrary_command": "sh"}},
                {"checks": [{"field": "metrics.foo", "min": float("nan")}]},
                {"checks": [{"field": "__dict__.__class__", "equals": 1}]}):
            bad = json.loads(json.dumps(VALID))
            bad["jobs"][0].update(replacement)
            with self.subTest(replacement=replacement), self.assertRaises(ValueError):
                contracts.load_suite(self.store(bad))

    def test_duplicate_id_and_heading_truth_constraints(self):
        bad = json.loads(json.dumps(VALID))
        bad["jobs"].append(bad["jobs"][0].copy())
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            contracts.load_suite(self.store(bad))
        bad = json.loads(json.dumps(VALID))
        bad["jobs"][0]["inputs"] = {"heading_csv": "phone.csv"}
        with self.assertRaises(ValueError):
            contracts.load_suite(self.store(bad))

    def test_evidence_validation_rejects_score_without_reference(self):
        report = {"status": "PASS", "dataset_id": "canonical",
                  "requested_engine": "python-pdr", "actual_estimator_executed": True,
                  "reference_used_to_score": False, "metrics": {"frames_processed": 1500}}
        runner.verify_report(report, VALID["jobs"][0])
        report["metrics"]["ate_rmse_m"] = 0.01
        with self.assertRaisesRegex(ValueError, "without reference"):
            runner.verify_report(report, VALID["jobs"][0])
        report["reference_used_to_score"] = True
        scored_job = json.loads(json.dumps(VALID["jobs"][0]))
        scored_job["checks"][1]["equals"] = True
        runner.verify_report(report, scored_job)
        report["metrics"]["frames_processed"] = 20
        with self.assertRaisesRegex(ValueError, "below"):
            runner.verify_report(report, scored_job)

    def fake_engine(self, command, timeout_s, cwd):
        if "make_inertial_fixture.py" in " ".join(command):
            folder = Path(command[-1]); folder.mkdir(parents=True)
            (folder / "imu.csv").write_text("t_ns,type,x,y,z\n1,ACCEL,0,0,9.8\n")
        elif "benchmark.py" in " ".join(command):
            output = Path(command[command.index("--out") + 1])
            output.mkdir(exist_ok=True)
            (output / "report.json").write_text(json.dumps({
                "status": "PASS", "dataset_id": "canonical", "requested_engine": "python-pdr",
                "actual_estimator_executed": True, "reference_used_to_score": False,
                "metrics": {"frames_processed": 1500}, "accuracy_claim_level": "not_metric",
                "reference_kind": "none_scored"}))
        return 0, "", ""

    def test_history_resume_and_tampering_reexecutes(self):
        self.store()
        out = self.root / "history"
        with patch.object(runner, "_subprocess", side_effect=self.fake_engine) as call:
            first = runner.execute(self.suite, out)
            self.assertEqual(first["status"], "PASS")
            self.assertEqual(call.call_count, 2)
        with patch.object(runner, "_subprocess", side_effect=AssertionError("should be reused")):
            second = runner.execute(self.suite, out, resume=True)
        self.assertIn("reused_from", second["cases"][0])
        self.assertEqual(len(list((out / "runs").glob("*/summary.json"))), 2)
        self.assertIn("Execution history", (out / "index.html").read_text())
        # Destroy the first artifact and the second one, making cached PASS invalid.
        for report in (out / "runs").glob("*/cases/walk/report.json"):
            report.write_text("{}")
        with patch.object(runner, "_subprocess", side_effect=self.fake_engine) as call:
            third = runner.execute(self.suite, out, resume=True)
        self.assertNotIn("reused_from", third["cases"][0])
        self.assertEqual(call.call_count, 2)

    def test_failure_remains_failed_and_does_not_reuse(self):
        self.store()
        def fail(command, timeout_s, cwd):
            return (1, "", "intentional error") if "benchmark.py" in " ".join(command) else self.fake_engine(command, timeout_s, cwd)
        with patch.object(runner, "_subprocess", side_effect=fail):
            result = runner.execute(self.suite, self.root / "bad")
        self.assertEqual(result["status"], "FAILED")
        self.assertEqual(result["counts"]["FAILED"], 1)
        self.assertIn("Benchmark exit 1", result["cases"][0]["error"])
        self.assertFalse((self.root / "bad" / ".testenv.lock").exists())

    def test_lock_rejects_overlapping_writers_and_filter_typo(self):
        self.store()
        out = self.root / "locked"
        out.mkdir(); (out / ".testenv.lock").mkdir()
        with self.assertRaisesRegex(RuntimeError, "lock"):
            runner.execute(self.suite, out)
        with self.assertRaisesRegex(ValueError, "Unknown --only"):
            runner.execute(self.suite, out, only={"walkk"})

    def test_large_engine_logs_are_bounded_in_memory_and_report(self):
        code = ("import sys; "
                "sys.stdout.write('A'*1000000+'GOOD_END'); "
                "sys.stderr.write('B'*1000000+'BAD_END')")
        rc, out, err = runner._subprocess([sys.executable, "-c", code], 10, self.root)
        self.assertEqual(rc, 0)
        self.assertLessEqual(len(out), 16000)
        self.assertLessEqual(len(err), 16000)
        self.assertTrue(out.endswith("GOOD_END"))
        self.assertTrue(err.endswith("BAD_END"))

    def test_dashboard_escapes_untrusted_metrics_and_errors(self):
        out = self.root / "html"
        target = out / "runs" / "20261018T102000Z-abcdefab"
        target.mkdir(parents=True)
        runner.write_json(target / "summary.json", {
            "status": "FAILED", "suite": "test", "run_id": target.name,
            "cases": [{"id": "x", "dataset": "canonical", "engine": "python-pdr",
                       "status": "FAILED", "error": "<script>alert('x')</script>"}]})
        page = presentation.render_dashboard(out).read_text(encoding="utf-8")
        self.assertIn("&lt;script&gt;", page)
        self.assertNotIn("<script>alert", page)


if __name__ == "__main__":
    unittest.main()
