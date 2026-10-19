import importlib.util
import json
from pathlib import Path
import unittest
P=Path(__file__).resolve().parents[1]/"thermal_contract.py"
spec=importlib.util.spec_from_file_location("thermal_contract",P)
m=importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

def fixture():
    px=[20.]*(32*24)
    for y in (8,9,10):
        for x in (15,16,17):px[y*32+x]=85.
    return {"timestamp_s":5.,"width":32,"height":24,"hfov_deg":55,
            "source":"synthetic_fixture","sensor_frame":"thermal_cam",
            "clock":"unix_host","apparent_temperature_c":px}

class ThermalContractTest(unittest.TestCase):
    def test_angular_only_not_person(self):
        f=m.parse_frame(fixture())
        heat=m.heat_components(f)
        self.assertEqual(len(heat),1)
        self.assertEqual(heat[0]["pixel_count"],9)
        self.assertAlmostEqual(heat[0]["azimuth_relative_sensor_rad"],0,places=3)
        self.assertIsNone(heat[0]["distance_m"])
        self.assertEqual(heat[0]["object_class"],"UNCLASSIFIED_HEAT_REGION")
    def test_unverified_unknown_pixel(self):
        data=fixture();data["apparent_temperature_c"][0]=None
        s=m.run_frames([json.dumps(data)])
        self.assertFalse(s["range_available"])
        self.assertFalse(s["human_detection_implemented"])
        self.assertLess(s["mean_valid_pixel_fraction"],1)
    def test_reject_nonfinite(self):
        data=fixture();data["apparent_temperature_c"][0]=float("nan")
        with self.assertRaises(ValueError):m.parse_frame(data)
    def test_reject_bad_clock(self):
        data=fixture();data["clock"]="guess"
        with self.assertRaises(ValueError):m.parse_frame(data)
    def test_strictly_increasing_capture(self):
        line=json.dumps(fixture())
        with self.assertRaises(ValueError):m.run_frames([line,line])

if __name__=="__main__":
    unittest.main()
