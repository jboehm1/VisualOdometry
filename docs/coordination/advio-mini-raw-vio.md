# ADVIO miniature raw VI dataset pilot
- Integration SHA read: 372c935fc27536d417fa371d79f5f453546ebefa on feature/slam-explorer.
- Reviewed AGENTS.md and docs/DEVELOPMENT_COORDINATION.md.
- Isolated branch: feature/advio-mini-raw-vio. MainActivity/Manifest/DATA/Java PDR and existing workflows remain untouched.
- Objective: fallback light raw VI dataset (70.7 MB) with synchronized video, IMU and independently described reference, if larger Mobile-GVIO dataset timestamps cannot be calibrated.
- Modifications: new standalone ADVIO downloader, derived analysis script, new dedicated CI, docs under docs/coordination and docs/campaigns only. No replacement of existing engines.
- Need explicitly distinguish reference computed using inertial fixes (ADVIO) from independent optical/ LiDAR truth and never feed its GT into estimator.
- Next step: inspect real original archive, require true frame/gyro timestamps, run actual sensor algorithm before reference scoring; keep source MD5/URL/commit provenance.

## Actual result (2026-10-18)

- [GitHub Actions run 39170934452](https://github.com/jboehm1/VisualOdometry/actions/runs/39170934452) **success** using original checksum-verified 70,695,364-byte `ADVIO-09` archive.
- 500 real camera video frames decoded, 33 optical-flow pairs, 11,903 features, 831 real gyro samples; **raw-data audit** is not a pose-estimation algorithm.
- Existing external Google Pixel ARCore metric poses (author-supplied) scored against fixation-aided reference: **2,687 pose pairs, rigid-SE3 ATE RMSE 2.0714 m**, no metric-scale fitting. This is **NOT VECTOR's Android app**.
- Small artifact transferred to private Drive; verified file ID `1FxN4LwLayRy-gzv7l7tJVDhusT5zoeqZ`.
- Isolated new files only: `tools/campaigns/advio09_real.py`, `.github/workflows/advio-real.yml`, probe workflow `.github/workflows/advio-inspect.yml`, `docs/campaigns/ADVIO09_REAL.md`, this note. No Android production code or shared older pipeline files changed.
- Next: open PR, review exact current integration SHA and CI results; merge only when all existing A/B/C and Android tests are green. Future raw ADVIO estimate requires real calibrated image + accelerometer + gyro fusion; do not present this external ARCore score as our app accuracy.
