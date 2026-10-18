# Unified benchmark CLI coordination

- Date: 2026-10-18.
- Integration base: feature/slam-explorer at 4bd4b0c17f80756f2a7c2296887c33e966ace88a.
- Reviewed AGENTS.md and docs/DEVELOPMENT_COORDINATION.md on that commit.
- Isolated branch: feature/unified-benchmark-cli.
- Scope: dataset/engine compatibility registry, unified execution, result provenance, honest test-capability levels, and CI validation.
- New files: tools/campaigns/unified.py, tools/campaigns/tests/test_unified.py, .github/workflows/unified-benchmark.yml, docs/UNIFIED_BENCHMARK.md and this note.
- Shared files modified: NONE. Existing benchmark scripts and Android/Java PDR engine are only read/invoked. No edits to MainActivity.java, AndroidManifest.xml, DATA menu, old workflows, or parallel branch code.
- Constraints: Java/Python PDR must remain separate; SIMUL step-count results are not metric displacement. Published EuRoC VINS-Mono trajectories validate the evaluator, not Android ARCore. Ground truth cannot be provided to the estimator; sensor calibration, reference quality and time/frame matching must be explicit. Existing coordination snapshots are not locks. Verify PR combined CI before merge.
- Next step: implement CLI, unit tests, public-real-data jobs; document exact commit/test evidence; merge only after checking the live integration head.
