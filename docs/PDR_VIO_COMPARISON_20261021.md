# What is actually compared, and how to improve PDR/VIO

## Measured update and Android fallback integration

Actual native ABBA passed at source `7e7e11b2fcdc4ba93a57d9e3e3e60e4b821a3f69`,
[run 39608662253](https://github.com/jboehm1/VisualOdometry/actions/runs/39608662253).
On the same AMD EPYC hosted runner and same 1200 stereo pairs:

| Median of two runs | Basalt stereo VO | Basalt stereo VIO |
|---|---:|---:|
| Posthoc rigid ATE RMSE | 0.038872 m | 0.036887 m |
| First-reference-pose gauge ATE RMSE | 0.229116 m | 0.228550 m |
| Translation RPE at 1 s | 0.031037 m | 0.031024 m |
| Rotation RPE at 1 s (reference caveat) | 0.52138 deg | 0.52600 deg |
| Observed sparse distance difference | -1.4299% | -1.5108% |
| Wall duration | 8.342 s | 11.320 s |
| Child CPU duration | 26.819 s | 37.154 s |
| Peak RSS | 60018 KiB | 78352 KiB |

VIO has slightly lower aligned position error here and higher CPU cost. It does
not dominate every metric. Coverage 99.983% is timestamped pose availability
within the available 58.91 s reference interval, not an independently reported
native tracking-state percentage. No ARCore inference ran in this experiment.

The first PDR candidates improved averages but failed the prespecified worst
placement gate. Timing correction alone also failed. A causal arbitration grid
was selected on **10 already inspected people / 30 trials**, then frozen before
reading reserved step annotations for people **15 and 16**. Source
`e407e8d26a1c489ec9061a965cbca86f404ef747`,
[run 39612354616](https://github.com/jboehm1/VisualOdometry/actions/runs/39612354616),
frozen receipt `851713312ea81139f30307fbcce0b3dfd6feddeb64345df9aa28d9643f5e1dbb`:

| Six reserved trials | Previous deployed Java fallback | Frozen consensus_130 |
|---|---:|---:|
| Macro temporal step F1 | 0.946675 | 0.957695 |
| Mean absolute step-count error | 5.115% | 3.025% |
| False-positive events | 330 | 240 |
| Missed reference steps | 124 | 114 |
| Worst per-trial F1 change | — | 0.000000 |

The development set still contains a worst per-trial F1 loss of 0.038549,
within the prespecified 0.04 budget; this is not universal per-person dominance.

The new **default acceleration fallback** is `JAVA_CAUSAL_CONSENSUS130_V2`.
It starts with the previous detector, observes both detectors' emitted peaks
in an 8 s past-only window, and switches only after sustained disagreement
(ratio >=1.3 for 1 s, at least 8 events per detector). Hysteresis and a 270 ms
output refractory prevent chattering and duplicate steps. Hardware Android
STEP_DETECTOR remains preferred. This is a cheap causal heuristic ensemble,
not a trained displacement/body-heading network. Stored step length and heading
assumptions remain unchanged. Recalibrate stride if changing the acceleration
fallback algorithm, gait or carrying position; a stride calibrated to an older
step counter can inherit its counting bias. Hardware-step profiles are not
automatically reset. Metre-scale accuracy must be checked separately; S21 metre-scale accuracy is still unqualified.

Canonical arithmetic lives in StepPeakDetector; research adapters call the
same code. Each real input verifies actual default PdrEngine timestamps equal
the frozen selected candidate. Repeated CI of IDs 15/16 is fixed-candidate
verification, **not fresh held-out evidence**. Retained reports preserve the
rejected initial/timing/arbitration variants as well as the accepted result.
The older historical original-engine failure in PDR_REAL_SIMUL_ITERATION.md
is not erased: the new acceptance study compares against the immediately
previous deployed fallback, not a claim of universal dominance over all engines.

A separate exact-Java probe reproduced pause/resume accepting an old step,
counting 3 instead of 2 and adding a backwards timestamp. Watermarks now survive
resume; a timed resume boundary rejects paused samples before priming filters.
Cadence resets independently so a pause is not treated as a walking period.
Three JUnit regressions and a before/after compiled probe cover this correction.

Original numeric evidence and actual native poses:
[retained receipt](work/pdr-vio-evidence-20261021/receipt.json).

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
displacement models. Rejected candidates remain research-only. The accepted consensus_130 policy now supplies the default acceleration fallback.

Use all ten previously inspected participants for selection; no relabeling
of old subjects as pristine holdouts. Freeze the chosen implementation/hash
before first scoring reserved IDs **15 and 16**. Require non-regressing macro
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
