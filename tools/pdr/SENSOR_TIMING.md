# PDR 1.2: Sensor timing and callback health

**Owner**: camera-free PDR; no test-env, shared workflow, manifest or main-menu modifications.

The pure-Java `SensorTimingMonitor` instrumented by `PdrSensorBridge`
records the **monotonic Android event timestamp** and the callback arrival
timestamp (`SystemClock.elapsedRealtimeNanos()`) separately.

Observed counters (per sensor): received events, duplicate timestamps,
out-of-order timestamps relative to the high-watermark, invalid timestamps,
samples stamped ahead of callback time, maximum callback lag, and delayed
callbacks exceeding 200 ms. Periodic accelerometer / gyroscope /
magnetic-field / rotation-vector streams also count intervals exceeding
250 ms and record the maximum observed interval. Pressure / hardware steps
are naturally sparse; they are excluded from the periodic gap threshold.

These diagnostics are shown on the PDR Activity and exported to ZIP
`metadata.txt` with the existing `events.csv`, `raw_sensors.csv`
and `path.csv`. An event before session START is rejected at the bridge
and also cannot prime the pure-Java accelerometer fallback.

**Interpretation**: a long sample interval can mean batching, sensor
throttling or suspension; timestamp holes and callback delays **do not
identify the number of lost samples, prove overflow, measure FIFO
capacity or certify IMU accuracy**. Callback timestamps are not sent into
the odometry engine. Battery impact and sensor rates must be measured on a
Galaxy S21 under screen-on/off and app lifecycle scenarios.

Test commands (from root):
```bash
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
bash tools/pdr/run.sh
python3 tools/pdr/progress.py --check
```

Relevant tests:
`app/src/test/java/dev/jboehm/vectorvio/pdr/SensorTimingMonitorTest.java`
checks duplicates/order/periodic gaps/latency and that sparse steps are
not misclassified. `PdrEngineTest` rejects pre-START accel, and
`PdrExportTest` verifies the actual ZIP metadata. The routine
`tools/pdr/smoke_datasets.py` retains its **synthetic schema** scope;
it is NOT a field-dataset or a trained-model test.

The acceptance gate PDR-04-04 remains open until actual S21 traces and
long-run interruption / batching observations exist.
