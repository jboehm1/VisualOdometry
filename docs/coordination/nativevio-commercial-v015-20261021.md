# Native VIO v0.15 commercial-safe kickoff (2026-10-21)

## Git, prior work and scope

- **Read** `AGENTS.md` SHA `9e56be8eb4a4d4575399b2324daa6fad4b05db7d`
  and `docs/DEVELOPMENT_COORDINATION.md` SHA
  `16640d065fbf77d56dd9cbc7cf76cdc302a54157` from
  `feature/slam-explorer` at
  `3d251b8e06536a972b88bcf01bd34912ea5ceabd`.
- Parent branch `feature/v014-region-gradient-exposure-optflow` PR #42 at
  `7f8da2396e01d930b3c762bf53c04600f2d5956f`.
  PR #42 depends on #41 → #40 → #39 → #38 (not integrated into
  `feature/slam-explorer` as of this review).
- **Own branch:** `feature/v015-commercial-nativevio-contracts`,
  based on parent SHA. No force push, no unrelated merges. Upstream work
  or unpushed chat branches are not assumed to be available.
- **Scope:** new isolated pure-Java Camera2/IMU timing gate, synthetic
  JUnit cases, first commercial license inventory checker and product
  documentation, updated central HTML atlas.
- **Shared files:** documentation-only append to
  `docs/VECTOR_VIO_ATLAS_INTERACTIF_v5.html`, `README.md`,
  `docs/ARCHITECTURE.md`, `docs/ROADMAP_CENTRAL.md`, if present;
  do not replace historical sections. New nativevio Java package only.
- **Absolutely not modifying:** `MainActivity.java`,
  `Camera2ManualActivity.java`, `AndroidManifest.xml`, menus,
  existing GPS/GPX/PDR/ARCore code, Camera2 exposure algorithm or
  Gradle/existing CI workflows.

## Coordination risks acknowledged

- Camera2 lab and ARCore remain exclusive users of camera hardware.
- Native VIO requires real raw YUV frames, calibration, camera-to-IMU
  timing, a real estimator, JNI and actual device tests. This branch
  cannot claim metric pose; tests only exercise conservative input
  rejection and license-inventory logic.
- `frame timestamp` and `SensorEvent.timestamp` may belong to
  different clock domains; source UNKNOWN means reject, not guess.
- Reanchoring/restarting must not concatenate disjoint world poses in
  GPX. New mode stays inactive/hidden until end-to-end validation.
- Baseline `tools/campaigns/mobile_gvio_raw.py` is not a native metric
  VIO engine and its Sim3 shape score is not Android odometry accuracy.
- Commercial intention requires scanning actual transitive dependencies,
  binary license/ARCore terms, privacy and credible claims *before*
  redistributing an APK as a product.

## Evidence / limitation

- GitHub connector can commit/PR; container Git clone fails DNS
  `Could not resolve host: github.com`; **no Basalt/Kimera/OKVIS
  host build or phone experiment** run locally in this turn.
- Tests to verify: JUnit testDebugUnitTest and Python inventory
  checks via Android CI at the PR head. Report actual workflow state
  after run, not an invented green build.
- Next gate: pin one engine commit, audit transitive licenses, host
  compile and actual EuRoC/TUM-VI image+IMU benchmark before JNI integration.

See [native VIO architecture](../NATIVE_VIO_ENGINE_V015.md) and
[commercial release policy](../COMMERCIAL_RELEASE_GATES.md).
