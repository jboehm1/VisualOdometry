#!/usr/bin/env python3
"""Static DATA-menu + AndroidManifest gate for safe multi-branch integration.

This detects missing registrations and lost entry-points. It does NOT prove the
Android Activity launches, that onClick dispatch is reachable, or that the
returned pose is physically accurate. Run an APK UI smoke after any merge.
"""
import argparse
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID_NS="{http://schemas.android.com/apk/res/android}"
APP_ROOT=Path(__file__).resolve().parents[2]/"app"/"src"/"main"
MODES={
    "GPS_ROUTES":("GPS ROUTES — import GPX / follow / record",
                  "GpsRoutesActivity.class",".GpsRoutesActivity"),
    "ARCORE_REPLAY":("ARCORE REPLAY — evaluate saved video with same odometer",
                    "playbackPicker();",None),
    "FLP_AB":("GPS ↔ FUSED — Android location A/B (experimental)",
              "LocationComparisonActivity.class",".LocationComparisonActivity"),
    "PDR":("PDR · NO CAMERA — steps / sensors / GNSS course",
           "dev.jboehm.vectorvio.pdr.PdrActivity.class",".pdr.PdrActivity"),
    "COURSES":("COURSES — plan manuel hors ligne",
                "StorePlannerActivity.class",".StorePlannerActivity"),
}
CURRENT_BASELINE=("GPS_ROUTES","ARCORE_REPLAY","FLP_AB")
TARGET_COMBINED=tuple(MODES)

def inspect(main_source,manifest_text,required=CURRENT_BASELINE):
    missing=set(required)-set(MODES)
    if missing:raise ValueError("Unknown modes: "+", ".join(sorted(missing)))
    root=ET.fromstring(manifest_text)
    activities={item.attrib.get(ANDROID_NS+"name"):item.attrib.get(ANDROID_NS+"exported")
                for item in root.iter("activity")}
    menu_start=main_source.find("void dataMenu()")
    if menu_start<0:raise ValueError("DATA menu missing")
    menu=main_source[menu_start:menu_start+12_000]
    # This gate specifically checks the DATA menu, not incidental mentions in docs.
    result={}
    for key,(label,action,activity) in MODES.items():
        label_present=label in menu
        action_present=action in menu
        declared=activity is None or activities.get(activity)=="false"
        good=label_present and action_present and declared
        result[key]={"ok":good,"label_present":label_present,"action_present":action_present,
                     "activity_declared_nonexported":declared,"required":key in required}
    failure=[k for k in required if not result[k]["ok"]]
    return {"schema":"vector-vio-android-menu-contract/1","pass":not failure,
            "source":"STATIC_ONLY_NOT_UI_TESTED","required":list(required),"missing":failure,
            "modes":result,"limitations":"Strings and manifest registrations only; run combined APK UI smoke."}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--main",type=Path,default=APP_ROOT/"java"/"dev"/"jboehm"/"vectorvio"/"MainActivity.java")
    p.add_argument("--manifest",type=Path,default=APP_ROOT/"AndroidManifest.xml")
    p.add_argument("--require",nargs="*",choices=tuple(MODES),default=list(CURRENT_BASELINE))
    args=p.parse_args()
    data=inspect(args.main.read_text(encoding="utf-8"),
                 args.manifest.read_text(encoding="utf-8"),args.require)
    print(json.dumps(data,indent=2,ensure_ascii=False))
    raise SystemExit(0 if data["pass"] else 2)

if __name__=="__main__":main()
