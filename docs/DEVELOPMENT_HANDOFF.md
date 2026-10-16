> **0.2 update:** Spatial Explorer work is on `feature/slam-explorer`.
> Read [SPATIAL_EXPLORER.md](SPATIAL_EXPLORER.md) first. It supersedes the 0.1
> inventory below: planes/points/anchored trail and diagnostic IMU CSV are added;
> displacement and height are now current-segment quantities. Physical phone
> validation remains pending. Consult the exact branch CI run for build evidence.

# VECTOR VIO — Complete Development Handoff
**Snapshot:** 2026-10-16 (Europe/Berlin)  
**Repository:** https://github.com/jboehm1/VisualOdometry  
**Maturity:** Android 0.1.0-alpha; **CI built and tested, not tested on a physical device, not production-ready.**

> **To the next engineer or AI coding agent:** Continue implementation directly in this connected GitHub repository. The user wants a polished, visually rich, scientifically defensible Android odometer based on a single camera plus the phone's IMU, initially on a Samsung Galaxy S21, with potential drone/visual-odometry research relevance. They want to install and manually test APKs, compare metrics against ground truth, and understand the mathematics. Act on concrete implementation tasks, run tests and publish real APKs and honest results. Do not give an unsupported declaration of "production-ready."

## 1. Canonical sources, commits, and safety rules

| Item | Exact value |
| --- | --- |
| GitHub repository | `jboehm1/VisualOdometry` |
| Original Colab snapshot | `d9e4a8b4fe6b3d6caa32d435e00d66b8fe5976c7` |
| Backup branch | `backup/2019-original-colab` (points to original snapshot) |
| Android development branch | `feature/android-vio-odometer` |
| **Last app commit verified green** | `942c7a9607b77050932361646e4ba0cf2b00244a` |
| Draft PR | https://github.com/jboehm1/VisualOdometry/pull/1 |
| Successful CI run | https://github.com/jboehm1/VisualOdometry/actions/runs/38970332390 |
| Exact build artifact | https://github.com/jboehm1/VisualOdometry/actions/runs/38970332390/artifacts/12076800936 |
| Build artifact expires | 2026-10-30 |
| SDK / runtime | Android min SDK 26, target 35, compile 36; Java 17; Gradle 8.13; Android Gradle Plugin 8.13.2; ARCore 1.56.0 |
| App ID | `dev.jboehm.vectorvio` |

**Critical repository rules:** Do not delete, rewrite, force-push or merge the backup. Do not modify or replace the 2019 notebook to hide historical mistakes. Work on the feature branch or a child feature branch. Keep PR #1 a **draft** until validated. The handoff document itself is a later *docs-only* commit; always fetch live branch HEAD and run CI on the current commit before claiming the current HEAD is green.

## 2. User goals and chosen strategy

1. **Immediately usable phone app**: install directly on Samsung Galaxy S21; uses its camera + accelerometer + gyro, with no GPS required.
2. **Actually meaningful odometry**: approximate traveled 3D path length, planar distance, net displacement, height change, speed, pose and tracking-loss behavior in SI units.
3. **Beautiful visual feedback**: modern dark transparent AR-like HUD, crosshair, colored tracking status, live camera feed, trajectory, real-time meters and speed, accessible touch controls, later meaningful overlays of tracked scene geometry/quality and better 3D visualization.
4. **Debuggability and reproducibility**: local time-stamped CSV, markers, export, plots, error metrics, explicit uncertainty and lost intervals, regression tests, build artifacts.
5. **Educational depth**: clear mathematical explanations (homogeneous coordinates, monocular scale, SE(3), quaternion composition, visual/inertial fusion, observability, limitations).
6. **Future R&D**: compare ARCore VIO to open VIO backends (OpenVINS/ORB-SLAM3/VINS-Mono), optionally add metric-depth AI, raw sensor capture, calibrated benchmarking, and investigate drone use in a separate validated effort.

**Key engineering decision:** The current working direction is **native Android + ARCore** (ARCore internally fuses camera and IMU). We are **not** claiming to have written a new VIO backend. This is the fastest route to a functioning metric odometer and a reference against which an open-source algorithm can be compared.

## 3. Current implementation inventory

### User-visible
- ARCore camera texture rendered with OpenGL ES 2.0.
- Custom `HudView` translucent dark/cyan overlay with central crosshair, mini XZ trajectory, metric path length, smoothed speed in km/h, displacement, Δheight, elapsed time, tracking status.
- START/PAUSE/RESUME/RESET/MARK and export of recorded CSV sessions through Android document picker.
- Persistent app-private sessions; no GPS/network permission; camera video displayed but not recorded.
- Handling of tracking unavailable, duplicate frames, missing frames > 0.70s, speed jumps > 25 m/s, tiny accumulated steps < 0.035m.
- Portrait-only prototype; performance and accessibility unmeasured.

### Source tree
```text
VO.ipynb                                       # preserved original 2019 research
readme.txt                                     # old one-line readme
README.md                                      # new overview/install/limitations
CHANGELOG.md
settings.gradle, build.gradle, gradle.properties
.github/workflows/android.yml                 # GitHub tests + APK
app/build.gradle
app/src/main/AndroidManifest.xml
app/src/main/java/dev/jboehm/vectorvio/
    MainActivity.java                          # ARCore lifecycle, Android UI/buttons, capture/export
    CameraBackground.java                      # OES texture shader, frame-to-screen UV conversion
    HudView.java                                # overlay rendering and 2D trajectory minimap
    Odometer.java                               # pure-Java gated camera-pose distance integration
    CsvRecorder.java                            # private session logging/export on serialized I/O
app/src/main/res/
    drawable/vector_logo.xml
    mipmap-anydpi-v26/ic_launcher.xml
    values/colors.xml
app/src/test/java/dev/jboehm/vectorvio/OdometerTest.java
docs/ARCHITECTURE.md                            # end-to-end pipeline and equations
docs/LEARNING_GUIDE.md                          # camera/IMU/SE3 mathematical walkthrough
docs/VALIDATION.md                              # physical field test plan
docs/ROADMAP.md                                 # backlog and known limitations
tools/plot_session.py                           # CSV summary + 4 matplotlib graphs
tools/tests/test_plot_session.py                # parser / report tests
```

### What the metrics actually mean

Let `p_k` be ARCore's camera center in **a local metric world frame**, not a global GPS position. While tracking remains valid:

```text
delta_k                  = p_k - p_(k-1)
path3D_m                += ||delta_k||
horizontal_path_m       += hypot(delta_k.x, delta_k.z)
net_displacement_vector += delta_k
net_displacement_m       = ||net_displacement_vector||
delta_height_m          += delta_k.y
speed_mps                = smoothed(||delta_k|| / dt) [gated estimator]
```

ARCore's local world uses Y up. The 2D map displays X/Z, and is **not** north-up. **Do not conflate path length with end-to-end displacement.** On a 5m out-and-back route, expected path ≈10m, expected displacement ≈0m.

ARCore estimates camera pose; `Odometer.java` only post-processes it. World-frame displacement differences are appropriate for measuring segment lengths; this does **not** mean that the original notebook's world-frame translation labels were correct for training camera-relative SE(3) motion.

### Gating logic and caveats
- `reference`: last *accepted distance keyframe*, not necessarily last camera frame.
- `lastObserved`: immediately previous frame, independently tracked in current implementation. The separation is essential: waiting 60s motionless must not create a false 0.7s frame gap when the phone moves again.
- Frame gap >0.70s: start a new segment, exclude the unknown interval. No straight-line invented travel.
- Frame speed >25m/s: reject implausible jump, reset segment. This is heuristic, not formal relocalization detection.
- Translation <0.035m since accepted keyframe: defer to reduce small jitter. **Not a mathematical jitter-free odometer;** slow/circular motion could still be biased.
- Tracking failure: stops integration and clears current anchor.
- Pause/background: stop integration, resume starts a fresh segment.
- Recorded pose rows contain raw ARCore poses (when tracking is valid), while UI metrics use accepted/gated increments. Never infer a lossless distance from simply summing all exported raw poses.
- Net displacement and mini-map join accepted deltas between segments; if ARCore changes its local world reference on re-localization, this can create an inconsistent map. **Must fix/characterize before reliability claims.**
- No pose covariance/metric confidence value exposed by current UI.
- ARCore may re-anchor, drift, or silently lose metric accuracy while TRACKING remains true.

## 4. Verified evidence and what is still unknown

On **2026-10-16**, GitHub Actions workflow **38970332390** for commit `942c7a9607b77050932361646e4ba0cf2b00244a` ended **success**. Verified workflow steps:
- Python telemetry analysis: **3 unit tests passed**.
- Gradle `app:testDebugUnitTest` completed successfully; source contains **9 Java JUnit tests**. Check XML reports for precise test counts.
- Gradle `app:assembleDebug` completed successfully; **BUILD SUCCESSFUL**, 41 tasks.
- Debug-signed APK uploaded as artifact `vector-vio-debug-apk`, artifact ID `12076800936`.
- Artifact is a ZIP containing `app-debug.apk` (897,674 bytes), not a release-signed APK.

**Not verified:** installation or run on actual Galaxy S21, overlay alignment, camera background rotation, AR tracking state changes, CSV export through the phone's document provider, real metric accuracy, thermal/battery behavior, crash lifecycle, vibration/drone scenarios. CI success is **not** a claim of field performance.

A previous CI setup issue occurred: `android-actions/setup-android@v3` tried to install removed legacy SDK `tools`. It was replaced by locating the preinstalled runner's `sdkmanager` and installing platform 36 / build tools 35.0.0. The subsequent green build confirms this specific issue was resolved.

## 5. Build, test, install and inspect

### Android Studio
- Install Android Studio, JDK 17, Android SDK Platform 36, Android Build-Tools 35.0.0, Gradle 8.13.
- Open repository root; use branch `feature/android-vio-odometer`.
- `gradle :app:testDebugUnitTest :app:assembleDebug --no-daemon --stacktrace`
- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Unit tests: `python3 -m unittest discover -s tools/tests -v`.
- No checked-in Gradle wrapper JAR. CI installs the specific Gradle version with `gradle/actions/setup-gradle@v4`. Adding a trustworthy pinned wrapper is desirable.

### Via GitHub Actions, without a computer
- Navigate: https://github.com/jboehm1/VisualOdometry/actions/workflows/android.yml
- Select a green run on current branch.
- Download the `vector-vio-debug-apk` artifact ZIP (GitHub login may be required).
- Extract `app-debug.apk` and install it on Galaxy S21 using Files/File Manager.
- Permit installing APKs from your selected Android file manager if prompted; enable camera permission, Google Play Services for AR install/update when asked.
- If Android blocks an upgrade with **signature mismatch**, export prior data, uninstall old alpha package, then reinstall. CI debug signing keys can vary between runner machines. Uninstalling deletes app-private sessions.

### Best first physical test
1. Record 60 seconds standing still while pointing camera at a well-lit textured static scene. Path ideally stays near zero; quantify false travel.
2. Move forward a measured **10.0 metres** and record path, displacement, gaps and tracking status.
3. Walk **5m out, 5m back** to exact phone starting position, inspect path (~10m) versus displacement (~0m).
4. Cover lens during movement, or background the app; verify it **does not fabricate distance** through unobserved motion and counts/labels gaps.
5. EXPORT as CSV; analyze:
   `python -m pip install matplotlib`
   `python tools/plot_session.py session.csv --out plots --known-distance 10`
6. Collect phone model, Android build, ARCore version, lighting, orientation, environment, screenshots/video, CSV, physical tape-measured route and reproducible steps for failures.

Use `adb logcat -s VectorVIO AndroidRuntime:E` for startup/rendering errors; also inspect uncaught Java exceptions. Do not upload private location recordings to public issues without user's approval.

## 6. Issues/risks to inspect immediately

**P0 — Make it reliable on real hardware**
1. Install *exact latest green build* on the S21, verify live camera is visible, properly rotated and cropped; verify the ARCore session resumes/pauses correctly and tracking acquires. OpenGL/ARCore SDK signatures compiling does NOT prove the GL texture appears at runtime.
2. Verify all session controls: START/PAUSE/RESUME/RESET/MARK/EXPORT, Android background/foreground, permission denial, AR service missing, rotation constraints, low-light failure.
3. Check black-screen recovery and activity lifecycle. ARCore GL thread/session access synchronization needs instrumentation.
4. Check geometry after tracking recovery: local world origin/orientation can be discontinuous; net displacement and minimap should NOT naively connect incompatible world frames. Consider explicit per-segment coordinate frames and visually separated line strips; never imply global continuity without a validated transform.
5. Improve trip-distance estimator: raw pose micro-jitter can accumulate; existing deadband is heuristic and may suppress real short motion. Measure both stationary false path and short deliberate movements; compare parameter settings on independent trials.
6. Confirm CSV event consistency, timestamps, all retained sessions, lack of silent I/O failure, and correct export after recording/resets/app restarts. `CsvRecorder` presently queues writes to one executor and flushes before copying.
7. Check the *export path* with actual Android document providers. Logging writes include raw tracking poses at ≤~10 Hz; **not raw accelerometer/gyro**.
8. Add USB/ADB or Android instrumented tests for UI and repeated app resumes; use stable synthetic pose replay harnesses.

**P1 — Better visuals, measurements and ergonomics**
- Add coherent small-screen responsive design, clear tracking state warning, no-scroll landscape/tablet strategy, dark mode contrast/accessibility, and genuinely responsive 60Hz overlay without excessive per-frame allocations.
- Better trail chart with per-segment gaps, origin marker, current camera heading, world-axis compass warning, user-toggleable reticle, scale bar; support zoom and recent/history view.
- Create an optional **3D trajectory visualization** with position and orientation, drawn in a world-consistent frame. Separate UI reticle from real tracked map points to avoid misleading overlays.
- Consider ARCore point-cloud display when exposed; color by measurement quality / depth, no fake feature tracking indicators.
- Show valid-tracking duration, frame rate, dropped frames, ARCore failure reason and current segment, plus recorded-vs-unrecorded motion intervals.
- Add charts for short-term speed and vertical displacement, screen recording support with explicit privacy controls, cleaner graphic hierarchy, export-sharing.
- Provide metric **accuracy indicators as estimates**, never precise/confident-looking numbers without measured covariance and a calibrated empirical model.

**P2 — Research-grade / drone-oriented extensions**
- Build open VIO as a *separate backend*, not a replacement for working ARCore initially: OpenVINS, VINS-Mono or ORB-SLAM3, after collecting raw synchronized camera and IMU, calibrating extrinsics/intrinsics/timing/rolling shutter and estimating biases.
- Benchmark on EuRoC MAV and TUM VI; compute ATE/RPE, path-length error, scale drift, loop-closure error and runtime/battery profile.
- Investigate pure monocular geometry baseline and learned depth models independently; drone-looking-down/natural terrain is an especially important domain shift.
- Evaluate semantic motion masks to remove people/traffic/vegetation flutter; use true tracked features to remain robust to natural scenes.
- Optional external GNSS/RTK or rangefinder may serve *independent* ground-truth evaluation; don't quietly use it if test claims camera+IMU-only.
- Plan for camera-shake, vibrations, fast flight, blur, high-altitude low-parallax, featureless terrain and scale observability. **This alpha app must not control a drone.**

## 7. Formal test matrix and acceptance gates

See `docs/VALIDATION.md` for detailed T01–T12, covering:
- stationary 60s, straight 1/5/10m, 5m out + 5m return, 20m square loop,
- staircase/elevation, pure rotation, lens occlusion and backgrounding,
- low light/featureless walls, hand vibration.

Proposed *targets only* (NOT achieved): stationary false distance <0.5m/min; median 10m path-length error <5%; 90th-percentile <10%; robust pause/gap handling and correct CSV. Choose realistic gates from user priorities once measurements exist. Always report samples per environment and dispersion; "pass" must include recorded evidence.

## 8. Most important code-level invariants

- **No distance is accumulated if recording is stopped or ARCore reports no TRACKING.**
- No fabricated interpolation across tracking interruptions, missing frames or pauses.
- A stationary stream of valid timestamps must **not** trigger false frame gaps (regression in OdometerTest).
- Frame-to-frame jump detection must not be diluted by stationary elapsed time.
- Pose timestamps must be monotonic; ARCore world translation is in meters; orientation quaternion isn't subtracted.
- A user-reset recording doesn't silently contaminate another session.
- The trajectory map must disclose if segments were stitched only for display; avoid an implied single world frame after relocalization.
- Do not claim 1cm accuracy just because two decimal places are displayed.
- Keep source, docs, tests and APK release matched by commit SHA.

## 9. Commands / prompt to give the next development agent

> You have repository write access to `jboehm1/VisualOdometry`. Read `docs/DEVELOPMENT_HANDOFF.md`, `README.md`, `docs/ARCHITECTURE.md`, `docs/VALIDATION.md`, `docs/LEARNING_GUIDE.md`, and the Java source/tests on `feature/android-vio-odometer`. Keep the original notebook and `backup/2019-original-colab` untouched. Confirm live HEAD, inspect the latest successful GitHub Actions run and APK artifact. Prioritize **real S21 installation/ARCore tracking and CSV export, honest distance benchmarking, hard failure modes**, then improved HUD, segmentation and quality telemetry. Write tests first when possible; commit changes in small auditable steps; run Java unit tests, Python tests, Android build and device tests if accessible; make a new installable debug APK for every validated milestone. Keep PR #1 draft until field evidence satisfies acceptance criteria. Provide the owner a link to the exact APK and a concise changelog, including what could not be tested. Never claim drone-grade accuracy based solely on ARCore or CI.

## 10. Known future dependency/publishing concerns

- This is a **debug-signed alpha**, not a Play Store-ready release and not stable for production data retention. Set up reproducible app signing and an APK release channel only after first phone validation.
- ARCore/Google Play Services is an external runtime dependency; a fully offline, freely deployable open-source VIO backend is not provided yet.
- Confirm versions of Android SDK, ARCore, AGP and GitHub actions regularly; do not upgrade without a reproducible test run.
- The original `readme.txt` and `VO.ipynb` are intentionally preserved as historical artifacts, not maintained runnable pipelines.
- CSV export can contain motion data describing a user’s private routes even without GPS; handle sharing deliberately.
- For the official current source, use the GitHub branch rather than a local stale ZIP. Each artifact must be matched to its specific commit ID.

**End of handoff.**
