"""Dual-radar velocity fusion deterministic tests; no original smoke bag embedded."""
import importlib.util
from pathlib import Path
import unittest
import math
import sys
P=Path(__file__).resolve().parents[1]/"dual_radar.py"
sys.path.insert(0,str(P.parent))
spec=importlib.util.spec_from_file_location("dual_radar",P)
a=importlib.util.module_from_spec(spec)
spec.loader.exec_module(a)


def sensor(yaw,x,y):
    return {"root":"base_link","xyz_m":[x,y,.35],"yaw_rad":yaw,"frame":"synthetic","planar_only":True}


LEFT=sensor(math.pi/2,.1,.05)
RIGHT=sensor(-math.pi/2,.1,-.05)
IMU=sensor(0.,.1,-.01)


def echo(theta,vx,vy,omega,which):
    p=LEFT if which=="left" else RIGHT
    ux,uy=math.cos(theta),math.sin(theta)
    rx,ry=p["xyz_m"][:2]
    d=-(ux*vx+uy*vy+omega*(-ux*ry+uy*rx))
    da=theta-p["yaw_rad"]
    return 3*math.cos(da),3*math.sin(da),d


class DualRadarMathTests(unittest.TestCase):
    def test_transform_absolute_base_xy(self):
        x,y=a.sensor_point_in_base(LEFT,1,0)
        self.assertAlmostEqual(x,.1,places=7)
        self.assertAlmostEqual(y,1.05,places=7)

    def test_rotation_lever_arm_doppler_compensation(self):
        records=[]
        vx,vy,omega=.5,-.2,.35
        for which in ("left","right"):
            for angle in (-2,-1,-.2,.5,1.2,2):
                x,y,d=echo(angle,vx,vy,omega,which)
                result=a.radial_corrected_for_gyro(LEFT if which=="left" else RIGHT,x,y,d,omega)
                records.append(result)
        from doppler_odom import solve_2d
        value,reason=solve_2d(records)
        self.assertIsNone(reason)
        self.assertAlmostEqual(value["vx"],vx,places=6)
        self.assertAlmostEqual(value["vy"],vy,places=6)

    def test_fail_on_missing_tf(self):
        c={"ground_truth_accessed":False,
           "sensor_frames_missing_base_link_tf":["radar_right"],
           "base_link_paths":{"radar_left":LEFT,"radar_right":RIGHT,"imu":IMU},
           "named_sensor_frames":{"radar_left":"left","radar_right":"right","imu":"os1_imu"},
           "potentially_dynamic_extrinsics":[],
           "imu_uptime_to_unix":{"slope":1.,"intercept_s":10.,"reference_pose_used":False,
                                 "p95_minus_p05_arrival_delay_ms":10}}
        with self.assertRaises(ValueError):
            a.validate_calibration(c)
        c["sensor_frames_missing_base_link_tf"]=[]
        poses,clock=a.validate_calibration(c)
        self.assertAlmostEqual(poses["left"]["yaw_rad"],math.pi/2)
        c["ground_truth_accessed"]=True
        with self.assertRaises(ValueError):a.validate_calibration(c)

    def test_full_sensor_estimator_without_truth(self):
        imu=[(10+i*.01,0.) for i in range(1001)]
        radar=[]
        for i in range(91):
            t=10.1+i*.10
            for which in ("left","right"):
                for ang in (-1,-.35,.25,.8,1.3):
                    radar.append((t,which,*echo(ang,.3,.1,0.,which)))
        track,cells,quality=a.estimate_two_radar(sorted(radar),imu,
                                                 {"left":LEFT,"right":RIGHT},.5)
        self.assertGreater(quality["accepted_windows"],12)
        self.assertGreater(quality["accepted_fraction"],.9)
        self.assertGreater(track[-1]["x"],1.)
        self.assertGreater(track[-1]["y"],.3)
        self.assertGreater(len(cells),1)
        self.assertTrue(quality["positive_echo_map"]["unknown_is_not_free"])


if __name__=="__main__":
    unittest.main()
