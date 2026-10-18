# VECTOR VIO — Modular Test Environment V2

Scope: deterministic suite orchestration atop the actual benchmark.py registry and the proven PR #7 Java PDR / SIMUL pipeline. This does NOT rewrite estimators, metrics or Android code.

## Architecture

1. tools/testenv/contracts.py — validate suite JSON and consult tools/campaigns/unified.py for allowed engine-dataset combinations, reference kind and claim level.
2. tools/testenv/runner.py — isolate each benchmark invocation, enforce timeout and truth-related gates, hash code and input, save evidence, avoid reusing unverifiable results.
3. tools/testenv/presentation.py — produce a **standalone offline HTML** index with all retained runs, PASS/FAILED/SKIPPED and JSON evidence links. All values are HTML-escaped.
4. tools/testenv/cli.py and root testenv.py — plan, run, render.
5. tools/testenv/suites/*.json — versioned, declarative campaigns, **not arbitrary scripts**.

Flow: suite JSON → capability check → real registered benchmark engine → evidence check → immutable run summary → HTML history. Truth labels are read by an existing estimator-independent scoring stage, never injected by this scheduler.

## Quick start (repository root, Python 3.11+)

    # Strict dry-run; no network, estimator or file downloads
    python testenv.py plan --suite tools/testenv/suites/smoke.json
    python testenv.py plan --suite tools/testenv/suites/real-public.json

    # Offline generated SYNTHETIC IMU, existing Python baseline executed
    python testenv.py run --suite tools/testenv/suites/smoke.json --out campaign_results/testenv

    # Exact cached evidence may be re-used, but only with identical code, source and gates
    python testenv.py run --suite tools/testenv/suites/smoke.json --out campaign_results/testenv --resume

    # Opt-in NETWORK: genuine SIMUL Java PDR and separately published VINS-Mono poses
    # Requires Java 17, Internet, and numpy==2.2.6 for trajectory evaluator.
    python testenv.py run --suite tools/testenv/suites/real-public.json --out campaign_results/real

    # Isolate just one expensive job
    python testenv.py run --suite tools/testenv/suites/real-public.json --only simul-java --out campaign_results/real

    # Regenerate offline dashboard after moving a history archive
    python testenv.py render --out campaign_results/testenv

Open campaign_results/testenv/index.html locally. Each test row shows engine, dataset, reference qualification, objective metrics, and links to complete report/case JSON. Reports with no score reference cannot pass unscored ATE gates.

## New suite JSON schema, version 1

    {
      "schema_version": 1,
      "id": "personal-tests",
      "description": "example only",
      "jobs": [
        {
          "id": "imu",
          "dataset": "canonical",
          "engine": "python-pdr",
          "inputs": {"input": "/data/imu.csv"},
          "params": {"stride_m": 0.70, "yaw_axis": "z"},
          "timeout_s": 600,
          "checks": [
            {"field": "actual_estimator_executed", "equals": true},
            {"field": "reference_used_to_score", "equals": false},
            {"field": "metrics.frames_processed", "min": 100}
          ]
        }
      ]
    }

Paths relative to the suite file are resolved against the suite directory, not the executing shell. Supported path keys: input, root, estimate, truth, heading_csv; param keys: stride_m, yaw_axis, known_distance, heading_source. Any other fields, arbitrary commands, implicit GT injection, unsupported dataset/engine pair, duplicate IDs, nonfinite gates, nonpositive timeout etc. are rejected. Existing unified.py defines dataset compatibility: testenv cannot pretend that Android ARCore can replay EuRoC.

## Reproducibility, privacy and speed

- Every invocation writes a new runs/<UTC>-<random>/summary.json and cases/<id>/{case.json,report.json}; root latest.json/index.html are regenerated atomically. One failed case does not conceal the others unless --fail-fast was explicitly selected.
- All input files are streaming SHA256 hashed, suite/code hashes recorded, Java/SIMUL original commits and source checksums retained in the underlying benchmark report. Raw dataset directory inputs cannot be resumed since the full directory content was not hashed. --resume checks the complete case fingerprint, a prior PASS, report hash and actual gates again.
- By default the heavy per-case engine/ directory is removed after each case. User-supplied source files are never deleted. Source and stderr logs are local; inspect before sharing. GitHub CI uploads JSON and HTML only, not original recordings. External Drive transfer is NOT automatic.
- A local atomic directory lock prevents two writers to the same result folder. After a crash, ensure no job is still running before removing .testenv.lock. The executor is sequential on purpose (avoid simultaneous large downloads, VIO memory pressure, and uncontrolled Java compiles).
- Synthetic fixture is marked SYNTHETIC and is not accuracy evidence. FAIL does not turn into PASS because an estimator returned a plausible number. Evaluation gates require the already documented reference type. Success in host tests does NOT imply the Android application is integrated, physically tested, or meter-accurate.

## Coverage and limitations

| Dataset/engine | Status in this test environment | Valid evidence |
| --- | --- | --- |
| Generated canonical IMU / Python PDR | Offline runnable | Deterministic engine regression only |
| SIMUL / pinned Android PdrEngine Java fallback | Real, public-download pipeline runnable | Step interval F1/count; PR #7: 9 real placements / 29,563 timestamps; expanded PR #9/#10: 12 placements |
| SIMUL / Python PDR | Available secondary baseline | Separate from the Java Android implementation |
| EuRoC external VINS-Mono published path | Real evaluator runnable | Trajectory metrics for previously published external results; estimator NOT run here |
| Raw EuRoC / TUM VI | Local dataset root required | Sensor indexing only, not camera-inertial estimation |
| OxIOD native CSV | Local file required | Inferred steps, NOT independently verified path meters |
| Phone export CSV | Local file required | CSV audit; surveyed length needed for independent distance error |
| RoNIN / RIDI / KITTI / Hilti and Android ARCore on EuRoC | No corresponding registered adapter | Cannot claim tested from this suite |
| Live Galaxy S21 VIO / ARCore | Device-only qualification remains | No proof from desktop suite alone |

Separate image+IMU smartphone research scripts/PR #12 are outside this tool's registered engine list; do not confuse them with the APK.

## Tests and integration

    python -m unittest discover -s tools/testenv/tests -p 'test_*.py' -v
    python testenv.py run --suite tools/testenv/suites/smoke.json --out /tmp/vector-testenv

The dedicated .github/workflows/testenv.yml runs unit contract tests and an actual offline Python engine smoke twice, verifying --resume and HTML history. On PR events, a separate bounded real-data job **also executes the exact V2 suite** against original SIMUL IMU (pinned Java PdrEngine) and published EuRoC VINS-Mono poses. This expensive job does not run on ordinary branch pushes. It uploads only small JSON/HTML reports; published VINS poses are external estimator outputs, never a claim of Android VIO execution.

Before merging, recheck AGENTS.md / docs/DEVELOPMENT_COORDINATION.md and the current feature/slam-explorer HEAD. This work changes no shared Android entrypoints, menus, manifests, Java engines, other workflows, or evaluator mathematics.
