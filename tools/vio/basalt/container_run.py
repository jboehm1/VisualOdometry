"""Runs inside a network-disabled, sensor-only container; no truth mount."""
import hashlib
import json
from pathlib import Path
import resource
import subprocess
import time

binary = Path('/engine/build/release/basalt_vio')
command = [str(binary), '--dataset-path', '/sensors', '--dataset-type', 'euroc',
           '--cam-calib', '/engine/data/euroc_eucm_calib.json',
           '--config-path', '/engine/data/euroc_config.json', '--show-gui', '0',
           '--num-threads', '2', '--use-imu', '1', '--use-double', '0',
           '--save-trajectory', 'tum', '--save-groundtruth', '0']
start = time.monotonic()
with Path('/out/native_stdout.log').open('w') as stdout, Path('/out/native_stderr.log').open('w') as stderr:
    result = subprocess.run(command, cwd='/out', stdout=stdout, stderr=stderr, timeout=900)
usage = resource.getrusage(resource.RUSAGE_CHILDREN)
report = {'command': command, 'exit_code': result.returncode,
          'wall_seconds': time.monotonic()-start,
          'child_cpu_seconds': usage.ru_utime+usage.ru_stime,
          'child_peak_rss_kib_linux': usage.ru_maxrss,
          'binary_sha256': hashlib.sha256(binary.read_bytes()).hexdigest(),
          'truth_mount': False, 'network': 'none', 'power_joules': None,
          'online_frame_latency_ms': None}
Path('/out/runtime.json').write_text(json.dumps(report, indent=2)+'\n')
if result.returncode: raise SystemExit(result.returncode)
if not Path('/out/trajectory.txt').is_file(): raise SystemExit('No actual native trajectory')
