# VECTOR VIO v0.14 — hybrid inertial continuation and adaptive camera QUALITY

**Work began on 2026-10-20. This is a DRAFT development branch, not an integrated neural PDR release.**

## Technical answer — exposure vs walking/bike camera tracking
Current live AR uses `new Session(this)` and ARCore's own camera auto-exposure, configured with a **30 fps target**, regardless of VIEW 5/15/30 fps. At this stage **we cannot force shutter speed or ISO** from normal ARCore APIs: it owns Camera2's continuous capture.
- We CAN read real, per-image `ImageMetadata.SENSOR_EXPOSURE_TIME` (nanoseconds) and `SENSOR_SENSITIVITY` (ISO-like camera gain) through `Frame.getImageMetadata` where available. New 1-Hz CSV `_camera_quality.csv` and HUD diagnostic flag.
- We CAN select ARCore's **30 vs 60 FPS target** via `CameraConfigFilter` *before session start*, if 60 supported. This does **not** force a particular shutter; actual fps can fall in darkness. Current 30 target is deliberate low-light-safe default.
- More control (manual shutter/ISO) may be possible using **Camera2 while ARCore paused**. While ARCore active SharedCamera does not permit `setRepeatingRequest` and disables depth-sensor access; it is not an immediate drop-in manual shutter slider. No unsafe mid-tracking mutation.
- Motion blur follows approximately exposure time × angular rate. Faster shutter improves feature localization but raises noise/ISO in the dark; longer shutter increases light but worsens motion blur. At high bicycle speed, translation blur depends on distance/scene depth too, not captured by the 1-Hz gyro diagnostic.
- Torch is already an optional live AR action, with hardware checks. It is an illumination aid, not a cure for featureless/fog/smoke scenes.
- New `CameraCaptureQuality` marks heuristic high ISO / angular blur **not actual tracking confidence**. Use tracking reason `INSUFFICIENT_LIGHT`, `EXCESSIVE_MOTION`, etc. only as ARCore diagnoses; capture exposure and true frame timestamp for evidence.

ARCore sources:
- https://developers.google.com/ar/reference/java/com/google/ar/core/ImageMetadata
- https://developers.google.com/ar/reference/java/com/google/ar/core/SharedCamera
- https://developers.google.com/ar/reference/java/com/google/ar/core/CameraConfig.TargetFps
- https://developers.google.com/ar/develop/java/camera-configs
- https://developers.google.com/ar/reference/java/com/google/ar/core/TrackingFailureReason

## Neural inertial navigation — research decision
No reliable universal **trained production-ready smartphone + bike** model is available to just plug into our APK without calibration and evaluation. Do not fabricate a benchmark or bundle research-only weights.

| Candidate | Strength | Weakness / effort | Decision |
| --- | --- | --- | --- |
| **RoNIN ResNet/TCN** (ICRA 2020) | 42.7 h, 100 participants, varying phone mounting; direct horizontal pedestrian estimation; ready research baselines | Repo GPLv3, project's **pretrained models research-only**; orientation/placement adaptation and phone->body conversion | **Primary walking benchmark + model reference**, train compatible weights with checked redistribution |
| **TLIO** (2022) | Neural displacement + uncertainty into EKF estimating position, velocity, attitude and IMU biases; best systems architecture | Released code BSD, requires training own network for this code; published training set may be NC; originally headset-centered rather than cycling phone | **Preferred fusion/filter architecture**, experiment after baseline |
| **TinyOdom** (2022) | Quantized tiny TCN aimed at extreme compute limits, walking datasets and other platforms | Old TensorFlow 2.5 tooling and hardware-oriented notebooks, licensing/models to audit | **Preferred compressed on-device baseline** after parity on RoNIN/OxIOD |
| **M²EIT** (ICCV 2025) | Reported SOTA across three datasets; richer temporal/wavelet experts | Heavier multiple experts/bi-Mamba; public deployable model/Android runtime and weights not established | Research challenger, **not first deploy** |
| **NIO from Lie Events** (RSS 2025) | Current event-based geometric IMU preprocessing, TLIO variant and pretrained checkpoints reported | Device generalization, model redistribution, deploy/latency need research | Challenger if easy to reproduce |

Sources: https://ronin.cs.sfu.ca/ ; https://github.com/Sachini/ronin ;
https://github.com/CathIAS/TLIO ; https://cathias.github.io/TLIO/ ;
https://github.com/nesl/tinyodom ;
https://openaccess.thecvf.com/content/ICCV2025/html/Li_M2EIT_Multi-Domain_Mixture_of_Experts_for_Robust_Neural_Inertial_Tracking_ICCV_2025_paper.html ;
https://github.com/RoyinaJayanth/NIO_Lie_Events

**Implementation recommendation**: PDR event/step baseline retained; integrate a *model-neutral displacement + uncertainty estimator*, align its IMU/body reference with the same tracked-world reference **before** feeding GPX rendering. Mobile inference on 100–200 Hz inertial windows with LiteRT/TFLite benchmarked for battery/CPU/latency, NOT on every camera frame. Keep an optional quantized TinyOdom-like TCN and a RoNIN ResNet/TCN. Fuse GNSS/VIO observations and network *delta+uncertainty* in a filter; no map-matching to invent measured accuracy. Cycling needs distinct labels/motion classifier and dataset, not pedestrian step length.

## Actual implementation in first branch commit (v0.14 alpha development)
- `CameraCaptureQuality.java`: pure Java observed shutter+ISO+gyro angular-smear diagnostics; bounded, no camera control.
- `MainActivity.java`: 1 Hz metadata read only, appends HUD warning on high ISO/motion blur; recording sidecar `_camera_quality.csv` for data collection, optional metadata supported.
- `hybrid/HybridPoseEstimator.java`: first strict contract for motion deltas **in AR world frame**, with speed, timestamp, uncertainty, 30s visibility gate, pedestrian/bicycle differentiation, and visual re-anchoring; unit tested. **This is not yet fed IMU/model outputs, GPX or world renderer; no neural weights are included.**
- Existing camera-free `PdrEngine`, route GPX, rendering, realtime geofusion and CSV unchanged except passive camera telemetry. Unit tests are fixture/synthetic only.

## Next acceptance gates (explicitly NOT done yet)
1. Dataset adapter + reproducible RoNIN vs TinyOdom vs TLIO comparison: SAME split, clocks, device axes, gravity, labels; walking, turning, held/pocket, stairs, outage durations 5/15/30 s. Report ATE/RTE, drift per 100m/30s, failure rate, latency, inference cost and uncertainty calibration, NO leaked ground truth.
2. License audit for each dataset, code and checkpoint. Avoid distributed research-only RoNIN pretrained weights and non-commercial TLIO data.
3. Android inertial window capture while AR running; device->body orientation model; compatible gyroscope/accel timestamp interpolation and frame transforms; replay parity. Never substitute magnetic phone azimuth for pedestrian course.
4. Convert tested inference to on-device runtime and test Galaxy S21 real 100-200 Hz sensor cadence, missing sensors, stress/CPU/thermal, bike separately.
5. Wire genuine output into the hybrid pose estimate, then GPX re-projection **with a separate source and uncertainty UI**. Keep last valid AR world alignment across a controlled short outage; invalidate on AR map rebase/clock issues. Preserve GPS-only map if world alignment invalid; no false 3D anchoring.
6. Run integration CI at exact SHA after merge, physical GPX 360° + walking/bicycle route under dark/high-speed/forest tests, and compare to existing v0.13.

Do not change Session FPS dynamically by pausing/restarting on every dark frame; this would itself interrupt AR tracking and anchor stability.

## Coordination
Source `feature/v013-full-gpx-global-lod` SHA `329a63133547a7d88ebda2babcba6b42303082e2`.
Integration branch `feature/slam-explorer` inspected via `AGENTS.md` and `docs/DEVELOPMENT_COORDINATION.md`. Branch `feature/v014-hybrid-inertial-camera-quality`, PR targets v0.13 branch (PR #38 remains unmerged). Shared file edited: `MainActivity.java` only, bounded additive telemetry near ARCore frame reading. All docs/tests new; no changes to Manifest, menu, PDR engine, camera reconfiguration or GPX renderer. Rebase/merge cautiously when v0.13 is integrated.