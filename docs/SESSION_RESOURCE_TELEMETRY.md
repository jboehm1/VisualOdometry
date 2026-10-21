# Session resources and the two user-facing candidates

## Decision

Only two modes are selected for user field tests: **ARCore with camera** and
**Java PDR without camera**. They solve different availability conditions;
PDR is not an interchangeable visual-inertial SLAM engine. No automatic
ARCore/PDR switching or new native Android engine is introduced here.

Basalt is an existing open-source C++ visual-inertial odometry/mapping library.
Our pinned build actually inferred poses on a short EuRoC stereo window.
The VO/VIO ABBA comparison establishes the effect of its IMU switch, **not**
an advantage against ARCore, a mono rolling-shutter phone result, or an S21
resource qualification. It remains an internal research challenger.

Do not send the user ten estimators. Before proposing a third field-test mode:
require a reproducible arm64 build, sensor/calibration compatibility, multiple
untuned phone sequences with independent reference, an ARCore comparison with
matched inputs or paired controlled runs, and credible device resource costs.
Freeze the acceptance rule before those trials. Promotion needs a useful
precision/robustness/resource or camera-control advantage, not a small gain
on one stereo dataset. No evidence currently establishes a globally best engine.

## What is recorded

Both selected modes share `SessionTelemetry` and the same 62-column CSV.
Resources are sampled at 1 Hz on a dedicated worker, PSS at 5 s, and thermal
headroom at no more than one probe per 10 s. GL and sensor callbacks update
atomic counters and fixed-size histograms; resource probes never run on GL.
Start, pause, resume, backgrounding and reset bound active intervals. Paused
CPU/battery use is excluded from interval totals. Diagnostics do not change
camera settings, sampling targets, pose filtering, step/stride or heading.

| Group | Observations | Interpretation |
|---|---|---|
| Provenance | App/device/SDK, engine/config, AR service version when visible; CI source/build commit/tree | Exact binary origin is embedded by Android CI; local builds can lack it |
| CPU | Process CPU ms, one-core percent, available-logical-core capacity percent | 100% core = one logical core; process includes app UI, mapping, telemetry and in-process AR; excludes external services |
| Memory | Java used/committed/limit, native heap allocation, process RSS, PSS, private dirty, threads, available system RAM/low-memory flag | Cached PSS includes age; RSS/PSS/native heap overlap, do not add them |
| Battery | Level, voltage, battery temperature, status, plugged/health, current now/average, charge and energy counters | Device properties can be unsupported; battery temperature is not CPU temperature |
| Energy | Signed discharge terminal-power estimate from current × voltage | Whole device, not app-attributed energy; charging is not zero consumption |
| Thermal | Android thermal status, current headroom and age, power saver/interactive state | Headroom is a relative throttling indicator, not degrees Celsius |
| Other resources | UID RX/TX bytes, own-process `/proc/self/io` logical/physical counters, screen brightness setting/mode | UID and device scope differ from process scope; brightness setting is not measured nits; restricted proc paths stay missing |
| Acquisition | Accelerometer/gyro/rotation callback counts and callback-lag histogram, accepted PDR steps | Observed callbacks, not independently measured lost samples; no sensor/AR camera-clock equivalence assumed |
| Tracking | Unique/tracked/untracked camera frames, duplicates, loss/recovery transitions and failure-reason counts, exported poses | Frame denominator excludes unseen missing frames; not temporal tracking coverage or physical position accuracy |
| Timing | ARCore update-call wall duration, camera frame period, GL draw-submit wall duration, window frame/draw timings | Distinct update/presentation rates; host wall timings are neither GPU completion nor photon-to-pose latency |
| GPU | Android window GPU duration if API >=31 and reported, count of valid samples | Window/HWUI only; separate SurfaceView GL workload is excluded. No portable full GPU utilization/frequency/temperature reading |
| Instrumentation | Collector wall time, probe ages, dropped window-metric callbacks, status/errors/missing reasons | The collector has a cost and its CPU is included; device overhead still needs a measured on/off comparison |

Unavailable CSV values remain empty. JSON lists explicit unavailable reasons;
no restricted GPU/temperature value is presented as measured zero. Histograms
have fixed millisecond upper bounds; p50/p95 reports are bucket bounds. The
last bucket is overflow and cannot be converted to a finite latency.

## Export and compare

- ARCore: **EXPORT → Current session — complete ZIP (metrics + IMU + optional video)**.
  CSV-only export intentionally retains the old trajectory CSV and excludes
  resource files. ZIP includes `<session>_resources.csv` and metadata JSON.
- PDR: stop and export its ZIP; it includes `resources.csv` and
  `resources_metadata.json` alongside unchanged replayable events/raw/path.
- No user sensor/image data is uploaded automatically. Resource files remain
  inside the session's existing app-private storage and chosen export ZIP.
- Session settings are recorded at segment creation, alongside existing event
  logs for later changes. Match display/video/torch/GNSS settings during tests.

Offline report (accepts either complete ZIP format):

```sh
python tools/telemetry/report.py arcore.zip pdr.zip --out comparison.json
```

The report excludes pause gaps, calculates process CPU from deltas, exposes
resource distributions, frame-count tracking fraction, callback rates and
latency histogram bounds. Whole-device discharge energy is integrated only
between adjacent, unplugged, nonnegative-discharge observations <=3 s apart;
coverage is reported. Missing/charging intervals are excluded, never assigned
zero energy. There is no automatic overall winner or accuracy score.

Use one measured route and one fixed phone mount, screen settings, video/torch
configuration and similar start temperature. Repeat ARCore/PDR/PDR/ARCore,
export after each, and label the true route distance and relevant incidents.
Keep uncalibrated fallback-stride or heading assumptions visible. A measured
distance validates distance error; full trajectory ATE needs independent,
synchronized positioning. Very short trials cannot resolve battery-percent
differences well. Energy and camera-based SLAM/PDR differences must be judged
as complete modes, not isolated estimator kernels.

## Primary sources

- https://developer.android.com/reference/android/os/BatteryManager
- https://developer.android.com/reference/android/os/PowerManager
- https://developer.android.com/reference/android/view/FrameMetrics
- https://developers.google.com/ar/devices
- https://gitlab.com/VladyslavUsenko/basalt

Robolectric/fixture tests verify lifecycle, units, missing data and exports;
they do not prove S21 sensor availability, energy accuracy or field precision.
