import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "vio"))
from release_license_gate import check_inventory

def component(**changes):
    record = {
        "name": "dummy-lib",
        "spdx": "BSD-3-Clause",
        "upstream": "https://example.org",
        "revision": "1234567890abcdef",
        "distribution": "bundled",
        "review_state": "reviewed",
        "notice_file": "docs/THIRD_PARTY_NOTICES.md",
    }
    record.update(changes)
    return record

class LicenseInventoryGateTests(unittest.TestCase):
    def test_approved_complete_inventory_can_pass_structural_gate(self):
        self.assertEqual([],check_inventory({"components":[component()]},release=True))
    def test_rejects_unknown_license_even_when_marked_reviewed(self):
        self.assertTrue(check_inventory({"components":[component(spdx="NOASSERTION")]},release=True))
    def test_rejects_unreviewed_and_unpinned_release_lib(self):
        failures=check_inventory({"components":[component(
            review_state="unreviewed",revision="UNPINNED",notice_file=""
        )]},release=True)
        self.assertGreaterEqual(len(failures),3)
    def test_rejects_unreviewed_runtime_service(self):
        self.assertTrue(check_inventory({"components":[component(
            distribution="service",review_state="needs_legal_review"
        )]},release=True))
    def test_excluded_gpl_candidate_does_not_block_without_packaging(self):
        self.assertEqual([],check_inventory({"components":[component(
            distribution="excluded",spdx="GPL-3.0-only",review_state="unreviewed",
            revision="UNPINNED",notice_file=""
        )]},release=True))
    def test_empty_or_duplicate_inventory_fails(self):
        self.assertTrue(check_inventory({"components":[]}))
        self.assertTrue(check_inventory({"components":[component(),component()]}))
