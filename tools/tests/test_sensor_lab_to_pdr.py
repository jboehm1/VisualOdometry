import json
import tempfile
import unittest
from pathlib import Path
from tools.phone.sensor_lab_to_pdr import convert, PhoneCaptureError

HEADER=("sensor_type,sensor_label,sensor_timestamp_ns,elapsed_arrival_ns,"
        "x,y,z,w,accuracy\n")

class SensorLabToPdrTest(unittest.TestCase):
    def run_case(self,text,stride=.74):
        with tempfile.TemporaryDirectory() as d:
            source=Path(d)/"sensor_samples.csv"
            out=Path(d)/"converted.csv"
            source.write_text(text,encoding="utf-8")
            result=convert(source,out,stride_m=stride)
            return result,out.read_text(),json.loads(out.with_suffix(".provenance.json").read_text())
    def test_actual_accel_pressure_but_no_compass_as_heading(self):
        src=HEADER+"1,Accelerometer,200,205,0,0,9.8,,3\n"+(
            "2,Magnetic field,100,107,12,5,9,,3\n") + (
            "6,Air pressure,210,211,1003,,, ,3\n") + (
            "1,Accelerometer,100,120,0,0,9.7,,3\n")
        manifest,data,details=self.run_case(src)
        lines=data.strip().splitlines()
        self.assertEqual("t_ns,type,x,y,z",lines[0])
        self.assertIn("100,START",lines[1])
        self.assertIn("100,STEP_LENGTH",lines[2])
        self.assertEqual(3,manifest["input_samples_used"])
        self.assertNotIn("HEADING",data)
        self.assertNotIn("STEP,",data)
        self.assertEqual("NONE: no phone-to-body transformation or magnetic heading inferred",details["heading_provenance"])
        self.assertIsNone(manifest["ground_truth"])
    def test_requires_manual_stride_and_genuine_acceleration(self):
        with tempfile.TemporaryDirectory() as d:
            f=Path(d)/"sensor_samples.csv"
            f.write_text(HEADER+"6,Pressure,100,110,1000,0,0,,3\n")
            with self.assertRaises(PhoneCaptureError):
                convert(f,Path(d)/"out.csv",stride_m=.74)
            with self.assertRaises(PhoneCaptureError):
                convert(f,Path(d)/"out.csv",stride_m=0)
    def test_rejects_duplicate_timestamps_and_nonfinite(self):
        src=HEADER+"1,Accel,100,110,0,0,9.8,,3\n"+(
            "1,Accel,100,120,0,0,9.8,,3\n") +(
            "1,Accel,200,220,NaN,0,0,,3\n")
        summary,output,_=self.run_case(src)
        self.assertEqual(2,summary["input_samples_rejected"])
        self.assertEqual(1,summary["input_samples_used"])
        self.assertEqual(3,summary["input_samples_total"])

if __name__=="__main__":
    unittest.main()
