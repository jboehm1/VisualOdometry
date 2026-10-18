# Coordination — smoke rescue Option C

- Review: 2026-10-19; integration feature/slam-explorer @ 5f0105effd4fb2fb66b7d23620189c9605576de7.
- Read **AGENTS.md** blob 9e56be8eb4a4d4575399b2324daa6fad4b05db7d and **docs/DEVELOPMENT_COORDINATION.md** blob 61b55fcea7c9a2e241735f0c17505ecc8721404b.
- Work branch: feature/smoke-rescue-research, created from above exact integration SHA.
- Scope: firefighter zero-visibility research, cheaper physical BOM, credible smoke-capable dataset catalogue, architecture & safety study, standardized Python-only synthetic occupancy replay with clearly labeled thermal angular cues, tests and standalone workflow.
- **Shared files modified: NONE.** Android MainActivity / Manifest / DATA menus / GPS / PDR Java / existing pipelines / existing catalogues / Atlas / ROADMAP_CENTRAL intentionally untouched. Added .github/workflows/smoke-rescue-research.yml, which is a *new dedicated workflow*.
- Parallel work considered: PR #5 camera-free PDR; PR #15 Atlas V5 smoke use case + central roadmap; PR #16 modular dataset benchmark. Avoid conflicting claims, duplicate UI or unreviewed integration. No unpushed changes visible.
- One small Python JSONL schema is temporary and is not a claim to be the canonical benchmark format. Coordinate a future adapter with PR #16, with raw/data/truth and selected alignment kept strictly separate.
- Checks: isolated unit suite, synthetic demo HTML, on-PR CI intended. No device smoke/heat testing or real-sequence download claimed by this module. A successful synthetic demo is not a demonstration through smoke.
- Integration gate: re-check branch SHAs, licenses and all tests on combined SHA; keep firefighting functionality disabled from operational APK; identify experiment mode and uncertainty in UI. Never present unknown/free confusion as safe navigation.
- Next steps: (1) PR exact-SHA CI proof, (2) acquire/check minimal genuine smoke recording with independent provenance, (3) input adapter + dataset tests, (4) low-cost real radar and thermal acquisition only after BOM check, (5) measured clean-air rig before non-dangerous fog tests, (6) separate Android module integration.


## Updated 2026-10-19 — user's real-data Test Environment integration request
- Upstream V2 PR #16 is merged (integration commit ancestry 836f724d); re-synchronized this isolated branch **without dropping any other changes**, with integration SHA **6878c6fc426b3c4335890a927ee522f96f0924e8** as parent and prior smoke branch as second parent; merge commit **71f169b7f7e03a898420e89d56545ff9b969312b**. Recheck upstream HEAD before PR integration.
- **Shared files now modified intentionally, ON ISOLATED BRANCH ONLY**:
  - tools/campaigns/unified.py: additive UPO dataset + radar-sensor-audit engine registry, catalog, runner, explicit no pose accuracy;
  - tools/testenv/runner.py: additive new decoder source to resume/hash provenance.
  No changes to existing estimators, Android MainActivity/Manifest, menus, PDR, Atlas, central roadmap, shared workflow jobs.
- New V2 suite tools/testenv/suites/smoke-upo7-real.json and new independent original-data workflow .github/workflows/smoke-upo-real.yml; original UPO bag excluded from artifact and purged on runner cleanup. Dataset under CC BY-NC-SA 3.0.
- **Physical ORIGINAL data confirmed through GitHub Actions**: first external job 39177341887 **SUCCESS**, 2,183,007,782 bytes, real 600 LiDAR frames, 781 left radar frames, 750 right radar frames over selected first ~60 sec, radar medians only 3/2 XYZ returns/frame. 18 tests prior to download. Code source identified as SENSOR AUDIT ONLY; real radar SLAM and independent localization precision are NOT measured, no heat data.
- New suite requires a second exact-head real recording run through V2; do not claim it green without its run-specific evidence.
- Current collision surface is limited to unified.py / runner.py; coordinate future modifications with Test Env owner. User requested this integration explicitly. Preserve Android/PDR and unrelated suites.
- Next steps: exact-head V2 CI on ORIGINAL bag; inspect source TF and calibrations; assess radar/LiDAR agreement with calibration; actual radar odometry with independent held-out reference; thermal sensor experiments when possible. NEVER feed the published UPO marker/odometry-smoothed poses to radar localization estimator.
