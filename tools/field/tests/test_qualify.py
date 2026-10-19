"""No-network qualification contracts using synthetic CSVs, NEVER actual device evidence."""
import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from qualify import qualify,validate_manifest


HEADERS=["event","wall_time_ms","frame_time_ns","tracking",
         "x_m","y_m","z_m","path_m","horizontal_m","displacement_m",
         "vertical_m","speed_mps","segments","gaps","outliers"]


def make_csv(path, path_m=10., backward=False):
    with path.open("w",newline="") as fd:
        writer=csv.DictWriter(fd,fieldnames=HEADERS)
        writer.writeheader()
        for i in range(11):
            writer.writerow({"event":"POSE","wall_time_ms":i*1000,
                             "frame_time_ns":(10-i if backward else i)*1_000_000_000+1,
                             "tracking":"TRACKING",
                             "x_m":i,"y_m":0,"z_m":0,
                             "path_m":i*path_m/10,"horizontal_m":i*path_m/10,
                             "displacement_m":i*path_m/10,
                             "vertical_m":0,"speed_mps":path_m/10,
                             "segments":1,"gaps":0,"outliers":0})


class FieldQualityContracts(unittest.TestCase):
    def test_real_device_not_asserted_from_fixture(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            jobs=[]
            for i in range(3):
                source=f"straight{i}.csv";make_csv(root/source,10*(1+i*.002))
                jobs.append({"id":f"walk{i}","file":source,"scenario":"straight",
                             "known_path_m":10.})
                source=f"still{i}.csv";make_csv(root/source,.02)
                jobs.append({"id":f"still{i}","file":source,"scenario":"stationary",
                             "known_path_m":0.})
            (root/"manifest.json").write_text(json.dumps({"schema_version":1,"trials":jobs}))
            r=qualify(root/"manifest.json",root/"report.json")
            self.assertEqual(r["status"],"PASS")
            self.assertEqual(len(r["trials"]),6)
            self.assertFalse(r["physical_android_app_executed_by_this_script"])
            self.assertFalse(r["metric_6dof_ground_truth_available"])
            self.assertEqual(r["gates"]["straight_p90_error_pct"]["n"],3)
            self.assertTrue((root/"report.json").is_file())

    def test_incomplete_cohort_never_automatically_passes(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);make_csv(root/"alone.csv")
            jobs=[{"id":"alone","file":"alone.csv","scenario":"straight","known_path_m":10}]
            (root/"manifest.json").write_text(json.dumps({"schema_version":1,"trials":jobs}))
            r=qualify(root/"manifest.json",root/"out.json")
            self.assertEqual(r["status"],"INSUFFICIENT_EVIDENCE")

    def test_bad_csv_and_data_path_fail_closed(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);make_csv(root/"wrong.csv",backward=True)
            row={"id":"bad","file":"wrong.csv","scenario":"straight","known_path_m":10}
            manifest=root/"manifest.json"
            manifest.write_text(json.dumps({"schema_version":1,"trials":[row]}))
            with self.assertRaisesRegex(ValueError,"backward|duplicated"):
                qualify(manifest,root/"report.json")
            row["file"]="../outside.csv"
            manifest.write_text(json.dumps({"schema_version":1,"trials":[row]}))
            with self.assertRaisesRegex(ValueError,"relative"):
                validate_manifest(manifest)

    def test_stationary_with_nonzero_truth_is_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);make_csv(root/"x.csv")
            row={"id":"x","file":"x.csv","scenario":"stationary","known_path_m":10}
            manifest=root/"manifest.json"
            manifest.write_text(json.dumps({"schema_version":1,"trials":[row]}))
            with self.assertRaisesRegex(ValueError,"0m"):
                validate_manifest(manifest)


if __name__=="__main__":unittest.main()
