# Portable camera-free neural candidate (NOT a trained RoNIN/TLIO model)

Model loader & inference: `app/src/main/java/dev/jboehm/vectorvio/pdr/ai/`.
**No pretrained network or learned weights are included.** This is a bounded,
Java-17 portable inference implementation awaiting properly trained, provenance-
reviewed weights. It does not alter the baseline PDR odometer.

The **default is no model**. The `NeuralDiagnosticRunner(null)` receives gyro
and accelerometer measurements, buffers and validates them, but cannot return a
fake neural displacement. A trained `PortableInertialModel` must be loaded
explicitly with **its expected externally reviewed SHA-256**.

## Strict input contract

- Timestamp: Android monotonic elapsedRealtimeNanos, positive.
- 64 frames near 50 Hz (~1.26 s per window), 6 channels:
  ax, ay, az in m/s²; gx, gy, gz in rad/s; **device axes**.
- Gyro is causally sample-and-held, no future data, at most 40 ms old.
- High-frequency accelerometers are decimated; gaps >55 ms reset state,
  stale gyro, out-of-order or nonfinite samples reject observation.
- Feature extraction deterministic, exactly 24 values:
  channel means [0..5]; channel std [6..11]; channel deltas [12..17];
  mean accel and gyro vector norm [18..19]; vector-norm std [20..21];
  RMS adjacent-vector delta [22..23].
- Network: 24 → 32 ReLU → 4 linear, standardized by training means/scales.
  This is a compact independent baseline **architecture**, not copied code
  or weights from any RoNIN/TLIO/EqNIO paper.
- Outputs: device X/Y incremental displacement **candidates**, log scales.
  These are **not body heading, metric geographic EN, GPS coordinates, or
  statistically calibrated confidence intervals**. Network outputs
  are never fused into `PdrEngine` until separate alignment, covariance,
  origin, and validation constraints are proved.
- Bounds: implied displacement speed <=8 m/s and scales bounded to plausible
  values. No claim these thresholds represent measured calibration.

## Binary model card + weights, version 1

The exact on-disk format is documented in `PortableInertialModel.java`.
It begins with `DataOutputStream.writeUTF("PDR_AI_MLP_V1")`, followed by
UTF model ID, SPDX license string, training dataset ID and
`DEVICE_XY_METRES` output frame. Three int dimensions 24/32/4.
Then float32 arrays in this order:

| Tensor | Elements | Order |
|---|---:|---|
| normalization mean | 24 | inputs 0..23 |
| normalization scale | 24 | inputs 0..23 |
| first-layer weights | 768 | row-major hidden x input |
| first-layer bias | 32 | hidden |
| output weights | 128 | output x hidden |
| output bias | 4 | dx, dy, log sx, log sy |

The loader validates SHA256, identity, declared output frame, dimensions,
finite parameters, scales, EOF and size. A checksum proves reproducibility,
not that a model is **safe, trained or legally redistributable**.

## Android activation gate (intentionally unfinished)

No weights are bundled, and `PdrActivity` does not switch on this engine.
Before enabling any AI prediction in the UI:
1. Agree on a **truth-free training feature extractor** identical to Java,
   with causal gyro alignment, unit/axis tests and a schema fixture.
2. Train on several **different people and carry locations** with a held-out
   subject split. Do **not** align a held-out trajectory with GT heading.
3. Check license and provenance of model artifacts; convert/quantize
   explicitly with model hash and version.
4. Verify offline Python → JVM golden tensors bitwise or to tolerances,
   and ensure on-device timing under CPU/battery budget.
5. Calibrate the uncertainty on held-out data; verify that a learned
   DEVICE-XY candidate is transformed into physical/world frame using
   independently available orientation, not hidden GT.
6. Compare against fixed-stride Java PDR on the same sequences; accept
   only if there is an improvement and no silent degradation.
7. Add explicit `AI EXPERIMENTAL` opt-in, fallback, user-readable quality
   and scope to the PDR UI only after the preceding gates.

**No test-pipeline or dataset scripts were modified for this contract.**
Its documentation lives inside `tools/pdr/` so the existing unchanged
GitHub Actions PDR workflow validates the new Java module.

## Links

- [RoNIN official code](https://github.com/Sachini/ronin)
- [TLIO official code](https://github.com/CathIAS/TLIO)
- [EqNIO](https://github.com/RoyinaJayanth/EqNIO)
- [IMUNet Android](https://github.com/BehnamZeinali/IMUNet_Android)
- [DeepILS](https://github.com/OmerTariq-KAIST/DeepILS-IoT-Journal-2025)

These are **candidates**, not included software; their trained architecture,
preprocessing, frames, weights and licenses do not match this tiny portable MLP.
