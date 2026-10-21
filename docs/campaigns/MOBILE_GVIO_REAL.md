# Mobile-GVIO • real smartphone images and IMU benchmark

Status: **experimental benchmark confirmed executed on real raw camera+IMU data and scored with explicitly posthoc time + scale fitted against the external reference**. It is NOT a validated metric VIO odometer. This is a research benchmark, not a release-quality odometer, and does not test Android ARCore.

## What was genuinely executed

- Source: [Mobile-GVIO Zenodo record 20525157](https://doi.org/10.5281/zenodo.20525157), real Honor smartphone `Indoor-2`.
- Compressed source: `Indoor-2.zip` **950,890,027 bytes**, original MD5 `2a5439c7064ecc1a254f8a5790586742`, checked against the downloaded bytes; unpacked `indoor_2.bag` is **2,833,868,105 bytes**. `calib.zip` 3,691 bytes, MD5 `35b950dd11f19d0c8f9a967a4687aa5e`.
- Visual sensor: real 1280 × 720 monocular camera images, camera pinhole and radial-tangential distortion calibration; ORB-SLAM3 YAML camera-to-IMU rigid transform is used.
- Inertial sensor: recorded IMU gyroscope; **no absolute translational scale from accelerometer** in this research baseline.
- Estimator: `tools/campaigns/mobile_gvio_raw.py`: calibrated optical flow + essential-matrix translation directions and independent gyroscope integration of orientation. It **does not use the LiDAR reference during inference**.
- Previous proven raw inference (run [39168785572](https://github.com/jboehm1/VisualOdometry/actions/runs/39168785572)): **131 camera frames, 2,200 IMU gyro samples, 2,185 integrated rotations, 130 visual direction updates, 14,313 RANSAC inliers**. This does not by itself establish precision.
- Scorer: `tools/campaigns/mobile_gvio_score.py` uses real LiDAR-IMU Fast-LIO2 positions **after** inference to compute Sim(3)-aligned trajectory *shape* RMSE; it **fits absolute scale from GT only for scoring**, so is **not** metric odometry accuracy.
- Original 8–30 sec inference window was outside the independent reference interval. Scorer rightly raised an error rather than inventing an ATE metric. Overlapping ~295–317 sec sensor window has been selected for new validation.
- Native phone recording is downloaded to GitHub Actions temporary disk, **not your Google Drive or personal computer**. Only small JSON reports are uploaded from successful jobs. Approximate temporary disk requirement 4 GiB plus unpack overhead.
- Tests: `tools/campaigns/tests/test_mobile_gvio.py` covers correct similarity alignment, fail-closed no-time-overlap, and unsorted-reference rejection; [workflow 39169482876](https://github.com/jboehm1/VisualOdometry/actions/runs/39169482876) passed.

## How to reproduce

```bash
python -m pip install rosbags opencv-python-headless scipy
python tools/campaigns/mobile_gvio_fetch.py --out /tmp/mobile_gvio
python tools/campaigns/mobile_gvio_raw.py \
    --bag /tmp/mobile_gvio/indoor_2.bag \
    --calib /tmp/mobile_gvio/orbslam3.yaml \
    --skip 295 --seconds 22 --out results/mobile_gvio
python tools/campaigns/mobile_gvio_score.py \
    --estimate results/mobile_gvio/estimate_unscaled.tum \
    --truth /tmp/mobile_gvio/ground_truth.txt \
    --out results/mobile_gvio/score.json
```

For automated execution consult `.github/workflows/real-phone-vio.yml`. Files originating from licensed research datasets remain subject to their upstream CC BY 4.0 attribution requirements; the CI uploads only compact derived reports. Never publish raw phone recordings by accident.

## Current validity boundaries

A green visual/IMU run proves that real image and gyro messages were ingested into a live experimental estimator, and that a shape-evaluator could compare against a separately captured reference. It does **not** demonstrate:

1. Metric scale of monocular motion without a metric anchor.
2. Proper fusion with accelerometer/velocity biases as in complete inertial VIO.
3. Reliability of GPS fusion, running motion, severe rotations or different phone models.
4. Production Android `feature/slam-explorer` app or S21 ARCore pose accuracy.
5. Absolute body/camera/LiDAR lever-arm calibration for rigorous 6DoF ATE.

A **production-quality** metric visual+inertial benchmark will need a calibrated real VIO estimator such as OpenVINS or ORB-SLAM3 operating on original images+IMU, verified extrinsics, fixed timestamps and independent reference path. This experimental baseline should remain separately labeled rather than replacing this later qualification.

## Comparison with inertial-only

SIMUL, already integrated in the unified benchmark, is the correct current reproducible **real-data step-detection** test for the pinned Android Java PDR engine. It has four different participants and twelve participant/placement combinations; annotated steps support true count and interval F1 but **not** independent distance or trajectory ATE. Mobile-GVIO contributes the complementary camera+IMU modality.

## Verified complete run — October 18, 2026

[GitHub Actions run 39170361901](https://github.com/jboehm1/VisualOdometry/actions/runs/39170361901) returned **SUCCESS** on git commit `ce8bd4141dfdd334887ad91530035c7814b12fe7`.

**Measured results (22-second actual recording window):**
- 131 genuine 1280x720 phone frames decoded and processed after downsampling; 2,200 raw gyro samples, 2,185 actually integrated.
- 130 accepted image essential-matrix pose-direction updates; 14,313 real tracked RANSAC inliers.
- 124 pose timestamps paired to a LiDAR-IMU reference after **post-hoc time alignment**.
- Rotation-signal correlation 0.815; posthoc time shift **282.95965 seconds** inferred by matching the estimated rotation-change signal to ground-truth orientation changes. The dataset does **not** supply a verified, independent phone↔LiDAR clock calibration.
- **Sim(3)-aligned trajectory-shape RMSE 0.40149 m**; fitted scale 0.213705. Both time shift and metric scale are fitted with reference **only after inference**, hence the result is *not* independent metric accuracy, *not* a valid absolute distance RMSE and cannot be compared directly to SE3/metric VIO results.
- Real raw archive byte length, independent upstream MD5 checksums and calibration were checked before inference.
- Workflow also asserts the reference-based clock shift was **not independently calibrated** and that the estimator did not observe absolute metric scale.

The report was copied via connected GitHub → Drive tools and its metadata was read back:
[Download the small report in the user's private SLAM_BENCHMARKS/04_REPORTS folder](https://drive.google.com/file/d/1OkFbRBkj6kgoHM1HAHTxogOMUKdnxL_t/view).
No raw smartphone sensor recording was added to Git or the user's Drive.

**Next research-quality criterion:** run a genuine **metric** tightly-coupled VIO on the same images+accelerometer+gyro, obtain independent timestamp calibration, verify LiDAR-vs-phone extrinsics, and report SE3 ATE/RPE on held-out sequences. The 0.401 m figure above is not this criterion.
