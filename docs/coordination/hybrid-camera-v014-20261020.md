# V0.14 hybrid inertial navigation + camera quality — coordination 2026-10-20

- Consulted source head: `329a63133547a7d88ebda2babcba6b42303082e2` (v0.13 draft PR #38).
- Consulted `AGENTS.md` and current `feature/slam-explorer:docs/DEVELOPMENT_COORDINATION.md` (the latter's contents are a historic snapshot, not a mutex).
- Work branch: `feature/v014-hybrid-inertial-camera-quality`, based explicitly on v0.13 full-world GPX, not `master`.
- Shared file: `app/src/main/java/dev/jboehm/vectorvio/MainActivity.java` — added passive 1-Hz camera metadata logging/HUD text. **No changes to session configuration, settings, data menu, AndroidManifest, PDR, replay, GPX alignment/rendering, or build scripts**.
- Files created: `CameraCaptureQuality.java`, `hybrid/HybridPoseEstimator.java`, JUnit tests for both, design `docs/HYBRID_NAV_CAMERA_V014.md`. Current hybrid component is intentionally standalone: no IMU->world adapter, neural weights, or AR fallback switched on yet.
- Important constraints: keep pose sampling independent from VIEW FPS; neither PDR/GPS nor learned estimation is ground truth; bike != steps; retain the raw sensor and independent evaluation contract, ARCore reference lifecycle, and GPX provenance. Camera2 shared path has substantial ARCore constraints.
- Next step: CI verification, then IMU/ground-truth dataset adapters, neural baseline benchmark and sensor bridge; do not label this v0.14 release yet.


## Actual progress update, same isolated branch

- New shared files edited in v0.14: `MainActivity.java` ARCore 30/60 profile and passive metadata; `SettingsCatalog.java` / `SettingsPresentation.java` two new strongly typed actions; `AndroidManifest.xml` Camera2 lab activity; `PdrActivity.java` optional SHA-pinned motion model runner; `app/build.gradle` v0.14 alpha version.
- New native Camera2 exclusive lab activity and range policy, tests; never attempts to override `ARCore` camera request while active.
- PDR neural candidates: existing pre-trained SIMUL *carry classifier* retained, separately optional genuinely executed portable 24→32→4 displacement model with imported weight and SHA, still **diagnostic only**. No trained displacement checkpoint shipped, no synthesized GPS/path from DEVICE_XY. `UncertaintyFusion2D` TLIO-inspired position correction test foundation, not full TLIO.
- Added independent optical OxIOD train-window converter, NumPy MLP trainer, heldout-leakage and Java-binary format CI tests. Original 1GB corpus remains external; no source ground truth or research model weights committed.
- Key technical risk: predicting 64×6 overlapping windows at 0.5-second increments means predicted displacements cannot be directly summed as if they were disjoint increments; must explicitly align/resample before hybrid fusion.
- Final integration gates: source SHA Android unit/lint/apk CI + native schema and 3 campaigns, then field camera capabilities and OxIOD real holdout, trained weights rights/accuracy, calibration, combined GPX AR fallback. Draft PR #39 against draft PR #38; do not merge independently against `feature/slam-explorer`.
