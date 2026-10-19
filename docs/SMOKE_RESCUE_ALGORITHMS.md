# Smoke Rescue — measurable real-world radar/IMU R&D (not navigation)

Date: 2026-10-19. Branch: feature/smoke-rescue-research. Review against AGENTS.md and docs/DEVELOPMENT_COORDINATION.md on current Android integration. No Android production UI is modified.

## Definition of the current experiment

**Actual real-data workflow**: .github/workflows/smoke-upo-real.yml automatically downloads the exact **UPO indoor artificial smoke Experiment 7** original ROS1 bag (2,183,007,782 bytes) transiently. The bag is CC BY-NC-SA 3.0; CI publishes reports only, then deletes the original. Source: https://robotics.upo.es/datasets/smoke/ . Its original index is https://robotics.upo.es/datasets/smoke/experiment_7.txt .

The existing **canonical Test Environment V2** \`upo_smoke / radar-sensor-audit\` checks original PointCloud2 radar and LiDAR stream statistics. A new **separate experimental engine** \`tools/smoke_rescue/doppler_odom.py\` reads original RadarScan *velocities* plus original lidar IMU angular speed and constructs research-only local planar positions. It additionally displays radar points in local estimated coordinates **without inferring free space**.

Do not mix these results or describe them as a validated SLAM system. There is **no loop detection**, no map/pose optimization, no camera or thermal sensing in UPO, no absolute heading and no safety-grade localization. Estimates are **open-loop inertial yaw + Doppler velocity** and will drift. Do not use any path to guide human emergency exits.

## Original sensor verification (real GitHub Actions)

A real source probe decoded the underlying UPO headers and found:

| Topic | Original header frame | Capture clock |
| --- | --- | --- |
| \`/ti_mmwave_left_radar/radar_scan\` | \`ti_mmwave_left_radar\` | Unix epoch, approximately 1606476287.55 s |
| \`/ti_mmwave_right_radar/radar_scan\` | \`ti_mmwave_right_radar\` | Unix epoch |
| \`/os1_cloud_node/imu\` | \`os1_imu\` | **sensor uptime approximately 2788.85 s**, not Unix |
| \`/ground_truth\` | \`map\` | Unix epoch |
| Radar PointCloud2 | \`ti_mmwave_left_radar\` | Unix epoch |

**Important:** ROS IMU header time is in a different clock domain. Comparing it directly to radar would produce an invalid filter. The initial research estimator explicitly substitutes ROS **bag receipt time** for the IMU, which introduces unknown latency/jitter. This must be improved by measured hardware-uptime-to-host clock transformation before any metric claims. Original samples also show RadarScan messages carry velocity, although PointCloud2 exposes only x,y,z,intensity. \`range\` and \`bearing\` in first custom RadarScan samples are zero even when x,y,z nonzero: calculate 2D geometry from x,y, NOT the published range=0.

Published raw message geometry resembles the TI ROS custom \`RadarScan\` definition at https://git.ti.com/cgit/mmwave_radar/mmwave_ti_ros/tree/ros2_driver/src/ti_mmwave_rospkg_msgs/msg/RadarScan.msg ; related README https://github.com/radar-lab/ti_mmwave_rospkg .

TF is available in \`/tf\` and \`/tf_static\` but a complete verified transform from radar frame to IMU frame has **not** yet been implemented. The initial code makes an **unverified** common upright-z orientation assumption, which can cause large heading/translation biases. Frame identity must be verified before calling this a calibrated sensor fusion.

## Algorithm — explicitly no ground truth used for pose estimation

For a stationary radar reflector at horizontal polar unit direction \(u=(x/r,y/r)\) and radial velocity \(d\), estimated radar ego velocity \(v=(v_x,v_y)\) satisfies approximately:

    u_x v_x + u_y v_y = -d

- Inputs: original per-point \`RadarScan\` x,y,velocity from **left** radar only (not the pseudo reference, not robot wheel odometry, not LiDAR).
- Accumulate returns into fixed 0.5-second temporal windows. A single sparse scan often contains only 2–3 points; insufficient to solve full horizontal velocity alone.
- Solve 2D normal equations by deterministic Huber iteratively reweighted least squares. Enforce minimum 5 returns, sufficient angular diversity, finite ranges/velocity, estimated speed < 3 m/s, and median absolute radial residual <= 0.7 m/s. Reject invalid windows and mark their displacement **unknown**, not zero.
- Use raw Ouster IMU gyro-z and \`dt\` to obtain yaw changes, with no absolute heading correction; its clock is bag-arrival-proxy **not calibrated**. Apply this yaw to radar-frame ego velocity, integrate only consecutive accepted bins, never fill missing data using truth.
- Build a positive-return-only 0.25 m grid by projecting real measured radar x/y from the closest estimated radar pose, if timestamp separation <=0.3 s. No range ray freespace inference; an orange cell means **a radar echo mapped using an uncertain pose**, not independently established structural geometry.
- Only **after** estimating the frozen trajectory, independently read UPO \`/ground_truth\` and pair by Unix header time <=120 ms. Fit *rigid* planar SE(2) (rotation + translation, **no scale fitting**) over matched points, then report RMSE vs the **partially wheel-odometry-smoothed** pseudo reference with matched sample count and a zero-motion baseline. This is **not independent absolute accuracy**; the SE2 fit uses test GT after inference, so it must be labeled *posthoc-aligned research score*, never live-deployable accuracy.

For a different radar data source, validate radial Doppler sign, body axes, clock basis, TF, echo filtering and motor/platform movement model **before** reusing this engine.

## How to reproduce with real data

No large datasets in the repository. Run the dedicated GitHub Actions workflow \`Smoke UPO real-bag sensor benchmark\` (on pushes to feature branch, or workflow_dispatch), and download the small *stats/artifact* ZIP.

Offline unit suite (no network):
\`\`\`sh
python -m unittest discover -s tools/smoke_rescue/tests -p 'test_*.py' -v
\`\`\`

With original bag deliberately present at your local location (dataset is licensed for non-commercial use):
\`\`\`sh
python -m pip install rosbags==0.11.4
python tools/smoke_rescue/doppler_odom.py --bag /data/experiment_7.bag --max-seconds 60 \
  --out /tmp/upo-radar-gyro.json --html /tmp/upo-radar-map.html
\`\`\`

Generated JSON separates \`motion_observability\`, \`sparse_positive_echo_map\` and \`pseudo_reference_posthoc_evaluation\`, with declared sensor clock caveat, software type, SHA and full estimated trajectory. A small HTML visualization is demonstrative and explicitly NOT a fire navigation map.

## What must be improved before calling this an actual SLAM benchmark

1. **Calibration first:** decode TF tree, independently validate radar_left/right↔base_link↔os1_imu rotations, 3D lever arms and actual motion directions. Derive hardware-vs-receipt clock offset using *sensor-only* signals rather than reference poses.
2. **Estimate orientation robustly:** gyro biases, possible attitude correction only from magnetometer or environment when trustworthy. Proper ZUPT where a foot IMU exists; a wheeled robot has no foot ZUPT.
3. **Multi-radar Doppler quality:** use both horizontal radars, test static scatterer assumption and multipath/moving clutter rejection; estimate uncertainty, not just least squares velocity.
4. **Spatial performance:** calibrate radar/LiDAR extrinsics and temporal matching, measure reflectivity detection recall vs well-visible calibrated solid structures. Do not call every LiDAR return truth inside dense fog.
5. **Trajectory evaluation:** count valid and missing bins, actual reference matched motion and segments, compare gyro-only/constant-zero baseline; report confidence and p50/p95 per smoke level; use truly independent AR marker positions where available.
6. **Smoke severity:** vary controlled-smoke experiments #1–#7 with separate smoke-density info if actually recorded; do not infer optical depth solely from a filename.
7. **Thermal + radar + IMU follow-on**: IROS 2021 IRS RTVI has genuinely synchronized 60GHz radar, thermal FLIR Boson, monochrome RGB and IMU, with calibration files and **MoCap truth for the 5 lab sequences**, but **no demonstrated smoke recordings** in its dataset listing. https://christopherdoer.github.io/datasets/irs_rtvi_datasets_iros2021 . That is the nearest option-C sensor fusion reference, complementary to UPO smoke instead of pretending one source has all 3 modalities. Reference software RRxIO: https://github.com/christopherdoer/rrxio (GPL-3.0, old ROS/Kinetic/Melodic dependency).
8. Proper radar-SLAM would add scan-to-submap matching/loop closure, dynamic object suppression, pose graph, source-grade ground truth and safe fail states; none exists here yet.

Source/license policies: UPO CC BY-NC-SA 3.0, IRS RTVI license must be checked prior to redistribution, RRxIO code GPL-3.0. A CI run only establishes algorithm execution on **that** source SHA, not real human/firefighter performance.
