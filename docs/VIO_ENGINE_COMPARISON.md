# VIO / SLAM comparator plan

Status: **comparison harness incomplete; no winner declared**. This document is the acceptance contract for adding non-ARCore estimators to the existing real-data pipeline. The current verified native result is Basalt on EuRoC V1_01 on hosted Linux; it is not phone performance or a clean cross-engine ranking.

## Engine roles

| Engine | What it estimates | Role in comparison | Android status | Distribution note |
| --- | --- | --- | --- | --- |
| ARCore | Google-managed visual-inertial tracking and world map | Android product/reference; run as its own capture, with ARCore pose and camera timestamps | Already integrated in app | Opaque implementation; camera sharing does not expose or replace the internal estimator |
| Basalt | Optimization-based visual-inertial odometry; optional mapping/loop-closure modules | Current custom native engine and first same-input baseline | JNI path exists, but phone sensor calibration and physical-device accuracy are not qualified | Upstream BSD-3; audit every enabled transitive dependency before shipping |
| ORB-SLAM3 | Visual, visual-inertial, multi-map SLAM, relocalization and loop closure | Full-SLAM challenger and map/relocalization reference | No app adapter | GPL-3.0; keep research runner separate pending legal review |
| OpenVINS | EKF/MSCKF visual-inertial navigation with optional maplab/loop-closure integration | Independent filter-based VIO family baseline | Upstream has Android-related work, but no app adapter or verified phone performance here | GPL-3.0; keep research runner separate pending legal review |

“Best” is workload-specific. We must report VIO accuracy, tracking loss/recovery, relocalization/map behavior, compute and energy separately. A full SLAM map loop-closure advantage does not mean better short-horizon phone odometry.

## Required comparison phases

### A. Same recorded sensors, host runners

Run Basalt, ORB-SLAM3, and OpenVINS against exactly the same immutable image+IMU bytes and calibrated parameters. Start with EuRoC monocular-left + IMU so all three process the same sensors; a stereo Basalt result is a separate capability row and cannot be compared as if it were monocular. Pin each source commit, build image digest, config, camera/IMU model, time offset, trajectory hash and runtime receipt.

The engine process must not see ground truth or network. Score only after it exits. Report first-pose-gauge ATE/RPE, tracking coverage and dropouts; separately label any post-hoc SE(3) alignment as a shape diagnostic. V1_01 reference attitude has a published caveat, so rotational and gauge-sensitive scores from that sequence are diagnostic. Repeat on additional EuRoC sequences and TUM-VI; do not select the test sequence based on observed scores.

### B. Lighting / exposure challenge

Use LaMAria’s per-challenge sequences and the ETH illumination-robust visual-inertial dataset to compare raw frames against fixed, logged exposure/contrast preprocessing. Keep every sequence/condition and its calibration fixed; fit thresholds only on a declared development split. Report failures and tail latency, not only mean ATE. ROVER 2025 is useful as a separate outdoor seasonal/vegetation robustness set, but its sensor configuration and truth quality must be checked before pooling scores.

The 2025 low-light paper cited in this project concerns DSO and ORB-SLAM3 monocular VO, not a phone VIO validation. Treat its enhancement methods as ablations, not evidence that our exposure controller is optimal. Our regional-gradient controller also needs equation-by-equation verification against its cited paper before it can be described as a reproduction.

### C. Same phone, paired runs

Once the Android dataset pipeline is qualified, compare ARCore and Basalt in randomized paired sessions on the same surveyed route and device. They cannot consume the same rear-camera stream simultaneously, so use separate runs, identical device settings and independently verified clock/calibration. Save raw camera/IMU and ARCore estimates for replay. Record app/engine version, actual frame/pose rate, latency distribution, thermal state, CPU, memory and energy. Repeat enough sessions to report confidence intervals.

ORB-SLAM3/OpenVINS can enter this phone phase only after their Android input adapters accept the exact same recorded YUV/IMU stream and pass timestamp, frame-convention, lifecycle, and resource tests. GPL review is a separate ship gate.

## Readiness gates

1. **Data integrity:** hashes, sensor timestamps, calibration and coordinate conventions validated; ground truth excluded from inference and selection.
2. **Runner parity:** every engine produces finite, monotonic poses for the identical input; all failures remain in the report with coverage and reason.
3. **Scoring parity:** one pinned evaluator, same time window, interpolation and frame transform; no test-set scale/clock fitting.
4. **Dataset holdout:** development and test sequences are disjoint; preprocessing/configuration is frozen before held-out runs.
5. **Phone comparison:** same physical phone and route, paired randomized sessions; host CPU timings are not substituted for phone speed or battery.
6. **Product decision:** rank only after the preceding evidence is complete. A score must not be described as “excellent” until acceptance thresholds are defined in advance.

## Sources and implementation references

- ORB-SLAM3 upstream, supported EuRoC/TUM-VI modes and GPLv3: https://github.com/UZ-SLAMLab/ORB_SLAM3
- OpenVINS upstream, dataset support, estimator documentation and license: https://github.com/rpng/open_vins
- Basalt upstream: https://github.com/VladyslavUsenko/basalt
- 2025 outdoor visual-inertial SLAM / loop-closing benchmark and code: https://github.com/iis-esslingen/vi-slam_lc_benchmark
- LaMAria egocentric visual-inertial SLAM dataset/benchmark (ICCV 2025): https://lamaria.ethz.ch/
- ROVER multi-season visual SLAM dataset (2025): https://arxiv.org/abs/2412.02506
- ETH illumination-robust visual-inertial dataset: https://github.com/ethz-asl/illumination_robust_vi_dataset
- Low-light DSO/ORB-SLAM3 comparison (2025): https://doi.org/10.1002/rob.22595

This is a reproducibility contract and candidate selection, not a benchmark result. Existing run receipts remain the source of measured numbers.
