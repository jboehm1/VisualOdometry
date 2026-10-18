# ADVIO-09: small real smartphone visual+inertial dataset

**Proof of execution:** [GitHub Actions #39170934452 — SUCCESS](https://github.com/jboehm1/VisualOdometry/actions/runs/39170934452).

## Why this is useful

[ADVIO](https://doi.org/10.5281/zenodo.1476931) has 23 recordings from real pedestrian environments, including the original iPhone video, synchronized 100 Hz gyro/accelerometer, separately recorded Google Pixel ARCore poses, and a reference trajectory reconstructed with inertial data plus independently marked position fixpoints. It was collected in 2018. License **CC BY-NC 4.0**: attribution is required, and commercial use is not authorized by that license.

The chosen first real recording, `advio-09.zip`, has **70,695,364 bytes compressed**, exact Zenodo pinned MD5 `a5fedb98e2f50954c670e61d04642286`, and need not occupy permanent Google Drive storage.

## Verified runtime facts

Our dedicated independent runner `tools/campaigns/advio09_real.py` downloaded the original public ZIP, verified exact MD5, and processed:

| Check | Real run |
| --- | ---: |
| Camera video frames actually decoded | 500 |
| Optical-flow feature-pair image steps | 33 |
| Tracked image features | 11,903 |
| Real gyroscope samples in camera time window | 831 |
| Measured cumulative angular motion magnitude | 2.2844 rad |
| Separately supplied Pixel ARCore pose samples matched with GT | 2,687 |
| ATE RMSE, external Pixel ARCore, rigid SE3 without metric scale fitting | **2.0714 m** |

**Two deliberately separate validations**:

1. `raw_multimodal_audit` proves the real video decoder and actual iPhone IMU gyro data are usable. It is an **input audit, not an odometry pose estimate from those raw signals**.
2. `external_vision_imu_estimator_score` compares **published Google Pixel ARCore poses** (computed beforehand on a real phone) against ADVIO fixation-aided reference. It **does not measure our own Android/ARCore application**. GT is not purely independent optical tracking: it uses a separate inertial trajectory combined with externally identified fixpoints.

The external ARCore ATE does not compare with the experimental Mobile-GVIO Sim(3) RMSE; these estimate different systems and use different alignment and ground-truth definitions. Also, rigid lever-arm discrepancies between the iPhone/Google Pixel and the reference assembly could affect the absolute score.

## Reproduce

```bash
python -m pip install numpy opencv-python-headless
python tools/campaigns/advio09_real.py --out campaign_results/advio09
```

GitHub workflow: `.github/workflows/advio-real.yml`; no Google Drive account access needed for public data. Only a small JSON report is uploaded to CI artifacts, not raw video/photos.

**Private Drive archive verified:** [ADVIO09 real video/gyro + external ARCore report](https://drive.google.com/file/d/1FxN4LwLayRy-gzv7l7tJVDhusT5zoeqZ/view).

## Scientific readiness

- **Real iPhone video+gyro ingest**: demonstrated.
- **Real metric VIO output provided externally (Pixel ARCore)**: score computed; it is not ours.
- **Our own unscaled research vision+gyro pose inference on Mobile-GVIO**: separately demonstrated in `docs/campaigns/MOBILE_GVIO_REAL.md`.
- **Our production Android app or metric six-axis VIO from raw ADVIO frames**: not executed by this script, not claimed tested.
- **Pedestrian PDR Java**: independently tested with real SIMUL IMU as documented in `docs/campaigns/REAL_SIMUL_JAVA_PDR_20261018.md`.

Thus there is now a practical small, reproducible, real phone-oriented test set for a future production VIO engine, but the system needs to actually replay this original video+6-axis IMU before its metric accuracy can be certified.
