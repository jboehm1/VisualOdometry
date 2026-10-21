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
