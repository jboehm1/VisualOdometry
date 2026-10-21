"""Same binary/input ABBA ablation: stereo VO vs stereo VIO, NOT ARCore."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import statistics

ORDER=('vio','vo','vo','vio')


def compare(root):
    root=Path(root)
    scores=[json.loads((root/f'trial_{i}_{mode}'/'score.json').read_text())
            for i,mode in enumerate(ORDER)]
    first=scores[0]
    identities=('sensor_tree_sha256','reference_sha256','pose_frame','metric_scale')
    for mode,s in zip(ORDER,scores):
        if s['status']!='MEASURED' or s.get('engine_mode')!=mode:raise ValueError('Missing actual engine score')
        if s.get('truth_available_to_engine') is not False:raise ValueError('Reference leaked')
        if any(s[k]!=first[k] for k in identities):raise ValueError('Inputs/reference/frame differ')
        runtime=s['runtime'];baseline=first['runtime']
        for key in ('binary_sha256','engine_shared_libraries_sha256','host'):
            if not runtime.get(key) or runtime[key]!=baseline[key]:raise ValueError('Engine/hardware differs')
        if runtime.get('exit_code')!=0 or runtime.get('engine_mode')!=mode:
            raise ValueError('Unsuccessful or mismatched inference')
        if runtime.get('truth_mount') is not False or runtime.get('network')!='none':
            raise ValueError('Isolation receipt missing')
        command=runtime['command'];reference_command=baseline['command']
        idx=command.index('--use-imu')+1
        if command[idx]!=('1' if mode=='vio' else '0'):raise ValueError('Mode flag mismatch')
        if command[:idx]+command[idx+1:]!=reference_command[:idx]+reference_command[idx+1:]:
            raise ValueError('More than the IMU switch differs')
        for group in ('first_reference_pose_gauge_diagnostic','posthoc_rigid_se3_diagnostic'):
            if s[group]['estimated_scale_correction']!=1:raise ValueError('Scale fitted')
            if s[group]['time_tolerance_s']!=first[group]['time_tolerance_s']:
                raise ValueError('Clock matching changed')
            if s[group]['truth_duration_s']!=first[group]['truth_duration_s']:
                raise ValueError('Reference interval changed')
    result={}
    for mode in ('vio','vo'):
        group=[s for m,s in zip(ORDER,scores) if m==mode]
        values={'first_pose_gauge_ate_rmse_m':[s['first_reference_pose_gauge_diagnostic']['ate_rmse_m'] for s in group],
                'posthoc_rigid_ate_rmse_m':[s['posthoc_rigid_se3_diagnostic']['ate_rmse_m'] for s in group],
                'reference_interval_coverage':[s['posthoc_rigid_se3_diagnostic']['tracking_coverage_time_fraction'] for s in group],
                'wall_seconds':[s['runtime']['wall_seconds'] for s in group],
                'cpu_seconds':[s['runtime']['child_cpu_seconds'] for s in group],
                'peak_rss_kib':[s['runtime']['child_peak_rss_kib_linux'] for s in group]}
        if not all(math.isfinite(x) for samples in values.values() for x in samples):raise ValueError('Nonfinite result')
        result[mode]={'each':values,'median':{k:statistics.median(v) for k,v in values.items()}}
    return {'schema':'vector-basalt-vo-vio-ablation/1','status':'MEASURED_ABLATION',
            'order':list(ORDER),'results':result,'host':first['runtime']['host'],
            'sensor_tree_sha256':first['sensor_tree_sha256'],
            'engine_binary_sha256':first['runtime']['binary_sha256'],
            'reference_sha256':first['reference_sha256'],
            'report_files_sha256':{f'trial_{i}_{m}/score.json':hashlib.sha256((root/f'trial_{i}_{m}'/'score.json').read_bytes()).hexdigest()
                                   for i,m in enumerate(ORDER)},
            'ARCore_executed':False,'global_best_engine_selected':False,
            'limits':['One short V1_01 stereo window, original reference attitude caveat',
                      'Posthoc rigid and first-reference-pose gauge are diagnostics',
                      'Offline hosted throughput, no Android latency/battery/thermal evidence',
                      'This isolates IMU use within Basalt; not a cross-engine product comparison']}


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--root',type=Path,required=True)
    p.add_argument('--out',type=Path,required=True);args=p.parse_args()
    value=compare(args.root)
    args.out.write_text(json.dumps(value,indent=2,allow_nan=False)+'\n')
    print(json.dumps(value,indent=2))
