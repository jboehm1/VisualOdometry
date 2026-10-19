# Frozen trained IMU diagnostic checkpoint v1

This PDR-only report includes references that can be checked independent
of the Android package. It intentionally triggers the existing PDR
JVM/Robolectric/lint/APK CI on PDR tool changes; no shared workflow edited.

- CI *real-training* run: https://github.com/jboehm1/VisualOdometry/actions/runs/39211176862
- Dataset: simpleLoc/SIMUL-dataset @ `ee7eb4e89bf33a17cafb4268207b802773427493`.
- Provenance/license: SIMUL source sensor data **CC-BY-SA-4.0**; model
  attribution distributed under CC-BY-SA-4.0; original repo and
  license https://github.com/simpleLoc/SIMUL-dataset .
- Architecture: 24→16 ReLU→3 Softmax, 451 float32 learnable parameters,
  exactly 432 input/hidden + hidden/output multiply-adds per inference.
- Binary checkpoint size: **2151 bytes** (Base64 asset 2869 ASCII bytes)
  + 64×6 float32 ring window = **1536 bytes** raw input (plus object/allocation
  overhead). CPU budget: at max 2 inferences/sec ~864 MAC/s **model only**.
- SHA256 (model *binary*, not Base64 text):
  `9e572920b796d03ea528a040694039851461b5c39e6d1549809c751da3940dc2`.
- Frozen result: 04+29 (6 labeled SIMUL body-sensor streams) 58.8856%
  balanced/overall accuracy, macro F1 **0.572215**. Training 81.1645%;
  validation 59.8459%. No target S21 phone-model guarantee.
- Model inputs: 64 contiguous six-axis IMU frames, 14–55ms apart after
  causal gyro sample hold, use the existing pure-Java `InertialWindow`.
  The 24 features are **not** orientation-invariant.
- Output semantic only: `front-pocket sensor` / `hand sensor` /
  `back-pocket sensor`. **Not navigation, step detection, pose, body
  heading or calibrated confidence**. Live display is opt-in only.
- Java model test golden features all zero:
  `[0.0004716125376276549,0.5149571321795693,0.4845712552828031]`.
  Sloped features `f[i]=i/2-5`: class 2 probability
  `0.999989647309023`. Tolerance 1e-6, independently decoded
  float32 binary and calculated with reference forward pass.
- Training/candidate test persons are now all exposed; no fresh heldout
  claim on IDs 04/29 in future improvements.
- Do not silently fuse this output into `PdrEngine`; any deployable
  carry-dependent step classifier needs a direct no-regression gate across
  fresh persons (prior failure on SIMUL #26 back pocket) and S21 measurements.
- Sensor continuous power draw, wakeup costs, RAM overhead, thermal effects
  and device latency have **not** been measured on S21.
