# Unified benchmark CLI — real-world odometry validation

**Integration target:** `feature/slam-explorer`.  
**Command:** `python benchmark.py …` (equivalent to `python tools/campaigns/unified.py …`).  
**Python:** 3.11. Install `tools/benchmark/requirements.txt` for the full evaluation jobs; `javac`/Java 17 required for the native Java PDR tests. Public recording downloads require network access when using SIMUL or published VINS-Mono trajectories.

This command is a **registry of truthful, executable combinations**, not a universal dataset decoder. New raw dataset formats and new estimators require independent implementations and validation.

## Quick start

```bash
python benchmark.py catalog
python benchmark.py plan --dataset simul --engine android-pdr
python benchmark.py run --dataset simul --engine android-pdr --out campaign_results/simul
python benchmark.py run --dataset euroc_external --engine external-vins --out campaign_results/euroc_vins
# Real phone: 71MB ADVIO camera+gyro AUDIT with external Pixel ARCore scoring
python benchmark.py run --dataset advio09 --engine external-pixel-arcore --out campaign_results/advio09
# Real phone: 951MB Mobile-GVIO actual research image+gyro inference; Sim3 shape only
python benchmark.py run --dataset mobile_gvio --engine research-mono-gyro --out campaign_results/mobile_gvio
# The V3 suite produces comparable *reports*, NOT directly comparable accuracy metrics:
python testenv.py run --suite tools/testenv/suites/real-phone.json --out campaign_results/real_phone
```

The published **SIMUL** run automatically fetches four actual recordings from 4 different people (12 tests: 3 carrying positions each); the Java PDR engine is compiled from the pinned parallel workstream. The Python PDR baseline is also executed, explicitly labeled secondary in the report. The annotations are only loaded **after** the estimates and give **step intervals**, not independent metric displacement.

The **EuRoC external** run evaluates actual published VINS-Mono poses (not raw images through a VIO engine). It validates alignment and scoring methods, **not** our Android visual tracking.

### Import locally available data

```bash
# Raw EuRoC and TUM VI sensor files: timestamps/calibration indexing, no estimator
python benchmark.py run --dataset euroc --engine sensor-index --root /datasets/MH_01_easy --out campaign_results/euroc_index
python benchmark.py run --dataset tumvi --engine sensor-index --root /datasets/room1 --out campaign_results/tumvi_index

# External pose estimate in the same declared IMU reference frame, raw EuRoC folder
python benchmark.py run --dataset euroc --engine external-trajectory \
  --root /datasets/MH_01_easy --estimate /datasets/estimate.tum \
  --out campaign_results/euroc_external_pose_score

# Real OxIOD native synchronized CSV; source-specific sensor axes/time must be checked
python benchmark.py run --dataset oxiod --engine python-pdr \
  --input /datasets/oxiod/syn/imu1.csv --out campaign_results/oxiod_python
python benchmark.py run --dataset oxiod --engine android-pdr \
  --input /datasets/oxiod/syn/imu1.csv --out campaign_results/oxiod_java

# Local canonical event CSV: t_ns,type,x,y,z (accelerometer m/s², gyro rad/s)
python benchmark.py run --dataset canonical --engine python-pdr \
  --input /datasets/imu.csv --stride-m 0.70 --yaw-axis z \
  --out campaign_results/pdr_python
python benchmark.py run --dataset canonical --engine android-pdr \
  --input /datasets/imu.csv --stride-m 0.74 \
  --heading-csv /datasets/device_heading.csv \
  --heading-source logged_phone_rotation_vector \
  --out campaign_results/pdr_java

# Exported app session; independent tape-measured length is OPTIONAL but required
# to claim independent path-distance accuracy. It does not imply 6DoF reference.
python benchmark.py run --dataset phone_csv --engine csv-audit \
  --input /datasets/my_phone_session.csv --known-distance 50.0 \
  --out campaign_results/phone
```

`--heading-csv` contains `t_ns,heading_rad` and is **measured orientation**, never derived from ground truth. The Java replay receives `STEP_LENGTH` as a real configuration event and `HEADING` as actual sensor measurements; the source file, replay hashes and Java source commit are recorded. Raw gyroscope events **do not** constitute absolute heading. Python PDR uses a specified assumed yaw axis and does **not** consume the measured heading CSV.

## Exact dataset support and claims

| Dataset | Registered engine(s) | Can run now? | What can actually be scored? |
| --- | --- | --- | --- |
| **SIMUL** | `android-pdr`, `python-pdr` | **Yes**, public real files fetched automatically; 4 subjects × 3 placements already executed | Independent **step interval / count / F1**, NOT metric distance |
| **EuRoC published example** (`euroc_external`) | `external-vins` | **Yes**, published poses downloaded automatically | Real trajectory ATE/RPE/coverage **for external VINS-Mono**; estimator not executed |
| **EuRoC raw** (`euroc`) | `sensor-index`, `external-trajectory` | **Yes with local raw files**, and an external estimate for scoring | Index count, optionally independently scored user-supplied estimate, not VECTOR/ARCore |
| **TUM VI raw** (`tumvi`) | `sensor-index` | **Yes with local raw files** | Index count only; not executed native VIO |
| **OxIOD raw CSV** (`oxiod`) | `android-pdr`, `python-pdr` | **Code path available with input**; complete OxIOD sequence/pose truth not qualified | Inferred steps/stride/distance; **no independent trajectory accuracy** |
| **Canonical IMU** (`canonical`) | `android-pdr`, `python-pdr` | **Yes with input**; Java and Python runners executable | Step metrics and inferred distance, not ground-truth error |
| **VECTOR exported phone CSV** (`phone_csv`) | `csv-audit` | **Yes with exported CSV** | Phone-reported distance/route; relative distance error only with independently measured path length |
| **ADVIO-09 smartphone** (`advio09`) | `external-pixel-arcore` | **Yes**, pinned public ~71MB ZIP; raw video+gyro audit and **previously recorded** Google Pixel ARCore | Rigid SE3 ATE for external Pixel trajectory against fixation-aided reference, NOT our app | 
| **Mobile-GVIO Indoor-2 smartphone** (`mobile_gvio`) | `research-mono-gyro` | **Yes**, pinned ~951MB public archive + ~2.8GB extracted; actual research image+gyro inference | Posthoc-clock + posthoc-scale **Sim3 trajectory shape** only, NOT independently calibrated metric odometry |
| **RoNIN, RIDI, KITTI, Hilti, TUM-VIE, TLIO…** | None in this CLI yet | **No**, catalog only | No valid score available through this CLI |

**Important:** The `android-pdr` name refers to the **real pinned Java PdrEngine algorithm**, compiled from `feature/camera-free-pdr` at `f8228aa7…`. It does **not** assert that this feature is already merged into the installable `feature/slam-explorer` APK. It does not execute Android hardware `STEP_DETECTOR` on the cloud runner; instead it uses the Java accelerometer fallback.

## Result contract and provenance

Each `run` creates `status.json` and `report.json` (and underlying engine-generated files) with:

- Exact `dataset_id`, `requested_engine`, `measured_engine`; distinguish a published **trajectory scorer** from a live estimator execution.
- `actual_estimator_executed` and `reference_used_to_score`, so successful sensor indexing cannot masquerade as an odometry benchmark.
- `capability`, `accuracy_claim_level`, `reference_kind`, `limitations`.
- Metrics **only** supported by that test. Example: SIMUL returns step-count and F1 but not ATE; canonical without GT returns counts and prior-scaled distance, not accuracy.
- SHA256 hashes and pinned upstream Git commits when available.
- A failure writes `FAILED` in both result files and produces a nonzero exit code. Invalid dataset-engine combinations are explicitly rejected.

Data and reports live under the chosen `--out` folder. A SIMUL run currently **downloads public originals into that workspace**. The CI artifacts upload **only small reports**, not raw IMU recordings or private phone logs. Remove workspaces after completion if disk space is constrained.

## GitHub Actions and Drive

The new standalone workflow `.github/workflows/unified-benchmark.yml` tests:

1. Registry, invalid combinations, refusal of silent ground-truth leakage and synthetic canonical fixture regression;
2. The **actual Java** and Python PDR executables on deterministic canonical IMU input;
3. Four **real** SIMUL human recordings with the exact Java engine and independent step annotations;
4. Published EuRoC VINS-Mono pose evaluation with explicit `actual_estimator_executed=false`.

When GitHub workflows are active on the integration branch, changes to this CLI rerun them. The default repo branch remains old `master`; a `workflow_dispatch` UI button may require updating the default branch, so normal `push`/PR CI is the current proven trigger.

Reports can be transferred **from ChatGPT** to private Google Drive using connected GitHub and Drive apps. **GitHub → private Drive automatically** requires an explicitly authorized credential/secret; this is not implied by green CI. The separate `drive-campaigns.yml` workflow has not yet been proven with the configured `SLAM_RCLONE_CONFIG_B64` secret.

## Smartphone datasets added in V3

The source programs and their original evidence are documented in [V3 real-phone suite](TEST_ENV_PHONE_V3.md). The records use different reference definitions: rigid SE3 for external previously recorded Pixel ARCore versus truth-aligned Sim3 *shape only* for actual research image+gyro output. Both declare their source engine and exact accuracy-claim level and retain no raw videos in CI artifacts. These errors are **not comparable** with each other or the EuRoC published VINS score.

## Next engineering gates

- Integrate the PDR Java Android module through its own reviewed PR, preserving DATA menu and manifest entries from the other parallel features.
- Add audited real OxIOD and RoNIN native reference decoders; match sensor axes, timestamps, 2D frames and quality levels. Truth must remain out of estimator inputs.
- Run an actual calibrated raw camera+IMU VIO estimator such as OpenVINS or ORB-SLAM3 on EuRoC and TUM VI; evaluate ATE/RPE and missing coverage.
- Test official ARCore video replay and S21 live performance **on a physical compatible Android device**; a Linux GitHub runner cannot certify it.

**No production-accuracy claim is made from a green CI alone.**
