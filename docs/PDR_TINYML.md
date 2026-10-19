# Ultra-lightweight ML on six-axis IMU — PDR research pilot

User request: determine whether IMU AI is expensive and add it if suitable.

**Decision:** yes, lightweight inference is cheap enough to investigate on
Android CPU without external accelerators or extra Android library dependencies.
Example planned 24→16→3 MLP for real SIMUL sensor-position classification:
**435 trainable parameters**, **432 multiply-adds** per inference and
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
