#!/usr/bin/env python3
"""Offline GNSS-vs-FLP diagnostic. No independent truth unless explicitly supplied."""
import argparse
import csv
import io
import json
import math
import statistics
import zipfile
from pathlib import Path

FILES={"fixes.csv","pairs.csv","metadata.json"}
SOURCES=("GPS_PROVIDER","GOOGLE_FUSED")
MAX_BYTES=30*1024*1024
MAX_ROWS=250_000

def _rows(text,required):
    reader=csv.DictReader(io.StringIO(text))
    if not required<=set(reader.fieldnames or []):
        raise ValueError("Missing CSV columns: "+str(sorted(required-set(reader.fieldnames or []))))
    result=[]
    for row in reader:
        if len(result)>=MAX_ROWS or None in row:raise ValueError("Excessive/malformed rows")
        result.append(row)
    return result

def _number(value,label):
    if value is None or value=="":raise ValueError("Missing "+label)
    number=float(value)
    if not math.isfinite(number):raise ValueError("Nonfinite "+label)
    return number

def _percentile(values,fraction):
    if not values:return None
    sorted_values=sorted(values)
    return round(sorted_values[min(len(values)-1,math.ceil(fraction*(len(values)-1)))],3)

def _distance(a,b,c,d):
    rad=math.pi/180
    lat_delta=(c-a)*rad;lon_delta=(d-b)*rad
    term=math.sin(lat_delta/2)**2+math.cos(a*rad)*math.cos(c*rad)*math.sin(lon_delta/2)**2
    return 12_742_017.6*math.asin(math.sqrt(max(0,min(1,term))))

def read_archive(path):
    if Path(path).stat().st_size>2*MAX_BYTES:raise ValueError("Oversized archive")
    with zipfile.ZipFile(path) as z:
        infos=z.infolist()
        if len(infos)!=3 or {f.filename for f in infos}!=FILES:
            raise ValueError("Expected exactly 3 whitelisted files")
        if any(f.file_size>MAX_BYTES or f.is_dir() for f in infos):
            raise ValueError("Oversized or invalid ZIP entry")
        metadata=json.loads(z.read("metadata.json"))
        raw=z.read("fixes.csv").decode("utf-8-sig")
        pairs=z.read("pairs.csv").decode("utf-8-sig")
    if metadata.get("schema")!="vector-location-ab/1":raise ValueError("Unsupported archive schema")
    a=_rows(raw,{"source","fix_elapsed_ns","receive_elapsed_ns","latitude_deg",
                 "longitude_deg","horizontal_accuracy_m","validity"})
    b=_rows(pairs,{"gps_time_ns","fused_time_ns","abs_delta_ms","disagreement_m"})
    return metadata,a,b

def analyze(session_zip,static_control=None,reference_note=None):
    metadata,fixes,pairs=read_archive(session_zip)
    if (static_control is None)!=(reference_note is None):
        raise ValueError("Control and provenance must both be supplied")
    if reference_note is not None and not reference_note.strip():
        raise ValueError("Control provenance required")
    if static_control is not None:
        lat,lon=static_control
        if not(-90<=lat<=90 and -180<=lon<=180):raise ValueError("Invalid control coordinates")
    if any(r["source"] not in SOURCES for r in fixes):raise ValueError("Unknown source label")
    by_source={}
    for source in SOURCES:
        rows=[r for r in fixes if r["source"]==source]
        valid=[]
        for row in rows:
            timestamp=int(row["fix_elapsed_ns"]);received=int(row["receive_elapsed_ns"])
            if timestamp<=0 or received<=0:raise ValueError("Invalid monotonic timestamp")
            state=row["validity"]
            if state not in {"OK","INVALID","OLD_OR_FUTURE","NO_ACCURACY","IMPRECISE","MOCK"}:
                raise ValueError("Unknown fix state")
            if state=="OK":
                lat=_number(row["latitude_deg"],"lat")
                lon=_number(row["longitude_deg"],"lon")
                accuracy=_number(row["horizontal_accuracy_m"],"accuracy")
                if not(-90<=lat<=90 and -180<=lon<=180 and accuracy>0):
                    raise ValueError("Invalid coordinates")
                valid.append((timestamp,received,lat,lon,accuracy))
        valid.sort(key=lambda x:x[0])
        intervals=[(b[0]-a[0])/1e9 for a,b in zip(valid,valid[1:]) if b[0]>a[0]]
        ages=[(x[1]-x[0])/1e6 for x in valid]
        item={
            "observations":len(rows),"eligible_fixes":len(valid),
            "noneligible_fixes":len(rows)-len(valid),
            "median_reported_accuracy_m":_percentile([x[4] for x in valid],.5),
            "median_callback_age_ms":_percentile(ages,.5),
            "p95_callback_age_ms":_percentile(ages,.95),
            "median_fix_interval_s":_percentile(intervals,.5),
            "max_fix_interval_s":round(max(intervals),3) if intervals else None,
            "intervals_over_3s":sum(x>3 for x in intervals)}
        if static_control is not None:
            errors=[_distance(x[2],x[3],*static_control) for x in valid]
            item["user_asserted_static_control_rmse_m"]=round(
                math.sqrt(sum(e*e for e in errors)/len(errors)),3) if errors else None
            item["user_asserted_static_control_p95_m"]=_percentile(errors,.95)
        by_source[source]=item
    distances=[];deltas=[]
    for row in pairs:
        disagreement=_number(row["disagreement_m"],"disagreement")
        delta=_number(row["abs_delta_ms"],"pair timestamp difference")
        if disagreement<0 or delta<0 or delta>1500.00001:
            raise ValueError("Malformed pair")
        distances.append(disagreement);deltas.append(delta)
    if metadata.get("raw_fix_count")!=len(fixes) or metadata.get("paired_count")!=len(pairs):
        raise ValueError("CSV/metadata counts disagree; possible truncated session")
    return {
        "schema":"vector-location-ab-analysis/1",
        "status":"COMPARATIVE_DIAGNOSTIC_ONLY" if static_control is None else
                 "USER_ASSERTED_STATIC_CONTROL_NOT_VERIFIED",
        "sources":by_source,
        "paired_count":len(pairs),
        "mean_pair_disagreement_m":round(statistics.mean(distances),3) if distances else None,
        "p95_pair_disagreement_m":_percentile(distances,.95),
        "median_pair_time_delta_ms":_percentile(deltas,.5),
        "ground_truth":"NOT_AVAILABLE" if static_control is None else "USER_ASSERTED_STATIC_REFERENCE",
        "reference_note":reference_note,
        "limitations":"FLP and GPS can share GNSS/common bias. Their disagreement is NOT absolute position accuracy. Static control is user-asserted and cannot validate moving trajectories, PDR or VIO."
    }

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("session_zip",type=Path)
    parser.add_argument("--static-control",nargs=2,type=float,metavar=("LAT","LON"))
    parser.add_argument("--reference-note")
    parser.add_argument("--out",type=Path)
    args=parser.parse_args()
    result=analyze(args.session_zip,args.static_control,args.reference_note)
    body=json.dumps(result,indent=2,ensure_ascii=False,sort_keys=True,allow_nan=False)+"\n"
    if args.out:
        args.out.parent.mkdir(parents=True,exist_ok=True)
        args.out.write_text(body,encoding="utf-8")
    print(body,end="")

if __name__=="__main__":main()
