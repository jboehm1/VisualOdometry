import tempfile
import unittest
from pathlib import Path
from tools.phone.assess_observability import analyze, InputError

HEADER = ("frame_time_ns,elapsed_ns,arcore_tracking,arcore_failure,imu_fresh,"
          "camera_quality,low_texture_known,low_texture,gate_state,"
          "arcore_pose_available,inertial_translation,visual_age_ms,reason\n")
def row(n,t,tracking,state,age=0):
    return f"{n},{t},{str(tracking).lower()},NONE,true,NORMAL,false,false,{state},{str(tracking).lower()},false,{age},TEST\n"

class TrackingAvailabilityTests(unittest.TestCase):
    def analyze_text(self, content, source="SYNTHETIC_FIXTURE"):
        with tempfile.TemporaryDirectory() as temp:
            p=Path(temp)/"observability.csv"
            p.write_text(content, encoding="utf-8")
            return analyze(p,source=source)

    def test_counts_intervals_but_never_reports_ate(self):
        data=HEADER+row(1,1_000_000_000,True,"VISUAL_CAUTION")+row(
            2,2_000_000_000,True,"VISUAL_OBSERVED")+row(
            3,3_000_000_000,False,"ORIENTATION_ONLY",600)+row(
            4,4_000_000_000,False,"POSITION_UNKNOWN",1700)
        result=self.analyze_text(data)
        self.assertEqual(4,result["sample_count"])
        self.assertEqual(3.0,result["covered_elapsed_s"])
        self.assertEqual(1.0,result["state_seconds"]["VISUAL_OBSERVED"])
        self.assertIsNone(result["ate_m"])
        self.assertFalse(result["independent_ground_truth"])

    def test_long_gaps_are_excluded_not_interpolated(self):
        result=self.analyze_text(HEADER+row(1,100,True,"VISUAL_OBSERVED")+
            row(2,5_000_000_100,False,"POSITION_UNKNOWN",5000))
        self.assertEqual(0,result["covered_elapsed_s"])
        self.assertIsNone(result["visual_observed_fraction_of_covered_time"])

    def test_forbids_inertial_translation_and_inconsistent_pose(self):
        text=HEADER+row(1,100,False,"ORIENTATION_ONLY",300)
        self.assertEqual(1,self.analyze_text(text)["sample_count"])
        with self.assertRaises(InputError):
            self.analyze_text(text.replace(",false,300,TEST",",true,300,TEST"))
        with self.assertRaises(InputError):
            self.analyze_text(text.replace("ORIENTATION_ONLY,false,false","ORIENTATION_ONLY,true,false"))

    def test_rejects_nonmonotonic_and_invalid_provenance(self):
        text=HEADER+row(2,101,True,"VISUAL_OBSERVED")+row(1,100,True,"VISUAL_OBSERVED")
        with self.assertRaises(InputError):
            self.analyze_text(text)
        with self.assertRaises(InputError):
            self.analyze_text(HEADER+row(1,1,True,"VISUAL_OBSERVED"),source="fake_ground_truth")

if __name__=="__main__":
    unittest.main()
