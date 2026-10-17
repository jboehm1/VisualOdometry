# Current module boundaries and dataset migration

Updated 2026-10-17. Application baseline: `8a3c4a1`, branch `feature/slam-explorer`, PR #2. CI run 39030133244 passed Android tests/lint/build and both three-lap procedural regressions. This is not a physical-device accuracy certification.

## Implemented reusable blocks

Sources live in `app/src/main/java/dev/jboehm/vectorvio/`.

| Block | Responsibility | Dependencies / reuse limits |
|---|---|---|
| Odometer, RouteHistory | Gate metric positions, integrate distance/speed, preserve segment breaks and bounded world route | Pure Java; reusable with another metric pose source. Odometer currently consumes position/timestamp, not a full generic engine output. |
| MapArchive, SessionBundle | Versioned sparse snapshots, validation, ZIP packaging | Pure Java file/data logic; no ARCore dependency. MapArchive is a bounded snapshot, not a reloadable tracking map. |
| Projection, GroundTrail | Homogeneous graphics clipping, display-only floor selection | Pure Java. Floor matching uses detected horizontal footprints plus height limits, not semantic floor recognition. Unknown floor uses a separately indicated display assumption. |
| CsvRecorder | Pose/IMU and sidecar persistence, export | Android Context/URI/Handler dependencies; format is reusable, service is not platform-neutral. |
| HudView / MapView | Live HUD and saved-map rendering | Android Canvas. HudView consumes Odometer and SceneMapper models; not yet engine-independent. |
| SceneMapper / MapCapture | Live projected surfaces/points, anchors, POIs and saved keyframes | Direct ARCore Frame/Session/Pose/Anchor dependencies. Must be adapted for another engine. |
| TrainingCapture / GpsMonitor / ImuMonitor | Phone recording and sensor acquisition | Android; TrainingCapture specifically uses ARCore recording. Not a generic video recorder or dataset player. |
| SimulationModel / CorridorRenderer / SyntheticVision / ReplayCamera | Generated truth, RGB rendering, limited estimator, camera calibration | Headless Java runner exists in tools/simulation. SyntheticVision assumes forward planar motion and takes a per-frame synthetic sensor sample; not a general real-data VIO engine. |
| MainActivity / SimulationActivity | Lifecycle, controls and pipeline orchestration | Two separate pipelines. MainActivity still coordinates ARCore directly; there is no runtime-selectable engine interface. |

## What is not implemented

- No common Engine/InputSource interface; no ORB-SLAM3/OpenVINS Android adapter.
- No EuRoC/TUM VI/ADVIO loader or executed benchmark with our estimator.
- No ARCore MP4 playback screen. Recording/export exist, but a supplied S21 ZIP now confirms nonempty H.264 camera video plus metadata/IMU; playback in the app remains unimplemented.
- No independent real-route ground truth, camera–IMU extrinsic/time calibration, global map optimizer or persistent ARCore-map reload.
- CI synthetic replay validates the Java simulation estimator, not live ARCore accuracy.
- APK signatures can vary between CI workers; a stable signing configuration remains necessary for reliable in-place updates.

## Required replacement boundary (design, not existing API)

Separate the following responsibilities before adding a second engine:

1. **InputSource:** ordered camera frames plus all intervening timestamped accelerometer/gyro samples. Include calibration, distortion, axes, units, camera–IMU transform/time offset and explicit reset/end events. Define buffer ownership and backpressure. Native ARCore camera/sensor acquisition stays inside its adapter: ARCore cannot consume arbitrary EuRoC frames.
2. **TrackingEngine:** lifecycle start/pause/reset/close, tracking state and metric camera-to-world poses with timestamp and segment/frame identity. Mapping is an optional capability; do not invent planes/anchors when an odometry engine does not provide them. Surface units, scale initialization and tracking failures explicitly.
3. **App state/output:** adapt poses into the existing Odometer; publish immutable render/sparse-map snapshots. Replace HudView's SceneMapper-specific model with neutral display data. Keep ground display separate from measured camera trajectory.
4. **Evaluator:** consume separate ground truth after inference; calculate ATE/RPE, coverage, initialization/failures, distance/speed and map errors. Never pass truth poses, scale, route dimensions or surveyed map into the engine.

Do not introduce empty interfaces and describe the app as pluggable. Extract the ARCore adapter and run it through the same boundary before claiming the abstraction works. Keep lifecycle/session calls on their required thread; bound pending frames and disk queues rather than retaining unlimited images.

## Real-data rollout

| Stage | Actual work needed | Evidence produced |
|---|---|---|
| Real phone, same engine | Verify TrainingCapture produces nonempty playable MP4 plus matching sidecars; implement official ARCore playback | Repeatable ARCore runs and render/tracking diagnostics; no independent accuracy claim |
| Established research baseline | Cache one EuRoC sequence and calibration; run a pinned ORB-SLAM3 or OpenVINS mono-inertial runner in a reproducible environment | External engine results, clearly separate from our APK |
| Shared app engine | Extract adapter boundary, add native research-engine adapter and exact timestamp/calibration conversion | Same selected engine runs in benchmark and application |
| Held-out validation | Freeze commit/config, run other EuRoC sequences and TUM VI with their supported camera models | Metric evaluation without post-hoc scale fitting; disclose partial ground-truth coverage |

Keep procedural scenarios for fast deterministic regression; add real datasets as another source, not as hidden truth inside the estimator. See REAL_DATA_VALIDATION.md for sources and evaluation constraints.

## Low-token automated workflow

Cache datasets once outside git. A deterministic command should pin engine/config/data checksum, run estimation, then emit summary JSON, trajectory files, plots and a failure log. CI compares declared thresholds and uploads artifacts. Send only changed metrics and short error excerpts to an AI; do not repeatedly inspect all frames. The existing procedural script follows this execution/report pattern. The real-data version remains to be implemented.

## Field evidence and UI status

The supplied phone ZIP contained pose/IMU CSV only, no MP4 or VIDEO_START event. It showed tracking/pose publication interruptions; CSV logging at roughly 100 ms cannot identify every rendered-frame freeze. SLOW_RENDER_MS events now flag render calls over 100 ms, rate-limited to one per five seconds; they are diagnostics, not a full profiler. Mapping snapshot allocation was reduced. Floor display, compact HUD and borderless fills are implemented, but floor registration/occlusion and sustained device performance still require phone tests.

Subsequent field evidence: the session starting 2026-10-17 12:10:46 UTC contains one 39.5 s 640×480 H.264 camera stream, frame metadata, IMU and four 470–679 ms slow-render diagnostics. The previous CSV-only statement above refers to the earlier session. Export selection now excludes technical sidecars and offers the current session directly.

## GPU overlay update

WorldOverlayRenderer now draws live surfaces, sparse points and the floor route with GLES 2.0, separately from Android Canvas text/mini-map/POI labels. WorldGeometry batches convex ARCore footprints into triangles, points into one batch, and each existing route segment into an 8 cm world-space ribbon. Segment gaps remain separate. Up to four draw calls are issued, filtered by the selected display layers. Persistent VBO capacity and a reusable staging buffer avoid per-frame GPU buffer allocation. World geometry refresh retains the prior 50 ms schedule; the current camera matrices are applied on every render, including between geometry updates. GPU clipping replaces CPU projection for these world shapes. Canvas fallback is retained if renderer initialization/update fails. This does not add depth occlusion or semantic floor recognition.

No sensor sample rate, image resolution, VIO thresholds, keyframe thresholds, saved-map point limits, CSV/video capture contract or procedural estimator changed. GPU staging is display-only. The visible route changes from fixed-pixel lines to a perspective-scaled world ribbon. Hiding a layer skips its GPU draw call; acquisition and mapping still run to preserve the same saved results. This is not a claim that all hidden-layer computation is removed.

`python3 tools/gpu_smoke.py` uses software EGL/GLES on a host with the corresponding libraries, compiles the exact shader strings from the Android source and checks a rendered pixel. It passed locally. This validates a basic shader path, not Android lifecycle, all geometry, S21 GPU behavior, sustained FPS, heat or power. The geometry unit tests cover polygon triangulation, ribbon width, invalid input and clearing. Physical-device performance improvement is still unmeasured; GPU migration alone cannot guarantee smooth operation on every device or remove ARCore/encoder stalls.

## Overlay fallback hardening

DATA → Overlay rendering selects Auto GPU, Force Canvas, or Retry GPU. A GPU error opens a display-only failure circuit persisted for the current Android build fingerprint. Auto does not clear that circuit; Retry GPU does. No per-frame retry loop is used. The HUD reports GPU, CANVAS, or CANVAS* (remembered failure). Manual preferences and detected failures apply to overlays only; camera/ARCore still require supported graphics hardware.

Initialization checks context availability, shader compile/link results, attribute/uniform bindings and nonzero buffer handles. Bounded glGetError checks surround uploads and draws, converting signaled errors (including memory/context errors) into fallback. Buffers/programs are released on the GL thread and bindings/blend/scissor/depth-write state are reset. Context recreation discards old handles before creating new ones; old-context IDs are never deleted in the new context. Shader cleanup covers partial initialization.

A failed GPU upload regenerates a Canvas snapshot immediately; a failed draw schedules regeneration on the next frame. HUD rejects empty/malformed projected shapes during the transition. Scene visualization errors are isolated from odometer tracking-gap logic and recording; they do not finish map segments merely because drawing failed. Fallback emits one event/toast per detected GPU failure, rather than retrying each frame.

Unit tests exercise persisted failure/retry policy, bounded GL-error handling, and actual Android Canvas rendering of GPU-only/malformed shapes during fallback. Host EGL smoke checks shader compile/link and a drawn pixel. These do not certify native driver crashes, silent visual corruption, physical-device context loss or every GPU. Manual Canvas remains necessary for undetected visual faults. Performance/correctness after switching must still be field-tested; Canvas is an alternative overlay renderer, not a complete software implementation of ARCore.
