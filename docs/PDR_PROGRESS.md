# Suivi — odométrie PDR sans caméra (Galaxy S21)

> **Avancement pondéré : 42.1 %** · 23 tâches / 92 sous-tâches · dernière mise à jour : 2026-10-19.
>
> **Important :** ce pourcentage mesure les *livrables jalonnés* du plan, **pas une précision de localisation de 42.1 %**. Les sous-tâches passent de 0 à 100 % uniquement sur preuve; leur somme pondérée donne le pourcentage de la tâche. Les tâches sont elles-mêmes pondérées, somme des poids = 100. Une réalisation logicielle ne remplace jamais les tests réels du Galaxy S21.
>
> Branche `feature/camera-free-pdr` · [PR #5](https://github.com/jboehm1/VisualOdometry/pull/5) · [CI de référence : succès](https://github.com/jboehm1/VisualOdometry/actions/runs/39173399005) · [Documentation technique](PDR_MODULE.md).
>
> Source modifiable : [PDR_TASKS.json](PDR_TASKS.json). Mise à jour : `python3 tools/pdr/progress.py --write`. Contrôle cohérence : `python3 tools/pdr/progress.py --check`.

## Synthèse par phase

| Phase | Poids global | Avancement de phase | Contribution réelle |
|---|---:|---:|---:|
| A — Architecture et intégration | 10 % | 76.5 % | 7.65 pts |
| B — Acquisition et expérience mobile | 15 % | 67.0 % | 10.05 pts |
| C — Tests, traces et reproductibilité | 18 % | 66.7 % | 12.00 pts |
| D — Bases de données et modèles open source | 16 % | 27.5 % | 4.40 pts |
| E — Fusion avancée et robustesse | 24 % | 18.1 % | 4.35 pts |
| F — Tests terrain et livraison | 17 % | 21.5 % | 3.65 pts |
| **TOTAL** | **100 %** | **42.1 %** | **42.10 pts** |

## État démontré et limites prioritaires

- **Validé en CI :** compilation Android, lint, tests unitaires, replays synthétiques, APK debug; cela ne démontre ni précision réelle ni absence de bugs sur le S21.
- **Opérationnel au niveau logiciel :** mode sans caméra, distance pas × foulée, trace locale sous hypothèse d'orientation, capture des capteurs, export ZIP, replay.
- **Non démontré :** fonctionnement physique complet sur S21, précision à pied/en courant, téléphone en poche, navigation fiable sans GNSS, vraie correction de dérive, service écran éteint.
- **Critère de publication :** tests matériels indépendants, échappatoires en cas d'ambiguïté, erreurs et batterie mesurées. Rester en PR draft tant que ces preuves manquent.

## Tableau des tâches

| ID | Tâche | Poids global | Avancement | État |
|---|---|---:|---:|---|
| [PDR-01](#pdr-01) | Isolation et intégration au projet | 3 % | **85 %** | Partiel |
| [PDR-02](#pdr-02) | Moteur PDR minimal | 4 % | **75 %** | Partiel |
| [PDR-03](#pdr-03) | Permissions et cycle de vie | 3 % | **70 %** | Partiel |
| [PDR-04](#pdr-04) | Acquisition multisensorielle Android | 4 % | **80 %** | Partiel |
| [PDR-05](#pdr-05) | Distance et calibration | 3 % | **45 %** | Partiel |
| [PDR-06](#pdr-06) | Direction, magnétisme et GNSS | 5 % | **65 %** | Partiel |
| [PDR-07](#pdr-07) | Interface PDR et retour utilisateur | 3 % | **75 %** | Partiel |
| [PDR-08](#pdr-08) | Journalisation et export fiable | 4 % | **85 %** | Partiel |
| [PDR-09](#pdr-09) | Replay déterministe | 4 % | **85 %** | Partiel |
| [PDR-10](#pdr-10) | CI GitHub et création APK | 4 % | **85 %** | Partiel |
| [PDR-11](#pdr-11) | Métriques et référence indépendante | 3 % | **35 %** | Partiel |
| [PDR-12](#pdr-12) | Tests fonctionnels Android | 3 % | **25 %** | Partiel |
| [PDR-13](#pdr-13) | Inventaire datasets et licences | 3 % | **60 %** | Partiel |
| [PDR-14](#pdr-14) | Adaptateurs de données réelles | 5 % | **20 %** | Partiel |
| [PDR-15](#pdr-15) | Benchmark multi-datasets reproductible | 4 % | **20 %** | Partiel |
| [PDR-16](#pdr-16) | RoNIN/TLIO/EqNIO et IA embarquée | 4 % | **20 %** | Partiel |
| [PDR-17](#pdr-17) | Fusion probabiliste inertielle + GNSS | 8 % | **15 %** | Partiel |
| [PDR-18](#pdr-18) | Couplage cartographique et map matching | 5 % | **20 %** | Partiel |
| [PDR-19](#pdr-19) | Incertitude, limites et rejet intelligent | 5 % | **25 %** | Partiel |
| [PDR-20](#pdr-20) | Marche, course, portage et autres modes | 6 % | **15 %** | Partiel |
| [PDR-21](#pdr-21) | Autonomie, service Android et robustesse | 5 % | **20 %** | Partiel |
| [PDR-22](#pdr-22) | Vérité terrain et qualification Galaxy S21 | 7 % | **20 %** | Partiel |
| [PDR-23](#pdr-23) | Prêt à diffuser / fusionner | 5 % | **25 %** | Partiel |

## Détail des sous-tâches, preuves et limites

## A — Architecture et intégration

Moteur isolé sans dépendance caméra; interfaces stables.

### PDR-01 — Isolation et intégration au projet (85 %)

**Poids dans le projet : 3 %. Limite actuelle :** Pas encore d'API de pose commune pour brancher PDR, GNSS, ARCore et fusion.

| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |
|---|---:|---:|---|
| PDR-01-01 — Noyau PDR Java indépendant d'ARCore | 35 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/pdr/PdrEngine.java](../app/src/main/java/dev/jboehm/vectorvio/pdr/PdrEngine.java). Critère : Le code doit être indépendant du SDK Android. |
| PDR-01-02 — Entrée depuis l'application existante | 30 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/MainActivity.java](../app/src/main/java/dev/jboehm/vectorvio/MainActivity.java). Critère : Le menu DATA doit ouvrir PDR sans démarrer ARCore. |
| PDR-01-03 — Convention capteurs, timestamps, repères | 20 % | **100 %** | Livré : [docs/PDR_MODULE.md](../docs/PDR_MODULE.md). Critère : Documenter mètres, temps monotone, nord magnétique et sources de direction. |
| PDR-01-04 — API commune Measurement/Pose avec autres moteurs | 15 % | **0 %** | **À faire :** Spécifier et tester une interface interchangeable; ne pas casser VIO/GPS. |

### PDR-02 — Moteur PDR minimal (75 %)

**Poids dans le projet : 4 %. Limite actuelle :** Longueur de pas fixe; aucune covariance calibrée, ni apprentissage de foulée.

| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |
|---|---:|---:|---|
| PDR-02-01 — Moteur déterministe testable sans Android | 35 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/pdr/PdrEngine.java](../app/src/main/java/dev/jboehm/vectorvio/pdr/PdrEngine.java). Critère : Fonctionner dans la JVM et accepter le replay. |
| PDR-02-02 — Cumul distance sur événements de pas | 25 % | **100 %** | Livré : [app/src/test/java/dev/jboehm/vectorvio/pdr/PdrEngineTest.java](../app/src/test/java/dev/jboehm/vectorvio/pdr/PdrEngineTest.java). Critère : Comptage, doublons et anti-rebond testés. |
| PDR-02-03 — Absences de direction explicitement marquées | 15 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/pdr/PdrTrailView.java](../app/src/main/java/dev/jboehm/vectorvio/pdr/PdrTrailView.java). Critère : Ne pas dessiner de déplacement inventé. |
| PDR-02-04 — Vitesse, foulée adaptative et modèles de cadence | 25 % | **0 %** | **À faire :** Réduire le biais marche/course avec erreur quantifiée sur vérité terrain. |

### PDR-03 — Permissions et cycle de vie (70 %)

**Poids dans le projet : 3 %. Limite actuelle :** L'enregistrement s'arrête lorsque l'activité passe en arrière-plan.

| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |
|---|---:|---:|---|
| PDR-03-01 — START/STOP et terminaison sur onPause | 35 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/pdr/PdrActivity.java](../app/src/main/java/dev/jboehm/vectorvio/pdr/PdrActivity.java). Critère : Ne jamais compter du mouvement hors session. |
| PDR-03-02 — Aucune permission caméra nécessaire | 15 % | **100 %** | Livré : [app/src/main/AndroidManifest.xml](../app/src/main/AndroidManifest.xml). Critère : L'activité ne doit appeler ni caméra ni ARCore. |
| PDR-03-03 — Permission ACTIVITY_RECOGNITION et repli | 20 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/pdr/PdrSensorBridge.java](../app/src/main/java/dev/jboehm/vectorvio/pdr/PdrSensorBridge.java). Critère : Marche avec ou sans accord utilisateur. |
| PDR-03-04 — Foreground service et continuité écran éteint | 30 % | **0 %** | **À faire :** Tester Android 15, batterie, suppression du service et reprise. |

## B — Acquisition et expérience mobile

Mesure réelle, calibration et visualisation compréhensibles.

### PDR-04 — Acquisition multisensorielle Android (80 %)

**Poids dans le projet : 4 %. Limite actuelle :** Fréquences réelles, biais, dérives, capteurs absents et synchronisation non caractérisés sur S21.

| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |
|---|---:|---:|---|
| PDR-04-01 — Acquisition accel, gyro, mag, rotation vector, pression | 60 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/pdr/PdrSensorBridge.java](../app/src/main/java/dev/jboehm/vectorvio/pdr/PdrSensorBridge.java). Critère : Recevoir les données horodatées et capturer le quaternion complet. |
| PDR-04-02 — Step detector matériel et fallback | 20 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/pdr/StepPeakDetector.java](../app/src/main/java/dev/jboehm/vectorvio/pdr/StepPeakDetector.java). Critère : Séparer la détection matérielle et le repli heuristique. |
| PDR-04-03 — Inventaire et statut de chaque capteur sur S21 | 10 % | **0 %** | **À faire :** Mesurer fréquence, bruit, précision et disponibilité matérielle. |
| PDR-04-04 — Contrôle du temps, trous et pertes d'échantillons | 10 % | **0 %** | **À faire :** Journaliser et qualifier décalages temporels, batchs et saturation. |

### PDR-05 — Distance et calibration (45 %)

**Poids dans le projet : 3 %. Limite actuelle :** Une seule longueur de pas pour chaque session; 2 % de précision non démontrés.

| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |
|---|---:|---:|---|
| PDR-05-01 — Modèle distance = pas × foulée | 25 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/pdr/PdrEngine.java](../app/src/main/java/dev/jboehm/vectorvio/pdr/PdrEngine.java). Critère : Savoir distinguer distance totale de distance traçable. |
| PDR-05-02 — Longueur de pas modifiable dans l'interface | 20 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/pdr/PdrActivity.java](../app/src/main/java/dev/jboehm/vectorvio/pdr/PdrActivity.java). Critère : Contrôler la plage et conserver la valeur dans le replay. |
| PDR-05-03 — Longueur de pas dynamique marche/course | 35 % | **0 %** | **À faire :** Gérer cadence, allure, dénivelé, accélérations et changements de mode. |
| PDR-05-04 — Calibration sur parcours indépendants | 20 % | **0 %** | **À faire :** Valider erreur moyenne et percentile selon portage et vitesse. |

### PDR-06 — Direction, magnétisme et GNSS (65 %)

**Poids dans le projet : 5 %. Limite actuelle :** Le cap estimé est celui du téléphone, pas du corps; perturbations magnétiques possibles.

| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |
|---|---:|---:|---|
| PDR-06-01 — Gyro/magnétomètre et cap Android fusionné | 25 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/pdr/PdrSensorBridge.java](../app/src/main/java/dev/jboehm/vectorvio/pdr/PdrSensorBridge.java). Critère : Enregistrer les sources et rejeter les champs très suspects. |
| PDR-06-02 — Cap GNSS optionnel avec seuils qualité | 25 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/pdr/PdrEngine.java](../app/src/main/java/dev/jboehm/vectorvio/pdr/PdrEngine.java). Critère : Rejeter vitesse insuffisante et mauvaise précision du cap. |
| PDR-06-03 — Cohérence nord vrai / nord magnétique | 15 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/pdr/PdrActivity.java](../app/src/main/java/dev/jboehm/vectorvio/pdr/PdrActivity.java). Critère : Transformer le cap GNSS avant fusion avec le rotation vector. |
| PDR-06-04 — Estimer orientation du corps / téléphone en poche | 35 % | **0 %** | **À faire :** Comparer heading learned vs inertiel; erreurs de virage mesurées. |

### PDR-07 — Interface PDR et retour utilisateur (75 %)

**Poids dans le projet : 3 %. Limite actuelle :** Pas d'indicateur de confiance chiffré; aucune UI de validation scientifique.

| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |
|---|---:|---:|---|
| PDR-07-01 — Écran dédié mesures sans caméra | 30 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/pdr/PdrActivity.java](../app/src/main/java/dev/jboehm/vectorvio/pdr/PdrActivity.java). Critère : Afficher distance et nombre de pas. |
| PDR-07-02 — Trace locale sans segments fictifs | 25 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/pdr/PdrTrailView.java](../app/src/main/java/dev/jboehm/vectorvio/pdr/PdrTrailView.java). Critère : Tracer uniquement des portions où un cap est disponible. |
| PDR-07-03 — Export de session depuis le téléphone | 20 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/pdr/PdrActivity.java](../app/src/main/java/dev/jboehm/vectorvio/pdr/PdrActivity.java). Critère : Sélectionner la destination et produire un ZIP. |
| PDR-07-04 — Afficher incertitude, source et qualité du cap | 25 % | **0 %** | **À faire :** Statuts explicables, seuils fiables et tests d'accessibilité. |

## C — Tests, traces et reproductibilité

Comparer les mêmes mesures avec les mêmes algorithmes; prouver la qualité.

### PDR-08 — Journalisation et export fiable (85 %)

**Poids dans le projet : 4 %. Limite actuelle :** Pas de récupération robuste après crash ni protocole complet de provenance/calibration.

| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |
|---|---:|---:|---|
| PDR-08-01 — Événements rejouables horodatés | 25 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/pdr/PdrTraceWriter.java](../app/src/main/java/dev/jboehm/vectorvio/pdr/PdrTraceWriter.java). Critère : Inclure la foulée et la précision du cap GNSS. |
| PDR-08-02 — Échantillons capteurs bruts | 25 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/pdr/PdrTraceWriter.java](../app/src/main/java/dev/jboehm/vectorvio/pdr/PdrTraceWriter.java). Critère : Conserver accel/gyro/mag/rotation vector/pression et timestamps. |
| PDR-08-03 — ZIP utilisateur avec trace et métadonnées | 20 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/pdr/PdrTraceWriter.java](../app/src/main/java/dev/jboehm/vectorvio/pdr/PdrTraceWriter.java). Critère : Inclure events.csv, raw_sensors.csv, path.csv et metadata.txt. |
| PDR-08-04 — Métadonnées basiques et règles de confidentialité | 15 % | **100 %** | Livré : [docs/PDR_MODULE.md](../docs/PDR_MODULE.md). Critère : Aucun upload automatique des données personnelles. |
| PDR-08-05 — Journal transactionnel et reprise sur corruption | 15 % | **0 %** | **À faire :** Test injecté arrêt brutal/espace plein/export partiel. |

### PDR-09 — Replay déterministe (85 %)

**Poids dans le projet : 4 %. Limite actuelle :** Pas de test automatique bit-à-bit Android↔JVM sur un enregistrement réel.

| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |
|---|---:|---:|---|
| PDR-09-01 — Runner indépendant pour événements normalisés | 35 % | **100 %** | Livré : [tools/pdr/PdrReplay.java](../tools/pdr/PdrReplay.java). Critère : Relire avec exactement le moteur Java utilisé par Android. |
| PDR-09-02 — Foulée et rejet GNSS conservés | 25 % | **100 %** | Livré : [tools/pdr/fixtures/gnss-stride.csv](../tools/pdr/fixtures/gnss-stride.csv). Critère : Aucune précision GNSS inventée pendant le replay. |
| PDR-09-03 — Scénarios synthétiques régressifs | 25 % | **100 %** | Livré : [tools/pdr/check_fixture.py](../tools/pdr/check_fixture.py). Critère : Échouer en CI si les résultats attendus changent. |
| PDR-09-04 — Parité Android et moteur replay vérifiée | 15 % | **0 %** | **À faire :** Comparer sur traces du S21 avec versionnement et checksum. |

### PDR-10 — CI GitHub et création APK (85 %)

**Poids dans le projet : 4 %. Limite actuelle :** CI JVM/Android construit l'APK, mais ne simule pas des capteurs physiques réels.

| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |
|---|---:|---:|---|
| PDR-10-01 — Gradle, JUnit, lint et APK debug | 40 % | **100 %** | Livré : [.github/workflows/android.yml](../.github/workflows/android.yml). Critère : La dernière CI de la PR est verte. |
| PDR-10-02 — Tests Python et fixtures PDR Java | 25 % | **100 %** | Livré : [.github/workflows/android.yml](../.github/workflows/android.yml). Critère : Vérification reproductible sans téléphone. |
| PDR-10-03 — Artefacts APK, tests et replays | 20 % | **100 %** | Livré : [.github/workflows/android.yml](../.github/workflows/android.yml). Critère : Téléchargeables dans GitHub Actions. |
| PDR-10-04 — Exécution instrumentée émulateur Android | 15 % | **0 %** | **À faire :** Démarrage permission/refus/rotation/export sans crash. |

### PDR-11 — Métriques et référence indépendante (35 %)

**Poids dans le projet : 3 %. Limite actuelle :** ATE/RMSE calculables, mais aucun score terrain validé ni synchronisation certifiée.

| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |
|---|---:|---:|---|
| PDR-11-01 — RMSE de trajectoire dans un repère local | 35 % | **100 %** | Livré : [tools/pdr/evaluate.py](../tools/pdr/evaluate.py). Critère : Comparer sans mise à l'échelle ni rotation opportunistes. |
| PDR-11-02 — Erreur distance totale à partir de pas calibrés | 30 % | **0 %** | **À faire :** Ne jamais la confondre avec longueur du chemin visible. |
| PDR-11-03 — Synchronisation des horloges vérité terrain | 20 % | **0 %** | **À faire :** Valider offset et drift, avec tolérance instrumentée. |
| PDR-11-04 — Couverture d'incertitude et intervalles de confiance | 15 % | **0 %** | **À faire :** Mesurer calibration probabiliste sur données retenues. |

### PDR-12 — Tests fonctionnels Android (25 %)

**Poids dans le projet : 3 %. Limite actuelle :** Un smoke test Robolectric n'équivaut pas à un test matériel long.

| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |
|---|---:|---:|---|
| PDR-12-01 — Test création activité et onPause Robolectric | 25 % | **100 %** | Livré : [app/src/test/java/dev/jboehm/vectorvio/pdr/PdrAndroidTest.java](../app/src/test/java/dev/jboehm/vectorvio/pdr/PdrAndroidTest.java). Critère : Arrêt de l'enregistrement vérifié. |
| PDR-12-02 — Refus permissions et capteurs indisponibles | 25 % | **0 %** | **À faire :** Tests avec capteurs simulés, refus GNSS et Activity Recognition. |
| PDR-12-03 — Export, rotation, destruction et reprise du processus | 25 % | **0 %** | **À faire :** ZIP et données cohérentes malgré interruption Android. |
| PDR-12-04 — Session complète sur Galaxy S21 | 25 % | **0 %** | **À faire :** Preuve vidéo/trace/logcat et test écran allumé/éteint. |

## D — Bases de données et modèles open source

Données pertinentes, droits contrôlés et comparatifs scientifiques.

### PDR-13 — Inventaire datasets et licences (60 %)

**Poids dans le projet : 3 %. Limite actuelle :** Les liens sont catalogués, mais pas tous les termes d'utilisation vérifiés ni les archives fixées.

| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |
|---|---:|---:|---|
| PDR-13-01 — Catalogue OxIOD, RoNIN, TLIO, ADVIO, RIDI, EqNIO | 60 % | **100 %** | Livré : [docs/PDR_DATASETS.json](../docs/PDR_DATASETS.json). Critère : Versionner liens et restrictions connues. |
| PDR-13-02 — Validation de licence et usage par dataset | 20 % | **0 %** | **À faire :** Autoriser explicitement les téléchargements et usages retenus. |
| PDR-13-03 — Manifestes de versions, SHA256, provenance | 20 % | **0 %** | **À faire :** Garantir des téléchargements reproductibles hors Git. |

### PDR-14 — Adaptateurs de données réelles (20 %)

**Poids dans le projet : 5 %. Limite actuelle :** Adaptateur CSV générique seulement; aucun benchmark complet sur vrai dataset.

| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |
|---|---:|---:|---|
| PDR-14-01 — CSV accéléromètre générique + unités configurables | 20 % | **100 %** | Livré : [tools/pdr/convert_accel.py](../tools/pdr/convert_accel.py). Critère : Refuser unités inconnues et timestamps incohérents. |
| PDR-14-02 — Adaptateur OxIOD avec horloge, axes et GT | 20 % | **0 %** | **À faire :** Importer séquences et vérité terrain avec tests de schema. |
| PDR-14-03 — Adaptateur RoNIN et métadonnées de portage | 20 % | **0 %** | **À faire :** Séparer entraînement/test par personne. |
| PDR-14-04 — Adaptateur ADVIO avec caveats sur vérité terrain | 20 % | **0 %** | **À faire :** Conserver le caractère non indépendant de certaines références. |
| PDR-14-05 — Adaptateur TLIO avec conversion de repères | 20 % | **0 %** | **À faire :** Vérifier conventions et données disponibles légalement. |

### PDR-15 — Benchmark multi-datasets reproductible (20 %)

**Poids dans le projet : 4 %. Limite actuelle :** Aucun résultat réel robuste entre modèles; fixtures synthétiques non probantes sur la précision.

| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |
|---|---:|---:|---|
| PDR-15-01 — Fixtures synthétiques déterministes en CI | 20 % | **100 %** | Livré : [tools/pdr/fixtures/walk-east.csv](../tools/pdr/fixtures/walk-east.csv). Critère : Garde-fou de non-régression fonctionnelle. |
| PDR-15-02 — Split de validation jamais utilisé à l'entraînement | 25 % | **0 %** | **À faire :** Figer manifestes de séquences et hyperparamètres. |
| PDR-15-03 — Exécuter ≥ 3 datasets réels pertinents | 40 % | **0 %** | **À faire :** Publier erreur distance, heading et pose avec échecs. |
| PDR-15-04 — Rapport comparatif et artefacts reproductibles | 15 % | **0 %** | **À faire :** Version code/données/configuration et métriques. |

### PDR-16 — RoNIN/TLIO/EqNIO et IA embarquée (20 %)

**Poids dans le projet : 4 %. Limite actuelle :** Aucun poids de réseau entraîné, exécuté ou comparé sur S21.

| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |
|---|---:|---:|---|
| PDR-16-01 — Revue des approches et shortlist technique | 20 % | **100 %** | Livré : [docs/PDR_MODULE.md](../docs/PDR_MODULE.md). Critère : Connaître pertinence, licences et limitations. |
| PDR-16-02 — Reproduire baselines open source sur données tenues à l'écart | 30 % | **0 %** | **À faire :** Rapports vérifiés et scripts exacts. |
| PDR-16-03 — Adapter/entraîner modèle marche-course-poche | 30 % | **0 %** | **À faire :** Amélioration mesurable vs PDR classique hors entraînement. |
| PDR-16-04 — Quantifier et mesurer latence/batterie Android | 20 % | **0 %** | **À faire :** Comparaison CPU/NNAPI et stabilité mémoire. |

## E — Fusion avancée et robustesse

Contrôler dérive et ambiguïté sans masquer les limites observables.

### PDR-17 — Fusion probabiliste inertielle + GNSS (15 %)

**Poids dans le projet : 8 %. Limite actuelle :** Le système sélectionne une source de cap; il n'existe pas encore d'EKF de position avec covariance.

| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |
|---|---:|---:|---|
| PDR-17-01 — Arbitrage simple cap GNSS ou téléphone | 15 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/pdr/PdrEngine.java](../app/src/main/java/dev/jboehm/vectorvio/pdr/PdrEngine.java). Critère : Garde de fraîcheur et précision. |
| PDR-17-02 — État inertiel EKF/ESEKF + correction GNSS | 35 % | **0 %** | **À faire :** Position, vitesse, cap, biais, états non observables documentés. |
| PDR-17-03 — Covariances identifiées sur bruit mesuré | 30 % | **0 %** | **À faire :** Innovation NIS/NEES et limites de confiance expérimentales. |
| PDR-17-04 — Transitions réacquisition et perte de mesure | 20 % | **0 %** | **À faire :** Éviter sauts artificiels et surestimation de la certitude. |

### PDR-18 — Couplage cartographique et map matching (20 %)

**Poids dans le projet : 5 %. Limite actuelle :** Le GPS/GPX existe à côté; le PDR n'utilise pas encore la carte pour se recaler.

| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |
|---|---:|---:|---|
| PDR-18-01 — Moteur GPS/GPX indépendant déjà dans l'application | 20 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/gps/GpsNavigator.java](../app/src/main/java/dev/jboehm/vectorvio/gps/GpsNavigator.java). Critère : Prérequis présent sans liaison PDR. |
| PDR-18-02 — Connexion PDR aux sentiers hors-ligne | 40 % | **0 %** | **À faire :** Ne pas accrocher une trajectoire hors chemin à tort. |
| PDR-18-03 — Hypothèses d'itinéraires et intersections ambiguës | 40 % | **0 %** | **À faire :** Filtre particulaire / alternatives explicites. |

### PDR-19 — Incertitude, limites et rejet intelligent (25 %)

**Poids dans le projet : 5 %. Limite actuelle :** Pas d'indicateur de position/calibration fiable; écart entre distance et pose restant fondamental.

| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |
|---|---:|---:|---|
| PDR-19-01 — Signaler segments sans orientation | 25 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/pdr/PdrEngine.java](../app/src/main/java/dev/jboehm/vectorvio/pdr/PdrEngine.java). Critère : Garder distance totale, marquer trous dans la trajectoire. |
| PDR-19-02 — Modéliser incertitude cap et déplacement | 25 % | **0 %** | **À faire :** Croissance de la dérive cohérente avec les observations. |
| PDR-19-03 — Conserver plusieurs hypothèses de position | 25 % | **0 %** | **À faire :** Gérer sentiers parallèles, virages et réacquisition. |
| PDR-19-04 — Visualiser confiance et états degradés | 25 % | **0 %** | **À faire :** Ne jamais présenter la trace estimée comme un relevé exact. |

### PDR-20 — Marche, course, portage et autres modes (15 %)

**Poids dans le projet : 6 %. Limite actuelle :** Usage validable essentiellement à pied avec top du téléphone orienté vers l'avant.

| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |
|---|---:|---:|---|
| PDR-20-01 — Mode marche à longueur de pas nominale | 15 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/pdr/PdrEngine.java](../app/src/main/java/dev/jboehm/vectorvio/pdr/PdrEngine.java). Critère : Algorithme de base exécuté sur fixtures, pas en plein air. |
| PDR-20-02 — Modèle spécifique course, foulée variable | 30 % | **0 %** | **À faire :** Comparer impacts/cadence et distances connues. |
| PDR-20-03 — Téléphone en poche, main mobile, rotations indépendantes | 35 % | **0 %** | **À faire :** Heading corps et distance robustes avec références externes. |
| PDR-20-04 — Modèles vélo/voiture séparés, détecter non-observabilité | 20 % | **0 %** | **À faire :** Ne jamais prétendre mesurer une distance de roue via des pas. |

## F — Tests terrain et livraison

Vérifier sur S21, maîtriser autonomie, erreurs et conditions réelles.

### PDR-21 — Autonomie, service Android et robustesse (20 %)

**Poids dans le projet : 5 %. Limite actuelle :** Ne fonctionne pas en tâche de fond; aucune mesure consommation/thermique sur S21.

| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |
|---|---:|---:|---|
| PDR-21-01 — Enregistrement lorsque l'activité est visible | 20 % | **100 %** | Livré : [app/src/main/java/dev/jboehm/vectorvio/pdr/PdrActivity.java](../app/src/main/java/dev/jboehm/vectorvio/pdr/PdrActivity.java). Critère : Aucune caméra lancée pendant ce mode. |
| PDR-21-02 — Foreground service écran éteint + notifications | 40 % | **0 %** | **À faire :** Android 15, lifecycle, permission et collecte contrôlée. |
| PDR-21-03 — Profilage batterie, CPU, latence et throttling | 40 % | **0 %** | **À faire :** Sessions longues reproductibles, capteurs échelonnés. |

### PDR-22 — Vérité terrain et qualification Galaxy S21 (20 %)

**Poids dans le projet : 7 %. Limite actuelle :** Aucun parcours de référence mesuré sur le téléphone; précision inconnue.

| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |
|---|---:|---:|---|
| PDR-22-01 — Protocole de tests 100/400/1000 m documenté | 20 % | **100 %** | Livré : [docs/PDR_MODULE.md](../docs/PDR_MODULE.md). Critère : Tester marche/course, poche/main, intérieur et extérieur. |
| PDR-22-02 — Mesures indépendantes 100 m, calibration | 20 % | **0 %** | **À faire :** Comparer distance et nombre réel de pas. |
| PDR-22-03 — Boucles 400 m et 1 km, trajets non rectilignes | 25 % | **0 %** | **À faire :** Mesurer dérive, heading et retour au départ. |
| PDR-22-04 — Course, poche et transitions de posture | 25 % | **0 %** | **À faire :** Garder séries complètes, sessions ratées comprises. |
| PDR-22-05 — Seuils objectifs signés et décision go/no-go | 10 % | **0 %** | **À faire :** Ne publier de chiffres que si validés sur jeux retenus. |

### PDR-23 — Prêt à diffuser / fusionner (25 %)

**Poids dans le projet : 5 %. Limite actuelle :** APK debug oui; produit d'odométrie validé non. PR encore en draft.

| Sous-tâche | Part de tâche | Avancement | Preuve / critère restant |
|---|---:|---:|---|
| PDR-23-01 — APK debug distribué via GitHub Actions | 25 % | **100 %** | Livré : [.github/workflows/android.yml](../.github/workflows/android.yml). Critère : Artefact APK vérifié dans la CI verte. |
| PDR-23-02 — Audit confidentialité / rétention / permissions | 25 % | **0 %** | **À faire :** Tester suppression locale, export et consentement. |
| PDR-23-03 — UX et gestion d'erreurs utilisable au quotidien | 25 % | **0 %** | **À faire :** Exécuter checklist sur S21 avec interruptions et mauvaises mesures. |
| PDR-23-04 — Build de livraison, documentation utilisateur, merge | 25 % | **0 %** | **À faire :** Version signée + décision qualité avant PR prête. |

## Règles de mise à jour

1. Ajouter une preuve vérifiable dans la sous-tâche (code, test, mesure, rapport) avant de changer `accepted` à `true`.
2. Ne jamais réduire les poids pour faire monter le score. Si l'objectif change, documenter la nouvelle base et conserver l'ancienne dans l'historique Git.
3. Lancer `python3 tools/pdr/progress.py --write` pour régénérer ce tableau; la CI refuse les incohérences.
4. Les tests réels, la qualité des positions et l'IA ne passent à 100 % qu'après une preuve indépendante.
