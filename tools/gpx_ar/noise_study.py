"""Synthetic observability experiment, NOT a phone/GNSS/VIO benchmark."""
import argparse
import json
import math
import random
from pathlib import Path


def mean_variance(n, sigma, rho=0.0):
    if not isinstance(n, int) or n <= 0 or not math.isfinite(sigma) or sigma <= 0:
        raise ValueError('positive integer n and finite positive sigma required')
    if not math.isfinite(rho) or not -1 < rho < 1:
        raise ValueError('stationary AR(1) requires abs(rho) < 1')
    return sigma * sigma * (n + 2 * sum((n-k)*rho**k for k in range(1, n))) / (n*n)


def predicted_fix(q, translation, bias):
    return tuple(q[i] + translation[i] + bias[i] for i in range(2))


def study(seed=20261019, trials=1000):
    rng = random.Random(seed)
    rows = []
    sigma = 5.0  # metres PER AXIS, not Android's reported horizontal accuracy radius
    for name, rho, bias in [('iid', 0.0, 0.0), ('iid_bias_5m', 0.0, 5.0),
                            ('correlated', 0.95, 0.0), ('correlated_bias_5m', 0.95, 5.0)]:
        for n in [10, 100, 1000]:
            squared_errors = []
            coverage = 0
            naive_radius = math.sqrt(-2*math.log(0.05))*sigma/math.sqrt(n)
            for _ in range(trials):
                e = [rng.gauss(0, sigma), rng.gauss(0, sigma)]
                sums = [0.0, 0.0]
                for j in range(n):
                    if j:
                        e = [rho*v+rng.gauss(0, sigma*math.sqrt(1-rho*rho)) for v in e]
                    # Residual after subtracting an IDEAL known relative trajectory/yaw.
                    sums[0] += e[0] + bias
                    sums[1] += e[1]
                error2 = sum((v/n)**2 for v in sums)
                squared_errors.append(error2)
                coverage += error2 <= naive_radius**2
            variance = mean_variance(n, sigma, rho)
            rows.append(dict(scenario=name, n=n, rho=rho, bias_east_m=bias,
                effective_n=sigma*sigma/variance,
                predicted_horizontal_rmse_m=math.sqrt(2*variance+bias*bias),
                observed_horizontal_rmse_m=math.sqrt(sum(squared_errors)/trials),
                naive_iid_95pct_coverage=coverage/trials))
    return dict(kind='SYNTHETIC_NOT_DEVICE_VALIDATION', seed=seed, trials=trials,
                sigma_per_axis_m=sigma,
                assumptions=['Ideal relative trajectory and known yaw; no VIO drift',
                             'Stationary Gaussian AR(1), fixed east bias when specified',
                             'No Android accuracy calibration; no FLP performance inference'], rows=rows)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    args.out.write_text(json.dumps(study(), indent=2)+'\n', encoding='utf-8')
