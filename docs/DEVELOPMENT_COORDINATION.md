# VECTOR VIO — coordination des développements parallèles

> **GPX v0.13 · 20/10/2026 :** Branche isolée `feature/v013-gpx-world-horizon` base `3d251b8e06536a972b88bcf01bd34912ea5ceabd`. Fichiers partagés : MainActivity, HudView, SceneMapper, WorldGeometry, WorldOverlayRenderer, ArRouteGuide, RouteIndex, Settings*. Ajout d'un GPX lointain distinctement fictif en altitude et désactivable, extension du rayon à 90 m, épaisseur adaptative, repère de retour à la trace. Aucune perturbation de PDR/SLAM/GNSS mesure, ni fusion des branches #17/#21/#22. [Documentation](GPX_AR_WORLD_GUIDE_V013.md). CI + S21 obligatoires.

> **UI v0.12 · 20/10/2026 :** branche isolée `feature/v012-settings-screen`, base SHA `0eeeeb22a18b88cc6fb842ecea2556fb28754c74`. Modification partagée `MainActivity.java` (MENU + result dispatch + restore des préférences), `HudView.java` (IMU overlay state), `AndroidManifest.xml` et version Gradle. Nouveau `SettingsActivity` natif et modèles de texte; tests Robolectric et documentation. Préserver tous les imports GPX, PDR, COURSES, replay/export, géofusion, CI. Lancement SettingsActivity met en pause ARCore conformément à onPause; ne pas prétendre suivi continu sous les réglages. [Spec](SETTINGS_SCREEN_V012.md).

> **ACTUALISATION 20/10/2026 :** PR #3/#5/#24–30 fusionnées sur `feature/slam-explorer` (SHA `789e358cb988beb27d1a530b7ee2598e91681321`). PR #30 source historique `feature/release-v011-ui-gps-bike` pour modifications partagées `MainActivity.java`, `HudView.java`, `GpsMonitor.java`, `SettingsCatalog.java`, `app/build.gradle` et tests/docs. Sans écrasement du manifeste, PDR, GPX ou smoke. CI du code fusionné PASS sur PR #30 SHA `d6ebf843` à arbre identique, tests Android/3 campagnes revérifiés en branche intégration. Ticket #31 : debug signing volatile, possible désinstallation avec perte de données entre APK; sauvegarder avant upgrade. [Spécification](BIKE_GPS_UI_V011.md). Les tableaux 18–19/10 ci-dessous constituent des archives, pas la situation Git en direct.

> **ACTUALISATION 19/10/2026 — LIRE EN PREMIER :** [audit Git / consolidation et 3 PR actives](coordination/git-consolidation-20261019.md) et [roadmap centrale](ROADMAP_CENTRAL.md). Les états ci-dessous datent du 18/10 et ne constituent **pas** un verrou Git actuel. Depuis : PR **#11 GPS↔FLP fusionnée** dans `feature/slam-explorer` (9ff7d3a), PR **#1/#2 closes comme historiques**, PR **#18 testenv V3 fusionnée**, restent **#3 B1–B8 / #5 PDR / #17 fumée-R&D** en chantier parallèle. **Ne fusionnez pas de `MainActivity` ni de manifeste Android par écrasement.** Vérifier la tête Git et les CI du dernier SHA avant toute action.

État contrôlé le 18 octobre 2026. **Point d’entrée commun pour les chats travaillant sur ce dépôt.** Les SHA ci-dessous définissent l’instantané audité ; une nouvelle poussée nécessite un nouveau contrôle. Les travaux non poussés dans les autres environnements ne sont pas visibles ici.

## Branche d’intégration et périmètres

`feature/slam-explorer` est la branche d’intégration Android, auditée à `1b2c59ecf6b17ae9740881834d603f91472c25a9`. `master` reste le projet historique 2019 ; ne pas le confondre avec l’app actuelle. `backup/2019-original-colab` conserve cette base. PR #1 et #2 sont encore des brouillons historiques en chaîne, pas des releases publiées.

| Chantier / conversation | Branche, SHA audité | État vérifié | Limite et prochaine preuve |
| --- | --- | --- | --- |
| Live AR, carte, GPX, énergie | `feature/slam-explorer` · `1b2c59e` | Intégré ; Android + benchmark CI réussis au SHA exact | Précision course/S21, thermique et batterie non qualifiées |
| Test env — fondation benchmark | `feature/real-dataset-benchmark` · `6315552` ; PR #4 fusionnée | Code benchmark, catalogue, documentation et tests identiques à la base intégrée ; seule différence de workflow : déclenchement sur intégration ajouté | 14 tests hôte passés pendant cet audit ; ce sont des fixtures, pas une campagne complète réelle |
| Videoless options — PDR Android | `feature/camera-free-pdr` · `52f7832` ; PR #5 brouillon | Moteur Java, acquisition, écran sans caméra, export ZIP, replay même moteur ; CI Android/PDR/GPS/AR réussie | Fonctionnement foreground, longueur de pas et orientation téléphone/corps à qualifier ; pas de campagne OxIOD/RoNIN complète attestée |
| Slam dev — B1 à B8 | `work/slam-roadmap-b1-b2-b8` · `6c89a83` ; PR #3 brouillon | Géofusion diagnostique horodatée, qualité, indication virage GPX, planner manuel, prototypes vision, Atlas/tâches, évaluateur supplémentaire | CI réussie observée à `ef5c4bc`, pas encore prouvée ici au dernier `6c89a83` ; vision non reliée à la caméra ; géofusion candidate non injectée dans la distance/GPX |
| Test env — trois campagnes | `feature/three-validation-campaigns` · `5ee0a97` | Code de playback ARCore dans MainActivity ; baseline PDR Python et tests | Pas de PR ouverte ni de CI PR trouvée à ce SHA ; pas de preuve d’exécution complète A/B/C sur données réelles |

Les pourcentages de `ROADMAP_EXECUTION.md` / `VECTOR_VIO_TASKS.json` restent des estimations de sous-livrables sur leur branche, **pas un pourcentage de production readiness**, ni une preuve que le code est déjà intégré.

## Conflits réellement reproduits

Le script `tools/coordination/audit.py` a exécuté six `git merge-tree` sans modifier index, fichiers de travail ni références. Voir [instantané JSON](coordination/snapshot-20261018.json).

| Comparaison aux SHA audités | Résultat textuel |
| --- | --- |
| Intégration + PDR | Fusion propre isolément |
| Intégration + B1–B8 | Fusion propre isolément |
| Intégration + campagnes | Fusion propre isolément |
| PDR + B1–B8 | Conflits `MainActivity.java` et `AndroidManifest.xml` |
| PDR + campagnes | Conflit `MainActivity.java` |
| B1–B8 + campagnes | Conflit `MainActivity.java` |

**Cause concrète : les trois chantiers ajoutent chacun une action DATA au même index 9** (PDR, courses/planner, replay). Conserver uniquement une version du fichier ferait disparaître une fonctionnalité. Réunir les entrées, garantir une action unique pour chacune et tester toutes les actions après fusion. Une petite table d’actions stables est préférable aux indices partagés pour les prochains ajouts. Le manifeste final doit conserver les activités PDR et StorePlanner ainsi que les permissions nécessaires.

Une fusion textuelle propre ne prouve ni compilation, ni fonctionnement, ni précision. Aucun code des trois branches n’a été fusionné dans l’intégration par cet audit.

## Risques transversaux et décisions

1. **Deux PDR différents.** `tools/campaigns/imu_pdr.py` est une baseline Python à pas fixe/yaw d’un axe. L’APK PDR utilise `pdr/PdrEngine.java` et `tools/pdr/PdrReplay.java`. La campagne B doit identifier le moteur testé et exécuter le replay Java pour prétendre évaluer le module Android. Garder la baseline Python séparée et nommée.
2. **Plusieurs évaluateurs.** `tools/benchmark/bench.py` (rigide SE(3), RPE rotation/translation, couverture temporelle), `tools/pdr/evaluate.py` (contrat local EN), et `tools/eval/trajectory_metrics.py` (alignement yaw/translation par segment, couverture par échantillons) ne produisent pas des chiffres interchangeables. Définir un rapport canonique avec moteur/config/hash, horloge, repère caméra/IMU, unités, définition des segments, origine de vérité, alignement et couverture. Ne pas optimiser l’échelle ou l’horloge sur le jeu tenu à l’écart.
3. **Catalogues multiples.** `tools/benchmark/catalog.json`, `docs/PDR_DATASETS.json` et `tools/datasets/catalog.json` doivent garder des identifiants compatibles et différencier référencement, adaptateur, données obtenues, moteur exécuté et métriques terrain. Un catalogue n’est pas un téléchargement ni une campagne.
4. **Replay à vérifier avant intégration.** Le compteur `playbackFrames` / `playbackTrackedFrames` est incrémenté avant le rejet des timestamps dupliqués par `poseRate.accept` dans la branche campagnes. Déplacer le comptage des frames distinctes après ce contrôle pour une couverture non gonflée. Le dialogue de sélection combine `setMessage` et `setItems` : ajouter un test Android qui vérifie que la liste est effectivement attachée/visible et l’import accessible. Tester aussi EOF, reprise, import annulé et MP4 ordinaire refusé. Ces contrôles ne remplacent pas un vrai replay ARCore sur téléphone.
5. **Conserver l’invariant énergie/course.** Les profils VIEW ne doivent pas réduire la cible de lecture des poses de 30/s ; caméra ARCore configurée 30 FPS, mesures distinctes, file GL bornée, pas de deadband de translation. Les nouveaux diagnostics/replay ne doivent pas rebrancher la fréquence de mesure sur la fréquence d’affichage. Une cible n’est pas une garantie de débit.
6. **Géofusion diagnostique.** Conserver les coordonnées candidates et l’erreur de fit distinctes de la position mesurée. Un bon ajustement ne prouve pas l’absence de biais GPS commun. Ne pas confondre cap magnétique, nord vrai, orientation téléphone et direction de marche.

## Ordre d’intégration conseillé

1. Relever les nouveaux SHA et arrêter le mélange des modifications de `MainActivity`, manifeste et CI : une seule intégration à la fois, chacun restant propriétaire de son module.
2. Préparer PDR (#5) contre la dernière intégration : module bien isolé, tests existants ; vérifier sa portée foreground et son export. Rejouer CI sur le résultat combiné avant fusion.
3. Préparer les campagnes contre cette nouvelle base : conserver PDR dans DATA, ajouter replay avec action distincte, corriger/tester compteur et sélection ; créer une PR pour bénéficier de CI. Exécuter A moteur VIO externe, B Java PDR réel + baseline clairement séparée, C ARCore playback de l’APK ; garder leurs résultats séparés.
4. Réintégrer B1–B8 (#3), de préférence par lots revus : diagnostics geo/qualité et virages d’abord ; planner magasin/prototypes vision ensuite. Conserver toutes les activités/actions et exports ajoutés précédemment.
5. À chaque lot : tests hôte, JUnit/lint/APK, régressions PDR/GPS/AR applicables, smoke des menus/export. Après la réunion complète : tests d’interaction, puis un APK unique issu du SHA combiné. Les APK de branches séparées ne contiennent pas automatiquement les autres nouveautés.
6. Qualification commune S21 : immobilité, rotation pure, parcours mesuré, course, faible lumière, interruptions/permissions, exports, température/batterie ; preuves indépendantes pour distance/trajectoire. Aucun label « production validée » sur la seule base des CI vertes.

## Tableau de coordination actionnable

| ID | Priorité | Action / responsable de périmètre | État à cet audit |
| --- | --- | --- | --- |
| C01 | P0 | État central, SHA, conflits reproductibles — coordination | Réalisé |
| C02 | P0 | Réunir les trois actions DATA + manifeste — intégration avec les trois chantiers | À faire sur résultat combiné |
| C03 | P0 | PR et CI au SHA courant — chantier campagnes ; contrôle du dernier SHA B1–B8 | Manquant / à actualiser |
| C04 | P0 | Exécuter A/B/C avec identifiants de moteur et références indépendantes — Test env | Aucune preuve complète auditée |
| C05 | P1 | Unifier contrat de métriques/repères/temps/segments — benchmark + PDR + B1–B8 | À définir ; trois outils existants |
| C06 | P1 | Tester le chemin UI et le comptage du playback — campagnes | Risques source identifiés, test demandé |
| C07 | P1 | CI + smoke de l’ensemble réuni — intégration | À faire après C02 |
| C08 | P1 | Valider course et invariants VIEW/poses/export — live + campagnes | Tests synthétiques existants ; terrain restant |
| C09 | P2 | Harmoniser catalogues et avancement sans supprimer l’historique Atlas — tous | À faire |

## Reprendre le travail dans un autre chat

Lire ce document, puis les docs du module et l’état Git actualisé. Avant de modifier un fichier partagé, relever les branches concurrentes qui le touchent. Utiliser une branche/worktree propre. Ne pas réécrire l’historique partagé pour résoudre un conflit ; ne pas supprimer les fonctionnalités de l’autre branche en choisissant tout « ours/theirs ». Publier le SHA, ce qui est réellement exécuté, ce qui reste non testé et les fichiers partagés modifiés. Réexécuter l’audit après une nouvelle poussée :

```bash
git fetch origin '+refs/heads/*:refs/remotes/origin/*'
python3 tools/coordination/audit.py --out /tmp/vector-git-audit.json
```

Le script utilise uniquement les refs locales rafraîchies, fige leurs SHA au démarrage et ne télécharge aucune donnée de capteurs. Ce document n’installe pas de surveillance en arrière-plan et ne voit pas les fichiers non poussés dans les autres chats.

## Preuves consultées

- Intégration Android : https://github.com/jboehm1/VisualOdometry/actions/runs/39154915225
- Intégration benchmark : https://github.com/jboehm1/VisualOdometry/actions/runs/39154915252
- PDR Android + replay : https://github.com/jboehm1/VisualOdometry/actions/runs/39156725543
- B1–B8 au précédent SHA `ef5c4bc` : https://github.com/jboehm1/VisualOdometry/actions/runs/39157220336
- PR : https://github.com/jboehm1/VisualOdometry/pull/3 · https://github.com/jboehm1/VisualOdometry/pull/4 · https://github.com/jboehm1/VisualOdometry/pull/5

Tests exécutés pendant l’audit : 14 tests `tools/benchmark/tests`, 3 tests `tools/tests`, six simulations de fusion et contrôle d’équivalence des fichiers benchmark. Aucun nouveau benchmark réel ni essai physique n’a été exécuté par cet audit.
