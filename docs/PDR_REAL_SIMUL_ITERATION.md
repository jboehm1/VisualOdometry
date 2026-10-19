# REAL SIMUL paired baseline — PDR step detection

**Scope:** Actual Java engine accelerometer fallback, evaluated with **genuine**
SIMUL IMU recordings and foot-IMU-annotated step intervals. Not 2D
trajectory, body-heading, surveyed distance, S21 STEP_DETECTOR or neural inference.

Reference old engine (pinned): `f8228aa7acbc453001d623c537bd8d7f0a9bc176`.
Current candidate: local checked-out `PdrEngine.java` and `StepPeakDetector.java`
at the workflow exact SHA. Dataset pinned upstream: `ee7eb4e89bf33a17cafb4268207b802773427493`.

A PDR-owned workflow downloads six recordings temporarily into the CI runner
(four previously measured participants 08/17/24/25 plus two additional
previously unreported participants 12/31). Three body placements per
person: front pocket, hand, rear pocket = **18 paired trials**.
The annotation files are downloaded after inference by both candidate and
baseline; they never affect the estimator. Data SHA256 and ground truth
provenance are in the uploaded report; raw data are not redistributed.

Scores: macro per-trial F1 for temporal overlap of one detected event per
annotated foot-step interval (+/-80 ms); mean absolute step count error;
counts of false-positive/undetected events per placement; all zero-result
and regression cases retained. This **does not measure distance in metres**.
The additional participant tests are frozen before running; once inspected
they can no longer serve as a pristine held-out set for later iterative
hyperparameter tuning. Future truly held-out tests need fresh subjects.

Run `python3 tools/pdr/simul_compare.py --out /tmp/simul-pdr` from repo root.
CI: `.github/workflows/pdr-real-steps.yml`.
Keep other teams' `tools/campaigns`, `tools/benchmark`, `tools/testenv`
untouched. **No claim of improved accuracy until per-participant measured
results are inspected**; never target a fabricated 100% metric.

## Iteration after first real 18-trial report

First exact report (GitHub Actions run 39178813507, candidate
`b502376c1206054501f738abe4dcbe7bae2a69a1`):
- **Macro F1** 0.906572 (original Java) -> **0.910695** (current), +0.004123.
- **Mean absolute step-count error** 15.486% -> **9.127%**,
  **6.359 percentage-points lower** (about 41% relative reduction).
- Overall false positives 608 -> 443, but missed annotation steps
  134 -> 238. More conservative detector may suppress valid steps.
- Two newly frozen participant IDs 12/31: 14.630% -> 8.942%
  mean absolute count error, macro F1 0.909158 -> 0.916643.

These improvements are real measured annotation-based results for the Java
fallback but do NOT establish 100%, a metric distance guarantee, or S21
performance. In fact some hand-held placement F1 decreased. A later
candidate explores rejecting fast rebound peaks without resetting previously
established gait period. The next runner output must decide whether that
change should remain. It must not be advertised as proven until it passes.
