# VECTOR VIO — expériences reproductibles, critères d'acceptation et limites

Dernière révision : 2026-10-18. Aucun pourcentage d'achèvement de la roadmap ne peut remplacer ces éléments de preuve.

## Artefacts opérationnels

- `tools/quality_report.py` lit le sidecar `_tracking_quality.csv` et produit une synthèse JSON de suivi ARCore (états et cadence). **Aucune erreur de position n'est calculée sans référence indépendante**.
- `tools/datasets/catalog.json` référence ADVIO, EuRoC, TUM VI, GOOSE, TOMD et RUGD, avec provenance/licence et limitations. `tools/datasets/dataset_inspector.py` inspecte les fichiers déposés localement, sans aucun téléchargement automatique, et ne prétend pas benchmarker.
- `GeoAlignment` ajuste yaw+translation sur paires GPS/VIO, `PoseFixSynchronizer` apparie strictement des timestamps sur un même segment, `GeoFusionTracker` produit des états explicites et un sidecar `_geo_alignment.csv`. Une excellente RMS résiduelle **ne prouve pas** un alignement vrai si les GPS ont un biais commun.
- `StorePlannerActivity` est un MVP entièrement manuel/offline : plan schématique, passages confirmés, produits associés à des rayons, itinéraire sous contrainte de passages et optimisation partielle. Le plan affiché à l'ouverture est **fictif** et doit être remplacé.
- `IntersectionAdvisor` annonce seulement un **changement de direction de la polyline GPX** ; il ne détecte pas un croisement réel. `TraceGuide` existait déjà avant cette itération et n'indique qu'un retour dans le segment VIO continu courant.
- `TrailAppearanceBaseline` calcule une proposition par ressemblance couleur/luminosité avec le sol visible, sans apprentissage. Elle n'est **ni une segmentation sémantique validée**, ni branchée aux images ARCore. `VisionTriggerPolicy` borne les appels expérimentaux à 10/minute, sans posséder la caméra.

## B1 — scènes et tests à effectuer physiquement

**Terrain sans danger seulement.** Filmer vers sol caillouteux, herbe texturée, chemin forestier, sol uniforme éclairé, neige plate sûre, visibilité artificiellement limitée en lieu contrôlé. Ne jamais s'engager dans une vraie situation de danger pour tester l'application.

Pour chaque scène : marche lente, marche normale, course douce si surface adaptée; téléphone pointé vers le sol puis horizon; répétitions 3 fois; longueur/angles mesurés indépendamment (ruban ou relevé géodésique, pas le GPS du même téléphone comme vérité). Exporter le ZIP pour chaque session.

```bash
python3 tools/quality_report.py path/to/vector_vio_XXXX_tracking_quality.csv --scene forest_ground --out reports/forest_ground.json
```

Critères de revue : nombre de pertes de tracking, fraction d'échantillons TRACKING, coupures >2 s, fréquence effective et comparaison distance/virages à une mesure indépendante. L'échantillonnage ~1 Hz ne capture pas toutes les microcoupures. Ne pas confondre `±n m` du GPS avec un rayon d'erreur VIO.

## B2 — recalage et synchronisation

Vérifier sur le S21 le décalage réel entre `Frame.getTimestamp()` et `SystemClock.elapsedRealtimeNanos()`. Si non compatible, la fusion doit signaler `GEO CLOCK UNKNOWN` et rester désactivée : ne pas corriger arbitrairement un offset. Enregistrer le segment, l'âge GPS et les données brutes en plus des coordonnées candidates.

Parcours de référence : boucle et deux chemins parallèles à espacement connu, au minimum trois séances, comparées à plusieurs repères géographiques externes indépendants. Étudier cas de GPS multipath avec biais persistants, couverture forestière, départ imprécis, altitude/change of level et interruption ARCore. Le candidat de l'alignement ne doit pas servir seul à décider le bon chemin.

### Limites de l'estimateur

Alignement rigide plan tangent WGS84 local, yaw + translation, sans covariance ARCore ni dérive variable. Une correction globale sur une trajectoire courbe de longue durée peut être inadéquate. Le test « common bias » démontre seulement que le problème est non observable sans autre source absolue. L'export expérimental est **diagnostique**, jamais une mesure cadastrale.

## B3 — GPX et intersections

Tests sur : boucle, figure-huit, deux branches parallèles rapprochées, épingles, piste grossière avec peu de points, itinéraire réellement absent du fichier GPX. Vérifier qu'`AMBIGUOUS` reste visible et qu'aucune flèche ne prétend reconnaître un chemin réellement inconnu. Le nouvel indice d'angle n'utilise que la géométrie de la trace importée.

## B4 — vision et ML

Ordre d'étude : (1) chronométrer `TrailAppearanceBaseline` sur images annotées avec PC et S21 ; (2) sélectionner une classe « trail/traversable » compatible avec les taxonomies GOOSE/TOMD/RUGD ; (3) entraîner/quantifier un modèle TFLite et mesurer IoU, rappel et erreurs de traversabilité séparément par surface et météo ; (4) seulement si gain démontré, connecter à la caméra avec un budget thermique/énergie strict et repli sans guidage.

L'absence de chemin visible doit produire « inconnu ». Ne pas transformer la détection de sol en instruction de marche sécurisée. Des jeux de données de robot peuvent être très éloignés des images à hauteur de smartphone.

## B5 — abstraction technique

Conserver l'orchestrateur ARCore et son thread GL tant qu'un `TrackingEngine` réellement branché et testé ne le remplace pas. `GeoFusionTracker` et `StoreRoutePlanner` sont des services pur Java, tests JUnit indépendants de l'interface Android; ils ne prouvent pas encore une architecture à moteurs VIO interchangeables. Benchmarks EuRoC/TUM VI destinés à un **moteur de recherche externe** : ARCore SDK ne sait pas directement lire leur séquence brute comme un capteur réel.

## B6 — test réel de magasin

Avec accord du magasin, dessiner manuellement 10–20 zones et 20 produits, saisir les liaisons praticables et comparer la longueur estimée du plan à une promenade de référence. Ne pas filmer personnes/étiquettes sans base légitime. Tester renumérotation de rayons, nœuds non connectés, articles inconnus et chemin bloqué. Les sauvegardes locales ne sont pas encore des cartes ARCore relocalisables entre visites.

## B7 — retour sur ses pas

Test à pied sur route dégagée, visibilité normale, avec perte de suivi simulée et réelle, sans obstacle dangereux. Le retour n'est autorisé que dans le segment métrique courant; une coupure doit invalider toute direction qui reposerait sur la continuité perdue. Les obstacles changent même si la trace géométrique est correcte. Une exportation GPX n'est pas un dispositif de secours.

## Tests automatiques

```bash
python3 -m unittest discover -s tools/tests -v
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
bash tools/gps/run.sh app/build/reports/gps/trajectory.csv
./tools/simulation/run.sh app/build/reports/replay-a 3 default 15 1
./tools/simulation/run.sh app/build/reports/replay-b 3 reverse 15 1
```

Le workflow GitHub Actions exécute ces tests hors caméra réelle. Vérifier son dernier statut et ne pas assimiler un APK qui compile à une application fiable en neige, brouillard, forêt ou escalier.

## Jeux de données externes et licences

1. **ADVIO** : AaltoVision, marche et intérieurs, ARCore comparatif; **CC BY-NC 4.0** : attention usage commercial. https://github.com/AaltoVision/ADVIO ; https://zenodo.org/records/1476931
2. **EuRoC MAV** : ETH ASL, images+IMU calibrées, vérité de référence; plateforme drone, pas smartphone. https://projects.asl.ethz.ch/datasets/euroc-mav/
3. **TUM VI** : TUM, fisheye + IMU, vérité souvent limitée aux extrémités des séquences. https://cvg.cit.tum.de/data/datasets/visual-inertial-dataset
4. **GOOSE** : Fraunhofer, classes terrain RGB/NIR; jeu de données CC BY-SA 4.0. https://github.com/FraunhoferIOSB/goose_dataset
5. **TOMD** : Durham, sentiers off-road et illumination difficile; archives volumineuses CC BY 4.0. https://researchdata.durham.ac.uk/collections/r2dn39x159k
6. **RUGD** : segmentation hors-route, droits du jeu original à vérifier. https://github.com/dataset-ninja/rugd/blob/main/DOWNLOAD.md

Ne pas committer les archives volumineuses dans Git : données externes dans un Drive ou disque local avec sommes de contrôle et métadonnées, scripts reproductibles dans `tools/`, rapports et métriques résumés sous `app/build/reports/` ou artéfacts CI.
