# PDR and VIO evidence — 21 October 2026

- Integration `feature/slam-explorer` inspected live at
  `3d251b8e06536a972b88bcf01bd34912ea5ceabd`; AGENTS and coordination read.
- Isolated branch `work/pdr-vio-evidence-20261021`. Preserve PR #45 exact host
  head `bc5b86927d9b18e15e965d6eefaa3d4d50eba326` and PR #46 benchmark head
  `d1a089cc8967a69bafe1dff5704bb9003e172e2b` as real remote parents.
- Local synthetic SHAs differ from GitHub; never push reconstructed local
  history. Publish tree on actual remote parents with a leased ref update.
- Resolved four documentation EOF conflicts by retaining both Basalt and
  quaternion sections. No whole-file ours/theirs resolution. Both code sets
  survive; combined Android/benchmark/native CI required.
- No MainActivity, Manifest, GPX/PDR export or live camera changes. PDR
  challengers remain host-only until genuinely superior, with no automatic
  promotion from one average score. No claim that a tiny classifier is
  metric neural odometry.
- PDR: exact deployed Java baseline versus causal smoothed-peak challengers;
  all previously inspected IDs 08/17/24/25/12/31/10/26 are development data.
  Fresh reserved IDs 13/14 fixed from the pinned source tree before inference.
  Source raw CSV bounded 80 MB each; original bytes remain temporary. Freeze
  candidate before reserved annotations; retain failures and per-placement
  scores. Future tuning consumes this held-out status, not a new clean test.
- VIO: actual same-binary Basalt stereo VIO/VO ABBA, same staged inputs and
  calibrated frame, flag --use-imu is the only command difference. Truth read
  only after all inferences. CPU model and actual CPU/RSS/wall recorded;
  no energy or online-latency invention. No ARCore result produced by host CI.
- Shared files: existing Basalt workflow, one new PDR research workflow,
  README/Architecture/Roadmap/Atlas additive documentation. Existing camera,
  PDR, menus and pose/display rate invariants preserved.
- Next: inspect actual CI experiments and exact-head combined checks, retain
  measured reports, and reject superiority claims if regressions remain.

## Measured outcome and justified scope extension
All 12 workflows passed at complete source 7e7e11b2fcdc4ba93a57d9e3e3e60e4b821a3f69, including real VO/VIO ABBA and Android. Initial PDR prototypes were rejected by worst-trial gate; later timing variant was rejected. After ten development subjects, consensus_130 passed six newly reserved 15/16 trials in push run 39612354616 at e407e8d26a1c489ec9061a965cbca86f404ef747: count MAE 5.115% -> 3.025%, macro F1 .946675 -> .957695, worst F1 change 0. Fixed parameters before reserved truth. IDs 15/16 are now consumed and may only be used for fixed-candidate verification.

Scope now includes PdrEngine/StepPeakDetector/PdrTraceWriter and PdrEngineTest/StepPeakDetectorTest: promote only the measured consensus130 acceleration fallback, preserve hardware step preference, export algorithm identity, prove default parity against the frozen candidate for each real stream. Canonical arithmetic is shared with research adapters, with an explicit legacy research mode for the old baseline. No learned model or arbitrary-body-heading claim.

Also reproduced PDR resume stale callbacks producing an older path timestamp and a third false step; keep per-stream/step watermarks, add explicit resume(timeNs), reject paused observations, and reset cadence independently. No PdrActivity, MainActivity, menu, manifest, Camera2, native activation, GPX or stride/heading policy change. Combined exact-head CI required after these app changes.

Publication correction: first draft tree accidentally omitted the verified base; forward repair restores all 522 files at c88b01ef4dd60055b86be0ad7c54e503334932fb. Local/remote full tree identity and zero removed files relative to integration were checked before further work. No integration merge occurred.
