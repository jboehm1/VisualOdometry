# 0.4.0-alpha — Live scene map and named POIs

- Replace the expanded path-only view with a bounded live X/Z scene map of point samples, tracked plane footprints, camera path and placed POIs.
- Add user-named, ARCore plane-anchored POIs with categories, camera overlay, saved 3D labels and per-segment CSV export.
- Add persistent controls for keyframe interval, translation/rotation capture thresholds, point confidence and overlay layers.
- Upgrade map snapshots to v3 while retaining v2 import; apply manual loop corrections to POI anchors too.
- Add POI round-trip/export, v2 compatibility and marker-correction tests.
- Still requires measured physical-device testing; no automatic semantic object detection or pose-graph SLAM is claimed.

# 0.3.0-alpha — Saved spatial maps

- Cap optional map images at 24 MiB total and each map at 64 MiB; reject malformed or trailing snapshot data before opening it.
- Run Android lint and compile both debug and release variants in CI.
- Save/reopen bounded sparse map snapshots, with background-save attempt.
- Native offline 3D orbit/zoom viewer, segment selection and camera-keyframe inspection.
- Opt-in JPEG keyframes and segmented PLY/TUM/photo ZIP export.
- Live return-to-start pose comparison and explicit manual endpoint constraint saved to a copy.
- Archive corruption/limit, round-trip, segmented export and constraint regression tests.
- Still no automatic pose-graph optimizer, tracking-map reload or physical accuracy evidence.

# 0.2.0-alpha — Spatial Explorer

- Add actual ARCore plane polygons, sparse scene points and anchored camera-height trail.
- Add homogeneous view clipping, four layer modes, expandable segment map and in-app help.
- Add live device-axis acceleration/gyro, measured stream rates, graph and CSV v2 IMU rows.
- Keep total distance across gaps but restart displacement/height and map per segment.
- Add projection and segment regression coverage; document limits and phone tests.
- No physical-device accuracy or persistent SLAM map claim.

# Changelog

## 0.1.0-alpha (2026-10-16)

- Preserved exact 2019 VisualOdometry notebook in `backup/2019-original-colab`.
- Introduced Android-only ARCore camera+IMU tracking application on `feature/android-vio-odometer`.
- Live OpenGL camera texture, custom metric HUD, X/Z trajectory mini-map.
- Gated displacement integration, start/pause/reset/mark, retained local CSV sessions and Android document export.
- Added pure-Java odometry tests and GitHub Actions debug APK workflow.
- Created architecture, math, safety and extensive field-validation documentation.
- Maturity is **engineering beta; untested on phone** until CI/device verification.
