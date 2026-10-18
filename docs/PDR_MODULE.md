# VECTOR VIO — camera-free PDR module / development & validation manual
Status: Android foreground working design, pending Android CI and real S21 measurements.
Date: 2026-10-18. Base: feature/slam-explorer. Branch: feature/camera-free-pdr.

## 1. Decision and integration
Integrate directly in the **same Android app**, not in a separate repository, but isolate
the new engine in the Java package `dev.jboehm.vectorvio.pdr` and a feature branch.
The existing ARCore Odometer, GPS Routes and training/simulation code are unchanged.
The main screen DATA menu opens the new activity. This is a genuinely camera-free
mode: it never initializes ARCore or requests camera permission.

Graph:

    Android accelerometer / STEP_DETECTOR / ROTATION_VECTOR / magnetometer / pressure
       |       optional LocationManager GNSS course
       v                             |
    PdrSensorBridge              quality gate
       |                             |
       +------------- PdrEngine <----+
                           |
           distance (steps x calibrated stride)
           local EN trajectory only with available heading
           approximate pressure elevation
                           |
               PdrActivity + PdrTrailView
                           |
                 PdrTraceWriter -> ZIP
                           |
                  PdrReplay -> evaluation

### Interfaces
- `PdrEngine` pure Java 17, deterministic replay without SDK, Android, ARCore or ML.
- All sample and step times are Android elapsedRealtimeNanos, monotonic since boot.
- Device coordinates: Android sensor axes. Engine heading: radians clockwise from
  magnetic/Android north. Output trajectory is **local East/North metres**.
- **No global georeferencing and no certified position accuracy.**
- GNSS bearing is accepted only if speed >=1.2 m/s, horizontal accuracy <=20 m,
  reported bearing accuracy <=25°, and recently sampled. GNSS fix positions do not
  adjust the PDR distance or silently warp the trajectory.
- When no heading exists, the engine still counts distance but labels steps
  without heading. It must not invent a position.
- Default stride 0.74 m is only a starting guess; user must calibrate stride:
  `reference_distance_m / counted_steps`.
- STEP_DETECTOR needs ACTIVITY_RECOGNITION on Android 10+. Permission denied or
  sensor missing -> raw magnitude heuristic fallback, clearly marked.
- Rotation vector heading estimates **top-of-phone direction**, not body heading.
  No model of arbitrary pocket/hand orientation is implemented.
- Pressure altitude is relative, affected by weather and indoor pressure.

## 2. How to run on Galaxy S21
1. Install the debug APK from the GitHub Actions Android workflow artifact.
2. Open DATA -> PDR · NO CAMERA. You do **not** need camera permission.
3. Hold phone upright top edge toward walking direction, press START, allow physical
   activity permission for hardware step detection (fallback otherwise).
4. Optionally enable GNSS ASSIST (location permission) to use its reliable course,
   but **not** GNSS path-distance as a hidden replacement for steps.
5. Walk a measured 100 m, STOP, compute metres per detected step, enter STEP length.
6. Repeat 100 m straight, 400 m loop, 1 km walk, 1 km run, pocket-change scenario.
7. STOP -> EXPORT ZIP via Android Documents picker. Foreground only: going to
   background terminates the active segment and keeps it ready for export.

ZIP files contain `events.csv`, `raw_sensors.csv`, `path.csv`, `metadata.txt`.
All recording stays private on phone until the user chooses an export destination.
Recordings are *not* uploaded or committed to Git automatically.

## 3. Testing pipelines, implemented
### Fast deterministic CI
- `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` (same existing CI).
- Pure Java replay (no Android SDK): `bash tools/pdr/run.sh`.
- Fixture gate: `bash tools/pdr/run.sh > /tmp/pdr.json && python3 tools/pdr/check_fixture.py /tmp/pdr.json`.
- JUnit: missing heading, known 90-degree heading, GNSS rejection/expiry,
  pause/duplicate filtering, relative pressure, synthetic peak detection.
- Python stdlib unit tests: `python3 -m unittest discover -s tools/tests -v`.
- Every PR triggers Android workflow. APK artifact is a debug-signed sideload APK,
  not a verified physical-device release.

### Real S21 replay (same engine as on phone)
Extract session ZIP, then:
```bash
bash tools/pdr/run.sh path/to/events.csv
# Emit trajectory to compare with an independently measured local EN truth:
d=$(mktemp -d)
javac -d "$d" app/src/main/java/dev/jboehm/vectorvio/pdr/{PdrEngine,StepPeakDetector}.java tools/pdr/PdrReplay.java
java -cp "$d" dev.jboehm.vectorvio.pdr.PdrReplay path/to/events.csv /tmp/pdr_path.csv > /tmp/pdr_summary.json
python3 tools/pdr/evaluate.py --pred /tmp/pdr_path.csv --truth /path/to/independent_ground_truth.csv --reference-distance-m 100
```
Ground truth must use identical monotonic clock origin and local East/North
axes, and span the full predicted trace. This does not optimize yaw, scale
or translation beyond subtracting the initial truth origin. If ground-truth
clock or axes cannot be verified, no ATE claim is permitted. A surveyed
reference distance alone can validate **distance** but not trajectory.

### Research-dataset adapter (no fabricated fields)
```bash
python3 tools/pdr/convert_accel.py /DATASET/accel.csv /tmp/real_accel.csv \
  --skip 1 --time-column 0 --xyz-columns 1 2 3 --time-unit s --accel-unit mps2
bash tools/pdr/run.sh /tmp/real_accel.csv
```
Verify each dataset's exact CSV layout and original units before using those
flags. Research IMUs lack Android STEP_DETECTOR events: the accelerometer
fallback must be evaluated separately, without inventing ground-truth heading.
A distance-only baseline is not an inertial trajectory benchmark.

## 4. Datasets and open-source R&D shortlist (sources checked 2026-10-18)
| Source | Benefit | First integration | Limit |
|---|---|---|---|
| [OxIOD](https://deepio.cs.ox.ac.uk/) | pedestrian IMU, runs, phone-carry variations, 1.01 GB download | raw accel adapter and held-out distance/position metrics | exact axis, clock, reference and license review before automated download |
| [RoNIN](https://ronin.cs.sfu.ca/), [code](https://github.com/Sachini/ronin) | 42.7 h, 100 users, body heading vs handheld phone | compare trained neural displacement/heading model separately | partial release; code GPL-3.0; no silent code copy into app |
| [TLIO](https://github.com/CathIAS/TLIO) | learned displacement + filter, public data and retraining code | offline benchmark and uncertainty study | BSD code; dataset conditions separate; network must be retrained |
| [RIDI](https://github.com/higerra/ridi_imu) | historical PDR baseline | regression after OxIOD | original authors recommend RoNIN instead |
| [ADVIO](https://github.com/AaltoVision/ADVIO) | 23 real-phone sequences, stairs, indoor/outdoor | real sensor stress test | CC BY-NC 4.0 data; truth partly IMU reconstruction; not fully independent for inertial accuracy |
| [EqNIO](https://github.com/RoyinaJayanth/EqNIO) (ICLR 2025) | equivariant neural inertial odometry | research A/B only | training and deployment required |
| [Air-IO](https://github.com/Air-IO/Air-IO) (2025) | learned IMU feature observability | future generalization comparison | targets UAV dynamics, not drop-in pedestrian algorithm |

The dataset catalog deliberately has **no auto-downloads**. Licensing, consent
and hashes must be recorded before mirroring multi-GB data to local disk or Drive.
Use `PDR_DATA_ROOT` for local or Drive-synced data; never push datasets or recordings
to GitHub. CI tests the lightweight synthetic fixture only. Larger real-data
benchmarks are a separate reproducible job on local WSL / research runner.

## 5. Acceptance gates and honest progress
| Gate | Status | Required evidence |
|---|---|---|
| Sensor-agnostic Java core + replay | IMPLEMENTED, not measured | JUnit + synthetic fixture |
| Live Android no-camera UI | IMPLEMENTED, device verification pending | APK, start/stop/export on S21 |
| Step permission + fallback + magnet/rotation/pressure | IMPLEMENTED, calibration pending | sensor inventory and 100 m measured course |
| GNSS course-only optional correction | IMPLEMENTED, not measured | walking accuracy/speed gating recordings |
| Foreground lifecycle and ZIP export | IMPLEMENTED, device verification pending | background/permission/export tests |
| OxIOD/RoNIN/TLIO adapters with heading & ground truth | NOT DONE | source-specific adapters + licensing + held-out reports |
| Learned body heading model deployable on Android | NOT DONE | model conversion, runtime budget, robust held-out results |
| Formal uncertainty EKF / loop closure / map matching | NOT DONE | calibrated measurement covariance, maps, dedicated evaluation |
| Release quality (<2% walk distance etc.) | NOT VALIDATED | surveyed courses, multiple phone poses, runners, dates |

### Strict failure rules
- Do not treat PATH distance (steps with heading only) as total PDR distance if
  steps lacked heading; benchmark total distance using summary.json steps and
  calibrated stride.
- Do not infer inertial position accuracy from synthetic step fixtures.
- Do not use Apple/iPhone/GNSS coordinates as an independent physical ground truth
  without reviewing how they were obtained.
- No GNSS -> no guaranteed north reference if magnetometer is disturbed.
- No camera + arbitrary phone handling -> body heading is generally unobservable
  without a learned heading model or extra constraints.
- The activity deliberately stops when backgrounded: foreground service remains
  future work for uninterrupted screen-off tracking.
- UI path may be unreliable when phone orientation changes; show uncertainty
  qualitatively rather than unearned metres of statistical confidence.

## 6. Next highest priority
1. Verify Android CI (compile/lint/JUnit) and test APK on S21 hardware.
2. Add sensor inventory + measured step detector vs fallback comparison.
3. Add independent 100/400/1000 m traces for walking, running, phone in pocket,
   device in hand, and constrained corridors. Split by person, not by near-duplicate windows.
4. Train/evaluate RoNIN/TLIO/EqNIO offline with licensed datasets, preserving an
   untouched test set and explicit baseline numbers.
5. Integrate learned heading/displacement only if demonstrably superior on S21.
6. Add ENU georeference and probabilistic fusion only after clock, frame, bias and
   GNSS course quality validation.
