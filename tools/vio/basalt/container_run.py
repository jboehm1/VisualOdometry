"""Runs inside a network-disabled, sensor-only container; no truth mount."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import resource
import subprocess
import time

binary = Path('/engine/build/release/basalt_vio')


def command(mode):
    if mode not in ('vio', 'vo'): raise ValueError('Unknown estimator mode')
    return [str(binary), '--dataset-path', '/sensors', '--dataset-type', 'euroc',
           '--cam-calib', '/engine/data/euroc_eucm_calib.json',
           '--config-path', '/engine/data/euroc_config.json', '--show-gui', '0',
           '--num-threads', '2', '--use-imu', '1' if mode == 'vio' else '0', '--use-double', '0',
           '--save-trajectory', 'tum', '--save-groundtruth', '0']


def host():
    try:
        models=sorted({line.split(':',1)[1].strip() for line in Path('/proc/cpuinfo').read_text().splitlines()
                       if line.startswith('model name')})
    except OSError:
        models=[]
    return {'platform':platform.platform(), 'machine':platform.machine(),
            'cpu_model':models, 'logical_cpus_visible':os.cpu_count()}


def run(mode):
    argv=command(mode);start=time.monotonic();timed_out=False
    with Path('/out/native_stdout.log').open('w') as stdout, Path('/out/native_stderr.log').open('w') as stderr:
        try:
            result=subprocess.run(argv,cwd='/out',stdout=stdout,stderr=stderr,timeout=900)
            code=result.returncode
        except subprocess.TimeoutExpired:
            code=124;timed_out=True
    usage=resource.getrusage(resource.RUSAGE_CHILDREN)
    report={'command':argv, 'engine_mode':mode, 'exit_code':code, 'timed_out':timed_out,
            'wall_seconds':time.monotonic()-start, 'host':host(),
            'child_cpu_seconds':usage.ru_utime+usage.ru_stime,
            'child_peak_rss_kib_linux':usage.ru_maxrss,
            'binary_sha256':hashlib.sha256(binary.read_bytes()).hexdigest(),
            'engine_shared_libraries_sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest()
                for p in binary.parent.glob('libbasalt*.so*')},
            'truth_mount':False, 'network':'none', 'power_joules':None,
            'online_frame_latency_ms':None}
    Path('/out/runtime.json').write_text(json.dumps(report,indent=2)+'\n')
    if code: raise SystemExit(code)
    if not Path('/out/trajectory.txt').is_file(): raise SystemExit('No actual native trajectory')


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--mode',choices=('vio','vo'),default='vio')
    run(parser.parse_args().mode)
