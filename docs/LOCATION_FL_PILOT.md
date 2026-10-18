# Android GNSS versus Google Fused Location Provider — VECTOR VIO pilot

## Decision / scope

Before designing a custom GPS–PDR–VIO filter, benchmark Android's existing positioning. The integration app currently selects **LocationManager.GPS_PROVIDER** (or NETWORK_PROVIDER when coarse permission), not Google's Play Services **FusedLocationProviderClient**. This pilot adds an independent **opt-in, foreground-only** comparison activity reachable via **DATA → GPS ↔ FUSED — Android location A/B (experimental)**.

This change **does not** replace GPX navigation, change camera/ARCore frame cadence, modify odometry, affect the PDR engine, or add background location, internet, microphone, camera or automatic upload permissions. It requests **ACCESS_FINE_LOCATION** at runtime, and clearly refuses approximate-only permission, missing Google Play Services or disabled GPS. Display remains on during an active test; activity backgrounding/locking stops collection.

## Sources and synchronization

| Stream | Android API | Requested updates | Clock |
| --- | --- | --- | --- |
| A — native satellite | \`LocationManager.GPS_PROVIDER\` | 1000 ms, 0-m distance filter | \`Location.getElapsedRealtimeNanos()\` |
| B — Google fusion | \`FusedLocationProviderClient\` / \`PRIORITY_HIGH_ACCURACY\` | nominal 1000 ms, min 500 ms | \`Location.getElapsedRealtimeNanos()\` |

Google dependency \`com.google.android.gms:play-services-location:21.4.0\` (published in official Google Play Services release notes on June 25, 2026). Both callbacks use **one HandlerThread**, allowing deterministic serial logging; each received fix is stamped with the original fix monotonic clock and the **local receipt** monotonic clock. GPS and fused locations can originate from the **same underlying GNSS measurements**. A small disagreement does not demonstrate accuracy.

The benchmark keeps raw fixes even if unusable. It pairs only *one-to-one* fixes with time difference **≤1.5 seconds**, a FIFO of at most **12 unmatched fixes per source**, and **no interpolation**. Out-of-order or duplicate timestamps, mock locations, unknown horizontal accuracy, claimed ± accuracy over 30 m and aged/future fixes (>5 s age or >0.5 s future) cannot form a pair. These are experimental thresholds, not safety standards; they do not apply to GPS navigation.

## Local results and export

Session path: private app storage \`files/location_comparisons/location_ab_<UTC>_<random>/\`.

- \`fixes.csv\`: source, original GNSS/FLP monotonic timestamp, callback arrival monotonic timestamp, wall timestamp, latitude/longitude, horizontal and vertical reported accuracy, altitude, speed/speed uncertainty, course/bearing uncertainty, mock and validity reason. Missing optional fields remain empty, **not zeros**.
- \`pairs.csv\`: both source timestamps, temporal difference, horizontal **disagreement** between reported points, both reported accuracies and original WGS84 coordinates.
- \`metadata.json\`: schemas/source APIs, request intervals, device, stop reason, count and the explicit field \`accuracy_ground_truth=NOT_AVAILABLE\`.

The activity shows update counts, rejected pairing samples, last estimated accuracy and age, mean/max disagreement and mean time separation. It never says “GPS positioning RMSE.” It saves CSVs in private files, flushes periodically, finalizes them when stopped and exports exactly three whitelisted files through Android's **ACTION_CREATE_DOCUMENT** system ZIP picker. An unfinished session is not considered a valid export. Session row cap: 200,000; approximate file-size cap: 25 MiB. Abrupt OS process death may truncate recent buffers.

The Play Services location provider itself may communicate over the network according to the user's device/Google settings; this app does **not** independently upload its local files.

## Concrete evaluation protocol (physical Samsung S21 still required)

1. **Static, outdoor 2–5 min:** true no-motion baseline, timestamp lag, dropout, jitter.
2. **Known-length track:** walk and run over a surveyed route, with independent distance and accurately placed ground-control points; compare two API tracks to the **same external reference**, never to one another as truth.
3. **Urban canyon / tree cover:** repeat at least three traversals, keep failed samples, characterize jumps, lag and recovery.
4. **Tunnel / indoors:** determine how often FLP provides fresh usable positions when the GNSS stream goes stale, and the recovery delay. This pilot contains **no inertial dead reckoning**.
5. **Power/correlation control:** compare GPS-only, FLP-only and simultaneous use in future experiments because parallel subscriptions can warm or share the same GNSS engine and distort power measurements. Capture battery/thermal conditions separately.

Current JUnit fixtures can validate time pairing and export **logic**, and GitHub Android CI can validate compilation and UI construction; only the physical trials can establish real gains, availability and battery impact.

## Coordination / integration boundary

Shared files changed on this **isolated** branch: \`MainActivity.java\` (one DATA entry with a **text-labeled semantic dispatch** rather than claiming index 10), \`AndroidManifest.xml\` (private activity), \`app/build.gradle\` (FLP dependency). All other additions are new private feature files. Existing DATA entries 0–9 remain byte-for-byte unchanged in their meaning; the GPX navigator is not rerouted.

PDR, store planner, GPX/VIO preview and ARCore replay branches also edit MainActivity and/or AndroidManifest. **No full-file ours/theirs merge**. Their actions and manifest registrations must all survive integration. Test the **combined** APK and not just this branch. Coordination handoff: \`docs/coordination/android-location-flp.md\`.

Official sources:
- https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient
- https://developers.google.com/android/reference/com/google/android/gms/location/LocationRequest
- https://developers.google.com/android/reference/com/google/android/gms/common/GoogleApiAvailability
- https://developers.google.com/android/guides/releases
- https://developer.android.com/reference/android/location/Location


## Local history and offline analyzer

DATA → A/B retains complete tests in app-private storage. From the A/B screen, **MES TESTS · EXPORTER / SUPPRIMER** allows the user to retrieve an older session after reopening the app, export it, or explicitly delete its private coordinates. Deletion refuses unrecognized paths, symlinks, incomplete sessions, or extra unexpected files. There is no automatic cloud sync.

Run the exported ZIP entirely offline:

```bash
python3 tools/location/analyze.py /path/to/location_ab_....zip --out /tmp/location-ab-report.json
# Only when stationary with an independently surveyed control point:
python3 tools/location/analyze.py /path/to/location_ab_....zip \
  --static-control 48.123456 7.123456 \
  --reference-note "surveyed static marker: method/date/source" \
  --out /tmp/stationary-check.json
```


The analyzer validates archive members, counts and timestamps and reports source-specific accepted fixes, update intervals, callback age, stated accuracy, and the mean/p95 pairwise disagreement. **None of these is a position accuracy claim.** Optional static control produces a user-asserted reference error for a **stationary** session only and is never treated as independently verified or a moving trajectory ground truth.

See `tools/tests/test_location_ab_analysis.py` for standalone deterministic contract tests. For moving-truth trajectory ATE/RPE, send synchronized estimates to the dedicated benchmarking project and identify exact engine, timebase, held-out truth source and frame transformations. 
