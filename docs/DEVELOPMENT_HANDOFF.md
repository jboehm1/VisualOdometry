## Mise à jour 0.10.0-alpha : fréquence de poses indépendante

La version 0.9 ci-dessous est historique. LiveFrameScheduler demande désormais des mises à jour à ~30/s sur un HandlerThread dédié, indépendamment du rendu VIEW 5/15/30/Hold. CoalescingTask borne la file GL ; processFrame conserve odométrie/capture de carte/métadonnées, onDrawFrame ne fait que présenter. FrameRateMeter élimine les doublons et mesure les frames distinctes. Voir POWER_PROFILES.md pour limites, course et protocole de validation. Vérifier matériellement EGL/ARCore, fréquence effective en courant et autonomie ; CI ne certifie pas ces points.

## Mise à jour 0.9.0-alpha : énergie

Voir [POWER_PROFILES.md](POWER_PROFILES.md). LiveFrameScheduler isole la politique de cadence Full/15/5/Hold ; CaptureProfile et le sélecteur de plage gèrent Ultra WALK avec repli annoncé. Tests de transitions, geste/menu et limites caméra. Aucune mesure batterie physique ni garantie de qualité identique en basse cadence.

# VECTOR VIO — Handover complet

État au 18 octobre 2026. Ce document remplace l'ancien handover 0.1.

## Actualisation : correctif de repère et guidage retour

Les sections historiques ci-dessous décrivent la baseline cc2533c. Le nouveau live utilise `AnchoredOdometry`/`AnchorCoordinates` pour corriger les changements rigides communs caméra/ancre, sans seuil sur les translations. Tests et limites dans ROTATION_ANALYSIS.md ; schéma `_motion.csv` et commandes du mode retour dans CAPTURE_FORMAT.md. La confirmation physique sur S21 reste nécessaire. Le problème de rotation n'est pas déclaré universellement résolu.

Une aide optionnelle DATA -> Return along current trace fige le segment courant en coordonnées métriques et indique retour/rejoindre la trace, distance restante, écart et gauche/droite. Guidage interrompu sur perte de référence ; pas de recalage d'anciennes cartes. Les poses monde brutes destinées à l'export et l'historique visuel restent distincts du trajet métrique. Les anciennes priorités de ce document concernant l'implémentation de cette référence ou du retour sont désormais partiellement réalisées ; tests terrain et projection cohérente de toute l'ancienne carte restent ouverts.

## Actualisation : WALK / énergie

Le contrôleur CollectionActivity reprend une capture existante au retour écran allumé ; le lanceur principal redirige vers lui si une capture est active. Bouton explicite de finalisation puis reprise AR (nouveau segment). Profils Complete 720p30 / Light 720p15 / Detail 1080p30, avec compromis vidéo explicite et mêmes cibles IMU/GPS. Batching matériel, flush capteurs à l'arrêt avec timeout journalisé, flush fichiers toutes les 5 s. Voir CAPTURE_FORMAT.md : le changement n'ajoute pas d'ARCore écran éteint, de reconstruction différée ou de GPX. Énergie et continuité physique S21 encore non validées. Les anciennes descriptions de profil/flush/contrôleur ci-dessous sont historiques.

## Actualisation : projection au sol

Ruban désormais limité aux surfaces détectées par défaut ; les zones sans sol créent des ruptures visuelles (NO FLOOR / FLOOR PARTIAL), pas une hauteur caméra moins 1,5 m imposée. Ancien mode approximatif réactivable dans DATA -> Unknown floor ribbon, marqué FLOOR EST. Normale complète du plan utilisée pour la hauteur lorsque disponible. Le terrain extérieur irrégulier/Depth n'est pas implémenté ; les surfaces restent géométriques sans reconnaissance de sol. Voir CAPTURE_FORMAT.md. Les anciennes descriptions du fallback automatique ci-dessous sont historiques.

## Actualisation : GPS / GPX 0.8

Voir GPS_NAVIGATION.md : import/export GPX, bibliothèque privée, guidage GPS indépendant avec service écran éteint, matching séquentiel multi-candidats sur le parcours importé, précision/ambiguïté/écart/reprise, vue hors ligne, enregistrement et récupération de promenades. Entrée DATA -> GPS ROUTES ou depuis WALK. Version 0.8.0-alpha / code 8. Les anciennes mentions « GPX absent » ci-dessous sont historiques. Pas de réseau routier OSM, de fond cartographique ou de moteur de calcul d'itinéraire ; reconnaître un chemin voisin absent du GPX n'est pas implémenté. Aucun mélange avec le repère AR ou les mètres VIO. Validation synthétique et Android automatisée documentée au SHA livré ; terrain S21 et signature de production restent des limites explicites.

## 1. Reprendre le projet

- Dépôt : https://github.com/jboehm1/VisualOdometry
- Branche de travail : `feature/slam-explorer`, PR https://github.com/jboehm1/VisualOdometry/pull/2
- L'utilisateur autorise les pushes sur cette branche. Ne pas fusionner ou réécrire les sauvegardes historiques. Ne pas publier ses vidéos, positions ou exports privés.
- Ancien notebook conservé dans le dépôt ; sauvegarde historique `backup/2019-original-colab`. Ne pas le remplacer par l'application.
- Téléphone utilisateur : Samsung Galaxy S21, Android 15. Aucun accès matériel/ADB pour l'agent pendant ces travaux.
- Application : `dev.jboehm.vectorvio`, version 0.7.0-alpha, code 7. Elle n'est pas certifiée production ready.
- SDK min 26 / cible 35 / compilation 36 ; Java 17, Gradle 8.13, AGP 8.13.2, ARCore 1.56.0.
- Toujours vérifier HEAD et le run CI correspondant. Une compilation verte n'est pas une mesure de précision sur téléphone.

## 2. Priorité et décisions actuelles

L'utilisateur observe encore une vitesse et une grande distance en rotation supposée pure. Ce défaut n'est PAS résolu. Ne pas prétendre le contraire.

Historique important :

1. `e84b608` a ajouté un seuil dépendant de la rotation, entre 3,5 et 12 cm. Heuristique insuffisante ; test initial limité à une erreur circulaire de rayon 4 cm.
2. Audit Java : deux tours avec positions sur un cercle de rayon 15 cm font compter 1,8303 m malgré ce filtre. Ce scénario est un test de l'accumulateur, pas une simulation de la physique d'ARCore.
3. `69440d451402cd48af75af76fae09c9b8d062050` retire ce seuil dépendant de la rotation. CI 39116842846 réussie, artifact APK 12132259605. Cette version intermédiaire conserve encore 3,5 cm ; ne pas la livrer comme version sans seuil.
4. Le présent changement retire AUSSI le seuil fixe de 3,5 cm, à la demande explicite de l'utilisateur. Événement CSV `DISTANCE_RAW_TRANSLATIONS_V3_NO_DEADBAND`. Toute translation non nulle d'un segment valide est additionnée intégralement. Aucun angle ne réduit ou augmente une translation.

Conséquence : davantage de détail conservé dans la distance, mais le bruit des poses s'additionne aussi. La distance brute peut dériver davantage à long terme. Ne pas promettre absence de perte ou exactitude. La vitesse est toujours lissée pour l'affichage. La route affichée est décimée séparément, les captures et cartes restent bornées.

## 3. Mathématiques et diagnostic à poursuivre

Analyse approfondie ultérieure : [ROTATION_ANALYSIS.md](ROTATION_ANALYSIS.md), avec audit du repère ARCore révisable et démonstrateur `tools/rotation/RotationAudit.java`. Les différences de positions entre frames supposent un repère fixe, ce que le contrat ARCore ne garantit pas. Une mesure caméra relative à une même ancre est une piste à tester ; aucun correctif live ajouté par cet audit.

ARCore fournit `camera.getPose()`, translation en mètres et quaternion de la caméra vers le monde local. MainActivity lit directement `getTranslation()` ; aucune multiplication erronée par la rotation n'a été trouvée dans ce chemin.

Pour T1=(R1,t1), T2=(R2,t2), translation relative = transpose(R1)*(t2-t1). Sa norme égale celle de t2-t1. À position constante, toute rotation ajoute exactement zéro. Le compteur utilise cette norme dans le repère monde ; changer de repère par une rotation ne change pas la longueur.

Une caméra qui décrit réellement un arc a une translation réelle. Une rotation autour du corps ou de la main n'est pas forcément une rotation autour du centre optique. ARCore peut aussi produire une erreur de translation pendant une rotation. Les seules poses ne permettent pas de distinguer ces deux causes avec certitude. Ne pas remplacer cette incertitude par un seuil arbitraire ou un blocage pendant tous les virages.

Prochaine expérience : enregistrer vidéo + poses et IMU sur support, rotation autour du centre caméra aussi proche que possible, position fixe, puis translation droite et virage. Comparer positions brutes, incréments, vitesse, état de suivi, timestamps et événements. Demander une capture récente ; les anciens exports ne documentent pas ce dernier test. Les poses ARCore ne sont pas une vérité terrain indépendante.

## 4. Architecture implémentée

| Module | Rôle et limites |
|---|---|
| MainActivity | Orchestration ARCore, cycle de vie, commandes, capture/export ; encore fortement couplée |
| Odometer | Java pur : positions métriques -> longueur 3D, horizontale, dénivelé, vitesse, segments |
| RouteHistory | Historique borné, décimation progressive, séparation des segments |
| SceneMapper / MapCapture | Plans, points, poses, POI et snapshots directement liés à ARCore |
| MapArchive / SessionBundle | Validation, persistance et ZIP ; blocs réutilisables |
| TrainingCapture / CsvRecorder | MP4 ARCore optionnel, CSV pose/IMU, métadonnées, I/O sérialisées |
| GpsMonitor / ImuMonitor | GPS indépendant et capteurs Android ; aucune fusion GPS dans VIO |
| WorldGeometry / WorldOverlayRenderer | Géométrie du tracé et surfaces, rendu GLES2 |
| OverlayPolicy / GlErrors | Choix GPU/Canvas et détection bornée des erreurs GL |
| HudView / MapView | HUD et carte Android Canvas |
| CollectionActivity / CollectionService / CollectionFiles | Capture Camera2 autonome, sans ARCore ni odométrie |
| SimulationModel / CorridorRenderer / SyntheticVision / ReplayCamera | Simulation procédurale et estimateur planaire limité, distincts du live |

Il n'existe pas encore d'interface commune Engine/InputSource ni d'adaptateur EuRoC, OpenVINS ou ORB-SLAM3. Ne pas qualifier tous les moteurs d'interchangeables. Voir MODULES_AND_DATASETS.md pour le contrat cible.

## 5. Comportement du compteur

- Distance cumulée : somme des normes des translations valides ; aucun seuil de petite translation désormais.
- Rotation : ignorée par le calcul de distance ; overload quaternion conservé pour compatibilité.
- Vitesse : déplacement/intervalle, puis lissage exponentiel ; zéro mouvement fait décroître la vitesse affichée.
- START/RESUME ouvre un nouveau segment. PAUSE/background interrompt le calcul ; pas de liaison à travers le mouvement inconnu.
- Perte de suivi, trou de plus de 0,70 s ou saut instantané supérieur à 25 m/s : référence réinitialisée, intervalle exclu. Ce sont encore des règles heuristiques explicites, pas un modèle d'incertitude.
- Timestamps dupliqués/non monotones exclus ; valeurs non finies rejetées.
- Distance totale et déplacement net diffèrent. Déplacement/dénivelé net concernent le segment courant ; distance totale traverse les segments sans inventer de liaison.
- Carte X/Z locale, pas nord géographique. Pas de repère partagé entre utilisateurs.
- Route bornée à 8192 points avec décimation ; petit historique télémétrique séparé. Les points du ruban ne constituent pas un export intégral des poses.

## 6. Fonctions visibles

- START, PAUSE, RESUME, RESET, MARK ; flash selon support ARCore/caméra.
- HUD compact, caméra plus visible, carte ajustée au parcours complet.
- Parcours cyan, départ orange, position actuelle bleue avec anneau et cône de direction. Cône indicatif, pas un champ de vision calibré ; position grisée sans orientation valide en perte de suivi.
- Tracé AR orange proche du sol : ruban continu 8 cm, raccords miter bornés et extrémités arrondies. Aucun lissage des positions métriques par cette géométrie. Les ruptures de suivi ne sont pas reliées.
- Sol estimé depuis plans horizontaux plausibles ; sinon hauteur supposée 1,5 m, mention FLOOR EST. Pas de classification sémantique ni d'occlusion réelle.
- Surfaces teintées sans bordure, couches activables. Plans/points ne constituent pas une reconstruction dense ou étanche.
- Cartes sauvegardées, inspection 3D hors ligne, exports PLY/TUM, contrainte manuelle de fermeture copiée. Recharger un snapshot ne restaure pas la carte privée de suivi ARCore ; pas de fermeture de boucle globale validée.

## 7. Rendu et performances

GLES2 dessine les surfaces, points et ruban en jusqu'à quatre lots/VBO persistants. Géométrie actualisée environ toutes les 50 ms ; matrices caméra actuelles appliquées à chaque dessin. HUD, texte et carte restent Canvas. Masquer une couche évite le dessin mais pas l'acquisition nécessaire à la carte sauvegardée.

DATA -> Overlay rendering : Auto GPU, Force Canvas, Retry GPU. Défaillance mémorisée par empreinte Android ; Auto ne réessaie pas en boucle. Gestion compilation shaders, buffers, upload/draw et recréation de contexte. Canvas est un repli pour overlays, pas une version sans GPU d'ARCore. Les pannes natives ou défauts silencieux de pilote ne sont pas tous détectables.

FPS affiché correspond à la cadence caméra observée, pas à un benchmark de tous les algorithmes. Événements SLOW_RENDER_MS avec durées ARCore/métadonnées pour les rendus >100 ms, limités à un toutes les 5 s. Des freezes réels restent à profiler sur téléphone ; aucune garantie de fluidité universelle.

## 8. Capture live et export

DATA -> Training video -> ARM pendant pause, puis START/RESUME. Vidéo désactivée par défaut. PAUSE et export finalisent ; reprise crée une nouvelle partie MP4. Sans activation ou démarrage réussi, aucun MP4 attendu.

ZIP : CSV principal, MP4 existants non vides, frames.csv, métadonnées, GPS optionnel et README. Le sélecteur exclut les CSV techniques de la liste des sessions ; session courante ZIP proposée en premier. CSV seul reste un choix explicite. Pas d'audio ni d'upload automatique.

Les poses enregistrées sont brutes au rythme du journal, pas forcément toutes les poses caméra. Journal POSE limité à environ 10 Hz ; metadata frames si capture vidéo active. IMU SensorManager demandée à 200 Hz, livraison variable. Les seuils du compteur ne modifient pas les poses exportées.

Formats détaillés : CAPTURE_FORMAT.md. Intrinsèques CPU-image, axes Android, accélération incluant gravité. Extrinsèques caméra/IMU et décalage temporel non calibrés ; PTS MP4 non assimilables automatiquement aux timestamps capteurs. Pas de dataset d'entraînement avec vérité terrain calibrée.

GPS optionnel : position, précision, vitesse, timestamps. Jamais fusionné avec carte locale. En voiture, caméra voyant surtout l'habitacle peut mesurer zéro mouvement relatif malgré le déplacement du véhicule.

## 9. Collect-only / longues captures

DATA -> COLLECT ONLY : Camera2 + service de premier plan, notification STOP, verrou CPU partiel ; démarrer avec écran/activity visible. Pas de preview, ARCore, carte ou compteur. Ne pas utiliser simultanément avec live AR.

- H264 720p30 cible 3 Mbps (~1,35 Go/h) ou 1080p30 cible 6 Mbps (~2,7 Go/h). Compression vidéo avec perte.
- IMU/GPS/frames et événements gzip sans réduction volontaire de précision des flottants.
- Parties vidéo jusqu'à 10 min ou 256 MiB ; redémarrage encodeur/session peut produire des trous vidéo. Thread capteurs distinct.
- Départ exige 256 MiB libres, arrêt sous 128 MiB, limite 24 h ; aucune suppression automatique.
- Pas de redémarrage automatique en arrière-plan. Interruption brutale peut corrompre le MP4 courant ou la fin gzip ; anciennes parties finalisées indépendantes.
- ZIP sans recompression, export après arrêt/finalisation ; garder le contrôleur ouvert pendant export.

Écran éteint, batterie, thermique, longue durée, changements de parties et interruptions restent à tester sur S21. Ce mode n'est pas un dataset ARCore rejouable.

## 10. Robustesse existante et limites

Non-finite map point : filtrage des valeurs invalides à l'acquisition/transformation, validation stricte à la sauvegarde. Erreurs carte isolées du compteur, sauvegarde atomique, autosave espacée, backoff 5–60 s, toast limité. Ne pas enlever les validations pour faire disparaître un message.

Cartes bornées : snapshots et images limités, pas d'archivage infini. Route décimée ; géométrie d'affichage et stockage scientifique sont deux besoins distincts.

Signature debug pouvant changer selon runner CI : mises à jour peuvent exiger désinstallation, donc exporter avant. VersionCode toujours 7. Signature stable et politique de migration restent des priorités avant déploiement durable. Ne pas committer de clé privée.

## 11. Preuves et non-preuves

- Version intermédiaire 69440d4 : CI 39116842846 réussie (tests, lint, debug/release, simulation). La version sans aucun seuil doit avoir son propre run CI.
- Dernier contrôle local du changement sans seuil : 16 tests Java Odometer/RotationOdometer/RouteHistory passent, dont 1000 incréments aller-retour de 1 mm donnant 1 m et déplacement net nul.
- Rotation à position constante : zéro distance et vitesse. Même série de positions avec/sans quaternion : distance et vitesse identiques. Cela vérifie le calcul, pas les estimations ARCore sur téléphone.
- Deux scènes procédurales fermées, trois tours : A vrai 88,599752 m, estimé 88,969058 m (+0,417 %) ; B sens inverse vrai 94,719228 m, estimé 95,313665 m (+0,628 %). Erreurs fermeture ~0,879/1,298 m, RMSE ~0,555/0,677 m. Ce sont des résultats historiques de l'estimateur SIM planaire, pas une preuve live ou une validation du compteur modifié.
- Aucun benchmark EuRoC/TUM VI/ADVIO exécuté, aucun moteur recherche intégré. Aucun playback ARCore dans l'UI.
- Un export utilisateur S21 a réellement contenu un MP4 H264 640x480 de 39,5 s, décodé. Quatre freezes 470–679 ms observés. Cela établit l'existence de capture/export live, pas sa calibration ni le mode collect-only écran éteint.
- Tests shader via EGL logiciel hôte et tests Canvas/geometry : pas de benchmark matériel Android.
- Captures utilisateur privées : ne pas pousser dans Git. Des chemins d'attachments temporaires sont devenus indisponibles ; ne pas prétendre les avoir relus.

## 12. Commandes de validation

```bash
# Avec SDK Android et Gradle 8.13, Java 17
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease --no-daemon --stacktrace
python3 -m unittest discover -s tools/tests -v
./tools/simulation/run.sh app/build/reports/replay-a 3 default 15 1
./tools/simulation/run.sh app/build/reports/replay-b 3 reverse 15 1
python3 tools/simulation/validate.py app/build/reports/replay-a app/build/reports/replay-b
# Optionnel : bibliothèques EGL/GLES hôte nécessaires
python3 tools/gpu_smoke.py
```

Workflow `.github/workflows/android.yml`, artefacts conservés 14 jours : APK debug, rapports tests, résultats simulation. Une release assemblée n'est pas automatiquement signée pour publication. Installer l'APK debug exact du commit vérifié.

## 13. Suite priorisée et critères

1. Livrer APK sans deadband avec CI correspondante ; annoncer que cela ne corrige pas l'erreur de translation rapportée.
2. Capturer le test de rotation réel et tracer positions/incréments/angles, comparer support fixe et arc réel. Mesurer également translation droite, petits retours et virages ; ne pas ajuster seulement au scénario rotation.
3. Conserver un flux de poses suffisamment complet et expliciter la distance brute/bruit avant d'envisager un estimateur filtré séparé. Pas de filtre arbitraire caché.
4. Stabiliser signature/versionCode, tests mises à jour sans perte, interruption stockage/process et récupération de sessions.
5. Profiler S21 : ARCore update, GPU/Canvas, GC, encodeur, I/O, chauffe/batterie ; conserver paramètres de mesure et préciser tout changement de qualité.
6. Implémenter playback reproductible puis adapter un moteur VIO ouvert à une séquence publique en cache. Ground truth réservé à l'évaluation ; pas de scale fit caché.
7. Extraire une vraie frontière moteur/source/évaluation ; migrer ARCore avant de déclarer l'architecture interchangeable.
8. Fonctions partage demandées mais non implémentées : lien GPS daté avec précision, GPX/image, rendez-vous, puis éventuellement partage live temporaire avec serveur/expiration/révocation. Pas de localisation partagée intérieure sans repère commun. Demande de cas d'usage extérieur/intérieur restée sans réponse.
9. Jalons distances/challenges évoqués, non implémentés. Ne pas prioriser gamification avant fiabilité des mètres.

Pour chaque jalon : code/documentation, tests ciblés, CI du SHA, APK exact, preuves et limites. L'utilisateur veut des résultats vérifiables et refuse les garanties non justifiées.
