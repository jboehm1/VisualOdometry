import importlib.util,csv,tempfile,unittest
from pathlib import Path
source=Path(__file__).resolve().parents[1]/"camera"/"evaluate_illumination.py"
spec=importlib.util.spec_from_file_location("camera_luma_audit",source)
audit=importlib.util.module_from_spec(spec);spec.loader.exec_module(audit)

class CameraFieldLuma(unittest.TestCase):
    def test_report_is_diagnostic_not_fabricated_minimum(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/"test.csv"
            with p.open("w",newline="") as out:
                w=csv.DictWriter(out,fieldnames=sorted(audit.HEADER));w.writeheader()
                for i in range(40):
                    bright=i<25
                    row={"frame_time_ns":str(i+1),
                         "tracking_state":"TRACKING" if bright else "PAUSED",
                         "failure_reason":"NONE" if bright else "INSUFFICIENT_LIGHT",
                         "p10_y":10 if bright else 2,
                         "p50_y":90 if bright else 11,
                         "p90_y":140 if bright else 32,
                         "dark_fraction":.05 if bright else .7,
                         "clipped_fraction":0,"edge_fraction":.12 if bright else .001,
                         "exposure_ms":8.3 if bright else 30.0,"iso":1600}
                    w.writerow(row)
            a=audit.analyze(p)
            self.assertEqual(a["frames"],40)
            self.assertEqual(a["not_tracking_frames"],15)
            self.assertEqual(a["groups"]["tracking"]["p50_y_median"],90)
            self.assertIsNone(a["deployment_threshold"])
            self.assertIsNone(a["minimum_valid_lux"])
    def test_bad_input_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/"x.csv";p.write_text("frame_time_ns,tracking_state\n1,TRACKING\n")
            with self.assertRaises(ValueError):audit.analyze(p)
if __name__=="__main__":unittest.main()
