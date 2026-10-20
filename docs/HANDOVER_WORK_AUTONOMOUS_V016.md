# VECTOR VIO — HANDOVER V0.15 → WORK AUTONOME

**Date :** 21 octobre 2026  
**Dépôt :** `jboehm1/VisualOdometry` — https://github.com/jboehm1/VisualOdometry  
**Destination :** nouveau chat en **mode ChatGPT Work**, avec accès GitHub / environnement de build / jeux de données selon les outils réellement connectés.  
**Ambition :** application Android potentiellement commerciale, véritable navigation GPX robuste, PDR sans caméra performant, VIO ARCore existant et moteur VIO Camera2 natif en parallèle pour essais A/B rigoureux.

> **Important :** Aucun GPS, caméra, PDR ou VIO ne peut garantir mathématiquement un ancrage GPX « parfait » sous toutes les conditions (forêt dense, tunnel, dérive accumulée, absence de vision, multipath, nuit). L'objectif concret est de **maximiser l'exactitude mesurée, estimer honnêtement l'incertitude, refuser les faux recalages, et conserver une navigation compréhensible et sûre même en mode dégradé**. Ne jamais inventer une position précise, une fiabilité ou un succès à 100 %.

---

## 0. PROMPT PRIORITAIRE POUR CHATGPT WORK — COPIER TEL QUEL

> **MISSION LONGUE DURÉE : ingénieur principal autonome pour VECTOR VIO.** Prends la responsabilité de faire progresser **effectivement**, sur GitHub, l'application `jboehm1/VisualOdometry` vers un Android produit de qualité commerciale. La mission se poursuit par **cycles successifs de développement, tests, mesure, analyse d'échec, correction et validation**, et non par une réponse proposant uniquement des idées. Tu dois avancer aussi longtemps et avec autant d'itérations que **le mode Work et les ressources autorisées le permettent**. Quand une limite de session, de quota ou d'outil empêche de continuer, **enregistre l'état vérifiable et un handover de reprise** pour qu'une nouvelle exécution poursuive la même boucle. Ne prétends ni travailler éternellement ni continuer en arrière-plan si aucune fonction de travail persistant/planifiée ne le permet.
>
> **Commence par l'état réel, jamais par un récit d'état présumé.** Rafraîchis toutes les branches, PR, workflows et SHA ; lis `AGENTS.md`, `docs/DEVELOPMENT_COORDINATION.md` **sur `feature/slam-explorer`**, le handover présent, `docs/NATIVE_VIO_ENGINE_V015.md`, `docs/ROADMAP_CENTRAL.md`, `docs/GPX_AR_WORLD_GUIDE_V013.md`, la documentation PDR et le journal de coordination `docs/coordination/nativevio-commercial-v015-20261021.md`. L'intégration historique est `feature/slam-explorer`, pas `master` (branche 2019). Les références Git ci-dessous datent du 21/10/2026 et ne sont PAS des verrous. Préserve tout travail des autres chats, y compris les chantiers GPX, PDR, UI, Camera2, datasets et workflows ; aucune résolution de conflit par écrasement `ours/theirs`. Développe en branche/worktree isolé, PR petites et revues, intégration séquentielle, CI sur **SHA exact**. Ne force-push pas des branches partagées.
>
> **Résultat produit visé (trois pipelines distincts)** : 1) **ARCore VIO actuel** : fonctionnel par défaut, GPX/route/odometer et caméra gérée par ARCore ; 2) **VIO natif Camera2** : capteur YUV réel + métadonnées de shutter/ISO et timestamps correctement appariés + gyro/accéléro synchronisés + calibration caméra–IMU + vrai moteur existant permissif (`Basalt`, `Kimera-VIO` ou `OKVIS2`) en C++/JNI + pose métrique avec qualité, segments, flags de perte/relocalisation ; 3) **PDR camera-free** : moteur Java/Android existant, puis modèles ML inertiels **seulement après comparaison mesurée** avec baseline et vérification de licences/datasets. Garder le VIO natif expérimental et désactivé tant que capture/JNI/accuracy/thermal ne sont pas qualifiés. ARCore et Camera2 ne doivent pas ouvrir simultanément la même caméra arrière ; comparer les moteurs sur des séquences enregistrées compatibles quand possible, et avec parcours physiques appariés sinon.
>
> **Priorité absolue : ancrage du GPX réellement robuste.** Un GPX mondial est une route de référence, **pas une mesure de position**, et un alignement local VIO→ENU/WGS84 ne se déduit pas d'une seule mauvaise mesure GPS. Mettre en place ou consolider un estimateur probabiliste de **transformation locale**, avec repères et timestamps distincts, GPS/FLP/GNSS filtré, précision reçue, innovations, vitesse/course du téléphone vs marche/vélo, détection d'anomalies et multipath, pondération robuste, contrôle d'observabilité du cap/dérive, segments continus; aucune « snap to GPX » utilisée comme vérité pour gonfler les performances. Conserver les points ancrés à haute confiance et afficher explicitement les parties **observées**, **estimées** et **indicatives**. À perte de fix ou de VIO : position marquée incertaine, propagation limitée avec PDR/IMU selon conditions et covariance croissante, **ne jamais dessiner un faux chemin parfaitement ancré**. À retour du GNSS/VIO : recaler de façon cohérente, seuil anti-saut, validation multi-fix et interpolation de présentation uniquement lorsque légitime ; le monde ARCore et le monde natif **ne se transfèrent pas par simple rotation**. Prévoir correction manuelle explicite de départ/heading si l'ancrage n'est pas observable. Tester intersections, boucles, détours, altitude/relief DEM quand disponible, forêt/tunnel, GPS médiocre, nuit, téléphone en poche, course/vélo, changement moteur et retour après interruption.
>
> **Moteur PDR le meilleur démontré, pas le plus sophistiqué.** Inspecter la branche `feature/camera-free-pdr`, les PR PDR actives, les estimations Java `pdr/PdrEngine.java`, `StepPeakDetector`, `PdrCalibration`, `SensorTimingMonitor`, la voie neural portable et les pipelines réels OxIOD/RoNIN/RIDI/ADVIO/TLIO. Séparer modèles `port de téléphone`, `détection de pas`, `longueur du pas`, `orientation corps`, `translation world-frame`. Comparer marche, course, vélo, immobile, escalier, téléphone main/poche/sac. Éviter les transformations IMU→ENU inventées et la fuite de ground truth. Adapter la taille du modèle aux ressources CPU/batterie ; modèle AI seulement s'il dépasse statistiquement les bonnes baselines sur test sujets/appareils tenus à l'écart. Usage GNSS comme ancre facultative, jamais comme vérité masquée du PDR offline.
>
> **Comparaison rigoureuse de performances VIO et PDR — même protocole.** Définir un registre A/B versionné : SHA source/build, dataset + SHA256 + licence, moteur exact, téléphone/SoC/OS, calibration intrinsics/distortion/extrinsics/offset/rolling-shutter, source de vérité terrain indépendante, temps et repères, fenêtre retenue hors tuning, mode captation, résolution/FPS/exposition/ISO, environnements et matériel, défaillances. Métriques VIO : **ATE métrique en mètres sans scale fitting**, RPE translation/rotation 1 s / 10 s / 100 m, dérive % distance, qualité de l'échelle, durée/fraction de suivi, p95 de relocalisation, latence p50/p95/p99, FPS image et poses, CPU/GPU, RAM, énergie J/min, Wh/km, température, frames dropped, usage disque. Métriques PDR : erreur distance %, erreur de cap, dérive de position/m et par minute, événements pas précision/rappel/F1, faux pas à l'arrêt et sur vélo, dépendance à l'orientation du téléphone, CPU/mémoire/énergie. Métriques GPX : cross-track error vs trajectoire **indépendante**, erreur de distance au virage, délai/perte d'ancrage, faux-lock, instant de retour et sauts visuels, disponibilité navigation (%), comportement en confiance faible. Comparer dans le **même dataset brut** les moteurs capables de lire les mêmes capteurs ; comparer ARCore Android vs Camera2 sur **parcours appariés indépendants** avec référence RTK/points géoréférencés et conditions randomisées ABBA, jamais une comparaison pseudo-simultanée trompeuse. Rapporter médiane, p95, intervalles de confiance, sous-groupes et coût ressources/qualité (front Pareto). Séparer un score de forme `Sim(3)` post-hoc d'un vrai score d'odométrie métrique. Le bilan comparatif doit déclarer `pas comparable` si données, horloges, repères ou estimations ne satisfont pas le contrat.
>
> **Ordre de développement concret.** P0 consolidation CI + conservation des fonctions (menus, GPX full-world, affichages, CSV/export, caméra, PDR, tests, Android target, UI) ; P1 capture de dataset smartphone pour A/B et comparaison métrique indépendante ; P2 GPX ancrage/relocalisation fail-closed avec tests sim et vrais trajets ; P3 PDR entraînement/validation réel ; P4 VIO natif moteur permissif PINNÉ et compilé sur host, puis dataset réel EuRoC/TUM-VI/Mobile-GVIO avec **moteur exact** avant NDK/JNI ; P5 Camera2 `YUV_420_888`, capture/IMU/temps/extrinsèques et JNI ; P6 contrôleur exposition asservi aux vrais inliers et repli ARCore ; P7 optimisation appareil/UX/batterie et readiness commercial. Dans chaque cycle, choisis **la tâche débloquante qui améliore le plus la qualité utilisateur par unité de risque et coût**, pas simplement l'ajout de features.
>
> **BOUCLE AUTONOME IMPÉRATIVE POUR CHAQUE CYCLE** : (1) inspecter dernier SHA/workflows/rapports, (2) choisir le défaut P0 ou gain mesuré le plus utile, écrire critère d'acceptation, (3) implémenter petit correctif isolé et tests, (4) exécuter tests unitaires + intégration / dataset réel pertinent, (5) obtenir métrique avant/après avec preuve et budgets CPU/batterie, (6) corriger les régressions et répéter si nécessaire, (7) mettre à jour **code, tests, README, `docs/ARCHITECTURE.md`, documentation module, `docs/ROADMAP_CENTRAL.md`, journal `docs/coordination/` et Atlas HTML `docs/VECTOR_VIO_ATLAS_INTERACTIF_v5.html`**, (8) commit/push et CI au HEAD, (9) mettre à jour PR, état des tâches, journal des essais, prochain problème prioritaire, (10) **redémarrer au point (1)** tant que les ressources et permissions le permettent. Pour une longue exécution, faire des checkpoints durables régulièrement et limiter le nombre d'itérations sans gain mesuré avant de réexaminer l'hypothèse.
>
> **Ne me sollicite pas pour des détails déjà établis.** Pour un choix technique réversible, prends la solution la plus modulaire, libre/pérenne et mesurable. Pour une action irréversible, perte de données, force-push, suppression, publication Play Store, dépenses, installation de services payants, exposition de données privées ou acceptation de licence, **demande explicitement mon autorisation**. Ne supprime pas les enregistrements utilisateur ni les expériences d'autres branches. Si un Android réel, une calibration matérielle ou une vérité terrain n'est pas accessible, marque la validation `BLOCKED: external evidence needed`, fournis exactement l'expérience manuelle à exécuter, puis avance sur une tâche indépendante ; ne simule pas un test terrain comme preuve.
>
> **Règles commerciale + qualité.** Offline-first, privacy by default, mesures sensibles exportées uniquement sur action de l'utilisateur, licences du moteur **et toutes ses dépendances** auditées, pas d'embarquement GPL/AGPL dans une app propriétaire sans décision explicite sur obligations/double licence, attribution conforme Google ARCore, verrouillage des versions, intégrité/SBOM, signature de production séparée, tests de migration des données debug→release, données sûres et crash reports opt-in. Favorise valeur utilisateur/précision/efficacité plutôt qu'un backend complexe ou un compte payant prématuré. Ne prétends pas une qualité de sécurité/certification pour secours ou évacuation.
>
> **Livrables minimaux avant chaque checkpoint majeur** : matrice de tâches avec statuts et preuves, SHA Git, PR liée, commandes exécutées et résultats réels, bugs corrigés, comparaison ancienne/nouvelle métriques et coût par environnement, graphiques/tableaux de comparaison si données disponibles, lien GitHub Actions, documentation + Atlas HTML mis à jour, APK extraite + checksum **si et seulement si** job réussi au SHA pertinent, blocages restants et une nouvelle version de CE handover. Ne t'arrête pas dès qu'un lot est terminé si tu peux travailler utilement sur le prochain sans m'attendre.

---

## 1. État Git connu au début de ce handover

⚠️ **Toujours revérifier les SHA et workflows** : des autres conversations peuvent pousser entre-temps.

| Élément | Référence revue | Ce qu'elle signifie |
|---|---|---|
| Intégration Android | `feature/slam-explorer` SHA `3d251b8e06536a972b88bcf01bd34912ea5ceabd` | Base actuelle à ne pas confondre avec `master` historique |
| GPX global | PR #38, branche `feature/v013-full-gpx-global-lod`, SHA `329a63133547a7d88ebda2babcba6b42303082e2` | GPX 2D monde, AR près/loin, ancrage non certifié terrain |
| Hybrid/Camera2 bases | PR #39/#40/#41, SHAs `a45403362`, `27eec284`, `836bd5e8` | Camera2 lab shutter/ISO, IA PDR diagnostique, courbe qualité ; travaux empilés |
| Gradient + flow expérimental | PR #42, `feature/v014-region-gradient-exposure-optflow`, SHA `7f8da2396e01d930b3c762bf53c04600f2d5956f` | Principe de publication recréé, pas équations reproduites exactement ; aucune pose VIO native |
| Nouveau travail V0.15 | **PR #43**, `feature/v015-commercial-nativevio-contracts`, base PR #42 | Contrats timing et calibration Camera2/IMU, C++ API dummy, contrôle licences, docs produit/Atlas, analyse comparative de résultats mesurés ; PAS un VIO qui fonctionne encore |

**Chaîne des PR** : `feature/slam-explorer` ← PR #38 ← PR #39 ← PR #40 ← PR #41 ← PR #42 ← PR #43. **Ne pas merger #43 directement dans l'intégration** sans vérifier les ancêtres et les conflits de fonctionnalités. Avant toute fusion, inspecter et conserver PDR, GPS/FLP, routes/menus, exports et les travaux en cours.

### Ce qui a été effectivement ajouté dans la branche PR #43

- `app/src/main/java/dev/jboehm/vectorvio/nativevio/NativeVioInputGate.java` : identifie la calibration, vérifie intrinsics, extrinsèques rigides 4×4, horloges, fenêtre gyro/accel autour de la mi-exposition, timestamp strict, segmentation des gaps. **Ne calcule aucune pose et n'est pas branché au capteur**.
- `app/src/test/java/dev/jboehm/vectorvio/nativevio/NativeVioInputGateTest.java` : tests JUnit synthétiques, y compris interruption, horloge, uncalibrated, pas d'IMU et géométrie invalide.
- `native/vio/VioEngine.hpp` + `native/vio/test_vio_contract.cpp` : **API C++17 neutre**, structures/calibration, image détenue par valeur, IMU et pose; un faux moteur de smoke retourne `nullopt` volontairement, **pas Basalt**.
- `.github/workflows/native-vio-contracts.yml` : compile/exec C++17 smoke, exécute tests contrat inventaire, affirme que le **release gate reste fermé** tant que les licences ne sont pas auditées. Workflow **SUCCESS** au SHA `59f0c275` (run GitHub `39514680252`), puis revérifier le HEAD final.
- `tools/vio/third_party.json` et `tools/vio/release_license_gate.py` + tests : inventaire licence initial, **bloque** une publication commerciale non revue ; source Basalt miroir épinglée au SHA `0f3b2b52c807f70ff4e2973ce253c73329eea7bc` mais moteur pas téléchargé/compilé.
- `tools/vio/compare_measured_runs.py` + `tools/tests/test_vio_measured_scoreboard.py` : **scoreboard de rapports mesurés** : contrôle métadonnées, interdit ajustement d'échelle/horloge sur GT, Pareto précision/latence/énergie/coverage, sépare matériel/dataset. **Ce n'est pas un moteur VIO ni la source des mesures.**
- Documentation : `docs/NATIVE_VIO_ENGINE_V015.md`, `docs/COMMERCIAL_RELEASE_GATES.md`, `docs/THIRD_PARTY_NOTICES.md`, `docs/coordination/nativevio-commercial-v015-20261021.md`, mises à jour `README.md`, `docs/ARCHITECTURE.md`, `docs/ROADMAP_CENTRAL.md`, `docs/VECTOR_VIO_ATLAS_INTERACTIF_v5.html`.

### Qualité des preuves actuelles

- **Arduino/Android** : la CI de la branche a réussi à plusieurs SHA précédents, mais le **dernier SHA après ajout du scoreboard et handover doit être testé à nouveau**. Une première compilation a échoué temporairement lorsqu'un constructeur Java avait été modifié avant ses tests ; les tests ont ensuite été corrigés et la CI suivante était réussie. Ne pas réécrire l'histoire ou déclarer HEAD vert si HEAD ne l'est pas.
- **Native C++** : compilateur C++17 sur runner GitHub a réussi pour l'interface et un dummy; le VIO Basalt/Kimera natif n'a toujours pas été compilé.
- **Dataset réel** : `tools/campaigns/mobile_gvio_raw.py`, `mobile_gvio_ab.py` sont des baselines sur vraies images/gyro, mais **sans échelle métrique online**. Le RMSE Sim(3) avec échelle/offset ajustés après coup ne certifie PAS l'odométrie Android. ADVIO a testé de la vidéo réelle/IMU et scoré un ARCore **extérieur**, pas le moteur VECTOR Android.
- **Terrain S21** : l'utilisateur a constaté que raccourcir le shutter diminue fortement le flou ; pas encore de benchmark VIO→GPX métrique indépendant ni campagne thermique/batterie prouvée.

---

## 2. Matrice de priorité et définition de terminé

| ID | Priorité | Tâche | Définition de terminé, non négociable |
|---|---:|---|---|
| R0 | P0 | Audit état Git, coordination, CI | SHAs/branches/PRs actuels, conflits, verdict exact-head, pas de perte de fonctions |
| G1 | P0 | GPX global stable | GPX complet conservé; tracé local observé/lointain indicatif, aucun tracé ancré sur une pose invalide |
| G2 | P0 | Ancrage GNSS↔VIO/PDR | Transformations WGS84/ENU/ARCore/VIO documentées, innovations et covariances, gating multipath/outliers, validation en vrais trajets |
| G3 | P0 | Perte de GPS et réacquisition | Pas de fausse précision, transitions de confidence, pas de saut utilisateur opaque, tests tunnels/forêt/retour GPS |
| G4 | P1 | Bifurcations / route matching | Comparaison multi-candidats sans circularité GPX→GT ; mesures sur croisements réels et erreurs contrôlées |
| P1 | P0 | PDR Java fonctionnel | Relecture exact moteur Android, zéro pas faux à l'arrêt/vélo, distance/yaw calibrés, export/replay |
| P2 | P1 | PDR IA léger | Progrès réel sujet/appareil held-out vs PDR classique, ablations, coût CPU/J/temps |
| V1 | P0 | VIO hôte réel | Moteur C++ permissif épinglé/compilé qui sort des poses à partir d'images+gyro+accel d'une séquence réelle; ATE métrique sans GT-assisted scale |
| V2 | P0 | Camera2 natif YUV | Flux `ImageReader`, appariement metadata YUV+ISO/exposure, RingBuffers bornés, fermeture sûr des images, timestamps/calibration prouvés |
| V3 | P1 | JNI Android + tracking | `.so` arm64 dans une APK, tests caméra/perms/app pause/resume, poses sources explicites, aucun saut monde |
| V4 | P1 | Régulation optique VIO-aware | Différence **mesurée** d'inliers/tracking/ATE/blur/batterie, profil walk/bike/low light et fallbacks |
| B1 | P0 | Ledger A/B/GPS/GPX/PDR | Méthodes alignement rigide fixed/presurvey, données réelles, score comparable, effet de la couverture |
| B2 | P1 | Coût/performance | Pareto précision/latence/J par minute/Wh par km/FPS/CPU/mémoire, mesures sur appareils identiques |
| U1 | P1 | Produit commercial | Offline-first, confidentialité, licence/SBOM, signature release, migration, ergonomie et crash-safe |
| D1 | P0 | Documentation et Atlas HTML | Docs courantes avec preuves/SHA/limites ; chaque code significatif a son test et note d'intégration |

**Terminé ≠ « 100 % parce qu'un test simulé passe ».** Tout composant réel doit avoir preuve sur hardware ou dataset représentatif, CI du SHA final et critères de fiabilité. Un passage de sortie critique encore bloqué doit afficher `BLOCKED`, pas être masqué.

---

## 3. Protocole de comparaison honnête VIO / PDR / GNSS

### 3.1 Comparer ARCore et le moteur natif

1. **Offline same raw** : sur Mobile-GVIO/EuRoC/TUM-VI, faire lire **exactement les mêmes images, IMU et timestamps** à chaque moteur compatible. ARCore ne doit pas être affiché comme évalué ainsi si l'Android Playback ne prend pas ce flux brut standard ; préciser cette limite et ne pas inventer de dataset « ARCore compatible ».
2. **Android terrain ABBA** : même Galaxy S21, trajet physiquement mesuré/RTK GNSS quand le ciel est dégagé; répéter `ARCore → Camera2 VIO → Camera2 VIO → ARCore` (ou randomiser la séquence) dans une luminosité et vitesse comparables. On ne peut pas prétendre les deux images de la même caméra enregistrées simultanément. Contrôler la répétabilité météo/voile, horaire, cadence et dérive de départ.
3. **Metric**, **shape** : métriques distinctes — odométrie métrique = extrinsèques calibrées + vitesse/translation scale estimated **during inference** ; score shape-only Sim(3) = *posthoc diagnostic*, interdit de l'utiliser comme preuve de distance.
4. **Terrain complexe** : démarrage à l'arrêt, vélo vitesse constante, courbes, couloirs, ciel/sol homogène, contre-jour, dusk/night, tunnel, feuillage, tremblements, crop/zoom/focus change, GPS outliers.
5. **Énergie** : test appareil sur batterie/puissance contrôlée, baseline écran allumé/éteint comparable, `J/min` et si pertinent `Wh/km`; les compteurs Android internes sont estimatifs — le dire. Exposer échelle de confiance/statistique.

### 3.2 Mesures recommandées

| Dimension | VIO ARCore / Camera2 natif | PDR | GPX/geo-ancrage |
|---|---|---|---|
| Exactitude | ATE SE3 métrique (sans fit d'échelle), RPE translation/rotation, distance % | distance %, dérive xyz, cap, pas F1 et faux positifs | cross-track vs GT, erreur lat/lon/alt et intersection |
| Disponibilité | % frames/temps en `TRACKING`, temps `LOST`, récupération p95 | couverture périodes sans caméra, détection de mode | % navigation ancrée fiable, taux faux-lock |
| Rapidité | FPS images/poses, latence p50/95/99, temps d'init | samples/s et latence détection/fusion | délai GPS→world et temps d'ancrage/reacquisition |
| Ressources | CPU/GPU, RAM, température, J/min | CPU/RAM/J/min | coût de geofusion + coût de dessin GPX |
| Robustesse | flou/noise/saturation, gyro bias, rolling shutter | port du téléphone, escalier/course/vélo | canyon urbain/multipath, sous-bois, blackout |
| Risque produit | compatibilité/licence/calibration support | datasets licences + généralisation | fausse confiance et risque utilisateur |

A/B par **scénario et par matériel comparable**, pas de score composite arbitraire unique. Utiliser Pareto front sur précision/availability/energy/latency, mais préserver les métriques brutes. Si GT introuvable : rapport qualitatif + indicateurs internes seulement, pas ATE.

### 3.3 Données / outils déjà existants

- `tools/benchmark/bench.py` — préparation EuRoC/TUM-VI et scoring ; **ne teste pas ARCore Android** par magie.
- `tools/eval/trajectory_metrics.py` — ATE/RPE alignement yaw+translation par segment, sans scale; **ne prouve pas repère global multi-segment**.
- `tools/campaigns/mobile_gvio_raw.py`, `mobile_gvio_ab.py`, `mobile_gvio_score.py` — images/IMU smartphone réels, mais baseline monoculaire unscaled.
- `tools/pdr/...` et `tools/campaigns/imu_pdr.py` — distinguer PDR Java exact de la baseline Python.
- `tools/vio/compare_measured_runs.py` (PR #43) — tableau d'enregistrements d'A/B déjà mesurés, vérifie le protocole et refuse les comparaisons illégitimes.
- `tools/vio/release_license_gate.py` — blocage contrôles de droits pour redistribution.

---

## 4. Contrats techniques à ne jamais trahir

- **Repères :** ARCore est local (+Y vertical), le VIO natif a un monde local différent, GPX est WGS84; ENU et quaternions/transformations sont explicitement identifiés, tracés, versionnés. Un nouveau monde local nécessite recalage documenté, pas une fusion de points naïve.
- **Horloges :** Camera2 REALTIME vs UNKNOWN, `Image.getTimestamp()` + CaptureResult du même frame, SensorEvent BOOTTIME; calibrer offset et rolling shutter, rejeter les appariements douteux; ne pas inventer un resampling continu lorsque la donnée est perdue.
- **Photométrie :** une image nette et riche en inliers est souvent plus utile qu'un SNR idéal, mais un shutter court augmente le bruit photonique. Contrôle multiobjectif; Camera2 lab n'impose PAS shutter à ARCore. Reproduction article Sun et al. **inspirée de l'abstract**, pas fidèlement identique sans PDF/maths vérifiés.
- **PDR :** gyroscope/accéléro seuls n'identifient pas la vitesse d'un vélo à translation uniforme; l'optical flow indique vitesse apparente **dépendant de profondeur**, pas km/h. PDR marche sans caméra reste sensible au port du téléphone, aux biais et à l'orientation corporelle.
- **Qualité :** TrackingState ARCore est grossier et sans covariance sûre. Un score issu de gradient/flow image ne remplace pas le nombre d'inliers de l'estimateur. Les covariances manquantes restent `unknown`, jamais `0`.
- **Vie privée + licence :** pas d'envoi automatique de vidéo/route/GPS/IMU ; toutes données exportées opt-in. Aucune bibliothèque GPL embarquée par accident ; audit **transitif** des dépendances natives, notices/Google terms et datasets. Pas de publication commerciale automatique.

---

## 5. Checkpoint durable que Work doit tenir à jour

Le chat Work doit maintenir dans le dépôt `docs/work/ACTIVE_SPRINT.md` (ou fichier de même rôle, sans écraser un sprint actif) au moins :

```yaml
project: VECTOR_VIO
objective: commercial-grade_GPX_PDR_dual_VIO
integration_branch: feature/slam-explorer
integration_sha_inspected: <actual SHA>
working_branch: <unique branch>
working_sha: <actual SHA>
last_known_ci:
  android: {run: <id>, sha: <SHA>, result: <status>}
  native_contract: {run: <id>, sha: <SHA>, result: <status>}
backlog:
  - {id: G2, state: in_progress, accepted_when: <objective measurable>}
  - {id: P2, state: blocked, missing: <external real dataset or calibration>}
baseline_measurements: <paths to artifacts and hash, or NOT_MEASURED>
new_measurements: <paths to artifacts and hash, or NOT_MEASURED>
regressions: <failures, links, fixes>
shared_files_touched: <paths and why>
next_command: <fully reproducible immediate next action>
blockers: <explicit; no manufactured successes>
```

À chaque arrêt imposé par outils/quota/durée, **commit/push de ce fichier**, du journal de résultats, de la feuille de route et de l'Atlas HTML si actualisé. À chaque reprise : lire ce checkpoint, inspecter le Git **live** et la CI, puis exécuter **`next_command`** si toujours valide, pas recommencer le design depuis zéro. Récupérer les tests échoués d'Actions avant nouvelle feature. Ne pas transformer un échec réel en `100 %` ni prétendre qu'un agent restera actif à l'infini.

---

## 6. Liens rapides

- **PR #43 :** https://github.com/jboehm1/VisualOdometry/pull/43
- **PR #42 :** https://github.com/jboehm1/VisualOdometry/pull/42
- **Branche v0.15 :** https://github.com/jboehm1/VisualOdometry/tree/feature/v015-commercial-nativevio-contracts
- **Docs techniques :** `docs/NATIVE_VIO_ENGINE_V015.md`, `docs/COMMERCIAL_RELEASE_GATES.md`, `docs/CAMERA_PAPER_REPRODUCTION_V014.md`
- **Atlas interactif V5 :** `docs/VECTOR_VIO_ATLAS_INTERACTIF_v5.html`
- **Données PDR :** `docs/PDR_DATASETS.json`
- **Protocoles GPX :** `docs/GPX_AR_WORLD_GUIDE_V013.md`
- **Coordination :** `AGENTS.md`, `docs/DEVELOPMENT_COORDINATION.md`

**Instruction d'exécution finale :** Lire ce document, confirmer le SHA HEAD, corriger toute CI rouge, lancer le cycle P0→P1→… en implémentant du code et en produisant des preuves; poursuivre d'une itération à la suivante, toujours sous contrainte de ressources et sans inventer de résultats. Mettre à jour ce handover à chaque reprise significative.