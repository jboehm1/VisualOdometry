"""Synthetic OxIOD layout and quaternion tests. Not proof of real dataset accuracy."""
import csv,importlib.util,sys,tempfile
from pathlib import Path
import unittest
try:import numpy as np
except ImportError:np=None

class OxiodMotionConverter(unittest.TestCase):
    def load(self):
        p=Path(__file__).resolve().parents[1]/"oxiod_motion_features.py"
        if str(p.parent) not in sys.path:sys.path.insert(0,str(p.parent))
        spec=importlib.util.spec_from_file_location("oxiod_motion_converter",p)
        module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        return module
    @unittest.skipIf(np is None,"numpy train backend missing")
    def test_device_frame_quaternion(self):
        s=self.load()
        v=np.array([1.,0,0])
        identity=s.quaternion_inverse_rotate([1,0,0,0],v)
        np.testing.assert_allclose(identity,v)
        quarter=s.quaternion_inverse_rotate([2**-.5,0,0,2**-.5],v)
        np.testing.assert_allclose(quarter,[0,-1,0],atol=1e-8)
        with self.assertRaises(ValueError):s.quaternion_inverse_rotate([0,0,0,0],v)
    @unittest.skipIf(np is None,"numpy train backend missing")
    def test_oxiod_syn_to_real_java_features(self):
        s=self.load()
        with tempfile.TemporaryDirectory() as d:
            i=Path(d)/"imu.csv";g=Path(d)/"vi.csv"
            with i.open("w",newline="") as f1,g.open("w",newline="") as f2:
                wi,wg=csv.writer(f1),csv.writer(f2)
                wi.writerow(["c"+str(n) for n in range(16)])
                wg.writerow(["c"+str(n) for n in range(9)])
                for idx in range(1200):
                    t=idx/100
                    wi.writerow([t,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0])
                    wg.writerow([t,0,.8*t,0,0,0,0,0,1])
            windows=s.sequence_features(i,g)
            self.assertGreaterEqual(len(windows),10)
            for row in windows:
                self.assertEqual(len(row),26)
                self.assertAlmostEqual(row[-2],1.008,places=4)
                self.assertAlmostEqual(row[-1],0.,places=5)
                self.assertAlmostEqual(row[2],9.80665,places=4)
if __name__=="__main__":unittest.main()
