# VECTOR VIO — Plan d’exécution et avancement vérifiable

**Date de référence : 18 octobre 2026 · État : lancement de développement, prototype expérimental.**

Ce document est distinct de [l’Atlas interactif](VECTOR_VIO_ATLAS_INTERACTIF_v3.html), qui conserve **l’historique des idées et choix**. **Les pourcentages sont des avancements d’exécution documentaire/code/tests, jamais des probabilités de précision ou de réussite.** Ils indiquent les artefacts vérifiables disponibles à cette date ; zéro signifie non commencé pour cette sous-tâche.

## Règle d’avancement

- **0 %** : non commencé ; **15–30 %** : cadrage/diagnostic et dépendances ; **40–70 %** : prototype partiel, validation manquante ; **80–95 %** : intégration/test effectués, validation limitée ; **100 %** : livrable précis terminé et vérifiable (n’implique jamais fiabilité terrain si la tâche portait sur la documentation).
- Le pourcentage de chaque branche est la **moyenne arithmétique arrondie de ses sous-tâches** ; le global est la moyenne des huit branches. Ces moyennes ne reflètent pas les risques critiques ni une pondération en effort.
- Un prototype isolé n’est **pas** une fonctionnalité accessible sur l’APK. Aucun test sur Galaxy S21 n’est réalisé ici. Les CI non vérifiées sont notées comme telles.
- Il n’existe aujourd’hui ni garantie centimétrique, ni intégration GPS↔VIO en direct, ni segmentation sentier, ni relocalisation inter-session.

## Synthèse

| Branche | Objectif | Priorité | Avancement indicatif | Critère de sortie | Risque principal |
|---|---|---|---:|---|---|
| **B1** Observabilité VIO en visibilité dégradée | Mesurer quand ARCore conserve/perd le suivi, sans confondre évaluation visuelle et sûreté. | P0 | **23 %** | Revue tests et preuve terrain spécifique | Pas de covariance ARCore directement disponible |
| **B2** Recalage GPS ↔ VIO multi-points | Aligner un segment VIO 2D avec des fixes GNSS incertains sans présenter une fausse certitude. | P0 | **39 %** | Revue tests et preuve terrain spécifique | Corrélations GPS, multipath, GPS systématiquement décalé |
| **B3** Suivi GPX robuste aux bifurcations | Réutiliser moteur GPX déjà présent, ajouter informations visuelles et inertielle sans plaquer aveuglément. | P1 | **27 %** | Revue tests et preuve terrain spécifique | Erreurs dangereuses aux bifurcations |
| **B4** Reconnaissance visuelle des sentiers | Évaluer d’abord le coût réel et le gain aux intersections, hors ligne. | P2 | **17 %** | Revue tests et preuve terrain spécifique | Impossible de reconnaître un chemin invisible |
| **B5** Noyau spatial modulaire et contrats | Réduire dépendances MainActivity/ARCore et permettre modes indépendants. | P1 | **34 %** | Revue tests et preuve terrain spécifique | Instrumentation sur téléphone toujours nécessaire |
| **B6** Courses et cartographie d’un supermarché | MVP sans IA: rayons saisis, passages tracés, liste triée, confidentialité. | P2 | **23 %** | Revue tests et preuve terrain spécifique | Consentement des tiers et précision multi-visites |
| **B7** Retour sur ses pas et fiabilité de navigation | Fil d’Ariane fidèle aux segments, suivi prudent, aucune promesse Search & Rescue certifiée. | P2 | **29 %** | Revue tests et preuve terrain spécifique | Ne doit jamais remplacer instruments de montagne |
| **B8** Atlas, traçabilité et reproductibilité | Versionner historique intégral, backlog détaillé, expériences et critères de preuve. | P1 | **100 %** | Revue tests et preuve terrain spécifique | Nécessite discipline de mise à jour |

**Avancement global moyen : 36 %** (indicateur organisationnel uniquement).

## Détail par tâche et sous-tâche

### B1 — Observabilité VIO en visibilité dégradée · P0 · 23 %

**Objectif :** Mesurer quand ARCore conserve/perd le suivi, sans confondre évaluation visuelle et sûreté.

| ID | Sous-tâche et livrable | Avancement | État réel / preuve | Limites et blocages |
|---|---|---:|---|---|
| B1.1 | Définir matrice forêt/brouillard/sol/neige/nuit, marche/course et posture caméra | **100 %** | Protocole documentaire défini; pas de résultats physiques | Conditions météo non contrôlées; sécurité terrain à organiser |
| B1.2 | Ajouter événements synchronisés FPS/qualité/ruptures et annotation de scènes | **0 %** | Instrumentation spécifique non ajoutée | ARCore expose peu de métriques internes; CSV actuel partiel |
| B1.3 | Collecter 3+ répétitions avec trajectoire étalon sur S21 | **0 %** | Aucune collecte dédiée | Ne pas tester seul en danger réel; équipement de secours |
| B1.4 | Quantifier ATE/RPE/dérive par texture, distance, mouvement | **0 %** | Pas de métrique terrain publiée | Vérité terrain indépendante nécessaire |
| B1.5 | Définir règles UI de perte de confiance et dégradation | **15 %** | Principes décrits, UI non branchée | Pas de covariance ARCore directement disponible |

### B2 — Recalage GPS ↔ VIO multi-points · P0 · 39 %

**Objectif :** Aligner un segment VIO 2D avec des fixes GNSS incertains sans présenter une fausse certitude.

| ID | Sous-tâche et livrable | Avancement | État réel / preuve | Limites et blocages |
|---|---|---:|---|---|
| B2.1 | Spécifier repères ENU/WGS84, yaw, timestamp, qualité et ruptures | **100 %** | Contrat rédigé et commenté dans module expérimental | Repères dépendants de l’orientation ARCore; projection locale |
| B2.2 | Implémenter estimateur robuste 2D par segment | **70 %** | Prototype pure Java GeoAlignment ajouté, smoke compilé | Pas encore lié au flux Android; aucun covariancier crédible |
| B2.3 | Tester bruit, gross outlier, mouvement insuffisant et biais commun | **65 %** | Scénarios synthétiques et assertions ajoutés | Exécution Gradle/CI à vérifier après push; biais non observable |
| B2.4 | Associer pose et fix par horloge monotone et interpolation bornée | **0 %** | Non connecté au runtime | GPS 1 Hz vs VIO ~30 Hz, latence, gaps et changement de repère |
| B2.5 | Intégrer à l’export WGS84 avec statut de qualité explicite | **0 %** | Exports actuels séparés | Éviter correction silencieuse du compteur VIO |
| B2.6 | Valider contre itinéraire géoréférencé de référence | **0 %** | Aucune campagne réelle | Corrélations GPS, multipath, GPS systématiquement décalé |

### B3 — Suivi GPX robuste aux bifurcations · P1 · 27 %

**Objectif :** Réutiliser moteur GPX déjà présent, ajouter informations visuelles et inertielle sans plaquer aveuglément.

| ID | Sous-tâche et livrable | Avancement | État réel / preuve | Limites et blocages |
|---|---|---:|---|---|
| B3.1 | Auditer Viterbi 12 états et ambiguïtés inter-branches existantes | **80 %** | Moteur et limites documentés dans GPS_NAVIGATION.md | Synthétique seulement; chemins externes au GPX inconnus |
| B3.2 | Définir fusion probabiliste des déplacements VIO dans les candidats GPX | **25 %** | Interfaces et contraintes spécifiées, pas de code | Heading VIO local et GPX géographique, biais GNSS |
| B3.3 | Implémenter N hypothèses et marge de confiance non trompeuse | **0 %** | Non implémenté pour GPS+VIO | Ne pas écraser AMBIGUOUS par une certitude fictive |
| B3.4 | Tester figure-huit, routes parallèles, dénivelé, sortie du GPX | **30 %** | Tests GPS pur existants, pas de tests fusion | Pas de réseau de sentiers, pas de qualification terrain |
| B3.5 | Alerter aux croisements et demander confirmation si nécessaire | **0 %** | UX GPX existe, pas d’alerte visuelle fusionnée | Erreurs dangereuses aux bifurcations |

### B4 — Reconnaissance visuelle des sentiers · P2 · 17 %

**Objectif :** Évaluer d’abord le coût réel et le gain aux intersections, hors ligne.

| ID | Sous-tâche et livrable | Avancement | État réel / preuve | Limites et blocages |
|---|---|---:|---|---|
| B4.1 | Définir baseline OpenCV, segmentation légère et politique événementielle | **85 %** | Comparatif d’approches et critères décrits | Résultat dépend fortement des données locales |
| B4.2 | Collecter échantillons étiquetés sentiers/sols/neige/carrefours | **0 %** | Aucun corpus terrain validé | Vie privée et biais de saison/environnement |
| B4.3 | Prototyper modèle TFLite/NNAPI et baseline classique | **0 %** | Non codé | Compatibilité délégation S21 à confirmer |
| B4.4 | Bench FPS, ms/frame, RAM, chauffe, Wh/h, précision et rappel | **0 %** | Aucun chiffre mesuré | Estimation théorique non substituable à test batterie |
| B4.5 | Déclencher vision sur faible ambiguïté GPS/embranchement, fallback | **0 %** | Politique proposée seulement | Impossible de reconnaître un chemin invisible |

### B5 — Noyau spatial modulaire et contrats · P1 · 34 %

**Objectif :** Réduire dépendances MainActivity/ARCore et permettre modes indépendants.

| ID | Sous-tâche et livrable | Avancement | État réel / preuve | Limites et blocages |
|---|---|---:|---|---|
| B5.1 | Inventorier modules, dépendances, limites de SceneMapper/HUD | **100 %** | Inventaire repo présent | MainActivity reste monolithique |
| B5.2 | Définir PoseSample/Segment/MapSession/RouteGraph | **50 %** | Contrats conceptuels et architecture définis | Aucune API d’exécution unifiée |
| B5.3 | Extraire TrackingEngine/InputSource et adapter ARCore réel | **0 %** | Non implémenté | ARCore lifecycle/GL thread, cadence et backpressure |
| B5.4 | Isoler navigation GeoAlignment/GPX/UI via modèles neutres | **20 %** | GeoAlignment pure Java candidate, pas de wiring | Ne pas dégrader odomètre/explorateur |
| B5.5 | Tests contrats et non-régression + CI Android | **0 %** | Existing CI pour ancien flux uniquement | Instrumentation sur téléphone toujours nécessaire |

### B6 — Courses et cartographie d’un supermarché · P2 · 23 %

**Objectif :** MVP sans IA: rayons saisis, passages tracés, liste triée, confidentialité.

| ID | Sous-tâche et livrable | Avancement | État réel / preuve | Limites et blocages |
|---|---|---:|---|---|
| B6.1 | Définir parcours utilisateur, rayons, articles et corrections | **100 %** | Scénario détaillé dans Atlas | Droits de filmer en magasin, cartes temporaires |
| B6.2 | Modèle magasin/sessions/étagères/POI/graphes versionnés | **15 %** | Schéma conceptuel seulement | ARCore snapshots ne sont pas des cartes de relocalisation |
| B6.3 | Créer saisie manuelle rayon + produit et éditeur plan 2D | **0 %** | Non implémenté | Ergonomie, changements de disposition |
| B6.4 | Construire graphe de passages et optimiser ordre de liste | **0 %** | Non implémenté | Aisles et passages praticables à vérifier |
| B6.5 | Tester sur vrai petit magasin, puis seulement OCR/code-barres | **0 %** | Non testé | Consentement des tiers et précision multi-visites |

### B7 — Retour sur ses pas et fiabilité de navigation · P2 · 29 %

**Objectif :** Fil d’Ariane fidèle aux segments, suivi prudent, aucune promesse Search & Rescue certifiée.

| ID | Sous-tâche et livrable | Avancement | État réel / preuve | Limites et blocages |
|---|---|---:|---|---|
| B7.1 | Définir trois sous-modes: retour local, GPX extérieur, secours/export | **100 %** | Cas et avertissements documentés | État de scène mutable, météo, neige et obstacles |
| B7.2 | Exploiter RouteHistory sans relier les gaps ou repères incompatibles | **25 %** | Trajectoire segmentée déjà enregistrée, moteur retour absent | Drift VIO après réinitialisation et orientation dérivante |
| B7.3 | UX dernier repère fiable, dégradation, confirmation manuelle | **0 %** | Non implémenté | Absence covariance ARCore; signaux heuristiques |
| B7.4 | Mode hors ligne/export secours et test perte caméra/batterie | **20 %** | GPS offline/GPX existent, mode secours non développé | Pas de couverture, GPS peu précis, panne de batterie |
| B7.5 | Tests sur parcours sûr avec erreurs injectées, échec honnête | **0 %** | Non réalisé | Ne doit jamais remplacer instruments de montagne |

### B8 — Atlas, traçabilité et reproductibilité · P1 · 100 %

**Objectif :** Versionner historique intégral, backlog détaillé, expériences et critères de preuve.

| ID | Sous-tâche et livrable | Avancement | État réel / preuve | Limites et blocages |
|---|---|---:|---|---|
| B8.1 | Préserver V1/V2 et intégrer réflexions dans HTML V3 | **100 %** | Atlas V3 produit localement, V1/V2 conservées | Modifications locales du navigateur requièrent export JSON |
| B8.2 | Produire plan distinct avec avancement par sous-tâche | **100 %** | ROADMAP_EXECUTION.md produit | Avancement mesuré par preuves, pas par temps estimé |
| B8.3 | Publier code/docs sur branche GitHub dédiée | **100 %** | Commits confirmés sur work/slam-roadmap-b1-b2-b8 | CI et revue de code restent à vérifier séparément |
| B8.4 | Tests hors-ligne Java du prototype de recalage | **100 %** | javac + smoke déterministe exécutés localement | Ni tests instrumentés ni test de précision réelle |
| B8.5 | Établir décisions et journal de preuves/limitations | **100 %** | Historique et réserves conservés dans Atlas | Nécessite discipline de mise à jour |

## Ordre d’exécution avec portes de validation

1. **Porte 0 — préserver le fonctionnement existant :** isoler le code nouveau, garder ARCore/GPS/CSV/GPX inchangés ; build/test JUnit/Android et APK vérifiés par CI ; contrôle humain sur S21.
2. **Porte 1 — B1** : établir benchmark terrain instrumenté, jeux de conditions lisibles, chemins sûrs ; ne pas réclamer de test en brouillard dangereux.
3. **Porte 2 — B2** : alignement robuste en pur Java (prototype), puis association temporelle réelle fix↔pose, puis intégration app en lecture seule, puis vérité terrain ; conserver la sortie GPS brute et le statut incertain.
4. **Porte 3 — B3** : intégrer la contrainte de déplacement au faisceau GPX déjà présent ; conserver plusieurs hypothèses, ne jamais forcer une branche sans preuve.
5. **Porte 4 — B5** : refactoriser l’interface engine après tests ; une seule APK multi-modes tant que l’architecture n’est pas stabilisée.
6. **Porte 5 — B7/B6** : MVP retour puis courses sans IA, tests utilisateurs et relecture sûreté/vie privée.
7. **Porte 6 — B4** : segmentation de sentier en événement, après dataset, baseline, mesures réelles de batterie et gain sur branches ambigües.

## Alignement GPS + VIO — contrat de première implémentation

`GeoAlignment` prend **uniquement des paires déjà synchronisées** de poses VIO métriques locales et de fixes GPS WGS84 horodatés sur l’horloge monotone. Un seul repère continu de tracking par estimateur ; un changement de segment réinitialise l’alignement. Il ajuste le SE(2) (yaw + translation, échelle métrique fixe) dans un plan tangent local ENU en utilisant un ajustement pondéré robuste avec perte de Huber simplifiée ; cela fonctionne sur distance courte, pas pour de la topographie longue portée ou au voisinage des pôles. Son statut `CANDIDATE` signifie une compatibilité géométrique grossière entre les mesures acceptées, **pas une probabilité de localisation ni une précision absolue certifiée**. Les grosses dérives GNSS communes peuvent rester totalement indétectables, même après de nombreux points. Les mouvements insuffisants et les séries incohérentes produisent des états non projetables.

**Non inclus** : synchronisation à 1 Hz GPS vs ~30 Hz VIO, interpolation orientée, nouvelle contrainte GPX, fusion probabiliste/covariance, interface Android, support multi-sessions, alignement au nord magnéto, GPS altitude, disponibilité camera sous brouillard. **Prochaine preuve** : replay CSV horodaté et relevé GNSS/VIO sur trajet avec point(s) géoréférencé(s) indépendants.

## Protocole B1 — observabilité et batterie

| Conditions | Regarder | Consigner | Critère de résultat |
|---|---|---|---|
| Sol pierreux/feuilles visible à 0,5–1 m | sol, puis horizon | FPS poses distinctes, trous, segments, distance | Comparer au chemin mesuré |
| Neige homogène/surface lisse | sol | état TRACKING/PAUSED, pertes, reprise | Ne pas extrapoler pendant l’absence de suivi |
| Arbres épars + visibilité latérale | horizon/sol | vitesse, texture, interruptions | Répéter même parcours et comparer |
| Marche, course, rotations et éclairage faible | posture contrôlée | fps, flou observé, dérive, consommation | Ne pas conclure sur « FPS minimum » sans données |
| GPS précis / sous forêt / biais GNSS simulé | parcours balisé | raw GPS, précision, VIO, offsets estimés | Tester ambiguïtés de branches et erreur géographique réelle |

Durée et énergie : comparer ARCore 30 poses/s avec VIEW 5/15/30 **sans confondre affichage et caméra** ; lancer segmentation séparément, comparer puissance/thermique mesurées. Aucun mA, W ou pourcentage de batterie n’est chiffrable sérieusement avant mesure sur S21.

## Limites de sécurité, confidentialité, commercialisation

- **Pas d’usage de secours certifié** : un fil d’Ariane n’affirme pas que le terrain reste praticable ; batterie/caméra/GNSS peuvent échouer ensemble. Le navigateur doit proposer arrêt/repères fiables, jamais instruction sûre inventée.
- **Magasin / visages / étiquettes** : traitements locaux, minimisation des images, information des tiers et règles du magasin avant toute capture.
- **Benchmark concurrentiel** : Komoot, OsmAnd, Organic Maps, Google Maps couvrent déjà navigation/GPX/offline à divers degrés ; la différenciation doit être prouvée sur un problème que ces produits ne résolvent pas.
- **Technique** : ARCore fournit la pose et le statut mais pas de covariance publiquement exploitable ; sans repère géographique fiable, tout recalage demeure un candidat.

## Sources et liens internes

[Architecture](ARCHITECTURE.md) · [Modules](MODULES_AND_DATASETS.md) · [GPS/GPX](GPS_NAVIGATION.md) · [Énergie et FPS](POWER_PROFILES.md) · [Atlas initial](PRODUCT_ATLAS.md) · [Protocole de validation](VALIDATION.md). Le fichier HTML V3 constitue la vue exhaustive narrative ; ce document est la vue de pilotage et de preuves.