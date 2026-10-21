# Real-data validation decision — 2026-10-17

## Decision

Use real synchronized visual-inertial datasets as the main accuracy evidence. Keep procedural scenes as deterministic regression tests. Do not promote the Java forward-planar replay estimator to general 6-DoF VIO based on corridor scores.

- **Current Android product:** retain ARCore. Add official ARCore Recording/Playback for repeatable real-phone sessions. A EuRoC/OpenVINS or ORB-SLAM3 result does **not** validate the app's ARCore path.
- **Independent camera+IMU mapping engine:** evaluate **ORB-SLAM3 in monocular-inertial mode**, using its provided EuRoC and TUM-VI runners. It includes persistent sparse SLAM maps and loop handling; Android integration is a separate native/NDK engineering task, not an existing drop-in dependency in this APK.
- **Primary controlled benchmark:** EuRoC, one camera plus the actual IMU, published calibration and independent trajectory/structure reference.
- **Cross-dataset generalization:** TUM VI handheld room sequences, then corridor/outdoor sequences. Ground truth is only available in the motion-capture volume; do not invent full-trajectory ATE outside it.
- **Smartphone realism:** ADVIO as a secondary stress test. Its reference is derived from IMU plus surveyed/fix points, so it is less independent for testing an inertial estimator than optical motion capture.

## Options checked against original project sources

| Dataset / engine | Useful properties | Limits for this project |
|---|---|---|
| EuRoC | Synchronized 20 Hz stereo images / 200 Hz IMU; camera–IMU extrinsics/intrinsics; motion and structure truth | Drone motion, global-shutter cameras; not a Samsung rolling-shutter camera |
| TUM VI | Handheld sequences, hardware synchronization, 20 Hz cameras / 200 Hz IMU; calibrated images | Fisheye model must be supported or rectified; motion-capture truth is spatially limited |
| ADVIO | Pedestrian indoor/outdoor, stairs, elevators, smartphone video/IMU, existing ARCore/ARKit tracks | Reference uses inertial reconstruction plus fix points; CC BY-NC 4.0 |
| ORB-SLAM3 | Mono/stereo visual-inertial SLAM, sparse mapping, loop/multi-map functionality, EuRoC/TUM-VI examples | GPLv3; native Android integration and profiling needed; not a dense mesh engine |
| OpenVINS | Strong filter-based VIO baseline; EuRoC/TUM-VI support and calibration guidance | Better for odometry comparison than replacing a full persistent SLAM map |
| ARCore Recording/Playback | Real recorded video + IMU in ARCore MP4, replay into the same app engine | Requires an ARCore recording, not an arbitrary video or EuRoC ZIP; pose results are not independent truth |

## Real-data input contract

1. Original camera exposure timestamps, image calibration and lens distortion model.
2. **All** intervening gyroscope and accelerometer samples, with original timestamps. Do not collapse 200 Hz IMU to one sample per 20 Hz image as a general VIO interface.
3. Published camera–IMU rigid transform, sensor axes, units, gravity convention, timing offset and noise parameters.
4. Rectification only with its matching updated intrinsics. Never stretch frames and keep the old focal length.
5. Ground truth kept in a separate evaluator. No truth poses, maps, real speed or scene dimensions passed to the estimator.
6. ARCore MP4 input handled through ARCore's official replay API, separately from a research-engine dataset adapter.

## Evaluation protocol

- Freeze engine commit/configuration before held-out runs. Publish hashes and any later changes.
- Development: EuRoC MH_01_easy only. Evaluate other machine-hall and Vicon sequences without sequence-specific tuning; heed the ground-truth issues documented by dataset/engine authors.
- Cross-dataset test: TUM VI room sequences first, then corridor sequences for return drift. Keep ADVIO locations unseen during tuning.
- Measure initialization time, tracking failures/coverage, ATE/RPE, path distance error, speed RMSE, and return drift. Report failures, not only successful runs.
- Preserve metric scale: no post-hoc similarity scale fit for VIO accuracy claims. Declare any pose-frame alignment.
- Use provided ground-truth velocity where available. For distance/speed derived from poses, predefine time sampling/filtering so ground-truth position noise does not inflate traveled distance.
- Evaluate sparse map point-to-reference error **and spatial coverage** where scan/structure truth exists. A low nearest-surface error alone does not prove a complete map.

## Status

The two procedural scenes have been executed headlessly using the exact Java renderer/estimator classes used by Android. The APK UI itself has not been exercised in an Android emulator here. No EuRoC, TUM VI, ADVIO or ORB-SLAM3 accuracy run is claimed yet. ARCore MP4 recording and session ZIP export are implemented (see CAPTURE_FORMAT.md); physical-device MP4 capture has not yet been verified here. ARCore playback controls and EuRoC/TUM VI adapters are still not implemented.

## Primary sources (checked 2026-10-17)

- [ETH EuRoC data and known issues](https://projects.asl.ethz.ch/datasets/euroc-mav/)
- [TUM VI original benchmark](https://cvg.cit.tum.de/data/datasets/visual-inertial-dataset)
- [ADVIO data and reference construction](https://github.com/AaltoVision/ADVIO)
- [ORB-SLAM3 official repository and license](https://github.com/UZ-SLAMLab/ORB_SLAM3)
- [OpenVINS dataset guidance](https://docs.openvins.com/gs-datasets.html)
- [ARCore recording/playback contents](https://developers.google.com/ar/develop/recording-and-playback)
- [ARCore Android recording/playback integration](https://developers.google.com/ar/develop/java/recording-and-playback/developer-guide)
