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
