# Camera-free odometry — option-by-option implementation map

**Scope:** `feature/camera-free-pdr` only; not a claim about the integrated branch or Galaxy S21 test certification.
**Meaning:** implemented = source exists and compiles pending latest CI; partial = limited algorithm / required physical assumption; open = no complete functional implementation in this mode.

| Earlier option | Status | What currently happens | Biggest missing part |
|---|---|---|---|
| Camera-free / ARCore-free independent operation | Implemented | Distinct Activity and pure Java PDR core | Real S21 field verification |
| Android hardware step detector | Implemented | Activity Recognition permission; register success required | Detection rate and device coverage |
| Accelerometer-based steps | Experimental | Magnitude peak detector fallback | Motion diversity, false positives, running model |
| Fixed walking stride and distance | Implemented | 0.74m default; manually configurable | Real independent calibration |
| WALK/RUN separately stored strides | Implemented | Two persisted adjustable profiles; user selects | Automatically detected transition and adaptive stride |
| Measured-course calibration | Implemented | Distance of last STOP session / accepted steps | Error characterization across users and postures |
| Steps per minute and speed estimate | Implemented | Exponential smoothing of recent steps; inactivity timeout | Independent speed sensor or learned estimate |
| Gyroscope logging | Implemented | Raw samples in exported ZIP | Online gyroscope bias estimation / inertial fusion |
| Magnetometer logging and plausibility | Implemented | Raw samples and simple magnetic strength gate | Interference modelling/calibrated uncertainty |
| Rotation-vector/phone azimuth | Partial | Opt-in PHONE FORWARD, face-up + fresh magnetic gate | Real body direction in arbitrary phone pose |
| Pure distance-only safe mode | Implemented | No phone-derived heading by default | Distance precision and gait generalization |
| 2D track from device heading | Experimental | Magnetic-oriented, explicitly gapped when heading unknown | Reliable arbitrary-pose direction + calibrated uncertainty |
| GNSS course assist (no distance substitution) | Implemented | Speed/accuracy/age gating and true/magnetic conversion | Stable low-speed heading, GNSS position fusion |
| GNSS distance or routing | Separate existing mode | GPS Routes exists outside PDR | Typed shared pose/distance API and combined testing |
| Relative pressure/barometer altitude | Implemented | Pressure baseline; relative height estimate | Weather/vertical drift correction |
| IMU orientation/coordinate-frame conversion | Partial | Phone orientation and magnetic heading | Learned body-frame to world-frame movement |
| RoNIN/TLIO-based learned displacement | Open | No learned network/weights on Android | Training, inference, held-out accuracy and licensing |
| Inertial EKF with covariance | Open | GNSS/device heading arbitration only | Filter, state, biases, innovations, observability |
| Drift and uncertainty intervals | Open | Missing heading labeled, gaps retained | Probabilistic errors, confidence bands and propagation |
| Loop closure / rerouting / map matching | Open | GPS/GPX code lives separately | Offline map constraints fused into inertial trajectory |
| Wi-Fi RTT indoor anchors | Open | Not in module | Device/service availability, anchor calibration |
| BLE beacons / UWB / external IMU anchors | Open | Not in module | Optional anchors and phone capability constraints |
| Cycling, driving, drone IMU odometry | Open | Only step-based WALK/RUN modes | Non-pedestrian observability, GNSS or external reference |
| Stop/start lifecycle | Implemented | Foreground-only; ends on background | Continuous background foreground service |
| Screen-off/long-duration tracking | Open | Activity stops onPause | Service/notifications, Android permissions, CPU/battery |
| Raw IMU + events + trajectory ZIP | Implemented | Local user-selected export, private cache | Crash recovery, retention/cleanup and on-device recovery |
| Android sensor inventory | Partial | Lists hardware declarations and sample counts | Measured S21 sampling frequency/bias/noise inventory |
| Dataset replay | Implemented in separate test workstream | Canonical JVM replay and CSV tools | Original full corpus benchmarks |
| User-facing quantitative accuracy | Open | No certified percent or error covariance | Surveyed 100/400/1000m tests and repeatability |
| Battery optimizations | Open | No camera used, but sensors record in foreground | Sensor-rate power profiles, live energy profiling |
| Production-quality integration of all branches | Open | PR #5 remains isolated | Combined menu/manifest conflict resolution + full CI |

**Critical distinction:** distance (accepted step count × stride) remains available
without a direction; position and path require heading or anchors. No amount
of accelerating a dead-reckoning double integration makes an arbitrary
unconstrained phone motion observable. The GNSS course and phone-top-forward
heuristics do not prove a body heading.

**Workstream ownership:** do not revise test environment and dataset pipelines
under `tools/benchmark/` and `tools/campaigns/`; coordination file owns this
chore's scope.

**Orientation opt-in lifetime:** PHONE FORWARD is NEVER persisted and resets after STOP/onPause. Device yaw requires fresh, deliberate user opt-in for each recording; walk/run stride preferences are persisted separately.

**AI options and percentages:** [detailed milestones, status and limitations](PDR_AI_OPTIONS.md); the neutral pure-Java candidate runtime is not a pretrained RoNIN/TLIO model. Neural testing remains a separate workstream.
