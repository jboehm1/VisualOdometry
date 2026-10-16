# Engineering roadmap and known limitations

## Completed as source code (requires CI + real-device verification)

- Android project + Java 17 build configuration.
- ARCore camera+IMU tracking integration, camera texture background and a graphical overlay.
- Start/Pause/Resume/Reset/Mark, elapsed time, cumulative distance, net displacement, relative height, filtered speed, 2D path.
- Tracking-gap handling, outlier rejection, tiny-motion deadband.
- Per-session private CSV and user-controlled export of historical sessions.
- Unit tests for deterministic odometer math.
- Python CSV audit/plotting utility with parser tests and a step-by-step VIO learning guide.
- GitHub Actions debug-APK build and test workflow.

## Not yet verified or implemented

- No compiled/released APK is bundled in source control; successful CI creates a downloadable debug APK artifact.
- Not tested on a Galaxy S21 or any physical Android device.
- No quantified localization / drift error or certified position-accuracy estimate.
- Raw diagnostic IMU observation added in 0.2; calibrated synchronized replay still missing.
- No camera pose covariance or calibrated motion uncertainty UI.
- Sparse cloud/plane and anchored path visualization added in 0.2; depth occlusion still missing.
- 0.3 adds saved snapshots, 3D orbit viewer, photos, PLY/TUM export and a manual endpoint constraint. No automatic feature-graph loop optimizer or persistent ARCore tracking-map reload.
- No background/screen-off tracking, no foreground service.
- No GPS, barometer, wheel odometry, LiDAR, offline image-depth inference or drone telemetry integration.
- No crash reporting, automated UI/instrumentation tests, long-duration stress tests or thermal characterization.
- No deterministic raw-sensor replay or flight-grade health monitoring.
- No formal product release signing, Play Store publishing, privacy-policy/legal review.

## Priority sequence

### P0 — Make it run and measure
1. Run CI and verify debug APK exists. Correct any SDK/build/compiler errors.
2. Install on at least one Galaxy S21 and verify camera feed, tracking, lifecycle and export.
3. Execute T01–T12 in VALIDATION.md, publish honest per-run results and plots.
4. If necessary, fix rotation/camera-texture transformations and Android insets on physical phones.
5. Calibrate the distance deadband and speed filtering empirically, not by aesthetics.

### P1 — Improve observability and quality
1. Record tracking-state transitions and app timing anomalies explicitly in telemetry.
2. Add ARCore camera intrinsics and frame-rate/latency telemetry.
3. Add motion-quality indicator based on observed drift, tracking failures, feature quality (if accessible).
4. Add optional GNSS **only as external ground-truth benchmark**, not as input to the VIO estimate.
5. A/B comparison against an established VIO reference on EuRoC or TUM VI data (separate desktop runner).

### P2 — Research-grade estimator
1. Build a separate C++ OpenVINS / ORB-SLAM3 / VINS-Mono backend with synchronized raw camera/IMU.
2. Calibrate intrinsics, distortion, IMU biases, camera–IMU extrinsics and time offset.
3. Add dynamic-object rejection, keyframe management, loop closure and relocalization.
4. Consider an AI metric-depth prior only when independently evaluated on aerial scenes.
5. Quantify uncertainty, lost tracking, segment discontinuity, scale drift and battery/thermal costs.

### P3 — Publication / operational hardening
1. Real device automated instrumentation tests and long-duration soak tests.
2. Crash recovery and journal retention policies with storage quota.
3. Release signing / version upgrades / background behavior / accessibility / multilingual UI.
4. Compare against survey-grade or motion-capture ground truth, document confidence intervals.

**Definition of production-ready:** buildable, field-validated, numerically characterized, crash-resilient, and sufficiently reliable for a clearly defined **non-safety-critical** use case. The initial code in this repository does not meet that definition yet.
