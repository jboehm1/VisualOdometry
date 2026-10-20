# Chantier GPX V0.13 global/frustum — coordination (20 octobre 2026)

- Base consultée: `feature/slam-explorer` at `3d251b8e06536a972b88bcf01bd34912ea5ceabd` (v0.12 merged).
- Existing base for independent code: PR #34 `feature/v013-gpx-world-horizon` at `dc6b01de0a41a35975cee53a0740586a8f0b1f42` with green Android/3-campaign CI.
- Requirement source: PR #36 `docs/coordination/GPX_FULL_WORLD_USER_REQUIREMENT_20261020.md`: not 90 m only, camera field-of-view, entire imported GPX retained, global off-route nearest, map 100%, no ground-altitude pretense.
- Work branch: `feature/v013-full-gpx-global-lod` (created from PR #34 without changing shared integration).
- Coordination read: `AGENTS.md`, `docs/DEVELOPMENT_COORDINATION.md`, `docs/GPX_AR_WORLD_GUIDE_V013.md`.
- Shared edited files: `ArRouteGuide`, `RouteIndex`, `SceneMapper`, `GpxExtendedGuide`, `HudView`, `GpsRoute`, `GpxIO`, unit tests, central roadmap, Atlas V5 HTML, GPX doc.
- Preserved: `MainActivity`, `SettingsActivity`, `AndroidManifest.xml`, PDR, nav estimator, GPS fixes, ARCore tracking, releases, CI workflows; no clobber of parallel smoke and low-light tasks.
- Separation: virtual-far GPX ≠ observed-floor GPX; GNSS alignment candidate ≠ measured absolute truth; AI VIO/IMU estimator unchanged. Do not count synthetic CI as physical S21 verification.
- Performance design: 4 geographic bands, full immutable index, ≤1600 global edges, ≤1400 visible virtual dashes, 50 km explicit virtual horizon and cached spatial search. Local 90m observed segment query stays intact; GPX route progress remains global and unchanged.
- Pending gates: GitHub CI exact SHA + merged result, then Galaxy S21 camera turns/real route/bridge/hills, runtime FPS/thermal, packaging/signature upgrade plan. PR #34/#36 remain independent until verified integration decisions.
