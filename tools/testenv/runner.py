"""Reproducible, sequential benchmark campaign executor.

The actual estimator is always the already-registered unified benchmark runner.
No command or Python module specified by a suite is ever executed directly.
"""
from __future__ import annotations

import datetime as dt
import hashlib
import json
import os
import secrets
import shutil
import signal
import subprocess
import sys
import tempfile
import time
from pathlib import Path

from tools.testenv.contracts import digest, job_inputs, load_suite
from tools.testenv import presentation

ROOT = Path(__file__).resolve().parents[2]
BENCH = ROOT / "benchmark.py"
PROVENANCE_CODE = tuple(ROOT / name for name in (
    "benchmark.py", "tools/campaigns/unified.py", "tools/campaigns/run_dataset.py",
    "tools/campaigns/imu_pdr.py", "tools/campaigns/oxiod.py",
    "tools/campaigns/simul_real.py", "tools/campaigns/chat_real_proof.py",
    "tools/campaigns/android_pdr_bridge.py", "tools/campaigns/public_trajectory.py",
    "tools/campaigns/java/PdrStepTrace.java",
    "tools/campaigns/make_inertial_fixture.py", "tools/benchmark/bench.py",
    "tools/testenv/contracts.py", "tools/testenv/runner.py",
    "tools/testenv/presentation.py", "tools/testenv/cli.py",
    "tools/smoke_rescue/upo_real.py"))


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def write_json(path: Path, value: dict) -> None:
    temp = path.with_name(path.name + ".tmp")
    temp.write_text(json.dumps(value, sort_keys=True, indent=2, allow_nan=False) + "\n",
                    encoding="utf-8")
    temp.replace(path)


def code_identity() -> dict:
    code = {str(p.relative_to(ROOT)): sha256(p) for p in PROVENANCE_CODE}
    git = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, capture_output=True,
                         text=True, check=False)
    return {"git_sha": git.stdout.strip() if git.returncode == 0 else None,
            "source_sha256": code}


def input_identity(files: dict[str, Path]) -> tuple[dict, bool]:
    """Content hashes for files; directory input disables cross-run reuse."""
    result = {}
    reusable = True
    for name, path in sorted(files.items()):
        if name == "root":
            if not path.is_dir():
                raise FileNotFoundError(f"Missing directory input {name}: {path}")
            result[name] = {"path": str(path), "directory_unhashed": True}
            reusable = False
        else:
            if not path.is_file():
                raise FileNotFoundError(f"Missing file input {name}: {path}")
            result[name] = {"path": str(path), "bytes": path.stat().st_size,
                            "sha256": sha256(path)}
    return result, reusable


def find(report: dict, dotted: str):
    value = report
    for key in dotted.split("."):
        if not isinstance(value, dict) or key not in value:
            raise ValueError(f"Missing check field {dotted}")
        value = value[key]
    return value


def verify_report(report: dict, job: dict) -> None:
    if report.get("status") != "PASS":
        raise ValueError("Underlying benchmark did not report PASS")
    if report.get("dataset_id") != job["dataset"]:
        raise ValueError("Benchmark returned the wrong dataset")
    from tools.campaigns.unified import ALIASES
    if report.get("requested_engine") != ALIASES.get(job["engine"], job["engine"]):
        raise ValueError("Benchmark returned the wrong engine")
    if not isinstance(report.get("actual_estimator_executed"), bool):
        raise ValueError("Estimator execution evidence must be explicit")
    if not isinstance(report.get("reference_used_to_score"), bool):
        raise ValueError("Reference use evidence must be explicit")
    if report.get("metric_distance_accuracy_proven") and not report["reference_used_to_score"]:
        raise ValueError("Cannot claim metric distance accuracy without independent reference")
    if not isinstance(report.get("metrics"), dict):
        raise ValueError("Malformed metrics object")
    for check in job.get("checks", []):
        value = find(report, check["field"])
        if "equals" in check and (type(value) != type(check["equals"]) or value != check["equals"]):
            raise ValueError(f"Gate {check['field']} != {check['equals']!r}: {value!r}")
        if "equals" not in check:
            if isinstance(value, bool) or not isinstance(value, (int, float)) or not (
                float("-inf") < value < float("inf")):
                raise ValueError(f"Gate {check['field']} needs a finite numeric metric")
            if "min" in check and value < check["min"]:
                raise ValueError(f"Gate {check['field']} below {check['min']}: {value}")
            if "max" in check and value > check["max"]:
                raise ValueError(f"Gate {check['field']} above {check['max']}: {value}")
    # Do not let scores from unsigned/no-reference tests masquerade as accuracy.
    if not report["reference_used_to_score"]:
        for name in ("ate_rmse_m", "rpe_translation_rmse_m", "relative_path_error_pct"):
            if name in report["metrics"]:
                raise ValueError(f"Unexpected independent-truth metric without reference: {name}")


def _subprocess(command: list[str], timeout_s: int, cwd: Path) -> tuple[int, str, str]:
    """Bound peak RAM even if a research engine prints millions of log lines."""
    def tail(stream):
        stream.seek(0, os.SEEK_END)
        stream.seek(max(0, stream.tell() - 16000))
        return stream.read().decode("utf-8", errors="replace")

    # Temporary disk-backed streams avoid subprocess.communicate() accumulating
    # arbitrarily large full output in RAM. We retain only a short local tail.
    with tempfile.TemporaryFile() as stdout, tempfile.TemporaryFile() as stderr:
        options = {"cwd": cwd, "stdout": stdout, "stderr": stderr}
        if os.name == "posix":
            options["start_new_session"] = True
        process = subprocess.Popen(command, **options)
        try:
            process.wait(timeout=timeout_s)
        except subprocess.TimeoutExpired:
            if os.name == "posix":
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
            elif os.name == "nt":
                subprocess.run(["taskkill", "/T", "/F", "/PID", str(process.pid)],
                               capture_output=True, check=False)
            else:
                process.kill()
            process.wait()
            raise TimeoutError(f"Benchmark exceeded {timeout_s}s (process tree terminated)")
        return process.returncode, tail(stdout), tail(stderr)


def _fingerprint(job: dict, inputs: dict, code: dict) -> str:
    # Explicitly includes engine runner sources plus SHA of each external file.
    # This fingerprint is not a claim that downloaded upstream data is immutable:
    # source-level provenance is separately present in the underlying report.
    return digest({"job": job, "input": inputs, "code": code})


def _reuse(root: Path, fingerprint: str, job: dict) -> tuple[dict, Path] | None:
    for path in sorted((root / "runs").glob("*/cases/" + job["id"] + "/case.json"),
                       reverse=True):
        try:
            previous = json.loads(path.read_text(encoding="utf-8"))
            artifact = path.parent / "report.json"
            if (previous["status"] == "PASS" and
                    previous["fingerprint"] == fingerprint and
                    previous["report_sha256"] == sha256(artifact)):
                report = json.loads(artifact.read_text(encoding="utf-8"))
                verify_report(report, job)
                return report, path
        except (KeyError, ValueError, OSError, json.JSONDecodeError):
            continue
    return None


def _case(job: dict, suite_path: Path, case_dir: Path, root: Path,
          resume: bool, identity: dict) -> dict:
    case_dir.mkdir(parents=True, exist_ok=False)
    start = time.monotonic()
    result = {"id": job["id"], "dataset": job["dataset"], "engine": job["engine"],
              "status": "FAILED", "checks": job.get("checks", []),
              "started_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
              "report_sha256": None, "fingerprint": None}
    try:
        inputs = job_inputs(job, suite_path)
        source, reusable = input_identity(inputs)
        result["inputs"] = source
        result["code_identity"] = identity
        fp = _fingerprint(job, source, identity)
        result["fingerprint"] = fp
        if resume and reusable:
            old = _reuse(root, fp, job)
            if old:
                report, old_path = old
                shutil.copy2(old_path.parent / "report.json", case_dir / "report.json")
                result.update(status="PASS", reused_from=str(old_path.parent.relative_to(root)),
                              metrics=report.get("metrics", {}), report_sha256=sha256(case_dir / "report.json"),
                              accuracy_claim_level=report.get("accuracy_claim_level"),
                              reference_kind=report.get("reference_kind"),
                              actual_estimator_executed=report.get("actual_estimator_executed"),
                              reference_used_to_score=report.get("reference_used_to_score"))
                return result
        case_output = case_dir / "engine"
        case_output.mkdir()
        if job.get("fixture") == "canonical_imu":
            gen = [sys.executable, str(ROOT / "tools/campaigns/make_inertial_fixture.py"),
                   "--out", str(case_dir / "fixture")]
            ret, stdout, stderr = _subprocess(gen, 30, ROOT)
            if ret:
                raise RuntimeError("Fixture generator failed: " + stderr[-500:])
            inputs["input"] = case_dir / "fixture/imu.csv"
            result["fixture_sha256"] = sha256(inputs["input"])
            result["input_kind"] = "SYNTHETIC"
        args = [sys.executable, str(BENCH), "run", "--dataset", job["dataset"],
                "--engine", job["engine"], "--out", str(case_output),
                "--timeout-s", str(job.get("timeout_s", 900))]
        for key, path in sorted(inputs.items()):
            args += ["--" + key.replace("_", "-"), str(path)]
        for key, value in sorted(job.get("params", {}).items()):
            args += ["--" + key.replace("_", "-"), str(value)]
        rc, stdout, stderr = _subprocess(args, job.get("timeout_s", 900), ROOT)
        (case_dir / "stdout.log").write_text(stdout, encoding="utf-8")
        (case_dir / "stderr.log").write_text(stderr, encoding="utf-8")
        if rc:
            error = ""
            original = case_output / "report.json"
            if original.is_file():
                try:
                    error = json.loads(original.read_text(encoding="utf-8")).get("error", "")
                except (OSError, ValueError):
                    pass
            raise RuntimeError(f"Benchmark exit {rc}: {error or stderr[-600:]}")
        report = json.loads((case_output / "report.json").read_text(encoding="utf-8"))
        verify_report(report, job)
        shutil.copy2(case_output / "report.json", case_dir / "report.json")
        result.update(status="PASS", metrics=report["metrics"],
                      report_sha256=sha256(case_dir / "report.json"),
                      accuracy_claim_level=report.get("accuracy_claim_level"),
                      reference_kind=report.get("reference_kind"),
                      actual_estimator_executed=report["actual_estimator_executed"],
                      reference_used_to_score=report["reference_used_to_score"])
    except (OSError, ValueError, RuntimeError, TimeoutError, subprocess.SubprocessError) as e:
        result["error"] = str(e)
    finally:
        # We keep tiny source/result reports, never downloaded raw datasets by default.
        if (case_dir / "engine").exists():
            shutil.rmtree(case_dir / "engine")
        result["duration_s"] = round(time.monotonic() - start, 3)
        write_json(case_dir / "case.json", result)
    return result


def execute(suite_path: Path, out: Path, only: set[str] | None = None,
            resume: bool = False, fail_fast: bool = False) -> dict:
    suite_path = suite_path.resolve(strict=True)
    suite = load_suite(suite_path)
    out = out.resolve()
    jobs = suite["jobs"]
    names = {x["id"] for x in jobs}
    if only and not only <= names:
        raise ValueError(f"Unknown --only case(s): {sorted(only - names)}")
    if only:
        jobs = [x for x in jobs if x["id"] in only]
    out.mkdir(parents=True, exist_ok=True)
    lock = out / ".testenv.lock"
    try:
        lock.mkdir()
    except FileExistsError as e:
        raise RuntimeError(f"Another run appears active (lock: {lock})") from e
    try:
        run_id = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + secrets.token_hex(4)
        run_dir = out / "runs" / run_id
        run_dir.mkdir(parents=True, exist_ok=False)
        identity = code_identity()
        summary = {"schema_version": 1, "suite": suite["id"], "run_id": run_id,
                   "suite_sha256": sha256(suite_path),
                   "code_identity": identity, "status": "RUNNING", "cases": [],
                   "created_utc": dt.datetime.now(dt.timezone.utc).isoformat()}
        write_json(run_dir / "summary.json", summary)
        stopped = False
        for job in jobs:
            if stopped:
                case = {"id": job["id"], "dataset": job["dataset"], "engine": job["engine"],
                        "status": "SKIPPED", "reason": "fail_fast"}
                folder = run_dir / "cases" / job["id"]
                folder.mkdir(parents=True)
                write_json(folder / "case.json", case)
            else:
                case = _case(job, suite_path, run_dir / "cases" / job["id"],
                             out, resume, identity)
                if case["status"] == "FAILED" and fail_fast:
                    stopped = True
            summary["cases"].append(case)
            write_json(run_dir / "summary.json", summary)
        summary["counts"] = {status: sum(x["status"] == status for x in summary["cases"])
                             for status in ("PASS", "FAILED", "SKIPPED")}
        summary["status"] = "PASS" if summary["counts"]["FAILED"] == 0 and summary["counts"]["SKIPPED"] == 0 else "FAILED"
        summary["finished_utc"] = dt.datetime.now(dt.timezone.utc).isoformat()
        write_json(run_dir / "summary.json", summary)
        write_json(out / "latest.json", summary)
        presentation.render_dashboard(out)
        return summary
    finally:
        lock.rmdir()
