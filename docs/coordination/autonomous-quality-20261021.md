# Autonomous quality work — 21 October 2026

- Read integration `AGENTS.md` and `docs/DEVELOPMENT_COORDINATION.md` live;
  integration SHA `3d251b8e06536a972b88bcf01bd34912ea5ceabd`.
- Base PR #43 SHA `94e428aa81f96f24425c64fa3344a510332b7736`; all four PR
  workflows including Android verified success before edits.
- Own branch `work/autonomous-quality-20261021`. Preserve ancestors #38–43;
  no merge, no force push, no whole-file conflict selection.
- Scope: reproduced GPS/VIO observability failures, unit regressions and
  seeded synthetic Java before/after audit; subsequent measured-report audit.
- Shared code: `GeoAlignment.java` and its JUnit test. Shared docs append:
  README, Architecture, Roadmap Central, GPX world guide, Atlas HTML.
- MainActivity, manifest, menus, exports, PDR, ARCore, Camera2 and settings
  are preserved. Raw sensor data and truth remain separate.
- Network Git fetch unavailable; source reconstructed from the connected
  GitHub tree, all 483 blob hashes verified against the exact baseline SHA.
  Local snapshot commit is not represented as an upstream ancestor.
- Evidence: `docs/work/OBSERVABILITY_AUDIT_20261021.md`. Local 17 GPX replay
  tests passed. Full Android/JUnit pending CI at new commit, not presumed.
- Next: publish reviewable isolated PR, inspect exact-SHA CI, harden invalid
  measured comparisons, continue useful fixes while preserving field gates.

## Further iterations

Added a fourth geometry regression for one compatible GPS fix with 50 m uncertainty, plus public ArRouteGuide route-hiding/recovery coverage. The 3000-case synthetic audit is now an automatic host regression. Also modified `tools/vio/compare_measured_runs.py`, its tests, `tools/testenv/runner.py` and its tests to reject invalid evidence and stale resume identities. See docs/work/MEASURED_REPORT_CONTRACT_V2.md. No runtime PDR/Camera2/MainActivity or workflow mutation.

## Native acquisition contract iteration
`nativevio/NativeVioInputGate.java` and its existing JUnit file now preserve state on rejected future captures and require increasing corrected mid-exposure. Exact Java probe showed stale 9 s frame accepted after invalid 12 s capture before the fix, rejected afterwards. Scope remains unconnected native gate; no camera/JNI/ARCore/GPX/PDR runtime wiring.
