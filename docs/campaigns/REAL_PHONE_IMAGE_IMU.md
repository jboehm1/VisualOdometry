# Real smartphone image + IMU benchmark (experimental research baseline)

## Dataset and storage

The original source is [Mobile-GVIO, Zenodo DOI 10.5281/zenodo.20525157](https://doi.org/10.5281/zenodo.20525157), provided by Shenzhen University under CC BY 4.0. The **Indoor-2** sequence was captured on a genuine smartphone (monocular 1280x720 images at approximately 30 Hz and IMU at approximately 100 Hz), with a separate LiDAR–IMU Fast-LIO2 reference.

The pinned official compressed file `Indoor-2.zip` is **950,890,027 bytes** (MD5 `2a5439c7064ecc1a254f8a5790586742`). It extracts a **2,833,868,105-byte raw ROS bag** and a pose reference file. The calibration archive is 3,691 bytes, pinned MD5 `35b950dd11f19d0c8f9a967a4687aa5e`.

All raw data stays on the **ephemeral GitHub Actions runner disk**. Neither Google Drive nor the user's PC receives the big archive automatically. The GitHub Actions artifacts include only results, not raw video and sensor data.

## Engines must not be conflated

- **IMU-only verified:** `SIMUL` actual Java `PdrEngine` from pinned parallel workstream, four persons × three placements, real sensor recordings and step interval annotations. This is a step-detection benchmark, not metric trajectory ground truth.
- **Camera+IMU pilot:** `tools/campaigns/mobile_gvio_raw.py`, explicitly an **experimental Python optical-flow/essential-matrix translation-direction plus measured gyro orientation** estimator, using the published camera–IMU extrinsics. This is not the VECTOR Android ARCore engine, and *monocular scale is unobservable* without additional information.
- `tools/campaigns/mobile_gvio_score.py` runs strictly **after inference**, using the LiDAR-IMU reference, and only reports post-hoc **Sim(3) trajectory-shape RMSE**. Ground-truth scale is never injected into the estimator; Sim3 shape RMSE is **not metric-scale odometry accuracy**.
- **Not yet delivered by this pilot:** full EuRoC stereo/raw OpenVINS, Android ARCore on a physical Galaxy S21, and statistically robust error across a full dataset collection.

## Reproduction

```bash
python -m pip install rosbags opencv-python-headless scipy
python tools/campaigns/mobile_gvio_fetch.py --out /tmp/mobile_gvio
python tools/campaigns/mobile_gvio_raw.py \
    --bag /tmp/mobile_gvio/indoor_2.bag \
    --calib /tmp/mobile_gvio/orbslam3.yaml \
    --seconds 22 --skip 295 --out /tmp/mobile_gvio_results
python tools/campaigns/mobile_gvio_score.py \
    --estimate /tmp/mobile_gvio_results/estimate_unscaled.tum \
    --truth /tmp/mobile_gvio/ground_truth.txt \
    --out /tmp/mobile_gvio_results/score.json
```

GitHub Actions workflow: `.github/workflows/real-phone-vio.yml`.

The first executable attempt verified the original ZIP/MD5 and reported **131 actual image frames decoded**, **2,185 gyro integrations**, and **130 successful image-based motion directions**. The first scoring attempt deliberately failed because the camera–IMU replay initially selected a time interval outside the LiDAR reference segment. The workflow now targets overlapping timestamp ranges. **Do not call the end-to-end camera+IMU scoring validated unless CI passes both inference and independent reference matching.**
