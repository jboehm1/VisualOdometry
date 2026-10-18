"""No-network tests for independent reference scoring and fail-closed timestamps."""
import json
import sys
import tempfile
import unittest
from pathlib import Path
import numpy as np

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from mobile_gvio_score import score, poses


def write_poses(path, times, positions):
    with path.open("w") as f:
        for t,p in zip(times,positions):
            f.write(f"{t:.8f} {p[0]:.8f} {p[1]:.8f} {p[2]:.8f} 0 0 0 1\n")


class IndependentReferenceTests(unittest.TestCase):
    def test_sim3_shape_fit_does_not_claim_metric_scale(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)
            t=np.arange(110,dtype=float)*.05+500
            pts=np.column_stack((np.sin(t*.8),np.cos(t*.8),.007*np.arange(len(t))))
            q=pts*3.5+np.array([6,-2,4])
            write_poses(p/"estimate.tum",t,pts)
            write_poses(p/"independent.tum",t,q)
            result=score(p/"estimate.tum",p/"independent.tum")
            self.assertGreater(result["matched_pose_pairs"],100)
            self.assertLess(result["sim3_shape_ate_rmse_m"],.0001)
            self.assertAlmostEqual(result["after_truth_similarity_scale"],3.5,places=3)
            self.assertFalse(result["estimator_metric_scale_observed"])
            self.assertIsNone(result["metric_distance_ate_rmse_m"])

    def test_time_nonoverlap_fails_instead_of_inventing_alignment(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d);t=np.arange(110)*.05
            pts=np.column_stack((np.sin(t),np.cos(t),t*.02))
            write_poses(p/"estimate.tum",t+100,pts)
            write_poses(p/"ground_truth.tum",t+1000,pts)
            with self.assertRaisesRegex(ValueError,"Insufficient time-synchronized"):
                score(p/"estimate.tum",p/"ground_truth.tum")

    def test_invalid_and_unsorted_reference_fails(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d);t=np.arange(110)*.05
            x=np.zeros((len(t),3))
            write_poses(p/"gt.tum",t,x)
            with (p/"gt.tum").open("a") as f:
                f.write("3 0 0 0 0 0 0 1\n")
            with self.assertRaisesRegex(ValueError,"unsorted|invalid"):
                poses(p/"gt.tum")


if __name__=="__main__":
    unittest.main()
