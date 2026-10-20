# GNSS ↔ Visual-inertial GPX alignment — research and acceptance gates (19 October 2026)

## Context and existing baseline

VECTOR VIO currently receives ARCore's **already fused** camera+IMU poses and Android GPS / Google Fused location fixes. Its current `GeoAlignment` robustly estimates local SE(2) heading and translation from paired poses/fixes. This is a loosely coupled architecture: adding the IMU readings again as if independent would **double-count information** already consumed by ARCore. The GPX must never be treated as independently measured ground truth.

A user's multiple 'uncertainty circles' are **position likelihood regions**, not range-only rings. Correlated GNSS multipath and common systematic biases cannot converge to zero just by accumulating many circles. A manual visual placement can provide an *additional user assertion*, but must stay separately labeled rather than modifying raw fixes.

## Current scientific leads

- Pan et al., *Smartphone-based Vision/MEMS-IMU/GNSS tightly coupled seamless positioning using factor graph optimization*, Measurement 229 (2024) 114420, doi:10.1016/j.measurement.2024.114420 — smartphone real measurements, asynchronous clock synchronization, GNSS outages. https://www.sciencedirect.com/science/article/pii/S0263224124003051
- Zhang et al., *GNSS/Multisensor Fusion Using Continuous-Time Factor Graph Optimization for Robust Localization* (gnssFGO), IEEE Transactions on Robotics 40 (2024), 4003–4023. Time-continuous factor graphs handle asynchronous sensors, use robust GNSS factors; vehicle/LiDAR results **not directly transferable to a pedestrian S21**. https://ieeexplore.ieee.org/document/10637732 ; maintained code https://github.com/hz658832/gnssFGO
- Zheng et al., *TSF-GINS: Based on time-fixed sliding window with factor graph*, Measurement 239 (2025) 115421. Windowed GNSS/IMU factors and marginalization useful in extended outages; platform differs from ARCore. https://www.sciencedirect.com/science/article/abs/pii/S026322412401306X
- GTSAM iSAM2 and IMU preintegration are offline references; full tightly coupled reprojection factors are **not available** from ARCore public interface. https://gtsam.org/ and https://borglab.github.io/gtsam/imufactor/

## Proposed experiments, not unverified production code

1. Freeze current estimator and evaluate synthetic **and measured** baselines for 0/5/30/300 seconds and unrestricted GNSS outages, direction changes, forest, intersections and lock/unlock. Ground truth must be **independent of GPS/ARCore used by the model**.
2. Test a small bounded **fixed-lag SE(2)/SE(3) smoother** over ARCore relative pose increments + GNSS robust fixes with temporal correlation/bias states, not raw IMU double counting. Compare to current `GeoAlignment` and independent Google FLP baseline.
3. Compare time synchronization, observation weights/Huber loss, robust outlier removal, GNSS reacquisition and jump limits. Evaluate potential visual landmarks/manual annotations as optional **independent** user measurements, not ground truth.
4. Report ATE/RPE p50/p95, cross-track offset, heading error, false-safe fraction, time to reanchor, runtime memory and mAh on an S21. Stage/cap performance by confidence **not elapsed time**.
5. Integrate only if held-out measured performance improves with no serious regressions. The factor-graph solver remains a separate module with contract and replay, to keep odometer distance logic unchanged.

## VIO without GPS: policy

No arbitrary GNSS duration limit. If ARCore tracking is fresh, stable, metric and continuous, render a **clearly tagged VIO-only estimate**; increase an **uncalibrated** warning of possible drift with traversed distance and tracking events. On tracking loss/relocalization/restart (no old anchor), hide the overlay and ask for reacquisition; do **not** promise infinite geographic accuracy. In this branch manual slide adjustments are session-scoped overlays only.

### Manual slide as additional input

A finger drag is a **user visual correction**, not a GNSS fix. Only horizontal translation, bounded gesture size, no scaling/rotation, no automatic snap to route or silent GPS rerouting; show active correction and provide Reset. A usable 3D floor remains required for the blue ribbon. Do not persist manual shift across screen lock, tracking reset, different GPX or power cycle without independent relocalization.

Acceptance: safety + UI tests, depth plan unaffected, tracking-loss reset verified, user can turn GPS off after initial alignment, measure geometric drift in real conditions.
