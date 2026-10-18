# ADVIO miniature raw VI dataset pilot
- Integration SHA read: 372c935fc27536d417fa371d79f5f453546ebefa on feature/slam-explorer.
- Reviewed AGENTS.md and docs/DEVELOPMENT_COORDINATION.md.
- Isolated branch: feature/advio-mini-raw-vio. MainActivity/Manifest/DATA/Java PDR and existing workflows remain untouched.
- Objective: fallback light raw VI dataset (70.7 MB) with synchronized video, IMU and independently described reference, if larger Mobile-GVIO dataset timestamps cannot be calibrated.
- Modifications: new standalone ADVIO downloader, derived analysis script, new dedicated CI, docs under docs/coordination and docs/campaigns only. No replacement of existing engines.
- Need explicitly distinguish reference computed using inertial fixes (ADVIO) from independent optical/ LiDAR truth and never feed its GT into estimator.
- Next step: inspect real original archive, require true frame/gyro timestamps, run actual sensor algorithm before reference scoring; keep source MD5/URL/commit provenance.
