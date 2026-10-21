import importlib.util
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

SCRIPT=Path(__file__).resolve().parents[1]/"location"/"analyze.py"
spec=importlib.util.spec_from_file_location("vio_location_analysis",SCRIPT)
loc=importlib.util.module_from_spec(spec)
spec.loader.exec_module(loc)

FIXES=("source,fix_elapsed_ns,receive_elapsed_ns,fix_wall_ms,latitude_deg,longitude_deg,"
       "horizontal_accuracy_m,altitude_m,vertical_accuracy_m,speed_mps,speed_accuracy_mps,"
       "bearing_deg,bearing_accuracy_deg,mock,validity\n"
       "GPS_PROVIDER,10000000000,10020000000,1,48,7,4,,,,,,,false,OK\n"
       "GOOGLE_FUSED,10050000000,10080000000,1,48,7.00001,3,,,,,,,false,OK\n")
PAIRS=("gps_time_ns,fused_time_ns,abs_delta_ms,disagreement_m,gps_accuracy_m,"
       "fused_accuracy_m,gps_lat,gps_lon,fused_lat,fused_lon\n"
       "10000000000,10050000000,50,0.75,4,3,48,7,48,7.00001\n")
META={"schema":"vector-location-ab/1","raw_fix_count":2,"paired_count":1}

def fixture(folder,fixes=FIXES,pairs=PAIRS,meta=META,extra=False):
    path=Path(folder)/"location.zip"
    with zipfile.ZipFile(path,"w") as z:
        z.writestr("fixes.csv",fixes)
        z.writestr("pairs.csv",pairs)
        z.writestr("metadata.json",json.dumps(meta))
        if extra:z.writestr("extra.txt","forbidden")
    return path

class LocationABAnalysisTest(unittest.TestCase):
    def test_no_absolute_accuracy_without_truth(self):
        with tempfile.TemporaryDirectory() as d:result=loc.analyze(fixture(d))
        self.assertEqual("COMPARATIVE_DIAGNOSTIC_ONLY",result["status"])
        self.assertEqual("NOT_AVAILABLE",result["ground_truth"])
        self.assertEqual(1,result["paired_count"])
        self.assertEqual(.75,result["mean_pair_disagreement_m"])
        self.assertEqual(1,result["sources"]["GPS_PROVIDER"]["eligible_fixes"])
        self.assertEqual(1,result["sources"]["GOOGLE_FUSED"]["eligible_fixes"])
    def test_static_reference_must_have_provenance(self):
        with tempfile.TemporaryDirectory() as d:
            path=fixture(d)
            with self.assertRaises(ValueError):loc.analyze(path,(48,7))
            r=loc.analyze(path,(48,7),"User surveyed point, only illustrative")
        self.assertEqual("USER_ASSERTED_STATIC_CONTROL_NOT_VERIFIED",r["status"])
        self.assertEqual(0,r["sources"]["GPS_PROVIDER"]["user_asserted_static_control_rmse_m"])
    def test_missing_fused_source_not_fabricated(self):
        with tempfile.TemporaryDirectory() as d:
            path=fixture(d,FIXES.splitlines(True)[0]+FIXES.splitlines(True)[1],
                PAIRS.splitlines(True)[0],{**META,"raw_fix_count":1,"paired_count":0})
            result=loc.analyze(path)
        self.assertEqual(0,result["sources"]["GOOGLE_FUSED"]["observations"])
        self.assertIsNone(result["mean_pair_disagreement_m"])
    def test_corrupt_metadata_refused(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaisesRegex(ValueError,"counts disagree"):
                loc.analyze(fixture(d,meta={**META,"paired_count":9}))
    def test_zip_with_extra_file_refused(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaisesRegex(ValueError,"exactly"):
                loc.analyze(fixture(d,extra=True))
    def test_nonfinite_distance_and_overwide_pair_refused(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(ValueError):loc.analyze(fixture(d,pairs=PAIRS.replace(",0.75,",",NaN,")))
            with self.assertRaises(ValueError):loc.analyze(fixture(d,pairs=PAIRS.replace(",50,",",2000,")))

if __name__=="__main__":unittest.main()
