import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path
from argparse import Namespace

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import run_dataset


def base_args(campaign,kind,root,inputfile,out):
    return Namespace(campaign=campaign,kind=kind,root=root,input=inputfile,
                     estimate=None,truth=None,truth_quality=None,
                     stride_m=0.7,yaw_axis="z",known_distance=None,out=out)


class CampaignDispatchTests(unittest.TestCase):
    def test_phone_field_report_requires_no_camera_for_csv_audit(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory=Path(tmp)
            sample=directory/"vector_vio_20261018_191300_001.csv"
            header=["event","wall_time_ms","frame_time_ns","tracking",
                    "x_m","y_m","z_m","path_m","horizontal_m","displacement_m",
                    "vertical_m","speed_mps","segments","gaps","outliers"]
            with sample.open("w",newline="") as file:
                w=csv.DictWriter(file,fieldnames=header);w.writeheader()
                for i in range(4):
                    w.writerow({"event":"POSE","wall_time_ms":i*100,
                                "frame_time_ns":1000000000+i*100000000,
                                "tracking":"TRACKING","x_m":i,"y_m":0,"z_m":0,
                                "path_m":i,"horizontal_m":i,"displacement_m":i,
                                "vertical_m":0,"speed_mps":1,"segments":1,
                                "gaps":0,"outliers":0})
            args=base_args("C","phone_csv",directory,sample,directory/"result")
            args.known_distance=3.0
            result=run_dataset.run(args)
            self.assertEqual(result["status"],"REAL_PHONE_EXPORTED_TRAJECTORY_AUDITED")
            self.assertTrue(result["independent_reference_available"])
            report=json.loads((directory/"result"/"phone_report.json").read_text())
            self.assertEqual(report["relative_path_error_pct"],0)

    def test_bad_campaign_format_does_not_silently_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory=Path(tmp)
            args=base_args("A","oxiod",directory,None,directory/"result")
            with self.assertRaisesRegex(ValueError,"EuRoC"):
                run_dataset.run(args)
            status=json.loads((directory/"result"/"status.json").read_text())
            self.assertEqual(status["status"],"FAILED")


if __name__=="__main__":
    unittest.main()
