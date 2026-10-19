# Coordination — GPX visual / Depth / lockscreen, 19 octobre 2026

- Branche propriétaire : `feature/gpx-visual-depth-fusion`, tirée de `feature/gpx-ar-guidance` SHA `179f395acc69318e7859dc8a311acb311af256a6`; PR #25 vers GPX seulement, pas vers branche intégration.
- Intégration commune consultée : `feature/slam-explorer` SHA `ab577a14b29261f2047503b2432e47ceb97e06ac` au démarrage du chantier GPX, à revérifier avant toute fusion.
- Lire AGENTS.md et docs/DEVELOPMENT_COORDINATION.md : préserver les autres modules (PR #3 B1–B8, PR #5 PDR, recherche #17), pas d'écrasement global de MainActivity/manifest/workflows, vérifier menus DATA, mesures poses/VIEW distinctes.
- Fichiers communs : MainActivity, SceneMapper, GpxArAndroidTest, DemElevationTest, docs GPX. Aucun manifeste ni moteur d'odométrie prévu.
- Régression héritée sur cette branche : `DemElevationTest` ne compilait pas (méthodes @Test sans `throws Exception`), corrigée dans notre branche de façon ciblée.
- Décision produit : plans ARCore déjà performants sur sentier montant; conserve comme vérité géométrique. Le module Raw Depth est **OFF par défaut**, facultatif, ne sert qu'à **occlusion des segments masqués par un obstacle** et n'ajoute aucune hauteur absolue. Aucun post-traitement ML maison.
- **Écran verrouillé** : arrêter session caméra/GPS, ne jamais prétendre suivre durant absence. Après déverrouillage GPX rechargé si vivant, recaler avec fixes/poses frais en Quick View sans START de capture. En cas de process kill, GPX stocké mais mode à réactiver.
- **Multi-cercles** : tâche F06 et maths sans mécanisme expérimental trompeur; corrélation GNSS et multipath commun modélisés, n'activer que sur métrique terrain réellement améliorée. Voir docs/GPX_AR_MULTI_CIRCLE_FUSION.md.
- À finir : CI Android/JUnit/lint/APK au SHA exact; tests de Depth activé/désactivé et absent; p50/p95 terrain, coûts S21, continuité graphique route, longue durée ARCore. Après succès publier APK et SHA, sans merge automatique.

## Preuves clôture logicielle — SHA `06acd5d2f252d690a1dde0dcadd636982bbfefce`

- Android workflow https://github.com/jboehm1/VisualOdometry/actions/runs/39323905158 — **SUCCESS**, rapport Gradle téléchargé et vérifié : **175/175 tests, 0 failure, 0 ignored**; lintDebug, assembleDebug, assembleRelease, tests Python GPX, replays Java ZIP et simulations GPS/odométrie avant/arrière réussis.
- Three Campaigns https://github.com/jboehm1/VisualOdometry/actions/runs/39323905280 — **SUCCESS**, A base VINS externe sur EuRoC, B Python PDR synthétique, C compile APK replay ARCore; aucune vérité terrain de l'overlay GPX.
- Artifact GitHub `12220303689` : https://github.com/jboehm1/VisualOdometry/actions/runs/39323905158/artifacts/12220303689 ; APK extrait (3 638 160 bytes), SHA256 `b1cf8655f77ee48eda7617fb9c7606af38db5bf67b8679885a467e1fe0a94018`; archive ZIP SHA256 `ff678d5016925430a9a74c1d54e015bcdf220771a67cce5046e30169cf7d5124`. ZIP CRC vérifié, signature APK gradle présente, manifeste présent.
- Base GPX PR #24 toujours séparée ; ce binaire ajoute Deep OFF opt-in, Quick View et VIO HOLD. Tests S21 réels, énergie, batterie et métriques d'exactitude restent nécessaires. Aucune fusion automatique.
