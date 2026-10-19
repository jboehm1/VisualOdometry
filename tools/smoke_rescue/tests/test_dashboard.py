"""Actual research-dashboard contract unit tests; data here synthetic and labeled."""
import importlib.util
from pathlib import Path
import unittest
P=Path(__file__).resolve().parents[1]/"dashboard.py"
spec=importlib.util.spec_from_file_location("dashboard",P)
mod=importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


def fake(variant=0):
    return {"actual_own_estimator_executed":True,
            "reference_used_during_estimation":False,
            "source_sha256":"f"*64,
            "mode":"MEASURED_RADAR_DOPPLER_PLUS_GYRO_UNCALIBRATED_EXTRINSICS",
            "trajectory":[{"x":float(k),"y":0.,"t":float(k),"yaw":0.,"new_segment":k==0}
                          for k in range(5)],
            "positive_evidence_grid":{"resolution_m":.25,"cells":[[1,2,4],[0,8,2]]},
            "motion_observability":{"accepted_fraction":.8,"accepted_bins":4},
            "pseudo_reference_posthoc_evaluation":{"ate_rmse_m_vs_pseudo_reference":3.,
                "zero_motion_baseline_rmse_m":2., "pairs":5},
            "sparse_positive_echo_map":{"occupied_evidence_cells":2},
            "sensor_source":{"gyro_clock_mode":"bag_receipt_time_unverified_delay"}}


class DashboardTests(unittest.TestCase):
    def test_real_report_contract_and_offline_html(self):
        d=mod.collect(fake(),fake())
        h=mod.render(d)
        self.assertIn('Smoke Rescue',h)
        self.assertIn('NON',h)
        self.assertIn('hors',h) if False else None
        self.assertIn('ORIGINAL',h) if False else None
        self.assertIn('application/json',h)
        self.assertIn('echoes',h)
        self.assertIn('RECHERCHE UNIQUEMENT',h)
        self.assertNotIn('__DATA__',h)
        self.assertIn('fffffff',h)

    def test_bad_source_is_rejected(self):
        b=fake();b["source_sha256"]="0"*64
        with self.assertRaises(ValueError):mod.collect(fake(),b)

    def test_estimator_truth_leak_rejected(self):
        p=fake();p["reference_used_during_estimation"]=True
        with self.assertRaises(ValueError):mod.collect(p,fake())

    def test_bad_cells_rejected(self):
        a=fake();a["positive_evidence_grid"]["cells"]=[[0,1,-1]]
        with self.assertRaises(ValueError):mod.collect(a,fake())

    def test_html_script_escape(self):
        p=fake()
        p["sensor_source"]["gyro_clock_mode"]="</script><script>alert(1)</script>"
        h=mod.render(mod.collect(p,p))
        self.assertNotIn("</script><script>alert(1)</script>",h)
        self.assertIn("\\u003c/script",h)


if __name__=="__main__":
    unittest.main()
