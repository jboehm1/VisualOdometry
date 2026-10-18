# Real-world odometry benchmark pipeline (2026-10-18)

## Scope and verified boundaries

This adds a **host-side testing foundation**. It does not make ARCore on Android accept EuRoC video, nor does it execute ORB-SLAM3 or OpenVINS automatically. Present Android odometer uses ARCore metric poses. Synthetic Java simulation tests remain a separate regression layer.

Source files:
- tools/benchmark/catalog.json: 15 relevant datasets and openly declared adapter status.
- tools/benchmark/bench.py: EuRoC, TUM VI, VECTOR Camera2 index adapters; trajectory metrics; external executable runner.
- tools/benchmark/fetch.py: opt-in HTTPS downloads with mandatory SHA256 and safe ZIP extraction.
- tools/benchmark/tests/test_bench.py: deterministic fixtures, sensor index, scale/leakage tests.
- .github/workflows/benchmark.yml: cloud smoke tests and manually requested real-file audit.

The catalog is a **prioritized research shortlist**; finding every dataset on the internet is not achievable, and only EuRoC, TUM VI and our Camera2 capture indexes currently have parsers.

## 1. Architecture

    DATA LAYER                         EXECUTION                    EVALUATION

    EuRoC / TUM VI ----+
                       +--> adapter --> frames.csv ------+
    Android Camera2 ---+               imu.csv ----------+--> external estimator
                       |               manifest.json     |    (user installs it)
                       |               calibration refs--+         |
                       |                                trajectory.tum
                       +--> independent ground truth --------+--> evaluator
                                                                  |
                                                              report.json

Input sensor indexes preserve raw integer nanosecond timestamps. Each gyro and accelerometer observation remains separate. Frames CSV contains image paths; for VECTOR Camera2 it contains references to MP4 parts instead of decoded frames. Calibration files are identified, **not automatically parsed or applied**. The estimator must transform its camera-world pose into the **same IMU/body frame** as EuRoC/TUM VI reference. The evaluator refuses mismatched declared pose-frame names. Ground-truth is never included among engine command variables.

ARCore official Recording/Playback on Android remains required to replay the *actual app estimator*. An external SLAM implementation on EuRoC does not establish Android accuracy.

## 2. Local quick start

Requires Python >=3.11, NumPy pinned in requirements file.

    python -m pip install -r tools/benchmark/requirements.txt
    python -m unittest discover -s tools/benchmark/tests -v
    python tools/benchmark/bench.py catalog

These exercises use generated fixtures, NOT a downloaded full real-world sequence.

Example original dataset tree:

    ~/slam-data/EuRoC/MH_01_easy/mav0/cam0/data.csv
    ~/slam-data/EuRoC/MH_01_easy/mav0/cam0/data/*.png
    ~/slam-data/EuRoC/MH_01_easy/mav0/imu0/data.csv
    ~/slam-data/EuRoC/MH_01_easy/mav0/state_groundtruth_estimate0/data.csv
    ~/slam-data/EuRoC/MH_01_easy/mav0/{cam0,imu0}/sensor.yaml

Prepare the actual native files (lightweight indexes only):

    python tools/benchmark/bench.py prepare \
      --dataset euroc --root ~/slam-data/EuRoC/MH_01_easy \
      --out benchmark_results/euroc_mh01/inputs

    python tools/benchmark/bench.py prepare \
      --dataset tumvi --root ~/slam-data/TUMVI/room1 \
      --out benchmark_results/tumvi_room1/inputs

For a VECTOR Camera2 capture directory (frames.csv.gz, imu.csv.gz and MP4 parts):

    python tools/benchmark/bench.py prepare \
      --dataset vector_capture --root ~/slam-data/phone/capture_20261018_123000_000 \
      --out benchmark_results/phone001/inputs

The phone capture has **no independent accuracy ground truth**. A separate MP4 decoder/timestamp converter and calibrated camera–IMU extrinsics are still needed to use it with independent VIO algorithms. Imported TUM VI truth may cover only the first and last portions; no invented full trajectory is permitted.

## 3. Run independent VIO / inertial baseline

Install and configure a trusted engine locally. Example engine definition in local_engine/engine.json:

    {
      "name": "pinned-local-vio-engine",
      "timeout_s": 1800,
      "argv": [
        "python3", "local_engine/run_vio.py",
        "--frames", "{frames}",
        "--imu", "{imu}",
        "--output", "{output}"
      ]
    }

    python tools/benchmark/bench.py run-engine \
      --prepared benchmark_results/euroc_mh01/inputs \
      --engine local_engine/engine.json \
      --out benchmark_results/euroc_mh01/engine_a

Arguments are sent as argv (no shell). Contract: estimator reads provided image/IMU indexes with correct native calibration and writes trajectory.tum (seconds, xyz in metres, qx qy qz qw) in the IMU pose frame. The harness records process logs, configuration hash and exit status. **No specific third-party engine wrapper is installed or implemented yet.** ORB-SLAM3 monocular-inertial and OpenVINS are the primary next integrations. Check ORB-SLAM3 GPLv3 before redistributing it in an APK.

## 4. Evaluate: metric scale / honest tracking coverage

    python tools/benchmark/bench.py evaluate \
      --truth ~/slam-data/EuRoC/MH_01_easy/mav0/state_groundtruth_estimate0/data.csv \
      --truth-format euroc --truth-frame imu \
      --estimate benchmark_results/euroc_mh01/engine_a/trajectory.tum \
      --estimate-format tum --estimate-frame imu \
      --align se3 --max-dt 0.02 --horizon 1 --max-gap 0.55 \
      --min-coverage 0.8 \
      --out benchmark_results/euroc_mh01/engine_a/report.json

Report:
- Metric rigid SE(3) ATE RMSE, median and P95.
- Relative translation and orientation RPE at a fixed 1 s interval.
- Observed time coverage over the available ground-truth time span.
- Distance error on observed contiguous intervals, sampled at approximately 5 Hz to reduce ground-truth jitter (NOT total route length when coverage drops).
- Input hashes, scale correction and alignment type.

SE(3) does not rescale distances. Sim(3) is permitted only as **an explicitly labelled diagnostic** for scale-ambiguous monocular VO; that result cannot prove metric distance accuracy. Timestamp matching uses a fixed tolerance and never optimizes a delay on held-out truth. Failures and missing samples reduce coverage. For paths in different body/camera frames, calibrate and transform the data before evaluating; no automatic extrinsic conversion is claimed. Validate results independently against the established EVO package.

## 5. Cloud and Google Drive

**GitHub Actions (already configured as code):** on relevant PR/push it installs pinned Python/NumPy, runs tests and catalogs. Normal CI never downloads huge datasets. The optional manual workflow mode accepts one explicit authorized HTTPS ZIP URL plus exact SHA256, validates/extracts it (4 GiB downloaded / 12 GiB expanded default bounds), prepares a real EuRoC/TUM VI sequence, and optionally evaluates a separately provided TUM IMU-frame trajectory with its own URL and checksum. Without an estimated trajectory, it audits files only — no VIO is executed.

Manual workflow_dispatch generally needs the workflow to exist on the **default branch**; while the PR is open, push/PR smoke tests work, and full runs can use a local or self-hosted runner. Do not include a dataset download link from a source whose terms do not permit this usage. GitHub-hosted cloud is fine for smoke and small individual sequences; larger datasets, GPU baselines and ROS bags should use private self-hosted Linux.

**Drive architecture:** retain original data once in private SLAM_BENCHMARKS/raw/{euroc,tumvi,advio,ronin,oxiod}. Keep source URLs, SHA256, licenses, split manifests, small reports and code in Git. Do not commit raw data. Google Drive Desktop on Windows must make datasets available offline before reading them in WSL; online-only placeholders can break a benchmark. An optional local rclone sync:

    rclone copy remote:SLAM_BENCHMARKS/raw/euroc/MH_01_easy \
      ~/slam-data/EuRoC/MH_01_easy --checksum

The remote is a preconfigured local rclone remote, not a token placed in GitHub. For private CI use a **private** self-hosted runner with a pre-synced dataset directory or an encrypted rclone secret. Google Drive connected to this chat does not give GitHub Actions or this compute container an automatic disk mount.

**Execution limitation in this chat:** this compute container cannot resolve GitHub or download full datasets; source changes can be submitted through the connected GitHub app and validated by hosted CI. No EuRoC/TUM VI accuracy run has been executed with the Android estimator here.

## 6. Data selection / ablations

| Priority | Corpus | Uses | Truth caveat |
|---|---|---|---|
| 1 | EuRoC | calibrated mono-inertial benchmark | drone motion, Vicon/Leica nuances |
| 2 | TUM VI | handheld room/corridor VIO | mocap valid in limited region |
| 3 | ADVIO | realistic smartphone indoor/outdoor + mag/baro | IMU/fix-point-derived reference |
| 4 | VECTOR phone captures | real target Android sampling/performance | no independent truth |
| 5 | RoNIN | camera-off PDR | subset access, research-only |
| 6 | OxIOD | pocket/hand/bag, accel/gyro/mag | Vicon or Tango-based references |
| 7 | RIDI | human IMU navigation, phone positions | Tango proxy not independent |
| 8 | TLIO | inertial network/filter baseline | dataset preprocessing/training |
| 9 | KITTI | driving VO | not representative of phone PDR |
| 10–15 | Hilti 2021/2022/2026, NTU VIRAL, TUM-VIE, external VIO examples | hard failures | specialized rigs and/or partial truth |

First dev trial: EuRoC MH_01_easy. Validate on unseen EuRoC and TUM VI room sequences. Held-out: corridor/low-light and new ADVIO/RoNIN/OxIOD persons and orientations. Lock engine configuration once before held-out tests. Keep IMU-only, camera-only, fused, GNSS-assisted and magnetometer/barometer ablations separate. When evaluating the Android app, also track FPS, CPU/GPU, battery, thermal load, absolute distance, stationary false distance, dropouts, and time sync quality.

## 7. Implementation status (not success claims)

| ID | Work item | Approx. implementation progress | Actual full real-data benchmark |
|---|---|---:|---|
| B00 | Audit repository data pipeline and limitations | 100% | N/A |
| B01 | Dataset catalog, links and limitations | 80% | not a parser for most sources |
| B02 | EuRoC, TUM VI and Camera2 CSV indexers | 75% | pending |
| B03 | Matching, rigid ATE/RPE, coverage/distance scoring | 75% | pending (fixture tested only) |
| B04 | External engine subprocess contract/log/provenance | 65% | no live VIO engine integrated |
| B05 | GitHub CI regression workflow | 90% | check current Actions run |
| B06 | Optional cloud real dataset ZIP ingestion | 70% | public real archive not yet checked |
| B07 | ORB-SLAM3/OpenVINS executable native adapter | 0% | pending |
| B08 | Android ARCore MP4 playback and reproducibility | 0% | pending |
| B09 | Common on-device/off-device estimator interface | 0% | pending |
| B10 | IMU-only navigation + RoNIN/OxIOD/RIDI loaders | 0% | pending |
| B11 | Independent phone survey/RTK/optical truth tests | 0% | pending |
| B12 | Held-out precision, robustness and thermal evidence | 0% | pending |

Aspirational development targets, not observed results: distance error <=2% on good walking routes and <=5% on running/varied routes at acceptable tracking coverage. A lower error on an incomplete trajectory does not meet these targets.

## 8. Primary upstream sources

- EuRoC: https://projects.asl.ethz.ch/datasets/euroc-mav/
- TUM VI: https://cvg.cit.tum.de/data/datasets/visual-inertial-dataset
- ADVIO: https://github.com/AaltoVision/ADVIO
- RoNIN: https://ronin.cs.sfu.ca/
- OxIOD: https://deepio.cs.ox.ac.uk/
- RIDI: https://yanhangpublic.github.io/ridi/index.html
- TLIO: https://github.com/CathIAS/TLIO
- KITTI: https://www.cvlibs.net/datasets/kitti/eval_odometry.php
- Hilti 2026: https://hilti-challenge.com/dataset-2026
- NTU VIRAL: https://ntu-aris.github.io/ntu_viral_dataset/
- EVO evaluation: https://github.com/MichaelGrupp/evo
- OpenVINS: https://docs.openvins.com/gs-datasets.html
- ORB-SLAM3: https://github.com/UZ-SLAMLab/ORB_SLAM3
- ARCore replay: https://developers.google.com/ar/develop/recording-and-playback
