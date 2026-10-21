# Geographic heading observability audit — 21 October 2026

Source baseline: `94e428aa81f96f24425c64fa3344a510332b7736` (PR #43).
Integration inspected live: `3d251b8e06536a972b88bcf01bd34912ea5ceabd`.
Branch: `work/autonomous-quality-20261021`, isolated descendant, not integrated.

## Reproduced failure and change

The original Java estimator accepted an 18 m VIO path paired with frozen GPS:
10/10 inliers, raw RMS 5.745 m, `CANDIDATE`, despite no geographic yaw information.
A single 100 m VIO outlier also supplied the pre-fit baseline while 9 compatible
pairs were effectively stationary (raw RMS 31.418 m). Both now return
`NEED_BASELINE`; projection and inverse projection remain unavailable.

After IRLS selection, require minimum motion in **both** coordinate systems on
compatible pairs: `min(pair VIO span, pair GPS span) >= max(15 m, 4 × max(endpoint accuracies))`.
The same pair must support both spans. A compatible low-weight GPS point with
50 m uncertainty cannot borrow the precision of many stationary fixes. This
additional case was found after the initial fix and rejected in a second iteration.
No route geometry, truth data or scale fitting enters the estimator. Raw RMS is
preserved; diagnostic baseline now uses the smaller compatible span. Added
four JUnit regressions. Existing GPS-outlier tolerance must remain functional.

## Before/after: synthetic contract experiment, not field accuracy

`tools/gpx_ar/GeoObservabilityAudit.java` uses 500 fixed random seeds and headings
per scenario. Noise and GPS spikes are synthetic. Test counts are not confidence
intervals or a held-out real-world evaluation.

| Scenario | Before candidates / 500 | After candidates / 500 | Desired |
|---|---:|---:|---|
| Frozen GPS, VIO motion | 500 | 0 | Reject |
| GPS jitter 0.2 m, VIO motion | 500 | 0 | Reject |
| Baseline supplied by excluded VIO point | 500 | 0 | Reject |
| One compatible 20 m GPS displacement with 50 m uncertainty | 500 | 0 | Reject |
| Coherent curved path, 0.45 m GPS noise | 500 | 500 | Retain |
| Coherent path with one 50/60 m GPS spike | 500 | 500 | Retain |

Raw JSON: `evidence/geo-observability-before.json` and `...-after.json`.
Elapsed time is a single shared-container Java run (startup/JIT/noise included),
not a phone CPU, latency or energy benchmark. Extra work is bounded by 64² pair
checks per fit; no additional sample storage beyond a 64-element mask.

## Reproduction

Compile the exact Java estimator, WGS84 frame and audit source with Java 17:

```bash
javac -d /tmp/geo-audit app/src/main/java/dev/jboehm/vectorvio/GeoAlignment.java app/src/main/java/dev/jboehm/vectorvio/geo/Wgs84LocalFrame.java tools/gpx_ar/GeoObservabilityAudit.java
java -cp /tmp/geo-audit GeoObservabilityAudit
python3 -m unittest discover -s tools/gpx_ar/tests -v
```

For the baseline compile the same harness against the original `GeoAlignment`
from source SHA above in a separate output directory. Do not mix classpaths.
Local runtime supplies the Java compiler module without a `javac` executable;
`java -m jdk.compiler/com.sun.tools.javac.Main` is the equivalent used here.
18 GPX replay/audit tests passed locally against the changed exact Java source.
Full Android/JUnit/lint/APK validation is tracked in the PR at its exact SHA.

## Remaining limits

Shared GNSS bias, smooth multipath, independent calibration and scale failures
are not certified by this check. It is not a probabilistic transformation filter.
No hardware, RTK, battery, thermal or physical-navigation test was performed.
Native VIO remains absent; ARCore remains default. Next work: harden measured
comparison inputs, then inspect further GPX reacquisition and source identity.
