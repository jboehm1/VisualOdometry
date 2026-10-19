"""Pure synthetic contract tests; separate real ADVIO suite downloads recorded data."""
import csv
import importlib.util
import io
import json
import math
from pathlib import Path
import tempfile
import unittest
import zipfile

TARGET=Path(__file__).parents[1]/"real_phone_dropout.py"
spec=importlib.util.spec_from_file_location("gpx_real_dropout",TARGET)
mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)


def fixture(path,drift=0):
    # Reference horizontal east/north with a deliberate bend to observe yaw.
    reference=[];pixel=[]
    for i in range(1500):
        t=i*.1
        east=min(t,65)
        north=max(0,t-65)
        reference.append([t,east,2.,north,0,0,0,1])
        # Pixel ARCore rotates EN axes into x/-z with true unit scale.
        pixel.append([t,east+drift*max(0,t-20),1.6,-north,0,0,0,1])
    with zipfile.ZipFile(path,"w",zipfile.ZIP_DEFLATED) as z:
        for key,array in [("pixel",pixel),("reference",reference)]:
            data=io.StringIO();csv.writer(data).writerows(array)
            z.writestr(mod.SOURCE_NAMES[key],data.getvalue())


class RealPhoneDropoutContracts(unittest.TestCase):
    def test_independent_scorer_math_and_incomplete_estimates(self):
        assert abs(mod.geo_error_m(*mod.world_to_geo(10,-5),10,-5))<1e-6
        with tempfile.TemporaryDirectory() as tmp:
            f=Path(tmp)/"replay.csv"
            f.write_text("camera_elapsed_ns,state,latitude,longitude\n"
                         "1000000000,VIO_HOLD,,\n"
                         "2000000000,VIO_HOLD,48,7\n",encoding="utf-8")
            score=mod.score(f,[(1000000000,0,0,0),(2000000000,0,0,1)],0,3,1.5,0)
            self.assertEqual(score["matched"],1)
            self.assertEqual(score["missing"],1)
    def test_no_postcutoff_gnss_and_real_java_vio_hold(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp=Path(tmp);fixture(tmp/"advio.zip")
            with zipfile.ZipFile(tmp/"advio.zip") as z:
                pixel=mod.read_pose_csv(z,mod.SOURCE_NAMES["pixel"])
                reference=mod.read_pose_csv(z,mod.SOURCE_NAMES["reference"])
            truth,cutoff,nfix,duration=mod.synthesize(
                pixel,reference,30.,tmp/"input.zip",sigma_gnss=.1)
            self.assertGreater(duration,100)
            self.assertGreater(nfix,20)
            with zipfile.ZipFile(tmp/"input.zip") as z:
                names=mod.replay.archive_members(z)
                fixes=list(mod.replay.rows(z,names["fixes"]))
                assert all(int(p["fix_elapsed_ns"])<31_000_000_000 for p in fixes)
            rep=mod.replay.run(tmp/"input.zip",tmp/"replayed",origin="synthetic_fixture")
            self.assertFalse(rep["absolute_accuracy_validated"])
            out=mod.score(tmp/"replayed"/"replay.csv",truth,31_000_000_000,nfix,.1,30)
            self.assertEqual(out["status"],"SCORED")
            self.assertGreater(out["last_dropout_seconds"],70)
            self.assertLess(out["p95_error_m"],2.0,"Synthetic dropout expected 0 drift: "+str(out)+"; replay="+(tmp/"replayed"/"replay.csv").read_text()[-1300:])
            self.assertIn("VIO_HOLD",out["states"])
    def test_calibration_rejects_disconnected_segments(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp=Path(tmp);fixture(tmp/"advio.zip")
            with zipfile.ZipFile(tmp/"advio.zip") as z:
                pixel=mod.read_pose_csv(z,mod.SOURCE_NAMES["pixel"])
                ref=mod.read_pose_csv(z,mod.SOURCE_NAMES["reference"])
            # A break in ARCore must not be bridged as one continuous reference.
            chunk=pixel[:150]+[[row[0]+500,*row[1:]] for row in pixel[150:300]]
            with self.assertRaises(ValueError):
                mod.synthesize(chunk,ref,20.,tmp/"input.zip")
    def test_reference_and_pixel_same_time_and_yaw_required(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp=Path(tmp);fixture(tmp/"advio.zip")
            with zipfile.ZipFile(tmp/"advio.zip") as z:
                pixel=mod.read_pose_csv(z,mod.SOURCE_NAMES["pixel"])
                ref=mod.read_pose_csv(z,mod.SOURCE_NAMES["reference"])
            with self.assertRaises(ValueError):
                mod.synthesize(pixel,ref,0,tmp/"out.zip")
            with self.assertRaises(ValueError):
                mod.synthesize(pixel,ref,10000,tmp/"out.zip")


if __name__=="__main__":unittest.main()
