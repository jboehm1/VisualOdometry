# Physical Samsung S21 / other Android phone acceptance checklist

The field CSV evaluator is now executable **when real on-device CSV evidence exists**. This step cannot be completed by GitHub runners because no physical phone, camera/ARCore session, surveyed site, thermal probe or Android battery measurement is connected. It never forges a test result.

## Input and privacy

Install the APK built from the exact combined SHA. On the same ARCore-supported phone collect at least:

1. Three independent **10m surveyed straight** recordings, with 10m measured as a walked path (not start/end GPS separation). In each, stand still for acquisition, START, walk between tape marks, STOP and export complete CSV. Repeat also while running when relevant.
2. Three **stationary >= 60s** recordings in distinct orientations. No walking, rotating or hand waving; this measures drift. For rotation-only use separate `rotation` case with 0m path.
3. Useful complementary tests: 10m return loop (10m walked, net displacement 0), square, stairs, camera covered, low light, GPS unavailable, pause/resume, screen/thermal conditions. These are valuable but are not covered by the three built-in initial numeric acceptance gates.
4. Optionally note start/end Android battery percentage and handset temperature (only if genuinely measured). Battery percentage drop is **not a power measurement in watts or joules**, and estimated thermals from Python runner are not S21 thermals.

Store each actual exported session CSV next to a `trials.json` JSON manifest locally. Do not put raw GPS traces, personal images, recordings or private notes in the public repository.

Example manifest (only filenames are illustrative, **not real test data**):

    {
      "schema_version": 1,
      "trials": [
        {"id":"walk01","file":"walk01.csv","scenario":"straight","known_path_m":10,"device":"S21"},
        {"id":"walk02","file":"walk02.csv","scenario":"straight","known_path_m":10,"device":"S21"},
        {"id":"walk03","file":"walk03.csv","scenario":"straight","known_path_m":10,"device":"S21"},
        {"id":"still01","file":"still01.csv","scenario":"stationary","known_path_m":0,"device":"S21"},
        {"id":"still02","file":"still02.csv","scenario":"stationary","known_path_m":0,"device":"S21"},
        {"id":"still03","file":"still03.csv","scenario":"stationary","known_path_m":0,"device":"S21"}
      ]
    }

Run after obtaining the **real** files:

    python tools/field/qualify.py --manifest /private/s21_trials/trials.json \
      --out /private/s21_results/qualification.json

The resulting report contains per-trial SHA256, actual pose update rate, maximum gap, detected gaps/rejections, reference length, path deviation, drift/minute and optional battery/temperature deltas, plus aggregated repeat gates. **A passing run must maintain at least 10 observed pose updates/s, no inter-pose interval above 0.5s, and each stationary/rotation trial must last at least 60s.** Poorly sampled data fails rather than quietly reducing the tested duration. Paths/lat/lon are **not** copied. A `FAILED` exit code 1 identifies insufficient accuracy; `INSUFFICIENT_EVIDENCE` exit code 2 means missing repeated trials. Green synthetic CI tests **do not qualify the phone**.

**Proposed acceptance targets, not yet measured:** for at least three 10m straights, median absolute relative length error below 5%, p90 below 10%; for at least three stationary runs, median false distance below 0.5 m/min. All tracking gaps and outliers remain visible; never drop bad runs to make a percentile pass.

## Other non-automated gates

- Actual installed APK current SHA and Android/ARCore versions.
- Recorded camera target vs actual tracked pose frames (particularly 30 Hz while running), no duplicate timestamps, no wake-lock/permission failures.
- Battery energy, temperature/time series, memory, CPU and frame latency on device—not inferred from GitHub Actions host runs.
- Relative metric odometer length and direction compared to independent measured paths; loop closure and 6DoF ATE only if a real synchronized spatial ground-truth system exists.
- Camera hidden, heavy smoke, complete darkness and firefighter safety are explicitly outside established accuracy. Never use prototype navigation for rescue decisions.
