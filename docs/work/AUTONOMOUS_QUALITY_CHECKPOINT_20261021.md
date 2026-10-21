# Autonomous quality checkpoint — 21 October 2026

```yaml
integration_branch: feature/slam-explorer
integration_sha_inspected: 3d251b8e06536a972b88bcf01bd34912ea5ceabd
base_pr: 43
base_sha: 94e428aa81f96f24425c64fa3344a510332b7736
working_branch: work/autonomous-quality-20261021
pr: 44
previous_remote_checkpoint: ba4e3dccf9dc0ebbfd8235b55e2a52e5c1554253
```

## Iterations and acceptance evidence

| Task | State | Evidence |
|---|---|---|
| Audit live Git/coordination | Done | Integration and PR #43 heads unchanged; all four base workflows success |
| Reject frozen GPS/outlier heading | Done in code | Initial 2500 synthetic cases; 1500 false candidates removed |
| Adversarial uncertainty refinement | Done in code | Expanded 3000 cases: 2000 false candidates → 0; 1000 coherent cases retained |
| JUnit + Java sweep regressions | Added | Four JUnit cases, executable seeded audit, automatic host test |
| Measured comparison integrity | Done in code | 12 contract tests pass; see v2 contract |
| Resume dependency identity | Done in code | 11 testenv + seven lock tests pass; modified/new converter invalidates fingerprint |
| Baseline changed-code CI | Success at previous checkpoint | Android run 39518330907, real external GPX dropout run 39518331031 |
| Current final-head CI | Must inspect after next push | Never inherit previous checkpoint's green status |
| S21/RTK/energy/thermal | BLOCKED: external evidence needed | No connected Android or independent field reference |
| Real native VIO engine/JNI | Not implemented | Preserved experimental/default-ARCore boundary |

Local evidence: 111 tools tests (four optional h5py/NumPy skips), 38 campaign
tests (four optional/runtime skips), 14 benchmark tests; the exact checkout
Java PDR fixture, heading-gap/session roundtrip and GNSS stride checks passed.
The Python PDR synthetic testenv case ran and resumed a hash-matched report;
the two-engine suite was lock/verify checked, not both executed locally.
Full pinned-Java deterministic suite remains a CI responsibility because its
source downloader cannot access GitHub directly in this runtime.

Source was reconstructed through the connected GitHub API and all baseline
483 blob hashes matched. Local snapshot Git commits are not upstream SHAs.
Real upstream commits use the verified parent tree and preserve untouched files.
No merge, force-push, PDR algorithm, Camera2, menu, manifest or settings change.

## Immediate next actions

1. Inspect PR #44 head and exact-head workflow results. Fetch actual failed job
   logs before changing code. Validate Android/JUnit/lint/APK and testenv suite.
2. Review retained GPS synthetic audit JSON and the v2 comparison contract.
3. Qualify GPS reacquisition against independent phone recordings: freeze/outlier
   fixes, GNSS loss and return, repeated/parallel paths and segment resets.
4. Build one audited native VIO engine on a host with dependencies and a real
   calibrated image/IMU dataset, then report metric ATE without scale/clock GT fit.
5. For field evidence: S21 measured route/RTK, walk/bike/low-light, raw GNSS/FLP
   and segmented ARCore logs, camera timestamps and independently logged power.
   A replay of an external phone or synthetic GNSS is not this APK's field test.

Do not integrate #44 ahead of ancestors #38–43. Continue the same small-change,
test/measure/refine loop; do not label unresolved physical gates 100% complete.
