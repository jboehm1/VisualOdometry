# VECTOR Spatial Explorer 0.5

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
8. MAPS → Place a point of interest: type a short label, choose LANDMARK / DOOR /
   HAZARD / NOTE, aim the reticle at a tracked plane and save. The label appears
   in the camera overlay, live top-down scene map, saved 3D view and ZIP export.
9. MAPS → Mapping & overlay settings changes capture interval, minimum distance,
   minimum rotation and point-cloud confidence. The LAYERS button cycles the
   camera overlay and persists its selection.

## What the visual elements mean

| Visual | Source | Interpretation |
|---|---|---|
| Cyan plane polygon | ARCore tracked, non-subsumed horizontal Plane | A geometric horizontal surface, potentially floor, table or ceiling |
| Violet plane polygon | ARCore vertical Plane | A geometric vertical surface; not semantic wall recognition |
| Gold dots | ARCore current PointCloud, confidence ≥0.2 | Sparse measured scene points, capped at 240 visible candidates |
| Cyan trail | Anchors created near accepted camera poses | Historical **camera-height** path, not a floor-snapped walking route |
| Reticle | Screen-space graphics | A visual aid, never a measured feature |
| Segment map | Accepted X/Z increments in latest independent segment | Local axes, not geographic north; old segments are not stitched |
| Gold POI pin | User-created ARCore plane anchor | A manually named place; no automatic object or semantic recognition |
| IMU graph | SensorManager acceleration magnitude | Approximately 4.5 seconds at 20 HUD updates/s, centered at 9.81 m/s² |

Plane labels show ARCore bounding extents, not polygon area or verified dimensions.
The display uses projected 3D geometry over the camera image. **No depth occlusion**:
a trail behind an object can still be shown. Planes/points appear only while TRACKING.
Plane detection is horizontal + vertical. Depth mode remains disabled.

## Map lifetime and tracking gaps

ARCore manages its own local map and relocalization. Anchors can adjust with its
model; that does not make the recorded path ground truth. This app does not expose
pose covariance, an explicit loop-closure graph, dense reconstruction, object
recognition, or map reload between launches. The saved snapshot is a sparse scene
record, not ARCore's private tracking map.

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

# 0.3 — Saved maps, 3D inspection, photos and loop constraints

## Save and reopen

START captures sparse mapping keyframes. MAPS → Save mapping snapshot stores a
versioned `.vmap` file in app-private storage. MAPS → Open saved 3D maps reopens
snapshots without an AR session. Going to the background also queues a snapshot
save for the current session. Manual saves make separate copies. Uninstalling
removes private maps: export important results first. A sudden process kill
before a manual/background save can still lose unsaved work.

The offline viewer supports drag to orbit, pinch to zoom, point/path toggles,
independent segment selection and a keyframe slider. The white camera arrow
shows the selected keyframe's saved optical direction. This is a sparse point
map and camera trajectory, not a dense textured reconstruction or mesh.
The viewer draws at most 10,000 point samples for responsiveness; export retains
all captured samples, including repeated observations of the same scene point.

The live expanded map is a bounded X/Z scene view. It combines a decimated
current-segment point cloud, tracked plane footprints, anchored camera path and
placed POIs. Pan/zoom is not supported in this live HUD yet; the saved 3D view
supports orbit and zoom. Points are display samples, not a fused/meshed cloud.

### User-marked points of interest

MAPS → Place a point of interest stores a user-supplied label and one of four
types at the center reticle. Placement requires a tracked ARCore plane. The
marker is anchored and follows ARCore's local map updates while the session is
active; it is frozen in the saved snapshot at a segment boundary. Up to 64
markers are kept per snapshot. Type and label appear in the live camera overlay,
the live map and saved 3D viewer, and export to one `points_of_interest.csv` per
segment. Version 3 map snapshots store markers; the viewer and importer continue
to read version 2 snapshots. Markers identify places the user named. The app
does not infer “door”, “hazard” or other object classes from pixels.

### Easy mapping settings

MAPS → Mapping & overlay settings controls capture interval (0.25–2.0 s), minimum
translation (0.25–2.0 m), minimum rotation (5–45°), and ARCore point confidence
(0.00–0.80). Values persist on the phone and affect new capture. LAYERS cycles
the live camera overlay and remembers the selected mode. Photo capture remains
separately opt-in and OFF by default.

### Limits

- Up to 128 keyframes per in-memory mapping session, 64 anchored keyframes in the
  current segment; limits are shown/disclosed, not unlimited mapping.
- Capture at most once per second when translation ≥0.75 m or rotation ≥20°.
- Up to 240 confidence-filtered sparse points per keyframe.
- A final pose-only endpoint is included on save/segment finalization when space
  remains, to retain the latest loop endpoint even without another full keyframe.
- Anchored point samples follow their capture-anchor pose while active. On a
  segment interruption those estimates freeze; separate segments are never fused.
- Large/open scenes, moving objects and long routes remain difficult. The sparse
  exported cloud can contain duplicate points, drift, noise and inconsistent
  overlapping samples. It is not a metric survey or a globally optimized cloud.

## Optional real images

MAPS → Photo keyframes → ENABLE PHOTOS explicitly enables local JPEG capture.
Default is OFF. Photos are optional and may be unavailable for some frames.
YUV_420_888 planes are sampled respecting crop/row/pixel strides, then encoded to
JPEG at ≤640 pixels width. Images are stored in sensor orientation and shown
alongside their matching camera pose. They are not painted onto a reconstructed
mesh. Photo capture/encoding runs on the GL thread at sparse intervals and needs
S21 performance profiling; reduce/disable photos if it causes frame gaps.

## Export

Open a saved map → EXPORT ZIP → choose destination. Each independent segment
gets `cloud.ply` (ASCII XYZ + confidence), `trajectory.tum` (timestamp + position
+ quaternion x/y/z/w), `points_of_interest.csv` and optional `images/keyframe_N.jpg`. Poses are sparse
keyframes, not the full odometer sample stream. N is the global snapshot keyframe
index. ZIP includes a README recording whether a manual constraint was applied and
`snapshot.vmap` as its first entry. MAPS → Import saved map ZIP restores this
portable snapshot, including photos, for offline 3D inspection. Import accepts
only this format, bounds decoded data to 64 MiB and extracts no archive paths.
PLY can be opened in CloudCompare, MeshLab or another compatible 3D viewer.
Images use diagnostic camera capture; no calibrated synchronized VIO dataset is
claimed. CSV recording/export remains separate. New maps use snapshot v3; old v2
maps remain importable.

## Offline simulation and deterministic replay

The **SIM** control opens a standalone simulation and never requests camera
permission. The app defers its live-camera permission request until **START**
is selected. SIM renders a changing indoor walk-through from a fixed synthetic
room and a scripted handheld camera path. A 40-second trajectory repeats every
40 seconds. `SimulationModel` samples ground-truth position/orientation plus
accelerometer and gyroscope values from the same time base, with fixed, smooth
hand tremor, walking cadence and deterministic sensor noise. Playback supports
an explicit START/PAUSE control, reset, looping and 0.5×/1×/1.5× speed. It
opens paused at time zero with an empty estimated map. PAUSE stops estimator
updates while leaving the current frame and map visible; RESET clears the path
and landmarks and returns to the empty time-zero state. CSV export writes
exactly 60 samples per second with pose, acceleration, angular velocity, units
and seed.

The reconstruction is empty on reset. `SyntheticVision` receives only the
rendered frame pixels and the synchronized synthetic IMU sample. It integrates
gyro rotation, estimates translation from image correspondences, blends in a
small accelerometer velocity prior, and adds a 3D landmark only after matching
the same feature from camera positions with enough estimated parallax. The SIM
inset shows this reconstructed XYZ path and landmark cloud. The scripted pose
and corridor dimensions are used by the renderer and a separate truth readout;
they are not estimator inputs. This is a deliberately small deterministic
visual-inertial mapping demo, not a production VIO/SLAM backend: scale uses a
depth prior, image matching is simple patch search, and neither drift nor map
accuracy has been validated against measured ground truth.

This mode is useful for UI, synchronization, persistence and deterministic math
regression, including dark environments and devices without ARCore. It is a
procedural rendered scene, not recorded camera footage, and its synthetic
inertial values are not calibrated measurements from a physical phone. It does
not inject frames into ARCore or exercise ARCore tracking, relocalization or
live-device VIO accuracy. The synthetic pipeline has deterministic unit tests;
the live pipeline still needs recorded datasets and phone-based acceptance
tests.

On the live AR screen, **FLASH OFF/ON** toggles the active ARCore camera torch
when that camera configuration reports flash support. The torch is switched off
when the activity pauses; the SIM exposure control changes generated pixels and
does not use the phone flash.

## Closing a loop

1. Begin at a clearly marked CAMERA position/orientation, walk a route and
   return to exactly that camera pose without a tracking interruption.
2. MAPS → Return-to-start loop check reports separation and orientation from
   the live starting anchor. It combines drift and physical placement error.
   ARCore can update anchors; this value is not an independent accuracy reference.
3. Save the map. Inspect first/last keyframes in the 3D viewer.
4. If these correspond to the SAME real pose, LOOP CONSTRAINT → APPLY TO COPY
   creates a **manually constrained** map. Original file and odometer CSV remain
   unchanged. Do not apply to an open route or different camera orientations.

The manual constraint distributes `C = T_first × inverse(T_last)` over keyframes,
using quaternion interpolation from identity and linear correction translation.
Each keyframe's points receive the same transform as its camera. This forces
endpoint agreement but **does not prove improved intermediate accuracy**. It is
an assumed endpoint correction, not feature matching, bundle adjustment,
probabilistic pose-graph optimization or automatic SLAM loop detection. The
manual flag persists in the map and ZIP README. Only one manual correction per
copy is permitted; reopen the original to compare.

A true automatic persistent SLAM system still needs a backend that exposes its
keyframes, landmarks, descriptors, loop constraints, relocalization and graph
optimization, plus replay data and ground-truth evaluation. ARCore's internal
map cannot be reconstructed/restored from this snapshot format.

## Additional acceptance checks (pending phone access)

- Save, background, kill and reopen: map survives and viewer works offline.
- Photo OFF saves no images; opt-in photos match selected camera keyframes.
- Export ZIP: independent PLY/TUM files and images open correctly on a desktop.
- Return-to-start check is unavailable after tracking interruption; never bridge
  incompatible worlds. Raw map and CSV remain unchanged after manual correction.
- Compare constrained vs raw endpoints AND intermediate surveyed checkpoints;
  endpoint agreement alone cannot establish reduced drift.
- Test 128-keyframe limit, image-unavailable frames, low storage, large map view,
  repeated viewer opening and 15-minute memory/thermal behavior.

## Automated visual fixtures

`VisualRenderTest` uses Robolectric native Android Canvas to render HUD, sensor
and 3D-map fixtures at phone dimensions into CI test reports. These use clearly
labeled **synthetic** data, not a live camera or measured reconstructed room.
They check renderability/layout; they do not establish AR alignment or tracking.
