# VECTOR VIO · GPX COMPLET dans le monde AR · clarification utilisateur 20/10/2026

## Commande utilisateur à conserver intégralement

L'utilisateur ne demande **pas uniquement « 90 m devant et derrière le vélo »**. Il exige de pouvoir explorer **l'intégralité du parcours GPX**, passé et futur, dans toutes les directions autour de lui. La caméra doit dessiner les segments qui se trouvent géométriquement dans son champ de vision, avec perspective naturelle : un segment proche est plus large, un segment loin plus fin. Tourner la caméra permet de découvrir les sections latérales et derrière soi. La partie hors champ n'est pas dessinée sur l'écran ; elle doit rester présente dans le repère virtuel. Il doit être possible de retrouver facilement la route lorsqu'on s'en éloigne.

L'**agrandissement de la petite carte** (v0.12, PR #33 fusionnée) doit afficher **100 % du GPX importé**, avec zoom/pan et, si possible, trajectoire VIO dans son propre repère. Ne jamais superposer une VIO non recalée avec des coordonnées GPS en faisant croire à la précision.

### État vérifié à date

- Intégration : `feature/slam-explorer` SHA `3d251b8e06536a972b88bcf01bd34912ea5ceabd` ; UI v0.12 fusionnée, Android + Three Campaigns SUCCESS au SHA d'intégration.
- PR #34, branche `feature/v013-gpx-world-horizon`, SHA **`dc6b01de0a41a35975cee53a0740586a8f0b1f42`**, OPEN/DRAFT non fusionnée. CI Android run `39461744029` SUCCESS, Three Campaigns run `39461744428` SUCCESS. APK debug artifact `12283516802`; SHA256 du APK extrait `6469c1619c84aed228f4b754ca94193bbfaca2ad600e8be77e2504d704033067`. **Pas de validation physique Galaxy S21**.
- Autre branche GPX `feature/gpx-world-visible-v013` SHA `88bb8e3a19f99fe07a4085d4cb6d54569bd0a4b4` : alternative expérimentale non fusionnée, à comparer sans écrasement.
- PR #17 fumée/radar R&D et PR #21/#22 docs low-light : hors périmètre.
- Issue #31 : clés de signature debug CI différentes entre builds ; sauvegarder les données avant éventuelle désinstallation.

### Explication technique du problème vu sur la capture

Dans la v0.11 le rendu AR était borné par `ArRouteGuide/RouteIndex.near(...,30m,...)`, puis `GpxGroundProjector` cachait les sections hors du polygone de sol observé par ARCore. Ainsi une ligne bleue apparaît juste sous le vélo lorsque seul ce petit plan est détecté. Les ancres locales et le GPU ne créent pas, seuls, un terrain complet.

La PR #34 améliore nettement : extraction locale jusqu'à **90 m** (950 arêtes max), segments observés bleus sur plan réel, **violet pointillé** sur altitude virtuelle pour les régions sans sol observé, option désactivable, épaisseur selon la distance et indicateur de retour. **Cette PR ne satisfait pas encore le GPX COMPLET en AR : sa fenêtre 90 m et ses budgets empêchent une garantie d'affichage de toute portion longue réellement visible.** Le GPX complet est déjà disponible sur la grande carte géographique 2D v0.12.

### Contrat de l'architecture cible

1. Conserver dans un index géographique l'ensemble des arêtes GPX importées, sans élimination permanente au-delà de 30/80/90 mètres. Respecter les coordonnées, ruptures de traces et distances le long du parcours.
2. Après calage crédible GNSS/AR, maintenir une transformation métrique cohérente. Ne pas confondre position GNSS, VIO, plan virtuel et altitude terrain ; invalider l'ancrage si ARCore perd son repère.
3. Pour le rendu caméra : récupérer les portions susceptibles de passer dans le **frustum**, utiliser des tuiles et **LOD** (décimation contrôlée en pixels/projection) et un cache GPU borné. Si une distance maximale est techniquement indispensable, elle doit être documentée comme limite/configuration, pas cachée.
4. Projeter les parties mesurées sur les plans ARCore (bleu plein). Pour les parties non observées : continuation **approximative**, couleur/tracé visiblement distincts et désactivable ; ne pas annoncer « ancré au sol » pour elles. Un terrain en relief, pont ou escalier ne peut pas être inféré d'un plan horizontal arbitraire.
5. Largeur décroissante avec la distance ; segment proche lisible sans masquer la chaussée. Le suivi doit être indépendant du rayon visible.
6. Déterminer la route la plus proche sur le **GPX global**, pas seulement les arêtes rendues à l'écran. Montrer direction/distance hors-route seulement quand l'orientation et le recalage sont crédibles ; annoncer la perte de confiance.
7. Garder la grande carte « tout le GPX » avec recadrage, zoom/pan et position GPS fraîche. La trajectoire VIO doit rester distincte si son recalage n'a pas été établi.
8. Le suivi vélo vers le bas peut souffrir des points/features mobiles de roue/guidon ; une segmentation sol ARCore ne masque pas directement les points de son estimateur. Prouver tout gain sur données/vidéos physiques.

### Tests de sortie obligatoires

- GPX de 2, 10 et 30 km, >50 000 points, boucles/croisements, sens retour : carte montre 100% de la route ; AR ne coupe pas *arbitrairement* les parties visibles au-delà de 90m.
- Orientation caméra devant/derrière/côtés/vers le sol et retour après 180° ; champ de vue correct et perspective proche/loin.
- Plans ARCore absents/partiels ; pointillés optionnels transparents et bien étiquetés ; ruban plein uniquement sur sol réellement détecté.
- Téléphone hors-route à 10/30/100 m ; direction/distance vers **la géométrie globale** ; perte/reprise GNSS et AR ne produit pas de fausse certitude.
- Performance Galaxy S21 : FPS, latence de réapparition des tuiles, mémoire, GPS/VIO séparés, consommation, enregistrement/exports, permissions.
- CI Android + Three Campaigns au SHA de la **branche finale ET du commit fusionné**. CI ne prouve pas la précision de position dans le monde réel.
- Ne déclarer 100% qu'après vidéos/mesures de terrain sur S21 et vérification de la route affichée.

### Sources de code/coordination à relire
`AGENTS.md`; `docs/DEVELOPMENT_COORDINATION.md`; `docs/GPX_AR_WORLD_GUIDE_V013.md` (sur PR #34) ; `gpxar/ArRouteGuide.java`, `gpxar/RouteIndex.java`, `SceneMapper.java`, `GpxExtendedGuide.java`, `GpxGroundProjector.java`, `GroundTrail.java`, `GpxRibbonAnchors.java`, `WorldGeometry.java`, `WorldOverlayRenderer.java`, `GpxStrokePolicy.java`, `HudView.java`, `MainActivity.java`, `SettingsActivity.java`, `SettingsCatalog.java`.

**Reprise recommandée :** partir de `feature/slam-explorer` à la tête Git actuelle, auditer PR #34 (verte mais DRAFT), comparer la branche `feature/gpx-world-visible-v013`, créer une nouvelle branche isolée pour la généralisation **GPX complet + LOD/frustum**, tests longs et CI ; ne pas fusionner aveuglément les deux implémentations concurrentes. Maintenir la roadmap/Atlas centraux, préserver PDR, GPX et les travaux parallèles.

**Référence GitHub PR #34 :** https://github.com/jboehm1/VisualOdometry/pull/34 — **CI Android** https://github.com/jboehm1/VisualOdometry/actions/runs/39461744029 — **Campagnes** https://github.com/jboehm1/VisualOdometry/actions/runs/39461744428 .
