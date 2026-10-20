# VECTOR VIO — Settings UI and visual GPX, Android CI release

**Release code SHA:** `58b08ed015e1d13dbea034af87559850c7373200` (19 October 2026). Branch `feature/settings-categories-ui`, draft PR #26 to `feature/gpx-visual-depth-fusion` (which is itself draft PR #25). Not merged into the central Android integration branch.

## Verified CI evidence

- [Android build, lint, unit tests, APK, GPS/synthetic replay](https://github.com/jboehm1/VisualOdometry/actions/runs/39332169016) **SUCCESS** at the exact SHA. Downloaded Gradle HTML report contains **180 tests, 0 failures, 0 ignored**.
- [Three campaigns](https://github.com/jboehm1/VisualOdometry/actions/runs/39332168974) **SUCCESS** at the exact SHA. Note A=external VINS on EuRoC, B=Python synthetic PDR, C=ARCore MP4 compile/replay capability; none are S21 GPX geometric accuracy.
- [Installable debug APK artifact](https://github.com/jboehm1/VisualOdometry/actions/runs/39332169016/artifacts/12225281452) SHA256 `20df6d09492deb7de09cf02025e5497172aadab036bc74fddd307ae20bfb7c13`, 3,654,544 bytes. Outer artifact ZIP SHA256 `84327c450ae2d78888bc1eb71f3f6025a1c2f9135bfe0f7fd6e95a915c72c6c9`, 2,933,659 bytes. ZIP CRC verified; APK manifest and classes.dex present. Artifact expires according to GitHub retention, 14 days.
- No actual Android device was physically used by this CI. Installation and camera interaction must be verified manually on S21.

## New menu and safety

Top **MENU** replaces the earlier large DATA list with six categorized dialogs: Navigation & GPX, Location & sensors, Camera & overlays, Recordings & exports, 3D maps & places, Performance & tests. Typed `SettingsCatalog.Action` IDs eliminate position-based dispatch within this module; other feature branches PDR/COURSES must be integrated explicitly in a later separate merge. Old quick MAPS/LAYERS/SIM/FLASH/START/EXPORT remain available.

Navigation → GPX AR additionally includes:
- `MANUAL GPX SLIDE`: user-controlled visual horizontal translation only after candidate alignment; one-finger drag, RESET, DONE. Uses paired ARCore hits on same observed ground plane where available with approximate fallback otherwise. Max 2 m per applied gesture, 25 m session offset. This **does not change** underlying route data, raw GPS fixes, sensor poses or odometer distance. Reset on AR tracking loss/lock/new GPX, not persistent across sessions. HUD flags manual visual adjustment.
- `GPS AID · ON/MUTED` simulation disconnects **GPX-specific location delivery**, not the entire phone's GNSS receiver nor all other location features. The GPX route remains referenced to prior accepted GNSS alignment. After GPS stops, visible route may continue without an arbitrary 30-second cutoff **only while ARCore continuously tracks in the same reference frame with fresh frames**. Tracking reset, lost tracking, or stale pose hides the line and forces reacquisition; view displays VIO-only, not a guarantee of geographic truth. Stated drift allowance is uncalibrated and cannot bound true error.
- Depth OFF default; optional confidence gated occlusion, not a 3D terrain replacement. The GPX's DEM elevation is advisory and not used as ground truth.

## Research and remaining validation

Future fixed-lag SE(2)/SE(3) factor graph / GNSS+VIO robust smoother F06: [research and references](../GPX_AR_SOTA_FUSION_RESEARCH.md). Correlated GNSS biases, FLP common data and ARCore IMU double-counting are explicit constraints; **not currently an operational new smoother**.

Physical gates remain: UI legibility/touch targets in sun on Galaxy S21, slopes, turns, 2–10 minutes deliberate GPS mute, comparison vs independent positions p50/p95, tracking interruptions/relocalization, camera lock/unlock, dynamic ground, energy impact Depth OFF/ON and long visual VIO. No guarantee of centimeter-level placement or indefinite drift-free navigation. Keep PR draft until branch-integration+field validation.
