# PDR 1.1 — reliability audit and measurable acceptance gates

**Branch:** `feature/camera-free-pdr` · **Date:** 2026-10-19 · **Integration SHA audited:** `5f0105effd4fb2fb66b7d23620189c9605576de7`.
No camera, no ARCore and no modification to the integration UI, manifest or shared workflows.

| Priority | Defect before fix | Code change | Automated evidence | Still unknown |
|---|---|---|---|---|
| P0 | One isolated accelerometer shock could immediately count a step | `StepPeakDetector`: sampled-time EMA, warmup, candidate/rhythm gating, gap reset | `StepPeakDetectorTest`, engine fixture | Real SIMUL F1 and count bias **not rerun**; start of short gait bouts can undercount |
| P0 | Old phone yaw could continue for up to 3s after tilt/bad magnet | `PdrSensorBridge` invalidates device yaw and writes replayable `HEADING_INVALID` | `PdrEngineTest`, `PdrHeadingPolicyTest`, `PdrExportTest` | Phone-in-pocket body yaw remains unavailable |
| P0 | Bad GNSS fix left previously accepted course active for up to 4s | `PdrEngine` revokes old GNSS course on later rejected fixes; negative accuracies rejected | Engine quality and revoke tests | GNSS dropout without a new event can only expire by age |
| P0 | Out-of-order pressure events could rewrite relative elevation | Monotonic pressure timestamp acceptance | Engine duplicate/out-of-order test | Barometric altitude weather drift/accuracy uncalibrated |
| P0 | Advertised step sensor could fail listener registration but UI still record | Real listener-registration gate and safe abort; one-arg bridge defaults to DISTANCE ONLY | Robolectric no-source and opt-in tests | Actual S21 permissions, OEM sensors, throttling, background are untested |

## Verified vs not verified

- First code batch `f4c2f84de7d615080b15bb2771e880ee49e3c174`: [GitHub Actions 39173399005](https://github.com/jboehm1/VisualOdometry/actions/runs/39173399005) **success** for Android lint/JUnit/Robolectric/APK and native schema + Java replay. CI artefacts include debug APK and small schema results. It is not a physical or real-data accuracy proof.
- Later changes must be checked against a **new exact SHA**; a previous green SHA is not evidence for an untested commit.
- Independent SIMUL annotations measured up to **+43.7%** overcount for the original fallback at `f8228aa` in the rear pocket. The new detector may reduce *some* false positives or create missed steps; no improvement is claimed until the **same** SIMUL held-out sequences are replayed on both engines.
- No trained RoNIN / TLIO / EqNIO is packaged. No measured metre-level body-heading or 2D position uncertainty. Manual WALK and RUN step lengths are not a learned model.
- No GNSS/phone-to-body heading assumption outside the explicit PHONE FORWARD opt-in. The default remains DISTANCE ONLY.

## Reproducible follow-up for the test-env owner

Use immutable engine commits `f8228aa7acbc453001d623c537bd8d7f0a9bc176` (original real SIMUL baseline) and the latest PDR 1.1 SHA. Replay the exact 4 SIMUL participants / 3 placements with **unaltered** time/accelerometer units and annotations. Record per-sequence step count, absolute/count percentage error, interval precision, recall and F1; include false positives and missed steps. Mark the detector re-tuning as pending until the result exists. Do **not** claim trajectory ATE/RPE from SIMUL's step annotations. If re-tuning on this cohort, reserve separate participants/placements as a truly untouched test set.

For Galaxy S21 physical qualification, log permissions and sensor inventory first, then measured 100/400/1000 m walk and run, stationary + violent shake, pure rotations, pocket/hand swaps, magnet disturbances, GNSS degradation and session interruptions. Compare ZIP `events.csv` → Java `PdrReplay` `path.csv` to the phone export with timestamps, skipped heading gaps and step totals. Report success and failures, not only mean errors.

**Current progress:** 23 tasks, 92 subtasks, **42.1%** accepted weighted milestones, deliberately unchanged (not position accuracy).
