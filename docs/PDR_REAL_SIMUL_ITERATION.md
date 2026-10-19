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
