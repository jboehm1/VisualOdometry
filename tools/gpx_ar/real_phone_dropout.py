#!/usr/bin/env python3
"""Evaluate the ACTUAL Java GPX estimator on published real phone ARCore POSES.

Input: ADVIO-09 separately recorded Google Pixel ARCore pose and reference pose.
No Android/ARCore inference is executed here. The ADVIO archive has no measured
outdoor GPS: fixes are SIMULATED from reference positions ONLY BEFORE dropout.
The complete reference remains scorer-only after the dropout; the route proxy
also comes from reference and is NOT an independent public GPX. The estimator
does not snap its projected pose to GPX route.

Avoid any claim of precise, drift-free, survey-ready or S21-specific results.
"""
import argparse
import csv
import hashlib
import importlib.util
import io
import json
import math
from pathlib import Path
import random
import statistics
import sys
import zipfile

REPLAY_FILE=Path(__file__).with_name("replay.py")
spec=importlib.util.spec_from_file_location("gpx_java_replay",REPLAY_FILE)
replay=importlib.util.module_from_spec(spec)
sys.modules[spec.name]=replay
spec.loader.exec_module(replay)

LAT0=48.0
LON0=7.0
R_EARTH=6378137.0
COS_LAT=math.cos(math.radians(LAT0))
SOURCE_NAMES={
    "pixel":"advio-09/pixel/arcore.csv",
    "reference":"advio-09/ground-truth/pose.csv",
}
MAX_GAP_SECONDS=0.7
SEED=20261020


def read_pose_csv(z, name):
    with z.open(name) as raw:
        table=csv.reader(io.TextIOWrapper(raw,encoding="utf-8-sig",newline=""))
        rows=[]
        for row in table:
            if not row:continue
            v=[float(x) for x in row]
            if len(v)!=8 or not all(map(math.isfinite,v)):
                raise ValueError("Invalid real pose row: "+name)
            if rows and v[0]<=rows[-1][0]:
                raise ValueError("Nonmonotonic timestamps: "+name)
            rows.append(v)
    if len(rows)<40:raise ValueError("Insufficient real pose samples: "+name)
    return rows


def interpolation(records, at, cursor):
    # Called with monotonically increasing t; reject GT gaps and extrapolation.
    while cursor+1<len(records) and records[cursor+1][0]<at:
        cursor+=1
    if cursor+1>=len(records):return None,cursor
    a,b=records[cursor],records[cursor+1]
    dt=b[0]-a[0]
    if at<a[0] or at>b[0] or not 0<dt<=MAX_GAP_SECONDS:return None,cursor
    fraction=(at-a[0])/dt
    return (a[1]*(1-fraction)+b[1]*fraction,
            a[2]*(1-fraction)+b[2]*fraction,
            a[3]*(1-fraction)+b[3]*fraction),cursor


def continuous(samples):
    if not samples:return []
    windows=[];start=0
    for i in range(1,len(samples)):
        if samples[i][0]-samples[i-1][0]>MAX_GAP_SECONDS:
            windows.append(samples[start:i]);start=i
    windows.append(samples[start:])
    return max(windows,key=lambda x:x[-1][0]-x[0][0])


def world_to_geo(east,north):
    return (LAT0+math.degrees(north/R_EARTH),
            LON0+math.degrees(east/(R_EARTH*COS_LAT)))


def geo_error_m(lat,lon,east,north):
    e=math.radians(lon-LON0)*R_EARTH*COS_LAT
    n=math.radians(lat-LAT0)*R_EARTH
    return math.hypot(e-east,n-north)


def synthesize(real_arcore, reference, cut_seconds, out, seed=SEED,
               sigma_gnss=1.5, drift_m_per_sec=0.0):
    """A real POSE STREAM with controlled simulated GPS outage.
    Ref is used only for pre-outage GNSS fixes, route proxy, and post-run scorer.
    drift_m_per_sec injects a benchmark fault, never an estimator correction.
    """
    if not (math.isfinite(cut_seconds) and cut_seconds>=10 and
            math.isfinite(sigma_gnss) and sigma_gnss>=0):
        raise ValueError("Invalid calibration/noise")
    if not math.isfinite(drift_m_per_sec):raise ValueError("Invalid injection")
    pixel=continuous(real_arcore)
    if len(pixel)<80 or pixel[-1][0]-pixel[0][0]<cut_seconds+8:
        raise ValueError("No uninterrupted external ARCore trajectory long enough")
    pixel_start=pixel[0][0]
    # Require reference overlap and synchronize from the original timebase.
    truth=[];filtered=[];j=0
    for pose in pixel:
        xyz,j=interpolation(reference,pose[0],j)
        if xyz is None:continue
        truth.append((pose[0],*xyz))
        filtered.append(pose)
    if len(filtered)<80 or filtered[-1][0]-filtered[0][0]<cut_seconds+8:
        raise ValueError("Insufficient GPS-off ARCore/reference overlap")
    t0=filtered[0][0]
    cutoff=t0+cut_seconds
    rng=random.Random(seed)
    route=[["route_revision","index","latitude_deg","longitude_deg","gpx_elevation_m","vertical_datum"]]
    step=max(1,len(truth)//500)
    for i in list(range(0,len(truth),step))+([len(truth)-1] if (len(truth)-1)%step else []):
        _,east,_,north=truth[i]
        route.append([1,len(route)-1,*world_to_geo(east,north),"","UNKNOWN"])
    poses=[["ar_frame_ns","android_camera_elapsed_ns","receipt_elapsed_ns","tracking_segment",
            "world_x","world_y","world_z","metric_x","metric_y","metric_z","route_revision"]]
    fixes=[["route_revision","source","fix_elapsed_ns","receive_elapsed_ns","fix_wall_ms",
            "latitude_deg","longitude_deg","horizontal_accuracy_m","altitude_m","vertical_accuracy_m",
            "speed_mps","speed_accuracy_mps","bearing_deg","bearing_accuracy_deg","mock","queued_for_pairing"]]
    truth_out=[];last_fix=-10.0
    for pose,gt in zip(filtered,truth):
        seconds=pose[0]-t0
        ns=1_000_000_000+round(seconds*1e9)
        # ARCore is Y-up. The published Pixel x/z horizontal components are
        # consumed by exactly the same Java guide class as the APK.
        bias=drift_m_per_sec*max(0,seconds-cut_seconds)
        vx,vz=pose[1]+bias,pose[3]
        poses.append([ns,ns,ns+20_000_000,1,vx,pose[2],vz,
                      vx,pose[2],vz,1])
        truth_out.append((ns,gt[1],gt[3],seconds))
        if pose[0]<cutoff and seconds-last_fix>=.99:
            last_fix=seconds
            # Real GNSS is NOT present: add separately seeded, labeled noise.
            east=gt[1]+rng.gauss(0,sigma_gnss)
            north=gt[3]+rng.gauss(0,sigma_gnss)
            fixes.append([1,"GPS_PROVIDER",ns,ns+30_000_000,0,
                          *world_to_geo(east,north),max(1,2*sigma_gnss),
                          "","","","","","","false","true"])
    if len(fixes)<10:raise ValueError("Not enough synthetic GNSS calibration fixes")
    out=Path(out)
    with zipfile.ZipFile(out,"w",zipfile.ZIP_DEFLATED) as z:
        for key,table in [("route",route),("poses",poses),("fixes",fixes)]:
            with io.StringIO() as stream:
                writer=csv.writer(stream);writer.writerows(table)
                z.writestr("vector_vio_advio_gpx_ar_"+key+".csv",stream.getvalue())
    return truth_out,cutoff,len(fixes)-1,filtered[-1][0]-t0


def score(replay_csv,truth,cutoff_ns,fixes_count,sigma_gnss,window_seconds):
    tlookup={t:(e,n,sec) for t,e,n,sec in truth}
    errors=[];seconds=[];states={};missed=0
    with open(replay_csv,newline="") as f:
        for row in csv.DictReader(f):
            t=int(row["camera_elapsed_ns"])
            if t<cutoff_ns:continue
            state=row["state"]
            states[state]=states.get(state,0)+1
            if t not in tlookup:continue
            if not row["latitude"] or not row["longitude"]:
                missed+=1;continue
            east,north,sec=tlookup[t]
            errors.append(geo_error_m(float(row["latitude"]),float(row["longitude"]),east,north))
            seconds.append(sec-window_seconds)
    if not errors:
        return dict(status="NO_LOCATION_AFTER_CUTOFF",states=states,
                    matched=0,missing=missed,ground_truth_provided=True,
                    synthetic_gnss_calibration_fixes=fixes_count,
                    synthetic_gnss_sigma_axis_m=sigma_gnss)
    a=sorted(errors)
    def pct(q):return a[min(len(a)-1,int((len(a)-1)*q))]
    return dict(status="SCORED",states=states,matched=len(errors),missing=missed,
        p50_error_m=pct(.5),p95_error_m=pct(.95),max_error_m=a[-1],
        rmse_error_m=math.sqrt(statistics.mean(v*v for v in errors)),
        last_error_m=errors[-1],last_dropout_seconds=seconds[-1],
        synthetic_gnss_calibration_fixes=fixes_count,
        synthetic_gnss_sigma_axis_m=sigma_gnss)


def relative_vio_with_calibrated_initial_pose(real_pixel, reference, cut_seconds):
    """Best-case 2D camera drift: rigid yaw+translation fitted ONLY before outage.

    Uses a synthetic perfect initial GNSS/survey alignment (from reference).
    Reports calibration baseline and assumes zero relative camera/IMU extrinsic
    lever-arm correction; NO post-hoc rotation/scale or held-out truth in fit.
    This isolates the underlying externally recorded ARCore pose drift from the
    app's stricter real-GPS yaw observability gate.
    """
    pixel=continuous(real_pixel)
    matched=[];j=0
    for row in pixel:
        target,j=interpolation(reference,row[0],j)
        if target is not None:
            matched.append((row[0],row[1],-row[3],target[0],target[2]))
    if len(matched)<60:raise ValueError("No real phone/reference pose pairs")
    t0=matched[0][0]
    before=[p for p in matched if p[0]-t0<=cut_seconds]
    after=[p for p in matched if p[0]-t0>cut_seconds]
    if len(before)<10 or len(after)<20:
        raise ValueError("Not enough independent reference after calibration")
    # 2D SE(2) Procrustes, metric scale held fixed.
    mx=statistics.mean(p[1] for p in before)
    mz=statistics.mean(p[2] for p in before)
    me=statistics.mean(p[3] for p in before)
    mn=statistics.mean(p[4] for p in before)
    cross=0.0;dot=0.0
    for _,x,z,e,n in before:
        xx=x-mx;zz=z-mz;ee=e-me;nn=n-mn
        dot+=xx*ee+zz*nn
        cross+=xx*nn-zz*ee
    yaw=math.atan2(cross,dot)
    co=math.cos(yaw);si=math.sin(yaw)
    dx=me-co*mx+si*mz;dz=mn-si*mx-co*mz
    def err(p):
        _,x,z,e,n=p
        return math.hypot(co*x-si*z+dx-e,si*x+co*z+dz-n)
    init=[err(p) for p in before]
    later=[err(p) for p in after]
    final_error=err(after[-1])
    later.sort()
    # The application requires maximum pairwise displacement (not merely
    # start-to-end displacement, which underreports loops and switchbacks).
    sampled=before[::max(1,len(before)//120)]
    def widest(i,j):
        return max((math.hypot(a[i]-b[i],a[j]-b[j])
                    for k,a in enumerate(sampled) for b in sampled[k+1:]),default=0.)
    ref_baseline=widest(3,4)
    pixel_baseline=widest(1,2)
    anchor=before[-1]
    def relative_error(p):
        _,x,z,e,n=p
        vx=x-anchor[1];vz=z-anchor[2]
        return math.hypot(co*vx-si*vz-(e-anchor[3]),si*vx+co*vz-(n-anchor[4]))
    relative_errors=sorted(relative_error(p) for p in after)
    delta_last=relative_error(after[-1])
    duration=after[-1][0]-before[-1][0]
    return dict(
        method="REFERENCE_CALIBRATED_CAMERA_ONLY_BEST_CASE_NO_GNSS_AFTER_CUTOFF",
        calibration_uses_reference_positions_before_dropout=True,
        post_dropout_reference_scoring_only=True,
        fit="rigid_SE2_translation_and_yaw; NO scale fitted",
        minimum_baseline_met= pixel_baseline>=15,
        horizontal_baseline_px_arcore_m=round(pixel_baseline,3),
        horizontal_baseline_reference_m=round(ref_baseline,3),
        fitted_initial_yaw_degrees=round(math.degrees(yaw),2),
        pre_dropout_rmse_m=round(math.sqrt(statistics.mean(e*e for e in init)),3),
        post_dropout_p50_m=round(later[int((len(later)-1)*.5)],3),
        post_dropout_p95_m=round(later[int((len(later)-1)*.95)],3),
        post_dropout_max_m=round(later[-1],3),
        post_dropout_last_m=round(final_error,3),
        displacement_error_from_last_calibration_p50_m=round(relative_errors[int((len(relative_errors)-1)*.5)],3),
        displacement_error_from_last_calibration_p95_m=round(relative_errors[int((len(relative_errors)-1)*.95)],3),
        displacement_error_from_last_calibration_last_m=round(delta_last,3),
        post_dropout_duration_s=round(duration,3),
        scored_pose_count=len(later),
        reference_truth_kind="ADVIO fixation-aided inertial reconstruction (not optical surveyed GNSS)",
    )


def audit_actual_iphone_gps(archive,prefix):
    """Optional REAL companion-iPhone CoreLocation audit; never fake its fixes."""
    name=prefix+"iphone/platform-location.csv"
    if name not in archive.namelist():
        return dict(status="NOT_IN_ARCHIVE",valid_fixes=0,
                    source="companion_iPhone_CoreLocation_not_Google_Pixel")
    samples=[]
    with archive.open(name) as f:
        for line in csv.reader(io.TextIOWrapper(f,encoding="utf-8-sig")):
            if len(line)<4:continue
            try:t,lat,lon,accuracy=map(float,line[:4])
            except (ValueError,TypeError):continue
            if not(all(map(math.isfinite,(t,lat,lon,accuracy))) and
                   -85<=lat<=85 and -180<=lon<=180 and (abs(lat)+abs(lon))>1 and
                   0<accuracy<=1000):continue
            samples.append((t,lat,lon,accuracy))
    if not samples:
        return dict(status="NO_USABLE_GPS_FIXES",valid_fixes=0,
                    source="companion_iPhone_CoreLocation_not_Google_Pixel")
    return dict(status="RECORDED_GNSS_AVAILABLE",
                source="companion_iPhone_CoreLocation_not_Google_Pixel",
                valid_fixes=len(samples),first_time_s=samples[0][0],
                last_time_s=samples[-1][0],
                reported_accuracy_median_m=round(statistics.median(p[3] for p in samples),2),
                raw_location_fed_to_estimator=False,
                note="Real iPhone location exists, but does not certify Pixel GPS or VIO accuracy")


def run(source,out,calibration_seconds=(20.0,40.0),noise=1.5,seed=SEED):
    out=Path(out);out.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(source) as z:
        candidates=[n for n in z.namelist() if n.endswith("/pixel/arcore.csv")]
        if len(candidates)!=1:raise ValueError("Archive must have exactly one ADVIO Pixel ARCore track")
        prefix=candidates[0][:-len("pixel/arcore.csv")]
        pixel=read_pose_csv(z,candidates[0])
        reference=read_pose_csv(z,prefix+"ground-truth/pose.csv")
        actual_gps=audit_actual_iphone_gps(z,prefix)
    results=[]
    for i,calibration in enumerate(calibration_seconds):
        path=out/("cut_"+str(int(calibration)))
        path.mkdir(parents=True,exist_ok=True)
        zipped=path/"input.zip"
        truth,cutoff,nfix,duration=synthesize(pixel,reference,calibration,zipped,
                                                seed=seed+i,sigma_gnss=noise)
        replay_report=replay.run(zipped,path/"replay",origin="real_external_phone_poses_synthetic_gnss")
        now_ns=1_000_000_000+int(round(calibration*1e9))
        s=score(path/"replay"/"replay.csv",truth,now_ns,nfix,noise,calibration)
        oracle=relative_vio_with_calibrated_initial_pose(pixel,reference,calibration)
        s.update(calibration_seconds=calibration,total_overlap_seconds=duration,
                 calibrated_camera_drift=oracle,
                 engine=replay_report["engine"],gps_after_cutoff=0,
                 real_pixel_pose_count=len(pixel),reference_pose_count=len(reference),
                 replay_engine_sha256=replay_report["engine_sources_sha256"])
        results.append(s)
        # Do NOT upload pseudo GPS session inputs derived from ground truth.
        zipped.unlink()
    summary=dict(schema="vector-gpx-real-external-poses-dropout/1",
        dataset=prefix.strip("/"),input_sha256=hashlib.sha256(Path(source).read_bytes()).hexdigest(),
        actual_companion_phone_gps_audit=actual_gps,
        source="https://doi.org/10.5281/zenodo.1476931",license="CC BY-NC 4.0",
        external_estimator="authors recorded Google Pixel ARCore poses; NOT our S21 APK",
        heldout_reference="ADVIO fixation-aided inertial reconstruction; not purely independent optical truth",
        gps="SYNTHETIC Gaussian GNSS derived from reference ONLY before cutoff; no measured GNSS",
        route="Reference-derived illustrative GPX proxy; route shape is NOT withheld independent truth",
        rendering="No real ARCore floors, camera frames, screen-lock or Depth replayed",
        no_posthoc_scale_fitting=True,
        relative_coordinates="External Pixel ARCore x/z vs ADVIO reference x/z (both y-up; dataset convention subject to independent check); no GT-fitted rotation/scale",
        seed=seed,results=results)
    (out/"report.json").write_text(json.dumps(summary,indent=2,allow_nan=False)+"\n")
    return summary


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--advio-zip",type=Path,required=True)
    p.add_argument("--out",type=Path,required=True)
    p.add_argument("--calibration-seconds",type=float,nargs="+",default=[20.,40.])
    p.add_argument("--gnss-noise-axis-m",type=float,default=1.5)
    a=p.parse_args()
    print(json.dumps(run(a.advio_zip,a.out,a.calibration_seconds,a.gnss_noise_axis_m),indent=2))


if __name__=="__main__":main()
