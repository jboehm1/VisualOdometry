# Coordination — Atlas opportunités et niches, 21/10/2026

- **Base inspectée** : branche d'intégration `feature/slam-explorer`, SHA `3d251b8e06536a972b88bcf01bd34912ea5ceabd` (HEAD communiqué dans PR #47, snapshot au démarrage) ; `AGENTS.md` et `docs/DEVELOPMENT_COORDINATION.md` consultés. Vérifier à nouveau avant merge.
- **Point de départ isolé** : PR #47 `work/pdr-vio-evidence-20261021`, commit `fbf07c385a9251639ace9905158fe04441e8b8bb`, contenant les améliorations Camera2, PDR et Basalt Android expérimental.
- **Branche d'écriture** : `docs/market-opportunities-atlas-20261021`. PR à cibler sur `work/pdr-vio-evidence-20261021` pour comparaison réduite; pas sur `master` Colab ni fusion non testée sur intégration.
- **Portée exclusive** :
  1. `docs/VECTOR_VIO_ATLAS_INTERACTIF_v5.html` (fichier partagé, sections 01–22 et bibliothèque localStorage strictement préservées) : ajout section 23 structurée, sommaire, filtres recherche/domaine/priorité, copie de nouvelles idées dans le gestionnaire existant; corrections des affirmations désormais obsolètes sur la disponibilité Basalt/Android.
  2. `docs/USE_CASE_OPPORTUNITY_LANDSCAPE_20261021.md` : recherches, sources fournisseurs officielles, limites, ~25 niches et expérimentation honnête.
  3. Le présent fichier de coordination.
- **Dépendances/concurrence** : d'autres branches peuvent modifier l'Atlas ou `ROADMAP_CENTRAL.md`. Aucun `MainActivity`, `AndroidManifest`, settings, export, outil de benchmark, moteur ni workflow modifié. Ne jamais résoudre un conflit en écrasant tout le document canonique ; ne pas écraser les idées personnelles localStorage.
- **Séparation exigée** : capacité revendiquée par fournisseur ≠ benchmark indépendant; priorité proposée ≠ marché validé; outil Basalt compilé ≠ qualification sur S21; ARWorldMap Apple ≠ capacité ARCore de notre application; aucune promesse 10 cm ou incendie/pompier.
- **Validation réalisée avant PR** : lecture état Git et fichiers; 25 objets catalogue, identifiants uniques, 32 balises section ouvrantes/fermantes, 3 balises style ouvrantes/fermantes, deux scripts séparés, liens internes explicites. Vérifier GitHub CI sur tête exacte après création PR.
- **Suite recommandée** : revue visuelle sur navigateur desktop/mobile de l'HTML brut (filtres, bouton « Ajouter », persistance JSON, impression), puis 8–12 entretiens, deux pilotes, et benchmarks QR seul / ARCore / Basalt avec vérité indépendante. L'interface du catalogue n'est pas un module Android.

## Continuation : recherche approfondie, design et tests navigateur

- PR de publication : **#48**. Branche source conservée, sans modification de moteurs VIO/PDR ni de paramètres Android.
- Atlas V5.3 : page d'accueil éditorialisée avec illustration vectorielle locale, navigation focus Marchés/Produit/Recherche/Développement/Tout, sommaire historique déroulant, notes de versions archivées **sans suppression**, ancienne bibliothèque d'idées conservée.
- Recherche : **35** hypothèses au total (25 initiales + 10 nouvelles); niches montage PV, réception carport/façade PV, travaux cachés avant fermeture, petit retrofit sans BIM, inspection locale en zone sensible, racks, gaines multiétages, dégâts des eaux, portes coupe-feu et actifs médicaux. Les solutions verticales OpenSpace Field (2026), Raptor Maps, Scanifly, PlanRadar, Dalux, DataXplor, DroneDeploy, QR/CMMS sont identifiées comme **concurrence existante**, non présentées comme un marché vide.
- Étude détaillée ajoutée : `docs/OPPORTUNITY_DEEP_DIVE_20261021.md`. Une capacité annoncée par un fournisseur n'est pas un score comparatif indépendant.
- UX : bibliothèque filtrée par texte/priorité/domaine, comparaison de deux niches (problème, solution minimale, hypothèse, obstacle, test) sans score fictif, bouton ajout aux idées locales, navigation clavier et responsive mobile.
- CI navigateur ajoutée : `.github/workflows/atlas-ui.yml` et `tools/atlas/test_atlas_ui.py`, Chromium/Playwright, captures desktop/mobile.
- **Validation mesurée** : workflow Atlas [39662903951](https://github.com/jboehm1/VisualOdometry/actions/runs/39662903951) SUCCESS sur SHA `b46dcef3e7c104b0f3deb2e8931ed801dab66714`, avec 4 tests couvrant filtres, comparaison, localStorage, liens historiques et largeur mobile.
- **Android CI de la précédente itération** [39662705406](https://github.com/jboehm1/VisualOdometry/actions/runs/39662705406) a trouvé un défaut de structure HTML : l'index déplacé avait perdu les liens historiques. Celui-ci a été restauré par `b46dcef3`; ne pas ignorer cet échec et vérifier une CI réussie sur le dernier SHA.
- **Amélioration ultérieure** : raccourcis en-tête vers des sections masquées initialement et multi-appartenance des catégories corrigés sur `e02cce1a474c82cd57ff2f31551bd6d1693d2cf1`, à revalider en CI.
- **Base a avancé indépendamment** : PR #47 observée à `a3de1dbe3c8cd7ecc907f53b0c48ed1dbcc603ca`. Son changement porte sur Android/native, non sur l'Atlas selon comparaison de commits consultée. Avant intégration finale, réconcilier la branche #48 avec la nouvelle base via merge fast-forward/merge commit non destructif, puis revalider.
- Recherche et prototype restent distincts des chiffres de précision : calibration native expérimentale, aucun benchmark S21/GT/ROI/entretiens métier produits ici.
