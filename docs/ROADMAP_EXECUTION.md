# VECTOR VIO — Plan d'exécution / suivi factuel

**Révision du 18 octobre 2026** · Branche de travail `work/slam-roadmap-b1-b2-b8` · document de pilotage distinct de l'Atlas visuel.

## Lecture du pourcentage

Les valeurs mesurent **la réalisation du sous-livrable**, jamais la probabilité que l'algorithme soit juste ou la précision de géolocalisation. 0 % = non commencé; 15–30 % = analyse; 40–70 % = prototype; 80–95 % = tests/intégration partiels; 100 % = sous-livrable précis terminé (ex. spécification ou document, non garantie terrain). Branches et global = moyennes non pondérées; on ne déduit aucune décision de sécurité de ces moyennes. Toute livraison d'APK/validation sur S21 reste à réaliser.

## Tableau de bord

| Branche | Sujet | Priorité | Avancement | État |
|---|---|---|---:|---|
| B1 | Observabilité VIO en visibilité dégradée | P0 | **23 %** | Mesurer la perte de suivi réelle, pas une sécurité garantie |
| B2 | Recalage GPS ↔ VIO multi-points | P0 | **39 %** | Estimer yaw + translation entre VIO métrique local et GPS WGS84 |
| B3 | GPX: stabilité aux intersections | P1 | **27 %** | Ajouter VIO au moteur GPX probabiliste existant |
| B4 | Reconnaissance visuelle des sentiers | P2 | **17 %** | Segmentation déclenchée seulement au besoin |
| B5 | Architecture du noyau spatial modulaire | P1 | **34 %** | Séparer tracking, carte, navigation et présentation |
| B6 | Courses en supermarché | P2 | **23 %** | Rayons manuels, plan 2D, ordonnancement des achats |
| B7 | Retour sur ses pas et secours | P2 | **29 %** | Fil d'Ariane segmenté et instructions prudentes |
| B8 | Atlas vivant et suivi de preuves | P1 | **80 %** | Historique intégral, backlog auditable et versioning |

**Progression moyenne indicative : 34 %**. En 2026-10-18, seul B2 comporte un **nouveau prototype de code**; aucune nouvelle fonction de navigation de l'APK n'est déployée ou certifiée par ces pourcentages.

## Sous-tâches vérifiables et limites

### B1 — Observabilité VIO en visibilité dégradée (P0, 23 %)

**Cible :** Mesurer la perte de suivi réelle, pas une sécurité garantie.

| ID | Livrable | % | État vérifiable | Blocage/limite |
|---|---|---:|---|---|
| B1.1 | Matrice sol/arbres/neige/brouillard/course | **100 %** | Protocole consigné | Terrain sûr et conditions difficiles à contrôler |
| B1.2 | Télémétrie qualité / frames / rupture horodatées | **0 %** | Non développée | ARCore n'expose pas sa covariance complète |
| B1.3 | 3 répétitions par environnement sur S21 + vérité terrain | **0 %** | Pas de collecte terrain | Ne pas créer de risque météo |
| B1.4 | ATE/RPE/dérive, recouvrement et vitesse | **0 %** | Aucune mesure terrain | Vérité terrain indépendante requise |
| B1.5 | Interface d'état de fiabilité et fallback | **15 %** | Règles documentées uniquement | Statut TRACKING seul ne suffit pas |

### B2 — Recalage GPS ↔ VIO multi-points (P0, 39 %)

**Cible :** Estimer yaw + translation entre VIO métrique local et GPS WGS84.

| ID | Livrable | % | État vérifiable | Blocage/limite |
|---|---|---:|---|---|
| B2.1 | Contrat ENU/WGS84, horloge et segment | **100 %** | Décrit dans GeoAlignment et documentation | Plan tangent local; orientation de repère variable |
| B2.2 | Ajustement robuste en pur Java | **70 %** | GeoAlignment prototype ajouté | Non branché dans l'application Android |
| B2.3 | Tests bruit, outlier, biais commun | **65 %** | 5 tests JUnit écrits; smoke javac exécuté | CI Gradle et terrain non prouvés |
| B2.4 | Synchroniser GPS 1 Hz et VIO ~30 Hz | **0 %** | Non développé | Latence/horloge et interpolation bornée indispensables |
| B2.5 | Export WGS84 + diagnostic qualifié | **0 %** | Non développé | Ne jamais corriger odomètre silencieusement |
| B2.6 | Validation géographique sur points de référence | **0 %** | Non réalisé | Biais GNSS corrélé non observable |

### B3 — GPX: stabilité aux intersections (P1, 27 %)

**Cible :** Ajouter VIO au moteur GPX probabiliste existant.

| ID | Livrable | % | État vérifiable | Blocage/limite |
|---|---|---:|---|---|
| B3.1 | Auditer Viterbi 12 états existant | **80 %** | Analyse documentaire faite | GPX n'énumère pas sentiers voisins |
| B3.2 | Spécifier contrainte de déplacement VIO | **25 %** | Spécification seulement | Orientation locale et biais GPS |
| B3.3 | Intégrer plusieurs branches candidates | **0 %** | Non développé avec VIO | Conserver AMBIGUOUS |
| B3.4 | Tests figure-huit, épingles et branches | **30 %** | Tests GPS seul existants | Aucune validation de la fusion |
| B3.5 | UI de confirmation aux bifurcations | **0 %** | Non développée | Faux positifs dangereux |

### B4 — Reconnaissance visuelle des sentiers (P2, 17 %)

**Cible :** Segmentation déclenchée seulement au besoin.

| ID | Livrable | % | État vérifiable | Blocage/limite |
|---|---|---:|---|---|
| B4.1 | Comparer OpenCV / TFLite / fréquence événementielle | **85 %** | Cadrage documenté | Pas de mesure modèle |
| B4.2 | Constituer corpus terrain étiqueté | **0 %** | Aucune donnée annotée | Saisonnalité, vie privée |
| B4.3 | Prototype NNAPI/TFLite + baseline | **0 %** | Pas de code | Compatibilité matérielle à vérifier |
| B4.4 | Bench ms/frame, RAM, énergie et rappel | **0 %** | Pas de mesure S21 | Ne pas inventer un coût batterie |
| B4.5 | Déclencheur intersections + repli | **0 %** | Non développé | Neige peut rendre voie invisible |

### B5 — Architecture du noyau spatial modulaire (P1, 34 %)

**Cible :** Séparer tracking, carte, navigation et présentation.

| ID | Livrable | % | État vérifiable | Blocage/limite |
|---|---|---:|---|---|
| B5.1 | Inventaire des modules et limites | **100 %** | MODULES_AND_DATASETS existe | MainActivity gère ARCore directement |
| B5.2 | Contrats Pose/Map/Route/POI | **50 %** | Conception Atlas | Interfaces fonctionnelles absentes |
| B5.3 | InputSource / TrackingEngine ARCore | **0 %** | Non codé | Lifecycle GL et tampon borné |
| B5.4 | Services métier purs et indépendants | **20 %** | GeoAlignment prototype isolé | Aucune connexion au GUI |
| B5.5 | Non-régression modules + CI Android | **0 %** | Pas de nouveaux tests architecture | Validation S21 requise |

### B6 — Courses en supermarché (P2, 23 %)

**Cible :** Rayons manuels, plan 2D, ordonnancement des achats.

| ID | Livrable | % | État vérifiable | Blocage/limite |
|---|---|---:|---|---|
| B6.1 | Scénario de visite et saisie | **100 %** | Atlas rédigé | Autorisation de filmer / droit à l'image |
| B6.2 | Modèle des rayons/produits/POI | **15 %** | Schéma conceptuel | Pas de relocalisation inter-session |
| B6.3 | Éditeur plan et affectation produit | **0 %** | Non implémenté | Ergonomie du magasin |
| B6.4 | Graphe navigable et liste optimisée | **0 %** | Non implémenté | Connexions de passages inconnues |
| B6.5 | Essai réel puis OCR/scanner en second temps | **0 %** | Pas de test | Respect vie privée / péremption de plans |

### B7 — Retour sur ses pas et secours (P2, 29 %)

**Cible :** Fil d'Ariane segmenté et instructions prudentes.

| ID | Livrable | % | État vérifiable | Blocage/limite |
|---|---|---:|---|---|
| B7.1 | Définir retour local / GPX / export secours | **100 %** | Documenté | Aucune certification de sécurité |
| B7.2 | Réutiliser RouteHistory segmenté | **25 %** | Trajectoire déjà enregistrée | Gaps et dérive VIO |
| B7.3 | Affichage dernier point fiable | **0 %** | Non implémenté | Confiance limitée sans covariance |
| B7.4 | Traçage/export hors ligne | **20 %** | GPS/GPX déjà disponibles | Pas de mode sauvetage autonome |
| B7.5 | Tests sûrs avec pannes simulées | **0 %** | Non réalisé | Ne pas remplacer GPS/boussole/cartes |

### B8 — Atlas vivant et suivi de preuves (P1, 80 %)

**Cible :** Historique intégral, backlog auditable et versioning.

| ID | Livrable | % | État vérifiable | Blocage/limite |
|---|---|---:|---|---|
| B8.1 | HTML V3 conservant V1/V2 et discussions | **100 %** | Fichier HTML autonome généré | Édits navigateur locaux: exporter JSON |
| B8.2 | Plan séparé task-by-task chiffré | **100 %** | Roadmap local et GitHub | Pourcentages indicatifs, non performance |
| B8.3 | Commits docs/code sur branche dédiée | **0 %** | En cours à la publication | CI à vérifier |
| B8.4 | Smoke du prototype GeoAlignment | **100 %** | javac et harness locaux OK | JUnit CI Android non exécuté ici |
| B8.5 | Décisions, risques et preuves | **100 %** | Historique / inventaire | Mises à jour systématiques nécessaires |

## Ordre d'attaque et critères d'acceptation

1. **Porte 0 (non-régression) :** préserver app existante, exécuter testDebugUnitTest/lint/assembleDebug sur CI, tester capture et GPS. Nouveau GeoAlignment totalement isolé jusqu'à acceptation.
2. **B1 mesurable :** enregistrer sur Samsung S21 trois répétitions par scénario sûr (sol/arbres/texture faible/course); mesurer trous, frames distinctes, qualité apparente, dérive et batterie avec référence indépendante. Exclure conditions météo dangereuses.
3. **B2 prototype → intégration :** coordonnées ENU locales, SE(2) yaw+translation, ajustement pondéré robuste. Ajouter appariement des timestamps Android, interpolation bornée, diagnostic candidat/ambigu/invalidé et export WGS84 sans transformer l'odomètre. Tester l'erreur absolue indépendamment du GPS courant.
4. **B3 intégration GPX :** réutiliser le matcher existant, tester chemins parallèles/épingle/figure-huit, émettre AMBIGUOUS plutôt que claquer sur le mauvais chemin.
5. **B5 architecture :** extraire la frontière réelle ARCore → poses → services sans adapter fantôme; rester une seule APK avec modes distincts au début.
6. **B7/B6 MVP :** retour sur ses pas avec zones incertaines; courses par attribution manuelle rayon/produit et graphe avant OCR/ML. Tests de sécurité/consentement.
7. **B4 étude expérimentale :** baseline OpenCV vs segmentation TFLite/NNAPI locale; ne lancer vision qu'aux intersections ou en cas d'ambiguïté, si gain démontré. Mesurer ms/image, RAM, chauffe, Wh/h et rappel sur cas hors distribution.

## GeoAlignment — limites d'implémentation

L'objet pure Java `GeoAlignment` estime une rotation plane + translation à partir de **paires pose VIO / fixe GPS déjà synchronisées** et d'une précision GPS déclarée. Les observations sont bornées à 64 fixes, l'alignement redémarre sur changement de segment, les séries géométriquement incohérentes ou trop courtes ne sont pas projetées. `CANDIDATE` n'est **pas une estimation probabiliste de sécurité**. Les coordonnées GPS biaisées de façon commune peuvent produire un ajustement très cohérent mais faux de dizaines de mètres, comme le teste explicitement `commonGpsBiasRemainsUnobservable`. Aucune interpolation temporelle, vraie covariance, fusion avec le GPS/GPX Android, batterie ou validation S21 n'est dans ce commit.

La caméra ARCore et l'IMU ne voient pas nécessairement un chemin sous neige ou brouillard. Pointez vers une surface texturée proche peut maintenir des points visuels même si l'horizon est opaque, sans garantie en cas de surface uniforme/flou. 30 poses/s est une cible, non un contrôle des images d'entrée via VIEW 5/15/30.

## Différenciation et dangers

Google Maps, Komoot, OsmAnd et Organic Maps couvrent déjà de nombreux cas offline/GPX. L'avantage à démontrer est un **positionnement relatif robuste entre fixes GNSS, une aide aux croisements et une cartographie d'espaces sans plan**, pas une copie de carte routière. En recherche et secours, la trace du passé ne prouve ni la présence d'un chemin sûr ni la capacité d'y revenir : pas de promesse de navigation sans visibilité ni de précision centimétrique.

Références : [Atlas source](PRODUCT_ATLAS.md) · [GPS](GPS_NAVIGATION.md) · [FPS](POWER_PROFILES.md) · [Modules](MODULES_AND_DATASETS.md) · [Validation](VALIDATION.md).
