"""Isolate EuRoC sensor inputs, then score native IMU poses after inference."""
from __future__ import annotations
import argparse
import csv
import hashlib
import json
import math
import re
from pathlib import Path
import shutil
import sys

ROOT = Path(__file__).resolve().parents[3]


def digest(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''): h.update(block)
    return h.hexdigest()


def index(path):
    result = []
    with path.open(newline='') as f:
        for row in csv.reader(f):
            if not row or row[0].startswith('#'): continue
            t = int(row[0])
            name = row[1].strip()
            if t <= 0 or (result and t <= result[-1][0]):
                raise ValueError('Camera timestamps must increase')
            if Path(name).name != name or '/' in name or '\\' in name or name.startswith('.'):
                raise ValueError('Unsafe image filename')
            result.append((t, name))
    return result


def ordinary(path):
    if path.is_symlink() or not path.is_file():
        raise ValueError('Expected ordinary sensor file: ' + str(path))
    return path


def stage(source, target, max_frames=1200):
    """No reference file is read. Target contains only cam0/cam1/imu0."""
    source, target = Path(source), Path(target)
    if max_frames < 3: raise ValueError('Need at least three frames')
    mav = source / 'mav0' if (source / 'mav0').is_dir() else source
    if any(p.is_symlink() for p in (source, mav, *[mav / n for n in ('cam0', 'cam1', 'imu0')])):
        raise ValueError('Symlink sensor directory forbidden')
    cameras = [index(ordinary(mav / name / 'data.csv'))[:max_frames] for name in ('cam0', 'cam1')]
    if len(cameras[0]) < 3 or [x[0] for x in cameras[0]] != [x[0] for x in cameras[1]]:
        raise ValueError('Stereo timestamps must match exactly')
    target.mkdir(parents=True, exist_ok=False)
    t0, t1 = cameras[0][0][0], cameras[0][-1][0]
    hashes = {}
    for name, frames in zip(('cam0', 'cam1'), cameras):
        out = target / 'mav0' / name
        (out / 'data').mkdir(parents=True)
        with (out / 'data.csv').open('w', newline='') as f:
            w = csv.writer(f); w.writerow(['#timestamp [ns]', 'filename'])
            for t, filename in frames:
                original = ordinary(mav / name / 'data' / filename)
                if original.parent.is_symlink(): raise ValueError('Symlink image directory forbidden')
                copied = out / 'data' / filename
                shutil.copyfile(original, copied)
                with copied.open('rb') as image:
                    if image.read(8) != b'\x89PNG\r\n\x1a\n': raise ValueError('Expected raw PNG image')
                hashes[str(copied.relative_to(target))] = digest(copied)
                w.writerow([t, filename])
        shutil.copyfile(ordinary(mav / name / 'sensor.yaml'), out / 'sensor.yaml')
    out = target / 'mav0' / 'imu0'; out.mkdir()
    imu_count = 0; previous = 0; first = last = None
    with ordinary(mav / 'imu0' / 'data.csv').open(newline='') as src, (out / 'data.csv').open('w', newline='') as dst:
        w = csv.writer(dst)
        w.writerow(['#timestamp [ns]', 'w_x', 'w_y', 'w_z', 'a_x', 'a_y', 'a_z'])
        for row in csv.reader(src):
            if not row or row[0].startswith('#'): continue
            t = int(row[0]); values = [float(x) for x in row[1:]]
            if t <= previous or len(values) != 6 or not all(map(math.isfinite, values)):
                raise ValueError('Invalid real IMU stream')
            previous = t
            if t0 - 500_000_000 <= t <= t1 + 500_000_000:
                w.writerow([t, *values]); imu_count += 1
                first = t if first is None else first; last = t
    if imu_count < 3 or first > t0 or last < t1:
        raise ValueError('IMU does not bracket selected camera interval')
    shutil.copyfile(ordinary(mav / 'imu0' / 'sensor.yaml'), out / 'sensor.yaml')
    for p in sorted(target.rglob('*')):
        if p.is_file() and str(p.relative_to(target)) not in hashes:
            hashes[str(p.relative_to(target))] = digest(p)
    return {'schema': 1, 'camera_frames_per_camera': len(cameras[0]), 'imu_samples': imu_count,
            't0_ns': t0, 't1_ns': t1, 'input_files_sha256': hashes,
            'sensor_tree_sha256': hashlib.sha256(json.dumps(hashes, sort_keys=True).encode()).hexdigest(),
            'truth_available_to_engine': False, 'window_selection': 'first camera frames; no reference-based selection'}


def score(reference, trajectory, staged, runtime):
    mode = runtime.get('engine_mode', 'vio')
    if mode not in ('vio', 'vo'): raise ValueError('Unknown engine mode receipt')
    if (runtime.get('exit_code') != 0 or runtime.get('truth_mount') is not False
            or runtime.get('network') != 'none'
            or not isinstance(runtime.get('binary_sha256'), str)
            or not re.fullmatch(r'[0-9a-f]{64}', runtime['binary_sha256'])):
        raise ValueError('Missing successful isolated native execution receipt')
    if staged.get('truth_available_to_engine') is not False:
        raise ValueError('Sensor-only staging receipt required')
    import numpy as np
    sys.path.insert(0, str(ROOT / 'tools' / 'benchmark'))
    import bench
    # EuRoC state CSV quaternion is w,x,y,z; the generic parser expects xyzw.
    times, positions, rotations = [], [], []
    with Path(reference).open(newline='') as f:
        for row in csv.reader(f):
            if not row or row[0].startswith('#'): continue
            t = int(row[0]); values = [float(x) for x in row[1:8]]
            if len(values) != 7 or not all(map(math.isfinite, values)):
                raise ValueError('Invalid reference pose')
            if staged['t0_ns'] <= t <= staged['t1_ns']:
                times.append(t * 1e-9); positions.append(values[:3])
                w, x, y, z = values[3:7]
                rotations.append(bench.quat_matrix([x, y, z, w]))
    bench.check_monotonic(times, 'reference')
    gt = (np.array(times), np.array(positions), np.array(rotations), np.ones(len(times), dtype=int))
    est = bench.parse_poses(trajectory, 'tum')
    pairs = bench.pair_by_time(gt[0], est[0], .02)
    if len(pairs) < 200: raise ValueError('Too few real native poses matched to reference')
    gi, ei = pairs[0]
    rotation = gt[2][gi] @ est[2][ei].T
    translation = gt[1][gi] - rotation @ est[1][ei]
    anchored = (est[0], est[1] @ rotation.T + translation,
                np.einsum('ij,njk->nik', rotation, est[2]), est[3])
    primary = bench.measure(gt, anchored, align='none', min_pairs=200)
    rigid = bench.measure(gt, est, align='se3', min_pairs=200)
    if primary['tracking_coverage_time_fraction'] < .5:
        raise ValueError('Insufficient scored time coverage')
    if not math.isfinite(primary['ate_rmse_m']): raise ValueError('Nonfinite ATE')
    return {'schema': 'vector-basalt-host-real/1', 'status': 'MEASURED',
            'engine': 'actual upstream Basalt C++ stereo '+('visual-inertial' if mode=='vio' else 'visual-only')+' estimator',
            'engine_mode': mode,
            'pose_frame': 'IMU/body', 'metric_scale': 'stereo calibrated; no reference scale fitted',
            'truth_available_to_engine': False,
            'reference_sha256': digest(reference), 'trajectory_sha256': digest(trajectory),
            'sensor_tree_sha256': staged['sensor_tree_sha256'], 'runtime': runtime,
            'first_reference_pose_gauge_diagnostic': primary,
            'posthoc_rigid_se3_diagnostic': rigid,
            'gauge_note': 'First matched reference pose fixes gauge AFTER inference; not an independent presurveyed alignment.',
            'limits': ['Not Android/ARCore performance', 'No power/thermal/online latency measurement',
                       'Selected V1_01 original reference has a published attitude-accuracy caveat; rotation and first-pose gauge errors are diagnostic',
                       'EuRoC MAV global-shutter stereo is not rolling-shutter smartphone mono',
                       'Native redistribution/transitive license review and JNI remain unqualified']}


def main():
    p = argparse.ArgumentParser(); sub = p.add_subparsers(dest='action', required=True)
    s = sub.add_parser('stage'); s.add_argument('--source', type=Path, required=True)
    s.add_argument('--target', type=Path, required=True); s.add_argument('--max-frames', type=int, default=1200)
    s.add_argument('--out', type=Path, required=True)
    e = sub.add_parser('score')
    for name in ('reference', 'trajectory', 'staged', 'runtime', 'out'): e.add_argument('--'+name, type=Path, required=True)
    a = p.parse_args()
    result = stage(a.source, a.target, a.max_frames) if a.action == 'stage' else score(
        a.reference, a.trajectory, json.loads(a.staged.read_text()), json.loads(a.runtime.read_text()))
    a.out.parent.mkdir(parents=True, exist_ok=True)
    a.out.write_text(json.dumps(result, indent=2, allow_nan=False)+'\n')


if __name__ == '__main__': main()
