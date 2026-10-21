# Basalt host baseline v0.17 — source build and real sensor replay

Branch `work/native-vio-basalt-host-20261021`, based on PR #44 at
`513bb614cd7aedd0649b1242d339fc2bc72fb8ba`. Android remains unchanged.

## Verified result — 21 October 2026

Actual upstream compilation and isolated real-sensor replay **PASSED** in
[run 39555701592](https://github.com/jboehm1/VisualOdometry/actions/runs/39555701592)
at source `cf4e51943c439db12b3d0693dea15e58feb1776b`.
[Durable result and identity files](work/basalt-host-20261021/receipt.json)
retain the seven original small evidence files, including actual estimated poses.
No original images, IMU/reference CSV or native binaries are retained.

| Observation | Measured result |
|---|---:|
| Input window / exported body poses | 59.95 s / 1200 |
| Real IMU samples / timestamp matches | 12091 / 1179 |
| Scorable reference duration | 58.91 s |
| Coverage of that reference interval | 99.983% |
| ATE RMSE, first-reference-pose gauge | 0.22855 m |
| ATE RMSE, full posthoc rigid SE3 | 0.036887 m |
| 1 s translation RPE RMSE | 0.031025 m |
| 1 s rotation RPE RMSE (attitude caveat) | 0.52606 degrees |
| Observed sparse path, reference / estimate | 18.7141 m / 18.4314 m |
| Observed path distance difference | -1.5106% |
| Engine wall / child CPU duration | 18.537 s / 59.633 s |
| Child peak RSS on Linux | 75992 KiB |

The coverage denominator is the **available 58.91 s reference interval**, not
the full 59.95 s sensor window. Neither alignment fits metric scale (factor 1).
First-pose and rotation errors retain the original V1_01 attitude caveat. One
short hosted run is not a phone accuracy, speed, repeatability or power claim.
The `--num-threads 2` flag limits upstream TBB parallelism; it is not a proven
cap on every process thread. CPU model, energy and online latency were not
measured. Do not rank this against ARCore or physical-device runs.

Local evidence checks passed: ZIP SHA/CRC; 1200 finite, monotonic exported
poses; binary and shared-library identities agree between build/runtime/score;
trajectory and staged-tree digests agree; source changes are empty. Installed
copyright-file hashes cover 96 dependency records, with legal review still open.

The original build receipt retains a historical image-policy label describing
first acquisition. For this successful run the workflow already pinned the
exact digest below; the workflow and recorded image identity are authoritative.
Upstream also unconditionally calls its internal ATE evaluator after inference;
without reference, its diagnostic is not authoritative and is never used here.
The exported poses are scored independently after the container exits.

## Acceptance gates

1. Compile the actual Basalt `basalt_vio` target from pinned upstream source,
   not a dummy engine or a supplied trajectory. Record binary and dependency IDs.
2. Feed real EuRoC V1_01 stereo PNGs and accelerometer/gyro with upstream EuRoC
   calibration/config. Select the first 1200 camera frames before looking at GT.
3. During inference, mount only staged sensors, engine/toolchain and an empty
   output directory. Container has read-only root and no network; raw archive,
   reference, project repository and scoring code are not mounted.
4. Only after inference, read the IMU/body-frame reference and score actual
   native poses using the existing `tools/benchmark/bench.py` metric functions.
5. Require >=200 timestamp matches, finite metric ATE and >=50% selected-window
   time coverage. No accuracy threshold tuned to this trial. Report errors even
   if disappointing; this gate proves execution/coverage, not commercial accuracy.

## Identities and cost limits

- Basalt source: `0f3b2b52c807f70ff4e2973ce253c73329eea7bc`.
- vcpkg gitlink: `1e199d32ad53aab1defda61ce41c380302e3f95c`.
- Upstream registry baseline: `05442024c3fda64320bd25d2251cc9807b84fb6f`.
- Tooling image is fixed after its first observed acquisition to
  `vladyslavusenko/basalt-ci@sha256:3553facf6bf25baf283547fab7fad0dd74d47ba79fb9a9c9f9c11fb777d3a502`.
  This identity pin is not a transitive security or licensing audit.
- Release-only static dependency triplet, float estimator instantiation, generic
  x86-64, two compiler/engine threads, 90-minute job cap and 15-minute replay cap.
- Dataset acquisition bounded to 2 GiB compressed / 8 GiB expanded. Mirror source is pinned to commit `d05bd6a92a5f456562153f83d65805fea27cb57d`,
  SHA256 `a920fe5b5e69a6ad19b32f1cfaf90ac2f59d45dfd1ca18cc9722e07684ba45fa`,
  1,149,702,102 bytes. Integrity is verified against mirror metadata, not an
  independently authenticated institutional original. Original data and native
  binaries stay temporary; upload only small poses/reports/identity metadata.

## Scoring distinctions

The EuRoC state CSV uses **wxyz** quaternion order. Convert explicitly rather
than silently interpreting it as the generic evaluator's xyzw format.

- Primary diagnostic: use only the first matched body pose to fix world gauge
  AFTER inference, then no further alignment or scale fitting. This is not an
  independently presurveyed production alignment and is named accordingly.
- Secondary diagnostic: full-trajectory rigid SE3 alignment, no scale fitting,
  explicitly post-hoc. Never pass it into the strict production A/B scoreboard.
- Preserve IMU/body frame; do not compare camera-centered poses against IMU GT.
- Collect child CPU time, wall duration and peak RSS inside the engine container.
  Energy, online latency, thermal behavior and Android performance remain unknown.

Six synthetic adapter tests pass locally: stereo/copy limits, no reference read,
unsafe paths/symlinks, IMU brackets, reference quaternion order, gauge and scale
preservation, unsuccessful/non-isolated receipt rejection. They do not prove that
the real C++ engine has been built. The separate successful source build and
real replay above establish those execution gates, while licensing remains open.

## Licensing and product scope

Upstream Basalt LICENSE is BSD-3-Clause. vcpkg dependencies and the tooling image
need a complete transitive review; recorded license-file hashes are not approval.
No third-party binary or source dataset is added to the APK or uploaded artifact.
Commercial release gate stays closed. NDK/JNI, live YUV capture, calibrated S21
camera–IMU timestamps, native/ARCore switching and field validation remain open.

Reproduce the workflow steps in `.github/workflows/basalt-host.yml`; the adapter
is `tools/vio/basalt/euroc_adapter.py`, inference wrapper `container_run.py`.

## First actual CI finding and acquisition adjustment
Official ETH V2_01 download timed out after 60 seconds in run 39554931508, before native build or any accuracy result. The next attempt uses the pinned HF V1_01 archive above (Git LFS metadata verified). This is an availability-driven sequence substitution, not a result-driven choice. Original V1_01 reference attitude is documented as inaccurate by OpenVINS; orientation and first-pose-gauge results are explicitly diagnostics, not authoritative field accuracy. Source: https://github.com/rpng/open_vins/blob/master/docs/gs-datasets.dox . Raw sources remain excluded from artifacts.

## Second actual CI finding
Run 39555347370 verified the 1,149,702,102-byte mirror archive, staged 1200 real stereo frames and IMU, fetched exact upstream source and pulled its tooling image. Configuration then failed because shallow vcpkg lacked the registry baseline commit. Restore full vcpkg history and verify `05442024...:versions/baseline.json` before configuration, as upstream CI does. Tooling image is now fixed to digest `sha256:3553facf6bf25baf283547fab7fad0dd74d47ba79fb9a9c9f9c11fb777d3a502`, observed in that run. No engine poses or accuracy result yet.
