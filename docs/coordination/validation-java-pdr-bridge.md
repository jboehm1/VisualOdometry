# Coordination — validation Java PDR / campagnes de données réelles

**Date de contrôle :** 2026-10-18  
**Dépôt :** `jboehm1/VisualOdometry`  
**Branche d'intégration relue :** `feature/slam-explorer`  
**SHA d'intégration consulté (recontrôlé avant cette note) :** `6c8f54f38af868195c8580a5c0c64c9999a6878f`  
**Branche de travail isolée :** `feature/validation-java-pdr-bridge` (créée directement à partir du SHA ci-dessus).  
**Premier commit de travail :** `dc32ed86d11e15038feb63aeb25c7e2f59ac3ac6`.

## Sources effectivement lues

- `AGENTS.md` sur `feature/slam-explorer`, blob `9e56be8eb4a4d4575399b2324daa6fad4b05db7d`.
- `docs/DEVELOPMENT_COORDINATION.md` sur `feature/slam-explorer`, blob `61b55fcea7c9a2e241735f0c17505ecc8721404b`.
- `docs/THREE_CAMPAIGNS.md`, `docs/BENCHMARK_PIPELINE.md`, `docs/REAL_DATA_VALIDATION.md`, `tools/campaigns/{imu_pdr.py,oxiod.py,run_dataset.py}` sur la branche d'intégration.
- `tools/pdr/PdrReplay.java`, `tools/pdr/evaluate.py`, `app/src/main/java/dev/jboehm/vectorvio/pdr/{PdrEngine.java,StepPeakDetector.java}` et `docs/PDR_DATASETS.json` lus **sur `feature/camera-free-pdr`**, pas sur l'intégration. Le PDR Java n'est donc **pas** présumé intégré.

## Contraintes retenues

1. **Travail parallèle :** jamais de modification directe de `feature/slam-explorer` ; ne pas écraser, cherry-pick aveuglément ni forcer les branches des autres chantiers. Recontrôler les HEAD/CI avant PR ou intégration. Le document central est un instantané, pas un verrou.
2. **Conflit DATA :** les branches `feature/camera-free-pdr`, `work/slam-roadmap-b1-b2-b8` et l'ancienne campagne replay ont chacune modifié `MainActivity.java` et les entrées du menu DATA, initialement toutes à l'index 9. Conserver **toutes** les actions (PDR, planner, ARCore replay), les exports et le manifeste ; pas de résolution « ours/theirs » par fichier entier.
3. **Moteurs différents :** `tools/campaigns/imu_pdr.py` est une baseline inertielle Python ; **ce n'est pas** le moteur Android `pdr/PdrEngine.java`. Pour mesurer le PDR de l'APK, utiliser son même moteur Java via `tools/pdr/PdrReplay.java`, et enregistrer l'identité/commit exact du moteur.
4. **Mesures honnêtes :** vérité terrain séparée des entrées estimateur ; préciser unités, timestamps/horloges, axes, direction magnétique/true north, détection de pas, prérequis d'orientation, source de référence, couverture et définition de l'erreur. Ne pas prétendre avoir testé les données réelles avec de simples fixtures ni ajuster une échelle/horloge sur le hold-out.
5. **Caméra et énergie :** garder pose sampling (objectif ARCore 30 FPS) indépendant des FPS de présentation VIEW, et conserver la séparation GPS mesuré / géofusion candidate. Tester le combined SHA, pas uniquement les CI des branches prises isolément.
6. **Replay ARCore :** aucun compteur de frames ne doit inclure les timestamps rejetés comme doublons ; vérifier sélecteur/import, EOF, pause/reprise, MP4 non-ARCore, export. Ne pas prendre le build Android pour une qualification physique du S21.

## Périmètre et fichiers partagés

**Propriétaire de ce chantier :** validation **B — PDR Java** à partir de données IMU canoniques, avec préparation de la comparaison aux baselines Python et aux références réelles. Le code initial `tools/campaigns/android_pdr_bridge.py` vient d'être ajouté sur notre branche, sans modifier l'app Android.

| Chemin | Opération prévue | Partagé / risque |
| --- | --- | --- |
| `docs/coordination/validation-java-pdr-bridge.md` | **Création et mise à jour** de cette note | Dossier commun ; fichier propre au chantier |
| `tools/campaigns/android_pdr_bridge.py` | **Nouveau** : conversion IMU canonique vers événements du replay Java, provenance des capteurs | Isolé, mais consommateur futur du module PDR |
| `tools/campaigns/tests/test_android_pdr_bridge.py` | **Nouveau** : tests sans données réelles ni GT injectée | Isolé |
| `tools/campaigns/README_JAVA_PDR.md` | **Nouveau** : contrat du replay et limites | Isolé |
| `docs/THREE_CAMPAIGNS.md` | **Modification éventuelle, minimale**, avec tests et limites vérifiés | **Partagé**, à recontrôler au blob SHA avant édition |
| `tools/pdr/PdrReplay.java`, `app/src/main/java/dev/jboehm/vectorvio/pdr/**` | **Lecture uniquement** pour l'instant | **Appartiennent au chantier `feature/camera-free-pdr`** ; pas de copie ni modification non coordonnée |
| `app/src/main/java/dev/jboehm/vectorvio/MainActivity.java` | **Aucune modification prévue** | **Très partagé** : menu DATA, ARCore, PDR, planner |
| `app/src/main/AndroidManifest.xml` | **Aucune modification prévue** | **Très partagé** : activités et autorisations |
| `.github/workflows/**` | **Aucune modification prévue** pour ce lot | CI commune ; éviter les conflits |

## État Git et preuves au point de départ

- `master` est l'historique 2019, **pas** l'application d'intégration.
- Le SHA `dab240210` (trois campagnes) est déjà intégré dans `feature/slam-explorer`; il ne faut **pas** le fusionner de nouveau.
- Sur `feature/slam-explorer`, le benchmark et le build Android ont des exécutions GitHub Actions vertes au SHA `dab240210`. Les exécutions sur `6c8f54f` doivent être recontrôlées avant intégration.
- La preuve actuelle de campagne A est une trajectoire **VINS-Mono externe** déjà estimée, non un run de notre application. La campagne B d'intégration exécute la **baseline Python sur signal synthétique**, non le PDR Java ni un benchmark OxIOD complet.
- `docs/DEVELOPMENT_COORDINATION.md` cite des SHA antérieurs et les conflits reproduits à l'époque. Recalculer les différences avec les HEAD courants avant de proposer un merge.

## Prochaine étape précise

1. Ajouter des tests unitaires du bridge (chronologie, flux accel/gyro, provenance heading, rejet d'injection de GT, absence de caméra).
2. Tester le bridge Python localement sur fichiers construits de manière déterministe, sans annoncer de score terrain.
3. Monter un chemin **Java PdrReplay réel**, en compilant/exécutant les classes provenant de la branche PDR sur un environnement de test isolé, sans déplacer son code dans l'intégration ni écraser celui d'autres chantiers.
4. Quand les données OxIOD/RoNIN seront disponibles et que leurs axes/timebases auront été audités, exécuter le moteur Java avec vérité terrain **externe** ; rapporter chaque limite.
5. Ouvrir une PR isolée vers `feature/slam-explorer` après relecture de son nouveau HEAD ; vérifier CI au SHA combiné. **Ne pas fusionner à l'aveugle** le chantier PDR ou B1–B8.

**Handoff :** Cette note n'atteste ni surveillance asynchrone d'autres conversations ni tests réels complets. Elle consigne seulement ce qui a été lu, prévu et poussé par ce chantier.

## Mise à jour — preuves de tests sur données pédestres **réelles** (18 octobre 2026)

**Branche :** `feature/validation-java-pdr-bridge` ; **SHA de base toujours vérifié :**
`6c8f54f38af868195c8580a5c0c64c9999a6878f`.

La suite `tools/campaigns/simul_real.py` est désormais réellement exécutée dans
[GitHub Actions run #39162292672](https://github.com/jboehm1/VisualOdometry/actions/runs/39162292672), **PASS**.
Le runner télécharge les CSV IMU originaux du vrai dataset SIMUL (source figée
`ee7eb4e89bf33a17cafb4268207b802773427493`), et compile les classes du
vrai moteur PDR Java de l'autre branche au commit
`f8228aa7acbc453001d623c537bd8d7f0a9bc176`.
L'outil Java `tools/campaigns/java/PdrStepTrace.java` instrumente uniquement les
timestamps de pas acceptés par **la même** méthode `PdrEngine.acceleration` ;
il ne remplace ni ne réécrit le moteur.

**Preuve produite :** 3 participants, 29 563 instants capteurs, 9 placements
corps × séquence, annotations de pas indépendantes des entrées moteur. F1 par
intervalles observé de **0,797 à 0,974**. Surestimation du comptage en poche
arrière jusqu'à **+43,7 %** ; détecteur à améliorer avant qualification.
Ces résultats ne mesurent **ni trajectoire 2D, ni erreur métrique de distance,
ni précision ARCore du Galaxy S21**. Détail dans
`docs/campaigns/REAL_SIMUL_JAVA_PDR_20261018.md`.

Le petit rapport JSON, **sans données brutes**, est archivé également dans
`SLAM_BENCHMARKS/04_REPORTS` du compte Drive vérifié :
https://drive.google.com/file/d/1wkbHUY19Iut2r6qe3zpgIwPVCYpZtCTh/view

### Fichiers finalement concernés

- **Créés par ce chantier :** `tools/campaigns/android_pdr_bridge.py`,
  `tools/campaigns/simul_real.py`, `tools/campaigns/java/PdrStepTrace.java`,
  `tools/campaigns/tests/test_android_pdr_bridge.py`,
  `tools/campaigns/README_JAVA_PDR.md`,
  `docs/campaigns/REAL_SIMUL_JAVA_PDR_20261018.md`,
  `docs/coordination/validation-java-pdr-bridge.md` et
  **`.github/workflows/real-pedestrian-java.yml` (nouveau workflow dédié)**.
- **Fichiers partagés Android non modifiés :** `MainActivity.java`,
  `AndroidManifest.xml`, menus DATA, `PdrEngine.java`,
  `PdrReplay.java`, `docs/DEVELOPMENT_COORDINATION.md`
  et workflows préexistants. Seul le **nouveau** workflow CI est ajouté ;
  les définitions CI historiques sont préservées.
- La PR #7 reste isolée et ne prétend pas intégrer le module Java Android
  ni les tâches B1–B8 ; ces chantiers doivent conserver leur propre branche.

### Prochaine étape

Après que tous les jobs CI du **SHA final** ont réussi, fusionner cette
passerelle autonome dans `feature/slam-explorer` (jamais en force, et sans
fusionner aveuglément la branche PDR). Réévaluer plus de personnes et la
course, puis collecter la preuve réelle du S21 avec capteurs STEP_DETECTOR,
références indépendantes et rapports d'erreur métrique.
