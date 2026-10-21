import copy
import unittest
from report import summarize,read_export


class ReportTest(unittest.TestCase):
    def setUp(self):
        self.meta={'schema':'vector-session-resources/1','engine':'FIXTURE', 'status':'RECORDED',
            'counters':{},'latency_histograms':{'test':[0,2,1]},'histogram_bounds_ms':[1,5,None]}
        self.rows=[{'schema':'1','t_ns':str(t*1_000_000_000),'segment':s,
            'active_interval_s':str(dt),'process_cpu_ms':str(cpu),
            'battery_plugged':plug,'device_discharge_power_mw':'2000'}
            for t,s,dt,cpu,plug in [(1,'1',0,0,'0'),(2,'1',1,1000,'0'),(12,'2',0,8000,'0'),(13,'2',1,8500,'0')]]

    def test_pause_excluded_and_one_core_not_whole_cpu(self):
        d=summarize(self.rows,self.meta)
        self.assertEqual(2,d['active_duration_s'])
        self.assertEqual(1500,d['process_cpu_ms'])
        self.assertEqual(75,d['mean_cpu_core_percent'])
        self.assertAlmostEqual(4000/3600,d['device_discharge_energy_mwh_estimate'])
        self.assertIsNone(d['resources']['process_pss_kib'])
        self.assertIsNone(d['frame_tracking_fraction'])
        self.assertTrue(d['latency_histogram_quantiles']['test']['p95']['overflow'])

    def test_charging_and_missing_power_are_not_zero_consumption(self):
        for r in self.rows:r['battery_plugged']='1'
        self.assertIsNone(summarize(self.rows,self.meta)['device_discharge_energy_mwh_estimate'])
        for r in self.rows:r['battery_plugged']='0';r['device_discharge_power_mw']=''
        self.assertIsNone(summarize(self.rows,self.meta)['device_discharge_energy_mwh_estimate'])

    def test_nonfinite_or_inconsistent_clock_and_hidden_pause_fail(self):
        for key,value in [('active_interval_s','nan'),('t_ns','0'),('active_interval_s','4')]:
            rows=copy.deepcopy(self.rows);rows[1][key]=value
            with self.assertRaises(ValueError):summarize(rows,self.meta)
        rows=copy.deepcopy(self.rows);rows[2]['active_interval_s']='10'
        with self.assertRaises(ValueError):summarize(rows,self.meta)

    def test_partial_collection_cannot_look_complete(self):
        self.meta['status']='PARTIAL_IO_ERROR'
        self.assertIn('partial/error',summarize(self.rows,self.meta)['warnings'][-1])

    def test_custom_failed_run_keeps_qualification_and_failure_visible(self):
        import tempfile,csv,io,json,zipfile
        from pathlib import Path
        self.meta['engine']='BASALT_CUSTOM_FACTORY_EXPERIMENTAL'
        raw=io.StringIO();writer=csv.DictWriter(raw,fieldnames=self.rows[0]);writer.writeheader();writer.writerows(self.rows)
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/'custom.zip'
            with zipfile.ZipFile(path,'w') as z:
                z.writestr('session_resources.csv',raw.getvalue());z.writestr('session_resources_metadata.json',json.dumps(self.meta))
                z.writestr('custom_metadata.json',json.dumps({'verified_calibration':False,'failed':True,'qualification':'EXPERIMENTAL','status':'STOPPED: IMU gap'}))
            result=read_export(path)
            self.assertFalse(result['custom_run']['verified_calibration'])
            self.assertTrue(result['custom_run']['failed'])
            self.assertIn('not a successful tracking trial',result['warnings'][-1])


if __name__=='__main__':unittest.main()
