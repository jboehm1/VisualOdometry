"""CI gates on the executed replay output; uses only Python standard library."""
import csv, math, statistics, sys
from pathlib import Path
for name in sys.argv[1:]:
    folder=Path(name)
    with (folder/'loops.csv').open() as f: laps=list(csv.DictReader(f))
    last={k:float(v) for k,v in laps[-1].items()}
    with (folder/'landmarks.csv').open() as f: points=list(csv.DictReader(f))
    with (folder/'trajectory.csv').open() as f: frames=list(csv.DictReader(f))
    initialized=[r for r in frames if float(r['metric_initialized'])>0]
    assert len(laps)>=3, 'must execute at least three uninterrupted laps'
    assert initialized, 'metric scale never initialized'
    speed_rmse=math.sqrt(statistics.mean((float(r['estimated_speed_m_s'])-float(r['truth_speed_m_s']))**2 for r in initialized))
    assert abs(last['distance_error_pct'])<5, last
    assert last['closure_error_m']<2, last
    assert last['position_rmse_m']<1.5, last
    assert speed_rmse<.08, speed_rmse
    assert len(points)>100, len(points)
    assert statistics.median(float(p['nearest_truth_surface_m']) for p in points)<.4
    print(f'{name}: PASS, distance error {last["distance_error_pct"]:+.3f}%, closure {last["closure_error_m"]:.3f}m, speed RMSE {speed_rmse:.4f}m/s')
