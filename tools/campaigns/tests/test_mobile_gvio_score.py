"""Synthetic-only contract tests for the AFTER-INFERENCE reference evaluator."""
import math,sys,tempfile,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from mobile_gvio_score import score

def write_poses(file,time_offset=0,scale=1,shift=(0,0,0)):
    rows=[]
    for i in range(100):
        x=(i/10, math.sin(i/11), math.cos(i/17))
        pos=[scale*x[k]+shift[k] for k in range(3)]
        rows.append(f"{100+i*.1+time_offset:.9f} "+" ".join(f"{v:.9f}" for v in (*pos,0,0,0,1)))
    file.write_text("\n".join(rows)+"\n")

class ReferenceScoreTests(unittest.TestCase):
    def test_sim3_fit_only_after_inference_not_metric_accuracy(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)
            pred=p/"estimate.tum";ref=p/"lidar_ref.tum"
            write_poses(pred)
            write_poses(ref,scale=2.5,shift=(100,200,300))
            result=score(pred,ref)
            self.assertGreaterEqual(result["matched_pose_pairs"],90)
            self.assertLess(result["sim3_shape_ate_rmse_m"],1e-6)
            self.assertAlmostEqual(result["after_truth_similarity_scale"],2.5,places=4)
            self.assertIsNone(result["metric_distance_ate_rmse_m"])
            self.assertFalse(result["estimator_metric_scale_observed"])

    def test_absent_timestamp_overlap_fails_loudly(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)
            pred=p/"estimate.tum";ref=p/"truth.tum"
            write_poses(pred)
            write_poses(ref,time_offset=300)
            with self.assertRaisesRegex(ValueError,"Insufficient time-synchronized"):
                score(pred,ref)

if __name__=="__main__":unittest.main()
