import csv
import json
import tempfile
import unittest
from pathlib import Path
import importlib.util

ROOT=Path(__file__).resolve().parents[2]

def load(name):
    spec=importlib.util.spec_from_file_location(name,ROOT/"tools"/"pdr"/(name+".py"))
    mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod);return mod

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
            with open(dst) as f: rows=list(csv.DictReader(f))
            self.assertEqual(rows[0]["t_ns"],"1")
            self.assertEqual(rows[1]["t_ns"],"500000001")
            self.assertAlmostEqual(float(rows[0]["z"]),9.80665)

if __name__=="__main__":unittest.main()
