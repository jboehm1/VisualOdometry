# VECTOR VIO — Smoke Rescue / Option C

Research branch feature/smoke-rescue-research · literature / prices checked 2026-10-19.
**RESEARCH ONLY. Never a certified firefighter navigation or life-safety tool.**

## Decision and independence
Build a modular radar (mmWave) + LWIR thermal + IMU/PDR lab demonstrator, subsequently a multimodal metric occupancy and position-estimation system. Start using synthetic offline replay and genuine independent datasets, buy cheap devices ONLY after confirming data interface. No claim of seeing a photographic image through smoke. Unknown/unobserved does NOT mean free/safe.

Read AGENTS.md and docs/DEVELOPMENT_COORDINATION.md on feature/slam-explorer, SHA 5f0105effd4fb2fb66b7d23620189c9605576de7. PR #15 already adds Atlas V5 smoke/firefighter use case; PR #16 develops modular tests; PR #5 develops Android camera-free PDR. Do not modify Android MainActivity, Manifest, DATA menu, PDR, existing workflows, central roadmap, Atlas, or shared dataset catalogues. This branch adds only dedicated tools/smoke_rescue, docs/SMOKE_RESCUE*, docs/coordination/smoke-rescue.md and a new isolated workflow.

## 1. Observability and limits
| Sensor | Observation | Advantage | Limitation |
| --- | --- | --- | --- |
| RGB / LiDAR | optical reflected light / time of flight | clear-air 3D detail | smoke/backscatter/no return |
| LWIR thermal 8–14um | angular thermal pixel contrast | people, heated targets, potential shape in smoke | NO depth; steam, hot backgrounds, flames, saturation, low contrast; thermal image can fail |
| 24/60/77 GHz FMCW | range/angle/radial velocity/reflection | relatively robust smoke penetration | wall specularity, ghosting, multipath, sparse/limited angular resolution, missed static items |
| Multi-antenna 4D radar | denser 3D plus Doppler | more useful geometry/classes | price, weight, power, processing |
| Foot IMU ZUPT | acceleration/rotation + stance constraint | local motion between scans | yaw drift, stairs/crawling/slippage, sensor placement |
| Smartphone IMU/PDR | pedometer/gyro orientation | existing S21, low cost | phone yaw != body heading; crawling may not produce steps |
| UWB with anchors | distances to known anchors | cooperative localization | requires survey/beacon deployment, NLOS error |

Fusion must preserve raw radar returns, thermal pixels, IMU and clock bases separately. Sensor time/extrinsics + calibrated confidence are required before image registration. Thermal-only angular detections must NOT be positioned at invented distances. Missing radar returns leave cells UNKNOWN, not free. No automatic emergency evacuation routing permitted.

Primary evaluation literature: https://www.nist.gov/publications/performance-metrics-fire-fighting-thermal-imaging-cameras-small-and-full-scale and https://www.nist.gov/publications/measurement-effective-temperature-range-fire-service-thermal-imaging-cameras

## 2. Existing solutions and opportunities
| Existing work | Strength | Limit / opportunity |
| --- | --- | --- |
| Qwake C-THRU Navigator, https://www.qwake.tech/ | firefighter thermal/HUD with visual enhancement and networking | cannot infer accurate radar 3D mapping or fire escape localization solely from marketing; price by quote |
| Dräger FireCore, https://www.draeger.com/en_uk/Products/UCF-FireCore | helmet thermal + in-mask display, PPE-oriented ergonomics | thermal observation, not radar SLAM |
| EU SmokeBot, https://www.smokebot.eu/project.html | 3D radar, stereo-thermal, gas, LiDAR + vision in smoky robotic scenarios | robotic bulk and different platform; useful open research and Dortmund data |
| SmokeNav 2024, https://doi.org/10.1002/aisy.202400241 | **nearest prior art**: TI IWR1843 with foot-mounted ZUPT inertial unit, probabilistic occupancy, radar reflectivity semantic map, measured smoky rooms | paper does not establish certified wearable system; dataset and full runnable code release NOT confirmed; walking foot constraints not automatically crawling-ready |
| Indoor FireRescue Radar (IROS 2025), https://huggingface.co/datasets/yysd123/indoor_mmwave | 4D radar, RGB, LiDAR, IMU; 27k frames, objects labeled, smoke tests with firefighters | cascade imaging-radar hardware unlike cheap 3Tx/4Rx board; no thermal, full data on request; CC-BY-NC-4.0 |
| NIST thermal studies | controlled performance conditions smoke/steam/flame/ETR | not a location/navigation product |

Improvement hypotheses, **unproven**: (a) fail-closed three-state grid with demonstrated low false-clear rate, (b) angle/multipath/conflict-quality gates before occupancy updates, (c) crawling/stairs/ladder trials with foot-ZUPT versus phone PDR, (d) radar reflectivity + Doppler + thermal cues with calibrated depth only, (e) online pose confidence and drop/stale markers, (f) reduce thermal saturation/contrast issues via sensor selection and adaptive but non-misleading display, (g) model generalization across completely held-out buildings/densities, (h) lightweight Android display with separate data channel and optional anchor help, (i) future ADC denoising and 4D radar when physically available.

## 3. Buy later: exact low-cost alternatives
All price observations are snapshot only (2026-10-19), EUR distributor figures usually **ex VAT**, USD **ex shipping/taxes**. Reconfirm stock/VAT/compatibility.

| Choice | Price listed | Access | Assessment |
| --- | ---: | --- | --- |
| Hi-Link LD2450 24GHz | roughly €15–45 RETAILER-DEPENDENT ESTIMATE | UART x/y/speed up to 3 people-like targets, about ±3m lateral / 6m forward | inexpensive detection comparison; **cannot map walls**, no dense point cloud, no ADC; not suitable as actual radar SLAM |
| M5Stack MLX90640 Unit | **$65** manufacturer | I2C real thermal pixels 32x24, requires MCU | cheap thermal proof, insufficient thermal resolution for life-critical image |
| M5Stack T-Lite | **$79** manufacturer | 32x24 sensor, MCU and screen | fastest standalone thermal demonstrator |
| Pimoroni MLX90640 BerryBase DE | **€74.90 incl VAT**, listing out-of-stock | 32x24 I2C | convenient EU option if restocked |
| **TI AWR6843AOPEVM** | **€174.92 ex VAT**, Mouser | USB point cloud with appropriate demo, requires vendor firmware/config; ADC requires extra board | best slightly higher-aperture starting point (3TX/4RX compared with 2TX/3RX of cheaper AWRL6432BOOST) |
| **TI AWRL6432BOOST** | **€139.75 ex VAT**, Mouser | USB point clouds, 2TX/3RX; 120°az/80°elev | **lower-cost real mapping-capable radar contender**; verify usable static returns, low-power firmware and FoV |
| TI IWR6843ISK-ODS | **€205.44 ex VAT**, Mouser | USB point-cloud wide 120°x120° FoV | more elevation coverage |
| TI IWR6843ISK | **€248.23 ex VAT**, Mouser | USB point-cloud 120°az x30°elev | less attractive elevation for stairs |

Sources: https://www.mouser.de/de/ProductDetail/Texas-Instruments/AWR6843AOPEVM ; https://www.mouser.de/de/ProductDetail/Texas-Instruments/AWRL6432BOOST ; https://www.ti.com/tool/AWRL6432BOOST ; https://www.mouser.de/de/ProductDetail/Texas-Instruments/IWR6843ISK-ODS ; https://www.mouser.de/de/ProductDetail/Texas-Instruments/IWR6843ISK ; https://www.ti.com/tool/IWR6843ISK-ODS ; https://shop.m5stack.com/products/thermal-camera ; https://shop.m5stack.com/products/m5stick-t-lite-thermal-camera-dev-kit-mlx90640 ; https://www.berrybase.de/mlx90640-waermebild-kamera-breakout-550 ; https://esphome.io/components/sensor/ld2450/

**False bargains:** Seeed MR60BHA2 vendor confirms no raw ADC/IQ/FFT access, only processed vital-sign information: https://github.com/Seeed-Studio/wiki-documents/discussions/3362 . Do NOT substitute human-presence module for true 3D obstacle mapping.

Stage-1 full approximate budget: **€90–170** (cheap 24GHz human tracker + M5 thermal 32x24 + ESP32/USB/power) detects moving targets/heat only, NO map. Stage-2 **~€250–340** with AWRL6432BOOST 2TX/3RX or **€300–420** with AWR6843AoP 3TX/4RX (plus MLX90640 + power/MCU/cables) is the recommended approximate radar-cloud + thermal hardware laboratory test. DCA1000/MMWAVEICBOOST may be needed for RAW ADC: USB demo point-cloud does NOT imply access to RF samples. Confirm serial interface, correct supply, board thermal operating limits and EU radio conformity. Do not put unprotected consumer electronics near fire.

## 4. Actual closest research datasets
Status words: DISCOVERED = link/description researched; NOT DOWNLOADED, NOT PARSED, NOT BENCHMARKED. See SMOKE_RESCUE_DATASETS.json for machine register.

| Rank | Source | Modalities and smoky relevance | Access and caveats |
| --- | --- | --- | --- |
| **P0** | SNAIL Radar Indoor https://snail-radar-indoor.github.io/ | 4D radar+LiDAR+IMU, handheld abandoned hospital and hotel with smoke; 217 sequences / **255.68 GB** claimed | huge; inspect smallest relevant smoke sequence first; no thermal; site shows future-dated changelog **2026-10-30** relative to audit 2026-10-19, availability unverified |
| **P0** | Indoor FireRescue Radar https://huggingface.co/datasets/yysd123/indoor_mmwave | 27k RGB+LiDAR+4D radar+IMU frames, 10 buildings/35 layouts, 3D boxes + actual fire-training smoke | ~14.9GB HF subset; full by email; CC-BY-NC-4.0; array much higher resolution than cheap radar |
| **P0 (first practical data)** | UPO Smoke https://robotics.upo.es/datasets/smoke/ | seven underground (12 x 6 m) artificial-smoke experiments, **4× TI IWR6843**, Ouster lidar, RGB and wheel odometry, **AR-marker-assisted partial ground truth** | Experiment 7 is smallest: **2.0 GiB displayed / 2,183,007,782 bytes**; ROS1 topics including /ti_mmwave_left_radar/radar_scan_pcl and /ground_truth; marker-derived pose partly interpolated using odometry, NOT fully independent for every frame. License **CC BY-NC-SA 3.0**. The exact hardware match is a strong reason to prioritize it. |
| **P0** | Dortmund SmokeBot https://zenodo.org/records/1489911 | ~10.7GB radar + Velodyne, real tests with firefighters and smoke | robot sensor platform, no guaranteed independent GT |
| **P1** | SmokeBasement https://zenodo.org/records/15173055 | ~27GB stereo thermal + Hesai LiDAR in firefighter exercise smoke, six sessions | NO radar; imperfect sync/host timestamp; calibrations partly provided |
| **P1** | MSC RAD4R smoke https://mscrad4r.github.io/smoke/overall/ | radar-oriented smoke-specific sequences | outdoor campus ~0.8km loops, not building rescuer; downloads require password |
| **P2** | SEW multimodal AMR https://github.com/SEW-Eurodrive-Open-Source/Multimodal_AMR_dataset | thermal + radar + ToF + RGB + LiDAR | no confirmed active smoke scenario; industrial robot |
| **P2** | MMVR https://zenodo.org/records/12611978 | indoor human multi-view mmWave radar, ~79.9GB | no controlled fire smoke, motion/body class research not nav |
| **P2** | MmodalFire https://doi.org/10.1038/s41597-026-06810-6 | fire smoke density/heat/UV/IR radiation and RGB | fire DETECTION not radar localization |

For every adapter require: per-source license, URL, downloaded size and sha256, clocks/extrinsics/frame definitions, true smoke yes/no, independent GT method, held-out splits, exact executable and independent metrics. Never commit the GB raw recordings to repository, never call mere catalog entries runnable. SmokeNav research data/code have not been proven openly downloadable.

## 5. Implemented offline module — TRUE status

Runnable pure-standard-library Python **synthetic** lab replay (standalone, no Android files changed):

    python -m unittest discover -s tools/smoke_rescue/tests -p 'test_*.py' -v
    python tools/smoke_rescue/replay.py --demo --out /tmp/smoke-demo.html

Open HTML offline. Demo has an **invented wall at x=4m**, exact synthetic poses and two thermal directional cues. It is NOT recorded radar, no physical smoke optical model, not a navigation estimator. CLI takes normalized JSONL only:

    python tools/smoke_rescue/replay.py --input frames.jsonl --out /tmp/replay.html

Frame example (one per line):

    {"timestamp_s":0.1,"pose":{"x_m":0,"y_m":0,"yaw_rad":0,"source":"external_reference"},"radar":[{"range_m":3.0,"azimuth_rad":0.3}],"thermal_bearings":[0.2]}

- timestamp_s strictly increasing; pose x/y meters local coordinate frame, yaw radians CCW from +x, declared provenance synthetic / external_reference / unverified_estimate. Pose **supplied, not estimated**; external reference must NEVER enter odometry estimator in a claimed independent benchmark.
- Radar is positive range + azimuth detections (0.2..20m). Sensor origin assumed at pose: NO extrinsic/time calibration, RF quality, multipath or sensor uncertainty gate yet.
- thermal_bearings radians only; draws angular indication of arbitrary **glyph length**, never calls it range or obstacle. No thermal image decoding/pixel fusion.
- 0.25m default probability-like bounded log-odds map, marking only cells traversed by valid returns; no return => unknown (not free); not accurate enough to guarantee clearance.
- User-facing static HTML explicitly separates occupied/free/unknown and marks synthetic provenance; no route instruction.

**NOT implemented:** vendors UART/ADC/ROS drivers, replay of smoke datasets, thermal-grid fusion, joint time sync, localization radar-SLAM, Java PDR integration, real tests, Android APK, trained AI, PPE-safe hardware, evacuation route or CE/NFPA qualification. CI unit+synthetic test only; record exact run SHA after it completes.

## 6. Staged scientific validation requirements
1. Download **small** real smoke sequence with licensed access and hash; decode real frame, verify clocks/extrinsics. Start with the **smallest UPO experiment 7 (~2.18GB)** or IFR subset, and do a download HEAD/partial index audit before any full transfer, reserve huge SNAIL later. No GT leakage.
2. Clean-air experiments measured independently: ranges 0.5 /1/2/4/6m, x/y angles, persons **and nonhuman obstacles**, moving and static; repeats x3. LD2450 is human-only comparison, not mapping metric.
3. Thermal on target vs hot background at known distances; test saturation and false person detections.
4. Authorized ventilated artificial fog/smoke, no flame; measured opacity and humidity; match clear control. Test water mist separately. NO unqualified fire exposure.
5. Walk, run, squat, crawl, stairs, body rotation and no-motion. Compare actual foot IMU vs Java Android PDR replay separately. Do not substitute Python baseline results for Java engine.
6. Full ablation: radar-only, thermal-only, inertial-only, fusion; held-out buildings/densities and independent clocks/truth.
7. Robustness: radar dropout, multipath metal wall, thermal occlusion/steam, dropped frames, USB disconnect, wrong sensor mounting, low batteries, extreme temperature. Uncertain is safer than a confident wrong map.
8. Later user study: HUD readability, glove controls, no interference with SCBA/PPE, accessibility, thermal and electromagnetic compliance with specialists.

**Desired future acceptance targets — unmeasured, not promises:** obstacle recall ≥95% and explicit false positive rate on HELD-OUT set; error median ≤0.3m over known 0.5–5m targets with p95; false-free-clear rate ≤1% on labeled route with UNKNOWN rate reported (to prevent empty-map gaming); latency p95 ≤250ms tracked with age/staleness; ATE/RPE from independent localization truth after preset alignment protocol. Must report per sensor, environment, static/dynamic, range; no automatic "safe exit" label.

## 7. Roadmap / honest progress
- S00 research source and competitor inventory: documented, source URLs provided; prices snapshot pending future vendor confirmation.
- S01 research replay/occupancy/thermal-angle render: **implemented, synthetic only**; first exact-head CI **39174535005 SUCCESS** at 06bf8e5f03faf15dbae1ba2830e4cf91bf821d5e, 8 Python unittest tests and 11-frame/55-return generated HTML; changes after that SHA require fresh CI. https://github.com/jboehm1/VisualOdometry/actions/runs/39174535005
- S02 real smoke dataset adapter: not started, no bytes downloaded.
- S03 benchmark occupancy vs ground truth: not started.
- S04 calibrated pixel-level thermal fusion: not started.
- S05 radar + independent inertial odometry: not started.
- S06 Android S21 separate plug-in integration: not started; do not collide with PDR/Atlas/bench branches.
- S07 nonhazardous controlled smoke physical tests: not started.
- S08 specialist hazard and certification review: not started; no release for fire service.

**Handover:** recheck fresh integration SHA and PR #15/#16/#5 immediately before touching shared files. Merge as isolated PR only after exact CI, review license of data and testing limitations. No claim of background progress or access to other chats unpushed work.

### Extra checked sources / dataset detail

- TI xWRL6432 (2TX/3RX, compact low-power; 120° azimuth and ~80° elevation board): https://www.ti.com/tool/IWRL6432BOOST ; distributor price https://www.mouser.de/de/ProductDetail/Texas-Instruments/AWRL6432BOOST . Point-cloud output exists but low angular resolution and minimum valid obstacles need bench verification. Compare static corners and person targets against AWR6843AoP before selecting.
- UPO university source gives robot hardware + marker assisted partial GT and license https://robotics.upo.es/datasets/smoke/ . Experiment 7 **ROS bag index** https://robotics.upo.es/datasets/smoke/experiment_7.txt lists 168s, RadarScan, PointCloud2, compressed RGB, lidar and /ground_truth; experiment_7.bag size 2,183,007,782 bytes. No samples downloaded during this audit; a downloadable bag being online is NOT successful parsing or estimation.
