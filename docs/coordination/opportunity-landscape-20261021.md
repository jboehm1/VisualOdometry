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
