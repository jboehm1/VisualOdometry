"""Replay exported GPX AR CSVs with the exact Java estimator; optionally compare user-supplied truth."""
import argparse
import bisect
import collections
import csv
import hashlib
import heapq
import io
import json
import math
from pathlib import Path, PurePosixPath
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]
JAVA_SOURCES = [
    'app/src/main/java/dev/jboehm/vectorvio/GeoAlignment.java',
    'app/src/main/java/dev/jboehm/vectorvio/geo/PoseFixSynchronizer.java',
    'app/src/main/java/dev/jboehm/vectorvio/geo/Wgs84LocalFrame.java',
    *['app/src/main/java/dev/jboehm/vectorvio/gps/'+n+'.java' for n in ['Geo','GpsRoute','GpsNavigator','GpsFixRules','RouteMatcher']],
    'app/src/main/java/dev/jboehm/vectorvio/location/LocationSample.java',
    *['app/src/main/java/dev/jboehm/vectorvio/gpxar/'+n+'.java' for n in ['ArRouteGuide','RouteIndex','GpxArJournal']],
    'tools/gpx_ar/GuideReplay.java',
]
MAX_CSV = 128 * 1024 * 1024


def integer(row, key):
    try:
        n = int(row[key])
    except (KeyError, ValueError, TypeError) as exc:
        raise ValueError('Missing/invalid integer '+key) from exc
    if not 0 <= n <= 9_223_372_036_854_775_807:
        raise ValueError('Out-of-range integer '+key)
    return n


def number(row, key, required=False):
    value = row.get(key, '')
    if value == '' and not required:
        return 'NaN'
    try:
        n = float(value)
    except (ValueError, TypeError) as exc:
        raise ValueError('Missing/invalid number '+key) from exc
    if required and not math.isfinite(n):
        raise ValueError('Non-finite '+key)
    return str(n) if math.isfinite(n) else 'NaN'


def archive_members(archive):
    names = archive.namelist()
    if len(names) != len(set(names)) or len(names) > 10000:
        raise ValueError('Duplicate or excessive ZIP members')
    for name in names:
        path = PurePosixPath(name)
        if path.is_absolute() or '..' in path.parts or '\\' in name:
            raise ValueError('Unsafe ZIP path')
    routes = [n for n in names if n.endswith('_gpx_ar_route.csv')]
    if len(routes) != 1:
        raise ValueError('ZIP must contain exactly one GPX AR session')
    prefix = routes[0][:-len('_gpx_ar_route.csv')]
    selected = {k: prefix+'_gpx_ar_'+k+'.csv' for k in ['route','poses','fixes']}
    for name in selected.values():
        info = archive.getinfo(name)
        if info.file_size > MAX_CSV or (info.external_attr >> 16) & 0o170000 == 0o120000:
            raise ValueError('Oversized CSV or symlink')
    return selected


def rows(archive, name):
    # Only selected CSVs are decompressed. MP4 stays untouched in the ZIP.
    with archive.open(name) as raw:
        with io.TextIOWrapper(raw, encoding='utf-8-sig', newline='') as stream:
            reader = csv.DictReader(stream)
            if not reader.fieldnames or len(reader.fieldnames) != len(set(reader.fieldnames)):
                raise ValueError('Missing/duplicate CSV columns')
            for row in reader:
                if None in row:
                    raise ValueError('Malformed CSV row')
                yield row


def normalized_poses(archive, name, revision, revisions):
    previous = 0
    for row in rows(archive, name):
        if 'route_revision' not in row and len(revisions) > 1:
            raise ValueError('Legacy poses have no route_revision: cannot disambiguate multiple routes')
        if 'route_revision' in row and integer(row,'route_revision') != revision:
            continue
        receipt = integer(row, 'receipt_elapsed_ns')
        if receipt < previous:
            raise ValueError('Pose receipt times out of order')
        previous = receipt
        metric = 'metric_x' in row
        # Empty metric coordinates are NOT replaced by raw world coordinates.
        yield receipt, 0, ['P', str(integer(row,'android_camera_elapsed_ns')), str(receipt),
            str(integer(row,'tracking_segment')), number(row,'metric_x' if metric else 'world_x'),
            number(row,'metric_z' if metric else 'world_z')]


def normalized_fixes(archive, name, revision):
    previous = 0
    keys=['latitude_deg','longitude_deg','horizontal_accuracy_m','altitude_m','vertical_accuracy_m',
          'speed_mps','speed_accuracy_mps','bearing_deg','bearing_accuracy_deg']
    for row in rows(archive, name):
        if integer(row,'route_revision') != revision:
            continue
        receipt = integer(row,'receive_elapsed_ns')
        if receipt < previous:
            raise ValueError('Fix receipt times out of order')
        previous = receipt
        if row.get('mock') not in ['true','false'] or row.get('source') not in ['GPS_PROVIDER','GOOGLE_FUSED']:
            raise ValueError('Invalid mock/source')
        values=['F',str(integer(row,'fix_elapsed_ns')),str(receipt)]+[number(row,k) for k in keys]+[row['mock'],'0',row['source']]
        yield receipt, 1, values


def distance(a,b):
    lat1,lon1,lat2,lon2=map(math.radians,[a[0],a[1],b[0],b[1]])
    h=math.sin((lat2-lat1)/2)**2+math.cos(lat1)*math.cos(lat2)*math.sin((lon2-lon1)/2)**2
    return 12742017.6*math.asin(math.sqrt(max(0,min(1,h))))


def compare_reference(estimates, path, note):
    if not note:
        raise ValueError('--reference-note must describe the external truth source')
    truth=[]
    with Path(path).open(newline='') as f:
        for row in csv.DictReader(f):
            ns=integer(row,'elapsed_ns');lat=float(number(row,'latitude',True));lon=float(number(row,'longitude',True))
            if abs(lat)>90 or abs(lon)>180 or (truth and ns<=truth[-1][0]):
                raise ValueError('Invalid reference coordinate or non-increasing clock')
            truth.append((ns,lat,lon))
    times=[r[0] for r in truth];errors=[]
    for row in estimates:
        if not row['latitude'] or not row['longitude']:
            continue
        ns=int(row['camera_elapsed_ns']);i=bisect.bisect_left(times,ns)
        candidates=truth[max(0,i-1):i+1]
        if not candidates:
            continue
        ref=min(candidates,key=lambda p:abs(p[0]-ns))
        if abs(ref[0]-ns)>100_000_000:
            continue
        errors.append(distance((float(row['latitude']),float(row['longitude'])),ref[1:]))
    errors.sort()
    return dict(kind='ERROR_TO_USER_SUPPLIED_REFERENCE',reference_note=note,independence_verified=False,
                alignment='NONE_ABSOLUTE_GEOGRAPHIC',max_time_separation_ms=100,matched=len(errors),
                rmse_m=math.sqrt(sum(e*e for e in errors)/len(errors)) if errors else None,
                p50_m=errors[int((len(errors)-1)*.5)] if errors else None,
                p95_m=errors[int((len(errors)-1)*.95)] if errors else None,
                max_m=max(errors) if errors else None)


def run(source, output, revision=None, truth=None, reference_note=None, origin='unknown'):
    output=Path(output);output.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='vector-gpx-replay-') as tmp:
        tmp=Path(tmp)
        with zipfile.ZipFile(source) as archive:
            members=archive_members(archive);versions=collections.defaultdict(list)
            for row in rows(archive,members['route']):
                rev=integer(row,'route_revision');idx=integer(row,'index')
                if idx != len(versions[rev]) or idx>=50000:
                    raise ValueError('Route indices must be consecutive, max 50000')
                versions[rev].append([number(row,'latitude_deg',True),number(row,'longitude_deg',True),number(row,'gpx_elevation_m')])
            if revision is None:
                if len(versions)!=1:
                    raise ValueError('Specify --revision when several route revisions exist')
                revision=next(iter(versions))
            if revision not in versions or len(versions[revision])<2:
                raise ValueError('Selected route missing/too short')
            route_path=tmp/'route.tsv';route_path.write_text('\n'.join('\t'.join(p) for p in versions[revision])+'\n')
            counts=collections.Counter()
            with (tmp/'events.tsv').open('w') as events:
                for _,_,values in heapq.merge(normalized_poses(archive,members['poses'],revision,versions),normalized_fixes(archive,members['fixes'],revision)):
                    counts[values[0]]+=1;events.write('\t'.join(values)+'\n')
            if not counts['P']:
                raise ValueError('No pose events')
        classes=tmp/'classes';classes.mkdir()
        subprocess.run(['java','com.sun.tools.javac.Main','-d',str(classes)]+[str(ROOT/p) for p in JAVA_SOURCES],check=True,capture_output=True,text=True)
        subprocess.run(['java','-cp',str(classes),'dev.jboehm.vectorvio.gpxar.GuideReplay',str(route_path),str(tmp/'events.tsv'),str(tmp/'replay.csv')],check=True,capture_output=True,text=True)
        with (tmp/'replay.csv').open() as f:
            estimates=list(csv.DictReader(f))
        with Path(source).open('rb') as f:
            input_digest=hashlib.file_digest(f,'sha256').hexdigest()
        summary=dict(schema='vector-gpx-ar-replay/1',engine='Android Java ArRouteGuide',input_origin=origin,
                     input_sha256=input_digest,
                     engine_sources_sha256=hashlib.sha256(b''.join((p+'\n').encode()+(ROOT/p).read_bytes() for p in JAVA_SOURCES)).hexdigest(),
                     revision=revision,events=dict(counts),state_counts=dict(collections.Counter(r['state'] for r in estimates)),
                     output_rate='1 Hz diagnostics; all pose/fix events processed',
                     ground_truth='NOT_PROVIDED',video_reprocessed=False,arcore_tracking_reexecuted=False,
                     absolute_accuracy_validated=False)
        if truth:
            summary['ground_truth']='USER_SUPPLIED_NOT_INDEPENDENTLY_VERIFIED';summary['reference_comparison']=compare_reference(estimates,truth,reference_note)
        (output/'replay.csv').write_bytes((tmp/'replay.csv').read_bytes())
        (output/'summary.json').write_text(json.dumps(summary,indent=2,allow_nan=False)+'\n')
    return summary


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('zip',type=Path);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--revision',type=int);p.add_argument('--truth',type=Path);p.add_argument('--reference-note')
    p.add_argument('--input-origin',choices=['unknown','real_capture','synthetic_fixture'],default='unknown')
    a=p.parse_args()
    try:
        print(json.dumps(run(a.zip,a.out,a.revision,a.truth,a.reference_note,a.input_origin),indent=2))
    except (ValueError,KeyError,OSError,zipfile.BadZipFile,subprocess.CalledProcessError) as e:
        detail=e.stderr[-4000:] if isinstance(e,subprocess.CalledProcessError) and e.stderr else ''
        p.exit(2,'GPX replay failed: '+str(e)+'\n'+detail)


if __name__=='__main__':
    main()
