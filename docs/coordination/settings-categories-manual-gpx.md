# VECTOR VIO — categorized UI / manual GPX / GPS-free continuation (19 Oct 2026)

## Coordination and branch isolation

- Branch `feature/settings-categories-ui`, PR #26 **draft** against `feature/gpx-visual-depth-fusion`, created from `2cc37fb5d474b26bf1a67a2f70662b7a5f91c624`. Do not merge into `feature/slam-explorer` before real integrated tests.
- Inspected current integration `AGENTS.md` and `docs/DEVELOPMENT_COORDINATION.md`: preserve PR #3 B1–B8 / #5 PDR, avoid index collisions in MainActivity, preserve AndroidManifest declarations, data exports, pose collection, and synthetic-vs-real distinctions. Integration document notes base `9ff7d3a...` from older audit and may have newer head: **re-fetch before merging**.
- Shared `MainActivity.java` (DATA navigation, camera gestures, GPX controls), `ArRouteGuide.java` (GPX-only UI offsets/VIO-only state), `GpxArAndroidTest.java`, `ArRouteGuideTest.java`, `docs/GPX_AR_TASKS.json`. New `SettingsCatalog.java` and tests, SOTA research.
- No modification of `Odometer.java`, `AnchoredOdometry.java`, original GPX track file, camera calibration, PDR or AndroidManifest. Existing MAPS, LAYERS, SIM, flashlight, START/PAUSE, recording/export paths stay intact.
- Source contract currently checks presence of historical DATA labels; catalog + dispatcher remain in `dataMenu` source window to keep bridge while UI moves to six sections. Future integration should extend catalog with PDR and courses **rather than reusing integer menu indices**.

## Interaction UX and meaning

Top MENU → **Navigation & GPX, Location & sensors, Camera & overlays, Recordings & exports, 3D maps & places, Performance & tests**. State badges for ON/OFF and privacy-sensitive options. No silent GPS or Depth enable by entering a submenu.

Navigation → GPX AR → normal route options (import/reverse/display/DEM/depth) plus:
- `MANUAL GPX SLIDE` : available only after established GPS+VIO alignment. START DRAG opens a prominent non-permanent bar. One-finger horizontal/forward drag shifts the **visual route only** in the same current metric AR reference; no yaw/scale adjustment and no automatic snapping. RESET removes user translation; DONE leaves it active until tracking reset/lock/restart. Max 2 m/gesture, 25 m accumulated per tracking segment; approximation of screen-to-world scale, **not a centimeter exact intersection**. Active HUD explicitly tags `MANUAL ALIGNMENT (NOT GPS VERIFIED)`.
- `GPS AID — ON / MUTED (TEST)` : intentionally pause the GPX-specific location provider after first alignment; the independent phone GPS diagnostic channel is unaffected. Continue VIO alignment and route display with no arbitrary 30-second timeout. Show `VIO ONLY` and increasing **non-calibrated** uncertainty; no stale GPS heading. On AR tracking discontinuity, stale frames, or lock/unlock, clear anchors and require fresh alignment. **Camera VIO may drift without a bound**; do not misrepresent repeated camera positioning as surveying precision.
- Depth remains OFF by default; upstream route projected on observed floor planes first, optional Raw Depth only masks confidently occluded ribbons.

## Research and real acceptance

`docs/GPX_AR_SOTA_FUSION_RESEARCH.md` documents current factor-graph research (smartphone FGO, gnssFGO, fixed-lag GNSS/IMU) and why already fused ARCore poses cannot be combined naïvely with the raw IMU as independent data. Task F06 (future validated solver), U01 (UI), R06 (manual), F07 (VIO-only) added to `GPX_AR_TASKS.json`.

CI must prove complete 6-category navigation, historical GPS/FLP/replay launch, settings persistence, typed action catalog uniqueness and modified AR guide. Field test still required on Galaxy S21 with GNSS intentionally muted, long wooded routes, intersections, hills, and restart/relock to measure p50/p95 cross-track+heading errors vs independent reference. Labels never promise survey-grade accuracy. APK must identify **exact SHA** and GitHub Actions success; documentation-only SHAs afterward are distinguished.

## Physical UI verification sequence

1. Install on S21. MENU shows six clearly named categories; Navigation → GPX AR and 2D GPS routes; Location → GPS & comparison; Camera → layers; Recording → replay/export/video; Maps → POI/saved; Performance → power/sim.
2. Import GPX and walk >15 m in good GPS until blue ribbon is CANDIDATE; turn on `MANUAL GPX SLIDE`; drag horizontally in each camera orientation, RESET then move again and DONE; verify orange odometer path and raw GPX are untouched, progress remains GNSS-derived, annotation visible.
3. GPX AR → `GPS AID MUTED`; walk 5, 30, 120, 300 s with camera tracking; VIO-only route should be visible where ARCore tracks and observed planes exist, with rising uncertainty, not silently considered accurate. Move through weak textures/shadows; if tracking is lost, ribbon hidden. Reactivate GPS and examine geographic jump/recovery.
4. Lock/unlock while paused: GPX stays saved, old manual shift/anchors vanish; requires new fresh alignment. If process killed, GPX selection persists, activation must be explicit. Check battery impact and Depth OFF vs ON.
