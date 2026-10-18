import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import oxiod


class OxiodTests(unittest.TestCase):
    def test_imports_gyro_and_full_accel_without_camera(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)
            rows=["time,a,b,c,gx,gy,gz,gravx,gravy,gravz,usrx,usry,usrz"]
            rows.extend(f"{1+i*.01},0,0,0,0,0,{i*.01},0,0,1,0,0,0"
                        for i in range(5))
            (path/"imu.csv").write_text("\n".join(rows)+"\n")
            result=oxiod.convert(path/"imu.csv",path/"out")
            self.assertEqual(result["frame_count"],0)
            self.assertEqual(result["imu_count"],10)
            self.assertIsNone(result["truth"])
            content=(path/"out"/"imu.csv").read_text()
            self.assertIn("9.80665",content)
            self.assertEqual(json.loads((path/"out"/"manifest.json").read_text())["truth_quality"],"none")

    def test_explicit_truth_origin_and_proxy_label(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)
            (path/"imu.csv").write_text("\n".join(
                f"{1+i*.1},0,0,0,0,0,0,0,0,1,0,0,0" for i in range(5))+"\n")
            (path/"vi.csv").write_text("\n".join(
                f"{1+i*.1},0,{i*.5},0,0,0,0,0,1" for i in range(5))+"\n")
            with self.assertRaisesRegex(ValueError,"Reference origin required"):
                oxiod.convert(path/"imu.csv",path/"out",path/"vi.csv")
            result=oxiod.convert(path/"imu.csv",path/"out",path/"vi.csv",
                                 "visual_inertial_proxy")
            self.assertEqual(result["truth_quality"],"visual_inertial_proxy")
            self.assertTrue(Path(result["truth"]).exists())


if __name__=="__main__":
    unittest.main()
