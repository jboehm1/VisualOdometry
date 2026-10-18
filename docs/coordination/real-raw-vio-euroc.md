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

## Mise à jour : basculement vers données smartphone authentiques

Le site historique de téléchargement des ZIP EuRoC n'a pas répondu au runner GitHub, et un miroir Google Drive tiers n'était pas accessible anonymement. Le chantier a donc choisi **Mobile-GVIO Indoor-2** avec un enregistrement public du capteur du téléphone, plutôt que de substituer une trajectoire VINS-Mono publiée à des images brutes.

- Source réelle : `https://zenodo.org/api/records/20525157` ; **951 Mo compressés** et **2,83 Go de bag décompressée** ; MD5 et taille vérifiés contre le fournisseur. Calibration caméra–IMU native également vérifiée et requise.
- Périmètre algorithmique : `tools/campaigns/mobile_gvio_raw.py` est une **baseline de recherche distincte** de notre estimateur Android ARCore. Elle utilise l'optical flow et l'essential matrix pour la direction de translation et l'intégration gyroscopique calibrée pour l'orientation ; **pas d'échelle métrique monoculaire estimée**.
- Évaluateur `tools/campaigns/mobile_gvio_score.py` : référence Fast-LIO2 LiDAR–IMU chargée **uniquement après** inférence ; Sim3 ajuste l'échelle a posteriori, donc score de forme et pas preuve d'erreur métrique de distance.
- Preuve d'inférence sur vraie caméra/IMU : [CI 39168458373](https://github.com/jboehm1/VisualOdometry/actions/runs/39168458373), **131 images**, **2 185 intégrations gyro**, **130 translations visuelles**. La première évaluation a échoué faute de chevauchement des timestamps : premier intervalle avant le segment de vérité. Ce résultat n'est pas passé sous silence.
- Rejouer sur l'intervalle 295 s après début pour rechercher un chevauchement temporel avec la référence. **Ne pas annoncer de validation bout en bout avant job vert**.
- Fichiers créés sur cette branche : `tools/campaigns/mobile_gvio_fetch.py`, `mobile_gvio_raw.py`, `mobile_gvio_score.py`, `tools/campaigns/tests/test_mobile_gvio_score.py`, `.github/workflows/real-phone-vio.yml`, `docs/campaigns/REAL_PHONE_IMAGE_IMU.md`, cette coordination. Les workflows provisoires de découverte ont été retirés (historique CI conservé).
- Fichiers Android et partagés modifiés : **aucun**. La branche d'intégration et les autres travaux PDR/géofusion restent intacts.

## Complete real-data run verified — 2026-10-18

- Exact tested commit: `ce8bd4141dfdd334887ad91530035c7814b12fe7`. [GitHub Actions run #39170361901](https://github.com/jboehm1/VisualOdometry/actions/runs/39170361901) **SUCCESS**.
- Actual raw 950,890,027-byte camera+IMU archive MD5 checked, original `indoor_2.bag` decoded. **131 images and 2,185 gyro integrations** performed; 130 visual updates with 14,313 RANSAC inliers.
- Reference score 124 matched poses, posthoc angular correlation 0.815, posthoc phone-to-reference shift 282.95965 seconds, Sim(3) after-truth-fit shape RMSE 0.40149 m. **NOT independent timestamp calibration, NOT independent metric scale or APK accuracy.**
- Reference poses were not estimator input. The reference **was** used in the separate evaluator to fit clock and scale; see warning above.
- Report transferred to user Drive `SLAM_BENCHMARKS/04_REPORTS`, verified: https://drive.google.com/file/d/1OkFbRBkj6kgoHM1HAHTxogOMUKdnxL_t/view
- Existing production Android, PDR Java, DATA menu, manifest, historical workflows and `benchmark.py` were not touched.
- Remaining handoff: review isolated PR against current integration SHA, run CI on combined PR SHA, merge if clean. Keep next step **metric full VIO** distinct from this research baseline.
