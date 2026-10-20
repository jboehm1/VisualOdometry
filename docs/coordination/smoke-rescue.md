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


## Final exact tested evidence — benchmark V2
- New canonical real dataset/engine \`upo_smoke / radar-sensor-audit\` is registered in **tools/campaigns/unified.py**, suite file \`tools/testenv/suites/smoke-upo7-real.json\`, decoder fingerprint included in **tools/testenv/runner.py**. These are shared files changed **only on this isolated branch**; relevant testenv owner must review before merge.
- **SUCCESS GitHub Actions 39178059836 on commit d92e86f7f1bed31e3fed9dce252e86912defe4a9**: original 2.18GB UPO bag downloaded and decoded, 20 tests, 781/750 radar and 600 lidar scans, 479 partial-reference poses. TestEnv V2 suite passes and generates report+HTML, artifact ID 12154344457. The original bag is not stored/re-uploaded, no noncommercial data redistribution.
- **Important**: no radar odometry engine executed; synthetic viewer remains separate. Report only sensor rates/returns, temporal reference association and observational metadata. No ATE, RPE or obstacle recall; not a rescue tool.
- PR integration: recheck current feature/slam-explorer HEAD and combined CI before merge, retain other commits, especially PDR, replay and benchmark refactor.


## 2026-10-19 — real own radar/IMU estimator, extended negative result
- Added isolated \`tools/smoke_rescue/doppler_odom.py\` (original TI RadarScan per-point Doppler + measured OS1 IMU z), \`radar_map.py\` (only positive echoes), \`probe.py\` (original message/TF schema) + synthetic mathematical tests. **Existing Android, PDR and V2 core estimators left unchanged**.
- New original source probe: IMU ROS header ~2788 s hardware uptime vs radar ROS header ~1606476287 Unix: critical timebase mismatch. The prototype explicitly uses IMU ROS bag arrival time; actual clock transform and radar/IMU extrinsics remain UNVERIFIED. Do not interpret output as calibrated pose.
- [Real smoke CI 39210658719](https://github.com/jboehm1/VisualOdometry/actions/runs/39210658719): **35 tests PASS**, 69/80 accepted 0.5 s radar Doppler velocity windows in first 60s, 2,045 real positive echoes/212 cells, frozen posthoc SE2 ATE RMSE **0.891 m on 60 marker/odometry-smoothed reference matches**, vs stationary **1.788 m**. Estimated path significantly too long.
- [Real smoke CI 39211106764](https://github.com/jboehm1/VisualOdometry/actions/runs/39211106764): same fixed estimator over original **168s**, 269/290 accepted windows, 8,241 echoes/723 uncalibrated cells, ATE RMSE **3.242 m over 258 reference matches**, WORSE than stationary baseline **2.930 m**; matched path estimated 33.366 m vs reported 26.569 m (25.6% long). 35 tests PASS does not mean accurate navigation. Artifact 12167518553 retains JSON + HTML, raw 2.18GB bag deleted.
- Exact CI-benchmarked code SHA for extended run: **a82bd255732b85c84ed1f3a6a1d7e9c5ea68e970**. Subsequent doc commits only must not be represented as different hardware/accuracy runs.
- Extra radar+thermal+IMU IRS RTVI IROS 2021 dataset registered for complementing UPO smoke; its scenarios include darkness but published smoke was NOT established; source calibration/MoCap stronger.
- Shared files remaining touched on smoke branch: additive \`tools/campaigns/unified.py\` and fingerprint-only \`tools/testenv/runner.py\`; all other added files isolated. Do not merge until owner review and combined CI, maintain separate attribution and source licenses.
- Next priority: TF extrinsics, gyro device-clock to host-clock transform, direct marker-only scoring, independent radar-vs-LiDAR obstacle comparison, held-out smoke level; robust radar SLAM is NOT done.


## FINAL HANDOVER — October 19, 2026, research SDK v0.1 (NOT operational)

- Latest code-combined smoke branch **dbc9f5159bdc292573134e36ba522440bd16053f**, merged in changes from \`feature/slam-explorer\` at **98cba16e45de162434c1b09d408e80b431524a93** without force. Existing Android, PDR, location, three-campaign and smartphone/V3 features preserved; no edits to shared MainActivity/Manifest by this module.
- Shared files touched **additively**: \`tools/campaigns/unified.py\` (UPO real radar-sensor-audit engine, source provenance) and \`tools/testenv/runner.py\` (additional code SHA fingerprint). New CI files/workflows, research scripts, tests, release docs and dataset sources in dedicated namespace.
- [CI 39215925171](https://github.com/jboehm1/VisualOdometry/actions/runs/39215925171) SUCCESS, **60 offline smoke Python tests**. Canonical unified benchmark and modular V2 CI also SUCCESS on the combined code SHA. Android, V3 smartphone and full multicampaign statuses must be checked independently on exact head; no assumptions about unpushed work.
- REAL physical sensing in source recordings demonstrated by transient GitHub runners: **Exp7 2,183,007,782 bytes** [run 39214922472](https://github.com/jboehm1/VisualOdometry/actions/runs/39214922472); **Exp6 2,914,876,693 bytes** different heldout [run 39214548698](https://github.com/jboehm1/VisualOdometry/actions/runs/39214548698). Original licensed CC BY-NC-SA bags removed after run; no fake zero-dataset benchmarks.
- Full Exp7 calibrated dual radar Doppler+gyro **3.395m posthoc SE2 RMSE**, stationary control 2.930m; heldout direct marker evaluation **5.733m**, stationary 4.135m. Exp6 frozen dual algorithm posthoc **2.916m**, stationary 1.972m; marker heldout 3.757m, stationary 2.621m. Therefore **no validated navigation improvement**, do NOT claim safe route. Original reference includes robot odometry, while fiducials are not proven fully independent; absolute accuracy has not been established.
- Original Exp7 calibrated radar↔LiDAR 2D co-observation near ±80ms yields fraction within 0.5m of decimated LiDAR cloud: left 86.44%, right 93.21%; **not recall or absence of obstacles**. Full R&D details, links, price snapshot and limits: [release documentation](../SMOKE_RESCUE_RELEASE.md).
- Added modular apparent LWIR thermal JSONL ingestion (MLX90640/Lepton pixel formats) and angular-only heat cues; original UPO contains NO thermal imaging, so physical radar+thermal fusion remains **UNVERIFIED**. No Android HW driver/helmet/PPE / controlled live fire / certifications exist. Do not merge as safety-grade feature.
- **User deliverable**: GitHub PR #17 code + CI evidence and portable self-contained laboratory HTML; user can view real radar observations with no network, but no operational firefighter route.
- **Next external prerequisites**: physical radar/thermal device and synchronized measurement capture, certified heat-resistant integration, truly independent positional and structural reference, supervised smoke/steam trials, calibrated uncertainty and SLAM drift correction. This is not completable through GitHub Actions alone.
