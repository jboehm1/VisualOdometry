"""Dataset lock tests use only temporary synthetic bytes. No real corpus claimed."""
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools.datasets import repro_manifest as repro


class ReproManifestTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.suite = self.root / "suite.json"
        self.sample = self.root / "imu.csv"
        self.sample.write_bytes(b"t_ns,type,x,y,z\n1000000000,ACCEL,0,0,9.8\n")
        self.suite.write_text(json.dumps({
            "schema_version": 1,
            "id": "repro-test",
            "description": "Only synthetic parser fixture",
            "jobs": [{"id": "imu", "dataset": "canonical",
                      "engine": "android-pdr",
                      "inputs": {"input": "imu.csv"},
                      "checks": []}]
        }), encoding="utf-8")

    def lock(self):
        # Preserve trust in source fingerprints without running git in fixture tests.
        with patch.object(repro, "code_identity", return_value={
            "git_sha": "a" * 40,
            "source_sha256": {"tools/testenv/contracts.py": "b" * 64}
        }):
            return repro.lock_suite(self.suite)

    def test_exact_same_bytes_are_stable_even_across_rechecks(self):
        a = self.lock()
        b = self.lock()
        self.assertEqual(repro._stable(a), repro._stable(b))
        self.assertEqual("LOCAL_CONTENT_SHA256", a["jobs"][0]["input_status"])
        self.assertEqual(hashlib.sha256(self.sample.read_bytes()).hexdigest(),
                         a["jobs"][0]["inputs"]["input"]["entries"][0]["sha256"])
        self.assertNotIn(str(self.root), json.dumps(a))

    def test_changed_data_invalidates_receipt_and_does_not_overwrite_old(self):
        before = self.lock()
        receipt = self.root / "receipt.json"
        repro.save_once(receipt, before)
        with self.assertRaises(FileExistsError):
            repro.save_once(receipt, before)
        self.sample.write_bytes(self.sample.read_bytes() + b"999000000,ACCEL,1,0,8.8\n")
        self.assertNotEqual(repro._stable(before), repro._stable(self.lock()))

    def test_reject_symlinks_and_mutated_folder(self):
        folder = self.root / "real"
        folder.mkdir()
        (folder / "a.csv").write_bytes(b"A")
        first = repro.lock_input(folder)
        (folder / "b.csv").write_bytes(b"B")
        second = repro.lock_input(folder)
        self.assertNotEqual(first, second)
        (folder / "link").symlink_to(self.sample)
        with self.assertRaisesRegex(ValueError, "Symlink"):
            repro.lock_input(folder)

    def test_unknown_dataset_engine_not_permitted(self):
        data = json.loads(self.suite.read_text())
        data["jobs"][0]["engine"] = "fake-vio"
        self.suite.write_text(json.dumps(data))
        with self.assertRaises(ValueError):
            self.lock()

    def test_remote_not_labelled_verified_real_data(self):
        data = json.loads(self.suite.read_text())
        data["jobs"][0] = {"id": "phone", "dataset": "advio09",
                           "engine": "external-pixel-arcore"}
        self.suite.write_text(json.dumps(data))
        report = self.lock()
        self.assertEqual("UNVERIFIED_REMOTE_BYTES_AT_PREFLIGHT",
                         report["jobs"][0]["input_status"])

    def test_synthetic_fixture_not_labelled_real_data(self):
        data = json.loads(self.suite.read_text())
        data["jobs"][0] = {"id": "fixture", "dataset": "canonical",
                           "engine": "android-pdr", "fixture": "canonical_imu"}
        self.suite.write_text(json.dumps(data))
        self.assertEqual("SYNTHETIC_FIXTURE_NOT_REAL_DATA",
                         self.lock()["jobs"][0]["input_status"])

    def test_size_limits_are_enforced(self):
        with self.assertRaisesRegex(ValueError, "cap"):
            repro.lock_input(self.sample, max_bytes=3)


if __name__ == "__main__":
    unittest.main()
