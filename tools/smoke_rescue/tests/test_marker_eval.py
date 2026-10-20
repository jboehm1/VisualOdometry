"""Synthetic contract tests; external marker samples always separate from estimator."""
import importlib.util
from pathlib import Path
import math
import unittest
P=Path(__file__).resolve().parents[1]/"marker_eval.py"
import sys
sys.path.insert(0,str(P.parent))
spec=importlib.util.spec_from_file_location("marker_eval",P)
mod=importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


class FiducialEvalTests(unittest.TestCase):
    def test_se2_no_scale_fit(self):
        source=[(float(k),.5*float(k%3)) for k in range(30)]
        theta=.8
        truth=[(math.cos(theta)*x-math.sin(theta)*y+8,
                math.sin(theta)*x+math.cos(theta)*y-4) for x,y in source]
        fit=mod.fit_se2(source[:10],truth[:10])
        self.assertAlmostEqual(fit[0],theta,places=8)
        for point,target in zip(source,truth):
            out=mod.apply_se2(point,fit)
            self.assertLess(math.dist(out,target),1e-5)

    def test_frozen_trajectory_heldout(self):
        ts=list(range(100))
        trajectory=[{"t":float(i),"x":float(i),"y":math.sin(i*.1),"new_segment":False} for i in ts]
        ref=[(float(i),float(i)+5,math.sin(i*.1)-4,.08) for i in ts]
        result=mod.split_marker_score(trajectory,ref)
        self.assertEqual(result["status"],"MARKER_EARLY_SE2_CALIBRATION_LATER_HELDOUT")
        self.assertEqual(result["heldout_pairs"],75)
        self.assertLess(result["heldout_aligned_rmse_m"],.001)
        self.assertFalse(result["alignment_scale_fit"])
        self.assertGreater(result["stationary_baseline_heldout_rmse_m"],20)

    def test_bad_coverage_no_fabrication(self):
        poses=[{"t":0.,"x":0.,"y":0.}]
        markers=[(i+100,0.,0.,0.) for i in range(100)]
        report=mod.split_marker_score(poses,markers)
        self.assertEqual(report["status"],"INSUFFICIENT_DIRECT_MARKER_MATCHES")
        self.assertEqual(report["heldout_pairs"],0)


if __name__=="__main__":
    unittest.main()
