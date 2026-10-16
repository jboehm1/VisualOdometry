#!/usr/bin/env python3
"""Summarize and visualize an exported VECTOR VIO CSV session.

Usage:
    python -m pip install matplotlib
    python tools/plot_session.py recording.csv --out plots/ --known-distance 10

No claim of ground truth unless an independently measured distance is supplied.
The original camera world coordinate system may reset after tracking loss; the
figure therefore breaks segments rather than connecting impossible jumps.
"""

import argparse
import csv
import json
from pathlib import Path
from typing import Any

REQUIRED = {
    "event", "wall_time_ms", "frame_time_ns", "tracking",
    "x_m", "y_m", "z_m", "path_m", "horizontal_m",
    "displacement_m", "vertical_m", "speed_mps", "segments",
    "gaps", "outliers",
}
NUMERIC = (
    "wall_time_ms", "frame_time_ns", "x_m", "y_m", "z_m",
    "path_m", "horizontal_m", "displacement_m", "vertical_m",
    "speed_mps", "segments", "gaps", "outliers",
)


def load_samples(csv_file: Path) -> tuple[list[dict[str, float]], int]:
    samples: list[dict[str, float]] = []
    events = 0
    with Path(csv_file).open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames is None or not REQUIRED.issubset(reader.fieldnames):
            missing = REQUIRED.difference(reader.fieldnames or [])
            raise ValueError(f"Not a valid VECTOR VIO CSV (missing: {sorted(missing)})")
        for number, row in enumerate(reader, start=2):
            if row["event"] != "POSE":
                events += 1
                continue
            try:
                sample = {key: float(row[key]) for key in NUMERIC}
            except (TypeError, ValueError) as exc:
                raise ValueError(f"Invalid numeric value in CSV row {number}") from exc
            samples.append(sample)
    return samples, events


def summarize(samples: list[dict[str, float]], events: int,
              known_distance: float | None = None) -> dict[str, Any]:
    if not samples:
        raise ValueError("No POSE samples in this recording")
    first, last = samples[0], samples[-1]
    report = {
        "samples": len(samples),
        "events": events,
        "duration_wall_s": round((last["wall_time_ms"] - first["wall_time_ms"]) / 1000.0, 3),
        "estimated_path_m": round(last["path_m"], 4),
        "horizontal_path_m": round(last["horizontal_m"], 4),
        "net_displacement_m": round(last["displacement_m"], 4),
        "vertical_change_m": round(last["vertical_m"], 4),
        "segments": int(last["segments"]),
        "tracking_gaps": int(last["gaps"]),
        "rejected_jumps": int(last["outliers"]),
        "maximum_observed_speed_kmh": round(max(s["speed_mps"] for s in samples)*3.6, 3),
        "caution": "ARCore estimates, not validated ground truth; poses may jump between segments",
    }
    if known_distance is not None:
        if known_distance <= 0:
            raise ValueError("--known-distance must be greater than zero")
        report["known_path_m"] = known_distance
        report["absolute_path_error_m"] = round(abs(last["path_m"]-known_distance), 4)
        report["relative_path_error_pct"] = round(
            100 * abs(last["path_m"]-known_distance) / known_distance, 3
        )
    return report


def make_plots(samples: list[dict[str, float]], destination: Path) -> None:
    import matplotlib.pyplot as plt

    destination.mkdir(parents=True, exist_ok=True)
    times = [(s["wall_time_ms"] - samples[0]["wall_time_ms"]) / 1000 for s in samples]
    series = (
        ("path_m", "Path distance (m)", "path_distance.png"),
        ("speed_mps", "Speed (m/s)", "speed.png"),
        ("vertical_m", "Relative height (m)", "height.png"),
    )

    for key, ylabel, filename in series:
        fig, ax = plt.subplots(figsize=(10, 4.5))
        ax.plot(times, [s[key] for s in samples], linewidth=1.6)
        ax.set_xlabel("Elapsed wall time (s)")
        ax.set_ylabel(ylabel)
        ax.set_title("VECTOR VIO — " + ylabel)
        ax.grid(True, alpha=0.25)
        fig.tight_layout()
        fig.savefig(destination / filename, dpi=170)
        plt.close(fig)

    fig, ax = plt.subplots(figsize=(7, 7))
    # Segments may not share the same origin. Avoid connecting them visually.
    sequences: list[list[dict[str, float]]] = []
    for sample in samples:
        if not sequences or int(sequences[-1][-1]["segments"]) != int(sample["segments"]):
            sequences.append([])
        sequences[-1].append(sample)
    for index, sequence in enumerate(sequences):
        ax.plot([s["x_m"] for s in sequence], [s["z_m"] for s in sequence],
                linewidth=1.6, label=f"segment {index + 1}")
        ax.scatter([sequence[0]["x_m"]], [sequence[0]["z_m"]], s=25)
    ax.set_xlabel("ARCore world X (m)")
    ax.set_ylabel("ARCore world Z (m)")
    ax.set_title("VECTOR VIO — camera path (top view)")
    ax.set_aspect("equal", adjustable="datalim")
    ax.grid(True, alpha=0.25)
    if len(sequences) <= 12:
        ax.legend()
    fig.tight_layout()
    fig.savefig(destination / "trajectory_xz.png", dpi=170)
    plt.close(fig)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv_file", type=Path, help="CSV exported by the Android app")
    parser.add_argument("--out", type=Path, default=Path("vio_plots"))
    parser.add_argument("--known-distance", type=float, default=None,
                        help="Independently measured true path length in metres")
    args = parser.parse_args()

    samples, events = load_samples(args.csv_file)
    report = summarize(samples, events, args.known_distance)
    args.out.mkdir(parents=True, exist_ok=True)
    with (args.out / "report.json").open("w", encoding="utf-8") as fh:
        json.dump(report, fh, indent=2)
    make_plots(samples, args.out)
    print(json.dumps(report, indent=2))
    print(f"Saved plots and report to {args.out.resolve()}")


if __name__ == "__main__":
    main()
