# Session resource telemetry — 2026-10-21

Live integration inspected: `feature/slam-explorer` at
`3d251b8e06536a972b88bcf01bd34912ea5ceabd`; live AGENTS.md and
DEVELOPMENT_COORDINATION.md reviewed. Consolidated PR47 head remains
`009e498aea13fd36e1c8d587f454577c6609044a`, tree
`3cbffafb49855209de85ef7cf46f5215c2507df4`.

Isolated worktree/branch `work/session-resource-telemetry-20261021` starts
from that complete tree. Publish a forward commit to the existing PR47
consolidation branch with an expected-head lease; no extra candidate PR or
force push. Preserve the GPX/settings/camera/exports/PDR work already present
in the stack. Do not rewrite MainActivity from another branch.

Scope: shared resource collector, offline report and focused tests. Shared
files: MainActivity lifecycle/counters/export wording, PdrActivity start hook,
PdrTraceWriter ZIP attachments, SessionBundle readme and Android CI tests/build
identity step. No manifest, permissions, GPX geometry, estimator math, menus,
camera exposure or pose-sampling/view-rate changes.

CPU whole-process, battery whole-device, battery temperature, optional window
GPU timing and unavailable full GPU utilization remain distinct. Optional
unsupported probes must not cancel motion recording or fabricate zero data.
User field candidates remain ARCore and camera-free PDR; Basalt stays internal
until it demonstrates a worthwhile advantage under phone-relevant validation.

Local: four telemetry-report tests pass (pause exclusion, CPU units,
charging/missing energy, malformed/nonfinite clocks, histogram overflow and
partial status). Added actual writer/export and collector lifecycle/unit
Robolectric tests; Android CI must run on the published exact head before APK
delivery. Existing 12/12 checks at the previous head do not validate new code.

Next gate: publish complete tree, Android unit/lint/build/APK plus combined
workflows, inspect artifact identity and metrics tests, then deliver one APK.
S21 availability/measurement overhead/energy/trajectory accuracy remain
physical-device qualification gates. See SESSION_RESOURCE_TELEMETRY.md.
