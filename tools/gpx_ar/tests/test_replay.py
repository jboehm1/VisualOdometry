import csv
import importlib.util
import io
import json
import math
from pathlib import Path
import tempfile
import unittest
import zipfile

spec=importlib.util.spec_from_file_location('gpx_replay',Path(__file__).parents[1]/'replay.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)


def fixture(path,clock=True,duplicate=False):
    R=6371008.8
    e2=6.6943799901413165e-3;w=1-e2*math.sin(math.radians(48))**2
    north_radius=6378137*(1-e2)/w**1.5;east_radius=6378137/math.sqrt(w)
    def geo(x,z):return 48+math.degrees(-z/north_radius),7+math.degrees(x/(east_radius*math.cos(math.radians(48))))
    route=[['route_revision','index','latitude_deg','longitude_deg','gpx_elevation_m','vertical_datum']]
    for i,(x,z) in enumerate([(0,0),(0,-30),(30,-30)]):route.append([1,i,*geo(x,z),'','UNKNOWN'])
    poses=[['ar_frame_ns','android_camera_elapsed_ns','receipt_elapsed_ns','tracking_segment','world_x','world_y','world_z','metric_x','metric_y','metric_z','route_revision']]
    fixes=[['route_revision','source','fix_elapsed_ns','receive_elapsed_ns','fix_wall_ms','latitude_deg','longitude_deg','horizontal_accuracy_m','altitude_m','vertical_accuracy_m','speed_mps','speed_accuracy_mps','bearing_deg','bearing_accuracy_deg','mock','queued_for_pairing']]
    for i in range(501):
        ns=1_000_000_000+i*100_000_000;t=i/10;x=max(0,t-30);z=-min(30,t)
        poses.append([ns,ns if clock else 0,ns+20_000_000,1,x+100,1.5,z+100,x,0,z,1])
        if i and i%10==0:
            fixes.append([1,'GPS_PROVIDER',ns,ns+30_000_000,0,*geo(x,z),2,'','','','','','','false','true'])
            if duplicate:fixes.append(fixes[-1].copy())
    with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED) as archive:
        for key,table in [('route',route),('poses',poses),('fixes',fixes)]:
            out=io.StringIO();csv.writer(out).writerows(table);archive.writestr('vector_vio_test_gpx_ar_'+key+'.csv',out.getvalue())
        archive.writestr('vector_vio_test_part001.mp4',b'not decoded by alignment replay')


class ReplayTests(unittest.TestCase):
    def test_same_java_engine_replays_metric_not_raw_world_and_marks_no_truth(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp=Path(tmp);fixture(tmp/'input.zip');summary=m.run(tmp/'input.zip',tmp/'out',origin='synthetic_fixture')
            self.assertEqual(summary['engine'],'Android Java ArRouteGuide');self.assertEqual(summary['ground_truth'],'NOT_PROVIDED')
            self.assertFalse(summary['absolute_accuracy_validated']);self.assertFalse(summary['video_reprocessed'])
            self.assertGreater(summary['state_counts'].get('CANDIDATE',0),0);self.assertEqual(summary['events']['P'],501)
            rows=list(csv.DictReader(io.StringIO((tmp/'out/replay.csv').read_text())));last=rows[-1]
            w=1-6.6943799901413165e-3*math.sin(math.radians(48))**2
            expected=(48+math.degrees(30/(6378137*(1-6.6943799901413165e-3)/w**1.5)),7+math.degrees(20/((6378137/math.sqrt(w))*math.cos(math.radians(48)))))
            self.assertLess(m.distance((float(last['latitude']),float(last['longitude'])),expected),.05)
            self.assertEqual(list((tmp/'out').glob('*.mp4')),[])

    def test_unverified_clock_never_becomes_a_precise_zero_error_result(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp=Path(tmp);fixture(tmp/'input.zip',clock=False);s=m.run(tmp/'input.zip',tmp/'out')
            self.assertEqual(s['state_counts'].get('CANDIDATE',0),0);self.assertGreater(s['state_counts'].get('CLOCK_MISMATCH',0),0)
            self.assertNotIn('reference_comparison',s)

    def test_duplicate_fixes_do_not_count_as_new_samples(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp=Path(tmp);fixture(tmp/'input.zip',duplicate=True);s=m.run(tmp/'input.zip',tmp/'out')
            rows=list(csv.DictReader(io.StringIO((tmp/'out/replay.csv').read_text())))
            self.assertLessEqual(max(int(r['samples']) for r in rows),50);self.assertEqual(s['events']['F'],100)

    def test_unsafe_archive_and_missing_fields_fail_explicitly(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp=Path(tmp);fixture(tmp/'input.zip')
            with zipfile.ZipFile(tmp/'input.zip','a') as archive:archive.writestr('../escape.txt','bad')
            with self.assertRaisesRegex(ValueError,'Unsafe ZIP'):m.run(tmp/'input.zip',tmp/'out')
            with self.assertRaises(ValueError):m.integer({},'receipt_elapsed_ns')

    def test_reference_comparison_is_absolute_and_declares_unverified_origin(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp=Path(tmp);p=tmp/'truth.csv';p.write_text('elapsed_ns,latitude,longitude\n1000000000,48,7\n')
            rows=[dict(camera_elapsed_ns='1000000000',latitude='48',longitude='7.0001')]
            s=m.compare_reference(rows,p,'user-provided surveyed marker; independently unverified')
            self.assertEqual(s['matched'],1);self.assertGreater(s['rmse_m'],7);self.assertFalse(s['independence_verified'])
            with self.assertRaises(ValueError):m.compare_reference(rows,p,'')


if __name__=='__main__':unittest.main()
