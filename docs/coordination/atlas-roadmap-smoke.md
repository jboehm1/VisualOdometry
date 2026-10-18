# Coordination — Atlas V5, feuille de route centrale et scénario fumée/pompiers

**Date de prise en compte : 19 octobre 2026.**

## Références vérifiées et consignes lues

- Branche d’intégration **`feature/slam-explorer`** au SHA **`5f0105effd4fb2fb66b7d23620189c9605576de7`** (état consulté avant modification).
- Fichier `AGENTS.md` sur cette branche, blob `9e56be8eb4a4d4575399b2324daa6fad4b05db7d`.
- Fichier `docs/DEVELOPMENT_COORDINATION.md` sur cette branche, blob `61b55fcea7c9a2e241735f0c17505ecc8721404b`. Il constitue un instantané ancien, **pas un verrou vivant**.
- `work/slam-roadmap-b1-b2-b8` = `51885d3747721f176c14083ccb391c0719691bac`, d’où sont lus l’Atlas V4 (blob `3cf3f9f052895dfb5487e3dfd1ff4597a552ba03`), le tableau B1–B8 de 41 tâches (blob `fc1d5b1e60d029229e54b7f9d163ff19ae06d573`) et `VECTOR_VIO_TASKS.json`.
- `feature/android-flp-comparison` = `ad48fdbdbf2dc789ca202a51edbdff5651fd7f4b`, PR #11 ouverte ; comparaison GPS direct / Google FLP, CI logicielle passée sur son SHA fonctionnel mais terrain S21 encore non mesuré.
- PDR `feature/camera-free-pdr` = `a98b8ccf8a1a63aab8b233b2aed2f59dfd509a80` ; campagnes `feature/three-validation-campaigns` = `9dbe7a26d16b57248b9f07fb03549e28ebc096a0`.
- **Notre branche isolée** : `docs/atlas-v5-roadmap-smoke` créée depuis l’intégration au SHA ci-dessus.
- **Attention GitHub :** branche par défaut `master` = archive historique de 2019, **pas** l’Android moderne. La feuille de route centrale courante appartient à `feature/slam-explorer` après intégration documentaire, et pourra être référencée depuis un petit index dans `master` sans changer le code historique.

## Contraintes comprises

1. Préserver intégralement l'Atlas V4 : sections 01–18, historique V1–V4, 41 sous-tâches et mécanismes d’édition/export locaux. La nouvelle version **V5** enrichit sans réécrire les anciennes décisions ; pourcentages B1–B8 sont un **instantané du chantier**, pas une qualification globale.
2. **Un scénario fumée dense / recherche pompier est un chantier de R&D à risque élevé, pas une fonction de secours.** Le GNSS est souvent indisponible en bâtiments ; caméra RGB/ARCore peut perdre toute texture ; PDR inertiel dérive, surtout en rampant, montant des escaliers et portant de l’équipement ; FLP peut fournir des données très biaisées ou périmées. L'imagerie thermique, UWB, radar, baromètre multi-étage et balises sont des **pistes matérielles externes** à étudier et non des fonctions existantes dans le S21. Pas de navigation de sauvetage ni guidage vers sortie garanti ; validation seulement en simulation et site d’entraînement non dangereux, jamais en intervention.
3. Roadmap **centrale** en `docs/ROADMAP_CENTRAL.md` (et JSON structuré) couvrant chantiers live, B1–B8, PDR, A/B FLP, campagnes, intégration et nouvelle piste S. Les 41 tâches B1–B8 détaillées restent conservées et référencées sur leur branche source. Indiquer statut par preuve et revue humaine, sans inventer de pourcentages.
4. **Fichiers partagés concernés :** `docs/DEVELOPMENT_COORDINATION.md` est consulté mais ne sera pas réécrit. `app/src/main/java/dev/jboehm/vectorvio/MainActivity.java`, `app/src/main/AndroidManifest.xml`, `.github/workflows/*`, menus DATA, PDR et GPX ne doivent pas être touchés dans ce chantier de documentation. `README.md` pourra recevoir seulement un lien de découverte après contrôle du SHA ; il est partagé, donc éviter autant que possible. Aucun changement aux tests/moteurs.
5. Synchronisation : source de vérité des statuts = SHA GitHub et CI explicitement vérifiés ; aucune supposition sur les travaux non poussés des autres chats. Une CI par branche ne valide pas l’APK réunie. Ne pas fusionner de code des branches expérimentales dans cette PR documentaire.

## Livrables et prochaine étape

- `docs/VECTOR_VIO_ATLAS_INTERACTIF_v5.html` : nouvel Atlas HTML autonome, enrichissement scénario fumée/pompier et GPS natif vs FLP, liens vers roadmap, conservant toutes les sections historiques.
- `docs/ROADMAP_CENTRAL.md` et `docs/ROADMAP_CENTRAL.json` : tableau unique de pilotage inter-chantiers avec pistes FLP et fumée, responsabilités, critères d’acceptation mesurables.
- `docs/coordination/atlas-roadmap-smoke.md` : présent accusé de coordination et mise à jour de livraison.
- PR **documentation seule** depuis `docs/atlas-v5-roadmap-smoke` vers `feature/slam-explorer`, tests de structure/JS HTML + validation JSON ; après revue CI, intégration documentaire sur branche Android active. **Ne pas fusionner automatiquement les chantiers PDR, B1–B8 ou FLP dans cette opération.**

**Prochaine étape** : créer la feuille de route centrale, l’Atlas V5, vérifier les liens/sections/syntaxe, publier PR, puis faire découvrir le document depuis la branche par défaut sans déplacer ou modifier la base historique 2019.
