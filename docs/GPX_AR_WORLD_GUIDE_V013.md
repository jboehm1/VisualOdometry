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
