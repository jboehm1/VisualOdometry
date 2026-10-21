"""Synthetic schema/training checks only. Not evidence of metric navigation accuracy."""
import csv
import importlib.util
import tempfile
from pathlib import Path
import unittest

src=Path(__file__).resolve().parents[1]/"train_motion_mlp.py"
spec=importlib.util.spec_from_file_location("pdr_model_training",src)
trainer=importlib.util.module_from_spec(spec);spec.loader.exec_module(trainer)

class MotionCheckpointTrain(unittest.TestCase):
    def make_file(self,path,leak=False):
        columns=["sequence_id","person_id","split"]+trainer.COLUMNS+["dx_device_m","dy_device_m"]
        with path.open("w",newline="") as f:
            w=csv.DictWriter(f,fieldnames=columns);w.writeheader()
            for split,person in [("train","person_a"),("val","person_b"),("test","person_c")]:
                for j in range(18):
                    row={"sequence_id":split+"-1","person_id":person if not leak else "same",
                         "split":split,"dx_device_m":str(.6+j*.001),"dy_device_m":"0.1"}
                    row.update({k:str((j+i)*.08) for i,k in enumerate(trainer.COLUMNS)})
                    w.writerow(row)
    def test_isolation(self):
        with tempfile.TemporaryDirectory() as temp:
            file=Path(temp)/"data.csv";self.make_file(file)
            data,subjects,seq=trainer.read_data(file)
            self.assertEqual(len(data["train"]),18)
            self.assertEqual(len(subjects["test"]),1)
            self.assertEqual(len(seq),3)
            self.make_file(file,leak=True)
            with self.assertRaisesRegex(ValueError,"person leaked"):
                trainer.read_data(file)
    def test_binary_compatible_and_deterministic(self):
        try:import numpy as np
        except ImportError:self.skipTest("numpy needed for train-only contract")
        with tempfile.TemporaryDirectory() as temp:
            file=Path(temp)/"data.csv";self.make_file(file)
            data,_,_=trainer.read_data(file)
            fitted=trainer.fit(data,epochs=8)
            destination=Path(temp)/"checkpoint.bin"
            result=trainer.save_checkpoint(destination,"fixture","CC-BY-4.0","synthetic",fitted[:6])
            self.assertEqual(len(result["sha256"]),64)
            raw=destination.read_bytes()
            self.assertIn(b"PDR_AI_MLP_V1",raw[:40])
            self.assertLess(len(raw),10000)
            self.assertEqual(trainer.save_checkpoint(destination,"fixture","CC-BY-4.0","synthetic",fitted[:6])["sha256"],result["sha256"])
            self.assertIn("test",fitted[6])
if __name__=="__main__":unittest.main()
