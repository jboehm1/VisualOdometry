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

**Host-testing note (Robolectric):** the PDR START control refuses to open a session if neither the hardware step detector nor accelerometer is available. Robolectric normally exposes no sensors by default. `PdrAndroidTest` installs a simulated accelerometer with `ShadowSensorManager.addSensor(ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER))` before asserting a successful START. The absent-sensor behavior is deliberate; do not bypass it simply to make a simulator pass.

**The PHONE FORWARD opt-in resets after every session and on new Activity creation.** Unlike the measured stride profile, it is never saved in SharedPreferences. This avoids inadvertently treating a phone moved into a pocket as a compass for walking direction.
