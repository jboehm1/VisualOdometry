# VECTOR VIO — Smoke Rescue Option C: Research SDK v0.1

**Status:** reproducible and tested **RESEARCH/EDUCATION** release, October 19 2026. **NOT** a production navigation device, PPE substitute, rescue/evacuation aid or certified firefighter instrument. Development repository: https://github.com/jboehm1/VisualOdometry ; independent PR #17, \`feature/smoke-rescue-research\`.

## Deliverables that are actually finished

- Real ROS1 original-data CI: automatic transient retrieval of **UPO Smoke Exp7 (2,183,007,782 bytes)**. Calibrated original TF, Ouster sensor-only clock fit and actual left/right TI IWR6843 Doppler returns; true sensor-decode jobs **no synthetic data substituted**.
- Blind-execution research algorithms: **single-side Doppler+IMU** (baseline) and **dual side radar+device clock IMU** (TF + angular lever-arm compensation), no reference/world map/robot wheel odometry read at inference.
- Independent posthoc measurement: both SE(2)-registered pseudo-reference (not independent absolute truth) and early-marker-trained / **late marker held-out** results, with a stationary baseline. No fitted scale, no tuning on heldout labels.
- Dense LiDAR-vs-sparse radar co-observation research metric (radar echo within a sampled LiDAR XY neighborhood, **not obstacle recall**).
- Positive-echo-only 25 cm occupancy-evidence projection. Unknown regions never declared free or safe.
- Responsive standalone **VECTOR_SMOKE_LAB.html** with play/pause, drag/zoom, 60s/full/clock-calibrated/dual mode, source SHA256, measurable failures and certified safety warnings. Offline, no CDN or live API.
- Source-isolated \`thermal_contract.py\`: vendor-neutral temperature image JSONL for low-cost 32×24 MLX90640 or 160×120 FLIR Lepton radiometric frames, heat **bearings only**, no temperature object class/person/depth inventions. **Only synthetic thermal fixture tests**, no actual synchronized thermal+smoke dataset yet.
- Proven repeatability: second original UPO Smoke **Exp6 2,914,876,693 bytes** held-out without tuning the dual-radar algorithm. CI destroys original files after execution; retained metrics, no copyrighted GB distribution.
- Source & dataset register, hardware shortlist, hazard/validation plan, module coordination docs, automated Python unit tests and GitHub Actions.

## Scientific results (measured: NOT guaranteed accuracy)

| Dataset and engine | Sampled experimental duration | Posthoc SE2 RMSE vs partially odometry-smoothed AR-marker pseudo-reference | Stationary reference RMSE | Late held-out fiducial RMSE vs early-only alignment | Stationary reference on held-out |
| --- | --- | ---: | ---: | ---: | ---: |
| **UPO Experiment 7** one left radar + IMU (ROS bag receipt clock) | 168s | **3.242 m** | 2.930m | **5.361m** | 4.189m |
| **UPO Experiment 7** one left radar + IMU, sensor-only clock corrected | 168s | **3.250m** | 2.930m | not separately calculated | — |
| **UPO Experiment 7** calibrated dual radar+IMU | 168s | **3.395 m** | 2.930m | **5.733m** | 4.135m |
| **UPO Experiment 6**, completely separate measured trial, same dual radar+IMU | 267s | **2.916m** | 1.972m | **3.757m** | 2.621m |

*All these algorithms FAILED to beat a fixed-position baseline for the full evaluation by the same scoring method. Thus the research system is **not** validated for navigation.* The earlier first-60s Exp7 left-radar result **0.891m** vs stationary **1.788m** does **not** generalize. Exact estimated/true path and p95 values are recorded in actual CI JSON. Marker positions themselves may still depend on robotics odometry; direct markers are a stronger *proxy* than interpolation, **not guaranteed survey-grade independent truth**. The posthoc alignment uses test data to establish coordinate orientation/translation, so these are not absolute, deployable accuracy claims. Different datasets have different trajectories, and their errors cannot be interpreted as a controlled smoke-severity improvement.

**Sensor clock audit** (original Exp7): Ouster IMU header is device uptime vs radars' Unix epoch. Sensor-only affine fit ~**−28.32ppm**, 16,808 paired messages, p95–p05 arrival delay variation **14.2ms**. Hardware-to-host fixed delay still uncalibrated; this clock correction **did not improve** Exp7 pseudo-reference RMSE (3.250m vs 3.242m). TF from actual bag: radar_left base yaw +90° at x=0.10m,y=+0.05m; radar_right −90° at x=0.10m,y=−0.05m; IMU base yaw 0 at x≈0.106m,y≈−0.012m. Full roll/pitch and fixed-LiDAR-sensor survey verification remain outside the prototype.

**Real radar↔LiDAR proximity** Exp7 original: 8,549 left and 7,922 right radar echoes matched near-in-time LiDAR frames; fraction with **some sampled LiDAR XY within 0.5 m** = **86.44% left / 93.21% right** (2D only, ≤1200 deterministically decimated LiDAR points per scan, timestamps p95 ~47/50ms). LiDAR is smoke-affected and also produces ghosts/limited returns; these fractions are NOT object detection recall, collision clearance or evidence of a safe path.

## Original GitHub Actions evidence

- [Full original UPO Exp7 168s radar/gyro + map run](https://github.com/jboehm1/VisualOdometry/actions/runs/39211106764), artifact JSON+HTML, 35 tests at that exact SHA.
- [Original Exp7 dual calibrated radar + heldout direct markers](https://github.com/jboehm1/VisualOdometry/actions/runs/39213948099), artifact portable offline HTML and JSON, 55 unit tests.
- [Original Exp7 radar/LiDAR geometric comparison](https://github.com/jboehm1/VisualOdometry/actions/runs/39214256329), 55 unit tests, co-observation result and downloadable \`VECTOR_SMOKE_LAB.html\`.
- [Distinct Exp6 frozen-model held-out original-data trial](https://github.com/jboehm1/VisualOdometry/actions/runs/39214548698), 55 offline tests, genuine 2.91GB bag and metadata-only artifact. **The original recordings themselves are NOT uploaded to our repo.**
- Source of original UPO recordings and license: https://robotics.upo.es/datasets/smoke/ .
- Synchronized thermal + 60GHz radar + IMU (non-smoke) reference: https://christopherdoer.github.io/datasets/irs_rtvi_datasets_iros2021 . Documented only, not ingested in this release.
- Published related smoke radar/foot IMU: SmokeNav, https://doi.org/10.1002/aisy.202400241 .
- EU SmokeBot: https://www.smokebot.eu/project.html .
- Dräger thermal camera: https://www.draeger.com/en_uk/Products/UCF-FireCore .
- Qwake firefighter HUD: https://www.qwake.tech/ .

## How to execute — compact smoke R&D pipeline

Requires Python 3.11+, *standard library* for synthetic/test replay. Original ROS bag code needs pinned \`rosbags==0.11.4\` (no ROS installation).

\`\`\`sh
python -m unittest discover -s tools/smoke_rescue/tests -p 'test_*.py' -v
python tools/smoke_rescue/replay.py --demo --out /tmp/smoke-research-demo.html
\`\`\`

CI automatizes the **real measured** UPO recordings, source hashes, tf-clock audit, reference-blind dual-radar estimate, posthoc scoring, side-radar/LiDAR comparison and dashboard packaging via \`.github/workflows/smoke-upo-real.yml\`. Additional held-out dataset \`.github/workflows/smoke-upo6-heldout.yml\`. Both are opt-in/run-on-source-change, not scheduled infinite tests.

For genuinely acquired thermal grid samples (research only), capture one JSON object per line e.g.:

\`\`\`json
{"timestamp_s":1690000000.01,"clock":"unix_host","sensor_frame":"thermal_sensor","width":8,"height":8,"hfov_deg":55,"source":"synthetic_test_only","apparent_temperature_c":[20,20,20,20,20,20,20,20]}
\`\`\`

The example deliberately omits the other 56 pixels; real input MUST supply width×height values. Use \`--thermal-jsonl\` for actual pixel frames. No distance, victim identity or fire-safety signal is inferred.

## Cheap physical lab bill of materials (NOT purchased)

| Purpose | Candidate | Budget ballpark | Required validation |
| --- | --- | --- | --- |
| Very cheap **person tracking**, **not wall mapping** | Hi-Link LD2450 24GHz | ~€10–45 | UART frame decoding, moving person only, no raw 3D point cloud; must NEVER use for obstacle map |
| Lowest-cost thermal pixels | M5Stack/Melexis MLX90640 32×24 | ~$65 / €70–90 | MCU/I2C/USB telemetry, low spatial resolution, operating temperature limitations |
| Cheapest actual 3D mmWave development | TI AWRL6432BOOST 2Tx/3Rx | ~€140 **ex VAT** | Manufacturer SDK/firmware point-cloud demo; static obstacles, range, angular resolution and supply |
| Slightly richer real 3D radar | TI AWR6843AOPEVM 3Tx/4Rx | ~€175 **ex VAT** | Same, USB point cloud ≠ raw ADC or detailed 3D shape |
| Laboratory mechanical/electrical parts | cabling, MCU, rigid fixture, regulated power | ~€40–100 | calibration/clock stamping; no hot-fire enclosure or PPE certification |

The previous hardware comparison and primary links are in [SMOKE_RESCUE.md](SMOKE_RESCUE.md). Prices are historic snapshots; check actual seller and delivered VAT. **Do not buy before checking which radar provides usable static point-cloud data**; presence-only radars cannot validate walls, stairs or safety.

## Unmet delivery acceptance gates — cannot honestly mark complete

1. True **radar SLAM** (landmark association, loop closure, multi-session map correction) and independently validated low localization error, **failed** on our 2 measured datasets.
2. True synchronized **thermal + radar + IMU** recorded through smoke: not publicly validated here; UPO has no thermal, IRS RTVI no verified smoke recordings.
3. Android S21 driver/USB interface + helmet/HUD prototype & physical power/heat/water protection, device environmental checks, electromagnetic/radio and PPE compliance; **requires purchased hardware and supervised experiments**.
4. Smoke severity, spray/steam/fog/metal reflections, real hot-fire structural obstacle recall, safe path reliability and failures — **not certified, not tested as life-safety**.
5. Commercial restrictions: UPO data **CC BY-NC-SA 3.0**, may not be redistributable for proprietary product model training; check licensing with owner.
6. No supplied low-cost radar/sensors physically purchased or sampled; prices are not verified purchase quotations.

**Decision:** Ship the *research SDK* and standalone viewer, but **do not present an operational firefighter product** or silently integrate an unqualified "navigate to exit" mode. The negative results are useful for choosing higher-resolution radar or adding stronger orientation and external landmarks. A life-safety device can only be accepted through independent fire service trials and relevant professional certification.
