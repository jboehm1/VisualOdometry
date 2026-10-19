# Coordination — Atlas central : valeur du tracé précis, éclairage faible, décision adaptative

**Date : 19 octobre 2026. Branche isolée** : `docs/atlas-precision-value-adaptive-20261019`.

## Références effectivement consultées
- Branche d'intégration Android `feature/slam-explorer` **`98cba16e45de162434c1b09d408e80b431524a93`** ; source HTML `docs/VECTOR_VIO_ATLAS_INTERACTIF_v5.html` blob `3e2c18e372603febd72593df2d1f49294c4e267b`.
- `AGENTS.md` sur intégration (blob `9e56be8eb4a4d4575399b2324daa6fad4b05db7d`) et `docs/DEVELOPMENT_COORDINATION.md` (blob `a8a7bcec0ff95098bfafa4d823982d8811b3b5da`) lus avant la modification.
- `docs/ROADMAP_CENTRAL.md` (blob `318641d6fed466f9dbb5a3d754020b8a3a3a083c`) et JSON (blob `93e7dbfa92f5ed9db7b25b0b7302ed24383e83e5`) consultés.
- PR **#21** `docs/precision-usecases-lowlight-20261019`, head `e39ee5b83e7d0c00485e287b71171b7e9472b735`, CI réussie ; PR **#22** `docs/lowlight-bike-precision-usecases`, head `4d6d1df2a6c7f7d6ccc301ff1014677a8ebbf1cb`, CI en cours : **les deux modifient le Markdown/JSON central avec des idées proches**.
- PR #3 `work/slam-roadmap-b1-b2-b8` (SHA `51885d3747721f176c14083ccb391c0719691bac`), #5 PDR `feature/camera-free-pdr` (`5bfc07f19b751417dbb71e3a9d46af3b189a3cd3`), #17 fumée `feature/smoke-rescue-research` (`ac5f30133af9322ba2877daba0fff6eab1a12dfd`), toutes séparées.

## Portée et garde-fous
- **Un seul document visuel canonique modifié** : `docs/VECTOR_VIO_ATLAS_INTERACTIF_v5.html` (pas de nouvelle copie V6 qui concurrencerait la même source) ; ajouter à la fin des sections 20/21/22 *sans casser* sections 01–19, sommaire, éditeur d'idées, localStorage ni export/import JSON.
- **NOUVELLE section 20** : faible luminosité vélo/trail/forêt, mouvements/vibrations, nuit, modes adaptatifs **proposés mais non implémentés** dans l'APK. Distinguer fiabilité des sources vs précision absolue; ne pas interrompre artificiellement la session VIO ou inventer une continuité GPS.
- **NOUVELLE section 21** : bibliothèque de valeur organisée par **professions**, scénario, ce que « être passé là » apporte, condition de réussite, limites et priorité commerciale. Maintenance/intérieur/inspection comme MVP le plus réaliste, autres domaines industrie, tunnel, logistique, voirie/forêt, travaux, recherche, sports, secours uniquement R&D.
- **NOUVELLE section 22** : hypothèses de précision submétrique/10 cm **relative locale** vs absolue géoréférencée vs retour inter-session; protocole de références indépendantes, tests VIO vélo jour/nuit, métriques couverture/dérive, besoin de recalage d'ancres/fiducials/RTK ou cartographie durable. Bibliothèque d'idées : éléments catalogués réutilisables dans l'éditeur, conservation des idées existantes.
- **Fichiers partagés volontairement NON modifiés** : `MainActivity.java`, `AndroidManifest.xml`, Gradle, workflows Android, `README.md`, `docs/ROADMAP_CENTRAL.md` et `.json` (PR #21 et #22 en parallèle), `docs/DEVELOPMENT_COORDINATION.md`.
- Ajout permis d'un fichier de test exclusivement consacré au contrat Atlas et du présent accusé de coordination. Aucun code auto-switch en APK ajouté ici : documenter clairement ce qui est *projeté* et les garde-fous (staleness, confidence, no pose interpolation, GPS bias, locomotive modes marche/vélo).

## Fusion et validation
Publier PR documentaire dirigée vers `feature/slam-explorer`, tester HTML (sections conservées, insertion source métier, données cataloguées, JS valide) et CI existante sur SHA exact, puis fusionner **seulement la portée HTML + test + note**, sans écraser la roadmap parallèle. Mettre à jour la prise en compte si la branche centrale change.

**Handoff** : tenir l'ensemble de ces notes et contraintes à disposition des autres chats ; les gains 10 cm sont une hypothèse à prouver par capteurs/datasets/terrain, jamais une certification GNSS ou indoor.
