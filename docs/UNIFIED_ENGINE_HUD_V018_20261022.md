# VECTOR VIO v0.18.0-alpha1 — unification UI ARCore / Basalt / PDR

**Date 2026-10-22.** Branch `feature/unified-engine-hud-v018-20261022`, based on #53 HEAD `9321312aa422d48100d2358ec8f86f0801e70433`, stacked on #52 → #48 → #47, no integration-branch files overwritten. Read `AGENTS.md` and `docs/DEVELOPMENT_COORDINATION.md` from `feature/slam-explorer`. Shared changes: `MainActivity` (ENGINE action), `Camera2ManualActivity` (full-screen UI), `HudView` (expanded minimap without ARCore), `CustomVioSession` (read-only snapshot), `app/build.gradle`; no native estimator, Camera2 exposure calculations, GNSS/GPX or PDR engine rewriting.

## Actually implemented

- **Persistent single visual design:** Main ARCore **and Basalt custom** now render the *identical Java `HudView` class* over a full-screen camera with VECTOR header, live measured odometer, mini/local trajectory map, sensor panel, status, common controls. No simulated coordinates are used to populate Basalt HUD: `CustomVioSession.snapshotForHud()` returns the real native `Odometer.Snapshot` backed by Basalt JNI accepted poses. Its state is guarded by synchronizing `Odometer.snapshot()`. The entire Camera2 mode is still an **independent activity**, rather than a live merged ARCore session; source ownership remains exclusive and resets when switching engines.
- **Engine selector** `EngineSwitcher`: one `ENGINE` button in ARCore and Basalt HUD. Choices **ARCore / Basalt (independent, experimental) / PDR (no camera)**. A recording must be stopped before switching; no unknown pose or distance jumps are silently joined. PDR keeps its dedicated camera-free UI, **not the full camera HUD**. Basalt returns to ARCore by finishing its Camera2 activity, releasing exclusive camera ownership, then reentering prior AR screen.
- **Basalt proper full-screen camera:** Camera2 `TextureView` fills the same HUD stage as ARCore. `START` opens an actual `CustomVioSession`; HUD displays **Basalt's independent odometer and live tracking state**, and existing `STOP`, `EXPORT` and `HISTORY` behavior are available without scrolling. The existing advanced camera panel is still accessible via **CAMERA** or **ISO / AUTO** and contains exact hardware shutter/ISO, dynamic exposure algorithm and its diagnostics. The generated CSV/ZIP and native Basalt initialization are unchanged.
- **No bootstrap, camera still visible:** Camera2 preview fallback can display with normal rear-camera selection if an ARCore factory-bootstrap is not available. **START Basalt remains correctly blocked** until its camera/IMU calibration bootstrap is available; camera preview is NOT VIO. This avoids an empty screen disguising the need for ARCore calibration.
- **Capabilities honesty:** Basalt has NO valid 3D GPX/plane reconstruction, no reusable ARCore anchors and no valid world-map projection. `GPX` / `MAPS` buttons explain limits rather than rendering fiction. The shared 2D minimap draws actual Basalt local X/Z pose trail; expanded-map view also supports `Odometer.Snapshot` with no ARCore `SceneMapper`. This is explicitly **a UI unification, NOT estimator parity**.

## Distinguishing experimental truth from UI
`0.18.0-alpha1` / `versionCode 19` is not a production-ready estimator. Native Basalt does ingest real Camera2 YUV frames and Android IMU, and computes independent poses when initialized, but its camera/IMU clock offset, distortion, rolling shutter and noise are not S21-calibrated, so the output may be unavailable/drifting/wrong. **No physical Galaxy S21 accuracy or success has been established.** The native source has no XFeat/EqNIO/TLIO checkpoints in this release. Do not assert real smoke penetration or fire safety.

## User test instructions

1. **First back up private local recordings.** Different debug signing keys can require uninstall with data loss.
2. Launch app ARCore. Select `Camera → AR camera profile → BALANCED / fixed focus`, relaunch if needed, and wait for tracking with rich indoor features. The bootstrap is captured opportunistically from supported ARCore camera metadata; it is **not** a validated calibration.
3. Tap **ENGINE → Basalt** (after stopping any ARCore recording), then **START**. If Basalt starts, the same VECTOR HUD will show the native measured distance/trail under the live Camera2 preview. If it does not start, the error is a valid outcome: check camera metadata, native availability, bootstrap or IMU.
4. Tap **CAMERA** to adjust automatic/exposure-specific controls (including motion mode, ISO and shutter); tap **STOP**, then **EXPORT** to collect independent Basalt ZIP. **ENGINE → ARCore** returns to the original HUD, but initializes a new local world reference.
5. `ENGINE → PDR` opens the camera-free PDR screen. For a scientifically sound comparison, record the **same independently measured route** separately on ARCore and Basalt, with known start/stop and physically stable illumination; never compare native map frame directly without independent, documented alignment.
6. GPX 3D and saved AR maps are still ARCore-only. Their Basalt buttons explicitly warn and never fabricate the projection.

## Regression contracts

- `EngineSwitcherTest`: 3 named engine IDs, active-recording switch guard and only ARCore supports 3D GPX.
- `UnifiedEngineHudTest`: real Android `HudView` and full-screen Camera2 preview in Basalt branch, ARCore engine action, independently available Camera2 Lab, visible engine-choice list and immediate saved custom session export.
- Existing `CustomVioHistoryUiTest` must still locate `basalt_history` and complete the export picker without a native camera.
- Full CI: `gradle :app:testDebugUnitTest :app:lintDebug`, host provenance/replay suite, native arm64 Basalt compile, packaged debug APK and 3 campaign replay checks.

## Still not delivered

**PDR full-HUD scene parity**, **true hot-swap in a single Activity**, **Basalt Camera2–ARCore fused alignment, 3D GPX overlays**, XFeat and learned inertial models, S21 quantitative accuracy and thermal qualification. These should not be represented as already integrated.
