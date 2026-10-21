# EuRoC quaternion parser correction — 21 October 2026

- Integration AGENTS/coordination read live at `3d251b8e06536a972b88bcf01bd34912ea5ceabd`.
- Own branch `work/benchmark-euroc-quaternion-20261021`, isolated worktree based
  on PR #44 `513bb614cd7aedd0649b1242d339fc2bc72fb8ba`.
- Scope: `tools/benchmark/bench.py`, existing test file, additive docs only.
- Actual ASL/EuRoC state quaternion is wxyz, TUM is xyzw. Generic evaluator
  previously passed both through xyzw. A +90° yaw became a +90° roll; a new
  mixed-format orientation/RPE test reproduced failure before correction.
- Existing timestamp-only fixture was incorrectly labeled xyzw for EuRoC;
  corrected fixture to actual wxyz and retained timestamp regression.
- Native host PR #45 separately converts reference explicitly and does not
  depend on this generic fix; let its costly source build continue unchanged.
- No camera, app, GPX, PDR, raw data, workflows or input timing changes.
- Shared docs appended: README, Architecture, Roadmap and Atlas. These may
  overlap #45 at EOF: retain both additions during any later integration.
- Next: CI on exact-head PR; never use prior rotational RPE from this parser
  as authoritative without re-evaluating the raw recorded poses.
