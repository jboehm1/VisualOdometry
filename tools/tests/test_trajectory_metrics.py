import sys
import tempfile
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"eval"))
import trajectory_metrics as v
import math

class TrajectoryEvalTest(unittest.TestCase):
    @staticmethod
    def walk():
        # One second sample intervals, curving path and vertical variation.
        truth=[];est=[]
        yaw=math.radians(33)
        for i in range(26):
            t=(i+1)*1_000_000_000
            x=i*1.6;z=math.sin(i*.3)*3;y=i*.13
            est.append(v.Sample(t,(x,y,z)))
            u=math.cos(yaw)*x-math.sin(yaw)*z+80
            w=math.sin(yaw)*x+math.cos(yaw)*z-14
            truth.append(v.Sample(t,(u,y+5,w)))
        return est,truth
    def test_metric_alignment_preserves_scale_and_precision(self):
        est,truth=self.walk()
        r=v.analyze(est,truth,up="y")
        a=r["segments"][0]
        self.assertEqual("METRIC_RIGID_YAW_ALIGNED",a["status"])
        self.assertEqual(33,a["alignment_yaw_deg"])
        self.assertLess(a["ate_rmse_m"],1e-5)
        self.assertLess(a["rpe_translation_rmse_m"],1e-5)
        self.assertFalse(a["metric_scale_fitted"])
        self.assertEqual(1.,r["matched_coverage_fraction"])
    def test_mis_scaled_estimate_cannot_get_zero_error(self):
        est,truth=self.walk()
        est=[v.Sample(s.ns,tuple(1.25*x for x in s.xyz)) for s in est]
        a=v.analyze(est,truth,up="y")["segments"][0]
        self.assertGreater(a["ate_rmse_m"],1)
    def test_gt_gap_has_no_fake_motion(self):
        est,truth=self.walk()
        sparse=[t for i,t in enumerate(truth) if i<6 or i>18]
        a=v.analyze(est,sparse,up="y",gt_gap_s=.15)
        self.assertLess(a["matched_coverage_fraction"],.6)
    def test_uncertain_only_short_segment(self):
        est,truth=self.walk()
        est=[v.Sample(i.ns,i.xyz,1 if n<12 else 2) for n,i in enumerate(est)]
        r=v.analyze(est,truth,up="y")
        self.assertEqual(2,len(r["segments"]))
        self.assertEqual("NOT_EVALUATED_ACROSS_SEGMENT_RESETS",r["global_metric_accuracy"])
    def test_nonmonotonic_and_parse(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/"test.txt"
            p.write_text("#timestamp x y z qx qy qz qw\n1.0 0 0 0 0 0 0 1\n2.0 2 0 0 0 0 0 1\n")
            a=v.parse(p,"tum")
            self.assertEqual(1_000_000_000,a[0].ns)
            p.write_text("1.0 0 0 0\n1.0 1 0 0\n")
            with self.assertRaises(ValueError):v.parse(p,"tum")
    def test_vector_motion_parsing_breaks_after_gap(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/"m.csv"
            p.write_text("frame_time_ns,metric_x,metric_y,metric_z\n1000000000,0,0,0\n2000000000,1,0,0\n2100000000,1.1,0,0\n")
            a=v.parse(p,"vector-motion")
            self.assertEqual([1,2,2],[s.segment for s in a])
    def test_new_vector_motion_segment_column_overrides_gap_heuristic(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/"m.csv"
            p.write_text("frame_time_ns,metric_x,metric_y,metric_z,odometer_segment\n"
                         "1000000000,0,0,0,3\n1010000000,1,0,0,4\n"
                         "1020000000,1.1,0,0,4\n")
            self.assertEqual([3,4,4],[s.segment for s in v.parse(p,"vector-motion")])
    def test_no_truth_is_explicit(self):
        with self.assertRaises(FileNotFoundError):v.parse(Path("/no/such/file"),"tum")
        est,gt=self.walk()
        data=v.analyze(est,gt[:-4],up="y")
        self.assertLess(data["matched_samples"],len(est))

if __name__=="__main__":unittest.main()
