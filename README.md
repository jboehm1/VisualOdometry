# VECTOR VIO — an Android visual–inertial odometer

**Camera + IMU → metric 6-DoF motion → real-time augmented camera HUD → local CSV.**

**Current maturity: 0.6.0-alpha / not production-ready.** CI builds and tests the app, but this workspace has no supported ARCore phone for installation, camera, lifecycle, or accuracy validation. No measured-route performance is claimed. This is not flight-control software.

This replaces the abandoned 2019 notebook *as an application direction*, without deleting it: the original [VO.ipynb](VO.ipynb) remains in history, with its exact original commit preserved at [backup/2019-original-colab](https://github.com/jboehm1/VisualOdometry/tree/backup/2019-original-colab). Android work is on [feature/slam-explorer](https://github.com/jboehm1/VisualOdometry/tree/feature/slam-explorer), pending field validation and review.

## Spatial Explorer 0.6

See [SPATIAL_EXPLORER.md](docs/SPATIAL_EXPLORER.md) for controls, mapping semantics,
CSV v2 and validation. New: real horizontal/vertical plane boundaries, measured
point-cloud overlays, anchored camera-height trail, an expanded live scene map and
live raw accelerometer/gyro diagnostics with timestamped CSV samples. ARCore
provides the local tracking/map engine; saved point-cloud/image snapshots reopen in an offline 3D viewer. Optional manual
endpoint constraints create a corrected copy; automatic loop optimization and
persistent tracking-map reload are not implemented. User-marked points of interest
are anchored and exported; no automatic object recognition is claimed. Physical
S21 validation is still pending. SIM opens an offline procedural video/IMU replay with a deterministic ground-truth trajectory and CSV export; it runs without camera permission or ARCore.

## What you can do

- Open a live camera preview with a custom high-contrast, semi-transparent **VIO HUD**.
- Read metric **accumulated path**, **current-segment displacement**, **smoothed speed**, **relative height**, **elapsed time**, and tracking status.
- Inspect a live top-down **scene map** of decimated ARCore points, plane footprints, camera path and named POIs, plus saved 3D snapshots.
- Place and export user-named door, hazard, landmark or note markers anchored to tracked planes.
- Open **SIM** for a deterministic 40-second generated indoor walk, synchronized 60 Hz synthetic IMU, ground-truth path, speed/loop controls, and repeatable CSV export.
- **START / PAUSE / RESUME / RESET / MARK** a recording, auto-save the evolving map, and use **SAVE MAP** for an extra named snapshot.
- Keep data **on the phone**; camera permission is requested only after you choose live AR tracking, never for SIM. The app does not request GPS or storage permission; Android document picker controls exported file destinations.
- Reject duplicate frames, tracking interruptions, frame gaps and physically implausible position jumps before they become odometer distance.
- Run unit tests for the distance accumulator using Java/JUnit and build a debug-signed APK in GitHub Actions.

### Intended phone

A Google-Play-enabled Samsung Galaxy S21 5G supports ARCore. Other supported Android devices with compatible Google Play Services for AR can work. Check the [official list](https://developers.google.com/ar/devices). Requires Android 8.0+, camera permission, and Google Play Services for AR.

### Install without a development computer

1. Open **Actions → Android • Build, Test & APK** in this GitHub repository.
2. Select a successful run for the `feature/slam-explorer` branch. If necessary, run the workflow manually.
3. Under **Artifacts**, download `vector-vio-debug-apk`.
4. Unzip the artifact, transfer `app-debug.apk` to your Android phone, and install it. Android may ask permission to install applications from the file-manager/browser used to open the APK.
5. Start **VECTOR · VIO**, grant Camera, and allow Google Play Services for AR to install/update if prompted.
6. Press **START** once to launch camera tracking and measurement together. Move the phone gently while looking at **textured static scenery** until TRACKING appears; speed and distance begin updating when tracking is active.

The workflow also compiles an unsigned release variant for build coverage; it is not a distributable production release. The downloadable debug APK is for testing and may not upgrade across builds signed by different CI workers. Never install an APK from untrusted mirrors.

### Build yourself (Android Studio)

- Install Android Studio with **JDK 17**, **Android SDK Platform 36**, Android SDK Build-Tools **35.0.0**.
- Open the repository root. Use Gradle **8.13** and Android Gradle Plugin **8.13.2**.
- Sync the project, then run the `app` configuration on a physical supported ARCore phone.
- CLI with a locally installed Gradle 8.13: `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease`. A Gradle wrapper JAR is **not** checked in; CI provisions Gradle directly.
- Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`. Never distribute a debug-signed APK as a Play Store release.

### Deterministic simulation

The offline replay generates a 40-second rounded-square corridor walk with reduced camera sway, continuous curved inner and outer walls, and a ceiling that closes the loop overhead. At startup the estimated map is empty. The estimator receives only rendered camera pixels and synchronized synthetic accelerometer/gyroscope samples: it integrates gyro rotation, estimates translation primarily from tracked image motion with a low-weight accelerometer velocity prior, and triangulates 3D feature landmarks from repeated pixel observations after estimated camera motion. It receives no simulated pose or corridor geometry. The live inset displays its relative XYZ camera path and estimated 3D landmarks; ground-truth pose is shown separately as an evaluation reference. Cycle the light control to test deterministic exposure loss and dark read noise. Monocular scale uses a depth prior and this lightweight pipeline is not metrically validated SLAM. Reset and the fixed seed provide repeatable sessions; CSV export contains the deterministic pose and IMU ground truth.

## Instrument display

The translucent overlay is drawn over a portrait camera feed:

    ┌────────────────────────────────────────┐
    │ VECTOR  / VISUAL–INERTIAL ODOMETER      │
    │ ● TRACKING              6-DOF • NO GPS │
    │                                        │
    │                ⊕                       │
    │     CAMERA VIEW        ┌────────────┐  │
    │                        │ X/Z trail  │  │
    │                        │ ╭────●     │  │
    │                        └────────────┘  │
    │ ┌────────────────────────────────────┐ │
    │ │ LIVE TELEMETRY              ● REC │ │
    │ │ PATH DISTANCE      SPEED          │ │
    │ │ 12.45 m            3.2 km/h       │ │
    │ │ DISPLACEMENT       Δ HEIGHT       │ │
    │ │ 11.91 m            +0.34 m        │ │
    │ │ TIME · SEGMENTS · MARKERS         │ │
    │ └────────────────────────────────────┘ │
    │ [START/PAUSE] [MARK] [SAVE MAP] [RESET] [EXPORT] │
    └────────────────────────────────────────┘

*This is a conceptual layout, not a screenshot. Reticle/grid are screen-space decorations, not reconstructed features or tracked 3D anchors.*

## How the real motion estimate works

**This app uses Google's ARCore tracking engine**, not an unvalidated implementation of sensor fusion or the 2019 CNN. ARCore performs camera + IMU visual-inertial tracking internally and reports a metric pose in an arbitrary local world frame. We integrate successive *accepted* camera-pose translations into a cumulative traveled path. Read [ARCHITECTURE.md](docs/ARCHITECTURE.md) for the coordinate math and [VALIDATION.md](docs/VALIDATION.md) for rigorous, repeatable evaluation.

The app is not a GPS odometer: it never requests location permission and does not query satellite positioning. Inertial fusion and visual tracking still have uncertainty. Tracking drift, moving objects, vibration, few features, low light, sudden re-localization, and loss of tracking can corrupt distance.

### Distances are not the same

| Field | Definition | Unit |
|---|---|---|
| PATH DISTANCE | Sum of accepted consecutive 3D world-position increments | m |
| DISPLACEMENT | Length of accepted translation vector in current segment | m |
| SPEED | Smoothed magnitude of observed translation per time interval | km/h |
| Δ HEIGHT | Current-segment accepted Y change (ARCore Y up) | m |
| HORIZONTAL | Sum of XZ-plane increments, present in CSV | m |
| SEGMENTS | Number of independently initialized tracked intervals | count |
| GAPS/OUTLIERS | Rejected intervals, written into telemetry | count |

When the tracking signal becomes unreliable, the accumulator does **not** pretend that the unobserved motion was zero. It starts a new segment and excludes unknown intervening motion; therefore reported path can **underestimate actual path**. Conversely, visual pose jitter or scale drift can **overestimate** it. The 3.5 cm keyframe threshold reduces small noise but is not a guarantee of accuracy, and the 25 m/s rejection threshold is only a safety heuristic.

### Your data

- Raw camera video is *shown*, never saved by this app.
- Raw accelerometer/gyro samples are separately logged through Android SensorManager in CSV v2; these are diagnostic device-axis readings, not ARCore internal calibrated measurements.
- Each session CSV contains device-wall timestamp, frame timestamp, camera position, orientation quaternion, derived metric counters and explicit events.
- Files remain in app-private `files/sessions`; EXPORT uses Android's native document picker. Reset retains previous sessions, and EXPORT lets you pick historical recordings.
- No app account, telemetry service, advertising, network permission or GPS permission.
- Uninstalling typically removes app-private files. Export important sessions first.
- Saved snapshots are bounded to 128 keyframes, 240 sparse points per keyframe, 2 MB per image, 24 MiB total images, and 64 MiB per native map. When the optional photo budget is reached, later map poses continue without images.

## Reading and contribution guide

1. [Architecture and mathematics](docs/ARCHITECTURE.md): monocular scale, SE(3), ARCore, metric accumulation and limitations.
2. [Field validation protocol](docs/VALIDATION.md): repeatable 1 / 5 / 10 / 50-m experiments and failure tests.
3. [Visual-odometry learning guide](docs/LEARNING_GUIDE.md): pixels, (u,v,1), scale, IMU, quaternions and SE(3).
4. [Roadmap and known limitations](docs/ROADMAP.md): what is missing before calling this production-ready.
5. [Plot and audit your session](tools/plot_session.py): export CSV and run `python tools/plot_session.py run.csv --out plots --known-distance 10` after `pip install matplotlib`. This writes separate trajectory, speed, height and distance graphs plus a JSON report. The known distance must be independently measured.

**Safety:** Not suitable for autonomous drone navigation, collision avoidance, geofencing, or safety-critical positioning. Phone camera/IMU quality does not make a certified flight controller. Do not fly with or rely on these measurements until evaluated independently in a controlled test environment.
