"""Synthetic CSV contract tests; never claim phone/odometer quality."""
import csv,importlib.util,tempfile,unittest
from pathlib import Path
src=Path(__file__).resolve().parents[1]/"camera"/"evaluate_camera2_lab.py"
spec=importlib.util.spec_from_file_location("camera2_lab_analysis",src)
analyzer=importlib.util.module_from_spec(spec);spec.loader.exec_module(analyzer)

class Camera2LabQuality(unittest.TestCase):
    def test_shutter_cap_iso_ceiling_and_motion_proxy(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/"camera.csv"
            columns=sorted(analyzer.REQUIRED)
            with p.open("w",newline="") as stream:
                w=csv.DictWriter(stream,fieldnames=columns);w.writeheader()
                for i in range(8):
                    mode="WALK" if i<4 else "BIKE"
                    row=dict.fromkeys(columns,"0")
                    row.update(time_elapsed_ns=str(i+1),profile=mode,auto="true",
                        p10="10",p50="90",p90="130",
                        gyro_rad_s="1.0",actual_exposure_ns="1000000",
                        actual_iso="800",shutter_cap_ns="2000000",
                        selected_exposure_ns="1000000",selected_iso="800",
                        iso_ceiling="1600",clipped_fraction=".01",
                        coherent_edge_fraction=".15",feature_median="70")
                    w.writerow(row)
            score=analyzer.summarize(p)
            self.assertEqual(score["n_samples"],8)
            self.assertEqual(score["by_mode"]["BIKE"]["fraction_actual_iso_above_user_cap"],0)
            self.assertEqual(score["by_mode"]["WALK"]["fraction_actual_shutter_above_gyro_cap"],0)
            self.assertFalse(score["visual_odometry_accuracy_verified"])
            self.assertIsNone(score["photometric_limit_lux"])
    def test_empty_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/"empty.csv";p.write_text("not,our,csv\n")
            with self.assertRaises(ValueError):analyzer.summarize(p)
if __name__=="__main__":unittest.main()
