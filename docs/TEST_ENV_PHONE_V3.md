# Test Environment V3 — real smartphone datasets

This extends the V2 framework without replacing its manifest, runner, isolated subprocess, evidence gates, SHA-based provenance, run history or offline HTML dashboard.

## Quick start

From repository root, Python 3.11 or later:

    python testenv.py plan --suite tools/testenv/suites/real-phone.json

    # ADVIO-09: about 71MB; original iPhone camera/gyro audit plus external Pixel ARCore score
    python -m pip install numpy opencv-python-headless
    python testenv.py run --suite tools/testenv/suites/real-phone.json --only advio09 --out campaign_results/real-phone

    # Mobile-GVIO: 951 MB compressed and 2.8GB extracted ROS bag, real images and gyro
    python -m pip install rosbags opencv-python-headless scipy
    python testenv.py run --suite tools/testenv/suites/real-phone.json --only mobile-gvio --out campaign_results/real-phone

    # Both, sequentially:
    python testenv.py run --suite tools/testenv/suites/real-phone.json --out campaign_results/real-phone

Open campaign_results/real-phone/index.html for past runs, metrics and full JSON evidence.

## What is scored (strictly separate)

| Dataset / Engine | Actual inference | Independent reference | Qualified output |
| --- | --- | --- | --- |
| ADVIO-09 / external-pixel-arcore | Raw iPhone images+gyro ingested and audited; external Pixel ARCore poses already recorded by authors (no inference from the raw iPhone signals here) | Fixation-aided inertial reference, not pure optical MoCap | Frames/gyro/tracks + SE3 ATE of PRECOMPUTED Pixel ARCore |
| Mobile-GVIO / research-mono-gyro | Our experimental Python optical-flow/real gyro pose estimates, **not** Android or full visual-inertial integration | LiDAR-IMU FastLIO2, accessed only AFTER inference; scale and clock offset fitted with reference | Frames+gyro+visual updates; exploratory Sim3 shape RMSE ONLY |
| SIMUL / android-pdr | Actual pinned Java step algorithm via accel fallback, not physical S21 sensor | Annotated foot IMU step intervals | Step count/F1, not metric distance |
| EuRoC / external-vins | Published external VINS-Mono poses scored (no estimator execution) | EuRoC reference | External trajectory SE3 metrics, NOT VECTOR app accuracy |

Do not compare ADVIO rigid-SE3 ATE with Mobile-GVIO posthoc Sim3 shape RMSE, or with EuRoC published pose ATE, as competing odometry accuracies. Different estimator, frame, reference, time and scale contracts.

## Provenance, privacy and license

- ADVIO-09 original DOI https://doi.org/10.5281/zenodo.1476931, pinned ZIP 70,695,364 bytes and MD5. License CC BY-NC 4.0: attribution/noncommercial; commercial use requires permission.
- Mobile-GVIO Indoor-2 https://zenodo.org/records/20525157, checksum-verified 950,890,027-byte archive plus camera calibration ZIP; extracted bag about 2.8GB, high transient disk demand.
- Suite accepts only known dataset-engine pairs. No caller-provided synthetic truth override. Model inferencing and GT evaluation are separate commands. Original pinned upstream checksums and calibration SHA are recorded.
- Both benchmark adapters included in V2 code SHA fingerprints; changing their source invalidates previous cache entries.
- Large camera/IMU downloads are transient and removed by the orchestrator; GitHub Actions artifacts upload only small HTML and JSON report evidence (not raw video). No automated Drive archive.
- GitHub workflow phone-dataset-matrix.yml tests small ADVIO on push/PR; Mobile-GVIO expensive download only on PR or manual dispatch.

## Remaining qualification

ADVIO processed original camera/gyro but did not run VECTOR or a raw-image VIO estimator for that sequence. Its trajectory metric is for externally recorded Pixel ARCore. Mobile-GVIO ran actual camera + measured gyro research inference, but its translation scale is not observable and the score fits time and Sim3 to reference after inference. Neither proves a physical Samsung S21 application or independent metric accuracy. Tests for course, different phone placements, low light, smoke, stairs, GPS failures and benchmark metric VIO remain distinct work.
