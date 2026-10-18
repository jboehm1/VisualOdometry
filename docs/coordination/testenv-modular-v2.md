# Coordination: modular benchmark test environment V2

**Date:** 2026-10-19. **Repo:** jboehm1/VisualOdometry.

- **Integration HEAD inspected:** feature/slam-explorer at **5f0105effd4fb2fb66b7d23620189c9605576de7**. Never mistake master (legacy 2019) for the integrated app.
- **Required instructions consulted:** AGENTS.md blob 9e56be8eb4a4d4575399b2324daa6fad4b05db7d and docs/DEVELOPMENT_COORDINATION.md blob 61b55fcea7c9a2e241735f0c17505ecc8721404b.
- **Base research/evidence inspected:** PR #7 (merged 9662709a, real Android Java PDR algorithm executed against original SIMUL step intervals), docs/campaigns/REAL_SIMUL_JAVA_PDR_20261018.md, docs/UNIFIED_BENCHMARK.md, tools/campaigns/unified.py, existing campaign tests and mobile-gvio baseline.
- **Isolated work branch:** feature/testenv-modular-v2, created at the integration SHA above.
- **Owned changed paths:** new testenv.py, tools/testenv/**, docs/TEST_ENV_V2.md, docs/coordination/testenv-modular-v2.md and dedicated new .github/workflows/testenv.yml. **Minimal shared .gitignore append:** ignore campaign_results/ to avoid committing downloaded original/private sensor data.
- **Shared paths:** only .gitignore (add campaign_results/), no other changes. No MainActivity.java, AndroidManifest.xml, Java PdrEngine/PdrReplay, DATA actions, GPS/ARCore, existing benchmarks, or existing workflows edited.
- **Parallel development risk:** feature/camera-free-pdr PR #5 and work/slam-roadmap-b1-b2-b8 PR #3 touch shared Android entry points; PR #14 is researching ADVIO. They must remain independent. Separate Python baseline from pinned actual Java PDR algorithm; ground-truth labels from estimator input; pose frequency from VIEW FPS; GPS measurements from candidate geoalignment. Green Linux tests do not certify S21 accuracy.
- **Design:** versioned validation manifest and registry-backed engine compatibility, no arbitrary subprocess definitions, input hashing, immutable per-run JSON+HTML reports, verified resume, engine/reference gates, opt-in downloaded real-data suite, strict file isolation, sequential execution with process timeouts, one output-folder lock.
- **Next actions:** check this branch's CI and PR diff; inspect new feature/slam-explorer SHA and re-run combined CI before merge. Then independently validate phone raw camera/IMU and S21 with calibrated reference. Do not claim any held-out independent metric distance qualification from gait interval F1.

No background monitoring or direct visibility into other chats' unpushed changes is implied.
