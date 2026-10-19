# Real phone same-recording algorithm A/B and runtime pilot

This is an experimental complement to [Test Environment V3](TEST_ENV_PHONE_V3.md). It closes the previous gap of measuring **two actual image+gyroscope tracking configurations on the same real smartphone recording**. It does not claim a finished metric VIO or field-tested Samsung S21 odometer.

## One original recording, two frozen trackers

On Mobile-GVIO Indoor-2 the existing research monocular+gyro pipeline is run four times in ABBA order:
- `balanced`: 750 optical-flow corners, quality 0.018, minimum spacing 7 pixels (unchanged historical baseline).
- `low_compute`: 260 optical-flow corners, quality 0.018, minimum spacing 10 pixels.
- Decode source ROS bag **once**, reuse identical camera images, timestamps, calibration and actual gyro samples.
- Both consume real camera features and measured gyro orientation; both use monocular unscaled translation directions. Both are Python/OpenCV research algorithms on a Linux host, not the VECTOR Android ARCore app.
- Measure per-inference CPU seconds and wall seconds, with two repetitions each, same short sequence, ABBA to reduce sequential warming bias. Report medians, image and IMU counts, rejected essential matrices and matched GT pairs.
- AFTER all inference runs finish, compare both to original LiDAR-IMU GT. The clock offset is fitted from the balanced reference rotations, then shared with low-compute to avoid different GT clock fits. **Each variant still fits metric Sim(3) scale from reference**. These are shape-only scores, not independent metric ATE.
- The runner explicitly flags any output claiming metric odometry or validated clock calibration as false.

## Reproduce

    python -m pip install rosbags opencv-python-headless scipy
    python tools/campaigns/mobile_gvio_fetch.py --out /tmp/phone_ab
    python tools/campaigns/mobile_gvio_ab.py \
      --bag /tmp/phone_ab/indoor_2.bag \
      --calib /tmp/phone_ab/orbslam3.yaml \
      --truth /tmp/phone_ab/ground_truth.txt \
      --skip 8 --seconds 22 \
      --out campaign_results/mobile_vio_ab

Evidence: `campaign_results/mobile_vio_ab/comparison.json`, plus small per-inference reports and trajectory points. The new CI `.github/workflows/mobile-vio-ab.yml` executes exact original downloaded 951MB archive and uploads small JSON only (no imagery, raw gyro or LiDAR). Replaying a different source and selecting hyperparameters **using its reference** would be leakage; these two tracker presets are frozen before evaluation.

## Remaining independent scientific / device gates

- **Metric six-axis VIO:** integrate gravity, accelerometer bias, velocity and temporal/camera-IMU calibration with observability checks; compare to OpenVINS/ORB-SLAM3 only using exactly the same sequence and validation protocol. Existing experimental image+gyro direction integration does not solve monocular metric scale.
- **Multiple recordings:** assess different motion, camera placements, GPS denial, staircase, lighting and running; provide median + percentile errors by episode rather than one 22s Indoor-2 window.
- **S21 actual APK:** install merged APK and record the same route repeatedly (walk, run, stationary, rotation, low light, outdoors/indoors), along with real thermal, battery, CPU and sampling rates. CI has no physical S21 and cannot validate actual power draw, thermal throttling or APK tracking.
- **Independent truth:** surveyed path length or synchronized independent LiDAR / RTK GNSS under tested conditions; do not tune absolute path scale from the scored reference, and distinguish SE3 ATE from Sim3 posthoc shape.
- **Licensing:** Mobile-GVIO data terms apply; ADVIO CC BY-NC forbids commercial use without separate permission.

Tests being green prove execution and input contracts, **not** certified positioning for firefighters or other safety-critical use.
