#!/usr/bin/env python3
"""Executable synthetic fixture gate, not a real-world accuracy claim."""
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
