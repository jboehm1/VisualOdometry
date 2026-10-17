# 0.6.0-alpha — Square-loop visual replay

- Change the deterministic walk to a square corridor loop with softened corners and substantially reduced continuous phone shake.
- Render a more structured corridor scene and process its camera pixels with a corner detector and patch tracker.
- Preserve feature-track identities so map landmarks stay stable instead of being re-added on every frame.
- Add deterministic exposure levels and low-light read noise so dim and dark tracking behavior can be reproduced.
- Draw tracked feature overlays and a relative path/landmark map built from image correspondences; the reconstruction receives no simulated pose or scene geometry.
- Keep truth pose separate for comparison, and document that the lightweight relative odometer uses a scale prior and is not a validated SLAM benchmark.
- Add tests for square-loop closure and image-derived feature tracking/reset behavior.

# Unreleased — Visual-inertial 3D simulation map

- Start with an empty estimated map and keep the renderer's ground-truth pose and corridor geometry outside the mapping estimator.
- Feed the estimator rendered camera pixels plus synchronized synthetic IMU samples; integrate gyro rotation and use accelerometer velocity only as a weak prior to image-derived translation.
- Triangulate persistent 3D landmarks from repeated feature observations after estimated camera parallax; show an XYZ camera path and estimated 3D cloud in the live inset.
- Start simulation at time zero with an empty map; START/PAUSE explicitly control estimation, and RESET clears the reconstruction and returns to the empty state.
- Add a live AR camera flash toggle, with camera support detection and automatic shutoff when the activity pauses.
- Expand deterministic tests to check empty startup, map growth from pixel motion, 3D landmarks, path coordinates, and reset behavior.
- Mark the simulation as a non-metric demo with a depth prior and unvalidated drift/accuracy.

# Unreleased — Reliable live capture and rounded simulation corridor

- Make the first START launch ARCore and begin recording together, with clear STARTING, PAUSE and RESUME states.
- Auto-save each growing live map after new keyframes or POIs, add an explicit SAVE MAP snapshot action, and preserve temporarily paused POI anchors.
- Keep CSV odometer/IMU capture aligned with the recording lifecycle so distance and speed begin with START.
- Reduce simulated camera and IMU shake, increase the rounded corridor bend radius, and render continuous curved walls with an overhead ceiling.
- Add regression checks for the reduced motion and corridor dimensions/roof height.

# 0.5.0-alpha — Deterministic offline simulation

- Add an offline replay with a procedurally rendered indoor camera walk, hand-held motion, synchronized accelerometer/gyroscope values and known ground-truth pose.
- Add repeatable 40-second route playback, pause/reset/loop controls, playback speed and 60 Hz CSV export using a fixed synthetic seed.
- Make camera and ARCore optional at installation so simulation can run without light, camera permission, network or an ARCore-capable phone.
- Add deterministic simulation tests for repeatability, route motion, gravity level and finite sensor values.
- Simulation imagery and IMU are generated test inputs, not real camera recordings or measured phone sensors.

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
