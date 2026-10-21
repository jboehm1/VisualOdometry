> Historical v0.15 plan below. Latest v0.17 evidence: **actual host build and real
> sensor replay passed** at `cf4e5194`, run 39555701592. See
> [host baseline](NATIVE_VIO_HOST_BASELINE_V017.md) and its retained reports.
> N1 host compilation and N2 host execution now have evidence; N1 dependency
> audit, NDK/JNI and phone validation remain open. Java/C++ interface contracts
> have passed CI; they remain disconnected from Android acquisition.

## Additional completed contract work (same isolated branch)

- Native `VioEngine.hpp` C++17 **interface** (not an engine): copied grayscale image buffer, raw per-sensor IMU samples, calibration struct, versioned source/segment/scale/quality pose contract with optional covariance. Its `test_vio_contract.cpp` proves memory ownership / conservative null pose on a dummy engine. CI `.github/workflows/native-vio-contracts.yml` builds/runs this smoke and independently checks commercial license inventory; a successful run does **not** imply EuRoC/TUM-VI accuracy.
- Java gate now additionally requires finite pinhole intrinsics and rigid 4×4 `T_imu_from_camera`, with defensive cloning of its matrix. The flag `verified` remains a human/automated calibration provenance assertion and must never be enabled by assumption.
- `tools/vio/compare_measured_runs.py` + tests creates a **gated comparison of externally measured** ATE/RPE/coverage/p95 latency/CPU/power, with incomparable datasets/hardware kept separate, post-hoc GT-assisted scale/clock fits rejected, and Pareto rather than one invented scalar ‘best’ score. Does not compute or invent these measurements. See benchmark protocol in handover.
- Basalt upstream source SHA pinned in the **prospective** inventory (`0f3b2b52c807f70ff4e2973ce253c73329eea7bc`), but native engine is **not downloaded, built, or integrated**; its transitive dependencies remain unaudited.

# VECTOR VIO — Native VIO + Camera2 · engineering plan (v0.15)

**2026-10-21 — experimental R&D, not a shippable native estimator.**
Parent: `feature/v014-region-gradient-exposure-optflow`, SHA
`7f8da2396e01d930b3c762bf53c04600f2d5956f` (PR #42).
Integration reviewed: `feature/slam-explorer` at
`3d251b8e06536a972b88bcf01bd34912ea5ceabd`.

## Product decision (potential paid/commercial Android app)

Preserve a **useful working ARCore default** while researching an optional Camera2
engine. Commercialization changes the priority order: minimize copyleft/licensing
surprises, require reproducible safety and accuracy evidence, avoid speculative
feature marketing, ship privacy-preserving defaults, and make versioned
integration contracts before rewiring GPX or paid-user journeys.

```text
Production Android                     Research only (isolated)
ARCore -> calibrated AR pose            Camera2 Lab: REAL shutter/ISO control
     |                                      |
Odometer / GPX / PDR                        v
     |                             YUV camera + real IMU
audited fallback                     clock/extrinsics gate [NOW]
                                           |
                                 VIO engine + JNI [NOT DONE]
                                           |
                                 untrusted metric pose candidate
                                           |
                                     pose quality gate [NEXT]
                                           |
                         segmented/local pose -> GPS alignment [LATER]
```

Camera2 Lab currently uses a scaled `TextureView` bitmap for photometric feedback;
this is **not** a lossless or calibrated `ImageReader YUV_420_888` SLAM source.
No OpenVINS/Basalt/Kimera pose is calculated inside the shipped app yet.
ARCore and the Camera2 lab must not concurrently claim the rear camera.

## Implemented in this isolated branch

- `nativevio.NativeVioInputGate`: immutable calibration ID, camera ID,
  image dimensions, camera-to-IMU time offset and explicit **verified** flag;
  only accepts Camera2 REALTIME-sourced matched capture timestamps.
- Bracket of gyro and accelerometer BOOTTIME timestamps must contain the
  **mid-exposure corrected time**. Reject unknown clocks, unverified calibration,
  mismatched camera/crop/version, non-monotonic frames, impossible exposure and
  insufficient IMU. After >1 s camera gap, **start a new acquisition segment**.
- Pure JVM JUnit tests cover acceptance, calibration fail-closed, missing sensors,
  duplicates, exposure bounds, segment gaps.
- `tools/vio/third_party.json` and `release_license_gate.py`: versioned initial
  inventory + testable release **blocker**, not legal sign-off and not an APK SBOM.
  Current inventory explicitly cannot pass release mode without reviews.

**Non-implementation:** Gate is not attached to `Camera2ManualActivity` or
`MainActivity`, and has no C++ engine yet. Successful JVM/Android build
is only evidence for these contracts.

## Timing contract

Camera2 `SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME` indicates timestamp alignment
with `SystemClock.elapsedRealtimeNanos()`. If source is UNKNOWN, no data enters
native fusion. The `SensorEvent.timestamp` stream uses the same BOOTTIME basis
in the documented platform contract; still verify this on the actual device.
`CaptureResult.SENSOR_TIMESTAMP` and the `Image.getTimestamp()` **must match
the same captured image**; sequence matching and camera HAL behavior must be
tested in the forthcoming YUV collector.

```text
t_frame_start (camera clock) ---- exposure duration ---->
                                ^
                        mid-exposure + calibrated offset
                                ^
gyro(t-δ) ------------ gyro(t+δ)
accel(t-δ) ---------------- accel(t+δ)
```

The 100 ms maximum bracket *span* is an engineering sanity threshold, NOT
proof of accurate calibration, rolling-shutter correction, or correct
interpolation. A real VIO port must interpolate samples at the correct
timestamps and properly account for distortion, focal changes, extrinsic
rigidity and rolling shutter. `verified=true` must only be supplied by a
separate calibrated and documented source, never to force tests green.

## Candidate engine decision (provisional, not a build result)

| Engine | Declared upstream license | Android/commercial risks | Current decision |
|---|---|---|---|
| Basalt | BSD-3-Clause; **its third-party modules may differ** | Large native build, CMake, Pangolin/other dep stripping, compiler + NDK proof | **First host prototype candidate**, NOT pinned/built yet |
| Kimera-VIO | BSD (MIT-SPARK institute, **not MIT license**) | GTSAM, OpenGV, DBoW2, OpenCV, Kimera-RPGO, native footprint | Independent challenger |
| OKVIS2 | BSD-3-Clause claimed upstream; verify repository files | Ceres/SuiteSparse and transitive licenses; device CPU | Alternative |
| ORB-SLAM3 / OpenVINS | GPL variants, check official license texts | Copyleft obligations for proprietary APK | Exclude from packaged product unless separate compatible rights |

**No complete transitive license inventory or source pin for a native engine
yet.** Thus neither Basalt nor Kimera is approved for redistribution.
The existing proprietary/terms-governed ARCore Android binary is also
subject to Google ARCore's terms and user disclosure obligations. Consult
`COMMERCIAL_RELEASE_GATES.md`.

## Work gates, not fabricated percentages

| Gate | Objective | Status (2026-10-21) |
|---|---|---|
| N0 | Git/coordination, isolated branch, no shared Android changes | DONE |
| N1 | Pin+audit all native engine dependencies; **actually build** native engine on host | NOT DONE (network to Git host unavailable in this runtime) |
| N2 | Run the exact native engine on a real dataset with GT | NOT DONE |
| N3a | Pure Java input timing/calibration gate + unit tests | CODED; CI pending |
| N3b | Complete C++ interface + lifecycle/pose contract | NOT DONE |
| N4 | arm64-v8a NDK build + actual native .so in APK | NOT DONE |
| N5 | ImageReader YUV+IMU live capture with matching CaptureResult | NOT DONE |
| N6 | Live metric native poses, provenance/quality/export | NOT DONE |
| N7 | VIO-aware exposure quality A/B, measured vs fallback | NOT DONE |
| N8 | Safe engine switch/rebase/GPX continuity | NOT DONE |
| N9 | Real S21 walking/bicycle/low-light, battery/thermal and GT | NOT DONE |
| N10 | Commercial release security/privacy/license checks | NOT DONE |

## Integration / validation conditions

1. Do not touch `MainActivity.java`, manifests, GPX, PDR, existing workflow
   semantics in this phase. A PR is stacked on #42; integrate ancestors in order.
2. Product default stays ARCore. Never enable native VIO in Settings until its
   engine, calibration, camera lifecycle and pose quality pass end-to-end gates.
3. Pose needs engine/version, segment/world ID, metric-scale status, tracking
   status, optional *unknown* covariance, and continuous monotonic clock.
4. Preserve capture-vs-display FPS distinction. Export engine-specific logs
   separately from ARCore and GPS measured fixes. Never use ground truth
   to adjust estimator inputs or disguise reanchoring errors.
5. Test against device loss of camera permission, app background/resume, light
   saturation, high-speed optical-flow aliasing, timestamp mismatch and CPU
   overload. Fail closed; do not bridge missing distance with made-up points.
6. Do not distribute raw public datasets, bundled training weights, or library
   binary artifacts without a documented distribution right.

## Verifiable runs

- JVM unit test location: `app/src/test/java/dev/jboehm/vectorvio/nativevio/`.
- Standard CI when PR opens: `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- License inventory structural check: `python3 tools/vio/release_license_gate.py tools/vio/third_party.json`.
- Release check is **EXPECTED TO FAIL** today:
  `python3 tools/vio/release_license_gate.py tools/vio/third_party.json --release`.
- No host Basalt binary/benchmark or Galaxy S21 data was acquired in this session.
  The connector can push GitHub files but this runtime has no network DNS route
  for `git clone`; do not misreport builds that have not run.

### Primary reference links

- [Camera2 timestamp source](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#SENSOR_INFO_TIMESTAMP_SOURCE)
- [ARCore developer terms](https://developers.google.com/ar/develop/terms)
- [Google ARCore Android SDK license](https://github.com/google-ar/arcore-android-sdk/blob/main/LICENSE)
- [Basalt](https://gitlab.com/VladyslavUsenko/basalt)
- [Kimera-VIO](https://github.com/MIT-SPARK/Kimera-VIO)
- [OKVIS2](https://github.com/ethz-mrl/okvis2)

**Proof hygiene:** JUnit guard != live VIO. Image gradient/patch flow !=
SLAM. Camera2 lab shutter != ARCore shutter. Proprietary-use planning != a
completed legal license review.

### Measured comparison integrity refinement

The offline scoreboard now emits schema 2, requires explicit truth-access declarations and full report/protocol hashes, preserves CPU tradeoffs and distinguishes identical-raw grouping from explicitly paired repeated-route cohorts. It validates declarations, not actual source bytes or scientific truth. See [v2 contract](work/MEASURED_REPORT_CONTRACT_V2.md). No native VIO engine or JNI has been added by this work.

### Input session-state correction
Rejected captures no longer mutate segmentation or erase the last accepted timestamp. Both capture start and corrected mid-exposure must be monotonic. Three new JUnit cases and a compiled exact-Java before/after probe reproduce the fix. See [audit](work/NATIVE_INPUT_STATE_AUDIT_20261021.md). The guard remains disconnected from acquisition and produces no VIO poses.

### v0.17 actual host-build workstream
An isolated descendant of PR #44 now attempts the pinned Basalt source build and EuRoC V1_01 real stereo/IMU replay. Inference cannot access GT; scores are produced afterwards, with no scale fit. See [host baseline](NATIVE_VIO_HOST_BASELINE_V017.md). Host build and real replay passed at `cf4e5194` (run 39555701592); see the baseline report for exact metrics, caveats and input identity. JNI/Android and license release remain blocked.
