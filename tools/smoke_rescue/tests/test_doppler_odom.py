"""Synthetic mathematical tests of Doppler + gyro dead reckoning.

The original UPO ROS bag is separately exercised by GitHub Actions.
This suite CANNOT be used as evidence for real smoke pose accuracy.
"""
import importlib.util
import math
from pathlib import Path
import unittest

P=Path(__file__).resolve().parents[1]/"doppler_odom.py"
spec=importlib.util.spec_from_file_location("doppler_odom",P)
# doppler_odom imports upo_real when loaded from arbitrary cwd; use sys.path.
import sys
sys.path.insert(0,str(P.parent))
algo=importlib.util.module_from_spec(spec)
spec.loader.exec_module(algo)


def echoes(vx,vy,angles,n=1,noise=None):
    return [(4*math.cos(a),4*math.sin(a),-(vx*math.cos(a)+vy*math.sin(a))+(noise or 0))
            for a in angles for _ in range(n)]


class RadarDopplerResearch(unittest.TestCase):
    def test_ideal_doppler_2d_solution(self):
        ans, reason=algo.solve_2d(echoes(.7,-.2,[-1,-.6,-.2,.3,.8,1.2]))
        self.assertIsNone(reason)
        self.assertAlmostEqual(ans["vx"],.7,places=5)
        self.assertAlmostEqual(ans["vy"],-.2,places=5)

    def test_single_direction_not_estimate(self):
        result,reason=algo.solve_2d(echoes(1.,0.,[0]*20))
        self.assertIsNone(result)
        self.assertEqual(reason,"geometry_degenerate")

    def test_insufficient_returns_no_pose(self):
        answer,reason=algo.solve_2d(echoes(1.,0.,[0,1]))
        self.assertIsNone(answer)
        self.assertEqual(reason,"insufficient_returns")

    def test_robust_outlier_rejected(self):
        data=echoes(.5,-.1,[-1,-.6,-.2,.3,.8,1.2],3)
        data.append((3,2,2.8))
        result,reason=algo.solve_2d(data)
        self.assertIsNone(reason)
        self.assertLess(abs(result["vx"]-.5),.3)
        self.assertLess(abs(result["vy"]+.1),.3)

    def test_bad_gyro_gap_fails_closed(self):
        with self.assertRaisesRegex(ValueError,"time gap"):
            algo.integrate_gyro([(0.,0.),(.01,0.),(2.,0.)])

    def test_gyro_heading_exact_simple(self):
        t,y=algo.integrate_gyro([(i*.01,.1) for i in range(100)])
        self.assertAlmostEqual(y[-1],.099,places=5)
        self.assertAlmostEqual(algo.yaw_at(t,y,.5),.05,places=5)

    def test_own_odom_no_reference_input(self):
        ts=[i*.01 for i in range(501)]
        imu=[(t,0.) for t in ts]
        radar=[]
        for t in (0.1+i*.10 for i in range(48)):
            for x,y,vr in echoes(.4,0,[-1,-.5,0,.4,.9,1.2]):
                radar.append((t,x,y,vr))
        traj,health=algo.estimate(radar,imu,bin_seconds=.5)
        self.assertGreater(len(traj),5)
        self.assertGreater(traj[-1]["x"],1.0)
        self.assertLess(abs(traj[-1]["y"]),.15)
        self.assertGreater(health["accepted_fraction"],.75)

    def test_se2_score_no_scale_fit(self):
        traj=[{"x":t,"y":0.,"t":float(t),"new_segment":False}
              for t in range(10)]
        # Rotation 90 degrees plus +5,+8 translation, NO scale.
        ref=[(float(t),5.,8.+t) for t in range(10)]
        score=algo.rigid_2d_align_and_score(traj,ref)
        self.assertAlmostEqual(score["ate_rmse_m_vs_pseudo_reference"],0,places=5)
        self.assertFalse(score["scale_fit"])
        self.assertFalse(score["used_reference_during_inference"])

    def test_incomplete_ref_not_accuracy(self):
        traj=[{"x":0.,"y":0.,"t":0.,"new_segment":False}]
        self.assertEqual(algo.rigid_2d_align_and_score(traj,[])["status"],
                         "NO_TIME_MATCHED_REFERENCE")


if __name__=="__main__":
    unittest.main()
