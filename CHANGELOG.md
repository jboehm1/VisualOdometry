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
