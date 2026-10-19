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
