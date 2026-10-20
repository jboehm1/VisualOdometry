# V0.14 hybrid inertial navigation + camera quality — coordination 2026-10-20

- Consulted source head: `329a63133547a7d88ebda2babcba6b42303082e2` (v0.13 draft PR #38).
- Consulted `AGENTS.md` and current `feature/slam-explorer:docs/DEVELOPMENT_COORDINATION.md` (the latter's contents are a historic snapshot, not a mutex).
- Work branch: `feature/v014-hybrid-inertial-camera-quality`, based explicitly on v0.13 full-world GPX, not `master`.
- Shared file: `app/src/main/java/dev/jboehm/vectorvio/MainActivity.java` — added passive 1-Hz camera metadata logging/HUD text. **No changes to session configuration, settings, data menu, AndroidManifest, PDR, replay, GPX alignment/rendering, or build scripts**.
- Files created: `CameraCaptureQuality.java`, `hybrid/HybridPoseEstimator.java`, JUnit tests for both, design `docs/HYBRID_NAV_CAMERA_V014.md`. Current hybrid component is intentionally standalone: no IMU->world adapter, neural weights, or AR fallback switched on yet.
- Important constraints: keep pose sampling independent from VIEW FPS; neither PDR/GPS nor learned estimation is ground truth; bike != steps; retain the raw sensor and independent evaluation contract, ARCore reference lifecycle, and GPX provenance. Camera2 shared path has substantial ARCore constraints.
- Next step: CI verification, then IMU/ground-truth dataset adapters, neural baseline benchmark and sensor bridge; do not label this v0.14 release yet.
