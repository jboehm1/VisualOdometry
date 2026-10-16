# VECTOR Spatial Explorer 0.2

This milestone adds local mapping inspection to the ARCore visual–inertial odometer.
**Hardware validation is pending.** Build success cannot establish tracking accuracy.

## Use on the phone

1. Initialize in good light, looking at a static textured scene. Move gently sideways.
2. START recording, then walk a measured route. Turn around to inspect the trail.
3. LAYERS cycles all / planes+trail / points+trail / trail only. Long-press for help.
4. IMU switches the small map to live device-axis acceleration and gyro readings.
5. MAP expands the current segment's top view; tap again to restore the compact view.
6. MARK inserts a CSV event; PAUSE excludes motion; RESET starts a new recording.
7. EXPORT retains the document picker and historical session selection.

## What the visual elements mean

| Visual | Source | Interpretation |
|---|---|---|
| Cyan plane polygon | ARCore tracked, non-subsumed horizontal Plane | A geometric horizontal surface, potentially floor, table or ceiling |
| Violet plane polygon | ARCore vertical Plane | A geometric vertical surface; not semantic wall recognition |
| Gold dots | ARCore current PointCloud, confidence ≥0.2 | Sparse measured scene points, capped at 240 visible candidates |
| Cyan trail | Anchors created near accepted camera poses | Historical **camera-height** path, not a floor-snapped walking route |
| Reticle | Screen-space graphics | A visual aid, never a measured feature |
| Segment map | Accepted X/Z increments in latest independent segment | Local axes, not geographic north; old segments are not stitched |
| IMU graph | SensorManager acceleration magnitude | Approximately 4.5 seconds at 20 HUD updates/s, centered at 9.81 m/s² |

Plane labels show ARCore bounding extents, not polygon area or verified dimensions.
The display uses projected 3D geometry over the camera image. **No depth occlusion**:
a trail behind an object can still be shown. Planes/points appear only while TRACKING.
Plane detection is horizontal + vertical. Depth mode remains disabled.

## Map lifetime and tracking gaps

ARCore manages its own local map and relocalization. Anchors can adjust with its
model; that does not make the recorded path ground truth. This app does not expose
pose covariance, an explicit loop-closure graph, dense reconstruction, object
recognition, persistent maps, or map reload between launches.

The trail is bounded to 96 anchors, separated by approximately 0.4 m. Old anchors
are detached. Plane drawing is capped at 24 visible polygons and projection runs
at approximately 20 Hz. Camera video remains at the ARCore renderer cadence.
A reset, pause, detected gap, jump or tracking loss clears the trail. The next
segment has an independent map origin. Total traveled distance continues across
segments; **offset and height now refer to the current segment only**. CSV column
names remain compatible; do not interpret displacement_m as whole-trip closure
after more than one segment. The raw world poses in CSV remain ARCore coordinates.

### Clipping

`Projection` transforms world coordinates by `projection × view`, clips polygons
and lines against the seven homogeneous view-volume halfspaces, then divides by w.
Clipping occurs before the divide so floor polygons crossing the near plane do
not vanish or explode. The HUD clips drawing away from controls and metrics.

## IMU and CSV schema v2

The original 19 columns are retained. Five columns are appended:
`sensor_time_ns,sensor_type,imu_x,imu_y,imu_z`.

- POSE rows: original fields plus five empty sensor columns, about 10 Hz.
- IMU rows: wall clock, sensor timestamp, ACCEL or GYRO and x/y/z values.
- Events: original event name and wall clock, other columns empty.
- ACCEL: Android device X right, Y toward top, Z out of screen, m/s² **including gravity**.
- GYRO: same device axes, rad/s. Bias correction is not supplied by this app.
- SensorManager requests SENSOR_DELAY_GAME (~50 Hz, actual device dependent).
- Missing/stale sensor streams show 0 Hz. These diagnostic readings do not replace
  ARCore's private fusion data and are not asserted camera/IMU synchronized.
- Logging is active only during recording, including tracking-loss periods to
  support diagnosis. Writes use the existing serial I/O executor and event flush.
- No camera images, GPS or network data are recorded. Export before uninstalling.

The existing plotting tool ignores non-POSE rows and accepts additional columns.
A calibrated open VIO dataset still requires camera images, intrinsics/extrinsics,
rolling-shutter and timing calibration, sensor bias/noise models and validation.

## Verification and next milestone

Automated regression coverage includes path accumulation, current-segment offsets,
tracking gaps and projection/clipping. CI compiles and packages the APK.

Physical checks still needed:
- S21 camera alignment and plane boundary alignment at all portrait tilt angles.
- Walk away, turn around: trail follows previous camera position, without long
  erroneous lines across a gap; plane remains visible across the near plane.
- Featureless wall / lens occlusion: geometry disappears, no invented travel.
- Pause, background, return, resume and reset: no stale anchors or mixed sessions.
- Stationary accel magnitude near local gravity, gyro near zero, plausible rates.
- Export CSV with equal field counts; compare event boundaries and sensor timing.
- Measured 10 m, out-and-back and square loop; report error without correcting it
  using the known answer. Profile frame rate, heat, memory and battery at 15 min.

Next SLAM milestone: replayable calibrated camera/IMU capture, benchmark an open
backend (for example OpenVINS/ORB-SLAM3) against independent ground truth, then
map persistence and explicit loop-closure evaluation. This release is an ARCore
local spatial explorer; it does not promise a full persistent SLAM system.
