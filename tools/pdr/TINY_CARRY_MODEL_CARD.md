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

## Detector fixture correction after failed Android CI

The existing PDR baseline was deliberately restored following poor
held-out SIMUL F1 for the earlier more aggressive rhythmic filter. A
previous test `shortReboundDoesNotResetEstablishedCadence` assumed that
removed filter's 320-ms impact rejection. It was inconsistent with the
restored baseline, which guarantees **only** a 300-ms refractory window,
not 320 ms. Exact GitHub Android run `39211176795` failed this test
before the TinyML functionality was added. The fixture has been narrowed
to a 240-ms rebound (a real guarantee of the selected code), and its
comment explicitly notes >300-ms shocks can still suppress true steps.
No failing held-out SIMUL validation has been suppressed or re-labeled.
The separate 24-trial F1 quality gate must remain red until genuinely
improved on unseen subjects. CI must be rerun on the new HEAD.

### Source-asset JVM test environment note (2026-10-19)

Exact Android CI `39211943324` compiled the application and reached
JUnit, but `TinyCarryModelTest` failed because Robolectric's default
`RuntimeEnvironment.getApplication().getAssets()` did not expose packaged
Android assets with this project's current Gradle test resources setting.
No modifications to shared `app/build.gradle`: the JVM source-test
fixture now reads the **same Git-tracked asset** under
`app/src/main/assets/pdr_carry_model.b64` (working directory fallback),
then enforces the pinned SHA and independently checked outputs.
Android production code still uses the normal asset manager. APK
packaging remains an additional CI build requirement; **do not equate
source-asset tests with a physical Android run**.

### Compatibility correction from exact Android CI

The follow-up run `39212275510` diagnosed `Files.readString(Path,Charset)`
not present in Android's unit-test API compilation stubs even though
host Java is 17. Replaced this test-only source-asset helper with
Android-supported `Files.readAllBytes(Path)` and an explicit charset.
The failure was a test compilation error, **not** evidence of inferencing
failure; this commit triggers exact-SHA Android unit/lint/APK validation.
