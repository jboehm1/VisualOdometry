import importlib.util
import unittest
from pathlib import Path

PATH=Path(__file__).resolve().parents[1]/"coordination"/"android_menu_contract.py"
spec=importlib.util.spec_from_file_location("android_menu_contract",PATH)
contract=importlib.util.module_from_spec(spec)
spec.loader.exec_module(contract)
ROOT=Path(__file__).resolve().parents[2]
MAIN=ROOT/"app"/"src"/"main"/"java"/"dev"/"jboehm"/"vectorvio"/"MainActivity.java"
MANIFEST=ROOT/"app"/"src"/"main"/"AndroidManifest.xml"

class AndroidModeContractTest(unittest.TestCase):
    def test_actual_baseline_exposes_gps_replay_and_flp(self):
        result=contract.inspect(MAIN.read_text(encoding="utf-8"),
                                MANIFEST.read_text(encoding="utf-8"))
        self.assertTrue(result["pass"],result["missing"])
        self.assertEqual("STATIC_ONLY_NOT_UI_TESTED",result["source"])
        # New modes may be legitimately integrated later; never fail just
        # because PDR or COURSES has become available.

    def test_missing_required_mode_fails_explicitly(self):
        # Synthetic three-mode source must continue to fail the five-mode gate
        # even after real PDR and COURSES are integrated in MainActivity.
        synthetic='''void dataMenu(){
            String[] choices={"GPS ROUTES — import GPX / follow / record",
              "ARCORE REPLAY — evaluate saved video with same odometer",
              "GPS ↔ FUSED — Android location A/B (experimental)"};
            startActivity(new Intent(this,GpsRoutesActivity.class));
            playbackPicker();
            startActivity(new Intent(this,LocationComparisonActivity.class));
        }'''
        manifest=MANIFEST.read_text(encoding="utf-8")
        result=contract.inspect(synthetic,manifest,required=contract.TARGET_COMBINED)
        self.assertFalse(result["pass"])
        self.assertIn("PDR",result["missing"])
        self.assertIn("COURSES",result["missing"])

    def test_missing_activity_export_entry_fails_even_when_menu_exists(self):
        actual=MAIN.read_text(encoding="utf-8")
        manifest=MANIFEST.read_text(encoding="utf-8")
        corrupted=manifest.replace('android:name=".LocationComparisonActivity"','android:name=".RenamedActivity"')
        result=contract.inspect(actual,corrupted,("FLP_AB",))
        self.assertFalse(result["pass"])
        self.assertEqual(["FLP_AB"],result["missing"])

    def test_missing_dispatch_is_not_mistaken_for_registered_activity(self):
        actual=MAIN.read_text(encoding="utf-8")
        manifest=MANIFEST.read_text(encoding="utf-8")
        source=actual.replace("LocationComparisonActivity.class","MissingClass.class")
        result=contract.inspect(source,manifest,("FLP_AB",))
        self.assertFalse(result["pass"])
        self.assertTrue(result["modes"]["FLP_AB"]["label_present"])
        self.assertFalse(result["modes"]["FLP_AB"]["action_present"])

    def test_unknown_mode_fails_open(self):
        with self.assertRaises(ValueError):
            contract.inspect("void dataMenu(){}","<manifest></manifest>",["UNKNOWN"])

if __name__=="__main__":unittest.main()
