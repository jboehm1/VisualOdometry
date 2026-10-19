# GPX en réalité augmentée — plan exécutable et suivi

19 octobre 2026. Branche `feature/gpx-ar-guidance`, base intégration `ab577a14b29261f2047503b2432e47ceb97e06ac`.

## Résultat visé et état réel

Importer un GPX, estimer son placement dans le repère local ARCore à partir de positions absolues et de poses caméra synchronisées, puis afficher un chemin 3D avec une précision annoncée qui corresponde aux preuves disponibles. Le téléphone peut bouger en hauteur et tourner sans entraîner le chemin. Le terrain ne doit pas être inventé au-delà de ce qui est connu.

**Implémentation expérimentale, itération 1 :** import GPX/segment et stockage atomique, providers AUTO/GPS/FUSED, appariement poses/fixes, inverse géographique→AR, fenêtre spatiale bornée, ruban bleu GPU/Canvas sur plans de sol observés, reset aux discontinuités, CSV brut/diagnostic et états visibles. Aucun changement à l’odomètre. 17 tests JUnit moteur Java/projection/journal réussis localement et 7 tests étude bruit; CI Android et téléphone restent à confirmer.

Le pilote A/B et le navigateur GPX 2D existants restent séparés. `GeoAlignment` et `PoseFixSynchronizer` sont repris de B1–B8; pas de fusion du reste de cette branche. L’adaptateur traite AR x/−z pour respecter la chiralité des repères, vérifiée sur courbes dans les deux sens.

Suivi machine : [GPX_AR_TASKS.json](GPX_AR_TASKS.json). Les pourcentages décrivent les sous-critères implémentés, pas la précision ou une qualification production. Les preuves et limites sont consignées par ID. Les tâches terrain restent à 0 tant que non exécutées.


## Décision localisation

Réutiliser le pilote FLP intégré et le type `location/LocationSample`; ne pas créer un deuxième comparateur. Créer un adaptateur `LocationSource` avec événements mesure/état, puis deux implémentations Android GPS natif et Google Play Services FLP. Préserver les API Java du moteur sans dépendance Google. Mode `AUTO` proposé : FLP si disponible et autorisé, sinon GPS natif; préférence explicite possible. La sélection effective dépendra des résultats L04, aucun gain garanti aujourd’hui.

FLP combine des signaux et gère un compromis qualité/énergie (GPS, Wi-Fi et autres selon appareil/configuration). C’est une dépendance Google Play Services, déjà présente dans l’app avec `play-services-location:21.4.0`; pas une nouvelle contrainte pour le cœur mathématique. Prévoir appareils sans Play Services, permissions approximatives, localisation coupée et mode hors réseau. Une requête haute précision ne garantit ni cadence ni précision. FLP ne garantit pas une odométrie continue en tunnel. Le pilote A/B actuel refuse certains cas; ce n’est pas encore le fallback de navigation.

GPS natif et FLP peuvent partager le même récepteur GNSS : ne jamais les traiter comme deux votes indépendants. Garder A/B pour mesurer; sélectionner une source pour le premier moteur. Si leur fusion simultanée est ensuite utile, justifier la prise en compte de leur covariance croisée. Ne pas transférer les seuils de rejet expérimentaux du pilote A/B dans l’odomètre ou supprimer le brut.

## Pourquoi le temps aide, et sa limite mathématique

Modèle simplifié : `z_i = R q_i + t + b + epsilon_i`. `q_i` est la position relative VIO, `R` la rotation horizontale, `t` le placement géographique et `b` un biais GNSS commun. La gravité fixe la verticale; un trajet suffisamment informatif aide à estimer le cap. Le GPX lui-même a une incertitude et une provenance.

Pour un point immobile, ou après compensation correcte du mouvement, un bruit indépendant, centré, d’écart type par axe sigma donne une erreur de moyenne sigma/sqrt(N). En mouvement, moyenner directement les latitudes/longitudes est faux : on ajuste la transformation sur des couples GPS–VIO horodatés. Ni VIO ni GPS ne sont supposés parfaits en production.

Mais remplacer `t` par `t + delta` et `b` par `b - delta` ne change aucune observation. Le biais commun et la translation absolue ne sont donc pas séparables dans ce modèle sans information supplémentaire. Même un résidu d’alignement quasi nul n’est pas une preuve d’exactitude géographique. Un décalage commun de 5 m déplace le placement du GPX; il ne rend pas forcément sa forme mauvaise. Des biais différents entre endroits peuvent aussi fausser le cap. À 20 m, 5 degrés d’erreur de cap représentent environ 1,75 m de décalage latéral.

Les erreurs GNSS peuvent être corrélées dans le temps et non gaussiennes. Pour le contre-exemple AR(1), la variance de la moyenne vaut `sigma² [N + 2 sum((N-k) rho^k)] / N²`. Le nombre de fixes n’est pas le nombre d’observations indépendantes. Des positions anciennes répétées n’ajoutent aucune information. Sans nouvelle référence absolue exploitable, attendre n’améliore pas la localisation absolue; la VIO/IMU peut prolonger le suivi relatif avec une dérive à caractériser.

Expérience reproductible : `python3 tools/gpx_ar/noise_study.py --out docs/gpx-ar/noise-study.json`. 1 000 essais par cas, graine fixe, N=10/100/1000, sigma=5 m PAR AXE, biais 0/5 m, corrélation 0/0,95. Le résultat est un contre-exemple synthétique avec VIO et cap idéaux, PAS un benchmark S21 ni une calibration du rayon d’incertitude Android. La couverture du cercle 95 % naïf montre le danger de l’hypothèse d’indépendance. Tests : `python3 -m unittest discover -s tools/gpx_ar/tests -v`.

Améliorer l’absolu reste possible si la géométrie/satellites changent, si un modèle de biais identifiable est justifié ou si une référence indépendante est disponible : point connu, appariement visuel VPS couvert, GNSS corrigé externe, carte fiable. Contraindre au GPX aide uniquement si l’hypothèse de bon chemin est correcte; deux chemins parallèles restent ambigus. Ne pas prétendre gagner de l’information en imposant la réponse attendue.

## Contrats techniques proposés

- `LocationSample` existant étendu seulement si nécessaire : latitude/longitude WGS84, altitude facultative + datum/source, précision déclarée séparée de la précision calibrée, horloge mesure monotone, heure arrivée, provider, mock/validité; conserver brut et causes de rejet.
- `TimedPose` : timestamp caméra et correspondance monotone vérifiée, segment, translation métrique, quaternion, tracking. Ne pas multiplier la pose par une matrice de projection pour calculer une distance. Rotation et translation restent séparées.
- `AlignmentEstimate` : transformation datée/versionnée, segment, statut observable/candidat, incertitudes horizontale/cap/verticale, dérive relative, âge, provenance et résidu de fit séparés. Ne pas présenter une probabilité calibrée sans Q02.
- Repères : WGS84 vers ENU local; convention explicite vers ARCore x/y/z et son y vertical; transformation rigide métrique et son inverse GPX→AR. Pas de scale fitting qui cacherait une erreur VIO. Changements d’origine/segment testés.
- `ProjectedRoute` : instantané immuable de points 3D finis, qualité de surface et sources. GPX `ele` facultatif; datum inconnu ne devient pas altitude ellipsoïdale. Les plans/depth donnent une surface LOCALE, pas la topographie lointaine. Aux ponts/escaliers, un GPX 2D ne suffit pas à choisir l’étage.
- Rendu : ruban continu GPU et fallback Canvas, parties locales bornées, travail hors boucle caméra, acquisition poses indépendante de VIEW. État incertain représenté par corridor/indication; ligne au sol seulement où surface et recalage sont utilisables. Seuils de rendu à calibrer sur erreurs réelles, pas de deadband arbitraire sur la translation.

Réutilisation : `gps/GpxIO`, `GpsRoute`, `RouteMatcher`, `GpsNavigator`; `WorldGeometry`/`WorldOverlayRenderer`; pilote `location/*`. Examiner puis intégrer uniquement les dépendances nécessaires de `GeoAlignment`, `geo/GeoFusionTracker`, `PoseFixSynchronizer` sur `work/slam-roadmap-b1-b2-b8` (`51885d3` à l’audit). Ne pas fusionner la totalité de PR #3 pour récupérer ces classes.

## Séquence, tâches et critères de sortie

Ordre pratique : A → L01/L03 → F01/F02 → L02 et H01/H02 → F03/F04/F05 → R01/R02/R03 → I01/I02 et R04 → Q01 → Q02 → Q03. L04 commence dès l’adaptateur disponible et bloque la décision de provider final. H03 suit H02. VPS est une option de référence absolue séparée, pas une condition obligatoire pour la forêt.

| ID | Tâche | Avancement | Dépendances | Acceptation vérifiable |
|---|---|---:|---|---|
| A01 | Auditer intégration et modules parallèles | 100 % | — | Lister GPX 2D, trail AR local, FLP A/B et GeoAlignment séparé; identifier les fichiers partagés. |
| A02 | Vérifier bruit, corrélation et observabilité | 100 % | A01 | Sources primaires; équations explicites; distinguer résidu et erreur absolue; expérience reproductible. |
| A03 | Définir contrats et protocole de qualification | 100 % | A02 | Repères, temps, incertitudes, dépendances et critères de sortie écrits; accord produit terrain restant. |
| L01 | Créer interface neutre de localisation | 100 % | A03 | Réutiliser location/LocationSample; contrat indépendant de Play Services; GPS/FLP interchangeables; tests provider simulé. |
| L02 | Brancher FLP et repli GPS natif | 60 % | L01 | Tester Play Services absent, localisation désactivée, permission approximative/refusée, annulation et reprise; aucune panne globale. |
| L03 | Contrôler horloge, fraîcheur et doublons | 90 % | L01 | Original elapsedRealtimeNanos, arrivée, provenance conservés; doublons/réordonnancement/batch testés; données brutes intactes. |
| L04 | Mesurer gain FLP sur appareil | 0 % | L02, L03 | Campagnes GPS seul, FLP seul, simultané; référence indépendante; disponibilité/latence/énergie/erreur p50/p95 rapportées. |
| F01 | Réutiliser synchronisation et alignement B1–B8 | 85 % | L03 | Revue GeoAlignment/GeoFusionTracker/PoseFixSynchronizer de PR #3; intégration ciblée; reset par segment, aucune copie divergente. |
| F02 | Implémenter conversion GPX vers repère AR | 100 % | F01 | WGS84 vers ENU puis axes AR documentés; yaw et translation sans ajustement libre de l’échelle; aller-retour et signes testés. |
| F03 | Conserver incertitude sous erreurs corrélées | 40 % | F01, A02 | Ne pas compter FLP et GNSS comme indépendants; doublons ne réduisent pas la confiance; biais commun déclaré non observable; couverture mesurée sur données tenues à part. |
| F04 | Gérer géométries ambiguës et suivi GPX | 55 % | F02, F03 | Immobilité/rotation pure/courte base ne créent pas un cap fiable; routes parallèles, croisements et boucle gardent plusieurs hypothèses; pas de snap certifié. |
| F05 | Gérer pertes et corrections sans sauts cachés | 70 % | F04 | Perte GNSS/VIO, reset AR, reprise et changement provider testés; incertitude croissante; transformation datée/versionnée, correction visible. |
| H01 | Préserver altitude et datum du GPX | 60 % | A03 | ele facultatif; datum inconnu explicite; aucune conversion implicite MSL↔ellipsoïde; ponts/étages distincts; import sans ele testé. |
| H02 | Construire surface locale observée | 65 % | H01, F02 | Plans/raycast/depth quand supportés; aucune hauteur dérivée silencieusement de la hauteur téléphone; terrain inconnu reste inconnu. |
| H03 | Tester montées, escaliers et téléphone vertical | 50 % | H02 | Lever/baisser le téléphone ne déplace pas la route au sol; pente suivie seulement sur géométrie observée; segments non observés identifiés. |
| R01 | Créer géométrie de route 3D continue | 80 % | F05, H02 | Ruban continu, raccords sans rectangles visibles, épaisseur métrique, points finis; CPU fallback si GPU indisponible; mêmes positions géométriques. |
| R02 | Stabiliser ancres et portions locales | 20 % | R01 | Chunks bornés; origine flottante si nécessaire; cohérence après relocalisation; pas d’accumulation illimitée; essai long. |
| R03 | Afficher états de confiance compréhensibles | 70 % | F03, R01 | Acquisition/ambigu/recalage utilisable/perdu; corridor ou flèche si précision insuffisante; mode précis réservé aux conditions validées; cap/position/hauteur séparés. |
| R04 | Offrir recalage manuel et aide au retour | 10 % | R03 | Point connu + direction avec incertitude déclarée; annuler/revenir en arrière; suivre/rejoindre trace sans masquer ambiguïtés. |
| I01 | Ajouter mode expérimental sans collision DATA | 60 % | R03 | Actions DATA distinctes; préserver GPS ROUTES, A/B, replay, PDR et capture; tests navigation et lifecycle sur résultat combiné. |
| I02 | Borner coût et préserver acquisition | 60 % | I01, R02 | Pose cible 30/s séparée de VIEW; files bornées; pas de deadband translation; export asynchrone brut/estimé séparés; métriques perf/power. |
| Q01 | Tester moteur réel en replay et négatifs | 30 % | F05, H03, R03 | Mêmes classes Java que l’app; métriques par moteur; perturbations biais, corrélation, perte, GPX erroné, sens inversé; données de test distinctes du réglage. |
| Q02 | Mesurer placement caméra sur parcours de référence | 0 % | Q01, L04, I02 | Points sol indépendamment référencés, marche/course/ville/forêt/pente/chemins parallèles; erreur latérale/longitudinale/cap/hauteur p50/p95, abstention et faux fiables. |
| Q03 | Produire APK de la branche réunie | 0 % | Q02 | CI tests/lint/build et smoke menus/export au SHA exact; APK et rapport avec limites; pas de validation via APK d’une autre branche. |
| O01 | Évaluer référence visuelle absolue Google | 0 % | L04, F03 | Essai séparé Geospatial/VPS avec couverture, réseau, autorisations/coûts; comparaison indépendante; désactivé par défaut et aucune dépendance obligatoire forêt. |

## Qualification : preuves et portes de sortie

1. **Référence indépendante.** Points sol et trajet référencés par méthode documentée (points topographiques, RTK de qualité vérifiée ou système optique), synchronisation et incertitude publiées. FLP n’est pas la vérité du GPS, ni le GPX importé la vérité d’un estimateur qui s’y contraint. Un terrain mesuré peut suffire au prototype; si sa précision ne permet pas de trancher, le rapport le dit.
2. **Ablations.** GPS seul, FLP seul, simultané pour corrélation; VIO relatif et recalage combiné; même appareil, provenance, moteur et conditions. Les souscriptions simultanées peuvent changer énergie et disponibilité. Ne pas tirer consommation GPS seul d’un test simultané.
3. **Cas positifs/négatifs.** Ciel ouvert, ville, forêt, signal très faible/absent, marche/course, téléphone en hauteur variable, rotation sur place, pente/escalier/pont, chemins parallèles, boucle et sens inverse, GPX décalé, arrêt/reprise, GPS ancien répété, cap magnétique perturbé. Au moins trois passages par condition pour exploration; taille finale de qualification définie par intervalles d’incertitude, pas par ce minimum.
4. **Jeu tenu à l’écart.** Régler paramètres sur sessions de développement; figer code/seuils; évaluer d’autres routes/jours et sens, sans réalignement optimal cachant l’erreur absolue. Conserver échecs et abstentions dans les métriques.
5. **Mesures.** Erreur horizontale brute et placée, latérale/longitudinale, cap et hauteur p50/p95/max, couverture des incertitudes, taux de fausse confiance/abstention, temps d’acquisition/récupération, poses/UI FPS distincts, latence p95, mémoire après 1 h, température, consommation par heure, taille export/heure. Les seuils produit de précision doivent être convenus après baseline et gelés avant le test tenu à part; aucune promesse centimétrique préalable.
6. **Livraison.** CI Android et tests moteur du SHA réuni; smoke des actions DATA, pause/reset/export, interruptions et fallback. APK accompagnée des conditions mesurées. Si Q02 échoue, conserver mode expérimental/dégradé plutôt que valider une ligne jolie mais fausse.

## Sources primaires consultées le 19/10/2026

- Google FLP : https://developers.google.com/location-context/fused-location-provider
- API, permissions et cadence : https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient
- Google Location Settings : https://developers.google.com/android/reference/com/google/android/gms/location/SettingsClient
- Android précision déclarée : https://developer.android.com/reference/android/location/Location#getAccuracy()
- GPS.gov causes et précision : https://www.gps.gov/gps-accuracy
- ESA NAVISP CAPRESE final report 2023, section erreurs GNSS / p. 37 : https://navisp.esa.int/uploads/files/project_documents/NAVISP1-TN-NSL-EL1017-0060_v1.2%20(Final%20Report%20PUBLIC).pdf?v=497380
- ESA multipath : https://gssc.esa.int/navipedia/index.php?title=Multipath
- GPX 1.1 : https://www.topografix.com/gpx/1/1/
- Référence visuelle optionnelle : https://developers.google.com/ar/develop/geospatial

Les sources motivent les hypothèses; les chiffres de l’étude viennent de notre modèle synthétique, pas de mesures citées sur téléphone.

## Utilisation de cette itération

1. DATA → GPX AR → IMPORT GPX; choisir un segment continu (maximum 50 000 points, section sous 100 km).
2. ENABLE AR GPX; autoriser localisation précise; AUTO propose Google Fused avec repli GPS natif. Choisir GPS/Fused explicitement pour comparer.
3. START/RESUME le live et marcher avec des textures visibles. Attendre plusieurs fixes frais et une base suffisante par rapport au bruit GPS. Une rotation sur place n’établit pas l’orientation géographique.
4. Bleu = GPX candidat, orange = trace locale. Aucun sol observé : aucune ligne GPX au sol. La localisation candidate reste exposée au biais GNSS/erreur GPX, même avec faible résidu.
5. Pause/background/perte AR exigent une nouvelle acquisition de recalage. Le GPX reste stocké; le démarrage automatique du mode est désactivé après recréation de l’application. Le repli Fused stale intervient après 12 s sans fix frais, sans souscription simultanée volontaire.

Cette itération ne fournit pas encore VPS, terrain lointain/DEM, occlusion, correction altitude/datum, recalage manuel point connu, recalage multi-hypothèses garanti, ancres GPX longue durée ni précision absolue validée. Le ruban est un affichage expérimental du candidat, pas une position certifiée. Les allowances sont diagnostiques et non des probabilités calibrées.

### Durcissement horloge, guidage et export

Le timestamp ARCore `Frame.getTimestamp()` n’a pas de timebase garantie. `ArPoseClock` vérifie `SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME` sur la caméra sélectionnée et utilise `Frame.getAndroidCameraTimestamp()` (présent dans l’AAR 1.56.0 inspectée). Source inconnue ou timestamp incompatible : recalage masqué avec état horloge explicite, aucune estimation d’offset par réception. Ceci ne qualifie pas encore les décalages matériels de pose/capture sur chaque appareil.

Le matching direction/progression réutilise `GpsNavigator`/`RouteMatcher`, alimenté par la seule source absolue sélectionnée. Il n’altère pas la transformation GPS/VIO pour forcer le GPX. La petite flèche bleue est une indication de direction horizontale estimée, pas un point 3D ni une preuve de terrain; pas de flèche si cible/alignement absents ou matching ambigu. Le ruban bleu reste limité aux sols observés.

Dataset ZIP conserve les nouveaux sidecars CSV : `_gpx_ar_fixes.csv` (tous les champs disponibles, horloges d’origine/arrivée, mock et admission à la file), `_gpx_ar_route.csv` (vertices exacts du GPX sélectionné, révision et datum UNKNOWN), `_gpx_ar_alignment.csv` (états/allowances, timestamp ARCore pour référence au journal vidéo). Pas de données de référence indépendante inventées; valeurs facultatives manquantes vides. La sérialisation des grands GPX se fait sur le worker CSV, pas sur la caméra.
