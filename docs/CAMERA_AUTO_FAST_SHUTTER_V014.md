# VECTOR VIO v0.14 — camera scene-adaptive fast-shutter lab

**2026-10-20 • Branch `feature/v014-camera-adaptive-fast-shutter` • Parent `feature/v014-hybrid-inertial-camera-quality` SHA `a45403362ee8f9c16d3de778c5d38c3f0aaf014a`.** This is optical R&D, NOT manual exposure control while ARCore is tracking.

## Field feedback and requirement
The user confirmed on **Galaxy S21** that manual Camera2 shutter and ISO really work and faster shutter visibly reduces blur. Objective: **shortest achievable shutter compatible with sufficient scene detail and a configured ISO-noise ceiling**, adapt to outdoors, tunnels, dusk and motion; **fallback on unsupported phones**.

## How brightness is measured (and what we cannot know)
- In the separate Camera2 lab, sample a **96×54 preview bitmap every ~430 ms** and inspect the middle ~80% of pixels; extract post-ISP gamma-domain brightness percentile **P10/P50/P90**, dark (<20) and saturated (>=248) pixel fractions and a cheap adjacent-pixel contrast proxy.
- **The sample is not a lux measurement, optical SNR, blur detector or feature-tracking inlier count.** Camera processing changes tone curves, and a uniform white wall may be bright but featureless.
- If an Android `TYPE_LIGHT` sensor exists, show its ambient **lux as separate diagnostic**. Often it points towards the screen, not the rear camera scene; never feed it as absolute camera brightness.
- Sensible **initial lab target**: median brightness 90 / 255 with warning if P50<48, very low image contrast, or strong highlight clipping. The target is adjustable 55–150, **not calibrated as a physically universal minimum**. The real minimum for odometry is a successful feature/tracking pipeline with acceptable frame-to-frame displacement/inlier quality, ISO noise and sufficient shutter time. Requires on-device testing in daylight, shade, dusk, indoor, bike motion and fixed camera.

## Implemented adaptive policy
- Toggle `AUTO FAST SHUTTER` ON/OFF (defaults ON); independent manual shutter and ISO sliders remain for A/B comparisons.
- User `ISO NOISE CEILING` slider limits ISO to sensor-supported range (default 1600, user-controlled, no hardcoded Galaxy-specific limits). The system uses the **highest permitted ISO when needed** to obtain a shorter shutter at a given estimated light level; in bright scenes at the minimum supported exposure it can lower ISO to avoid overexposure.
- Use bounded negative-feedback image brightness adjustment (max 0.43 EV per update, at least 650 ms between updates, deadband, anti-clipping), modelled as exposure × ISO. It is not physical linear brightness calibration because preview is processed gamma-space. It **never exceeds the hardware ISO ceiling** nor requests shutter outside `CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE` and frame-duration cap.
- If even maximum acceptable ISO and maximum allowed exposure cannot reach the brightness target, the UI reports `LIGHT LIMIT` rather than pretending a sharp tracked image remains possible. Current lab targets 30 FPS (max ~33 ms exposure); slower FPS / illuminator / IR-specific cameras can be studied separately.
- `Camera2` manual availability requires rear-camera `MANUAL_SENSOR` and valid exposure + gain ranges; **otherwise native camera AE fallback** is opened and manual/adaptive controls disabled, while preview brightness diagnostics still work. This applies to LEGACY and LIMITED devices without manual sensor capability. If neither rear camera nor compatible preview can open, report an explicit error.
- Optional device `TYPE_LIGHT` sensor (no permission), missing lux simply displayed `N/A`.
- Sampled bitmap is recycled; onPause removes callbacks, releases sensors/camera. This is a LAB and must never claim ARCore GPX alignment during the lab.
- ARCore mode remains under its **own camera AE**; it can select 30/60 FPS profiles and log exposure/ISO, but Google ARCore SharedCamera must not receive `setRepeatingRequest` during active AR tracking.

## Acceptance and known limits
- Synthetic Java regression tests for dark/bright/no-texture, ISO cap/hardware caps, settling, saturation, auto mode fallback; run Android Gradle + full existing GPX/PDR/AR suite at exact SHA.
- **Real S21 test required**: lab under shadow/sun, indoor bright/dark, bike-simulated panning, torch, filter performance, thermal stability, verify actual CaptureResult exposure/ISO follow requests and realistic FPS. Repeat on a second Camera2 FULL/LIMITED device and on a nonmanual/LEGACY device for fallback.
- Reliability concerns: Camera2 output ISP smoothing/exposure response delayed several frames, temporal sampling aliasing, anti-flicker indoors 50Hz, lens stabilization/AF, preview tone curves, scene movement. No fixed universal minimum lux; compare actual ARCore TRACKING loss and feature quality separately to calibrate target on data.
- The next *major architectural research question* is using this acquisition with custom VIO while preserving GPX coordinate alignment, since manual Camera2 control is **not presently injected into ARCore**.

References: https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics , https://developer.android.com/reference/android/hardware/camera2/CaptureRequest , https://developers.google.com/ar/reference/java/com/google/ar/core/SharedCamera .

## Paired ARCore tracking/brightness diagnostic added

When the normal **ARCore VIO recording** is active, approximately once per second the existing 1-Hz camera metadata pass *attempts* `Frame.acquireCameraImage()` (no camera parameter changes), samples its Y plane at 96×54, immediately closes the image, and adds `_arcore_luma.csv` with actual `tracking_state`, `failure_reason`, P10/P50/P90, dark/clipped fractions, image edge proxy, exposure and ISO. If CPU image is temporarily unavailable or unsupported this telemetry is skipped without affecting SLAM. This explicitly enables real-light calibration **against whether ARCore was tracking** rather than guessing a single universal minimum from ambient lux. Preview RGB luma and YUV plane luma are different image spaces and should be analysed separately. The app does NOT apply manual shutter to ARCore.

## Offline analysis after a phone field recording

```bash
python3 tools/camera/evaluate_illumination.py --csv path/to/session_arcore_luma.csv --out report_luma.json
```

Uses the *recorded* ARCore tracking state and failure reason to compare P50 image brightness / texture / exposure among tracked frames and dropped frames. It **does not guess a universal minimum scene lux**, does not ascribe causal illumination to every lost track and reports no deployment threshold without independent datasets. Unit fixtures in `tools/tests/test_camera_illumination.py` run under the existing GitHub Android workflow Python test step. This is how we can calibrate a sensible image brightness target later rather than inventing one now.
