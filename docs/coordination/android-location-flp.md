# Coordination — Android GNSS direct ↔ Google Fused Location Provider

Date : 18 octobre 2026. **Chantier indépendant « comparaison Android FLP ».**

## État Git et règles consultées avant la première modification
- Branche d'intégration consultée : `feature/slam-explorer` à **`372c935fc27536d417fa371d79f5f453546ebefa`**.
- `AGENTS.md` consulté : blob `9e56be8eb4a4d4575399b2324daa6fad4b05db7d`.
- `docs/DEVELOPMENT_COORDINATION.md` consulté : blob `61b55fcea7c9a2e241735f0c17505ecc8721404b`. Il décrit un ancien instantané ; le SHA ci-dessus a été vérifié directement.
- Branches parallèles relevées au lancement : B1–B8 `work/slam-roadmap-b1-b2-b8` = `51885d3747721f176c14083ccb391c0719691bac` ; PDR `feature/camera-free-pdr` = `a98b8ccf8a1a63aab8b233b2aed2f59dfd509a80` ; campagnes `feature/three-validation-campaigns` = `9dbe7a26d16b57248b9f07fb03549e28ebc096a0`.
- **Branche isolée créée exactement depuis l'intégration : `feature/android-flp-comparison`** (`372c935...`). Ne pas fusionner directement les branches PDR/B1–B8/campagnes dans ce chantier.

## Périmètre et contraintes assimilées
**Objectif :** acquérir volontairement et simultanément `LocationManager.GPS_PROVIDER` et Google Play Services `FusedLocationProviderClient` en mode haute précision, vérifier horodatages/qualité, comparer les désaccords et exporter des CSV/ZIP locaux reproductibles. **Ce n'est ni une correction automatique de GPS/GPX, ni une fusion PDR/VIO, ni une validation de précision réelle.** Les sources peuvent partager GNSS et biais communs.

**Fichiers partagés concernés par ce chantier** (modifiés sur **branche isolée uniquement**) :
- `app/src/main/java/dev/jboehm/vectorvio/MainActivity.java` : une entrée DATA supplémentaire avec dispatch **par étiquette explicite**, pas par indice partagé.
- `app/src/main/AndroidManifest.xml` : activité indépendante non exportée, sans nouveau privilège.
- `app/build.gradle` : dépendance explicite `com.google.android.gms:play-services-location:21.4.0` (version référencée par Google en juin 2026).

**Fichiers exclusivement propriétaires :**
- `app/src/main/java/dev/jboehm/vectorvio/LocationComparisonActivity.java` (écran de test A/B premier plan).
- `app/src/main/java/dev/jboehm/vectorvio/location/LocationSample.java`, `LocationPairer.java`, `LocationComparisonRecorder.java`.
- Tests JUnit et document `docs/LOCATION_FL_PILOT.md`.

### Précautions de coordination
1. Les branches PDR, B1–B8 et campagnes touchent elles aussi `MainActivity.java`. Les actions DATA indexées, notamment l'indice 9/10, créent des conflits. L'entrée de ce chantier est reconnue par son texte unique plutôt que par `i==10` ; **l'intégrateur devra conserver les actions PDR, replay, courses et GPX + VIO et tester chacune après fusion**. Ne jamais choisir tout ours/theirs.
2. Manifest final : préserver à la fois l'activité PDR et StorePlanner et la nouvelle activité de comparaison. Ne supprimer aucune permission de capture/service.
3. Ne pas modifier les algorithmes existants GPS/GPX/ARCore ; comparaison passive uniquement, explicitement lancée par l'utilisateur ; pas de permission arrière-plan, ni de télémétrie réseau propre à VECTOR.
4. `HIGH_ACCURACY` et période 1 s sont des **requêtes**, pas des garanties de précision/cadence ; la comparaison ne prouve pas que FLP ou GPS a raison. Exclure des paires (mais conserver en brut) les mesures mauvaises, obsolètes ou simulées. Un bon accord FLP–GPS peut résulter de la même source GNSS.
5. Échantillonnage VIEW/poses ARCore non modifié ; aucune influence sur compteur, navigation GPX ou position utilisée par VIO.
6. Tests Java/Robolectric + Android Lint/APK dans GitHub Actions et essai physique S21 restent des preuves séparées. Toujours citer le SHA testé.

## Étape suivante
Implémenter et tester le pilote GNSS–FLP sur cette branche, rendre le mode accessible par DATA et les sessions exportables, puis PR en brouillon contre `feature/slam-explorer`. Ne pas fusionner automatiquement la PR tant que les conflits PDR/B1–B8 ne sont pas résolus et que le comparateur n'est pas testé physiquement.

**Handoff attendu :** SHA, statut CI et tests effectivement réussis, limites sur S21 (GPS direct/FLP dérivés, batterie, permissions, lifecycle), liste exacte des fichiers partagés, prochaine intégration.


## Handoff du pilote Android GPS ↔ FLP — livraison codée et testée

**SHA fonctionnel testé : `04ad04e3036a734243e4d9f24cfb9778a697912c`**. Développement sur `feature/android-flp-comparison`, PR brouillon **#11** : https://github.com/jboehm1/VisualOdometry/pull/11. Depuis le relevé initial, `feature/slam-explorer` a avancé à `5f0105effd4fb2fb66b7d23620189c9605576de7` (campagnes VIO raw et workflows sans modification des fichiers A/B communs). **Ne pas écraser cette évolution lors d'une intégration.**

### Périmètre livré
- Nouveau contrôleur Android `LocationComparisonActivity` via DATA ; GPS direct et FLP collectés indépendamment, au premier plan uniquement, précision fine obligatoire, Google Play Services exigé ; session fermée quand l'écran disparaît.
- Noyau `location/LocationSample` / `LocationPairer` : appariement monotone un-à-un sans extrapolation, invalidation des fix imprécis/périmés/mock/dupliqués, traces brutes conservées et séparées des paires.
- `LocationComparisonRecorder` : fichiers privés fixes/paires/métadonnées et export ZIP explicite ; `LocationComparisonRepository` : retrouver/exporter/supprimer les sessions finalisées sans agir hors du dossier autorisé.
- `tools/location/analyze.py` : diagnostic hors ligne du taux et délai des positions, de l'écart GNSS↔FLP ; aucun écart traité comme vérité terrain absolue. Contrôle immobile **déclaré par l'utilisateur** uniquement, jamais certifié.
- Documentation : `docs/LOCATION_FL_PILOT.md` ; tests Java/Robolectric + Python dédiés.

### Fichiers partagés effectivement modifiés
- `app/src/main/java/dev/jboehm/vectorvio/MainActivity.java` — une entrée DATA supplémentaire avec dispatch sur étiquette et verrouillage quand VIO enregistre.
- `app/src/main/AndroidManifest.xml` — nouvelle Activity non exportée.
- `app/build.gradle` — dépendance Play Services Location 21.4.0.
Aucun changement dans `GpsNavigationService`, le moteur GPX, les algorithmes VIO/ARCore, la collection caméra/IMU ou le PDR. **Les futures fusions avec PDR, B1–B8 et replay doivent préserver toutes les actions DATA, Activity et permissions, et tester l'APK réellement réunie.**

### Preuves effectives sur le SHA fonctionnel
- GitHub Actions Android **#39171501819 — success** : `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleRelease`, GPS regression, deux scènes de replay trois tours. Rapport Android **129 tests, zéro échec** ; ZIP APK debug artefact **12152429209**.
- Trois campagnes **#39171501774 — success** : contrôles A/B/C du workflow existant. Leur succès ne prouve ni une exécution physique S21 de ce nouveau comparateur, ni une précision GNSS–FLP.
- Analyseur Python : **6 tests dédiés**, plus les autres tests hôte de `tools/tests`, réussis dans le workflow.
- Liens CI : https://github.com/jboehm1/VisualOdometry/actions/runs/39171501819 ; https://github.com/jboehm1/VisualOdometry/actions/runs/39171501774 .

### Limites et prochaine étape
Le **pilote A/B logiciel est opérationnel selon la CI**, mais les souscriptions des deux providers, cadence réelle, autorisations Google Play Services, utilisation batterie, comportement écran verrouillé et précision doivent encore être vérifiés sur un appareil physique. Pas de promesse d'amélioration de précision ni de fusion GPS/VIO/PDR validée ; aucun appareil n'a été commandé à distance.

**Décision développement :** prendre d'abord une série comparative GNSS vs FLP sur S21 (immobilité, parcours mesuré, forêt, tunnel, course), calculer les écarts à une référence externe réellement indépendante et les pertes de signal, puis seulement choisir les seuils/source de localisation à intégrer dans l'odométrie adaptative. Laisser PR #11 séparée tant que l'APK et les menus de l'ensemble multi-branches ne sont pas validés.
