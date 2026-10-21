#!/usr/bin/env python3
"""Fail-closed manifest checks: smartphones cannot be credited with external sensors or lifesaving navigation.

This intentionally tests the *research registry*, not Android capabilities or odometry accuracy.
"""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
REGISTRY = ROOT / "docs" / "SMARTPHONE_MODEL_EVALUATION_REGISTRY_20261021.json"
ATLAS = ROOT / "docs" / "VECTOR_VIO_ATLAS_INTERACTIF_v5.html"

class SmartphoneOnlyModelRegistryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.reg = json.loads(REGISTRY.read_text(encoding="utf-8"))
        cls.html = ATLAS.read_text(encoding="utf-8")

    def test_all_candidates_only_use_built_in_rgb_and_imu(self):
        models = self.reg["smartphone_models"]
        self.assertEqual(len(models), len(set(m["id"] for m in models)))
        self.assertGreaterEqual(len(models), 8)
        permitted = {"rgb", "imu", "barometer", "magnetometer", "gnss", "ambient_light", "depth_from_motion"}
        for m in models:
            with self.subTest(id=m["id"]):
                self.assertEqual(m["hardware_tier"], "S0")
                self.assertTrue(set(m["modalities"]).issubset(permitted))
                self.assertTrue(m["source"])
                self.assertTrue(m["license"])
                self.assertFalse(m["can_observe_hidden_obstacles_in_opaque_smoke"])

    def test_no_unsafe_physical_impossibilities_are_claimed(self):
        gates = self.reg["fixed_platform_gates"]
        self.assertTrue(gates)
        self.assertTrue(all(value is False for value in gates.values()))
        bicycle = next(x for x in self.reg["smartphone_models"] if x["id"]=="pdr_java")
        self.assertTrue(bicycle["can_predict_position_without_visual_input"])
        self.assertFalse(gates["bicycle_speed_from_imu_only_observable"])
        self.assertFalse(gates["emergency_route_guidance_approved"])
        self.assertFalse(gates["supports_smartphone_only_lwir"])

    def test_external_hardware_strictly_separated(self):
        self.assertTrue(all(x["hardware_tier"]=="H" for x in self.reg["excluded_external_hardware"]))
        self.assertTrue(all(x["hardware_tier"] in ("S0", "S1") for x in self.reg["experimental_scenarios"]))
        self.assertTrue(all(x["requires_independent_gt"] is True for x in self.reg["experimental_scenarios"]))

    def test_html_has_s0_as_primary_and_hardware_closed(self):
        self.assertEqual(self.html.count('id="phone-first-degraded"'), 1)
        self.assertEqual(self.html.count('id="phoneExternalHardware"'), 1)
        self.assertIn("SMARTPHONE_ONLY_SENSOR_AI_ROADMAP_20261021.md", self.html)
        self.assertIn("S1 (et non S0 autonome)", self.html)
        self.assertIn("POSITION_UNKNOWN", self.html)
        self.assertIn("RoNIN", self.html)
        self.assertIn("TLIO", self.html)
        self.assertIn("Depth Anything V2 Small", self.html)
        self.assertIn("Radar mmWave 4D", self.html)
        self.assertNotIn('<details id="phoneExternalHardware" open', self.html)

if __name__ == "__main__":
    unittest.main(verbosity=2)
