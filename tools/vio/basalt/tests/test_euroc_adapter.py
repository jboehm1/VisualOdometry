"""Synthetic parser/isolation regressions, not native-estimator accuracy."""
import csv
import importlib.util
import json
import math
from pathlib import Path
import tempfile
import unittest

MODULE = Path(__file__).resolve().parents[1] / 'euroc_adapter.py'
spec = importlib.util.spec_from_file_location('euroc_adapter', MODULE)
adapter = importlib.util.module_from_spec(spec); spec.loader.exec_module(adapter)


class AdapterTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def fixture(self):
        source = self.root / 'source'; mav = source / 'mav0'
        for camera in ('cam0', 'cam1'):
            d = mav / camera / 'data'; d.mkdir(parents=True)
            (d.parent / 'sensor.yaml').write_text('sensor_type: camera\n')
            with (d.parent / 'data.csv').open('w') as f:
                f.write('#timestamp,filename\n')
                for i in range(5):
                    t = 1_000_000_000+i*50_000_000; name = str(t)+'.png'
                    (d/name).write_bytes(b'\x89PNG\r\n\x1a\nFIXTURE_ONLY')
                    f.write(f'{t},{name}\n')
        d = mav / 'imu0'; d.mkdir()
        (d/'sensor.yaml').write_text('sensor_type: imu\n')
        (d/'data.csv').write_text('#timestamp,wx,wy,wz,ax,ay,az\n'+''.join(
            f'{900_000_000+i*5_000_000},0,0,0,0,0,9.81\n' for i in range(100)))
        (mav/'state_groundtruth_estimate0').mkdir()
        (mav/'state_groundtruth_estimate0/data.csv').write_text('POISON: stage must never parse this\n')
        return source

    def test_sensor_copy_is_bounded_and_reference_is_not_read_or_mounted(self):
        source=self.fixture(); target=self.root/'sensors'
        r=adapter.stage(source,target,3)
        self.assertEqual(3,r['camera_frames_per_camera'])
        self.assertEqual({'cam0','cam1','imu0'}, {p.name for p in (target/'mav0').iterdir()})
        self.assertFalse(r['truth_available_to_engine'])
        self.assertEqual(64,len(r['sensor_tree_sha256']))
        self.assertEqual(3,len(list((target/'mav0/cam0/data').glob('*.png'))))
        self.assertNotIn('groundtruth',json.dumps(r))
        with self.assertRaises(FileExistsError): adapter.stage(source,target,3)

    def test_stereo_mismatch_and_unsafe_filename_rejected(self):
        source=self.fixture(); p=source/'mav0/cam1/data.csv'
        p.write_text(p.read_text().replace('1000000000,','1000000001,'))
        with self.assertRaisesRegex(ValueError,'Stereo'): adapter.stage(source,self.root/'bad',3)
        p=source/'mav0/cam0/data.csv';p.write_text('1000000000,../secret.png\n')
        with self.assertRaisesRegex(ValueError,'Unsafe'): adapter.stage(source,self.root/'unsafe',3)

    def test_symlink_and_missing_imu_bracket_rejected(self):
        source=self.fixture(); image=source/'mav0/cam0/data/1000000000.png'
        image.unlink();image.symlink_to(source/'mav0/imu0/data.csv')
        with self.assertRaisesRegex(ValueError,'ordinary'): adapter.stage(source,self.root/'bad',3)
        image.unlink();image.write_bytes(b'\x89PNG\r\n\x1a\n')
        (source/'mav0/imu0/data.csv').write_text('1000000000,0,0,0,0,0,9.81\n')
        with self.assertRaisesRegex(ValueError,'bracket'): adapter.stage(source,self.root/'bad2',3)

    def scoring_fixture(self,scale=1):
        gt=self.root/'gt.csv';est=self.root/'est.tum'
        with gt.open('w') as g,est.open('w') as e:
            for i in range(301):
                t=1_000_000_000+i*50_000_000;x=i*.1
                # Reference rotation is +90 deg, wxyz; local estimate is identity.
                g.write(f'{t},3,{4+x},5,{math.sqrt(.5)},0,0,{math.sqrt(.5)}\n')
                e.write(f'{t*1e-9:.9f} {x*scale} 0 0 0 0 0 1\n')
        staged={'t0_ns':1_000_000_000,'t1_ns':16_000_000_000,
                'sensor_tree_sha256':'a'*64,'truth_available_to_engine':False}
        runtime={'exit_code':0,'truth_mount':False,'network':'none','binary_sha256':'b'*64}
        return gt,est,staged,runtime

    def test_wxyz_reference_conversion_and_first_pose_gauge(self):
        r=adapter.score(*self.scoring_fixture())
        self.assertLess(r['first_reference_pose_gauge_diagnostic']['ate_rmse_m'],1e-8)
        self.assertLess(r['first_reference_pose_gauge_diagnostic']['rpe_rotation_rmse_deg'],1e-5)
        self.assertEqual(1,r['posthoc_rigid_se3_diagnostic']['estimated_scale_correction'])

    def test_wrong_metric_scale_cannot_be_fitted_away(self):
        r=adapter.score(*self.scoring_fixture(scale=2))
        self.assertGreater(r['first_reference_pose_gauge_diagnostic']['ate_rmse_m'],10)
        self.assertGreater(r['posthoc_rigid_se3_diagnostic']['ate_rmse_m'],5)
        self.assertEqual(1,r['posthoc_rigid_se3_diagnostic']['estimated_scale_correction'])

    def test_missing_isolation_or_failed_engine_receipt_rejected(self):
        gt,est,staged,runtime=self.scoring_fixture()
        runtime['exit_code']=1
        with self.assertRaisesRegex(ValueError,'receipt'): adapter.score(gt,est,staged,runtime)
        runtime['exit_code']=0;runtime['truth_mount']=True
        with self.assertRaisesRegex(ValueError,'receipt'): adapter.score(gt,est,staged,runtime)


if __name__=='__main__': unittest.main()
