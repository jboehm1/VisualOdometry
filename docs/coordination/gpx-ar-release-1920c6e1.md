# GPX AR — Livraison logicielle reproductible (19 octobre 2026)

## Identité du code, dépôt et coordination

- Dépôt : `jboehm1/VisualOdometry`, branche `feature/gpx-ar-guidance`, PR brouillon [#24](https://github.com/jboehm1/VisualOdometry/pull/24) vers `feature/slam-explorer`.
- SHA exact de compilation : `1920c6e17c88844df99d26c56c0ca902b856e82b`; documentation ultérieure non incluse dans ce binaire.
- Base de travail consultée : `ab577a14b29261f2047503b2432e47ceb97e06ac`. Les autres modules et PR restent séparés. Pas de fusion automatique, pas de changement au calcul de distance.
- Fichiers partagés impliqués : `MainActivity.java`, `SceneMapper.java`, `HudView.java`, `WorldOverlayRenderer.java`, `SessionBundle.java`, `.github/workflows/android.yml`, classes GeoAlignment/PoseFixSynchronizer et tests. À l’intégration, conserver toutes les actions DATA, exports et nouveautés des autres branches.

## Preuves de compilation et tests — SHA exact

- [Android Build/Test/APK — CI 39317910882](https://github.com/jboehm1/VisualOdometry/actions/runs/39317910882) : **SUCCESS**, rapport HTML **163 tests exécutés, 0 échec, 0 ignoré**; `:app:lintDebug`, `:app:assembleDebug`, `:app:assembleRelease` réussis. Tests Python telemetry/GPX et replay du moteur Java sur un ZIP **synthétique** réussis.
- Régression GPS bruitée avec routes parallèles et tracés denses : étape SUCCESS.
- Rejeu de trois tours sur scènes générées : `replay-a` PASS, erreur distance +0,417 %, fermeture 0,879 m, erreur vitesse RMSE 0,0076 m/s ; `replay-b` PASS, erreur distance +0,628 %, fermeture 1,298 m, vitesse 0,0095 m/s. **Ne pas présenter ces nombres comme une précision GNSS/AR réelle**.
- [Three Campaigns — CI 39317910825](https://github.com/jboehm1/VisualOdometry/actions/runs/39317910825) : **SUCCESS**. A = VINS-Mono exemple EuRoC avec vérité publiée (moteur externe, pas VECTOR Android). B = baseline inertielle Python sur signal synthétique (pas moteur PDR Android). C = compilation d'APK avec capacité de rejeu ARCore; cela ne prouve pas la précision de guidage GPX sur téléphone.
- Les classes Java du moteur GPX et les tests d'ancres locales sont exercés, mais le rejeu ZIP GPX ne redécode **ni MP4, ni IMU, ni tracking ARCore**.

## APK installable pour essai réel (non qualifiée terrain)

- [Télécharger artifact vector-vio-debug-apk](https://github.com/jboehm1/VisualOdometry/actions/runs/39317910882/artifacts/12218742148) : archive officielle du run vert (rétention GitHub 14 jours), contenant `app-debug.apk`.
- Taille APK : 3 638 136 octets. SHA256 APK : `22b86224112e1ea4aec13cf808756e02720ce8f8e046fd138e4f39745d5c40ff`.
- Taille archive : 2 915 946 octets. SHA256 ZIP : `fc98ff9da33ea01ed49e065d27d01856ec820b8643c67d24497b249b80d836a5`.
- Archive et APK contrôlés : CRC ZIP OK, fichier `AndroidManifest.xml` présent. APK debug-signed pour installation manuelle, **pas distribution Play Store ni certification de sécurité**.
- Installer, importer un fichier GPX sous DATA → GPX AR, activer la source localisée autorisée et START/RESUME. Procédure précise : [GPX_AR_MANUAL_TEST.md](../GPX_AR_MANUAL_TEST.md).

## Acquis et limites

Implémentés/testés en simulation : repère métrique transporté, ancres ARCore locales bornées, densification/ENU WGS84 ellipsoïdal, sol observé uniquement, menus GPX/affichages persistants, replay Java sur Dataset ZIP, horloges et pertes simulées. L'option direction ON/OFF a été corrigée dans le harnais Robolectric, sans modifier l’odomètre.

**Non validés / non promis :** précision absolue du ruban dans la vraie caméra, erreur p50/p95 terrain, mode GNSS faible ou absent sur S21, stabilité longue et raccords d’ancres ARCore après relocalisation, vraie qualification FLP versus GNSS sur référence indépendante, pente/escalier/pont, batterie/thermique, occlusion, repères verticaux, calibration statistique des allowances. Le biais GPS commun ne peut pas être éliminé sans observation géographique indépendante. Un filtre temporel ne garantit pas la convergence d’un biais systématique.

Tâches restant ouvertes : L04, F03/F04/F05, H02/H03, R02/R03/R04, I02, Q02 et O01 dans [GPX_AR_TASKS.json](../GPX_AR_TASKS.json); Q03 reflète la réussite de la *branche* mais pas une intégration/téléphone qualifiés.

**Porte finale hors CI :** vraie capture S21 et tracé de vérité indépendante sur un parcours de référence, outdoor/forêt/ville, dynamique GPS bon/bruité/perdu/récupéré, 3 passages par sens, p50/p95 position/cap, fausses confiances/abstentions, stabilité ancres et bilan batterie. Ensuite seulement décider si le mode peut sortir de « expérimental ».
