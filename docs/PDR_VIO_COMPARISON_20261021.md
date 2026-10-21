# What is actually compared, and how to improve PDR/VIO

## Current engines and evidence

| Engine | Inputs | Runs today | What the evidence means |
|---|---|---|---|
| Android ARCore | Phone camera + IMU, Google implementation | APK live/replay code exists; no paired physical accuracy trial here | Build/replay support is not a new ARCore accuracy result |
| Basalt C++ | Real calibrated EuRoC stereo + IMU | Source compiled, two actual V1_01 replays passed | Metric-scale poses, first-pose gauge and posthoc rigid diagnostics; not Android |
| Basalt C++ VO ablation | Same stereo, IMU flag off | New ABBA workflow; read exact CI result | Measures IMU contribution within one estimator, not ARCore superiority |
| Python optical-flow/gyro pilot | Real Mobile-GVIO phone frames + gyro | Independent research A/B runner | Unknown metric scale; reference-fitted Sim3/clock are shape diagnostics only |
| Java PdrEngine | Step events or raw accelerometer fallback, optional direction | Same Java code in APK and replay; genuine SIMUL step tests | Step detection/count, not independent metre-scale position accuracy |
| Tiny carry MLP | Six-axis IMU | Experimental placement classifier | Does not calculate displacement or improve PDR distance |

Basalt is an existing VIO implementation, not our own completed SLAM engine.
Our C++ interface and timing gate do not themselves compute poses.

## Valid comparison protocols

1. **Estimator comparison with common input:** capture timestamped CPU images,
   IMU and calibration for native inference alongside an official ARCore
   recording, then export each engine's poses. Validate clocks, intrinsics,
   distortion, camera-to-body extrinsics and source-image identity. Google
   SharedCamera can share supported camera streams; it is not two independent
   camera controllers and needs device/surface validation. A EuRoC ZIP or an
   ordinary video is not an official ARCore recording.
2. **Whole-product comparison with different exposure controllers:** ARCore
   automatic exposure versus a native Camera2 pipeline require separate,
   paired randomized/ABBA runs on the same S21 and measured route, because
   independently changing exposure changes the input images. Match mounting,
   lighting, motion, resolution and temperature; report failures and repeats.
3. **Independent reference:** synchronized motion capture, surveyed tracking
   or a rigidly mounted calibrated external positioning system. A measured
   100 m route establishes distance error, not timestamped 3D ATE or heading.
   GPX/consumer GPS and an engine's own poses are not independent exact truth.

ARCore recording/playback is a supported SDK path. Replaying the same session
can yield different estimates; preserve ARCore version/config and repeated
results, not an assumption of identical poses.

Official references:
- https://developers.google.com/ar/develop/recording-and-playback
- https://developers.google.com/ar/develop/java/camera-sharing
- https://developers.google.com/ar/reference/java/com/google/ar/core/SharedCamera
- https://developers.google.com/ar/develop/c/recording-and-playback/developer-guide

## Measured quantities, without a misleading single best score

| Dimension | Measures | Restrictions |
|---|---|---|
| Metric trajectory | ATE RMSE/median/p95, translation and rotation RPE | Same body/camera frame and clock; no hidden scale/time fit |
| Practical distance | Signed/absolute distance error, loop closure error | Use a separate measured distance; preserve missing segments |
| Robustness | Tracked fraction, losses, reacquisition time, pose jumps | Denominator is the whole declared interval; do not hide failed trials |
| Resources | CPU time, memory, latency p50/p95, energy, thermal drift | Same hardware/protocol; throughput is not per-frame latency |
| PDR steps | Temporal F1, precision/recall, count error by person/placement | SIMUL labels steps, not metric position or body heading |

Keep native host diagnostic alignments separate from surveyed alignment on
phone. Original V1_01 attitude has a published caveat; its first-pose gauge and
rotation figures remain diagnostics. Missing energy/latency stays unknown.
The strict measured scoreboard continues to reject incomplete production
claims, mismatched hardware/input/reference and GT-fitted scale or clocks.

## New PDR experiment and promotion rule

Two causal Java peak challengers add a short time-based smoothing filter and
local-maximum confirmation. One uses fixed threshold, one a bounded recent
signal-energy threshold. They are research heuristics, not trained neural
displacement models. `PdrEngine` remains unchanged.

Use all eight previously inspected participants for selection; no relabeling
of old subjects as pristine holdouts. Freeze the chosen implementation/hash
before first scoring reserved IDs **13 and 14**. Require non-regressing macro
F1 and count MAE, no per-placement F1 drop over 0.04, and an actual improvement.
Keep the deployed algorithm when no challenger qualifies. A failed challenger
is a valid experiment outcome, not an error to conceal or an APK improvement.
Even a gain on two people is insufficient for arbitrary-pocket body heading,
running stride, long-term drift, or real S21 qualification.

## Remaining work toward the best measured configuration

- PDR: a licensed trained displacement/body-heading challenger with original
  preprocessing, separate person/session splits and exact Android parity;
  uncertainty, calibrated stride/direction, and measured phone trials.
- VIO: arm64 NDK/JNI build, dependency review/reduction, calibrated raw YUV+IMU,
  lifecycle and quality gates, followed by paired S21/ARCore experiments.
- Best means a demonstrated precision/robustness/resource tradeoff for each
  use case. PDR pedestrian step distance does not establish bicycle speed.
