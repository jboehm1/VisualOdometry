> **Chantier parallèle lancé le 19/10 :** `feature/gpx-visual-depth-fusion` (PR #25) bifurque au SHA GPX `179f395acc69318e7859dc8a311acb311af256a6`. Fichiers communs modifiés : `MainActivity.java`, `SceneMapper.java`, `GpxArAndroidTest.java`, `DemElevationTest.java`, documentation GPX. Ajouts isolés : `GpxDepthOcclusion.java`, tests et tâche F06 `docs/GPX_AR_MULTI_CIRCLE_FUSION.md`. Respecte les menus DATA et manifest existants; **aucune fusion** vers `feature/slam-explorer`. Priorités : GPX affichable après verrouillage sans capture, mode Raw Depth facultatif/non bloquant, corriger CI DEM, revoir CI et APK exact. L'évaluation terrain demeure bloquante (pas d'affirmation de précision).\n\n# Mise à jour — finalisation logicielle, 19 octobre 2026

> **Statut postérieur au texte historique ci-dessous** : code GPX ancré `1920c6e17c88844df99d26c56c0ca902b856e82b`, [Android CI 39317910882](https://github.com/jboehm1/VisualOdometry/actions/runs/39317910882) verte (163 tests), [Three Campaigns 39317910825](https://github.com/jboehm1/VisualOdometry/actions/runs/39317910825) vertes, APK artifact 12218742148 (SHA256 `22b86224112e1ea4aec13cf808756e02720ce8f8e046fd138e4f39745d5c40ff`). Les phrases « CI en attente » et ancien SHA APK ci-dessous sont des références **historiques**. Détails : [gpx-ar-release-1920c6e1.md](gpx-ar-release-1920c6e1.md). Pas de qualification terrain; PR #24 non fusionnée.\n\nBranche `feature/gpx-ar-guidance`, intégration auditée `ab577a14b29261f2047503b2432e47ceb97e06ac`, départ `8641432`. Aucun autre module fusionné. Les preuves APK/CI ci-dessous sont **historiques**, antérieures à cette itération; la nouvelle CI sera consignée après compilation.

- Référence métrique GPX distincte de l’odomètre; inverse additive AnchorCoordinates/AnchoredOdometry, sans modification des mesures.
- GpxRibbonAnchors : portions 6 m, 32 références maximum, 2 créations par présentation, cleanup et masque en échec. SceneMapper réutilise la géométrie ancrée GPU/Canvas.
- Wgs84LocalFrame pour GeoAlignment et RouteIndex; densification dérivée 25 m, limites explicites, source inchangée.
- MainActivity/SceneMapper/HudView : options direction/distances/départ-arrivée/POI locaux/restant, persistance; tous les choix masquent seulement l’affichage.
- Export poses CSV étendu métrique/révision; outils Java/Python de rejeu du ZIP, sans retraitement vidéo/IMU/ARCore. CI archive une fixture déclarée synthétique. Guide : [GPX_AR_REPLAY.md](../GPX_AR_REPLAY.md).
- 41 tests Java ciblés, 12 Python passés localement. Compilations Android/lint/tests globaux à consigner après CI; aucune précision terrain certifiée.

Risques de coordination : préserver menus DATA, exports existants et séparation poses/VIEW; GeoAlignment à réunir explicitement avec PR #3; aucun whole-file ours/theirs. Les CSV métriques vides ne doivent pas être remplacés par le monde brut. Ne pas présenter la fixture CI comme un test réel du S21.

---

# Handoff — GPX dans la caméra

19 octobre 2026. Branche isolée `feature/gpx-ar-guidance`; PR **#24**, contre `feature/slam-explorer` : https://github.com/jboehm1/VisualOdometry/pull/24 . **Aucune fusion dans l’intégration exécutée.**

## SHA et preuves

- Base d’intégration lue et préservée : `ab577a14b29261f2047503b2432e47ceb97e06ac`.
- **Code de l’APK testé : `37eda6d71308748956fffe7f3ef94b424b6a18eb`.** Le commit documentaire contenant ce handoff peut être ultérieur; vérifier app/ identique à ce SHA.
- CI Android : https://github.com/jboehm1/VisualOdometry/actions/runs/39305523845 : tests JUnit (153, zéro échec), lintDebug, assembleDebug et assembleRelease, régression GPS et deux replays générés initial/inverse réussis.
- Exécution locale ciblée : 29 JUnit Java (19 nouveaux moteur/projection/journal + 10 régressions sol/ruban) réussis, 7 tests Python bruit réussis. Régression GPS existante : 30 seeds, 0 échec, 60 runs tenus à part; erreur progression synthétique max RMSE 8,592559 m, aucune inférence de précision téléphone.
- Test de rendu réel Android Canvas sous Robolectric natif : fixture `gpx_ar_synthetic_canvas.png` dans les rapports de tests. C’est une fixture, pas une image de caméra/S21.
- **Aucun test physique S21, aucune calibration d’incertitude et aucune précision absolue/consommation certifiée.**

## Instructions et coordination prises en compte

AGENTS.md, DEVELOPMENT_COORDINATION.md, ROADMAP_CENTRAL.md, coordination/android-location-flp.md et LOCATION_FL_PILOT.md lus depuis la base. Travaux B1–B8 examinés au SHA `51885d3747721f176c14083ccb391c0719691bac` sur `work/slam-roadmap-b1-b2-b8`.

Reprise ciblée de `GeoAlignment.java` et `geo/PoseFixSynchronizer.java`, avec inverse et correction de longitude dateline. Pas de fusion globale PR #3; `GeoFusionTracker` n’est pas dupliqué. Lors de la future réunion avec B1–B8, préserver les extensions de GeoAlignment et vérifier la convention **AR x/−z → rotation horizontale → est/nord**, y vers le haut. Cette itération remplace le modèle sphérique par une projection horizontale WGS84 ellipsoïdale ECEF/ENU; préserver cette évolution lors de la réunion avec B1–B8.

PR #3 B1–B8, #5 PDR et #17 recherche restent des périmètres indépendants au dernier audit. Relever leurs nouvelles têtes avant intégration. Aucun choix global ours/theirs. Préserver toutes les actions DATA, activités et exports, puis tester le SHA combiné.

## Comportement livré

- DATA → GPX AR : import GPX borné via parseur existant, sélection d’un segment, stockage atomique privé, activer/pause, AUTO/GPS/FUSED, inversion, reset et suppression.
- Une source absolue pour le moteur. AUTO tente Google Fused et se replie sur GPS natif si absent/erreur ou sans fix frais pendant 12 s. Aucun gain FLP garanti; Play Services déjà dépendance de la base.
- Appariement sans extrapolation, horloges originales, file de fixes bornée, rejets de doublons/ancien/mock/qualité; raw conservé séparément. `ArPoseClock` vérifie SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME et utilise getAndroidCameraTimestamp; Frame.getTimestamp ne sert pas comme horloge GPS. Source inconnue : mode géographique masqué.
- Inverse GPX→monde AR, fenêtre spatiale 30 m, cap de 512 arêtes et 2048 subdivisions de projection. Ruban bleu continu GPU ou Canvas sur plans ascendants observés uniquement; sélection du sol bas plausible, pas de hauteur caméra utilisée pour inventer un sol.
- Progression/matching/flèche horizontale estimée réutilisent GpsNavigator/RouteMatcher sans forcer le recalage sur le GPX. Flèche absente si cible/alignement/matching inutilisables.
- Resets aux pauses, pertes AR, segments et changement provider; stale masque le placement. L’odomètre, la cadence poses visée et les profils VIEW restent indépendants.
- Dataset ZIP : CSV fixes complets, GPX sélectionné par révision et altitude/datum UNKNOWN, poses monde et correspondance des deux horloges à cadence poses, diagnostics séparés. MP4 uniquement si capture armée avant START/RESUME.

## Fichiers partagés modifiés

- MainActivity.java : entrée DATA sémantique, import, lifecycle, hooks poses et export asynchrone.
- SceneMapper.java, WorldOverlayRenderer.java, HudView.java : projection et rendu GPX/direction distincts de la trace locale.
- GroundTrail.java : même sélecteur de sol refactorisé en fonctions scalaires pour éviter les allocations dans la boucle; 10 tests sol/ruban existants préservés.
- SessionBundle.java : README interne de l’export uniquement; sélection des fichiers inchangée.
- .github/workflows/android.yml : ajoute les 7 tests Python bruit. Pas de nouveau job ni de changement de cible pose/VIEW; validation de branche par PR.
- **Aucun changement au manifeste, app/build.gradle, Odometer ou aux moteurs gps/*.** AnchorCoordinates et AnchoredOdometry exposent seulement l’inverse worldFromMetric; algorithme de mesure intact.

Modules propres : ArPoseClock, GpxGroundProjector, location/LocationSource et AndroidLocationSource, gpxar/ArRouteGuide, RouteIndex, GpxArRouteStore, GpxArJournal; tests dédiés et fixture Canvas.

## Limites et prochaine étape

Lire [plan](../GPX_AR_GUIDANCE_PLAN.md), [25 tâches](../GPX_AR_TASKS.json) et [guide téléphone](../GPX_AR_MANUAL_TEST.md). Les pourcentages portent sur les sous-critères, pas la qualification production.

Restent : L04 mesures GPS/FLP avec référence indépendante; F03 calibration corrélation/biais/incertitude; R02 qualification physique longue des ancres/raccords et corrections sans sauts; R04 recalage manuel point connu; terrain/depth/étages et Q02 qualification extérieure, course et énergie. Pas de VPS, DEM, occlusion ou garantie centimétrique. Les seuils du recalage sont des heuristiques documentées, jamais un deadband de translation de l’odomètre.

Incidents corrigés pendant validation : ambiguïtés d’imports Android/Google détectées par compilation; test UI initial par réflexion référençant GL10 absent du harnais, remplacé par clic réel DATA. Aucun test désactivé. Les tests synthétiques ne sont pas présentés comme des essais sur téléphone.
