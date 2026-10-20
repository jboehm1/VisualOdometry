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

## Scientific iterations and selection policy

The actual original-vs-candidate comparison (6 participants, 18 placements,
macro F1 and mean absolute count error) was run before selecting the
deployment candidate:

| Candidate commit | Minimum rhythmic early-peak behavior | Macro F1 | Mean abs count error | Drawback |
|---|---|---:|---:|---|
| `b502376c` | original PDR 1.1 cadence re-acquisition | **0.910695** | **9.127%** | small F1 drops in several hand trials |
| `3b5c5223` | ignore <70% rhythm period without resetting | 0.915210 | 8.609% | hand subject 24 F1 0.779 (baseline 0.936) |
| `17e3fb76` | ignore <60% rhythm period without resetting | 0.918614 | 8.226% | hand subject 08 F1 0.865 (baseline 0.932) |
| `be245628` | ignore <55% rhythm period without resetting | 0.914567 | 9.406% | hand subject 08 F1 0.870 |

**Decision:** select `b502376c` detector logic as the more conservative
hand/placement-balanced release candidate, rather than cherry-pick maximum
macro F1 with a large per-user regression. This was based on inspected
benchmark results and is *not* a new trained model.

**New pre-declared participants 10 and 26** were selected from original
SIMUL index before running the final validation. Their step reference must
not be used to retune this candidate. An automatic CI gate requires mean
F1 non-regression, >=3 percentage-point reduction in mean absolute step
count error, <=0.04 per-trial F1 loss, plus improvements on the two newly
reserved subjects. If any gate fails, report failure rather than
silently removing hard participants.

## Final reserved-subject check — gate FAIL (2026-10-19)

**Run:** https://github.com/jboehm1/VisualOdometry/actions/runs/39179451806
at candidate `1857d9f97f4a83f9c8596507b89ecde74c4530f6`.
Dataset: 8 genuine SIMUL participants × 3 placements = **24 trials**.
New reserved participant IDs 10 and 26 were not used for choosing the
candidate; this test consumed their held-out status, so they MUST NOT
be reused as pristine untouched data after another parameter iteration.

| Metric | Pinned original Java | Selected PDR candidate | Result |
|---|---:|---:|---|
| 24-trial mean abs step count error | 13.135% | **7.167%** | **Improved by 5.968 percentage points (~45.4% relative)** |
| 24-trial macro temporal step F1 | 0.916047 | **0.915470** | **Regression -0.000577** |
| New reserved six-trial mean abs count error | 6.079% | **1.288%** | Improved |
| New reserved six-trial temporal F1 | 0.944469 | **0.929793** | **Regressed -0.014676** |
| Participant 26, back pocket temporal F1 | 0.916 | **0.814** | **Serious regression** |

**Quality gate FAILED (intentionally):** macro F1 non-regression, reserved
participant F1 non-regression and maximum 0.04 per-trial F1 loss all failed.
Step count improvement alone must never be sold as globally better
pedestrian odometry. Two successful subgates: >=3 percentage points lower
count error and improvement of count error on the new participants.

The new detector reduces excess counted peaks but suppresses or shifts
valid step events in some carrying conditions. Next engineering tasks:
identify timestamp alignment and peak-phase errors independently of
count totals; build separate training/validation/test subjects for a more
robust classifier; carry a pre-registered frozen evaluation; prefer Android
hardware STEP_DETECTOR when actually available on phone. Never tune against
IDs 10/26 and call their new results held out. No metric pose error or
S21 measurements have been obtained.
