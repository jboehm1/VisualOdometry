"""Compare exported ARCore/PDR resource traces; no trajectory truth or engine winner."""
import argparse
import csv
import io
import json
import math
from pathlib import Path
import statistics
import zipfile


def number(row, key):
    value = row.get(key)
    if value in (None, ''):
        return None
    result = float(value)
    if not math.isfinite(result):
        raise ValueError(f'Nonfinite {key}')
    return result


def distribution(values):
    values = sorted(v for v in values if v is not None)
    return None if not values else {'samples': len(values), 'min': values[0],
        'mean': statistics.fmean(values), 'median': statistics.median(values),
        'p95_observed_samples': values[max(0, math.ceil(.95*len(values))-1)], 'max': values[-1]}


def summarize(rows, metadata):
    if metadata.get('schema') != 'vector-session-resources/1':
        raise ValueError('Unsupported resources metadata')
    if not rows:
        raise ValueError('Empty resource trace')
    total_time = cpu_time = energy = energy_time = 0.
    previous = None
    for row in rows:
        if row.get('schema') != '1':
            raise ValueError('Unsupported resources CSV')
        t = number(row, 't_ns')
        dt = number(row, 'active_interval_s')
        if t is None or dt is None or dt < 0:
            raise ValueError('Missing or invalid interval')
        if previous:
            pt = number(previous, 't_ns')
            if t < pt:
                raise ValueError('Time moved backwards')
            same = previous['segment'] == row['segment']
            if same and not math.isclose(dt, (t-pt)/1e9, abs_tol=1e-6):
                raise ValueError('Interval inconsistent with monotonic clock')
            if not same and dt != 0:
                raise ValueError('Pause included in active duration')
            if same:
                dc = number(row, 'process_cpu_ms')-number(previous, 'process_cpu_ms')
                if dc < 0:
                    raise ValueError('Process CPU reset without new segment')
                cpu_time += dc
                p0 = number(previous, 'device_discharge_power_mw')
                p1 = number(row, 'device_discharge_power_mw')
                if dt > 0 and dt <= 3 and p0 is not None and p1 is not None and p0 >= 0 and p1 >= 0 \
                        and row.get('battery_plugged') == '0' and previous.get('battery_plugged') == '0':
                    energy += (p0+p1)/2*dt/3600
                    energy_time += dt
        elif dt != 0:
            raise ValueError('First sample must be a baseline')
        total_time += dt
        previous = row
    counters = metadata.get('counters', {})
    latencies = {}
    for name, bins in metadata.get('latency_histograms', {}).items():
        bounds = metadata['histogram_bounds_ms']
        if len(bins) != len(bounds) or any(n < 0 for n in bins):
            raise ValueError('Invalid histogram')
        count = sum(bins)
        if not count:
            continue
        def quantile(q):
            target = math.ceil(count*q)
            cumulative = 0
            for bound, n in zip(bounds, bins):
                cumulative += n
                if cumulative >= target:
                    return {'upper_bound_ms': bound, 'overflow': bound is None}
        latencies[name] = {'samples': count, 'p50': quantile(.5), 'p95': quantile(.95)}
    tracked = counters.get('tracked_camera_frames', 0)
    frames = counters.get('distinct_camera_frames', 0)
    fields = ('process_rss_kib', 'process_pss_kib', 'java_heap_used_bytes',
        'native_heap_allocated_bytes', 'process_threads', 'battery_temperature_c',
        'battery_current_ua', 'device_discharge_power_mw', 'thermal_status',
        'thermal_headroom', 'collector_wall_ms')
    result = {'schema': 'vector-resource-comparison/1', 'engine': metadata.get('engine'),
        'configuration': metadata.get('configuration'), 'segment_configurations': metadata.get('segment_configurations'),
        'build_provenance': metadata.get('build_provenance'), 'device': metadata.get('model'),
        'build_fingerprint': metadata.get('build_fingerprint'), 'app_version': metadata.get('app_version'),
        'arcore_package_version': metadata.get('arcore_package_version'),
        'collector_status': metadata.get('status'), 'collector_error': metadata.get('collector_error'),
        'active_duration_s': total_time, 'process_cpu_ms': cpu_time,
        'mean_cpu_core_percent': 100*cpu_time/(total_time*1000) if total_time else None,
        'device_discharge_energy_mwh_estimate': energy if energy_time else None,
        'device_discharge_energy_covered_s': energy_time,
        'device_discharge_energy_coverage_fraction': energy_time/total_time if total_time else None,
        'frame_tracking_fraction': tracked/frames if frames else None,
        'observed_rates_hz': {k: counters.get(k, 0)/total_time if total_time else None for k in
            ('accel_callbacks', 'gyro_callbacks', 'rotation_callbacks', 'distinct_camera_frames', 'pose_exports', 'view_draw_callbacks')},
        'counters': counters, 'resources': {k: distribution([number(r, k) for r in rows]) for k in fields},
        'latency_histogram_quantiles': latencies, 'unavailable': metadata.get('unavailable', {}),
        'warnings': ['CPU includes this app process and collector; external AR services are excluded.',
            'Battery power/energy is whole-device estimated terminal discharge, not app-attributed energy.',
            'Battery temperature is not CPU/GPU temperature; window GPU duration excludes SurfaceView GL.',
            'Tracking fraction counts observed unique camera frames, not tracking time or unseen missing frames.',
            'PSS/headroom can repeat cached probes: inspect age columns, not independent sample counts.',
            'Host call/window timings are not photon-to-pose latency. No metric accuracy or global winner follows.']}
    if metadata.get('status') != 'RECORDED':
        result['warnings'].append('Collector reports partial/error status; inspect export before comparison.')
    return result


def read_export(path):
    path = Path(path)
    if path.suffix.lower() != '.zip':
        raise ValueError('Use the complete exported session ZIP, not its trajectory-only CSV')
    with zipfile.ZipFile(path) as z:
        names = [i for i in z.infolist() if i.filename.endswith('resources.csv')]
        if len(names) != 1:
            raise ValueError('Expected exactly one resource trace; older APK exports have none')
        raw = names[0]
        meta_name = raw.filename[:-4]+'_metadata.json'
        if raw.file_size > 32*1024*1024 or z.getinfo(meta_name).file_size > 1024*1024:
            raise ValueError('Oversized resource export')
        rows = list(csv.DictReader(io.StringIO(z.read(raw).decode('utf-8'))))
        metadata=json.loads(z.read(meta_name))
        result=summarize(rows,metadata)
        if metadata.get('engine')=='BASALT_CUSTOM_FACTORY_EXPERIMENTAL':
            custom_name=str(Path(raw.filename).parent/'custom_metadata.json')
            if z.getinfo(custom_name).file_size>1024*1024:raise ValueError('Oversized custom metadata')
            custom=json.loads(z.read(custom_name))
            result['custom_run']={k:custom.get(k) for k in ('engine','qualification','verified_calibration','failed','status','frames_enqueued','pose_rows','drops','imu_pairs','imu_pairing_drops')}
            result['warnings'].append('Custom Basalt uses unverified factory calibration; resource statistics do not validate camera/IMU calibration or trajectory accuracy.')
            if custom.get('failed') is not False:
                result['warnings'].append('Custom acquisition failed or its completion status is missing; this is not a successful tracking trial.')
        return result


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('exports', nargs='+', type=Path)
    p.add_argument('--out', type=Path, required=True)
    args = p.parse_args()
    reports = [read_export(f) for f in args.exports]
    args.out.write_text(json.dumps({'sessions': reports, 'automatic_winner': None,
        'comparison_rule': 'Compare matched route, device, mounting, screen, temperature and engine config; accuracy needs independent reference.'}, indent=2, allow_nan=False)+'\n')
