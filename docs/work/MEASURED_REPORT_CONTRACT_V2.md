# Measured comparison contract v2 — 21 October 2026

`tools/vio/compare_measured_runs.py` still consumes externally measured reports.
It does not run a VIO engine, validate ground truth, verify file bytes or make
statistical significance claims. Valid-looking hashes are declarations only.

## Defects reproduced and fixed

- Missing/null/nonboolean `has_gt_in_estimator` previously passed as no truth
  access. Only explicit JSON `false` is accepted now.
- Non-string or unversioned identities could pass, and omitted truth/frame
  metadata could yield an apparently efficient singleton. Require nonempty
  string engine/hardware/truth/frame identifiers, full lowercase 40-digit Git
  SHA, 64-digit dataset/protocol digests and `sha256:<64 digits>` report identity.
- Reference matches could exceed tracked frames; coverage had an arbitrary
  five-percentage-point grace. Require matches <= tracked <= total and declared
  frame-based scored coverage <= tracked/total (floating-point epsilon only).
- Pareto omitted CPU despite accepting CPU measurements. It now preserves
  tradeoffs across ATE, RPE, coverage, latency, energy **and CPU**.
- Same raw dataset bytes were incorrectly required even for separately captured
  repeated routes. The latter now require an explicit pinned comparison cohort.
- The output now retains each score's source dataset and immutable report digest;
  it does not erase provenance when grouping repeated-route captures.

12 unit tests cover these contracts, including detached input records and invalid
top-level shapes. Before the fixes the expanded suite reported 48 assertion
failures and four errors across subtests; afterwards all 12 tests passed. These
are validation tests, not physical VIO measurements.

## Required protocol identity

`evaluation_protocol_sha256` is the digest of a retained canonical evaluation
specification: selected time/route section, RPE horizons, pose matching and gaps,
scoring and alignment method, frame coverage denominator, CPU, latency and energy
measurement definitions. Different hashes cannot share a ranking group.

- `same_raw`: group only identical dataset digest, hardware, truth source, frames,
  clock/pose alignment and evaluation protocol. Keep other captures separate.
- `repeated_surveyed_route`: different raw dataset digests are expected. Require
  `comparison_group_sha256` to identify the retained surveyed section and
  predeclared paired/randomized ABBA/BAAB design, sensor/device configuration and
  environmental eligibility. All common grouping conditions still apply.
- Repeated-route output has `dataset_sha256: null` at group level and a list of
  `source_dataset_sha256`; every score keeps its own dataset and report source.

Schema version is now **2**. Earlier report fixtures must supply the explicit
new fields; do not fabricate hashes to make old evidence pass. The contract does
not establish comparable environmental conditions by itself. Physical trials,
replicates, uncertainty intervals and source-byte verification remain necessary.

## Test environment cache identity

The previous entrypoint-only hash list omitted converter/evaluation dependencies
and the dataset registry. An uncommitted edit or newly imported source could
therefore reuse a cached PASS at the same Git SHA. `code_identity()` now also
hashes runtime source/configuration files in campaigns, benchmark, PDR, eval and
testenv (excluding tests/cache directories), plus the top-level testenv entry.
Dataset bytes continue through the existing separate input hash mechanism.

Two added tests prove known runtime dependencies are covered and new/changed
converter source changes the fingerprint without a commit. All 11 testenv tests
and seven reproducibility-lock tests passed. Existing receipts/caches must be
regenerated after this intended source-identity expansion. This does not lock
the host OS, Java/Python runtimes or all installed package versions.
