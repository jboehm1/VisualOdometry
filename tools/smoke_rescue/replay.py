#!/usr/bin/env python3
"""Smoke-rescue research-only occupancy visualizer; Python standard library.

Input is a normalized JSONL interchange format, NOT a native radar driver.
See docs/SMOKE_RESCUE.md for frames, coordinate/pose requirements, limits.
"""
import argparse
import html
import json
import math
from pathlib import Path

MIN_RANGE_M, MAX_RANGE_M = 0.2, 20.0
OCC_STEP, FREE_STEP = 0.85, -0.35
LOG_MIN, LOG_MAX = -3.0, 3.0


def finite(value, name):
    if isinstance(value, bool) or not isinstance(value, (float, int)):
        raise ValueError(name + " must be a finite number")
    v = float(value)
    if not math.isfinite(v):
        raise ValueError(name + " is not finite")
    return v


def parse_frames(lines):
    """Reject ambiguous clocks, missing pose provenance, and malformed returns."""
    frames, last_t = [], float("-inf")
    for line_no, line in enumerate(lines, 1):
        if not line.strip():
            continue
        try:
            raw = json.loads(line)
            if not isinstance(raw, dict):
                raise ValueError("frame must be an object")
            t = finite(raw["timestamp_s"], "timestamp_s")
            if t <= last_t:
                raise ValueError("timestamps must be strictly increasing")
            pose = raw["pose"]
            if not isinstance(pose, dict):
                raise ValueError("pose must be an object")
            source = pose.get("source")
            if not isinstance(source, str) or source not in (
                "synthetic", "external_reference", "unverified_estimate"
            ):
                raise ValueError("pose.source must state provenance explicitly")
            x = finite(pose["x_m"], "pose.x_m")
            y = finite(pose["y_m"], "pose.y_m")
            yaw = finite(pose["yaw_rad"], "pose.yaw_rad")
            if abs(yaw) > 10 * math.pi:
                raise ValueError("yaw_rad implausible (degrees supplied?)")
            radar = raw["radar"]
            if not isinstance(radar, list) or len(radar) > 10000:
                raise ValueError("radar must be a bounded list")
            hits = []
            for h in radar:
                if not isinstance(h, dict):
                    raise ValueError("radar return must be an object")
                distance = finite(h["range_m"], "radar.range_m")
                azimuth = finite(h["azimuth_rad"], "radar.azimuth_rad")
                if not MIN_RANGE_M <= distance <= MAX_RANGE_M:
                    raise ValueError("radar range outside research gate")
                if abs(azimuth) > math.pi:
                    raise ValueError("radar azimuth outside [-pi, pi]")
                hits.append((distance, azimuth))
            thermal = raw.get("thermal_bearings", [])
            if not isinstance(thermal, list) or len(thermal) > 200:
                raise ValueError("thermal_bearings must be a bounded list")
            bearings = []
            for bearing in thermal:
                angle = finite(bearing, "thermal bearing")
                if abs(angle) > math.pi:
                    raise ValueError("thermal bearing outside [-pi, pi]")
                bearings.append(angle)
            frames.append({"t": t, "pose": (x, y, yaw, source),
                           "hits": hits, "thermal": bearings})
            last_t = t
        except (KeyError, TypeError, ValueError, json.JSONDecodeError) as exc:
            raise ValueError(f"invalid frame at line {line_no}: {exc}") from exc
    if not frames:
        raise ValueError("no valid frames")
    return frames


def line_cells(a, b):
    """Integer Bresenham including both endpoints (all octants)."""
    x0, y0 = a
    x1, y1 = b
    dx, dy = abs(x1 - x0), abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx - dy
    while True:
        yield (x0, y0)
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 > -dy:
            err -= dy
            x0 += sx
        if e2 < dx:
            err += dx
            y0 += sy


def make_map(frames, resolution_m=0.25):
    resolution_m = finite(resolution_m, "resolution_m")
    if not 0.05 <= resolution_m <= 2.0:
        raise ValueError("resolution_m outside [0.05, 2.0]")
    logodds, poses, thermal_lines = {}, [], []
    for frame in frames:
        x, y, yaw, source = frame["pose"]
        poses.append((x, y))
        start = (math.floor(x / resolution_m), math.floor(y / resolution_m))
        for distance, azimuth in frame["hits"]:
            ex, ey = x + distance * math.cos(yaw + azimuth), y + distance * math.sin(yaw + azimuth)
            end = (math.floor(ex / resolution_m), math.floor(ey / resolution_m))
            cells = list(line_cells(start, end))
            # A positive radar return is evidence, not proof of a traversable free path.
            # Freespace inference is gated on valid returns, never missing measurements.
            for cell in cells[:-1]:
                logodds[cell] = max(LOG_MIN, logodds.get(cell, 0.0) + FREE_STEP)
            logodds[end] = min(LOG_MAX, logodds.get(end, 0.0) + OCC_STEP)
        # Thermal angles have no range. Display a short direction indicator ONLY,
        # not a map obstacle, not a person detection, not pixel-registered fusion.
        for azimuth in frame["thermal"]:
            thermal_lines.append(((x, y), (x + 1.25 * math.cos(yaw + azimuth),
                                             y + 1.25 * math.sin(yaw + azimuth))))
    return logodds, poses, thermal_lines


def render_html(logodds, poses, thermal_lines, resolution_m, provenance):
    occupied = [key for key, value in logodds.items() if value >= 0.65]
    free = [key for key, value in logodds.items() if value <= -0.65]
    coords = [(x * resolution_m, y * resolution_m) for x, y in logodds] + poses
    xs, ys = [p[0] for p in coords], [p[1] for p in coords]
    minx, maxx = min(xs) - 1, max(xs) + 1
    miny, maxy = min(ys) - 1, max(ys) + 1
    span = max(maxx - minx, maxy - miny, 1.0)
    factor = 700 / span

    def p(x, y):
        return ((x - minx) * factor + 24, (maxy - y) * factor + 24)

    cells = []
    for (ix, iy), evidence in sorted(logodds.items()):
        if abs(evidence) < 0.65:
            continue
        x, y = p(ix * resolution_m, (iy + 1) * resolution_m)
        color = "#fa795d" if evidence > 0 else "#314d60"
        size = resolution_m * factor
        cells.append(f'<rect x="{x:.1f}" y="{y:.1f}" width="{size:.1f}" height="{size:.1f}" fill="{color}"/>')
    track = " ".join(f"{p(x, y)[0]:.1f},{p(x, y)[1]:.1f}" for x, y in poses)
    tl = []
    for a, b in thermal_lines:
        ax, ay = p(*a)
        bx, by = p(*b)
        tl.append(f'<line x1="{ax:.1f}" y1="{ay:.1f}" x2="{bx:.1f}" y2="{by:.1f}" stroke="#ffd37a" stroke-width="2" stroke-dasharray="4 4"/>')
    details = html.escape(", ".join(sorted(provenance)))
    html_text = '''<!DOCTYPE html><html lang="fr"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Smoke Rescue — Research Replay</title>
<style>body{margin:0;padding:25px;background:#0c1720;color:#eef4f6;font:16px system-ui,sans-serif}h1{color:#9de0e3}section{max-width:950px;margin:auto}svg{background:#162735;width:100%;height:auto;border:1px solid #6b858e}small{color:#b3c4cc}.warning{padding:14px;background:#452b2b;border-left:5px solid #fd705a} .legend{display:flex;gap:24px;flex-wrap:wrap}</style>
<section><h1>Smoke Rescue — laboratoire hors ligne</h1><p class="warning"><strong>RECHERCHE UNIQUEMENT — JAMAIS POUR GUIDER UN SECOURS.</strong> Zones inconnues non affichées. Trajectoire fournie en entrée, non estimée par cet outil. Ni obstacle absent ni sortie sûre ne sont démontrés.</p>
<p>Origine des poses : <strong>__PROVENANCE__</strong>. La thermique illustre seulement une direction, <strong>sans profondeur ni position géométrique garantie</strong>.</p>
<div class="legend"><p>🟥 Retour radar / occupé probable</p><p>🟦 Libre probable (inféré)</p><p>🟩 Parcours fourni</p><p>🟨 Direction thermique sans distance</p></div>
<svg viewBox="0 0 748 748" role="img" aria-label="Carte d'occupation de recherche, non certifiée">
__CELLS__
<polyline points="__TRACK__" fill="none" stroke="#74ffc5" stroke-width="3"/>
__THERMAL__
</svg><p><small>__COUNTS__ — cellules inconnues omises. Aucune vérité terrain ou exactitude garantie par cet affichage.</small></p></section></html>'''
    return (html_text.replace("__PROVENANCE__", details)
            .replace("__CELLS__", "\n".join(cells)).replace("__TRACK__", track)
            .replace("__THERMAL__", "\n".join(tl))
            .replace("__COUNTS__", f"{len(poses)} poses · {len(occupied)} cellules occupées probables · {len(free)} libres probables"))


def demo_frames():
    """Explicitly synthetic; not measured radar, not smoke physics simulation."""
    frames = []
    for i in range(11):
        x, y = 0.3 * i, 0.0
        # Artificial straight wall ahead at x=4; known exact user pose.
        hits = []
        for target_y in (-1, -0.5, 0, 0.5, 1):
            dx, dy = 4 - x, target_y - y
            hits.append({"range_m": math.hypot(dx, dy),
                         "azimuth_rad": math.atan2(dy, dx)})
        frames.append({"timestamp_s": i * 0.2, "pose": {
            "x_m": x, "y_m": y, "yaw_rad": 0, "source": "synthetic"},
            "radar": hits, "thermal_bearings": [0.25] if i in (3, 7) else []})
    return [json.dumps(frame) for frame in frames]


def main(argv=None):
    parser = argparse.ArgumentParser(description="Offline synthetic/normalized radar occupancy research replay")
    inputs = parser.add_mutually_exclusive_group(required=True)
    inputs.add_argument("--input", type=Path, help="Normalized JSONL. No native ROS/TI parsing.")
    inputs.add_argument("--demo", action="store_true", help="Generate SYNTHETIC demonstrator")
    parser.add_argument("--out", required=True, type=Path, help="Output HTML file")
    parser.add_argument("--resolution-m", type=float, default=0.25)
    args = parser.parse_args(argv)
    lines = demo_frames() if args.demo else args.input.read_text(encoding="utf-8").splitlines()
    frames = parse_frames(lines)
    values, poses, thermal = make_map(frames, args.resolution_m)
    page = render_html(values, poses, thermal, args.resolution_m,
                       {frame["pose"][3] for frame in frames})
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(page, encoding="utf-8")
    print(json.dumps({"mode": "synthetic_demo" if args.demo else "normalized_replay",
                      "frames": len(frames), "returns": sum(len(f["hits"]) for f in frames),
                      "pose_source": sorted({f["pose"][3] for f in frames}),
                      "occupied_cells": sum(v >= 0.65 for v in values.values()),
                      "output": str(args.out), "safety": "RESEARCH_ONLY"}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
