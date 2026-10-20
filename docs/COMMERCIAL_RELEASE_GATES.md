## Commercial performance benchmarking (not marketing approval)

A paid app should optimize **usable navigation per joule**, not just a raw VIO ATE. Maintain the mandatory A/B ledger in `tools/vio/compare_measured_runs.py`. It accepts independent, prealigned metric-scale measurements only and separates datasets/hardware and capture protocol. Measure scene coverage, re-localization latency, dropout, ATE/RPE, GPX cross-track position error, 95th-percentile latency, energy (J/min from physical power instrumentation or attributed device sensor), CPU/RAM and battery/thermal. Report raw metrics; any Pareto frontier is conditional on the tested modes. Data access rights and SDK licenses remain preconditions for release. Never use best-case-only segments to market a paid accuracy claim.

# Commercial release gates — VECTOR VIO (draft, 2026-10-21)

**Status: PRODUCT PLANNING / EXPERIMENTAL. No commercial-release approval.**
A possible paid product changes decisions now, not only at launch. Native
exposure/odometry is an optional R&D path; ARCore tracking remains default
until equivalent field quality and failover exist.

## Product principles

- **Offline-first / on-device by default.** Do not upload camera images, GPS
  traces, raw IMU, phone IDs or saved maps by default. User chooses any export,
  upload, or cloud processing with clear explanation and deletion controls.
- **Trustworthy output.** Distinguish GPS fixes, ARCore poses, native VIO
  estimates, PDR candidates, route guidance and measured ground truth. Show
  tracking LOST, gap markers and uncertainty. Never advertise cm-level
  accuracy without independent field evidence.
- **Battery / thermal / devices.** A paid app must work without custom manual
  Camera2 exposure on phones lacking MANUAL_SENSOR and without a supported
  native VIO build. ARCore default, user-visible capability detection and
  safe fallback are mandatory. Validate Android 15+/Samsung and a
  non-Samsung LIMITED/LEGACY device before any broad compatibility claim.
- **Feature utility over research demos.** Improve GPX route visibility,
  recovery after GPS dropouts, usable export, readable Settings and background
  walking GPS first; a sophisticated native SLAM backend has commercial value
  only after it measurably improves these user tasks.
- **Reproducibility.** Every performance/accuracy claim names engine, code
  SHA, dataset/real device, trajectory reference, metric definition,
  calibration and exact mode. No truth-assisted alignment can be presented as
  online odometer accuracy.

## Must-pass release checklist

| Gate | Minimum evidence | Current state |
|---|---|---|
| Licenses | Actual native + Java binary SBOM, dependency SHA, SPDX/other terms, NOTICE/attribution, app store redistribution rights | **BLOCKED** (inventory incomplete; native engine unchosen) |
| ARCore terms | Explicit easily accessible Google Play Services for AR disclosure, correct branding, current terms and privacy implications | **BLOCKED** for commercial release |
| Privacy | Data map and retention (camera/GPS/IMU/diagnostics), Android runtime permissions, consent/export/delete test, policy/Store Data safety | **BLOCKED** |
| Security | Signed production App Bundle, updates without lost recordings, dependency vulnerability review, safe file import validation, no debug logs/secrets | **BLOCKED** |
| Reliability | Camera ownership/permission handling, crash-free pause/resume, nonmanual-device fallback, thermal/battery endurance, offline tests | **BLOCKED** |
| Tracking claims | Real walking/bike/night/occlusion tests vs surveyed or independent reference, explicit failure state | **BLOCKED** |
| Business | Clear primary audience, price experiment, support process, crash diagnostics opt-in, refund/support contact | **UNDECIDED** |

Running `python3 tools/vio/release_license_gate.py
tools/vio/third_party.json --release` must be treated as a **release
blocker**, not bypassed by flipping `review_state` to `reviewed` without
a documented, human-reviewed analysis. Successful structural check still
does **not** replace a binary SBOM / legal approval.

## Licenses and dataset policy

- **Permissive code ≠ automatically releasable binary.** Audit every source
  and embedded dependency as built; preserve author copyright and license
  notices. Basalt's BSD-3-Clause declaration specifically warns that
  third-party submodule licenses differ.
- **GPL/AGPL libraries:** do not incorporate into a closed-source product
  without understanding obligations or securing appropriate commercial
  terms. This is a product-policy choice, **not a claim GPL prohibits sales**:
  GPL software can be sold with required rights/source obligations.
- **Kimera-VIO from MIT-SPARK** is licensed BSD; university affiliation does
  not mean the MIT *software license*. Verify dependencies separately.
- **Google ARCore AAR** is governed by its additional API terms, not merely
  an OSS SPDX license. The ARCore SDK README requests prominent disclosure
  that Play Services for AR is provided by Google and subject to Google's
  Privacy Policy; implement user-visible disclosure before distribution.
- **Research datasets** (EuRoC, TUM-VI, ADVIO, Mobile-GVIO) must remain in
  benchmark tooling and caches unless individual redistribution rights are
  explicitly confirmed; do not package raw scenes, videos or reference poses.

## Paid-product architecture boundary

```text
User-visible features (odometer, GPX navigation, recording, map)
         |
Spatial contracts (pose source / quality / world / segment / clock)
         |                                    |
ARCore on-device default              Native VIO (opt-in, gated)
         |                                    |
Google API terms                 auditable BSD/MIT code + JNI
         |                                    |
Per-engine evidence          camera+IMU calibration and field evidence
         +---------------------+--------------+
                               |
                    Honest error / fallback UI
                               |
                    Explicit user-controlled exports
```

No remote subscription backend, account requirement, payment SDK or
marketing tracker should be added just because commercialization is possible.
Choose a specific business model only after user value and retention tests.

## Primary policy sources

- [ARCore Additional Terms](https://developers.google.com/ar/develop/terms)
- [ARCore Android SDK repository / disclosure](https://github.com/google-ar/arcore-android-sdk)
- [Google Play data safety overview](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Android permission guidance](https://developer.android.com/guide/topics/permissions/overview)

Document owner: Native VIO commercial preparation; **not legal advice**.
