"""OxIOD/RoNIN adapter and Java engine integration tests: SCHEMA FIXTURES only."""
import csv
import importlib.util
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/"tools"/"pdr"))
sys.path.insert(0,str(ROOT/"tools"/"pdr"/"tests"))
import datasets
import run_real
from fixtures import create_oxiod,create_ronin

try:
    import h5py
    import numpy
    HDF5=True
except ImportError:
    HDF5=False

JDK=bool(shutil.which("javac") and shutil.which("java"))


class DatasetPipelineTest(unittest.TestCase):
    def test_oxiod_schema_with_reference_is_separated_and_hashed(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            imu,gt=create_oxiod(root/"src",180)
            manifest=datasets.prepare_oxiod(imu,root/"prepared",gt,
                                             "visual_inertial_proxy",.8)
            prepared=root/"prepared"
            self.assertEqual(manifest["imu_samples"],180)
            self.assertEqual(manifest["reference_quality"],"visual_inertial_proxy")
            self.assertEqual(manifest["diagnostic_counts"]["GYRO"],180)
            self.assertTrue((prepared/"reference_xyz.csv").exists())
            self.assertGreater(manifest["reference"]["reference_polyline_xy_m"],3)
            self.assertAlmostEqual(manifest["reference"]["reference_coverage_time_fraction"],1,places=6)
            rows=list(csv.DictReader((prepared/"events.csv").open(newline="")))
            self.assertEqual(rows[0]["type"],"START")
            self.assertEqual(rows[1]["type"],"STEP_LENGTH")
            self.assertEqual(rows[2]["type"],"ACCEL")
            self.assertAlmostEqual(float(rows[1]["x"]),.8)
            self.assertAlmostEqual(float(rows[2]["z"]),9.80665)
            self.assertNotIn("HEADING",{x["type"] for x in rows})
            self.assertEqual(manifest["files_sha256"]["events.csv"],
                             datasets.checksum(prepared/"events.csv"))
            reference_text=(prepared/"reference_xyz.csv").read_text()
            self.assertIn("x_m",reference_text)
            self.assertNotIn("x_m",(prepared/"events.csv").read_text())

    def test_oxiod_rejects_corrupt_time_and_reference_assumption(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);imu,gt=create_oxiod(root/"src")
            with self.assertRaisesRegex(ValueError,"required together"):
                datasets.prepare_oxiod(imu,root/"bad",gt)
            with imu.open("a",encoding="utf-8") as f:
                f.write("1.4,0,0,0,0,0,0,0,0,1,0,0,0,20,10,15\n")
            with self.assertRaisesRegex(ValueError,"Non-increasing"):
                datasets.prepare_oxiod(imu,root/"bad2")
            self.assertFalse((root/"bad2").exists())

    def test_oxiod_requires_exact_complete_native_schema(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);root.mkdir(exist_ok=True)
            (root/"bad.csv").write_text("Time,roll,pitch,yaw,gyroX\n1.0,0,0,0,1\n")
            with self.assertRaisesRegex(ValueError,"fewer than 16"):
                datasets.prepare_oxiod(root/"bad.csv",root/"prepared")

    @unittest.skipUnless(HDF5,"h5py/numpy required for RoNIN original format")
    def test_ronin_hdf5_schema_start_frame_and_tango_isolated(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            src=create_ronin(root/"native")
            m=datasets.prepare_ronin(src,root/"prepared",
                "visual_inertial_proxy",.9,"fixture_subject")
            self.assertEqual(m["imu_samples"],188)
            self.assertEqual(m["start_frame"],12)
            self.assertEqual(m["reference_quality"],"visual_inertial_proxy")
            self.assertEqual(m["subject_id"],"fixture_subject")
            self.assertFalse(m["original_imu_calibration_applied"])
            with (root/"prepared"/"events.csv").open(newline="") as f:
                events=list(csv.DictReader(f))
            self.assertEqual(int(events[0]["t_ns"]),1_000_000_000)
            self.assertTrue(all(e["type"] not in ("HEADING","GPS_COURSE","POSE") for e in events))
            self.assertAlmostEqual(float(events[2]["z"]),9.80665,places=5)
            self.assertTrue((root/"prepared"/"reference_xyz.csv").exists())

    @unittest.skipUnless(HDF5,"h5py/numpy required for RoNIN original format")
    def test_ronin_prevents_incorrect_optical_claim_or_misaligned_pose(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            native=create_ronin(root/"native",with_reference=False)
            with self.assertRaisesRegex(ValueError,"never optical"):
                datasets.prepare_ronin(native,root/"nope","optical")
            with self.assertRaisesRegex(ValueError,"missing/misaligned"):
                datasets.prepare_ronin(native,root/"nope2","visual_inertial_proxy")
            self.assertFalse((root/"nope2").exists())

    @unittest.skipUnless(JDK,"Java 17+ required for exact Android PDR engine")
    def test_oxiod_to_actual_java_replay_and_explicit_scoring(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            imu,gt=create_oxiod(root/"src")
            datasets.prepare_oxiod(imu,root/"prepared",gt,"visual_inertial_proxy",.74)
            without=run_real.run(root/"prepared",root/"java")
            self.assertEqual(without["engine"],
                             "Android Java PdrEngine/StepPeakDetector, same Java classes")
            self.assertEqual(without["steps"],without["steps_without_heading"])
            self.assertFalse(without["distance_scored"])
            self.assertTrue((root/"java"/"path.csv").exists())
            self.assertFalse(without["accuracy_verified"])
            second=run_real.run(root/"prepared",root/"java_scored","xy")
            self.assertTrue(second["distance_scored"])
            self.assertEqual(second["reference_quality"],"visual_inertial_proxy")
            self.assertIn("proxy",second["reference_warning"])
            with self.assertRaises(ValueError):
                run_real.run(root/"prepared",root/"java_repeated","xyz_bad")

    @unittest.skipUnless(JDK and HDF5,"Java plus h5py required")
    def test_ronin_to_actual_java_replay_and_no_truth_leakage(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            src=create_ronin(root/"src")
            datasets.prepare_ronin(src,root/"prepared",
                                   "visual_inertial_proxy",.75)
            result=run_real.run(root/"prepared",root/"java","xy")
            self.assertEqual(result["dataset"],"ronin")
            self.assertTrue(result["distance_scored"])
            self.assertEqual(result["steps"],result["steps_without_heading"])
            provenance=json.loads((root/"java"/"provenance.json").read_text())
            self.assertTrue(provenance["reference_not_passed_to_java"])
            self.assertIn("PdrEngine.java",provenance["estimator_source_sha256"])

    @unittest.skipUnless(JDK,"Java required for exact engine replay")
    def test_tampered_estimator_stream_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            imu,_=create_oxiod(root/"src")
            datasets.prepare_oxiod(imu,root/"prepared")
            with (root/"prepared"/"events.csv").open("a") as f:
                f.write("1000000000,HEADING,3,0,0,\n")
            with self.assertRaisesRegex(ValueError,"checksum mismatch"):
                run_real.run(root/"prepared",root/"java")

    def test_never_overwrite_existing_prepared_results(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);imu,_=create_oxiod(root/"src")
            datasets.prepare_oxiod(imu,root/"prepared")
            with self.assertRaises(FileExistsError):
                datasets.prepare_oxiod(imu,root/"prepared")


    def test_batch_prevents_cross_split_subject_leakage(self):
        import batch
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            imu,_=create_oxiod(root/"src")
            plan={"schema_version":1,"sequences":[
                {"id":"s1","dataset":"oxiod","split":"train","subject_id":"person_A",
                 "imu":str(imu),"step_length_m":.74},
                {"id":"s2","dataset":"oxiod","split":"test","subject_id":"person_A",
                 "imu":str(imu),"step_length_m":.74}]}
            src=root/"plan.json"
            src.write_text(json.dumps(plan))
            with self.assertRaisesRegex(ValueError,"leakage"):
                batch.load_manifest(src)
            plan["sequences"][1]["subject_id"]="person_B"
            src.write_text(json.dumps(plan))
            self.assertEqual(len(batch.load_manifest(src)["sequences"]),2)

    @unittest.skipUnless(JDK and HDF5,"Java plus h5py required for batch integration")
    def test_batch_executes_native_oxiod_and_ronin_with_true_java_engine(self):
        import batch
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            imu,_=create_oxiod(root/"data"/"ox")
            ronin=create_ronin(root/"data"/"ronin")
            plan={"schema_version":1,"sequences":[
                {"id":"ox","dataset":"oxiod","split":"exploratory","subject_id":"person_A",
                 "imu":str(imu),"step_length_m":.74},
                {"id":"ro","dataset":"ronin","split":"test","subject_id":"person_B",
                 "sequence":str(ronin),"step_length_m":.74}]}
            source=root/"plan.json";source.write_text(json.dumps(plan))
            result=batch.run_batch(source,root/"output")
            self.assertEqual(result["sequence_count"],2)
            self.assertEqual(result["score_count"],0)
            self.assertTrue(result["subjects_split_validated"])
            self.assertFalse(result["accuracy_verified_on_device"])
            self.assertTrue((root/"output"/"ro"/"java"/"summary.json").is_file())
            self.assertTrue((root/"output"/"ox"/"java"/"summary.json").is_file())

if __name__=="__main__":unittest.main()
