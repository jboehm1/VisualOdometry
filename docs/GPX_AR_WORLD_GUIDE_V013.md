# VECTOR VIO — GPX world guidance v0.13 · 20 octobre 2026

## Pourquoi la trace semblait visible seulement sous les pieds
Analyse du code existant (commit `3d251b8e06536a972b88bcf01bd34912ea5ceabd`) :
1. `ArRouteGuide.snapshot` ne donnait que des segments dans **30 m** autour du téléphone, parfois 512 segments maximum, selon l'ordre historique du GPX.
2. `GpxGroundProjector.project` rejetait **tout segment hors des polygones de sol effectivement détectés**. Un ARCore horizontal plane n'est qu'une région observée ; il ne couvre pas tout le chemin.
3. `GpxRibbonAnchors` plaçait ensuite la géométrie mesurée avec une petite quantité d'ancres locales (32 max) : bonne protection contre les faux ancrages, mais pas de vision sur les mètres futurs non observés.
4. `WorldOverlayRenderer` affichait la vraie surface en bleu sur le monde, mais sa largeur de ruban 8 cm était constante. La flèche GPX ne fournissait qu'une petite direction, sans distance visible.

Ce n'était donc pas une segmentation neuronale du sol : ARCore fournissait les plans horizontaux, et **le code de rendu imposait que les deux extrémités de chaque morceau soient dedans**.

## Nouveau mode à deux niveaux — distinction obligatoire
- **BLEU continu / large près du téléphone :** GPX géographique calé par ARCore+GNSS, posé sur un **plan horizontal ARCore observé** et ancré localement. La présence d'un plan ne garantit cependant ni 10 cm d'absolu ni terrain exactement réel.
- **VIOLET pointillé / mince :** prolongement jusqu'à 90 m autour du téléphone, **futur et passé**, même au-delà de la portion de plan détectée. Dessiné en projection monde via la pose de caméra. Hauteur **supposée** (1,5 m sous la caméra au premier relevé en absence de plan, puis sol observé si disponible). Ne représente jamais une mesure réelle du relief.
- **Navigation de récupération :** si une cible GPX est disponible, flèche relative à la caméra et distance estimée. Sinon, avec alignement valide mais hors route, calcul de la projection sur le tronçon GPX visible le plus proche : flèche pour **retrouver le chemin**, sans injection dans GPS ou odomètre.
- **Largeur adaptative** : près du chemin, ruban bleu large, lointain nettement plus fin, en métrique dans le GPU 3D et en dp sur Canvas de secours. La qualité de mesure n'est pas modifiée.
- **Interrupteur natif** : Paramètres → Parcours → « GPX au loin (pointillé) ». Persistant, activé par défaut et totalement désactivable.
- **Côté charge CPU** : rayon de 90 m, 950 segments géographiques max, 720 petits traits indicatifs max, recherche spatiale plus proche prioritaire, géométrie mise en cache jusqu'à translation >2 m, upload GPU borné par les listes de segments. Rendu indépendant du FPS de prise de poses ARCore. Les profils de performance restent à comparer sur S21.

## Conditions STRICTES
- Pas de trace GPX projetée sans alignement GNSS/ARCore `CANDIDATE` ou `VIO_HOLD`; aucun trait géographique apparemment calé sur caméra alors que le GPS seul est disponible.
- Après changement de référence, tracking perdu ou position incohérente, les données du cache et la hauteur de référence sont invalidées. Une vieille pose ne doit pas afficher un chemin ancré.
- Les segments du sol observé restent séparés de l'aperçu indicatif. L'aperçu violet **ne crée aucune Anchor ARCore** et ne devient pas une preuve de surface, de GPS ni d'odométrie.
- La carte plein écran GPX du v0.12 reste accessible via la miniature; aucune superposition illégitime de repères.
- Pas de tracking IA sur vélo dans cette PR et pas de garantie de précision sous forêt, tunnel, fumée ou la nuit.

## Tests automatiques et terrain
- Unitaires : sans plan, un GPX retourne une suite de tirets violets ; avec plan valable, le ruban bleu prévaut et la couche approximative s'abstient ; géométrie bornée et NaN rejetés ; orientation hors-trace correcte ; largeur loin/près.
- Régression : `GpxGroundProjectorTest`, `GpxRibbonAnchorsTest`, `WorldGeometryTest`, `GpxArAndroidTest`, PDR, B1–B8, replay et validation synthétique complète.
- À faire sur S21 : marcher 300 m sur une trace GPX, regarder vers le sol/avant/côté, passer au-delà du plan détecté, revenir en arrière, s'éloigner de 5, 15, 40 m du GPX, puis revenir; vérifier la lisibilité relative de la flèche et l'absence de lignes bleues faussement posées.
- Photos nécessaires : vue de près bleue, violet pointillé au-delà du plan, flèche de récupération après écart, GPS absent, reprise après pause. Relevés FPS/CPU/thermique.

## Coordination
- Branche isolée : `feature/v013-gpx-world-horizon`; base `feature/slam-explorer` `3d251b8e06536a972b88bcf01bd34912ea5ceabd`.
- Fichiers partagés : `MainActivity`, `HudView`, `SceneMapper`, `WorldGeometry`, `WorldOverlayRenderer`, `ArRouteGuide`, `RouteIndex`, `SettingsActivity`, `SettingsCatalog`, `SettingsPresentation`, Gradle, tests et docs centrales.
- PR fumée #17 et docs low-light #21/#22 préservées. Priorité : CI au SHA exact, puis fusion sans remplacement de fichiers entiers du reste de l'application.

## Global GPX camera extension (separate V0.13 finishing branch)

This release extends the previous 90 m-only AR virtual horizon with an explicitly
**bounded global geographic LOD**. It does not claim surveyed metric accuracy.

- `RouteIndex.world(...,50000m,1600)` scans the immutable route, keeps all
  imported geometry in memory, and samples across four distance bands (90–300 m,
  300–1500 m, 1.5–6 km, 6–50 km). Nothing is permanently removed from GPX.
  The drawn subset is fair across earlier and later portions, even when the
  original route exceeds 50,000 points.
- Sampled far edges are NEVER joined across discarded vertices. This may appear
  intermittent on very dense long tracks: truthful absence of detail is preferable
  to inventing a path through unseen turns. The full 2D GPX map remains canonical.
- `GpxExtendedGuide.dashesInView` projects into the current camera frustum,
  culls off-screen segments, and draws distance-adjusted violet strokes via
  Canvas (no ARCore Anchor/ground observation). Local near-90 m blue anchors
  and navigation closest-point matching remain independent.
- **Limits**: 50 km maximum global AR candidate radius, 1600 global geometric
  segments and 1400 visible dashed primitives per frame, while observed-floor
  local track remains independently limited to 90 m. On distant/mountain routes
  the virtual height is only a visual assumption, not measured topography. A
  50 km selection radius does not mean all 50 km are visible in the physical
  camera image: occlusion, perspective and screen resolution still apply.
- Camera frustum uses 60 km virtual far clipping for 2D-overlay projection;
  near/far dash sampling limits avoid unbounded draw work.
- `GpsRoute.MAX_POINTS` increased to 120000. GPX input remains limited to
  12 MiB total and route densification 200000 vertices in the AR index.

### Exact release / field gates
Unit tests: global 40 km bidirectional query and hard budgets, 50k+ point route,
invalid input, previous 90 m local behavior, GpxWorldGuide, GPX/PDR regressions.
Verify CI at exact PR head SHA and again at merge SHA before distributing one APK.
On Samsung S21: rotate 180°/360°; test >90 m straight route and winding path,
10/30 km GPX zoom, GPS outages, ARCore loss, fps/CPU/thermals and artifacts.
Do not represent these automatic tests as a physical device qualification.

### Observability rejection refinement (21 October 2026)
Before a candidate can expose geographic projection, both GPS and VIO must have sufficient baseline among compatible pairs. A frozen GPS position, small jitter, or one excluded high-leverage VIO sample cannot supply heading. See [audit and limits](work/OBSERVABILITY_AUDIT_20261021.md).
