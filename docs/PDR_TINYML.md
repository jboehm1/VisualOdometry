# Ultra-lightweight ML on six-axis IMU — PDR research pilot

User request: determine whether IMU AI is expensive and add it if suitable.

**Decision:** yes, lightweight inference is cheap enough to investigate on
Android CPU without external accelerators or extra Android library dependencies.
Example planned 24→16→3 MLP for real SIMUL sensor-position classification:
**451 trainable parameters**, **432 multiply-adds** per inference and
**1.5 KiB** raw 64×6 window. At 2 Hz: just 864 MAC/s + filtering and
sensor callbacks. These are arithmetic FLOP/MAC **estimates**, not a device
latency/battery measurement. Compare whole Android sensor pipeline energy
before claiming actual savings; sampling and wake-ups can dominate.

Research sources:
- LiteRT edge AI: https://ai.google.dev/edge/litert/ (CPU fallback available).
- Official Android sensor manager docs:
  https://developer.android.com/reference/android/hardware/SensorManager
  sensor delivery/suspend/batching/wake locks cause power variation.
- Example TinyML 6-axis 1D CNN with ~3,000 parameters / 12.5-KB quantized
  model on ESP32-S3: https://github.com/Yihao23/ble-gesture-recognition
  (third-party claim, not a measured VECTOR VIO benchmark).
- RoNIN official code: https://github.com/Sachini/ronin; RoNIN models predict
  displacement/body heading, **not compatible** with this simple MLP binary.

## Real supervised training result (19 October 2026)

Measured on genuine IMU from the original SIMUL repository,
https://github.com/jboehm1/VisualOdometry/actions/runs/39211176862 :

| Human-disjoint subset | Balanced 3-class accuracy | Macro F1 |
|---|---:|---:|
| Training, 6 people (08,17,24,25,12,31) | 81.16% | 80.93% |
| Validation, 2 people (10,26) | 59.85% | 57.11% |
| New, frozen 2 people (04,29) | **58.89%** | **57.22%** |

The untrained/random 3-way benchmark would average 33.3% accuracy,
but this is **not** a validated phone carry-mode classifier. Test
generalization is modest; front-pocket sensor recall on frozen people was
only 29.1%. No parameters may be retuned and called held-out on IDs 04/29.

A full real checkpoint was exported: `simul-carry-proxy-20261019`,
**2,151 bytes**, `24->16->3` (451 parameters / 432 MACs).
Model SHA256: `9e572920b796d03ea528a040694039851461b5c39e6d1549809c751da3940dc2`.
License: CC-BY-SA-4.0; attribution: simpleLoc/SIMUL-dataset at
`ee7eb4e89bf33a17cafb4268207b802773427493`.
A copied, exact checksum-pinned base64 checkpoint is included **only as
an experimental Android sensor-placement diagnostic**, with an explicit
opt-in button. It does not touch steps, path, distance, heading or
position and is not an automatic adaptation mechanism.

The Java interpreter and Robolectric tests verify checksums, model
provenance, positive normalized class scores, input rejection, resource
budgets and non-fusion. Android UI shows observed inference timing
(avg/max), not a laboratory battery measurement.

## What the actual learning task means

The original `PDR_AI_MLP_V1` (24→32→4) foundation estimates **device-frame
displacement with zero real weights**, which cannot safely be switched on.
This separate pilot trains a `PDR_TINY_CARRY_V1` 24→16→3 classifier for
**SIMUL placement identity** from six-axis IMU windows (front pocket sensor,
hand sensor, back pocket sensor). Sensor locations are those of the SIMUL
original experimental setup, not necessarily a Galaxy S21 in clothing.
Do NOT call this step detection, body heading, walking intent, or metric
navigation accuracy. Never alter PdrEngine trajectory/step counts with it.

Training: genuine original SIMUL records pinned by SHA, six training human
IDs 08/17/24/25/12/31, two validation IDs 10/26 and two never-before
inspected research subject IDs 04/29 for a single frozen check.
Subject-disjoint split; mean and scale computed from train alone;
no use of position labels at inference beyond model input. Export includes
license provenance, SHA, exact feature schema and float32 weights.
Any evaluation after inspecting new subjects is **not** untouched validation.

Code: `tools/pdr/tiny_carry_train.py` and PDR-owned GitHub workflow
`.github/workflows/pdr-tinyml.yml`. Initial checkpoint will be generated
as a CI artifact, and not automatically inserted into the APK. This is
intentional: review held-out error, Java↔Python tensor parity, exact model
hash, legal CC-BY-SA-4.0 attribution and real Android device before opt-in
diagnostic integration. No background service, permissions, Manifest,
MainActivity or shared benchmark edits.

Future steps: only if the above proxy classifier generalizes, add a small
audit-only Android diagnostic and use carry-class posterior as a *candidate
quality signal*, never as an automatic step rejection. A real step-output
network would require step-event labels, temporal de-duplication, held-out
person/place splits and direct paired 24-trial F1, especially participant
26 back pocket where hand-coded filters failed.

### Low-resource architecture selected
No TensorFlow / LiteRT / ONNX library was necessary for this exceptionally
small 451-parameter MLP; pure Java inference is simpler and avoids
additional APK binary dependencies. The experimentally reported ~59%
test classification is not strong enough to gate operational steps.
Power draw depends on sensor acquisition, task wakeups and continuous
foreground lifecycle; hardware S21 battery profiling still required.

### Numerical parity reference

The checked-in research binary is audited with a fixed SHA and two
checkpoint-reference softmax fixtures on the Android Robolectric/JUnit
runner; these test real trained weights instead of synthetic constants.
The Activity loader reads the asset through a bounded stream loop and
refuses missing/corrupt files. The optional diagnostic resets with each
session and does not change PDR distance, step, heading or path estimates.
