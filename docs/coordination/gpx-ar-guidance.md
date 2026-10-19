# Coordination — GPX en AR, placement et incertitude

19/10/2026. Branche dédiée `feature/gpx-ar-guidance`; base intégration inspectée `ab577a14b29261f2047503b2432e47ceb97e06ac`. Lire [plan et tâches](../GPX_AR_GUIDANCE_PLAN.md), [JSON](../GPX_AR_TASKS.json).

## Périmètre de cette livraison

Plan précis, contrats proposés, recherche primaire et contre-exemple synthétique reproductible. Aucun moteur Android GPX AR ajouté ici; aucun nouvel APK. Instructions lues : AGENTS.md, DEVELOPMENT_COORDINATION.md, ROADMAP_CENTRAL.md, coordination/android-location-flp.md et LOCATION_FL_PILOT.md de cette base.

État étudié : GPX 2D et trace locale AR déjà présents; pilote GPS↔FLP intégré mais pas source du navigateur; GeoAlignment/GeoFusionTracker/PoseFixSynchronizer de B1–B8 sur branche séparée `work/slam-roadmap-b1-b2-b8` SHA `51885d3747721f176c14083ccb391c0719691bac`. Réutiliser ce travail après revue ciblée, ne pas créer un deuxième moteur ou fusionner tout MainActivity.

Fichiers propres à cette livraison : docs/GPX_AR_GUIDANCE_PLAN.md, docs/GPX_AR_TASKS.json, docs/gpx-ar/noise-study.json, docs/coordination/gpx-ar-guidance.md, tools/gpx_ar/noise_study.py, tools/gpx_ar/tests/test_noise_study.py. Aucun fichier partagé Android, menu, manifeste, workflow ou roadmap centrale modifié.

## Risques d’intégration à garder visibles

PR #3 B1–B8, #5 PDR et #17 recherche restent des périmètres distincts au dernier audit. MainActivity et manifeste sont partagés; vérifier nouvelles têtes avant intervention. Conserver DATA GPS ROUTES, GPS↔FLP, PDR, replay, courses et exports. Ni fusion pleine ours/theirs ni réduction de cadence poses en changeant VIEW. Aucun filtre arbitraire de translation ajouté.

## Preuves et limites

7 tests hôte `python3 -m unittest discover -s tools/gpx_ar/tests -v` réussis; étude seed 20261019, 1000 essais par scénario, résultats versionnés. Vérification indépendante de la formule de variance par matrice de covariance complète et de l’indistinguabilité biais/translation.

Ces tests vérifient le modèle synthétique; ils ne valident ni FLP sur S21, ni le moteur Java GeoAlignment, ni la future ligne 3D. Pas de build Android exécuté pour ces seuls documents/scripts. Aucun changement de comportement de l’APK intégration. Le commit de livraison est celui ajoutant ce fichier (voir historique Git); toujours citer ce SHA pour reproduire les preuves.

Prochaine tâche : L01/L03, interface neutre et horloges, puis F01 réutilisation du recalage; L04 mesure physique reste nécessaire à la décision FLP finale. Après intégration, exécuter CI Android au SHA combiné et produire son APK; ne pas présenter une APK de la base comme contenant le nouveau GPX AR.
