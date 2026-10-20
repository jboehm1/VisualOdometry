"""Synthetic score tests do NOT validate real neural odometry."""
import importlib.util
import tempfile
from pathlib import Path
import unittest

module=Path(__file__).resolve().parents[1]/"hybrid"/"evaluate_outage.py"
spec=importlib.util.spec_from_file_location("hybrid_score",module)
score=importlib.util.module_from_spec(spec);spec.loader.exec_module(score)

class HybridOutageScoring(unittest.TestCase):
    def rows(self,scale):
        p=[];g=[]
        for i in range(11):
            t=1_000_000_000*i+1
            p.append((t,{"dx_world_m":0 if not i else scale,"dz_world_m":0}))
            g.append((t,{"x_world_m":float(i),"z_world_m":0.0}))
        return p,g

    def test_perfect_prediction(self):
        p,g=self.rows(1.)
        s=score.evaluate(p,g,"oracle-fixture","test")
        self.assertEqual(s["endpoint_error_m"],0)
        self.assertEqual(s["rmse_m"],0)
        self.assertEqual(s["duration_s"],10)
        self.assertEqual(s["anchor"],"truth_position_first_row_only")

    def test_drift_is_not_hidden_by_end_alignment(self):
        p,g=self.rows(.8)
        s=score.evaluate(p,g,"biased","test")
        self.assertAlmostEqual(s["endpoint_error_m"],2.,places=6)
        self.assertAlmostEqual(s["endpoint_drift_percent_of_distance"],20.,places=6)

    def test_reject_incorrect_clock(self):
        p,g=self.rows(1.)
        p[7]=(p[7][0]+1,p[7][1])
        with self.assertRaises(ValueError):score.evaluate(p,g)

    def test_reject_gt_delta_in_first_row(self):
        p,g=self.rows(1.)
        p[0]=(p[0][0],{"dx_world_m":1,"dz_world_m":0})
        with self.assertRaises(ValueError):score.evaluate(p,g)

    def test_reject_unphysical_delta(self):
        p,g=self.rows(100.)
        with self.assertRaises(ValueError):score.evaluate(p,g)

    def test_csv_requirements(self):
        with tempfile.TemporaryDirectory() as tmp:
            file=Path(tmp)/"x.csv"
            file.write_text("time_ns,dx_world_m,dz_world_m\n1,0,0\n2,nan,1\n")
            with self.assertRaises(ValueError):score.load_csv(file,("time_ns","dx_world_m","dz_world_m"))

if __name__=="__main__": unittest.main()
