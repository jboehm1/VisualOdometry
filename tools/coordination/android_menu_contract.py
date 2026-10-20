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

def inspect(main_source,manifest_text,required=CURRENT_BASELINE,catalog_source="",settings_source=None):
    # Accept both the legacy flat DATA list and the categorized semantic action
    # catalog. The latter is only trusted if MainActivity actually dispatches
    # its stable enum IDs. A disconnected catalog alone cannot pass the gate.
    missing=set(required)-set(MODES)
    if missing:raise ValueError("Unknown modes: "+", ".join(sorted(missing)))
    root=ET.fromstring(manifest_text)
    activities={item.attrib.get(ANDROID_NS+"name"):item.attrib.get(ANDROID_NS+"exported")
                for item in root.iter("activity")}
    menu_start=main_source.find("void dataMenu()")
    if menu_start<0:raise ValueError("DATA menu missing")
    menu=main_source[menu_start:menu_start+12_000]
    # The old DATA menu and its semantic handler stay the only permitted
    # dispatch scope, not incidental mentions in documentation elsewhere.
    categorized="SettingsCatalog.items(category)" in menu and "void performSettingsAction(" in menu
    if settings_source is None:
        settings_file=APP_ROOT/"java"/"dev"/"jboehm"/"vectorvio"/"SettingsActivity.java"
        settings_source=settings_file.read_text(encoding="utf-8") if settings_file.exists() else ""
    # v0.12 replaces the old double-dialog with a full-screen Activity. Check
    # BOTH halves of the semantic result route, not merely a disconnected catalog.
    page_registered=activities.get(".SettingsActivity")=="false"
    page_opened=bool(re.search(r"void dataMenu\\(\\).*?startActivityForResult\\(new Intent\\(this,SettingsActivity\\.class\\),SETTINGS_REQUEST\\)",menu,re.S))
    page_returns=("SettingsCatalog.items(selected)" in settings_source
                  and "SettingsCatalog.items(section)" in settings_source
                  and "setResult(RESULT_OK" in settings_source
                  and "putExtra(EXTRA_ACTION,a.name())" in settings_source)
    action_handled=("SettingsActivity.EXTRA_ACTION" in main_source
                    and "SettingsCatalog.Action.valueOf(name)" in main_source
                    and "performSettingsAction(" in main_source)
    full_screen=page_registered and page_opened and page_returns and action_handled
    catalog_actions={"GPS_ROUTES":"Action.GPS_ROUTES", "ARCORE_REPLAY":"Action.ARCORE_REPLAY",
                     "FLP_AB":"Action.GPS_COMPARISON",
                     "PDR":"Action.PDR", "COURSES":"Action.COURSES"}
    result={}
    for key,(label,action,activity) in MODES.items():
        in_catalog=((categorized or full_screen) and label in catalog_source
                    and catalog_actions.get(key,"") in catalog_source)
        label_present=label in menu or in_catalog
        action_present=action in menu and (not in_catalog or
             ("case "+catalog_actions[key].split(".")[-1]+":" in menu))
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
    p.add_argument("--catalog",type=Path,
        default=APP_ROOT/"java"/"dev"/"jboehm"/"vectorvio"/"SettingsCatalog.java")
    args=p.parse_args()
    data=inspect(args.main.read_text(encoding="utf-8"),
                 args.manifest.read_text(encoding="utf-8"),args.require,
                 args.catalog.read_text(encoding="utf-8") if args.catalog.exists() else "")
    print(json.dumps(data,indent=2,ensure_ascii=False))
    raise SystemExit(0 if data["pass"] else 2)

if __name__=="__main__":main()
