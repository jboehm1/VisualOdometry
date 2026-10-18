# Raw EuRoC vision + IMU pilot coordination

- Reviewed Git SHA: `372c935fc27536d417fa371d79f5f453546ebefa`, branch `feature/slam-explorer`, including `AGENTS.md` and `docs/DEVELOPMENT_COORDINATION.md`.
- Work branch: `feature/real-raw-vio-euroc`, isolated; `master` is the old historical branch and must remain untouched.
- Scope: build and prove one **real recorded raw-image plus synchronized IMU** benchmark (EuRoC), with independent trajectory reference and conservative output claims. Preserve the previous successful Java PDR SIMUL campaign unchanged.
- Shared files involved: NONE of `MainActivity.java`, `AndroidManifest.xml`, DATA menu, legacy workflows, Java PDR, existing benchmark adapters. Add only new `tools/campaigns/raw_stereo_imu.py`, isolated downloader/workflow, tests, docs and this note. If a preexisting CLI must be touched later, coordinate review and maintain backwards compatibility.
- Important concurrency: existing PDR and roadmap branches conflict in Android UI. This task will not merge their code, nor claim Linux runs ARCore. Device frame vs camera frame, body/camera extrinsics, ground truth leakage, and gyro integration must be explicitly tested. No trajectory accuracy claim from merely indexed images, fake images or published estimated poses.
- Immediate next: confirm download source and license, execute actual camera frame decoding, track 3D image features using stereo calibration and metric depth, use real synchronized gyroscope to influence pose, then score against real reference. Stop/fail closed if truth or calibration is insufficient. Run exact-SHA CI before merge.

## Update 2026-10-18: validated smartphone instead of inaccessible EuRoC RAW archives

The official EuRoC ZIP mirrors timed out from GitHub Actions. A third-party Google Drive mirror failed permission checks; **neither was misrepresented as raw VIO evaluation**.

Selected source for the real camera+IMU benchmark: [Mobile-GVIO](https://doi.org/10.5281/zenodo.20525157), actual handheld Honor smartphone with independently captured LiDAR-IMU Fast-LIO2 reference, recorded `Indoor-2.zip`. Official dataset record and checksums confirmed via [source probe](https://github.com/jboehm1/VisualOdometry/actions/runs/39167664948) and [calibration probe](https://github.com/jboehm1/VisualOdometry/actions/runs/39167810961).

Confirmed:
- Authentic 950,890,027-byte compressed archive with upstream pinned MD5 `2a5439c7064ecc1a254f8a5790586742`, containing a 2,833,868,105-byte raw ROS bag and ground_truth.txt.
- Independent pinhole camera intrinsics and camera/IMU extrinsics from `calib.zip`; compressed source checksum pinned.
- Real decoding & inference **succeeded** on a 22-second interval: 131 genuine camera frames, 2,200 gyro samples (2,185 integrated), 130 accepted monocular essential-pose direction estimates with 14,313 geometric inliers ([source](https://github.com/jboehm1/VisualOdometry/actions/runs/39168785572)).
- **Previous evaluation FAILED honestly**: initial video estimate range `1777276473.64–1777276495.35` seconds fell outside independent truth range `1777276757.70–1777276848.90`. No zero-overlap ATE is claimed. The current isolated raw workflow starts its window later at `--skip 295` to overlap actual LiDAR reference and is being verified separately.
- Raw monocular direction steps are arbitrary-unit increments; gyro orientation gives orientation, but the pipeline does **not** recover absolute metric translation scale. Scoring is **post-inference Sim(3) shape RMSE only**, not phone odometer metric ATE, not ORB-SLAM3 and not Android ARCore.
- A separate 3-test [scoring contract workflow](https://github.com/jboehm1/VisualOdometry/actions/runs/39169482876) passed; tests include no-time-overlap rejection and refusal to claim metric scale.

### Changed file ownership
New isolated `tools/campaigns/mobile_gvio_fetch.py`, `mobile_gvio_raw.py`, `mobile_gvio_score.py`, `tools/campaigns/tests/test_mobile_gvio.py`, independent CI workflows, this coordination note and new documentation. **No changes to `MainActivity.java`, `AndroidManifest.xml`, Java PDR engine, DATA menu, or existing campaign orchestrators.**

### Next gate
Wait for and audit exact results of [raw evaluation workflow](https://github.com/jboehm1/VisualOdometry/actions/runs/39168984191). If overlapping reference and enough successful image/gyro updates, archive compact reports on user's Drive, remove obsolete probe workflows, run CI on PR against current integration SHA, and merge only if green. If it fails, preserve failure status and document the remaining reference-alignment issue rather than inventing success.
