# VECTOR VIO v0.14 — gradient-weighted exposure + image-space bike motion

**Paper:** Qirui Sun, Qi Ouyang, Xiaolong Li, Zhaoying Cheng,
*Region-weighted gradient and motion-aware camera exposure control for robust visual odometry*,
Journal of Automation and Intelligence, online 17 October 2025,
DOI: [10.1016/j.jai.2025.10.001](https://doi.org/10.1016/j.jai.2025.10.001).

## REPRODUCTION HONESTY — not an exact numerical reproduction
The original abstract is openly readable at
https://www.sciencedirect.com/science/article/pii/S2949855425000577
and confirms four conceptual steps: weighted sum of image-region gradients,
first-order derivative of image-quality score w.r.t exposure, irradiance
adaptation of update size, max shutter constrained by **VO motion feedback**.
Access to **full mathematical methods, source code, numerical parameter
tables and their camera response function** was not confirmed at implementation.
Do **not** describe this repository as a bit-for-bit or numerically faithful
implementation of the 2025 paper; only an independent implementation of
its four **publicly described principles** with documented substitutions.

| Publication abstract | Our concrete implementation | Reproduction gap |
|---|---|---|
| Sum weighted gradient per local image region | `RegionWeightedGradient`: 8×6 tiles, 3×3-smoothed gradient, square-root weight derived from stable texture and non-clipping fractions | Exact paper weights, gradient operator and clipping penalty not verified |
| Compute first-order derivative dQ/dE of gradient quality | Gamma-projected 8-bit virtual exposures ±0.25 EV, fixed tile weights, central finite difference dQ/dEV | No calibrated camera response / real finite shutter exposure pair. Does not reproduce their derivative equations |
| Irradiance-informed step size | Mean ISP-luma^2.2 estimate (nonphysical proxy), variable proportional gain, deadband, ±0.38 EV/cycle; 650ms settle | Not actual scene irradiance in lux; no paper step coefficients |
| VO feedback limits shutter to mitigate motion blur | Actual Camera2 thumbnail patch correspondences track **image-space motion**, including constant bike-speed translations, plus phone gyroscope; 2px full-size image-smear cap and walk/bike angular cap | This is **not full 6DOF VO feedback**, no camera extrinsic/timestamp calibration, no metric speed |
| Shutter and gain output | Configured Camera2 MANUAL_SENSOR, actual CaptureResult exposure/ISO, hardware bounded, user ISO ceiling; Camera2 AE fallback | **ARCore paused** during manual shutter; not coupled to ORB-SLAM3/VINS-Mono |

## Why the bike does not need accelerometer integration
For straight-line **constant-velocity** bicycle movement, the accelerometer
after gravity compensation measures approximately zero, and the gyroscope
may also measure zero. IMU cannot supply absolute translational speed
without additional anchors. The image itself can show substantial optical
flow when nearby scene features move across the sensor. `CameraPreviewFlow`
estimates candidate 2D patch displacements on consecutive **160x90**
Camera2 thumbnails; 10×6 sample grid, ±14 sample-pixel search, local
zero-mean SAD matching, ambiguity screening and median consensus.
Convert px/frame -> px/s using monotonic timestamps and approximate
preview/full-frame image scale. Shutter cap = min(hardware, 2 full-res px /
optical-flow px/s, gyro-based angular-smear cap).

This tracks 2D *apparent* motion and therefore partially addresses
forward bike speed. It does NOT infer **km/h**, scene depth, exact camera
rotation, moving-object rejection, or rolling shutter correction.
Very rapid motion beyond ±14 thumb pixels per 430ms is unobservable:
fallback to bike profile and gyro; no false "zero motion" assertion.
Optical flow can be unreliable in featureless dark conditions or on
moving people/trees, and sharp exposure alone does not guarantee reliable VO.

## Exposure optimization implementation
- Histogram and coarse coherent edges: original `CameraImageQuality`.
- Opt-in **EXPOSURE: REGION GRADIENT (EXPERIMENTAL)** selectable vs
  **P50/GYRO BASELINE** in separate Camera2 optical lab.
- Compute normalized dQ/dEV, choose first-order step clipped to ±0.38EV,
  scale using an irradiance *proxy*, avoid jitter <.055EV, and constrain
  budget using gyro/optical flow/ISO cap. Limit changes >=650ms apart;
  abrupt motion forces shutter shortening ahead of that cadence.
- If gradient is unavailable or unreliable, conservatively fallback
  to baseline brightness regulation. No internet/cloud inference.
- Live diagnostic includes Q, slope, optical flow px/s and confidence.
  Bounded lab CSV (7200 samples) retains gradient and flow diagnostics;
  exported only with explicit Android file picker.

## Experimental protocol, not proven improvement
1. Record walking/cycling Camera2 traces in bright sun, HDR shadow,
   cloudy light, dusk, indoor hallway, near textured ground and distant horizon.
   Repeat comparable scenes in **manual shutter**, **P50 baseline**, and
   **region-gradient + image-flow controller**.
2. Compare actual CaptureResult shutter/ISO, blur limit violations,
   texture score and first-derivative behavior. Current diagnostic
   confidence is not a SLAM inlier count.
3. Record independent ARCore `_arcore_luma.csv` in the same locations.
   **You cannot compare the two pipelines' VO accuracy directly**,
   since ARCore captures a distinct camera stream with auto exposure.
4. Real end-to-end exposure→odometry comparison requires a **Camera2
   image VIO pipeline** (OpenVINS / ORB-SLAM3 port), camera-IMU
   calibration, and independent ground truth. Do not claim exact paper
   performance, or that image quality always reaches a global optimum.

## Device and battery constraints
On rear-camera models without MANUAL_SENSOR and ISO/shutter ranges,
manual regulation is disabled and native Camera2 AE remains available.
Missing gyro/failed patch flow falls back to mode-specific safe shutter.
Low light can force `LIGHT / BLUR CAP` when insufficient photon exposure
within noise threshold. Pixel quality metric uses downsampled ISP RGB,
not raw sensor image; noise/denoising and tone-mapping vary by device.
CPU cost from 160×90 sampling + finite difference 3 quality passes +
limited patch matching at about twice per second requires S21 thermal
and dropped-frame verification before promoting to production.

## Source and reproducibility
- Open abstract: https://www.sciencedirect.com/science/article/pii/S2949855425000577
- Android Camera2: https://developer.android.com/reference/android/hardware/camera2/CaptureRequest
- ARCore SharedCamera: https://developers.google.com/ar/reference/java/com/google/ar/core/SharedCamera
- Source files: `RegionWeightedGradient.java`,
  `RegionGradientExposureController.java`, `CameraPreviewFlow.java`,
  `Camera2ManualActivity.java` and 3 new JUnit fixtures.

TODO for EXACT reproducibility: obtain legally accessible full article PDF
and author implementation or supplementary code, verify all equations,
weight choices, transfer function and VO blur feedback, cite table/section
numbers, translate tests to paper parameter parity. Until then do not label
implementation as "exactly the paper".
