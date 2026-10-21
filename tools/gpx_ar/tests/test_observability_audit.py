"""Seeded synthetic geometry contract; no real sensor accuracy claim."""
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]


@unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "Java 17 required")
class GeoObservabilityAuditTest(unittest.TestCase):
    def test_frozen_jitter_outlier_and_uncertain_motion_cannot_anchor(self):
        with tempfile.TemporaryDirectory() as folder:
            sources = [
                "app/src/main/java/dev/jboehm/vectorvio/GeoAlignment.java",
                "app/src/main/java/dev/jboehm/vectorvio/geo/Wgs84LocalFrame.java",
                "tools/gpx_ar/GeoObservabilityAudit.java",
            ]
            subprocess.run(["javac", "-d", folder, *[str(ROOT / p) for p in sources]],
                           check=True, capture_output=True, text=True, timeout=30)
            output = subprocess.run(["java", "-cp", folder, "GeoObservabilityAudit"],
                                    check=True, capture_output=True, text=True, timeout=30)
            result = json.loads(output.stdout)
        self.assertEqual(500, result["cases_per_scenario"])
        for scenario in ("frozen_gps_candidates", "jitter_gps_candidates",
                         "outlier_baseline_candidates", "uncertain_gps_baseline_candidates"):
            with self.subTest(scenario=scenario): self.assertEqual(0, result[scenario])
        self.assertEqual(500, result["coherent_candidates"])
        self.assertEqual(500, result["gps_spike_candidates"])
