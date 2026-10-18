import csv
import json
import tempfile
import unittest
from pathlib import Path
import importlib.util

ROOT = Path(__file__).resolve().parents[2]

def load(name):
    spec = importlib.util.spec_from_file_location(name,ROOT/"tools"/"pdr"/(name+".py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod

class PdrToolsTests(unittest.TestCase):
    def test_relative_trajectory_zero_error(self):
        m=load("evaluate")
        trajectory=[(1,0,0),(2,1,0),(3,1,1)]
        truth=[(1,25,-7),(2,26,-7),(3,26,-6)]
        s=m.score(trajectory,truth,2)
        self.assertEqual(s["final_error_m"],0)
        self.assertAlmostEqual(s["charted_path_distance_error_pct"],0)

    def test_refuses_extrapolation(self):
        m=load("evaluate")
        with self.assertRaises(ValueError):
            m.score([(1,0,0),(5,2,0)],[(2,0,0),(3,1,0)])

    def test_convert_acceleration_scale_and_monotonicity(self):
        m=load("convert_accel")
        with tempfile.TemporaryDirectory() as d:
            src=Path(d)/"src.csv";dst=Path(d)/"dst.csv"
            src.write_text("t,ax,ay,az\n0.0,0,0,1\n0.5,1,0,1\n")
            self.assertEqual(m.convert(src,dst,0,[1,2,3],"s","g",1),2)
            with open(dst) as f:rows=list(csv.DictReader(f))
            self.assertEqual(rows[0]["t_ns"],"1")
            self.assertEqual(rows[1]["t_ns"],"500000001")
            self.assertAlmostEqual(float(rows[0]["z"]),9.80665)

    def test_gap_does_not_fake_charted_path_distance(self):
        m=load("evaluate")
        # Third point is AFTER a missing-heading interval.
        pred=[(1,0,0,False),(2,.8,0,False),(3,1.6,0,True)]
        truth=[(1,0,0),(2,.8,0),(3,3.2,0)]
        result=m.score(pred,truth,3.2,total_distance_m=2.4)
        self.assertEqual(result["path_breaks_due_to_missing_heading"],1)
        self.assertAlmostEqual(result["charted_path_distance_m"],.8)
        self.assertNotIn("charted_path_distance_error_pct",result)
        self.assertAlmostEqual(result["pdr_total_distance_m"],2.4)
        self.assertAlmostEqual(result["pdr_total_distance_error_pct"],-25.0)
        self.assertGreater(result["final_error_m"],0)

    def test_distance_only_when_heading_absent(self):
        m=load("evaluate")
        actual=m.distance_metrics(7.4,10,{"steps":10,"steps_without_heading":10,"rows":11})
        self.assertAlmostEqual(actual["pdr_total_distance_error_pct"],-26.0)
        self.assertEqual(actual["steps_without_heading"],10)

    def test_load_rejects_corrupt_gaps_and_timestamps(self):
        m=load("evaluate")
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/"path.csv"
            path.write_text("t_ns,east_m,north_m,gap_before\n1,0,0,false\n2,1,0,true\n")
            points=m.load(path)
            self.assertTrue(points[1][3])
            path.write_text("t_ns,east_m,north_m,gap_before\n1,0,0,false\n2,1,0,maybe\n")
            with self.assertRaisesRegex(ValueError,"Unrecognized gap"):
                m.load(path)
            path.write_text("t_ns,east_m,north_m\n2,0,0\n2,1,0\n")
            with self.assertRaisesRegex(ValueError,"increasing"):
                m.load(path)

    def test_distance_accuracy_requires_java_summary(self):
        m=load("evaluate")
        with self.assertRaises(SystemExit):
            m.main(["--reference-distance-m","100"])
        with tempfile.TemporaryDirectory() as directory:
            d=Path(directory)
            summary=d/"java.json"
            summary.write_text(json.dumps({"distance_m":100.5,"steps":135,
                "steps_without_heading":22,"rows":159,"rejected_rows":0}))
            report=m.main(["--summary",str(summary),"--reference-distance-m","100",
                           "--out",str(d/"report.json")])
            self.assertAlmostEqual(report["pdr_total_distance_error_pct"],.5)
            self.assertEqual(report["steps_without_heading"],22)
            self.assertEqual(json.loads((d/"report.json").read_text())["pdr_total_distance_m"],100.5)

    def test_rejects_nonfinite_distance_and_broken_summary(self):
        m=load("evaluate")
        with self.assertRaises(ValueError):
            m.distance_metrics(float("nan"),100)
        with self.assertRaises(ValueError):
            m.distance_metrics(12,0)
        with self.assertRaises(ValueError):
            m.distance_metrics(12,100,{"steps":2,"steps_without_heading":3})
        with self.assertRaises(ValueError):
            m.score([(1,0,0),(2,float("nan"),0)],[(1,0,0),(2,1,0)])

if __name__=="__main__":
    unittest.main()
