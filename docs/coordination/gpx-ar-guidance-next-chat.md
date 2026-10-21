# Handover — GPX caméra, reprise immédiate

> **RELEASE LOGICIELLE GPX AR VALIDÉE EN CI LE 19/10/2026 (mise à jour prioritaire)** : code `1920c6e17c88844df99d26c56c0ca902b856e82b`, [Android CI verte](https://github.com/jboehm1/VisualOdometry/actions/runs/39317910882) (163/163 tests; lint; debug/release; GPS et simulations), [Three Campaigns vertes](https://github.com/jboehm1/VisualOdometry/actions/runs/39317910825); [APK artifact 12218742148](https://github.com/jboehm1/VisualOdometry/actions/runs/39317910882/artifacts/12218742148) ; APK SHA256 `22b86224112e1ea4aec13cf808756e02720ce8f8e046fd138e4f39745d5c40ff`. **Les éléments ci-dessous décrivent un blocage historique maintenant corrigé**. PR #24 toujours brouillon, aucune validation physique/absolue; voir [rapport final logiciel](gpx-ar-release-1920c6e1.md).\n\nÉtat vérifié le **19 octobre 2026, vers 21:50 Europe/Berlin**. Dernière demande : « Status ? Give a handover to continue in another chat ». L’utilisateur attend encore la finalisation et un **nouvel APK validé**. Ne pas déclarer le travail terminé.

## Git et environnement

- Dépôt : https://github.com/jboehm1/VisualOdometry
- Branche de travail isolée : `feature/gpx-ar-guidance`.
- **Dernier commit de code poussé : `6878ccd1d4a4976e176887e625ea7cf0b5843ae1`.** Le commit contenant ce handover peut être ultérieur, documentaire seulement.
- PR brouillon **#24**, cible `feature/slam-explorer` : https://github.com/jboehm1/VisualOdometry/pull/24
- Intégration distante vérifiée : `ab577a14b29261f2047503b2432e47ceb97e06ac`. Aucune fusion effectuée.
- Worktree de cette session : `/workspace/scratch/c38708a31a30/VisualOdometry-gpx-ar-guidance`; HEAD code ci-dessus, propre avant ajout de ce document. Si ce chemin a disparu, récupérer la branche distante.
- Lire `AGENTS.md`, `docs/DEVELOPMENT_COORDINATION.md` depuis l’intégration actualisée, `docs/coordination/gpx-ar-guidance.md`, `docs/GPX_AR_GUIDANCE_PLAN.md`, `docs/GPX_AR_TASKS.json`, `docs/GPX_AR_REPLAY.md` et le protocole téléphone.
- Rafraîchir les refs et inspecter les autres PR avant intégration. D’autres chats travaillent en parallèle; ne pas écraser MainActivity, menus DATA, manifeste, exports ou workflows par résolution globale ours/theirs. Ne pas fusionner automatiquement.

## Blocage actuel EXACT

CI Android **39312917796**, commit code `6878ccd` :
https://github.com/jboehm1/VisualOdometry/actions/runs/39312917796

- Compilation Java Android passée jusqu’à exécution des tests.
- **163 tests Android exécutés : 162 réussis, 1 échec.**
- Échec : `GpxArAndroidTest.displayOptionsPersistWithoutStartingCaptureOrLocation`, **ligne 57** : `expected:<0> but was:<1>`.
- Le test ouvre DATA, GPX AR, DISPLAY OPTIONS, puis `options.getListView().performItemClick(null,0,0)` et attend le bit direction désactivé dans `spatial_explorer/gpx_display_flags`.
- La cause précise n’est PAS établie. Examiner le toggle multi-choice Robolectric, l’état initial sélectionné et le callback; vérifier aussi le vrai comportement UI. Ne pas supprimer le test ni affaiblir son invariant pour rendre la CI verte. Un premier clic simulé peut ne pas reproduire l’état checked Android réel : hypothèse, pas conclusion.
- Implémentation : `MainActivity.gpxDisplayMenu()` lignes ~431–438. Flags chargés dans onCreate depuis `spatial_explorer` ligne ~182. MultiChoice listener met/retire le bit selon `checked`, puis SharedPreferences.apply().
- Les étapes lint/APK et régressions GPS/simulation ne sont **pas validées par ce run** : la commande Gradle s’arrête sur le test. Upload APK sauté. **Aucun nouvel APK pour `6878ccd`.**
- Job ID `118266895077`. Rapports artifact `12216162505` (`android-unit-test-reports`); replay artifact `12216912509` (`measured-replay-results`). Liens de téléchargement temporaires : en demander de nouveaux, ne pas réutiliser un lien signé du chat précédent.
- Les 12 tests Python GPX/bruit et le nouveau smoke ZIP/moteur Java ont réussi en CI.

## Implémenté et poussé dans cette itération

1. **Repère GPX métrique transporté** : une instance AnchoredOdometry dédiée au GPX, indépendante de celle de l’odomètre. Ajout de l’inverse `worldFromMetric()` dans AnchorCoordinates/AnchoredOdometry; aucune modification de l’algorithme de distance. Pose GPX ancre-relative au lieu du monde brut; reset aux discontinuités et clocks non vérifiées.
2. **GpxRibbonAnchors** : tuiles métriques 6 m, 32 ancres maximum, 2 créations par présentation; réutilisation, detach en sortie/perte/reset et cooldown 2 s après création impossible. Les coordonnées locales suivent les corrections d’ancre entre révisions de recalage. Échec d’ancre => portion masquée, pas de fallback monde non ancré.
3. **SceneMapper** : inverse métrique→monde, projection sur plans de sol observés puis ancres; même géométrie GPU/Canvas. Labels de route seulement si portions ancrées disponibles. POI masqués visuellement restent présents dans la capture/snapshot.
4. **Wgs84LocalFrame** : WGS84 ellipsoïdal ECEF→ENU horizontal et inverse. GeoAlignment et RouteIndex l’utilisent. Axes AR x/−z vers est/nord, échelle non ajustée. RouteIndex densifie les arêtes à ≤25 m, limite 200000 sommets et extension 100 km; GPX source intact.
5. **Informations optionnelles persistées** : direction estimée, repères tous les 25 m, départ/arrivée, POI locaux avec distance directe, distance restante. DATA → GPX AR → DISPLAY OPTIONS (index 7, menu 8 éléments). RouteDisplayOptions sépare les flags du moteur. Pas d’import/affichage des waypoints nommés GPX dans cette itération.
6. **Export poses** : CSV `_gpx_ar_poses.csv` conserve monde + horloges et ajoute `metric_x,metric_y,metric_z,route_revision`. Une métrique vide ne doit jamais devenir le monde brut en fallback.
7. **Pipeline de rejeu réutilisable** : `tools/gpx_ar/replay.py` + `GuideReplay.java` compilent/exécutent les mêmes classes Java ArRouteGuide/GeoAlignment/PoseFixSynchronizer/GpsNavigator/RouteMatcher que l’app sur Dataset ZIP. Ordre d’arrivée et timestamps originaux; diagnostics 1 Hz mais tous les événements sont traités. Vérification ZIP/CSV/révisions, empreintes entrée/sources, provenance, états, comparaison absolue référence facultative sans fit spatial. Aucun retraitement pixels/IMU/ARCore; vidéo non décodée. Guide complet `docs/GPX_AR_REPLAY.md`.
8. CI : smoke export synthétique connu + résultats archivés sous `measured-replay-results/gpx-ar`. Ne jamais appeler cela un essai réel S21.

## Tests déjà réellement exécutés

- **41 JUnit Java ciblés réussis localement** : ArRouteGuideTest, GpxArJournalTest, RouteDisplayOptionsTest, GpxGroundProjectorTest, GroundTrailTest, WorldGeometryTest, AnchoredOdometryTest, GpxRibbonAnchorsTest, Wgs84LocalFrameTest.
- **12 tests Python réussis localement et en CI** : bruit/corrélation/biais, rejeu métrique distinct du monde, horloge inconnue, doublons, archive dangereuse, référence absolue.
- Workflow YAML parsé, git diff --check passé.
- Dernière CI globale : **162/163 réussis**, pas verte; détails ci-dessus.
- Aucun test physique téléphone ni campagne GPS/FLP avec référence indépendante exécuté. Aucun rejeu d’une vraie capture de cette nouvelle version disponible.

## Reprise prioritaire

1. Reproduire et corriger proprement l’échec d’option UI, en vérifiant les deux sens ON/OFF, la persistance après recréation et l’absence de démarrage capture/location par ce menu. Déterminer si bug produit ou harnais.
2. Pousser un correctif ciblé sur la même branche; CI doit passer au **nouveau SHA exact**. Commande CI : `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease --no-daemon --stacktrace`.
3. Vérifier aussi régression GPS et replays générés initial/inverse, tous exécutés par le workflow Android. Inspecter le second workflow three-campaigns au SHA exact; sa présence ne certifie aucune précision terrain.
4. Mettre à jour tâches et handoff : docs actuels mentionnent encore CI en attente et preuves historiques. Consigner SHA code testé, run, nombre de tests et résultats réels. Garder L04/Q02 et calibration terrain non terminés.
5. Récupérer l’artifact **vector-vio-debug-apk** du run vert correspondant; vérifier intégrité ZIP et SHA256, nommer APK avec SHA. Publier lien installable à l’utilisateur et mettre à jour PR #24. Sauver le livrable de façon persistante selon les instructions de l’environnement. Ne pas donner un APK ancien comme celui du nouveau code.
6. Revue avant intégration : raccords entre patches pouvant corriger indépendamment, stabilité réelle après relocalisation et performances/énergie. Aucune donnée de terrain ne permet encore de certifier ces points.

## Commandes hôte utiles

Pas de SDK Android/Gradle local dans cette session; utiliser GitHub CI pour tests Android. JDK disponible via `java com.sun.tools.javac.Main` (pas de binaire javac).

```sh
python3 -m unittest discover -s tools/gpx_ar/tests -v
python3 tools/gpx_ar/replay.py capture.zip --out reports/gpx-capture --input-origin real_capture
```

Jars locaux temporaires de cette session : `/tmp/vector-gpx-java/junit.jar`, `hamcrest.jar`, `arcore.jar`; classes `/tmp/vector-gpx-java/classes`. Compiler les classes pures et tests ci-dessus avec les jars; rajouter `geo/Wgs84LocalFrame.java` à toute ancienne liste de compilation.

## Publication Git dans cet environnement

Le fetch CLI fonctionne. Les commits de cette itération ont été publiés via les outils GitHub Git Data (create_blob → create_tree avec base HEAD tree → create_commit parent HEAD → update_ref avec expected_sha, force=false). 26 fichiers dans le commit `6878ccd`, tree `243d33886ac2458637817efd8a747426c4d228e8`. Ensuite fetch, staging des seuls fichiers possédés, vérification write-tree et update-ref local avec ancien SHA attendu. Ne pas utiliser de reset destructif sur les worktrees partagés. Une autre session peut avoir un push CLI normal : vérifier son environnement.

## Ancien APK, uniquement historique

Code `37eda6d71308748956fffe7f3ef94b424b6a18eb`, ancienne CI verte `39305523845` (153 tests). Il ne contient pas les ajouts d’ancres/rejeu/options de cette itération.

Chemin session : `/workspace/scratch/c38708a31a30/deliverables/VECTOR-VIO-GPX-AR-37eda6d.apk`; identité persistante précédente `libfile_9aa3123ad7b88191a8fbfdb5e76e95d9`. SHA256 `4586d7e2a0df04e9a785e178de41745a3cea7fa654cc4f67db458c00ba85574d`.

## Invariants et limites à préserver

Aucun deadband arbitraire ou filtre de 12 cm réintroduit. Odomètre et raw inchangés; cadence poses distincte de VIEW. Source absolue GPS ou Fused sélectionnée, pas deux sources supposées indépendantes. Bon résidu ≠ position exacte : un biais GPS commun reste non observable. Surface inconnue masquée, GPX altitude/datum UNKNOWN conservé, pas de terrain inventé. Pas de VPS, DEM, occlusion ou garantie centimétrique. Ne pas annoncer 100 % production-ready sur la seule CI; le S21, les parcours indépendamment référencés, course/forêt/pente et batterie restent à qualifier.
