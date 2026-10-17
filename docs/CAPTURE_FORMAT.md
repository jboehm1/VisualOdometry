# Capture/export format v1 (app 0.7)

## User controls

1. DATA → Training video → ARM CAPTURE, while paused.
2. Optionally DATA → GPS; grant precise or approximate foreground location. No background permission is requested.
3. START/RESUME records odometry/IMU and, if armed, an ARCore dataset MP4.
4. PAUSE/backgrounding stops video and measurement. RESUME produces a new MP4 part of the same CSV session. RESET preserves previous files and starts a new session next time.
5. EXPORT pauses an active session to finalize its video. Choose CSV only or Dataset ZIP, select a session and destination.

The camera image, scene and optional GPS may contain private information. Data remains in private app storage until explicitly exported. There is no microphone capture or automatic upload.

## Files

| File | Contents |
|---|---|
| `vector_vio_<timestamp>.csv` | POSE rows, diagnostic SensorManager IMU and start/pause/video/marker events |
| `*_partNNN.mp4` | ARCore camera video and internal recorded sensor data; no HUD or microphone audio |
| `*_frames.csv` | Part filename, ARCore frame timestamp, CPU Image timestamp when available, boot/wall clock samples, tracking state, CPU-image intrinsics/dimensions, estimated camera pose |
| `*_gps.csv` | Provider, wall/boot timestamps, latitude/longitude, optional altitude/speed/bearing, reported horizontal and speed accuracy |
| `*_metadata.json` | Device/app/sensor provenance, units/axis conventions and known/unknown calibration fields |
| `README.txt` in ZIP | Format and training limitations, including missing video on older recordings |

Video/camera metadata exist only when capture was armed and successfully started. GPS exists only when enabled, with fixes received during recording. MP4 errors are reflected in the HUD and CSV video events. Optional photo keyframes are separate map assets and do not replace training video.

## Training contract

- Poses are ARCore estimates, **not independent ground truth**.
- SensorManager accelerometer is m/s² including gravity; gyroscope is rad/s in Android device axes. Requested rate is 200 Hz; delivery is device-dependent and timestamps must be used to measure the actual rate.
- ARCore frame time, CPU image time and Android SensorEvent time are separate recorded values; their offset/alignment is not asserted calibrated.
- Frame intrinsics apply to the CPU image dimensions in that row, not the stretched on-screen preview.
- Camera–IMU extrinsics, timing offset and rolling-shutter readout are unknown/null. Recover/calibrate them before using the export as a research VIO training dataset.
- Intrinsics and estimated camera pose are available, but no independent 3D truth map, exposure/readout calibration or calibrated raw IMU biases are claimed.
- MP4 is an ARCore dataset container, not EuRoC format. Use a dataset-aware extractor/replay tool to associate images and internal sensor tracks; do not assume video presentation times equal exported sensor timestamps.
- Respect pauses and tracking segment boundaries; do not interpolate unknown camera movement across them.

## GPS and vehicle recordings

GPS speed is a separate ground-speed measurement with its own accuracy and update rate. VIO reports camera movement relative to visual scenery; a fixed camera seeing an interior can report zero relative translation while the vehicle moves. This app does not fuse GNSS into its local VIO map or use it as metric training truth. Poor/approximate fixes remain marked by their reported accuracy in the CSV.

## Validation limits

Automated tests verify history fitting/segment breaks, session asset selection and ZIP contents; Android unit/lint/build checks verify integration. Actual GPS acquisition, MP4 encoding, timestamp behavior and capture performance require a physical ARCore phone test. Synthetic SIM truth export is not a recording of the SIM estimator.

## References

- [ARCore recording/playback](https://developers.google.com/ar/develop/java/recording-and-playback/developer-guide)
- [RecordingConfig](https://developers.google.com/ar/reference/java/com/google/ar/core/RecordingConfig)
- [Android LocationManager](https://developer.android.com/reference/android/location/LocationManager)

## Export picker and performance diagnostics (2026-10-17)

EXPORT offers current-session complete ZIP first, older sessions, and explicit CSV-only alternatives. The session list excludes `_frames.csv` and `_gps.csv`; those are technical sidecars included automatically with their parent session ZIP. Older sessions show UTC timestamps and nonempty MP4 part counts.

A supplied S21 session has now produced a ZIP containing a 39.5 s H.264 640×480 camera stream near 30 Hz, frame metadata and IMU CSV. This verifies that device capture/export produced a real MP4; it does not establish timing calibration or pose accuracy. Four SLOW_RENDER events measured 470–679 ms in that capture. New diagnostics append ARCore update and metadata acquisition durations. Frame-row string formatting now runs on the serialized recording worker, with timestamps and values captured before enqueueing.

## COLLECT ONLY (Camera2 long-trip mode)

Open DATA → COLLECT ONLY while the application is visible. This opens an independent screen without ARCore, a preview, mapping, pose estimation or an odometer. Start it while the app is visible and permissions are granted. It uses a camera/location foreground service with a visible notification and STOP action, plus a partial wake lock. Switching off the screen is an intended use case, **not yet verified on the physical S21**; OEM battery policy, interruptions, camera contention and thermal limits can stop acquisition. No automatic background restart or boot recording is attempted. Stop/finalize before exporting or starting live AR. The Android foreground-service rules are described at https://developer.android.com/develop/background-work/services/fgs/service-types . No background-location permission, microphone or network upload is added.

- Default profile: H.264 1280×720, 30 FPS target, 3 Mbps target (about 1.35 GB/hour video).
- Optional profile: 1920×1080, 30 FPS target, 6 Mbps target (about 2.7 GB/hour video).
- Video is **lossy**. These are bitrate budgets, not measured file-size/quality guarantees. Unsupported sizes fail explicitly rather than silently changing calibration.
- Accelerometer and gyroscope request 200 Hz each; actual rates depend on hardware/OS. `imu.csv.gz` preserves original timestamps and Float.toString values without rounding or downsampling. Types use Android Sensor constants, values use device axes, acceleration includes gravity.
- GPS/network location requests 1-second updates when authorized/enabled. Approximate permission or a disabled provider can limit GPS; events record its absence. `gps.csv.gz` includes timestamps, location, reported accuracy and speed when provided.
- `frames.csv.gz`: Camera2 callback frame number, sensor timestamp, local elapsed clock, exposure, rolling-shutter skew, ISO and focal length. These callbacks are **not proven one-to-one associations with decoded MP4 PTS**. Metadata includes timestamp source and available intrinsic/distortion calibration; camera-to-IMU extrinsics/time offset remain unknown. Intrinsics can require crop/scale corrections before training.
- MP4 parts roll after up to 10 minutes or 256 MiB. Camera session/encoder restart can create video gaps, recorded in events. Sensor logging uses a separate thread and continues across video changes; actual continuity must be checked from timestamps.
- Gzip writers flush roughly once per second; STOP closes/finalizes files. Abrupt process/power loss can leave the current MP4 or gzip tail incomplete. Older finalized MP4 parts remain independent. Recording requires 256 MiB free and stops below 128 MiB; it never deletes old captures. One session stops after 24 hours.
- Private folder `collections/capture_YYYYMMDD_HHMMSS_mmm/` (UTC). Metadata, summary counts, events, compressed sensor logs and MP4 parts export together. ZIP uses no additional compression because MP4/gzip are already compressed; it never re-encodes video. Export can require another full dataset's space at the chosen destination. Keep the controller open during export.

These Camera2 files are separate from ARCore recording datasets and cannot be fed directly to ARCore playback. They provide real camera/sensor input for future adapters, not calibrated training ground truth. Long-duration screen-off, encoder chunk changes, power use and synchronization require device tests. The GPU live-AR migration does not change its measurement/capture parameters; this new collection mode deliberately offers separate video compression profiles.
