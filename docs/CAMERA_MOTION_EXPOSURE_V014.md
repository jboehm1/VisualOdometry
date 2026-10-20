# VECTOR VIO v0.14 — fast shutter with gyroscope motion ceiling (experimental)

**2026-10-20, based on camera automatic exposure PR #40 @ `27eec28405c4a8012613e3c16f8cd01bbde3e9d8`.**

## Purpose and optical rationale
At walking/running/cycling speeds blur destroys trackable corner gradients; ISO gain adds noise and does **not** recover photons. We bias toward a shorter shutter within a **configurable ISO noise ceiling**, and tell the user when the light is inadequate instead of pretending high ISO fixes everything.

The Camera2-only auto-fast control already reads a 96x54 ISP-processed preview thumbnail and solves a rough shutter × gain budget from P50 and highlight saturation (closed feedback loop). This change adds:
- The **real device gyroscope**, registered foreground-only with `SENSOR_DELAY_GAME`. Values are filtered with EMA and rejected if stale >500 ms. Camera2 setting/preview remain independent from ARCore.
- **WALK / BIKE** selector. Provisional maximum exposures **1/40 s walk** and **1/100 s bike** absent recent usable angular rate. Faster rotation imposes additional bound from estimated angular smear: **0.30° walk**, **0.18° bike**, computed from `exposure × gyroRadPerSecond`. The hardware's minimum exposure remains authoritative.
- If ISO at the user's configured ceiling and the **motion cap** make a bright image physically impossible, keep the shutter <= cap and display **LIGHT / MOTION LIMIT**. No invented minimum lux or guarantee of visual tracking.
- Gyro sudden motion can trigger an immediate short shutter, bypassing the normal >=650ms brightness settle timer. ISO cannot exceed the configured cap or reported sensor range.
- **EXPORT CAMERA2 QUALITY TRACE (CSV)**: bounded to 7200 rows in app memory, requiring user intent to export through Android document picker; includes timestamps, requested and actual sensor exposure/gain, gyro speed, scene P10/P50/P90, texture proxy, clipping, optional ambient lux, selected motion profile and state. Camera2-only, **no video/images/location saved**.
- Device fallback unchanged: if gyroscope missing, use conservative WALK/BIKE cap without gyro; if manual Camera2 capability absent or request unsupported, system AE fallback and disabled manual controls.

## What is and isn't optimized
This is **not true VIO-gradient optimization**. P50 brightness feedback + motion smear proxy + texture warning help study the trade-off. A universal best exposure/noise budget and threshold cannot be inferred without repeated field sessions and independent VIO tracking/feature quality. ISO maximum 1600 and P50 goal 90 are starting values, NOT S21/calibrated scientific truth.

For actual ARCore records, `_arcore_luma.csv` already captures optional 1Hz YUV luminance and TrackingState/FailureReason separately; `tools/camera/evaluate_illumination.py` produces diagnostic comparisons. **ARCore's own automatic exposure remains in effect during 3D-GPX/VIO** — we do not override the capture request because ARCore SharedCamera does not allow it safely.

Future if field evidence warrants: camera-to-IMU timestamp offset calibration, angular-to-pixel flow via focal length / pixel pitch, scene depth dependent translational blur, real tracked-feature inlier ratio on *the same camera stream*, compare motion/noise/frame drops against ARCore baseline on multiple phones and nighttime exposures. Only then decide custom VIO acquisition.

Sources:
- Android Camera2 sensor exposure and sensitivity behavior: https://developer.android.com/reference/android/hardware/camera2/CaptureResult
- Android motion sensors: https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion
- Motion and region-gradient based VO exposure optimisation (2025): https://doi.org/10.1016/j.jai.2025.10.001


## Exposure guided by textured regions (experimental enhancement)

`CameraImageQuality` now computes an independent, 3×3-smoothed gradient ratio and the **median brightness of pixels with spatially coherent gradients**. Exposure feedback uses a tentative 65% feature-median / 35% scene-median mixture when >=4.5% of the examined image has coherent texture. This reduces the influence of bright, featureless sky when a darker patterned path fills the lower preview. A high-frequency alternating 1-pixel checkerboard no longer counts as usable *coherent* texture even though raw gradients are abundant, helping distinguish some alias/noise from persistent structure. Bright clipping continues to constrain adjustments.

The threshold/weight and spatial Gaussian approximation are heuristics, **not benchmarked true feature inliers nor measured noise**. No real model weight or VIO pose correction is added. Exported Camera2 CSV includes both raw and smoothed texture fraction and textured median to allow later comparison with the actual tracking losses recorded by ARCore on independent runs.
