# VECTOR VIO 0.17.0-alpha1 — S21 gyro dropout and repeatable sensor experiments

**Implementation branch:** `feature/s21-relative-gyro-context-v017-20261022` based on **PR #52** HEAD `e7c22b9d92971de84783c16d8d34ce5ef06b4d45`. This is an **additive experiment**, not a production-safe navigation module. Other app features from stacked #47 and #48 are not rewritten.

## Actual Android changes

- `ShortHorizonGyro`: pure Java, physically meaningful integration of *real* `Sensor.TYPE_GYROSCOPE` Android device-frame angular rates. Each currently tracked ARCore camera frame resets the integrated **relative DEVICE rotation** to identity. During vision loss only, gyro quaternion `qx,qy,qz,qw`, accumulated angle and timestamps can be exposed for **at most 1.5 seconds**. Midpoint integration, normalize quaternion, monotonic time, reject gyro samples out of order, gaps >100ms, sample age >250ms, nonfinite/unphysical samples and expired anchors. **No camera frame pose**, calibrations, stride, velocity, global heading, absolute position, map or world rotation estimated; no covariance claims. This is **not full VIO propagation**: gyro bias, device-to-camera extrinsics and exposure timing remain uncalibrated. The UI labels it `DEVICE ROT XX° · NO POS`. Source sidecar `*_relative_gyro.csv` records valid/invalid reason, device angular delta, tracked state, sample counts, age, and clock. Raw angular signals do not automatically provide pedestrian direction or bicycle speed.
- `SensorLabActivity`: opt-in scientific condition selector before START, **user-labelled only**: stationary bright / walk bright / walk dim / camera physically covered in a safe place / bike outdoors safe / run outdoors safe / phone in pocket / unknown carry. It records a frozen manual label in `experiment_context.json`, adds that file to the normal ZIP, and repeats in `summary.json`. Conditions are NOT detected, certified or sensor-inferred. Camera coverage simulation must be done in an otherwise safe ordinary environment; **never test in active fire or real smoke**.
- App Android version `0.17.0-alpha1`, `versionCode=18`, distinguishable from older APKs.

## Architecture and limits

1. ARCore remains authoritative for its own tracked poses, while PDR classic and Basalt keep their existing separate implementations. There is **no fusion into the path** from the new quaternion. Existing `VisualObservabilityGate` remains fail-closed with `IMU_ONLY_NO_POSITION` and `POSITION_UNKNOWN`; it does not claim a validated probability or metric position covariance.
2. Sensor clock is `SensorEvent.timestamp`, anchored against `SystemClock.elapsedRealtimeNanos`, **not** ARCore camera timestamp (the ARCore timestamp may use a different clock). The rotor represents uncalibrated relative *phone/body* axis changes, not world-frame rotations; no pose transfer from ARCore.
3. Short horizon may still fail under fast motion, bias, Samsung sensor sampling throttling, asynchronous callbacks, severe optical degradation, variable mounting, or a visually indistinct scene. The gate abstains rather than fill missing translations. Tests do not constitute independent physical accuracy.
4. S0 phone-only analysis stays strictly separate from H-only radar, thermal camera, extra IMU, new BLE/UWB anchors. The app does **not** see through opaque smoke, map hidden objects, guide evacuation or replace personal safety equipment.
5. AI research candidates XFeat / EqNIO / TLIO / MARIO / ZipDepth are **NOT embedded**; do not present this version as the implementation of those learned networks. Their weights/licensing and S21 on-device inference must be checked independently.

## Verification

Run `gradle :app:testDebugUnitTest :app:lintDebug` with the Android workflow and both replay campaigns. Pure-Java test `ShortHorizonGyroTest` covers known 90-degree rotation, reset, invalid order, stale data, missing anchors and expiration. Robolectric `SensorLabActivityTest` checks scenario presets.

**Physical experiment on S21**: first export legacy v0.16 session backups; verify version 0.17 shown; perform easy safe hallway walks: opt-in Sensor Lab conditions with *known measured route* and capture 1–2 min; open ARCore record same route under safe lit/low-light conditions and visually cover camera **only while stationary**. Inspect `_relative_gyro.csv`, `_visual_observability.csv` and `_phone_environment.csv`. Compare to independently observed angles/distances; choose a bias estimate only after real logs, not a fabricated accuracy number. Android **debug signing may change** across builds and require uninstall; BACK UP data beforehand.

## Need further work
- Genuine phone VIO/PDR benchmark with independent GT, rolling-shutter and camera-to-gyro calibration, energy and latency; safe under dim light, camera covered.
- Evaluate licensed learned inference first off-device on real captured logs. Future S21 LiteRT models only after reproducible checkpoint provenance and measured uplift.
- Sensor Lab now preserves explicit scenario labels to prevent accidental data leakage and benchmark dataset confusion; neither manually selected scenario nor GitHub CI is ground truth.

Last CI result is **not inferred** from code; check the exact PR HEAD Actions run.
