# Validation protocol — PHONE EXPERIMENTS (NOT YET EXECUTED)

This document defines what to check; **no empirical test passes are claimed here**.

## Preparation

1. Use a Google-Play-supported Samsung Galaxy S21 5G or another ARCore-compatible device.
2. Install Google Play Services for AR and grant camera permission.
3. Clean the lens, choose daylight and richly textured static scenery.
4. Place a tape or physical markers at known lengths; start and finish at marked camera-center positions. A 10 m **surveyed** distance is ideal.
5. Hold the phone in a consistent orientation. Avoid rapid rotations, shaky hands and moving cars/people.
6. Disable GPS/location services for the experiment to verify the app is camera+IMU-only.
7. Start the app, slowly move/rotate until status TRACKING, then START.
8. Export CSV immediately after each run. Note route length, lighting, speed, device, orientation, software version and ARCore version.

## Required test matrix

| ID | Motion | Reference measurement | What to inspect |
|---|---|---|---|
| T01 | Hold still for 60 seconds | 0 m | Accumulated path from tracking noise |
| T02 | Straight 1 m | 1.00 m | Path, net displacement |
| T03 | Straight 5 m | 5.00 m | Path and motion smoothness |
| T04 | Straight 10 m | 10.00 m | Relative path-length error |
| T05 | Out 5 m + return 5 m | Path 10 m, displacement 0 m | Drift and return-to-origin |
| T06 | Square, four 5 m sides | Path 20 m, displacement 0 m | Loop closure / accumulated yaw error |
| T07 | Ascend fixed stairs | Measured vertical rise | Δ HEIGHT |
| T08 | Tilt and rotate in place | 0 m | Position drift under pure rotation |
| T09 | Cover camera for 5 seconds | Unknown motion deliberately excluded | Gap increments, no phantom jump |
| T10 | Background app, walk, foreground | Unknown interval excluded | Automatic PAUSE / manual RESUME |
| T11 | Low-light/featureless wall | Tracking likely degraded | Warning and no invalid integration |
| T12 | Moderate shake / hand vibration | 0 m | Jitter-related false distance |

**Example calculations:**

    path relative error (%) = 100 * |estimated_path - known_path| / known_path
    closure error (m) = ||estimated_trajectory_end - estimated_trajectory_start||
    closure error (%) = 100 * closure_error / loop_path_length

Also report **missing distance** if tracking was lost. Do not hide bad samples: record outliers and gaps from CSV.

## Operational acceptance gates

These are suggested **targets**, not achieved results:
- All core JUnit tests pass.
- Application starts reliably, permission and AR-service failures show actionable messages.
- Camera image is correctly rotated and cropped; overlay stays legible.
- APK installs on a real ARCore phone without a laptop.
- Start/Pause/Resume/Reset/Mark/Export work, even after app backgrounding.
- No fabricated distance from forced camera occlusion or pause.
- All CSV columns are consistently parseable and time monotonic within a segment.
- After independent trials: median 10-m path error < 5%, 90th percentile < 10%, and stationary false distance < 0.5 m/min (proposed aspirational gates; adjust according to application).
- Validate robustness over **multiple trials and locations**, not just one smooth walk.

## Debugging protocol

- If camera is black: check AR service, permission, GPU driver, Activity life-cycle, and logcat tag `VectorVIO`.
- If tracking stays paused: improve illumination, slowly move sideways to create parallax, avoid blank walls and reflective surfaces.
- If path increases when stationary: measure stationary drift and adjust `MIN_STEP_METERS` in Odometer.java; beware lost genuine slow motion.
- If distance is too small: inspect segment count and camera tracking quality; dropped intervals are intentionally excluded.
- If sudden spikes occur: inspect timestamps, outliers and motion threshold, and check ARCore tracking resets.
- If VIO works handheld but fails on a drone: check vibration, motion blur, short exposures, scene texture, thermal throttling, and camera-IMU calibration.

## Reproducibility

Export CSV, compute total path over each test, plot world X/Z and XY trajectories, keep raw ground-truth distances alongside logs, and report per-environment errors. Keep separate runs: same-sample train/validation metrics do not establish real-world generalization.

**Do not attach this phone to an active drone as a flight-navigation sensor on the basis of these tests alone.**
