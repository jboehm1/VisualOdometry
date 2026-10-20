# Coordination: v0.14 adaptive shutter / Camera2

- Read `AGENTS.md`, `docs/DEVELOPMENT_COORDINATION.md` and `docs/HYBRID_NAV_CAMERA_V014.md`, checked integration ref `feature/slam-explorer` = `3d251b8e06536a972b88bcf01bd34912ea5ceabd`.
- Source v0.14 head at start: `a45403362ee8f9c16d3de778c5d38c3f0aaf014a` (PR #39 unmerged), dependent on v0.13 PR #38. Branch isolated: `feature/v014-camera-adaptive-fast-shutter`.
- User's real S21 feedback: existing Camera2 manual shutter+ISO work, fast shutter visibly less motion blur. Device-specific hardware capabilities must not be assumed.
- Shared touched file **only**: `app/src/main/java/dev/jboehm/vectorvio/Camera2ManualActivity.java`. Added `CameraImageQuality.java`, `CameraExposureOptimizer.java`, corresponding Android JUnit tests and this documentation. No PDR, VIO, GPX, MainActivity, Settings screen, Manifest, Gradle, CI workflow or release signing touched.
- Design invariant: separate Camera2 laboratory must not imply exposure override during ARCore session. Preview p50/contrast are image-space heuristics, not lux or ARCore tracking confidence.
- Hardware compatibility: manual capability probing with system-AE fallback; sensor ISO/shutter limit, no platform/model-specific values; optional light sensor.
- Next: verify exact SHA via Android CI, handle any tests/compile issues, link PR and artifact. S21 second user field test must verify automatic adjustment, saturation and fallback. Preserve v0.13 GPX/other parallel feature branches.
