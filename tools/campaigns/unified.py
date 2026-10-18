#!/usr/bin/env python3
"""One honest entry point for *existing*, genuinely supported odometry tests.

This orchestration does NOT create universal raw data adapters or make ARCore
run on a Linux CI host. All engines are explicit, and every report states
exactly what was measured, with what reference and which input provenance.

Usage:
  python tools/campaigns/unified.py catalog
  python tools/campaigns/unified.py plan --dataset simul --engine android-pdr
  python tools/campaigns/unified.py run --dataset simul --engine android-pdr --out /tmp/simul
  python tools/campaigns/unified.py run --dataset euroc_external --engine external-vins --out /tmp/vins
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
BENCH_CATALOG = ROOT / "tools/benchmark/catalog.json"

# A runnable combination is NOT proof of independent odometry accuracy.
# Reference classes and engine identities are part of the public API.
RUNNERS = {
    "simul": {
        "engines": ("android-pdr", "python-pdr"),
        "input": "pinned_public",
        "capability": "step_count_vs_annotated_intervals",
        "reference": "foot_imu_step_intervals",
        "accuracy": "step_count_only",
    },
    "euroc_external": {
        "engines": ("external-vins",),
        "input": "pinned_public",
        "capability": "published_external_trajectory_metrics",
        "reference": "euroc_groundtruth_as_bundled_with_upstream",
        "accuracy": "external_estimator_only",
    },
    "euroc": {
        "engines": ("sensor-index", "external-trajectory"),
        "input": "local_dataset_root",
        "capability": "raw_sensor_index_or_external_trajectory_score",
        "reference": "original_euroc_reference_if_estimate_provided",
        "accuracy": "no_app_estimation",
    },
    "tumvi": {
        "engines": ("sensor-index",),
        "input": "local_dataset_root",
        "capability": "raw_sensor_index",
        "reference": "none_scored",
        "accuracy": "no_estimator_executed",
    },
    "oxiod": {
        "engines": ("android-pdr", "python-pdr"),
        "input": "local_imu_csv",
        "capability": "real_inertial_estimator_no_metric_truth_score",
        "reference": "none_scored",
        "accuracy": "uncalibrated_stride_and_heading",
    },
    "canonical": {
        "engines": ("android-pdr", "python-pdr"),
        "input": "local_canonical_imu_csv",
        "capability": "inertial_estimator_no_metric_truth_score",
        "reference": "none_scored",
        "accuracy": "uncalibrated_stride_and_heading",
    },
    "phone_csv": {
        "engines": ("csv-audit",),
        "input": "local_phone_export",
        "capability": "existing_phone_trajectory_audit",
        "reference": "independent_distance_only_if_provided",
        "accuracy": "distance_if_surveyed_no_full_6dof_truth",
    },
}

ALIASES = {"java-pdr": "android-pdr"}
REAL_DATA_EXECUTED = {"simul": True, "euroc_external": True}
LIMITATIONS = {
    "simul": [
        "Actual pinned Java PdrEngine is compiled from a parallel branch, not the current integrated Android APK",
        "Python baseline is also executed as a secondary engine by the existing SIMUL runner",
        "Independent annotated step intervals do not provide metric distance/ATE/RPE",
        "The Java fallback is accelerometer-based; not the Android hardware STEP_DETECTOR",
    ],
    "euroc_external": [
        "Published VINS-Mono estimates predate this run: the VIO estimator is NOT executed",
        "No raw image+IMU sequence is replayed through VECTOR or OpenVINS",
        "Published pose-frame extrinsics require independent audit",
    ],
    "euroc": [
        "Sensor indexing is not estimator execution",
        "External poses are not the VECTOR Android or ARCore estimator",
        "Pose frame and time alignment require verification before interpreting score",
    ],
    "tumvi": [
        "Sensor indexing only, not a VIO reconstruction",
        "TUM VI motion-capture truth is not available along the complete trajectory",
    ],
    "oxiod": [
        "Source-specific time units and sensor axes must be audited against the exact OxIOD version",
        "No trustworthy metric displacement score without calibrated, independently aligned truth",
        "Java engine estimates step distance with a fixed prior; no raw gyro heading integration",
    ],
    "canonical": [
        "Source and coordinate frame provenance must be declared outside the canonical CSV",
        "No metric accuracy can be established without independent time-aligned position reference",
        "Java engine counts steps without using raw gyro as heading",
    ],
    "phone_csv": [
        "Summarizes a CSV already exported by the phone; cannot run ARCore on this cloud host",
        "A known route length allows distance error, not absolute 6DoF ATE",
    ],
}


def write_json(path: Path, value: dict | list) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True, allow_nan=False) + "\n", encoding="utf-8")


def hash_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def available_catalog() -> list[dict]:
    existing = json.loads(BENCH_CATALOG.read_text(encoding="utf-8"))["entries"]
    names = {x["id"]: x for x in existing}
    names["simul"] = {
        "id": "simul", "name": "SIMUL six synchronized body IMUs",
        "scenario": "real pedestrian gait and step detection, not metric path truth",
        "url": "https://github.com/simpleLoc/SIMUL-dataset",
        "truth": "hand-corrected step intervals from foot sensors",
        "license": "CC BY-SA 4.0",
    }
    names["canonical"] = {
        "id": "canonical", "name": "Canonical timestamped IMU events",
        "scenario": "user-provided accel/gyro/baro recordings",
        "truth": "none unless separately measured",
    }
    names["phone_csv"] = {
        "id": "phone_csv", "name": "VECTOR on-phone exported trajectory",
        "scenario": "audit an actual CSV from VECTOR app",
        "truth": "distance only when measured independently",
    }
    for id, item in names.items():
        spec = RUNNERS.get(id)
        item["run_status"] = (
            "verified_real_public_pipeline" if id in REAL_DATA_EXECUTED else
            "adapter_runnable_input_required" if spec else
            "not_implemented_in_unified_runner"
        )
        item["supported_engines"] = list(spec["engines"]) if spec else []
        item["reference_capability"] = spec["reference"] if spec else "not_scored"
    return sorted(names.values(), key=lambda x: (x.get("rank", 999), x["id"]))


def plan(dataset: str, engine: str) -> dict:
    engine = ALIASES.get(engine, engine)
    spec = RUNNERS.get(dataset)
    if spec is None:
        raise ValueError(
            f"Dataset {dataset!r} is catalogued but not runnable. "
            "No adapter/engine was invented: use 'catalog' for actual coverage."
        )
    if engine not in spec["engines"]:
        raise ValueError(
            f"Engine {engine!r} incompatible with {dataset!r}; "
            f"supported: {', '.join(spec['engines'])}. "
            "Never claim Android ARCore can consume EuRoC or SIMUL recordings."
        )
    return {
        "dataset": dataset, "requested_engine": engine,
        "valid": True, "input_policy": spec["input"],
        "capability": spec["capability"], "reference_kind": spec["reference"],
        "accuracy_claim_level": spec["accuracy"],
        "requires": (
            ["--root DIRECTORY with original image+IMU records"] if spec["input"] == "local_dataset_root" else
            ["--input FILE from a real recording"] if spec["input"].startswith("local_") else
            []),
        "limitations": LIMITATIONS[dataset],
    }


def _invoke(argv: list[str], folder: Path, *, timeout_s: int) -> None:
    proc = subprocess.run(
        argv, cwd=ROOT, capture_output=True, text=True, timeout=timeout_s, check=False
    )
    # Logs are useful for diagnosis but never include raw dataset bytes.
    (folder / "engine_stdout.log").write_text(proc.stdout[-100_000:], encoding="utf-8")
    (folder / "engine_stderr.log").write_text(proc.stderr[-100_000:], encoding="utf-8")
    if proc.returncode:
        raise RuntimeError(
            f"Estimator/adapter exited {proc.returncode}; inspect engine_stderr.log. "
            f"Last error: {proc.stderr[-400:]}"
        )


def _require_file(value: Path | None, label: str) -> Path:
    if value is None:
        raise ValueError(f"Missing required {label}")
    f = value.resolve()
    if not f.is_file():
        raise FileNotFoundError(f"Missing input {label}: {f}")
    return f


def _require_dir(value: Path | None) -> Path:
    if value is None:
        raise ValueError("Missing --root for raw camera+IMU sequence")
    p = value.resolve()
    if not p.is_dir():
        raise FileNotFoundError(f"Raw dataset root not found: {p}")
    return p


def _numbers(report: dict, fields: tuple[str, ...]) -> dict:
    return {k: report[k] for k in fields if k in report}


def _run_simul(base: dict, output: Path, engine: str, timeout_s: int) -> None:
    _invoke([sys.executable, str(HERE / "chat_real_proof.py"),
             "--out", str(output / "public_data")], output, timeout_s=timeout_s)
    full = json.loads((output / "public_data" / "real_step_report.json").read_text())
    receipt = json.loads((output / "public_data" / "chat_proof_receipt.json").read_text())
    positions = [p for seq in full["sequences"] for p in seq["positions"]]
    is_java = engine == "android-pdr"
    kind = "java" if is_java else "python"
    errors = [abs(p[f"{kind}_step_count"]["step_count_error_percent"]) for p in positions]
    steps = [p[f"{kind}_step_count"]["count"] for p in positions]
    metrics = {
        "test_cases": len(positions), "subjects": len(full["sequences"]),
        "original_sensor_timestamps": receipt["real_sample_timestamps"],
        "absolute_step_count_error_mean_pct": sum(errors) / len(errors),
        "steps_predicted_total_across_placements": sum(steps),
        "annotated_steps_total_across_placements": sum(seq["reference_steps"] * len(seq["positions"]) for seq in full["sequences"]),
    }
    if is_java:
        metrics["step_interval_f1_macro"] = sum(p["java_timed_step_score"]["f1"] for p in positions) / len(positions)
    base.update({
        "status": "PASS", "measured_engine": (
            "actual_pinned_Java_PdrEngine" if is_java else
            "Python_fixed_stride_peak_baseline"),
        "additional_engine_executed": ("python-pdr" if is_java else "android-pdr"),
        "actual_estimator_executed": True,
        "reference_used_to_score": True,
        "reference_kind": "annotated_step_intervals",
        "metrics": metrics,
        "provenance": {
            "original_IMU_dataset_upstream_commit": full["dataset_upstream_commit"],
            "actual_Java_PdrEngine_commit": full["java_upstream_commit"],
            "source_manifests": [
                {"dataset_sha256": s["data_source"]["sha256"],
                 "annotation_sha256": s["annotation_source"]["sha256"],
                 "sequence": s["sequence"]} for s in full["sequences"]
            ],
        },
        "raw_outputs_directory": "public_data (ephemeral; do not upload)",
    })


def _run_external_vins(base: dict, output: Path, timeout_s: int) -> None:
    _invoke([sys.executable, str(HERE / "public_trajectory.py"),
             "--out", str(output / "published")], output, timeout_s=timeout_s)
    manifest = json.loads((output / "published" / "source_manifest.json").read_text())
    metrics = json.loads((output / "published" / "metrics.json").read_text())
    base.update({
        "status": "PASS", "measured_engine": "published_VINS_Mono_trajectory_only",
        "actual_estimator_executed": False,
        "evaluator_executed": True,
        "reference_used_to_score": True,
        "reference_kind": "published_external_euroc_reference",
        "metrics": _numbers(metrics, ("pairs", "ate_rmse_m", "tracking_coverage_time_fraction",
                                       "rpe_translation_rmse_m", "rpe_rotation_rmse_deg",
                                       "path_distance_error_pct")),
        "provenance": {
            "external_repository_commit": manifest["upstream_commit"],
            "downloaded_files": manifest["downloaded_files"],
        },
        "raw_outputs_directory": "published (public trajectories only)",
    })


def _run_raw_index(base: dict, output: Path, dataset: str,
                   engine: str, args: argparse.Namespace) -> None:
    root = _require_dir(args.root)
    if engine == "external-trajectory" and dataset != "euroc":
        raise ValueError("Only the EuRoC pose format is configured for external scoring")
    command = [sys.executable, str(HERE / "run_dataset.py"),
               "--campaign", "A", "--kind", dataset,
               "--root", str(root), "--out", str(output / "native")]
    if engine == "external-trajectory":
        command += ["--estimate", str(_require_file(args.estimate, "--estimate"))]
        if args.truth:
            command += ["--truth", str(_require_file(args.truth, "--truth"))]
    _invoke(command, output, timeout_s=args.timeout_s)
    native = json.loads((output / "native" / "status.json").read_text())
    if native["status"] == "FAILED":
        raise ValueError(native.get("error", "Raw-index preparation failed"))
    base.update({
        "status": "PASS",
        "measured_engine": "none_raw_sensor_index" if engine == "sensor-index" else "external_user_supplied_trajectory",
        "actual_estimator_executed": False,
        "reference_used_to_score": bool(native.get("ground_truth_scored")),
        "reference_kind": "EuRoC_original_pose" if native.get("ground_truth_scored") else "none_scored",
        "metrics": _numbers(native, ("sensor_frame_count", "sensor_imu_count")),
        "provenance": {"root": str(root)},
    })
    if native.get("ground_truth_scored"):
        base["metrics"].update(json.loads((output / "native" / "metrics.json").read_text()))


def _run_inertial(base: dict, output: Path, dataset: str, engine: str,
                  args: argparse.Namespace) -> None:
    source = _require_file(args.input, "--input")
    canonical = source
    if args.truth is not None:
        raise ValueError("This runner does NOT score OxIOD/canonical truth until frame, clock and reference quality are audited")
    if dataset == "oxiod":
        _invoke([sys.executable, str(HERE / "oxiod.py"),
                 "--imu", str(source), "--out", str(output / "prepared")],
                output, timeout_s=args.timeout_s)
        canonical = output / "prepared" / "imu.csv"
    if args.stride_m <= 0 or not math.isfinite(args.stride_m):
        raise ValueError("--stride-m must be finite and positive")
    if engine == "android-pdr":
        # Compile the *real* pinned Java classes from the parallel Android PDR
        # workstream, never copy a Python implementation into a Java result.
        sys.path.insert(0, str(HERE))
        import simul_real
        java_path, sources = simul_real.compile_java(output)
        from android_pdr_bridge import convert
        import csv
        replay = output / "java_input.csv"
        bridge = convert(canonical, replay, args.heading_csv, args.heading_source)
        if not 0.35 <= args.stride_m <= 1.8:
            raise ValueError("Java PdrEngine stride must be within [0.35, 1.8] m")
        # Inject the legitimate STEP_LENGTH configuration event, which the
        # actual Java PdrReplay supports, BEFORE any recorded sensor event.
        # Keep the converter's original output immutable/hash-verifiable.
        configured = output / "java_configured_events.csv"
        with replay.open(newline="", encoding="utf-8") as src, configured.open(
            "w", newline="", encoding="utf-8") as dst:
            reader = csv.reader(src)
            writer = csv.writer(dst)
            writer.writerow(next(reader))
            writer.writerow((1, "STEP_LENGTH", args.stride_m, 0, 0))
            writer.writerows(reader)
        _invoke(
            ["java", "-cp", str(java_path),
             "dev.jboehm.vectorvio.pdr.PdrReplay",
             str(configured), str(output / "java_path.csv")],
            output, timeout_s=args.timeout_s,
        )
        measured = json.loads((output / "engine_stdout.log").read_text().strip().splitlines()[-1])
        if measured.get("rejected_rows"):
            raise ValueError("Java PdrReplay rejected sensor records")
        base.update({
            "status": "PASS",
            "measured_engine": "actual_pinned_Java_PdrEngine",
            "actual_estimator_executed": True,
            "reference_used_to_score": False,
            "metrics": _numbers(measured, ("steps", "distance_m", "rows",
                                           "east_m", "north_m", "steps_without_heading")),
            "provenance": {
                "sensor_input_sha256": hash_file(source),
                "Java_PdrEngine_commit": simul_real.JAVA_SHA,
                "Java_source_files": sources,
                "configured_stride_m": args.stride_m,
                "configured_replay_sha256": hash_file(configured),
                "bridge_manifest": bridge,
            },
        })
    else:
        if args.heading_csv is not None or args.heading_source is not None:
            raise ValueError("Python baseline accepts --yaw-axis and --stride-m, not a measured heading CSV")
        _invoke([sys.executable, str(HERE / "imu_pdr.py"),
                 "--imu", str(canonical), "--out", str(output / "python"),
                 "--stride-m", str(args.stride_m), "--yaw-axis", args.yaw_axis],
                output, timeout_s=args.timeout_s)
        result = json.loads((output / "python" / "report.json").read_text())
        base.update({
            "status": "PASS", "measured_engine": "Python_fixed_stride_peak_baseline",
            "actual_estimator_executed": True, "reference_used_to_score": False,
            "metrics": _numbers(result, ("steps", "path_m", "net_displacement_m",
                                        "end_heading_deg", "frames_processed")),
            "provenance": {"sensor_input_sha256": hash_file(source),
                           "canonical_IMU_sha256": hash_file(canonical)},
        })
    base["reference_kind"] = "none_scored"
    base["metric_distance_accuracy_proven"] = False
    base["metrics_are_estimates_not_independent_errors"] = True


def _run_phone(base: dict, output: Path, args: argparse.Namespace) -> None:
    source = _require_file(args.input, "--input")
    command = [sys.executable, str(HERE / "run_dataset.py"),
               "--campaign", "C", "--kind", "phone_csv",
               "--input", str(source), "--out", str(output / "phone")]
    if args.known_distance is not None:
        if not math.isfinite(args.known_distance) or args.known_distance <= 0:
            raise ValueError("--known-distance must be a positive finite surveyed distance")
        command += ["--known-distance", str(args.known_distance)]
    _invoke(command, output, timeout_s=args.timeout_s)
    native = json.loads((output / "phone" / "phone_report.json").read_text())
    base.update({
        "status": "PASS", "measured_engine": "none_previously_recorded_android_csv",
        "actual_estimator_executed": False, "evaluator_executed": True,
        "reference_used_to_score": args.known_distance is not None,
        "reference_kind": "independent_route_length" if args.known_distance is not None else "none",
        "metrics": native,
        "provenance": {"phone_export_sha256": hash_file(source)},
    })


def run(args: argparse.Namespace) -> dict:
    engine = ALIASES.get(args.engine, args.engine)
    output = args.out.resolve()
    output.mkdir(parents=True, exist_ok=True)
    status_file = output / "status.json"
    result_file = output / "report.json"
    base = {
        "schema_version": 1, "status": "RUNNING",
        "dataset_id": args.dataset, "requested_engine": engine,
        "actual_estimator_executed": False,
        "reference_used_to_score": False,
        "metric_distance_accuracy_proven": False,
        "metrics": {}, "provenance": {},
    }
    write_json(status_file, base)
    try:
        capability = plan(args.dataset, engine)
        base["capability"] = capability["capability"]
        base["accuracy_claim_level"] = capability["accuracy_claim_level"]
        base["limitations"] = capability["limitations"]
        if args.dataset == "simul":
            if args.input or args.truth or args.root or args.estimate:
                raise ValueError("SIMUL uses a pinned public source; no external truth/input override")
            _run_simul(base, output, engine, args.timeout_s)
        elif args.dataset == "euroc_external":
            if args.input or args.truth or args.root or args.estimate:
                raise ValueError("External EuRoC example uses a pinned public pair")
            _run_external_vins(base, output, args.timeout_s)
        elif args.dataset in ("euroc", "tumvi"):
            _run_raw_index(base, output, args.dataset, engine, args)
        elif args.dataset in ("oxiod", "canonical"):
            _run_inertial(base, output, args.dataset, engine, args)
        elif args.dataset == "phone_csv":
            _run_phone(base, output, args)
        else:
            raise AssertionError("Unsupported registry entry")
    except Exception as exc:
        base["status"] = "FAILED"
        base["error"] = str(exc)
        write_json(status_file, base)
        write_json(result_file, base)
        raise
    write_json(status_file, base)
    write_json(result_file, base)
    return base


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("catalog", help="List both runnable and NOT-YET-runnable datasets")
    for action in ("plan", "run"):
        p = sub.add_parser(action, help=f"{action.capitalize()} a validated dataset/engine combination")
        p.add_argument("--dataset", required=True)
        p.add_argument("--engine", required=True)
        if action == "run":
            p.add_argument("--out", type=Path, required=True)
            p.add_argument("--input", type=Path)
            p.add_argument("--root", type=Path)
            p.add_argument("--estimate", type=Path)
            p.add_argument("--truth", type=Path)
            p.add_argument("--heading-csv", type=Path)
            p.add_argument("--heading-source")
            p.add_argument("--known-distance", type=float)
            p.add_argument("--stride-m", type=float, default=0.70)
            p.add_argument("--yaw-axis", choices=["x", "y", "z"], default="z")
            p.add_argument("--timeout-s", type=int, default=1800)
    return parser


def main() -> None:
    args = build_parser().parse_args()
    try:
        if args.command == "catalog":
            result = available_catalog()
        elif args.command == "plan":
            result = plan(args.dataset, args.engine)
        else:
            result = run(args)
        print(json.dumps(result, indent=2, sort_keys=True, allow_nan=False))
    except (ValueError, RuntimeError, OSError, subprocess.TimeoutExpired) as exc:
        print(f"BENCHMARK_FAILED: {exc}", file=sys.stderr)
        raise SystemExit(2)


if __name__ == "__main__":
    main()
