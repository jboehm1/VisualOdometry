import importlib.util
import math
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('noise_study', Path(__file__).parents[1]/'noise_study.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class NoiseStudyTest(unittest.TestCase):
    def test_iid_variance(self):
        self.assertAlmostEqual(m.mean_variance(100, 5), 0.25)

    def test_ar1_against_full_covariance(self):
        n, rho = 13, .95
        expected = sum(25*rho**abs(i-j) for i in range(n) for j in range(n))/n**2
        self.assertAlmostEqual(m.mean_variance(n, 5, rho), expected)

    def test_correlation_prevents_naive_sample_count(self):
        self.assertGreater(m.mean_variance(100, 5, .95), 25/100*20)

    def test_bias_translation_unobservable(self):
        for q in [(0, 0), (20, -3), (10, 30)]:
            self.assertEqual(m.predicted_fix(q, (2, 7), (5, -1)),
                             m.predicted_fix(q, (7, 4), (0, 2)))

    def test_no_measurements_or_nonfinite_rejected(self):
        for args in [(0, 5, 0), (10, math.nan, 0), (10, 5, 1), (10, 5, math.nan)]:
            with self.assertRaises(ValueError):
                m.mean_variance(*args)

    def test_duplicate_error_limit(self):
        # Perfectly repeated fixes approach a single observation, not n independent ones.
        self.assertAlmostEqual(m.mean_variance(100, 5, 1-1e-12), 25, places=7)

    def test_reproducible(self):
        self.assertEqual(m.study(trials=2), m.study(trials=2))


if __name__ == '__main__':
    unittest.main()
