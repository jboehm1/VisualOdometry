# VECTOR VIO — Plan d’exécution / preuve d’avancement (itération 2)

**Date : 18 octobre 2026** · Branche `work/slam-roadmap-b1-b2-b8` · PR #3 en brouillon.

**Règle impérative :** 100 % signifie qu’un **sous-livrable précis** est réalisé ; ce n’est jamais une garantie d’exactitude. Les colonnes sont des moyennes non pondérées. Nous **n’avons pas** réalisé les campagnes réelles du S21 ni de vérification indépendante GPS/ARCore. Il serait mensonger de mettre tous les sujets à 100 % avant ces étapes.

## Synthèse des 8 branches et des 41 sous-tâches

| Branche | Fonction | Priorité | Avancement | Limite principale |
|---|---|---|---:|---|
| **B1** | Observabilité VIO en visibilité dégradée | P0 | **54 %** | Aucune covariance ARCore ni qualification sécurité |
| **B2** | Recalage GPS ↔ VIO multi-points | P0 | **75 %** | Corrélations GPS, multipath, GPS systématiquement décalé |
| **B3** | Suivi GPX robuste aux bifurcations | P1 | **66 %** | Ce n’est pas une intersection reconnue visuellement |
| **B4** | Reconnaissance visuelle des sentiers | P2 | **47 %** | Non branché à la caméra ARCore, car pertinence non démontrée |
| **B5** | Noyau spatial modulaire et contrats | P1 | **65 %** | Pas de test instrumenté sur téléphone ni seconde implémentation VIO |
| **B6** | Courses et cartographie d’un supermarché | P2 | **68 %** | Autorisation magasin et utilisateur requis |
| **B7** | Retour sur ses pas et fiabilité de navigation | P2 | **72 %** | Aucune campagne réelle ou certification |
| **B8** | Atlas, traçabilité et reproductibilité | P1 | **100 %** | Maintien systématique à poursuivre |

**Avancement indicatif global : 68 %**. B8 est un livrable de documentation/traçabilité à 100 %, pas une certification technique.

### Avancées de cette itération

- B1 : télémétrie `_tracking_quality.csv` à ~1 Hz; résumé `tools/quality_report.py` + tests.
- B2 : `PoseFixSynchronizer` / `GeoFusionTracker` raccordés au flux `MainActivity` + CSV candidat GNSS/ENU ; *alignement encore non validé sur S21*.
- B3 : avertissement au prochain virage géométrique GPX ; suivi GPS Viterbi préexistant conservé ; aucun chemin détecté.
- B4 : baseline sans ML `TrailAppearanceBaseline` et `VisionTriggerPolicy` avec plafond de calcul ; pas de segmentation fiable ni caméra branchée.
- B5 : nouveaux services purs Java + tests, sans prétendre avoir extrait un moteur ARCore interchangeable.
- B6 : nouvelle activité Android COURSES, plan schématique 2D, passages manuels, Dijkstra + amélioration 2-opt.
- B7 : retour existant `TraceGuide` reconnu dans l’inventaire et nouveaux tests de rupture de segment.
- B8 : Atlas visuel V4, rapport distinct, JSON traçable, catalogue de 6 datasets et protocole de validation.

## Détail exact des tâches, preuves et obstacles

### B1 — Observabilité VIO en visibilité dégradée (54 %)

**Objectif :** Mesurer quand ARCore conserve/perd le suivi, sans confondre évaluation visuelle et sûreté.

| ID | Tâche | % | État vérifiable | Limite / reste à faire |
|---|---|---:|---|---|
| B1.1 | Définir matrice forêt/brouillard/sol/neige/nuit, marche/course et posture caméra | **100 %** | Protocole documentaire défini; pas de résultats physiques | Conditions météo non contrôlées; sécurité terrain à organiser |
| B1.2 | Ajouter événements synchronisés FPS/qualité/ruptures et annotation de scènes | **85 %** | Instrumentation Android _tracking_quality.csv à ~1 Hz; analyseur Python avec 3 tests | Pas de mesures S21 ni observation des micro-coupures <1s |
| B1.3 | Collecter 3+ répétitions avec trajectoire étalon sur S21 | **0 %** | Protocole sûr documenté, aucune mesure sur S21 | Téléphone et vérité terrain indépendante requis |
| B1.4 | Quantifier ATE/RPE/dérive par texture, distance, mouvement | **30 %** | Outil quality_report.py calcule fractions tracking, périodes de latence et cadence | ATE/RPE toujours impossibles sans référence indépendante |
| B1.5 | Définir règles UI de perte de confiance et dégradation | **55 %** | HUD ARCore TRACKING/LOST existant + nouvel état GEO et événements | Aucune covariance ARCore ni qualification sécurité |

### B2 — Recalage GPS ↔ VIO multi-points (75 %)

**Objectif :** Aligner un segment VIO 2D avec des fixes GNSS incertains sans présenter une fausse certitude.

| ID | Tâche | % | État vérifiable | Limite / reste à faire |
|---|---|---:|---|---|
| B2.1 | Spécifier repères ENU/WGS84, yaw, timestamp, qualité et ruptures | **100 %** | Contrat rédigé et commenté dans module expérimental | Repères dépendants de l’orientation ARCore; projection locale |
| B2.2 | Implémenter estimateur robuste 2D par segment | **90 %** | GeoAlignment robuste + GeoFusionTracker connectés à MainActivity Android | Alignement 2D par segment seulement; biais GPS commun invisible |
| B2.3 | Tester bruit, gross outlier, mouvement insuffisant et biais commun | **95 %** | JUnit géolocalisation, sync, biais commun; CI Gradle précédente réussie | Pas de qualification GNSS terrain |
| B2.4 | Associer pose et fix par horloge monotone et interpolation bornée | **85 %** | PoseFixSynchronizer interpolation bornée et vérification timebase dans flux GL + fix GNSS | Compatibilité clock et latence sur S21 non mesurées |
| B2.5 | Intégrer à l’export WGS84 avec statut de qualité explicite | **80 %** | _geo_alignment.csv inclus dans SessionBundle ZIP; état explicite et coordonnées candidates | Export diagnostique non garanti; non injecté dans GPX/suivi |
| B2.6 | Valider contre itinéraire géoréférencé de référence | **0 %** | Aucune campagne réelle | Corrélations GPS, multipath, GPS systématiquement décalé |

### B3 — Suivi GPX robuste aux bifurcations (66 %)

**Objectif :** Réutiliser moteur GPX déjà présent, ajouter informations visuelles et inertielle sans plaquer aveuglément.

| ID | Tâche | % | État vérifiable | Limite / reste à faire |
|---|---|---:|---|---|
| B3.1 | Auditer Viterbi 12 états et ambiguïtés inter-branches existantes | **100 %** | Code RouteMatcher audité: beam Viterbi borné existant, diagnostics ambiguïté | Sans graphe externe, ne peut connaître les sentiers non-GPX |
| B3.2 | Définir fusion probabiliste des déplacements VIO dans les candidats GPX | **45 %** | GeoFusionTracker mesure un déplacement cohérent, contrat de matching documenté | Service GPS GPX indépendant non alimenté par VIO pour l’instant |
| B3.3 | Implémenter N hypothèses et marge de confiance non trompeuse | **55 %** | Beam 12 candidats GPS déjà existant; reconnaissance VIO non ajoutée à ce beam | Aucune élimination sûre des branches parallèles |
| B3.4 | Tester figure-huit, routes parallèles, dénivelé, sortie du GPX | **55 %** | Régressions GPS préexistantes en CI + test IntersectionAdvisor | Cas GPS+VIO fusion non exécutés sur terrain |
| B3.5 | Alerter aux croisements et demander confirmation si nécessaire | **75 %** | GpsRoutesActivity montre le virage GPX à venir avec avertissement; AMBIGUOUS reste visible | Ce n’est pas une intersection reconnue visuellement |

### B4 — Reconnaissance visuelle des sentiers (47 %)

**Objectif :** Évaluer d’abord le coût réel et le gain aux intersections, hors ligne.

| ID | Tâche | % | État vérifiable | Limite / reste à faire |
|---|---|---:|---|---|
| B4.1 | Définir baseline OpenCV, segmentation légère et politique événementielle | **100 %** | Comparatif et politique VisionTriggerPolicy événementielle, hors caméra | Aucune réduction de batterie mesurée |
| B4.2 | Collecter échantillons étiquetés sentiers/sols/neige/carrefours | **15 %** | Catalogue jeux de données GOOSE/TOMD/RUGD et validation de présence | Aucune séquence annotée téléchargée; licences à vérifier |
| B4.3 | Prototyper modèle TFLite/NNAPI et baseline classique | **40 %** | TrailAppearanceBaseline pur Java + tests synthétiques; aucun NN quantifié | Proposal par couleur ≠ segmentation sémantique; hors APK caméra |
| B4.4 | Bench FPS, ms/frame, RAM, chauffe, Wh/h, précision et rappel | **15 %** | Baseline compilée, tests fonctionnels; méthodologie benchmark documentée | Aucune mesure FPS/RAM/thermique ni vérité annotation |
| B4.5 | Déclencher vision sur faible ambiguïté GPS/embranchement, fallback | **65 %** | VisionTriggerPolicy 4s cooldown, 10/min, arrêt caméra indisponible | Non branché à la caméra ARCore, car pertinence non démontrée |

### B5 — Noyau spatial modulaire et contrats (65 %)

**Objectif :** Réduire dépendances MainActivity/ARCore et permettre modes indépendants.

| ID | Tâche | % | État vérifiable | Limite / reste à faire |
|---|---|---:|---|---|
| B5.1 | Inventorier modules, dépendances, limites de SceneMapper/HUD | **100 %** | Inventaire repo présent | MainActivity reste monolithique |
| B5.2 | Définir PoseSample/Segment/MapSession/RouteGraph | **65 %** | Contrats concrets PoseFixSynchronizer / GeoFusionTracker / StoreRoutePlanner | Pas de PoseSample/TrackingEngine complet |
| B5.3 | Extraire TrackingEngine/InputSource et adapter ARCore réel | **0 %** | Extraction TrackingEngine/InputSource encore absente | MainActivity gère directement le GL/ARCore |
| B5.4 | Isoler navigation GeoAlignment/GPX/UI via modèles neutres | **80 %** | GeoFusionTracker pur Java alimenté par MainActivity, planner magasin indépendant | Flux GPS ROUTES reste séparé du VIO |
| B5.5 | Tests contrats et non-régression + CI Android | **80 %** | Nouveaux JUnit + Python unittest + Android build/quality tests CI exécutés | Pas de test instrumenté sur téléphone ni seconde implémentation VIO |

### B6 — Courses et cartographie d’un supermarché (68 %)

**Objectif :** MVP sans IA: rayons saisis, passages tracés, liste triée, confidentialité.

| ID | Tâche | % | État vérifiable | Limite / reste à faire |
|---|---|---:|---|---|
| B6.1 | Définir parcours utilisateur, rayons, articles et corrections | **100 %** | Scénario détaillé dans Atlas | Droits de filmer en magasin, cartes temporaires |
| B6.2 | Modèle magasin/sessions/étagères/POI/graphes versionnés | **70 %** | Graphe spatial Node/Edge/product + persistance texte SharedPreferences | Pas de carte multi-session versionnée ni relocalisation AR |
| B6.3 | Créer saisie manuelle rayon + produit et éditeur plan 2D | **80 %** | StorePlannerActivity : éditeur manuel, vue schématique 2D, liste et sauvegarde locale | Pas de UX test utilisateur ni capture rayon automatique |
| B6.4 | Construire graphe de passages et optimiser ordre de liste | **90 %** | StoreRoutePlanner Dijkstra sur arêtes validées + optimisation gloutonne/2-opt + JUnit | Optimisation heuristique pas preuve d’optimal global |
| B6.5 | Tester sur vrai petit magasin, puis seulement OCR/code-barres | **0 %** | Scénario test terrain détaillé, non exécuté | Autorisation magasin et utilisateur requis |

### B7 — Retour sur ses pas et fiabilité de navigation (72 %)

**Objectif :** Fil d’Ariane fidèle aux segments, suivi prudent, aucune promesse Search & Rescue certifiée.

| ID | Tâche | % | État vérifiable | Limite / reste à faire |
|---|---|---:|---|---|
| B7.1 | Définir trois sous-modes: retour local, GPX extérieur, secours/export | **100 %** | Cas et avertissements documentés | État de scène mutable, météo, neige et obstacles |
| B7.2 | Exploiter RouteHistory sans relier les gaps ou repères incompatibles | **85 %** | TraceGuide EXISTAIT déjà : segment continu seulement; nouveaux tests de retour/croisements | Tracking gaps réinitialisent retour; drift monde inconnu |
| B7.3 | UX dernier repère fiable, dégradation, confirmation manuelle | **70 %** | HUD RETURN/REJOIN/RETURN STOPPED existant et visible | Confusion possible sans validation UX et véritable incertitude |
| B7.4 | Mode hors ligne/export secours et test perte caméra/batterie | **70 %** | Export et récupération GPS/GPX offline existants ; ZIP diagnostics accru | Pas de « sécurité secours » et géorecalage GPS non validé |
| B7.5 | Tests sur parcours sûr avec erreurs injectées, échec honnête | **35 %** | JUnit TraceGuide ajouté; protocole terrain sécurité rédigé | Aucune campagne réelle ou certification |

### B8 — Atlas, traçabilité et reproductibilité (100 %)

**Objectif :** Versionner historique intégral, backlog détaillé, expériences et critères de preuve.

| ID | Tâche | % | État vérifiable | Limite / reste à faire |
|---|---|---:|---|---|
| B8.1 | Préserver V1/V2 et intégrer réflexions dans HTML V3 | **100 %** | Atlas HTML V4 préservant toutes sections historiques V1–V3 + journal V4 | Édits navigateur restent locaux jusqu’à export |
| B8.2 | Produire plan distinct avec avancement par sous-tâche | **100 %** | Roadmap séparée à 41 tâches recalculées automatiquement depuis JSON | Pourcentages descriptifs pas objectifs qualité |
| B8.3 | Publier code/docs sur branche GitHub dédiée | **100 %** | Branche work/slam-roadmap-b1-b2-b8 et PR #3 draft | Revue humaine et éventuelle fusion restent volontaires |
| B8.4 | Tests hors-ligne Java du prototype de recalage | **100 %** | CI Gradle et tests Python/JUnit + catalogue données vérifié | CI pas terrain S21 |
| B8.5 | Établir décisions et journal de preuves/limitations | **100 %** | Historique et limites de tous modes conservés et mis à jour | Maintien systématique à poursuivre |

## Jalons / définition de 100 % à chaque étape

**Jalon logiciel** : compilation Gradle, tests JUnit/Python et script GPS/simulation au vert sur le commit exact + publication d’un APK téléchargeable. Ce jalon ne valide pas les capteurs sur téléphone.

**Jalon matériel** : trois répétitions contrôlées sur S21 par scénario, avec export capteurs et mesures d’énergie/thermique. Nécessite un appareil ARCore physique et un opérateur.

**Jalon positionnement GPS** : comparaison à points géoréférencés indépendants, plusieurs sites et multipath, avec métriques de couverture et erreurs incluant les échecs. Un GPS biaisé partout n’est pas une vérité terrain.

**Jalon sécurité/navigation** : les états ambigu/lost doivent suspendre les instructions, pas affirmer un sentier invisible. Le retour sur trace n’est pas un dispositif de secours certifié.

**Jalon ML** : tester sur GOOSE/TOMD/RUGD réellement téléchargés et une collecte propre au téléphone, mesures IoU/rappel par classe et profil batterie/latence; aucune affirmation à ce stade.

## Commandes et sources de preuve

```bash
python3 -m unittest discover -s tools/tests -v
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
python3 tools/datasets/dataset_inspector.py advio /chemin/vers/donnees
python3 tools/quality_report.py /chemin/session_tracking_quality.csv --scene sol_rocailleux
```

[Protocoles de terrain et datasets](EXPERIMENTS_AND_ACCEPTANCE.md) · [Atlas visuel V4](VECTOR_VIO_ATLAS_INTERACTIF_v4.html) · [GPS](GPS_NAVIGATION.md) · [Architecture](MODULES_AND_DATASETS.md).
