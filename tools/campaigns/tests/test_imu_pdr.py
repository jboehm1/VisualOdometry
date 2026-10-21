import csv
import math
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import imu_pdr


def make_imu(duration=12.0, cadence_hz=100, walking=True, gyro_rate=0.0):
    records=[]
    for i in range(int(duration*cadence_hz)):
        t=1_700_000_000+i/cadence_hz
        phase=2*math.pi*(1/0.65)*i/cadence_hz
        az=9.80665+(2.3*math.sin(phase) if walking else 0.02*math.sin(phase))
        # both raw sensor types, identical t allowed across types
        records.append((t,"GYRO",0.0,0.0,gyro_rate))
        records.append((t,"ACCEL",0.0,0.0,az))
    return records


class PdrCampaignTests(unittest.TestCase):
    def test_walking_estimates_steps_without_a_camera(self):
        poses,summary=imu_pdr.pdr(make_imu())
        self.assertGreater(summary["steps"],8)
        self.assertLess(summary["steps"],27)
        self.assertAlmostEqual(summary["path_m"],summary["steps"]*0.7)
        self.assertEqual(summary["sensor_counts"]["ACCEL"],1200)
        self.assertGreater(len(poses),1000)

    def test_stationary_does_not_accumulate_path(self):
        poses,summary=imu_pdr.pdr(make_imu(walking=False))
        self.assertEqual(summary["steps"],0)
        self.assertAlmostEqual(summary["path_m"],0)

    def test_yaw_rotation_turns_path_when_gyro_is_available(self):
        _,straight=imu_pdr.pdr(make_imu(gyro_rate=0))
        _,turn=imu_pdr.pdr(make_imu(gyro_rate=math.pi/12))
        self.assertGreater(turn["steps"],0)
        self.assertGreater(abs(turn["end_heading_deg"]),100)
        self.assertLess(turn["net_displacement_m"],straight["net_displacement_m"])

    def test_missing_gyro_rejected(self):
        with self.assertRaisesRegex(ValueError,"gyro"):
            imu_pdr.pdr([(1+i*0.1,"ACCEL",0,0,9.8) for i in range(50)])

    def test_real_input_schema_loader(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/"imu.csv"
            with path.open("w",newline="") as f:
                w=csv.writer(f);w.writerow(["t_ns","type","x","y","z"])
                for t,typ,x,y,z in make_imu(duration=2):
                    w.writerow((round(t*1e9),typ,x,y,z))
            poses,report=imu_pdr.pdr(imu_pdr.load_canonical(path))
            self.assertGreater(len(poses),100)
            self.assertGreaterEqual(report["steps"],1)


if __name__=="__main__":
    unittest.main()
