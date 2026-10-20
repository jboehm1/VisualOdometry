# Copy-ready handoff — Test Pipeline chat (camera-free neural odometry)

**Date:** 2026-10-18. **User direction:** testing infrastructure and datasets
are owned by the test-env chat; the camera-free chat **must not edit or
replace them**. Please implement the missing neural benchmark gates on
your own test branch, with our AI module providing ONLY the contract.

## Sources and exact baseline

- Integration: `feature/slam-explorer`, observed at
  `372c935fc27536d417fa371d79f5f453546ebefa` (re-fetch before work).
- Camera-free PDR: `feature/camera-free-pdr`, audit initial
  `01edef7b32b86cd76bb2527de14389fd0b5d098d`; see PR #5 and
  `app/src/main/java/dev/jboehm/vectorvio/pdr/ai/`.
- Existing Java PDR engine: `PdrEngine.java` +
  `StepPeakDetector.java` and `tools/pdr/PdrReplay.java` — pure-Java
  tests and Android CI pass, but this is the fixed-stride **non-neural**
  detector.
- Existing real-data evidence:
  `docs/campaigns/REAL_SIMUL_JAVA_PDR_20261018.md`, run
  [39162292672](https://github.com/jboehm1/VisualOdometry/actions/runs/39162292672).
  SIMUL has authentic multi-sensor IMU and human step-interval annotations.
  **This validates step detection, NOT metric 2D/3D neural trajectory.**
- Existing original-format adapters:
  `tools/pdr/datasets.py` for OxIOD CSV and RoNIN HDF5,
  `tools/pdr/run_real.py` for Java PDR, and
  `.github/workflows/pdr-datasets.yml`: as audited, real datasets not
  yet run there, and the workflow generates **synthetic schema fixtures**.
- Another independent unified benchmark PR #10 may be active: inspect
  and preserve its work before touching any shared registry.

## Most appropriate datasets, in order

**1 — RoNIN pedestrian data (priority for learned body heading/carry handling).**

Use original RoNIN sequences with synced `time`, `acce`, `gyro`
and proper `info.json/start_frame`. Record 6-axis units, initial
orientation, sensor/body transforms and source IMU calibration.
`pose/tango_pos` is a Tango **visual-inertial proxy** reference;
identify it as such, never call it independent optical ground truth.
First held-out benchmark must partition participants (not windows)
and carry modes (hand/pocket/bag) to avoid train/test leakage.
Do not use Tango heading/orientation to create IMU-only directions at
evaluation time. Training labels may be obtained from annotated training
sequences, but the inference must not see held-out truth.

**2 — OxIOD original optical/Vicon subsequences where available.**

For quantitative independent position and local displacements,
use explicit original `syn/imuN.csv` and matched `syn/viN.csv`,
review original sample units (gravity/user_acc g, gyro rad/s), timestamps,
Vicon-vs-Tango reference quality and coordinate transformations.
Optical Vicon subsequences give a stronger independent sanity check than
RoNIN Tango for metric trajectory accuracy. Hold out people/sessions/
carrying positions; beware unsmoothed path-length noise.

**3 — TLIO released golden dataset (only when implementing TLIO/EqNIO).**

Separate from RoNIN feature layout. The TLIO example network-only
testing from upstream warns it uses ground-truth orientation to integrate
displacements. Report such cases as an **oracle upper bound**, never a
deployable result. A real benchmark must run the network + legitimate
IMU-based heading/EKF and report calibration of uncertainty and failures.

**4 — SIMUL for step counts and gait robustness only.**

Reuse the real data already tested to challenge accelerometer fallback
across users and placements. No position trajectory reference is
available from step annotations alone, so do NOT report ATE/RPE from SIMUL.

## Missing work / acceptance gates

1. Obtain original archives legally; record original URL, release/commit,
   terms, raw SHA256, sequence count, sensor units, reference quality.
   Do not commit raw data to Git. A README link and JSON catalogue are
   not proof that an actual dataset was processed.
2. Implement a **model-specific** preprocessing contract, independent
   ground truth loader and a synchronized IMU input stream. Our new
   `NeuralMotionModel` interface consumes an actual 64x6 device-IMU
   window; `PortableInertialModel` uses 24 engineered features and
   is **NOT RoNIN/TLIO**.
3. Before attempting on-device integration, establish an actual
   **trained checkpoint** with provenance, license, exact feature/units/
   frame normalization and SHA. Without it set status
   `NO_MODEL_AVAILABLE` and do NOT report "AI test passed".
4. Train/validate on separate participants. Freeze model and calibration
   before held-out tests. Provide both per-user and pooled metrics;
   report missing/failed trajectories rather than silently dropping them.
5. Run reference original PyTorch/TensorFlow network, export (ONNX/LiteRT
   or explicit binary only when architectures mathematically match),
   and compare golden tensors vs Java/Android in inference-only mode.
6. Score no-ground-truth-alignment deployable displacement, total distance,
   trajectory ATE, RPE, drift, yaw error/turns and frame coverage where
   the corresponding **independent** truth exists. Show units, axes,
   clock sync, start origin and optional motion type; do not fit
   yaw/scale/time against held-out GT.
7. On low-speed, still, pure rotation, walking, running, pocket/hand
   transitions and noisy magnets: test reject/uncertain states, fallback
   and physical non-observability.
8. Produce machine-readable report with `dataset_id`, `dataset_sha`,
   `subject_split_hash`, `model_id`, `model_sha`, `engine`,
   `gyro_acc_units`, `model_frame`, `truth_frame`, `truth_source`,
   `is_ground_truth_used_for_inference=false`, `sample_coverage`,
   failure count, CPU latency, metrics and explicit evidence grade.
9. CI gates must separate `SCHEMA_FIXTURE_PASS`,
   `REAL_ORIGINAL_DATASET_TESTED`,
   `TRAINED_MODEL_REPRODUCED`, `ANDROID_MODEL_INFERENCE_PASS`,
   `S21_FIELD_VALIDATED`. Synthetic fixtures are not evidence
   for the latter four states.
10. Keep dataset downloads and external weights out of GitHub PR files.
    Use CI artefacts/Drive reports and externally pinned model/weights
    only after permission review. Do not modify application menus,
    Android manifests or owner-specific model code without coordination.

## Suggested test command for non-neural baseline only

```bash
python3 tools/pdr/datasets.py ronin \
  --sequence /DATA/RoNIN/<subject>/<seq> \
  --reference-quality visual_inertial_proxy \
  --subject-id <verified-subject> \
  --out /tmp/ronin-prepared

python3 tools/pdr/run_real.py \
  --prepared /tmp/ronin-prepared --out /tmp/ronin-pdr-java
```

**This command only tests the step-based Java PDR baseline**. A trained AI
model runner is NOT finished. Do not alter these existing pipelines on behalf
of the camera-free chat; implement/check their readiness in the test-env
workstream, and share exact commit/run/report links once genuinely passing.

## Expected reply to the camera-free model chat

Give us: verified usable original dataset paths/IDs, license terms,
reference type, train/validation/test partition, raw/preprocessed SHA256,
frozen trained checkpoint and normalization recipe, expected output
coordinate frame, exact metrics from a clean real run, and a golden input/
output tensor for parity. If any part is missing, state which gate is open.
