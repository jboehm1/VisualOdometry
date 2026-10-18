"""Pure fixture checks for post-inference clock fitting; no metric claims."""
import sys, tempfile, unittest, math
from pathlib import Path
import numpy as np
from scipy.spatial.transform import Rotation
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from mobile_gvio_score import score

class ClockTests(unittest.TestCase):
    def test_time_shift_found_from_rotation_not_from_positions(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            timestamps=100.+np.arange(220)*.1
            rates=.6+.35*np.sin(np.arange(220)*.125)+.18*np.sin(np.arange(220)*.31)
            yaw=np.cumsum(rates*.1)
            p=np.column_stack((np.arange(220)*.07,np.sin(np.arange(220)*.1),np.zeros(220)))
            def write(path,shift,scale):
                with path.open("w") as f:
                    for t,x,angle in zip(timestamps,p,yaw):
                        quat=Rotation.from_euler("z",angle).as_quat()
                        f.write(f"{t+shift:.9f} "+" ".join(f"{v:.9f}" for v in (*tuple(x*scale),*quat))+"\n")
            write(root/"estimated.tum",0,1)
            write(root/"reference.tum",284,2)
            score_data=score(root/"estimated.tum",root/"reference.tum",infer_clock=True)
            self.assertGreater(score_data["matched_pose_pairs"],150)
            self.assertAlmostEqual(score_data["posthoc_clock_shift_seconds"],284,delta=.2)
            self.assertGreater(score_data["posthoc_clock_angular_speed_corr"],.7)
            self.assertTrue(score_data["clock_shift_fitted_using_reference_after_inference"])
            self.assertFalse(score_data["timestamp_calibration_independently_verified"])
            self.assertFalse(score_data["estimator_metric_scale_observed"])
            self.assertIsNone(score_data["metric_distance_ate_rmse_m"])

if __name__=="__main__": unittest.main()
