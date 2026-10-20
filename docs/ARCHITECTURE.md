## Interface déterministe de validation (v0.16, pas une nouvelle estimation)

La pipeline de test réutilise `benchmark.py` → `tools/campaigns/unified.py` → `tools/testenv/` plutôt qu'un quatrième runner ou une nouvelle vérité synthétique cachée. `tools/datasets/repro_manifest.py` capture SHA code/fichiers d'entrée et vérifie les mutations avant relecture. Le reçu indique explicitement **SYNTHETIC_FIXTURE** ou **UNVERIFIED_REMOTE_BYTES_AT_PREFLIGHT** quand rien de réel n'est localement hashé. Les mesures ATE/RPE, GPX p95, couverture, latence p95 et énergie exigent des données et métadonnées réelles et le moteur effectivement exécuté. Le test de deux PDR sur une entrée synthétique ne prouve jamais leur précision terrain. Voir [matrice et licence](DATASET_REPRODUCIBILITY_V016.md).

## v0.15 native-VIO boundary (experimental)

The new pure Java `nativevio.NativeVioInputGate` is a **fail-closed acquisition contract**, not a VIO estimator. It checks explicit camera identity/calibration and clock source, frame monotonicity, shutter duration and gyro/accelerometer timestamp bracketing; >1-second capture gaps start fresh acquisition segments. It is **not connected** to existing Camera2 Lab or ARCore. Do not report the gate as a second visual-inertial engine, a metric pose, or a replacement for ARCore/GPX. Future boundaries: `ImageReader YUV_420_888` + checked `CaptureResult` timestamps → calibrated C++ estimator → source/scale/quality-tagged pose → explicit world reanchoring. See [native VIO](NATIVE_VIO_ENGINE_V015.md) and [commercial release policy](COMMERCIAL_RELEASE_GATES.md).

> Current implementation inventory: [MODULES_AND_DATASETS.md](MODULES_AND_DATASETS.md). This document retains historical mathematical explanations; use that inventory and CAPTURE_FORMAT.md for implemented features.

> Updated behavior: see [SPATIAL_EXPLORER.md](SPATIAL_EXPLORER.md). Since 0.2 displacement/height restart per independent segment, and diagnostic raw IMU is logged. Since 0.3 map snapshots can be saved/reopened, with optional image keyframes and a manual endpoint constraint. The older baseline explanations below describe 0.1 where inconsistent.

# Architecture, mathematical model and assumptions

## 1. The pipeline

    physical environment
          |
          v
     rear phone camera  ────────┐
                                │
     gyro + accelerometer  ─────┤  ARCore internal VIO
                                │  proprietary tracking engine
                                v
                     pose in local metric world
                     (tx,ty,tz, qx,qy,qz,qw)
                                |
                      tracking status check
                                |
                 duplicate / gap / jump gating
                                |
                    distance accumulator
                       |            |
                       v            v
                  real-time HUD   CSV samples

ARCore is responsible for the actual visual-inertial estimation. We do not claim to have recreated ORB-SLAM3, VINS-Mono or OpenVINS. For learning, inspect Odometer.java: it is a transparent **post-processor of poses**, not a SLAM estimator.

## 2. Why one camera cannot measure absolute scale by geometry alone

An image ray in a calibrated pinhole camera is

    λ [u, v, 1]^T = K [X, Y, Z]^T

where homogeneous-coordinate normalization to 1 does **not** specify distance. The positive λ is an unknown scene depth (after normalization). From two camera views, geometry can estimate R and the **direction** of t but not its magnitude. Scaling all scene distances and camera translations by the same positive factor leaves the images unchanged.

An accelerometer responds in physical units (m/s²); with a calibrated, synchronized camera/IMU and **sufficient excitation**, a properly initialized visual-inertial estimator can constrain metric scale. Degenerate motions, sensor bias, high vibration, rolling shutter and poor texture degrade observability.

ARCore internally combines vision and IMU and exposes metric poses. The SDK does not promise a particular error bound, covariance matrix, or perfect metric scale.

## 3. Coordinate convention and pose integration

ARCore world coordinates are local and use +Y as up. Camera.getPose() represents the camera pose in the ARCore world. If camera centers are c_k=(x,y,z) in metres:

    Δc_k = c_k − c_{k−1}
    path_m += ||Δc_k||₂
    horizontal_m += sqrt(Δx² + Δz²)
    elevation_change_m += Δy
    displacement_m = || Σ accepted Δc_k ||₂
    speed_mps ~= smoothed( ||Δc_k||₂ / Δt_k )

We integrate world-frame differences because Euclidean displacement magnitude is invariant to rotation. **That is NOT the same as the relative camera-frame pose target from the legacy notebook.** If training a visual model on KITTI camera-to-world poses:

    T_a→b = inverse(T_world←a) · T_world←b

which gives the proper relative transform, including rotation and camera-frame translation. Subtracting quaternions is mathematically wrong.

No object recognizer or explicit ground plane is necessary to read ARCore metric poses. ARCore itself is free to use visual landmarks as part of estimation.

## 4. Recording semantics, observability and failure behavior

- Pressing START sets a new motion-reference point only after good tracking.
- PAUSE/RESUME discards unknown motion between segments.
- Camera tracking PAUSED/STOPPED triggers a gap; it never interpolates the missing path.
- Duplicate or out-of-order frame timestamps are ignored.
- A gap > 0.70 seconds **between successive valid camera frames** or an implausible **frame-to-frame** speed > 25 m/s resets the segment anchor. Holding the phone still for minutes does not count as a missing-frame gap.
- Motion below 0.035 m relative to the most recently accepted pose is deferred to avoid per-frame jitter; slow motion can accumulate into a larger accepted step.
- 2D mini-map uses XZ displacement; Y is height. It is **not a global north-up map**, geographic route or ground-truth trajectory.
- Displacement is formed from accepted translations only. If ARCore's world origin or orientation changes between segments, displacement and the map may be **inconsistent even if path length remains plausible**.
- No data is integrated while tracking is lost. Actual distance may be greater than displayed.

Thresholds are heuristics, not calibrated confidence bounds. For a real validation study, log varying feature density, speed, vibration and environmental conditions.

## 5. What is observed / estimated / unknown

| Quantity | Source | Confidence provided by app |
|---|---|---|
| Camera pose | ARCore camera tracking | Only coarse TRACKING / PAUSED |
| Pose orientation | ARCore quaternion | No covariance |
| Metric scale | ARCore's fused internal state | Not independently verified |
| Travel distance | Our gated pose differences | No formal uncertainty bound |
| Relative height | +Y pose integration | No barometric constraint |
| GPS coordinates | Optional foreground GPS/network fixes | Provider-reported accuracy; separate from VIO |
| Raw IMU samples | SensorManager CSV, requested at 200 Hz | Actual delivery rate varies; camera timing/extrinsics not calibrated |
| Ground truth | External tape / surveyed track | Not available inside app |

**Do not confuse a 2-decimal display with 1-cm accuracy.** The UI formatting is a display choice, not a precision claim.

## 6. Design trade-offs

**Why ARCore first?** Integrated calibrated timestamps, camera/IMU fusion, Android device support, tracking state, GPU camera feed, and shorter time-to-first-working-odometer. Reimplementing VIO requires rigorous camera–IMU extrinsic and temporal calibration, gravity/bias initialization, marginalization and nonlinear optimization. It is a separate, more advanced R&D project.

**Why no AI metric-depth model?** ARCore's IMU already supplies an independent metric reference. Generic learned depth estimators often generalize poorly to aerial/natural scenes. Adding one without validation could make scale less reliable.

**Why local CSV?** Repeatable benchmarking and privacy. A CSV can be read in Python/Pandas or processed in evo/TUM tools after conversion.

**Why no background tracking?** ARCore must own an active camera and its session lifecycle. When Android backgrounds the app, recording pauses; there is no deceptive estimate through unknown motion.

## 7. Pointers for further research

- ARCore: https://developers.google.com/ar/develop/java/enable-arcore
- ARCore Camera: https://developers.google.com/ar/reference/java/com/google/ar/core/Camera
- ARCore Session: https://developers.google.com/ar/reference/java/com/google/ar/core/Session
- OpenVINS: https://github.com/rpng/open_vins
- VINS-Mono: https://github.com/HKUST-Aerial-Robotics/VINS-Mono
- ORB-SLAM3: https://github.com/UZ-SLAMLab/ORB_SLAM3
- EuRoC MAV dataset: https://projects.asl.ethz.ch/datasets/doku.php?id=kmavvisualinertialdatasets

### Geographic heading observability
`GeoAlignment` checks minimum supported motion in both local VIO and GPS coordinates after robust inlier selection. Its `baselineMeters` is the smaller compatible span, used conservatively for diagnostic heading allowance. Raw residual RMS still includes rejected fixes. This is a fail-closed geometry check, not a covariance estimate or common-bias detector.
