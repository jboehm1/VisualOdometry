#!/usr/bin/env python3
import json,math,sys
a=json.load(open(sys.argv[1],encoding="utf8"))
assert a["steps"]==3,a
assert a["steps_without_heading"]==0,a
assert math.isclose(a["distance_m"],2.4,abs_tol=1e-4),a
assert math.isclose(a["north_m"],1.6,abs_tol=1e-4),a
assert math.isclose(a["east_m"],0.8,abs_tol=1e-4),a
assert a["rejected_rows"]==0,a
print("GNSS course quality + stride: PASS")
