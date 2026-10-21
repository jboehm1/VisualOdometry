# S21 PHONE-FIRST: implementation and reproducibility record

**Date:** 2026-10-22. **Implementation branch:** `feature/s21-sensor-quality-lab-20261022`. **Parent pinned:** `2d89a90484622dbfbd8f8c778247f0a90909b302` (PR #48 including PR #47 head `eb4ef55`). Git coordination & AGENTS read. Changes isolated from Camera2 native JNI, PDR core, training weights and benchmark central workflows.

## What actually runs in Android
1. **Settings → GPS & IMU → Sensor Lab** (new, foreground-only activity). Probe hardware without opening camera, scanning wifi, querying location or requesting new permissions. Reports S21 runtime `Build.MODEL`, every advertised SensorManager device, min/max reporting delays,  Camera2 module IDs, hardware levels, exposure/ISO ranges, YUV sizes, clock info; presence does **not** imply benchmark quality or simultaneous availability.
2. **START SENSORS / STOP** live accelerometer, gyroscope, uncalibrated variants, pressure, magnetometer, light, Android fused gravity/game rotation if available. **Raw `SensorEvent.timestamp` plus elapsed arrival timestamps, actual delivered Hz and values**. Bounded 180,000-event session, app-private CSV. Pausing activity *stops* the listener and ends the run.
3. **EXPORT ZIP** via Android Storage Access Framework opt-in: `capabilities.json`, `sensor_samples.csv`, `summary.json`, measured per-sensor sample count/rates. No upload, no camera permission required. Raw pressure and magnetic vectors can be location-sensitive; review before sharing.
4. **Active ARCore session diagnostics** acquire pressure, compass vector and ambient light at low rate. Writes `_phone_environment.csv` at ~1Hz with **sensor age**; does not couple these readings as fake global fixes. Existing ARCore/GPX/PDR and Camera2 exposure controls remain functionally unchanged.
5. **VisualObservabilityGate** consumes actual ARCore `TrackingState`, current IMU timing and existing CameraCaptureQuality (shutter ISO gyro smear); opportunistic CPU image texture flag. Reports `VISUAL_OBSERVED` (means ARCore tracking, not independent GT), `VISUAL_CAUTION`, `IMU_ONLY_NO_POSITION` for at most 1.5s after last visual pose if IMU fresh, then `POSITION_UNKNOWN`. `IMU_ONLY_NO_POSITION` means **gyroscope data is fresh but no orientation or translation pose is computed**. Starts UNKNOWN, resets on pause/new EGL surface, handles duplicate and nonmonotonic clocks fail-closed. 1Hz sidecar `_visual_observability.csv` includes source, image quality, ages and diagnostic reason. HUD displays a separate visible quality label; it does **not** secretly filter/modify ARCore odometry, GPX, maps or path sampling.

## Testability, status and honest limits

| Workstream | Done | Blocking evidence |
|---|---|---|
| Runtime device probe + local export | Java source, Android menu/manifest, Robolectric tests | No physical S21 execution yet; Camera2 capability list needs actual permission/config |
| Pressure/magnetometer/light concurrent with ARCore | Java monitor & independent sidecar | No calibrated field fingerprint/map/floor inference |
| Camera tracking + IMU fail-closed rule | Java gate, JUnit failure scenarios, HUD+sidecar | heuristic is not error covariance; does not calibrate ATE/RPE |
| Dark/low-texture/no-camera conditions | Quality gate & tests for no tracking, stale IMU and blur | NOT a real smoke-penetration sensor or field validation |
| ARCore vs Basalt exact paper 2025 / exposure control | Preserved existing PR #47 Camera2 experiment and Basalt path | Real S21 A/B with identical calibrated motion/GT still needed |
| XFeat / EqNIO / TLIO / MARIO / ZipDepth integration | **NOT embedded**, candidates and licensing documented in PR #48 | No vetted compatible weights/artifact signature/Android runtime/portability or held-out phone metrics. RoNIN official weights are research-only. Cannot claim an improvement |
| Additional hardware | **NOT included** | Radar, LWIR, foot IMU, anchors deliberately separate; no rescue or evacuation feature |

**No GPU model runtime dependency was added.** The existing `PortableInertialModel` already supports a SHA-256-verified, tiny MLP *diagnostic* checkpoint for PDR; we do not relabel it as EqNIO/TLIO. To integrate actual learned models, implement proper input preprocessing and coordinate-frame contracts for each, benchmark on same raw S21 logs against GT, select only models with distributable weights, and keep any inference diagnostic until validation.

## Host replay bridge: reuse the actual Java PDR engine

The sensor lab ZIP can now feed **the existing PdrEngine exactly**, rather than an unrelated Python dead-reckoning proxy:

```sh
# Unzip an explicitly exported Sensor Lab run into a local private directory.
python3 tools/phone/sensor_lab_to_pdr.py sensor_samples.csv \
    --stride-m 0.74 --out /tmp/s21-events.csv
bash tools/pdr/run.sh /tmp/s21-events.csv > /tmp/s21-java-pdr-summary.json
# Tracking availability diagnostics from a separately exported real ARCore bundle:
python3 tools/phone/assess_observability.py vector_vio_..._visual_observability.csv \
    --source PHONE_UNVERIFIED --output /tmp/s21-tracking-availability.json
```

The **0.74 m is an EXAMPLE**; users must replace it with externally measured stride length. Converter emits **only real accelerometer and pressure measurements**, sorted by `SensorEvent.timestamp`, plus explicit START/stride setting. It emits **no STEP, GNSS, magnetic heading, body pose, velocity, position or independent ground truth**. The Java `PdrEngine` is still the source of estimated steps/distance and `DISTANCE ONLY` must remain the safe default when heading is missing. Each converter output has SHA256 and input provenance JSON. `assess_observability.py` reports device-tracking availability (not actual ATE/RPE), excludes gaps greater than 2 seconds and refuses any inertial translation claim.

**CI host tests** added under `tools/tests/test_phone_observability.py` and `tools/tests/test_sensor_lab_to_pdr.py`; existing Android workflow automatically invokes `python -m unittest discover -s tools/tests` before Gradle.

## Safety/consistency guards

- Gate only emits observations: ARCore currently reports pose but could still drift. If camera opaque or lost, **no inferred obstacle map or safe exit**.
- Barometer/magnetometer are **auxiliary telemetry**, not maps, position fixes, true heading in ferromagnetic environments or floor detection without calibration.
- Walking learned PDR does not infer bicycle speed in camera-free GNSS-denied conditions.
- No special responder hardware or certification. Never deploy for firefighting, search/rescue or evacuation.
- Capturing raw signals begins on explicit START; rendered UI does not fake measurements at unavailable rates; stop on background; no background service.
- Screenshot + CI generated Android APK are **build validation**, not physical S21 qualification.

## How to evaluate

1. Install the new debug APK; back up/export existing sessions before sideloading if app's debug signing changed.
2. Settings → GPS & IMU → Sensor Lab → PROBE DEVICE, then START; walk a short indoor corridor with permission and log 1–2 minutes; STOP → EXPORT ZIP. Review count/hardware inventory without identifying the site in public.
3. Record separate ARCore route in fully lit and dim safe indoor space. ZIP should include `_phone_environment.csv` and `_visual_observability.csv`. Verify `POSITION_UNKNOWN` when ARCore loses tracking, and no inertial translation sent downstream.
4. Revisit with independent manually measured landmarks / no smoke. Validate gate coverage, timing drift, power and false recovery on S21. **No dangerous smoke or active fire tests.**
5. Feed the real raw IMU streams into host PDR/ML evaluation, keeping learned models segregated by training/held-out device and license.

## CI contract

`app/src/test/java/dev/jboehm/vectorvio/VisualObservabilityGateTest.java` tests transitions, good pose, blur, low texture, IMU stale, duplicate timestamps, reset and unknown on initial lost tracking. `AuxSensorMonitorTest` checks truthful environmental CSV ages. `SensorLabActivityTest` verifies settings routing and no auto-record. Existing Android workflow compiles/tests the APK. Latest SHA and CI result must be checked **after** PR push.

**Coordination risk:** touched shared `MainActivity.java`, `SettingsCatalog.java`, `SettingsPresentation.java`, and `AndroidManifest.xml` only with small additions. All 3rd-party development heads must be refreshed before integration, and all previous settings/GPX/PDR must survive tests.
