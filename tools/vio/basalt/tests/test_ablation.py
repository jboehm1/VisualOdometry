import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
def module(name):
    spec=importlib.util.spec_from_file_location(name,ROOT/(name+'.py'))
    value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value
ablation=module('ablation');runner=module('container_run')


class AblationTests(unittest.TestCase):
    def fixture(self, root):
        scores=[]
        for i,mode in enumerate(ablation.ORDER):
            metric={'estimated_scale_correction':1,'time_tolerance_s':.02,'truth_duration_s':60,
                    'ate_rmse_m':.1,'tracking_coverage_time_fraction':1}
            score={'status':'MEASURED','engine_mode':mode,'truth_available_to_engine':False,
                   'sensor_tree_sha256':'s','reference_sha256':'r','pose_frame':'body','metric_scale':'stereo',
                   'runtime':{'binary_sha256':'b','engine_shared_libraries_sha256':{'libbasalt.so':'l'},
                       'host':{'cpu_model':['fixture']},'exit_code':0,'engine_mode':mode,
                       'command':runner.command(mode),'truth_mount':False,'network':'none',
                       'wall_seconds':2,'child_cpu_seconds':3,'child_peak_rss_kib_linux':100},
                   'first_reference_pose_gauge_diagnostic':copy.deepcopy(metric),
                   'posthoc_rigid_se3_diagnostic':copy.deepcopy(metric)}
            folder=root/f'trial_{i}_{mode}';folder.mkdir()
            path=folder/'score.json';path.write_text(json.dumps(score));scores.append((path,score))
        return scores

    def test_only_the_imu_switch_changes_and_no_arcore_claim(self):
        a=runner.command('vio');b=runner.command('vo')
        self.assertEqual(1,sum(x!=y for x,y in zip(a,b)))
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);self.fixture(root)
            result=ablation.compare(root)
            self.assertFalse(result['ARCore_executed'])
            self.assertEqual(2,len(result['results']['vio']['each']['wall_seconds']))

    def test_mismatched_input_binary_reference_hardware_or_scale_rejected(self):
        for field in ('sensor_tree_sha256','reference_sha256','binary','hardware','scale','flag'):
            with self.subTest(field=field),tempfile.TemporaryDirectory() as td:
                root=Path(td);path,value=self.fixture(root)[1]
                if field in ('sensor_tree_sha256','reference_sha256'):value[field]='other'
                if field=='binary':value['runtime']['binary_sha256']='other'
                if field=='hardware':value['runtime']['host']={'cpu_model':['other']}
                if field=='scale':value['posthoc_rigid_se3_diagnostic']['estimated_scale_correction']=2
                if field=='flag':value['runtime']['command']=runner.command('vio')
                path.write_text(json.dumps(value))
                with self.assertRaises(ValueError):ablation.compare(root)


if __name__=='__main__':unittest.main()
