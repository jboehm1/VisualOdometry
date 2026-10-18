# Camera-free mode compatibility notes

The Java PDR core under `app/src/main/java/dev/jboehm/vectorvio/pdr/`
is shared with `tools/pdr/PdrReplay.java`. The **UI mode** changes which
HEADING events are *accepted* and recorded by the Android sensor adapter,
not the meaning of those replayed events:

- **DISTANCE ONLY** default: do not turn phone azimuth into user heading.
  Real GNSS course, if explicitly enabled and quality accepted, can still
  supply a direction. With no GNSS, every step contributes to distance but
  NOT to a misleading 2D trace.
- **PHONE FORWARD** opt-in: use the top edge of a nearly level screen-up
  phone as assumed walking direction. The screen-up gate is cos(45°).
  Magnetic strength must be 20–75 µT and the last sample <2s old.
- **WALK/RUN**: two manually stored fixed-stride settings, not learned
  stride-length prediction. Session START and STEP_LENGTH are logged to
  `events.csv`. Replays remain deterministic.
- **Calibration**: user enters an independently measured course length for
  their completed session; the next session uses measured_m / accepted_steps.
  Do not use a held-out test sequence's GT to tune that stride.
- **Step detector** is used only if Android subscription succeeds;
  accelerometer magnitude fallback is otherwise experimental.
- **Pressure** yields relative elevation, not stable absolute height.
- **Speed** is derived from recent detected step cadence * stride, not GPS.

This note is in the module-specific tools directory so its code changes
under GitHub Actions can be checked without editing shared workflows. All
performance claims on a physical Galaxy S21 remain unverified.
