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
