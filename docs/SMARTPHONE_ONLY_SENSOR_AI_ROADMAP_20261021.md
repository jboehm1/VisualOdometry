# VECTOR VIO — Smartphone-Only First : maximiser RGB + IMU + capteurs Android sans matériel dédié
**Audit technique du 21 octobre 2026** · Documentation de recherche à destination de l'Atlas, **pas une déclaration d'implémentation Android**. Cette étude répond au cas **obscurité / faible visibilité / fumée**, en donnant la priorité stricte au téléphone seul. Le matériel spécifique est traité exclusivement en **annexe H**.

> **Verdict physique :** Aucun modèle d'IA ne peut « voir » des murs, des personnes ou des obstacles complètement occultés par une fumée optiquement opaque à partir de la seule caméra RGB du Galaxy S21. Les capteurs inertiels peuvent prolonger **temporairement** une trajectoire, pas découvrir les obstacles. L'objectif ingénierie honnête est d'exploiter toute information observable, estimer l'incertitude, refuser une fausse route et documenter les interruptions. Pas de guidage d'évacuation ou d'engagement incendie.

## Mise à jour : candidats 2025–2026 à confronter aux anciennes références

La date de 2026 change la sélection : **RoNIN et TLIO sont des baselines scientifiques solides, pas nécessairement les meilleurs modèles modernes à adopter**. L'ordre ci-dessous correspond à notre cas Galaxy S21 et à la reproductibilité, **pas à un classement de performances prouvé**.

| Modèle / article | Ce qui compte pour S21 S0 | État réel au 21/10/2026 | Restrictions / décision |
|---|---|---|---|
| **EqNIO (ICLR 2025)**, [article](https://proceedings.iclr.cc/paper_files/paper/2025/hash/6554c4151c2ccbd36da2d55c015b2039-Abstract-Conference.html), [code](https://github.com/RoyinaJayanth/EqNIO) | Cadre inertiel canonique *équivariant* limitant la dépendance à la rotation arbitraire du téléphone; applique aux architectures RoNIN et TLIO | dépôt source disponible, portage mobile et poids préentraînés prêts à l'emploi **non vérifiés** | **Licence non établie** depuis la page du dépôt : recherche/benchmark host seulement, bloquer embarquement APK |
| **MARIO (CVPR 2026)**, [article](https://arxiv.org/abs/2606.02996), [code](https://github.com/SPICExLAB/MARIO) | Très intéressante **physique du mouvement humain**, prior de pose appris sur une IMU, avec baromètre/magnétomètre potentiels. L'article rapporte jusqu'à −36 % dérive via pose prior sur Nymeria; les auteurs seulement, pas S21 | dépôt officiel dit **aucun checkpoint distribué**, entraînement à reprendre. La **version complète** utilise une deuxième IMU d'une paire de lunettes : **pas S0** ! | **MIT (source)**, mais SMPL/Nymeria/PoseNet et dépendances licences propres; seules configurations **mono-IMU** sont candidates S0; performances full multi-capteurs non revendicables |
| **AirIO (RA-L 2025)**, [article](https://doi.org/10.1109/LRA.2025.3581130), [code](https://github.com/Air-IO/Air-IO) | Retient les données IMU en **repère du corps** avec attitude explicite et EKF, bon témoin méthodologique de fusion; utilise dataset drones | entraîné pour **UAV**, pas piéton smartphone; expérimentation host seulement | Ne pas dire modèle prêt pour marche sans réentraînement. Licence/dépendances à auditer |
| **ZipDepth (ECCV 2026)**, [code officiel](https://github.com/fabiotosi92/ZipDepth) | **6,1 M paramètres**, faible encombrement, modèle de profondeur monoculaire RGB, démo sur mobile selon auteurs. **Plus cohérent pour un test de profondeur embarquée** que les énormes ViT; éventuellement comparateur Depth Anything V2 Small | code + poids annoncés disponibles juillet 2026, **iPhone démo ≠ S21 Android**; exporter/quantifier et benchmark avant APK | **MIT repo** (audit droits poids/dépendances). Entrée RGB visible requise, ne perce pas la fumée, profondeur non garantie métrique/GT |
| **MMVIO (IEEE Access 2025)**, [DOI](https://doi.org/10.1109/ACCESS.2025.3555319) | γ correction / CLAHE réglés par optimisation bayésienne pour prolonger les tracks FAST en faible lumière; **plus direct que grand réseau** pour première A/B | résultats sous auteurs, calcul BO sur S21 et effets de texture non mesurés | P1 en prétraitement **uniquement sur flux du tracker custom**; pas de gain présupposé |
| **Sun et al. (2025)**, [DOI](https://doi.org/10.1016/j.jai.2025.10.001) | Exposure control par **somme de gradients pondérés par régions**, dérivée première et pas lié à irradiance; plafond de shutter dépend de VO pour limiter flou | notre publication de référence confirmée (en ligne **17 octobre 2025**). Implantation *exacte* sur S21 exige tests de boucle gain/shutter, métadonnées Camera2 et comparaison métrique; **cette étude ne prouve pas que la PR47 est identique à l'article** | ROI gradient ≠ simplement normaliser l'image; ne prétendre contrôler l'exposition ARCore interne |
| **HS-ES-VINS (Sensors 2026)**, [étude](https://www.mdpi.com/1424-8220/26/18/5849) | Importance de l'instant **milieu d'exposition** et des risques exposure/gain/motion pour le suivi IMU; pertinent pour chronométrer YUV/gyro | démontré sur **stéréo industrielle global-shutter matériellement synchronisée**; le S21 rolling shutter exige autre calibration | P1 instrumentation temps seulement; ne pas copier les % de gains donnés par auteurs |

**Deux corrections de licence importantes** :
- Le [site officiel de RoNIN](https://ronin.cs.sfu.ca/) précise que **ses données et modèles préentraînés sont à usage recherche seulement**, même si le *code* est disponible sous GPL-3.0. Ne pas intégrer un checkpoint RoNIN dans un APK commercial sans droits explicites.
- [Depth Anything V2](https://github.com/DepthAnything/Depth-Anything-V2) distingue **Small Apache-2.0** et les autres tailles **CC BY-NC 4.0**; [ZipDepth](https://github.com/fabiotosi92/ZipDepth) annonce MIT pour le dépôt, mais la licence du checkpoint doit être contrôlée séparément.

**Ordre de benchmark recommandé maintenant** : 1) ARCore et Basalt vs refus QUALITY/UNKNOWN ; 2) PDR classique vs **EqNIO** (si droit d'usage vérifié) / TLIO/RoNIN sur trajectoires *smartphone piéton*; 3) **MARIO mono-IMU + pose prior** si poids entraînés disponibles légalement ; 4) **ZipDepth vs ARCore raw depth** sur images visibles (pas fumée opaque); 5) prétraitements **CLAHE/γ** vs contrôle d'exposition pondéré conforme à l'étude 2025 avec métriques ATE/RPE. Pas d'empilement systématique de tous les réseaux : consommation/latence et erreur d'identité priment.

## A. Inventaire — exclusivement les capteurs déjà dans le téléphone

| Source Android (vérifier disponibilité S21 au runtime) | Information exploitable | Proposition dans le pipeline | Limitation fondamentale |
|---|---|---|---|
| **Camera2 RGB**, flux YUV + métadonnées EXIF/capture si exposées | textures, flux optique, lignes/coins, netteté, exposition/gain, timestamps | baseline ARCore et séparément Basalt natif expérimental; score photométrique par régions, dynamique et décision flash/ISO/shutter dans Camera2 propre | faible lumière, diffusion fumée, rolling shutter, autofocus; la caméra commune à ARCore n'est pas un contrôle total du flux VIO **interne** |
| **Accéléromètre brut + gyroscope** (`TYPE_ACCELEROMETER[_UNCALIBRATED]`, `TYPE_GYROSCOPE[_UNCALIBRATED]`) | rotation rapide, inertie, mode marche/course/vélo/immobile, vibrations | propagation inertielle, détection incohérence pose, correction de bias en stationnaire ; Horodatages `SensorEvent.timestamp` | intégration de l'accélération crée dérive; impossible d'estimer une **vitesse de vélo constante** sans référence |
| **Gravity, rotation-vector, game-rotation-vector** si exposés | gravité/attitude estimée par Android, souvent utile pour orientation relative | comparaison avec filtre inertiel, calibration changement de portage | valeurs déjà **fusionnées** (pas indépendantes), rotation-vector magnétique peut se tromper en milieu ferreux; ne pas double compter comme observations indépendantes |
| **Magnétomètre, de préférence raw/uncalibrated** | champ vectoriel et perturbations, parfois signature locale répétable | calibrer hard-/soft-iron; détecter biais et utiliser champs connus comme fingerprint **après enregistrement du site** | ne donne pas de position universelle; énormément de perturbations près armoires, acier et feu |
| **Baromètre** (`TYPE_PRESSURE`) | variations de pression, potentiel changement de niveau relatif | détecter escalier/ascenseur en combinaison IMU, rejeter faux changements d'étage | météo, climatisation, ventilation, souffle; aucun étage absolu sans ancrage |
| **GNSS et FusedLocationProvider** et bruts GNSS si supportés | point global, vitesse/course et niveau de confiance, recalage extérieur→intérieur | démarrer à une vraie référence extérieure, conserver timestamps/accuracy, prendre les fixes seulement avec qualité acceptable | GNSS dégradé/absent en intérieur; vitesse GPS = vitesse globale, pas pose indoor; fonctions brutes L1/L5/ADR **à sonder** par chipset |
| **Wi-Fi scans RSSI**, **BLE scans RSSI** | empreintes radio et variations relatives | relocalisation *opportuniste* dans bâtiment où des AP/appareils existent déjà, sans poser de nouveau matériel | **dépend d'infrastructure externe déjà présente**; scans throttlés par Android, permissions et confidentialité; RSSI instable/multipath |
| **Wi-Fi RTT** si `FEATURE_WIFI_RTT` + AP FTM compatible | distance à AP compatibles | point de recalage éparse quand possible | **dépend d'AP compatible** ; ne pas attribuer cette capacité au smartphone isolé en forêt/cave sans AP |
| **Flash/torche LED intégrée** | photons dans noir, images VIO davantage observables | essai lampe ON/OFF contrôlé, flou vs bruit, adaptation possible selon UX et risques | batterie/chaleur; en fumée rétro-diffusion souvent pire, peut augmenter voile lumineux; **pas de bascule automatique en ambiance dangereuse** |
| **Capteur de luminosité** (`TYPE_LIGHT`, si exposé) | lux ambiant grossier | choix stratégie avant acquisition, diagnostic contexte | mesure différente de la caméra, ne doit pas remplacer l'histogramme RAW/YUV |
| **Microphone / haut-parleur** (strictement optionnels, consentement) | signature ambiante/écho ou événements acoustiques de recherche | R&D privée classification de conditions acoustiques uniquement | **pas un sonar 3D ni un moyen réaliste de voir derrière la fumée**; bruit et confidentialité; aucun guidage |
| **ARCore Depth API**, si ARCore runtime prend en charge | profondeur issue du mouvement et éventuels capteurs existants, confiance en RAW_DEPTH_ONLY | exploiter qualité/densité des points raw depth pour bloquer les POI 3D douteux **en mode ARCore non partagé** | requiert images et mouvement; se dégrade sous faible lumière/fumée. ARCore SharedCamera **désactive l'utilisation du capteur depth**. Ce n'est pas une seconde technologie voyant la fumée |

**Vérification prioritaire réelle sur Galaxy S21** : journaliser `Build.MODEL`, `CameraCharacteristics` (capteur, optique, capacités YUV, stabilisation, exposition, FPS, zoom, OIS), `SensorManager.getSensorList()` (name/vendor/resolution/maxRange/minDelay/actual sampling), `PackageManager.hasSystemFeature(FEATURE_WIFI_RTT)`, `ARCore.isDepthModeSupported()`, et GNSS measurement flags. Le fait qu'une capacité existe dans Android ne prouve **pas** sa disponibilité en session sur le S21 concret.

Android 12+ limite les capteurs courants à ~200 Hz en l'absence de permission et impose diverses contraintes de timing; un taux nominal demandé ne garantit pas les timestamps effectivement observés. Ne pas reconstruire des IMU à partir du temps de réception Java : utiliser les timestamps natifs.

### Attention : trois sens de « hardware »

- **NIVEAU S0, véritable smartphone seul** : caméra, IMU, baro, magnétomètre, GNSS, lampe, capteurs Android; fonctionne sans AP/beacon/QR ajouté au site (la qualité et la précision restent conditionnelles).
- **NIVEAU S1, téléphone sans accessoire mais dépendant de l'environnement** : Wi-Fi/BLE existants, AP compatible Wi-Fi RTT, carte bâtiment, repères architecturaux/QR existants, plan/door constraints. Il faut **l'indiquer séparément** et ne jamais présenter ces entrées comme auto-suffisantes.
- **NIVEAU H, matériel dédié** : radar mmWave, LWIR USB, lidar externe, IMU au pied, badge UWB, beacons posés ou GNSS RTK. Hors périmètre de l'APK S0. Voir annexe H à la fin seulement.

## B. Les meilleurs modèles/algorithmes candidats et la vérification de licences

| Piste / priorité | Entrées ; sortie | Pourquoi candidat | Faisabilité edge sur S21 ; état modèle | Licence confirmée / risque |
|---|---|---|---|---|
| **B0 ARCore** P0 | RGB + IMU gérés par SDK → poses 6DoF + tracking | **baseline robuste à battre**, déjà dans le projet | Android natif, tester suivi/nuit et profondeur séparément | SDK Google propriétaire; suivi interne non reconfigurable arbitrairement |
| **B1 Basalt** P0 | images + IMU synchronisés → 6DoF | moteur VIO open source pertinent pour contrôle Camera2 réel | C++ hôte/Android expérimental dans PR #47, synchronisation/calibration S21 et GT non validées ici | **BSD-3-Clause** sur repo moteur, dépendances à auditer |
| **B2 PDR classique + contraintes** P0 | acc+gyro+baro+mag → pas / mode / déplacement + incertitude | fonctionne lorsque la caméra est aveugle; fallback honnête en marche | coût faible; app PDR existe, calibration marche/S21 et orientation téléphone à évaluer | code propre |
| **B3 RoNIN ResNet/TCN/LSTM** P1 | fenêtres IMU prétraitées, orientation/gravity → déplacement/heading appris | bon benchmark IMU *piéton*, potentiellement meilleur PDR en faible visibilité | poids et cadence d'entrée à adapter; port Android et coût à mesurer. **Pas entraîné pour vélo, chute, fumée de feu** | **GPL-3.0** officiel : attention à intégration commerciale; checkpoints à auditer |
| **B4 TLIO** P1/R&D | fenêtres IMU → déplacement 3D + incertitude + filtre stochastic cloning EKF | intéressant pour propagation probabiliste lors de perte caméra | code cherche un dataset d'entraînement personnalisé, **pas simplement copier un APK**; coût de filtre et robustesse portage non mesurés | **BSD 3 clauses** code; données/weights séparés à auditer |
| **B5 RNIN-VIO (2021)** P1 | neural inertial motion prior + vision → VIO avec contrainte apprise | fusion apprenant à limiter la dérive en scènes visuelles dégradées | référence recherche, pas prouvé temps réel sur S21 ou smoke; comparer contre notre Basalt avec et sans prior | **Apache-2.0** repo, re-vérifier sous-modules/poids |
| **B6 Lightweight IONet / L-IONet** P1 | IMU piéton → mouvement learned léger | article évalue l'inférence sur appareils mobiles contraints | idéal pour mesurer gain au coût inférieur; disponibilité weights/code à auditer, pas de choix automatique avant test | **licence à vérifier pour implémentation** |
| **B7 Depth Anything V2 Small** P2 | **une image RGB visible** → profondeur relative apprise | utile quand géométrie RGB pauvre mais toujours observable, ou pour POI/visualisation, **pas une vérité métrique** | compression/quantization/benchmark FPS mémoire non réalisés sur S21; grosses architectures non privilégiées | **Small Apache-2.0** ; Base/Large/Giant **CC BY-NC 4.0**, restriction commerciale |
| **B8 Low-light enhancement léger (Zero-DCE++ / Retinexformer compressé) + raw gradients** P2 | RGB peu éclairé → image plus lisible + features *possibles* | comparer amélioration de nombre de tracks et erreur géométrique, **pas PSNR seulement** | coût mémoire/latence notable, risque de fausses textures et compression; ne pas imposer dans VIO avant A/B | poids/licence/code à vérifier individuellement; pas de source fixe tant que non audité |
| **B9 Classificateur d'observabilité léger (features/IMU)** P0/P1 | mesures de blur, luminance, distribution de keypoints, inliers, innovation, gaps IMU, pression → OK / DEGRADED / UNKNOWN | **meilleur rapport complexité/valeur** : ne trompe pas l'utilisateur, évite fusion de poses invalides | commencer **sans ML** par seuils calibrés, puis petit modèle TCN/gradient boosting/MLP si données réelles étiquetées | code propre; entraîner uniquement sur jeux d'entraînement, pas de leakage test |
| **B10 Self-supervised learned inertial velocity constraints (2024)** P2 | IMU → vitesse + incertitude pseudo-mesure | article RA-L affirme VIO plus robuste lorsque la vision se dégrade | code/weights annoncés mais disponibilité réelle non vérifiée; pas de preuve sur S21 ni en fumée dense | vérifier licence et reproductibilité avant adoption |

**Aucun modèle IA ci-dessus ne justifie un « mode traversée de fumée dense smartphone only »**. Mieux vaut afficher `INERTIAL_UNCERTAIN` puis `POSITION_UNKNOWN` que produire un itinéraire géométriquement arbitraire. Les modèles piétons exploitent les régularités des pas ; lorsqu'on pédale, l'accéléromètre du téléphone ne mesure pas directement la vitesse et les modèles piétons ne sont pas transférables.

## C. Architecture cible capteurs & IA, orientée observabilité

```text
Sensors native Android
 Camera2 YUV + capture metadata   IMU hardware (clock monotonic)     barometer + mag/light
             |                           |                            |
 Photometric ROI score              gyro+acc bias calibration       quality+context
 blur/flow/inlier score             step/mode classifier             floor-event
             |                           |                            |
       VIO estimator ARCore           PDR / learned PDR              opportunistic
       OR Camera2→Basalt              (walk only if trained)         GNSS/radio
             |                           |                            |
             +---------------------------+----------------------------+
                                      |
                           Quality/observability gate
                          residual innovation, anti-spike
                          recent anchored pose, covariance
                                      |
                      SAFE DECISION: POSE_VALID / DEGRADED
                        INERTIAL_UNCERTAIN / POSITION_UNKNOWN
                                      |
                 Display/record trajectory WITH source, time & confidence
                      never infer obstacle geometry in dense smoke
```

La qualité a **deux dimensions séparées** : (1) confiance du *tracking caméra/VIO* et (2) incertitude de la *position globale/relative*. Une caméra noire peut avoir une orientation gyro valide durant quelques secondes mais une translation complètement inconnue. Un correcteur magnétique ne doit pas modifier une position si les perturbations locales sont fortes. Un point GPS en intérieur peut être visuellement bien situé mais faux.

### Modes opérationnels S0

1. **GOOD_VISUAL** : images nettes, inliers répartis, ARCore tracking stable, alignement récent; pose VIO autorisée.
2. **LOW_LIGHT_RECOVERABLE** : tester éclairage LED intégré (consentement + contexte sans fumée), optimiser shutter contre flou ISO et SNR pour le moteur **qui contrôle réellement le flux**; afficher dégradation de la géométrie même si l'aperçu est lumineux.
3. **VISUAL_DEGRADED** : alerter; fusion inertielle **à horizon limité**, stopper la création de nouveaux POI absolus; pas de « 2D corridor » inventé.
4. **VISUAL_LOST_WALK** : seulement si état *marche* et portage entraîné sont robustement identifiés : PDR + incertitude croissante et éventuellement contraintes de plan connues. Pas de reconstruction de nouvelles pièces.
5. **VISUAL_LOST_BIKE/UNKNOWN** : IMU ne donne pas la distance d'une vitesse constante; maintenir orientation courte éventuellement, **position inconnue sans GNSS/roue/vision**.
6. **DENSE_SMOKE** : si RGB inutilisable, même comportement qu'en VISUAL_LOST; **pas de classification de fumée autoritative fondée sur image invisible**; si tâche critique, interface « NOT FOR RESCUE, POSITION UNKNOWN ».

### Capteur visuel / éclairage — ordre pratique

- Préférence P0 : **diagnostic Camera2** de l'exposition réelle, histogramme, saturation, blur, optical-flow et répartition de features, puis Q0 *faisabilité visible ou non*.
- P0 : exposer mode lampe **manuelle** pour essais de nuit, journaliser `FLASH_STATE`, lux/temps de pose effectif et thermiques/batterie. Ne pas laisser l'app basculer la torche automatiquement en atmosphère potentiellement explosive ou fumée inflammable.
- P1 : repli sur configuration shutter plus court quand mouvement élevé **si on contrôle Camera2**; ne pas prétendre changer l'AE interne d'ARCore via SharedCamera. Le mode partagé ne fournit pas un `setRepeatingRequest` libre en cours de suivi.
- P1 : détecter la meilleure **caméra physique** avec `CameraCharacteristics` et calibrations; les trois objectifs du S21 ne sont pas un système stéréo simultané simplement accessible. Nuit : souvent le grand-angle principal est préférable à l'ultra-grand-angle, mais **test appareil obligatoire**.
- P2 : augmenter localement le contraste/l'image pour l'affichage ou un tracker dédié, seulement si ATE/RPE et fiabilité s'améliorent sur vrais jeux de données. Éviter que le réseau hallucine des points.

## D. Evaluation reproductible et vraie data smartphone : prioriser 2026

| Expérience (S21 seul) | Jeu / référence | Moteurs comparés | Critère mesuré |
|---|---|---|---|
| E1 **éclairage 100%→0%** (pas de fumée) | S21 recording, parcours mesuré indépendamment ou repères vérifiés, scènes proches/distantes | ARCore, Camera2+Basalt, +torche, +qualité | couverture de pose valide, dérive erreur relative, latency, batterie; fail-closed |
| E2 **mouvement fort en lumière faible** | mêmes trajectoires, vitesse contrôlée et données IMU réelles | shutter/motion-aware, Basalt, ARCore | motion blur, inlier ratio, différences erreur et temps |
| E3 **marche caméra obturée** | RoNIN / OxIOD + collecte S21 *avec trajectoire externe validée* | PDR Java, RoNIN, TLIO si modèle entraîné, filtres gyro/baro | distance 10/50/100 m, heading drift, RTE sur mêmes fenêtres, taux de fausse pose |
| E4 **vélo caméra obturée** | collectes S21 avec vitesse/vérité externe, météo et capteurs mesurés | IMU brut/PDR; GNSS si dehors | prouver que l'IA piéton ne crée pas de fausse vitesse |
| E5 **faible visibilité / fumée simulée dans replay**, pas test incendie | images RGB dégradées, scénarios synthétiques séparés des vrais datasets | tous moteurs; le contrôle est la qualité du refus | `unknown` rapide, aucune route plausible fausse. Simulation RGB NE PROUVE PAS « through smoke » |
| E6 **radio existante** | RSSI Wi-Fi/BLE avec scan horodaté et plan/site repères si autorisés | PDR + radio versus PDR seul | amélioration incrémentale, effets des scan quotas / false relocalization |
| E7 **pression / aimantation** | escaliers, étages, couloirs métallique | PDR+baro/mag vs baseline | faux changement étage, fausses heading corrections |
| E8 **depth réellement disponible** | ARCore `RAW_DEPTH_ONLY` et confidence avec tracking | depth gating on/off | POI rejetés à bon escient, taux de depth unknown |

**Règle indispensable** : mesurer sur un parcours couvert par *toutes* les méthodes comparées. ATE n'est pas une GT indépendante si elle est alignée sur une trajectoire produite par l'estimateur lui-même; ne pas utiliser des labels du même modèle pour s'évaluer. Journaliser label source, device variant, calibration, model hash, FPS réels, timestamps, IMU gaps, temperature, battery joules/proxy et licence du dataset.

Le premier résultat utile peut être **une courbe de survie du tracking valide vs visibilité**, sans inventer de précision centimétrique. L'intégration VIO ne devient « meilleure » que si ATE/RPE et faux-association + continuité gagnent à conditions comparables.

## E. Réemploi du code existant — audit des changements

Le dépôt `jboehm1/VisualOdometry` possède déjà un moteur PDR Java, une intégration ARCore, une branche Camera2 + Basalt JNI encore expérimentale et un pipeline de replay. **Cette PR documentaire ne modifie aucun moteur.** Un état de build Android / tests ne prouve pas la précision terrain. Ce plan vise des PR séparées :
- S0-01 : instrumentation exhaustive `SensorCapabilityReport` à l'exécution + exports de timestamps/source; sans permissions inutiles et sans dupliquer les buffers ARCore.
- S0-02 : scénario caméra cachée + obscurité + lampe, avec anti-spike et compteur de couverture de poses valides.
- S0-03 : comparateur offline PDR classique vs modèles RoNIN/TLIO à partir de vrais logs; licence revue avant import weights dans APK.
- S0-04 : classification **mode de portage / locomotion** (marche, course, vélo, immobile, inconnue), prérequis obligatoire au learned-PDR.
- S0-05 : fusion de sources **sans confiance excessive** avec horizon d'IMU, conflits magnétomètre/baromètre et `POSITION_UNKNOWN` mesuré.
- S0-06 : optimisation CPU/latence/énergie (quantized TFLite/LiteRT pour modèles sélectionnés uniquement après baseline host), ne pas faire tourner tous les réseaux ensemble.

### Critères d'acceptation de chaque PR

**Tests unitaires statiques** : droit de la position à UNKNOWN sur obscurité, vélo sans GPS, bruit mag et baro, capteurs absents; aucune photo ne restaure une position si track lost.

**Benchmarks host** : mêmes logs bruts, validation des horloges et du dataset, métriques et coût comparables. **Tests Galaxy S21** : capture réelle de tout capteur, capteurs réellement présents, lecture sans overruns, énergie thermique, écran éteint / restrictions Android, reviviscence/temps de reprise. Pas de déploiement opérationnel avant mesure réelle et tests de sécurité.

## H. ANNEXE — **EXCLUSIVEMENT si le téléphone seul échoue, matériel spécifique**

**Cette annexe ne fait pas partie de S0 (smartphone seul), ni de l'APK actuel.**

- **Thermique externe LWIR USB** : pour contrastes de température en obscurité ; fumée/vapeur chaudes peuvent masquer le contenu. SDK, accès bruts, synchronisation et licence nécessaires; pas de « vision infaillible ».
- **Radar mmWave FMCW 60/77 GHz** : candidat scientifique à faible visibilité optique ; points épars, champ de vue, multipath, faible résolution sur objets fins. Nécessite host, calibration extrinsèque et une stratégie de SLAM différente, **ne se branche pas dans ARCore comme une caméra**.
- **IMU au pied / smart boots, roues odométriques, UWB anchors** : améliorent les contraintes de mouvement / position ; **tout cela est externe**, et TLIO piéton non équivalent sans portage adapté.
- **LiDAR externe / caméras thermique de secours / caméras industrielles** : produits de niches déjà occupées et très réglementées. Pas de prototype d'évacuation ou de secours.

Matériel radar/thermique et collecte de fumée réelle sont traités dans `docs/HARSH_VISIBILITY_SMOKE_DARKNESS_20261021.md` **uniquement comme une étude R&D séparée**. Ne pas mélanger les coûts, les scores ou les profils de capteurs S0 et H.

## Sources primaires consultées (21/10/2026)

1. Android Sensors Overview & sampling : https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview
2. Android Wi-Fi scan quotas : https://developer.android.com/develop/connectivity/wifi/wifi-scan
3. Android Wi-Fi RTT : https://developer.android.com/develop/connectivity/wifi/wifi-rtt
4. Android raw GNSS : https://developer.android.com/develop/sensors-and-location/sensors/gnss
5. ARCore SharedCamera constraints : https://developers.google.com/ar/reference/java/com/google/ar/core/SharedCamera
6. ARCore Depth (RAW, confidence) : https://developers.google.com/ar/develop/java/depth/developer-guide
7. ARCore certified S21 + Depth : https://developers.google.com/ar/devices/
8. Basalt source BSD-3-Clause : https://github.com/VladyslavUsenko/basalt
9. RoNIN GPL-3.0 source and baseline : https://github.com/Sachini/ronin
10. TLIO BSD-style source, retraining required : https://github.com/CathIAS/TLIO
11. RNIN-VIO Apache-2.0 : https://github.com/zju3dv/rnin-vio
12. Depth Anything V2 Small Apache-2.0 versus Base/Large/Giant CC-BY-NC-4.0 : https://github.com/DepthAnything/Depth-Anything-V2
13. L-IONet on-device, Oxford IMU data : https://arxiv.org/abs/2001.04061
14. Neural learned velocity as pseudo-measurement under degraded visual conditions : https://doi.org/10.1109/LRA.2024.3466073
15. Constraint of camera/laser in smoke + radar/foot IMU : https://doi.org/10.1002/aisy.202400241

Sources fournissent des capacités de plateforme ou résultats **des auteurs**, aucun gain chiffré attribuable à VECTOR VIO/S21 sans campagne.
