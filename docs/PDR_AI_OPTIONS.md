# Camera-free odometry — neural models, genuine implementation status

> Audit date: 2026-10-18. Development branch: `feature/camera-free-pdr` at or after
> `01edef7b32b86cd76bb2527de14389fd0b5d098d`. Integration audited at `372c935fc27536d417fa371d79f5f453546ebefa`.
> **Progress is milestone completion, NOT trajectory accuracy, performance,
> or a forecast.** The previous global PDR 42.1% score is separate and
> has not been recomputed for this independent AI backlog.

## What actually ships, and what doesn't

- A **real, pure-Java, 24→32→4 inference interpreter** now exists under
  `app/src/main/java/dev/jboehm/vectorvio/pdr/ai/`. It includes causal,
  bounded 64-frame IMU windows, timestamps, acceleration/gyro alignment,
  finite-value guards, feature normalization, SHA-256 checkpoint verification,
  coordinate-frame metadata, numeric inference and no-fusion safeguards.
- `NeuralMotionModel` is the backend-agnostic interface for future RoNIN,
  TLIO, EqNIO, IMUNet, DeepILS or ONNX/LiteRT adapters. **Their original
  weights/preprocessors do NOT fit our tiny MLP binary or architecture.**
- The Java tests exercise forward passes using **synthetic constant
  TEST_ONLY weights**, invalid checkpoint rejection, and no-model fallbacks.
- There is **no trained checkpoint** bundled, **no AI navigation running
  inside the Android Activity**, **no certified neural body heading**,
  **no integrated learned displacement, no EKF**, and no published
  real-data/S21 metric accuracy.
- The runtime's estimates are **DEVICE_XY_METRES diagnostic candidates**,
  not world East/North positions. They deliberately never update
  `PdrEngine` steps, stride, heading, position or trail.

## Options worth implementing, prioritized

| Priority | Model / method | Progress toward a usable Android AI feature | What is done | Next major missing work |
|---|---|---:|---|---|
| **P0** | Generic neural IMU foundation (our **portable MLP**, independent baseline) | **55%** | Java inference, causal buffering, model/fingerprint checks, unit tests | Real held-out training, model export, Activity integration and physical validation |
| **P0** | **RoNIN ResNet or TCN** (learned pedestrian displacement) | **10%** | Official approach/format identified, original-format test adapter elsewhere | Full original preprocessing and trained checkpoint, format conversion, Android runtime, tests |
| **P0** | **RoNIN body-heading network** | **10%** | Method identified | Reliable body direction relative to phone, independent magnetic/world frame, actual trained model |
| **P0** | **TLIO** learned displacement + probabilistic EKF | **10%** | Model and code studied; common diagnostic interface prepared | Gravity-aligned windows, trained network, covariance + full EKF, Android deployment |
| **P1** | **EqNIO** equivariant TLIO/RoNIN variant | **10%** | ICLR'25 architecture/sources identified | Upstream preprocessing/weights reproduced, orientation invariance tests and conversion |
| **P1** | **DeepILS** (lightweight mobile inertial net) | **10%** | Paper/ONNX and Android demonstration identified | Checkpoints/ONNX IO audited, latency proven on S21, trained-model integration |
| **P1** | **IMUNet** / MobileNet-style compact regression | **10%** | Open-source network and Android example identified | Original model/weights and Android input contracts reviewed + validated |
| **P1** | Learned stationary/walk/run and stride correction | **0%** | No trained classifier; manual walk/run stride profiles already work but are NOT AI | Causal labels with held-out subjects and calibrated per-mode regressors |
| **P2** | **KISS-IMU** / self-supervised inertial representations | **10%** | Research reference identified | Assess robotics-to-phone transfer, licensing/retraining, quality improvement |
| **P2** | **Air-IO / AirIMU** and nonpedestrian IMU models | **10%** | Research candidate catalogued | Drone/vehicle-specific state, training data and observability |
| **P2** | On-device ONNX Runtime / LiteRT accelerated backend | **0%** | No dependency added to shared `app/build.gradle` | Choose framework after a verified compatible trained checkpoint exists |

**Percentage rule for each published AI family:** reviewed evidence/licensing
10%, tested exact model-specific preprocessing 20%, actual frozen and
reproduced trained checkpoints 25%, Android inference/interoperation 25%,
held-out real-world + hardware/energy qualification 20%. The above original
architectures have ONLY completed the **research/reference** milestone.
The portable MLP has its own technology milestones: binary feature/model
contract and Java runtime tests are complete, but real-model training,
Android UI integration and real accuracy remain open (55% engineering
estimate). Do not sum these percentages to one overall product score.

## Research source/license notes

- [RoNIN](https://github.com/Sachini/ronin): pedestrian ResNet, LSTM,
  TCN, body-heading; upstream code GPL-3.0; dataset released only in part,
  so subject splits/provenance require extra review. Do not copy source into
  a differently licensed app without a license decision.
- [TLIO](https://github.com/CathIAS/TLIO): displacement AND learned
  uncertainty fed to EKF, usually gravity-aligned. Upstream README warns
  network test trajectories may use **ground-truth orientations** as a
  debugging convenience. Such trajectories are NOT an independent phone
  odometry benchmark.
- [EqNIO](https://github.com/RoyinaJayanth/EqNIO): equivariance of
  neural displacement and covariance; original pretrained architectures
  are not compatible with our tiny Java MLP.
- [DeepILS](https://github.com/OmerTariq-KAIST/DeepILS-IoT-Journal-2025):
  promising lightweight edge inference; verify repository/model licensing
  before copying third-party weights/code; not deployed here.
- [IMUNet](https://github.com/BehnamZeinali/IMUNet_Android): Android
  example and pretrained-model research; its ARCore-derived reference
  needs separation from independent accuracy truth.
- [KISS-IMU](https://github.com/sparolab/KISS-IMU): 2026
  self-supervised odometry, trained with robotics pseudo-labels; research
  not a pedestrian Android drop-in; usage terms require review.

## Hard acceptance criteria before claiming a trained AI option complete

1. Document exactly which model, commit, config, binary SHA-256, training
   license, training dataset(s), subject/carry-mode splits, sample rate,
   IMU calibration and output frame are used.
2. Train/convert original model architecture; compare model-native CPU
   inference with Android inference on identical **held-out test tensors**.
3. No use of Tango/Vicon/GT orientation to align predicted navigation on
   held-out data unless clearly scored as an **oracle upper bound**,
   distinct from deployable IMU-only inference.
4. Compare at least one held-out human + carrying mode against fixed-stride
   Java PDR on the SAME time interval, and report both step-count errors
   and independent metric-distance/trajectory errors.
5. Include impossible-motion, unknown sensor/coordinate frame, uncalibrated
   covariance, discontinuity and no-model safeguards. Fallback must remain
   safe DISTANCE ONLY.
6. Prove Android startup, permissions, lifecycle, APK install, CPU/thermal
   power consumption and accuracy with the S21. Real S21 evidence is
   necessarily pending until hardware is available.

## Test pipeline decision

**Not finalized for neural models.** Existing actual SIMUL study proves
Java step detection against step annotations; OxIOD/RoNIN CI on this
branch proves only **native-format SYNTHETIC fixtures**, not trained
networks on original corpus with independent position truth.
`tools/pdr/run_real.py` currently executes the *fixed-stride Java
step fallback*, NOT any AI network. A separate AI-training/checkpoint
evaluation entrypoint, original dataset manifests and model-independent
metric tests remain necessary.

The test workstream is intentionally untouched. Hand this to its owner:
[AI evaluation pipeline handoff](PDR_AI_TEST_HANDOFF.md).

## Next implementation milestone

After the test owner provides a model checkpoint and golden tensor checks:
implement an appropriate `NeuralMotionModel` backend for RoNIN body
heading or DeepILS/IMUNet (weight license permitting) and connect it
**as a diagnostic only** on the Android Activity. Integrate into
`PdrEngine` only once error and physical alignment are demonstrated.

## Pilot diagnostic model trained 2026-10-19 (separate from motion odometry)

An extremely compact **24→16→3** supervised IMU network has real
weights now, but its sole task is to classify **original SIMUL sensor
placement** among three body-mounted sensors, NOT step events,
position, displacement, stride, motion speed or body heading.
Frozen two-subject test balanced accuracy 58.89%, macro F1 57.22%,
and front-pocket recall 29.1%: an experimental research diagnostic,
**not a navigation quality gate**. Exact source, model hash, checkpoints,
Java on-device diagnostic and limitations: `docs/PDR_TINYML.md`.
The `PDR_AI_MLP_V1` displacement model above remains **untrained**.
