"""Radar map geometry tests, synthetic only — no fire-service operational claim."""
import importlib.util
from pathlib import Path
import unittest
import math

P=Path(__file__).resolve().parents[1]/"radar_map.py"
spec=importlib.util.spec_from_file_location("radar_map",P)
m=importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class RadarMapResearchTest(unittest.TestCase):
    def test_real_positive_echo_never_implies_free(self):
        pose=[{"t":5.0,"x":1.0,"y":2.0,"yaw":0.0}]
        cells,summary=m.project_hits(pose,[(5.,2.,0.,.4)])
        self.assertIn((12,8),cells)
        self.assertEqual(len(cells),1)
        self.assertTrue(summary["unknown_is_not_free"])
        self.assertIsNone(summary["obstacle_recall"])

    def test_missing_echo_does_not_mark_area(self):
        cells,summary=m.project_hits([{"t":3.,"x":0.,"y":0.,"yaw":0.}],[])
        self.assertEqual(cells,{})
        self.assertEqual(summary["occupied_evidence_cells"],0)

    def test_stale_echo_abstains(self):
        cells,summary=m.project_hits([{"t":3.,"x":0.,"y":0.,"yaw":0.}],
                                     [(5.,1.,0.,.1)])
        self.assertEqual(cells,{})
        self.assertEqual(summary["echoes_unprojected"],1)

    def test_rotation_and_grid(self):
        pose=[{"t":0.,"x":0.,"y":0.,"yaw":math.pi/2}]
        cells,_=m.project_hits(pose,[(0.,1.,0.,0.)])
        self.assertEqual(cells,{(0,4):1})

    def test_invalid_pose_or_cell_size(self):
        with self.assertRaises(ValueError):m.project_hits([],[],.25)
        with self.assertRaises(ValueError):m.project_hits([{"t":0.}],[],0.)


if __name__=="__main__":
    unittest.main()
