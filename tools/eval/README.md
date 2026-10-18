# Metric trajectory validation (experimental, headless)

`trajectory_metrics.py` evaluates *independently obtained* trajectories and never runs or trains SLAM. Designed to help B1 / B2 / B5: tests run without `numpy`, dataset downloads, network or proprietary services.

```bash
python3 tools/eval/trajectory_metrics.py \
  --est /path/algorithm.tum --est-format tum \
  --truth /path/independent-ground-truth.tum --truth-format tum \
  --up z --out /tmp/ate-rpe.json
# For tracked phone local frame, not global geolocation:
python3 tools/eval/trajectory_metrics.py \
  --est /path/session_motion.csv --est-format vector-motion \
  --truth /path/independent-reference.tum --truth-format tum \
  --up y --time-offset-ns 0
```

**Output:** independent-truth matched coverage, metric yaw+translation aligned ATE RMSE/p95 and translation-only RPE at ~1 s; exact status for insufficient/degenerate sequences. 1 position at 1 timestamp equals 1 observation. The code does **not** optimize scale, so errors from metric-scale drift remain. It does **not** do full SE(3) registration (requires suitable gravity-aligned reference), orientation RPE, ground-plane semantic tracking, or WGS84 accuracy. It estimates alignment independently per continuous segment and **never** reports combined absolute map accuracy across reset segments.

**Timing:** formats `tum` (timestamp seconds + xyz + quaternion), `euroc` (nanosecond timestamp + xyz...), and `vector-motion` (frame_time_ns, metric_x/y/z). TUM/EuRoC metadata/calibration and segment conventions must be verified manually before comparison; a CSV named “ground truth” is not automatically independent truth. Timestamps are required in the same timebase; an explicitly selected constant `--time-offset-ns` is logged (automatic offset fitting to minimize error would risk optimistic bias). GT interpolation is rejected across gaps > `--gt-gap-s` (default .15 s). No extrapolation.

**VECTOR caveat:** New `_motion.csv` exports append `odometer_segment`; the evaluator honors that authoritative ID. Older exports lack it: the evaluator infers splits from gaps > 0.7 s, **but tracking can reset within a shorter interval**. For rigorous comparisons using old sessions join their main CSV segment/event log. Anchor rebase is not necessarily a tracking segment reset.

**Gate:** do not mark benchmark complete until (a) real dataset actually cached, calibrated and licensed, (b) engine outputs *unseen* estimates, (c) independent truth covers a declared fraction, (d) time/frame transforms fixed without per-sequence tuning, (e) failures and degenerate runs counted. The ARCore APK cannot replay EuRoC images directly; use an engine that supports that dataset.
