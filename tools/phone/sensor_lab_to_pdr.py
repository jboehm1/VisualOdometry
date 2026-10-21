#!/usr/bin/env python3
"""Convert S21 Sensor Lab's *real device* CSV to existing Java PdrReplay input.

No compass-derived heading, no assumption phone-forward and no step labels/GT
are invented. Caller MUST supply a measured stride length; the output is
PdrEngine-only distance with chart heading explicitly UNKNOWN.
"""
from __future__ import annotations
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path

REQUIRED = {"sensor_type","sensor_timestamp_ns","x","y","z","accuracy"}
ACCEL = 1
PRESSURE = 6


class PhoneCaptureError(ValueError):
    pass


def convert(source: Path, dest: Path, *, stride_m: float) -> dict:
    if not math.isfinite(stride_m) or not 0.30 <= stride_m <= 2.00:
        raise PhoneCaptureError("Require an externally calibrated 0.30–2.00m stride, not a guessed truth")
    if not source.is_file() or source.stat().st_size > 45_000_000:
        raise PhoneCaptureError("Input missing or over 45MB")
    events = []
    source_samples = 0
    rejected = 0
    seen = set()
    with source.open("r", encoding="utf-8-sig", newline="") as file:
        reader = csv.DictReader(file)
        if not reader.fieldnames or not REQUIRED.issubset(reader.fieldnames):
            raise PhoneCaptureError("Invalid Sensor Lab CSV schema")
        for row in reader:
            source_samples += 1
            if source_samples > 180_000:
                raise PhoneCaptureError("Bounded capture limit exceeded")
            try:
                typ = int(row["sensor_type"])
                t = int(row["sensor_timestamp_ns"])
                values = [float(row[k]) for k in ("x", "y", "z") if row[k] != ""]
            except (TypeError, ValueError) as exc:
                raise PhoneCaptureError(f"Malformed raw sample at row {source_samples}") from exc
            if typ not in (ACCEL, PRESSURE):
                continue
            if t <= 0 or any(not math.isfinite(v) for v in values) or len(values) < (3 if typ == ACCEL else 1):
                rejected += 1
                continue
            if (typ,t) in seen:
                rejected += 1
                continue
            seen.add((typ,t))
            events.append((t, "ACCEL" if typ == ACCEL else "PRESSURE",
                           values[0], values[1] if len(values)>1 else 0.,
                           values[2] if len(values)>2 else 0.))
    if not any(e[1] == "ACCEL" for e in events):
        raise PhoneCaptureError("No valid raw accelerometer observations")
    events.sort(key=lambda e: (e[0], 0 if e[1] == "ACCEL" else 1))
    first = events[0][0]
    dest.parent.mkdir(parents=True, exist_ok=True)
    with dest.open("w", encoding="utf-8", newline="") as out:
        writer = csv.writer(out, lineterminator="\n")
        writer.writerow(("t_ns","type","x","y","z"))
        writer.writerow((first,"START",0,0,0))
        writer.writerow((first,"STEP_LENGTH",f"{stride_m:.8f}",0,0))
        for event in events:
            writer.writerow(event)
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    manifest = {
        "schema": 1,
        "sensor_source": "S21_SENSOR_LAB_REAL_ANDROID_RAW_INPUT_UNVERIFIED",
        "sensor_csv_sha256": digest,
        "estimator_target": "Java PdrEngine via tools/pdr/PdrReplay.java",
        "input_clock": "SensorEvent.timestamp elapsedRealtimeNanos",
        "input_samples_total": source_samples,
        "input_samples_used": len(events),
        "input_samples_rejected": rejected,
        "step_length_m": stride_m,
        "step_length_source": "user supplied; must be externally calibrated",
        "heading_provenance": "NONE: no phone-to-body transformation or magnetic heading inferred",
        "ground_truth": None,
        "model_id": "PDR_JAVA_CLASSIC_ONLY",
        "learned_imu_inference": False,
        "position_accuracy_claim": False,
        "warnings": ["No automatically inferred global heading.",
                     "Raw acceleration can indicate steps; distance depends on stride calibration.",
                     "Recorded phone orientation must be assessed separately.",
                     "Does not validate performance in dense smoke or on bicycle."],
    }
    dest.with_suffix(".provenance.json").write_text(json.dumps(manifest, indent=2)+"\n",encoding="utf-8")
    return manifest


def main() -> None:
    cli=argparse.ArgumentParser(description=__doc__)
    cli.add_argument("sensor_samples",type=Path)
    cli.add_argument("--out",type=Path,required=True)
    cli.add_argument("--stride-m",type=float,required=True)
    a=cli.parse_args()
    print(json.dumps(convert(a.sensor_samples,a.out,stride_m=a.stride_m),indent=2))


if __name__=="__main__":
    main()
