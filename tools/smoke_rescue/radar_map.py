"""Build display-only sparse radar positive-return map from an estimated trajectory.

Does NOT infer obstacle-free space. No TF/extrinsic or truth used. The map is
only as accurate as supplied radar-IMU odometry and assumed sensor frame.
"""
import math
from collections import defaultdict
from bisect import bisect_left


def project_hits(poses, hits, resolution=0.25, max_pose_age_s=.3):
    if not poses or resolution <= 0:
        raise ValueError("Need estimated poses and positive resolution")
    times=[p["t"] for p in poses]
    if times != sorted(times):
        raise ValueError("Pose times must be sorted")
    bins=defaultdict(int)
    used=0
    rejected=0
    for t,x,y,doppler in hits:
        radius=math.hypot(x,y)
        if not all(map(math.isfinite,(t,x,y,doppler))) or not .25<=radius<=12:
            rejected+=1
            continue
        i=bisect_left(times,t)
        candidates=[k for k in (i-1,i) if 0<=k<len(times)]
        if not candidates:
            rejected+=1
            continue
        k=min(candidates,key=lambda j:abs(times[j]-t))
        p=poses[k]
        if abs(p["t"]-t)>max_pose_age_s:
            rejected+=1
            continue
        a=p["yaw"]
        px=p["x"]+x*math.cos(a)-y*math.sin(a)
        py=p["y"]+x*math.sin(a)+y*math.cos(a)
        bins[(math.floor(px/resolution),math.floor(py/resolution))]+=1
        used+=1
    return dict(bins), {
        "positive_real_radar_echoes_projected":used,
        "echoes_unprojected":rejected,
        "occupied_evidence_cells":len(bins),
        "cell_size_m":resolution,
        "unknown_is_not_free":True,
        "source_pose":"uncalibrated radar-IMU experimental estimate",
        "obstacle_recall":None,
    }
