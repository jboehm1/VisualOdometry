#!/usr/bin/env python3
"""Executable synthetic fixture gate, not a real-world accuracy claim."""
import csv
import subprocess
import tempfile
from pathlib import Path
import json
import math
import pathlib
import sys

p=pathlib.Path(sys.argv[1])
a=json.loads(p.read_text())
assert a["rows"] == 12, a
assert a["rejected_rows"] == 0, a
assert a["steps"] == 10, a
assert a["steps_without_heading"] == 0, a
assert math.isclose(a["distance_m"],7.4,abs_tol=1e-5), a
assert math.isclose(a["east_m"],7.4,abs_tol=1e-5), a
assert math.isclose(a["north_m"],0,abs_tol=1e-5), a
print("PDR synthetic fixture: PASS",a)

# This additional fixture checks that replay timestamps correspond to START
# on the phone and that missing heading creates an explicit break.
root=Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory() as d:
    output=Path(d)/"path.csv"
    replay=subprocess.run(["bash","tools/pdr/run.sh",
        "tools/pdr/fixtures/session-roundtrip.csv",str(output)],
        cwd=root,check=True,text=True,capture_output=True)
    metrics=json.loads(replay.stdout)
    assert metrics["steps"]==3,metrics
    assert metrics["steps_without_heading"]==1,metrics
    assert abs(metrics["distance_m"]-2.4)<1e-6,metrics
    with output.open(encoding="utf-8",newline="") as f:
        trajectory=list(csv.DictReader(f))
    assert trajectory[0]["t_ns"]=="1000000000",trajectory
    assert trajectory[0]["gap_before"]=="false",trajectory
    assert trajectory[-1]["gap_before"]=="true",trajectory
    assert len(trajectory)==3,trajectory
print("PDR START timestamp / heading gap replay: PASS")
