# Coordination: smartphone real-data V3

**Date:** 2026-10-19.
**Inspected integration HEAD:** feature/slam-explorer at 6878c6fc426b3c4335890a927ee522f96f0924e8 (following PR #14 ADVIO, PR #16 V2 testenv and PR #12 Mobile-GVIO).
**Working branch:** feature/testenv-phone-datasets-v3, isolated from this SHA.

Read AGENTS.md and docs/DEVELOPMENT_COORDINATION.md on integration. Also read docs/campaigns/ADVIO09_REAL.md, PR #14 raw video + gyro script, Mobile-GVIO fetch/infer/score, prior PR #7 Java PDR findings, and V2 unified schema and runner. The central coordination record predates recent merges; it is NOT a live lock.

**Modified shared files:** tools/campaigns/unified.py (additive registered capabilities for ADVIO-09 and Mobile-GVIO, true engine/metric labeling) and tools/testenv/runner.py (include original real-phone adapters in cache code identity).
**New files owned by this work:** tools/campaigns/tests/test_unified_phone.py, tools/testenv/suites/real-phone.json, docs/TEST_ENV_PHONE_V3.md, docs/coordination/testenv-phone-v3.md and new .github/workflows/phone-dataset-matrix.yml.
**Untouched:** MainActivity.java, AndroidManifest.xml, DATA menu, Android/PDR engine, all existing workflows, and other concurrent workstreams.

**Scientific contract:** ADVIO evaluates externally PRECOMPUTED Google Pixel ARCore positions against fixation-aided reference; its original camera/gyro are audited, not run through our Android estimator. Mobile-GVIO executes a research Python monocular image plus gyro estimator, and fits scale/time to GT only for exploratory scoring after inference. No metric VIO or S21 accuracy claim. No ground-truth information enters the estimator.

**CI gate:** contract tests + real 71MB ADVIO suite + original 951MB Mobile-GVIO run and shape score, each verified independently; retain only HTML/JSON. Before PR merge recheck latest integration SHA and combined CI. Physical S21 field trials remain separate. No automatic monitoring or Google Drive sync.
