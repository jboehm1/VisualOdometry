# Coordination — chantier PDR sans caméra

**Audit effectué : 2026-10-18 (GitHub distant).**  
**Dépôt :** `jboehm1/VisualOdometry`  
**Branche d'intégration lue :** `feature/slam-explorer` à **`6c8f54f38af868195c8580a5c0c64c9999a6878f`**.  
**Instructions lues :** `AGENTS.md` et `docs/DEVELOPMENT_COORDINATION.md` à ce SHA, puis `docs/PDR_MODULE.md` sur ma branche.  
**Ma branche de travail :** `feature/camera-free-pdr`, auditée initialement à **`f8228aa7acbc453001d623c537bd8d7f0a9bc176`** ; PR **#5** en brouillon.  
**Dernière CI PDR au SHA initial :** GitHub Actions `39159010078` = **success** (compilation Android, lint, JUnit, Python, fixtures/replay, APK debug). Ce n'est pas un test sur S21.

## Contraintes comprises et appliquées

1. **Isolation stricte du chantier.** Développer dans `app/src/main/java/dev/jboehm/vectorvio/pdr/`, `app/src/test/java/dev/jboehm/vectorvio/pdr/`, `tools/pdr/`, les tests PDR et les documents PDR. Ne rien fusionner dans `feature/slam-explorer` sans validation d'intégration.
2. **Préserver les développements concurrents.** Le chantier B1–B8 est sur `work/slam-roadmap-b1-b2-b8` (SHA contrôlé `f34c8f704b7f35f085825551f74feb2734f83bdc`). Le chantier des campagnes est sur `feature/three-validation-campaigns` (SHA contrôlé `9dbe7a26d16b57248b9f07fb03549e28ebc096a0`). L'instantané de coordination n'est pas un verrou et n'inclut pas les fichiers non poussés.
3. **Conflit réel identifié :** les autres branches ajoutent chacune une entrée à l'index 9 du menu DATA de `MainActivity.java` ; ne pas écraser le menu, ses actions, l'import ou les services. L'intégration devra attribuer des identifiants/actions stables, préserver toutes les entrées et les vérifier sur le résultat **combiné**, pas sur des APK de branches séparées.
4. **Manifest et build partagés :** préserver simultanément les activités PDR, planner, replay et leurs permissions dans `AndroidManifest.xml`, ainsi que les builds/benchmarks dans les workflows. Pas de résolution de conflit de type « tout ours » ou « tout theirs ».
5. **Mesures et repères non interchangeables :** `pdr/PdrEngine.java` (Android) et `tools/pdr/PdrReplay.java` exécutent le moteur PDR réel ; `tools/campaigns/imu_pdr.py` reste une *baseline Python* séparée. `tools/pdr/evaluate.py`, `tools/benchmark/bench.py` et `tools/eval/trajectory_metrics.py` ont des conventions/métriques différentes. Indiquer horloge, axes, repère magnétique/nord vrai, moteur, source de vérité terrain, alignement et couverture dans chaque rapport. La vérité terrain ne doit jamais alimenter l'estimateur.
6. **Énergie et sécurité :** préserver la séparation fréquence de lecture des poses VIO (~30/s cible) / affichage VIEW. Ne pas perturber ARCore, la géofusion diagnostique ni les sorties GPS mesurées. Ne pas promettre de précision ni de fonctionnement écran éteint sans preuve.
7. **Preuves à chaque étape :** relever les nouveaux SHA, inspecter les changements concurrents avant toute modification partagée, exécuter la CI au commit exact, documenter erreurs, limites et fichiers touchés ; le build sur une branche ne certifie pas le build combiné.

## Fichiers partagés et décision de propriété

| Fichier / interface | Pourquoi partagé | Décision pour ce chantier |
|---|---|---|
| `app/src/main/java/dev/jboehm/vectorvio/MainActivity.java` | Menu DATA commun, ajouts concurrents à l'index 9 | **Ne pas modifier** ; future intégration coordonnée avec tests de chaque action |
| `app/src/main/AndroidManifest.xml` | Activités et permissions PDR / planner / replay | **Ne pas modifier** ; préserver toutes les déclarations lors de l'intégration |
| `.github/workflows/android.yml` et autres workflows | Build, campagnes, APK | **Ne pas modifier** ; utiliser CI PR existante |
| `README.md`, `docs/DEVELOPMENT_COORDINATION.md`, `AGENTS.md` | Documentation transversale et consignes d'agents | **Ne pas modifier** |
| `tools/benchmark/**`, `tools/campaigns/**`, `tools/eval/**` | Évaluateurs/campagnes d'autres chantiers | **Ne pas modifier** ; aligner les contrats par documentation, sans remplacement |
| `docs/coordination/camera-free-pdr.md` | Handoff de ce chantier | **Seule exception à l'isolation : créer/mettre à jour mon compte rendu**, sans toucher aux autres fichiers de coordination |

## Fichiers que je prévois de modifier après cet audit

Seulement `app/src/main/java/dev/jboehm/vectorvio/pdr/{PdrEngine,PdrActivity,PdrSensorBridge,PdrTraceWriter}.java` si nécessaire, `app/src/test/java/dev/jboehm/vectorvio/pdr/*`, `tools/pdr/{PdrReplay,evaluate,progress}.py` ou `.java` selon le fichier, `tools/tests/test_pdr_tools.py`, `docs/PDR_MODULE.md`, `docs/PDR_TASKS.json`, `docs/PDR_PROGRESS.md`, et ce compte rendu. Vérifier le diff exact et conserver toute autre contribution.

## Prochaine étape du chantier

**P0 : renforcer le contrat de replay/évaluation PDR Java sur données réalistes** : corriger les ambiguïtés distance totale vs longueur de trajectoire (trous de cap), synchronisation/tri des événements et fidélité export/replay, créer des tests de non-régression cohérents, puis lancer la CI sur le **nouveau SHA**. Ensuite : capteurs/permissions/replay S21 et benchmarks réels indépendants. Les performances de terrain et l'usage en poche restent **non validés**.

**Intégration :** PR #5 demeure en brouillon, sans fusion sur `feature/slam-explorer`. Recontrôler les têtes et résoudre les conflits inter-chantiers uniquement dans une intégration revue et testée.

### Intervention de fiabilisation — PDR export/replay (2026-10-18)
Changements limités au module : **PdrEngine**, **PdrTraceWriter**, tests JUnit et outils d'évaluation PDR. Protection contre échantillons d'avant START et anciennes orientations réutilisées après pause ; `path.csv` exporté avec `gap_before` comme le replay Java. Ne modifie pas le menu DATA, le manifeste ni les workflows partagés. La CI du nouveau commit devra être contrôlée avant toute intégration.

### Isolated camera-free usability changes (2026-10-18)
Read integration SHA `9662709a508064e011de3f34109a8a73d9374122` and current PDR branch before editing. Scope: only `app/src/main/java/dev/jboehm/vectorvio/pdr/*`, its unit tests, PDR-specific documentation, and a small module-local replay contract note. **No edits to** `MainActivity.java`, `AndroidManifest.xml`, shared Android workflows, `tools/benchmark/*`, `tools/campaigns/*`, or the other agents' dataset/test environment. Implemented default safe DISTANCE ONLY, explicit optional phone-forward heading with physical/magnetic gates, separately persisted WALK/RUN strides, manual measured-course calibration, sensor inventory/cadence readout, session-safe export metadata, and robust step-sensor fallback. Next: CI at the exact new SHA, fix any module-specific regressions; then safely hand off integration of background service without a conflicting manifest change. Physical-device and neural-body-heading validation remain outstanding.

### Per-session opt-in safeguard (2026-10-18)
`PHONE FORWARD` no longer persists between Activity launches or completed recordings. Explicit opt-in required per session; walk/run calibration remains persistent. Files limited to PDR module, module documentation and coordination note, no shared manifest/menu/build workflow changes. New CI run triggered through the pre-existing PDR paths filter; do not treat this run as physical proof.

### Camera-free neural inference handoff — 2026-10-18
Integration SHA consulted: `372c935fc27536d417fa371d79f5f453546ebefa`. Working branch: `feature/camera-free-pdr`, initially `01edef7b32b86cd76bb2527de14389fd0b5d098d` at AI documentation stage. Read `AGENTS.md` and `docs/DEVELOPMENT_COORDINATION.md` from integration. Added only `app/src/main/java/dev/jboehm/vectorvio/pdr/ai/*`, module-local JUnit tests, `tools/pdr/AI_MODEL_CONTRACT.md` (text only, existing pipeline trigger), and `docs/PDR_AI_OPTIONS.md` / `docs/PDR_AI_TEST_HANDOFF.md` / this note / PDR status documentation. **NO edits to** `MainActivity.java`, `AndroidManifest.xml`, `app/build.gradle`, shared workflows or `tools/campaigns/*`, `tools/benchmark/*`, `tools/pdr/datasets.py`, `tools/pdr/run_real.py`. This task uses no real trained weights; its backend-neutral neural candidate is deliberately excluded from the operational odometer. Test-environment owner must validate full original RoNIN/OxIOD/TLIO datasets with held-out models; see handoff. Next: CI exact SHA, then only after model/parity evidence consider an Android diagnostic UI, never automatic PdrEngine fusion.


### Reprise PDR 1.1 — audit initial 2026-10-19 (UTC+02)

- **GitHub refs rechecked before editing:** PDR `feature/camera-free-pdr` = `a98b8ccf8a1a63aab8b233b2aed2f59dfd509a80`; integration `feature/slam-explorer` = `5f0105effd4fb2fb66b7d23620189c9605576de7`; roadmap `work/slam-roadmap-b1-b2-b8` = `51885d3747721f176c14083ccb391c0719691bac`; campaigns `feature/three-validation-campaigns` = `9dbe7a26d16b57248b9f07fb03549e28ebc096a0`. Other open PRs (#14, #11, #5, #3, #2, #1) checked.
- **Instructions reviewed at integration SHA:** `AGENTS.md` and `docs/DEVELOPMENT_COORDINATION.md`; module coordination, `docs/PDR_MODULE.md`, `docs/PDR_AI_OPTIONS.md`, `docs/PDR_AI_TEST_HANDOFF.md`, `tools/pdr/AI_MODEL_CONTRACT.md`, `docs/PDR_OPTIONS_STATUS.md`. Open PR #5 is still draft, marked non-mergeable; do **not** force merge.
- **Baseline evidence:** existing PDR GitHub Actions run `39167520138` on the exact initial `a98b8ccf` concluded `success` (no physical S21 test). Independent SIMUL annotations document overcount risk; not trajectory or learned model validation.
- **Owned branch/scope:** `feature/camera-free-pdr`. Only module-local `pdr/*`, dedicated PDR tests, `tools/pdr/*` if unavoidable and `docs/PDR_*` plus **this** coordination note. Do not touch other agents' files.
- **Sensitive shared files unchanged:** `MainActivity.java`, `AndroidManifest.xml`, `.github/workflows/android.yml`, `README.md`, `AGENTS.md`, `docs/DEVELOPMENT_COORDINATION.md`, `tools/campaigns/**`, `tools/benchmark/**`, `tools/eval/**`. DATA index collisions and Manifest activity preservation require integration-owned test at combined SHA.
- **Reliability audit targets:** (1) false-positive fallback steps/cadence, (2) magnetic/pose stale-heading reuse, (3) stale pressure/event timestamps, (4) actual sensor registration vs claimed availability, (5) exported event and replay correctness. All improvements need tests; failures or incomplete targets must remain explicitly open.
- **Tests at initial coordination commit:** upstream GitHub Actions `39167520138` green; new patch **not yet tested**. No local Android SDK, emulator or S21 available in this chat; use GitHub CI at new exact commit SHA.
- **Next action:** implement strictly module-local P0 improvements plus regression tests, refresh refs before pushing, verify exact GitHub Actions results and update PDR progress only where acceptance evidence truly changes. This document is a recorded coordination snapshot, not a lock.

### PDR 1.1 — premier lot testé et suites (2026-10-19)

- Push `f4c2f84de7d615080b15bb2771e880ee49e3c174` après coordination `6419eba`: moteur IMU + Android corrigés sans aucun changement aux fichiers partagés; test `StepPeakDetectorTest`, test moteur et Robolectric. GitHub Actions `39173399005` au **SHA exact**: `success` sur **native-schema-and-java-replay** et **Android APK + Robolectric PDR + lint**; artefact debug `camera-free-pdr-debug-apk` ID `12153213255`. Ce test **n'exécute pas** le SIMUL réel sur le nouvel algorithme.
- Complément présent dans le prochain commit de ce chantier : un GPS_COURSE de mauvaise qualité invalide immédiatement la direction GNSS précédente; rejet Android d'un événement de perte de précision magnétique; test de l'export de `HEADING_INVALID` et des ruptures de trace. Le moteur Java replay consomme déjà `HEADING_INVALID` explicitement.
- **Score audité :** 23 tâches, 92 sous-tâches, **42,1 %**, inchangé volontairement: les tests synthétiques et les corrections sur des sous-tâches déjà acceptées ne prouvent pas les critères encore ouverts (S21, vrais métriques, modèle appris). Les `reference_ci` seront actualisées seulement sur run terminé et constaté.
- **Demande au responsable datasets/campagnes:** rejouer strictement le même jeu SIMUL des 4 participants × 3 positions contre le SHA nouveau, fournir count error, F1 apparié, faux positifs et sous-détections par portage ainsi que comparaison appariée au moteur `f8228aa`; ne jamais prétendre que les résultats d'hier se transfèrent au détecteur modifié. Inclure marche/course/arrêt si données annotées, sinon déclarer absentes.
- **Suite :** valider la nouvelle CI au dernier commit exact, puis étude de la calibration de foulée/course et des pertes GNSS/heading sur données indépendantes. Maintenir PR #5 en brouillon; l'intégration combinée du menu DATA est un chantier distinct.


### Reprise PDR 1.2 — instrumentation capteurs (2026-10-19)
- Git refs audited: integration `feature/slam-explorer` at `836f724d938004df941aff3a5466a799d9965ae0`; work branch `feature/camera-free-pdr` initially `bf7ee500c6441a9525c22da3831d497b18f8fd56`; B1–B8 `51885d3747721f176c14083ccb391c0719691bac`; campaigns `9dbe7a26d16b57248b9f07fb03549e28ebc096a0`.
- Read `AGENTS.md` and `docs/DEVELOPMENT_COORDINATION.md` at the new integration SHA, module PDR, this coordination file and task tracker. The 35 integration commits since `5f0105e` modify central testenv/Atlas/README, not our PDR package; do not overwrite any of them.
- Working scope: `PdrEngine.java` (reject pre-session accelerometer samples), `PdrSensorBridge.java` (timestamp/delivery diagnostics), new module-local pure Java `SensorTimingMonitor.java`, `PdrActivity.java` and `PdrTraceWriter.java` (observability + ZIP metadata), corresponding `app/src/test/.../pdr/*`, `docs/PDR_MODULE.md`, `docs/PDR_RELIABILITY_1_1.md`, `docs/PDR_TASKS.json`, `docs/PDR_PROGRESS.md`, `tools/pdr/progress.py` only if required for tracker correctness, and this coordination note.
- Sensitive shared files to change: **none**. Never edit `MainActivity.java`, Manifest, workflows, coordination global, `README.md`, `tools/campaigns/**`, `tools/benchmark/**`, `tools/eval/**`. PR #5 stays draft, menu DATA conflict stays integration-owned.
- Baseline exact CI `39173832012` at `bf7ee500`: Android JUnit/Robolectric/lint/APK and native schema Java replay successful. No real SIMUL re-evaluation or S21 proof at that SHA.
- Plan and limits: measure per-sensor timestamps, out-of-order/duplicates, inferred gaps, delivery latency/possible batching and stale callback markers; export as **diagnostic only**, not certified missed-sample or FIFO saturation counts. Test pure Java counters, Android bridging/ZIP persistence, and reject pre-START accel. Preserve the safe DISTANCE ONLY invariant. Update percentages only when an acceptance criterion has demonstrable evidence; CI exact SHA afterwards.

### PDR 1.2 — code handoff pending exact-SHA CI

Module-only changed files: `PdrEngine`, `PdrSensorBridge`, `PdrActivity`,
`PdrTraceWriter`, new `SensorTimingMonitor`, PDR JUnit/Robolectric tests,
`docs/PDR_MODULE.md`, `docs/PDR_RELIABILITY_1_1.md`, and this note.
No shared Android entry points, Manifest, workflows, benchmarks or campaign
code changed. Inferred long sensor intervals / delayed callbacks are quality
signals only and cannot prove dropped samples; no S21 measurements made.
The timestamp diagnostic task should remain **not accepted** until hardware
qualification shows sampling, buffering and actual interruptions, preserving
the weighted 42.1% progress score for now. Next: exact SHA GitHub Actions
(Android JUnit/Robolectric/lint/APK + native-schema fixtures), correct any
failures, followed by held-out SIMUL / S21 testing by their respective owners.

### PDR 1.2 — later GNSS/heading loss fixes

Detected a fail-safe hole: switching GNSS ASSIST off, receiving an
incomplete fix, or losing the GNSS provider left the accepted course active
until timeout. Added explicit `GPS_INVALID` across `PdrActivity`,
`PdrEngine`, `PdrReplay` and PDR tests. Also updated magnetic accuracy
callbacks to invalidate using a current monotonic timestamp. All changes
isolated to PDR code/tests/docs; no shared menu/Manifest/CI changes.
The next CI must validate the exact new HEAD; SIMUL/S21 validation remains
separately unproven and the 42.1% tracker unchanged.

### PDR 1.3 — paired genuine SIMUL gate (2026-10-19)
New integration ref read: `a9683c47452c00f48c68527dc8d92f6fd3c00d82`;
PDR initial ref `adf5488f56960ea9a88d7d52a66c705ffe34ecd6`.
Reviewed current `AGENTS.md`, global coordination, module/AI limitations
and the existing other-agent real SIMUL scripts. To avoid modifying other
chantiers, added **PDR-owned** `tools/pdr/simul_compare.py` + isolated
`tools/pdr/SimulStepTrace.java`, new separate `pdr-real-steps.yml`
workflow, `docs/PDR_REAL_SIMUL_ITERATION.md` and this record only.
No existing shared Android/menu/manifest/benchmark/campaign workflows or
tools modified. Four previously seen SIMUL subjects and two additional
frozen participants; each benchmark compiles the exact candidate engine.
Goal: quantify step F1 and count-error vs original pinned Java engine
on actual IMU; no trajectory accuracy or Galaxy S21 claim. Next: inspect
exact-SHA CI and report and modify detector only if warranted by evidence.

### SIMUL 1.3 initial empirical result — 2026-10-19
GitHub Actions **39178813507** ran `b502376c1206054501f738abe4dcbe7bae2a69a1`
against pinned original `f8228aa` on **18 actual SIMUL trials**, recording
sensor and annotation SHA256. Mean absolute step-count error improved
15.486% -> 9.127%; macro timed-step F1 0.906572 -> 0.910695.
False positives down 608 -> 443, missed up 134 -> 238: accuracy trade-off
must not be concealed. The additional 12/31 subjects are NOW exposed,
so any subsequent iterations must score them as seen; choose fresh
participants for truly independent final validation. No step accuracy 100%,
no position GT, no S21 test.
Next iteration adjusts refractory cadence rule only after proving with new
CI; no shared files touched.
