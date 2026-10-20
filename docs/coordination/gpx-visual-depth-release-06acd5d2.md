# GPX Quick View / Depth opt-in — livraison logicielle, 19 octobre 2026

## Résultat vérifié
- Code APK SHA : `06acd5d2f252d690a1dde0dcadd636982bbfefce`. Branche isolée `feature/gpx-visual-depth-fusion`, PR #25 vers `feature/gpx-ar-guidance` PR #24. Ne pas fusionner sans revue des branches concurrentes.
- CI Android : https://github.com/jboehm1/VisualOdometry/actions/runs/39323905158 **SUCCESS**, rapport Gradle 175 tests, 0 échec/ignoré, lintDebug, assembleDebug/assembleRelease, GPS regression, deux replays simulés sens inverse.
- Three Campaigns : https://github.com/jboehm1/VisualOdometry/actions/runs/39323905280 **SUCCESS**; A moteur VINS externe EuRoC, B Python inertial baseline synthétique, C compile APK ARCore playback, pas trois mesures de précision de GPX sur S21.
- APK SIDeload debug : https://github.com/jboehm1/VisualOdometry/actions/runs/39323905158/artifacts/12220303689 ; 3 638 160 octets; SHA256 `b1cf8655f77ee48eda7617fb9c7606af38db5bf67b8679885a467e1fe0a94018`; ZIP SHA256 `ff678d5016925430a9a74c1d54e015bcdf220771a67cce5046e30169cf7d5124`. APK extrait et vérifié, manifeste ZIP existant. Rétention artifact 14 jours.

## Comportement réel du code
- Lock/unlock : STOP caméra + GPS lorsque la page est suspendue; pas d'enregistrement ni localisation background. À l'unlock, ARCore/GPS se réabonnent et toute ancre de l'ancienne session est invalidée; un GNSS frais permet un statut progression/distances GPS ONLY tandis que 3D se recale à partir de fixes + déplacement 15 m minimum. Quand Android tue entièrement l'app, GPX stocké mais activer GPX AR à nouveau.
- GPX ancré VIO (nouvelles poses sans enregistrement) : si perte de fix GNSS après placement candidat, ruban local conservé jusqu'à 30 secondes de façon provisoire en mode VIO HOLD (dès 5 secondes), étiquette alerte, allowance diagnostic augmente; pas de flèche GPS ancienne. Après temps limite/perte tracking/pose périmée l'affichage est masqué.
- Sol/pente : planes ARCore upward facing et normales observées restent le modèle vertical. Altitude GPX/DEM = altitude géographique indicative, ne force pas hauteur ARCore et datum inconnu.
- Raw Depth : option dans DATA/GPX AR, OFF par défaut; support matériel vérifié. RAW_DEPTH_ONLY peut masquer uniquement des sous-segments derrière un obstacle mesuré avec ≥180/255 confiance et ≥0.35 m de marge, 5 lectures max/s et 512 segments max. Depth absent ou mauvais => comportement initial par plans. Un unlock applique le changement ARCore. Aucun maillage dense, aucune hypothèse sol/verticale en plus, pas de ML propre ni téléchargement obligatoire.
- « Multi-cercles » : tâche F06 avec maths GNSS covariance/biais corrélés dans [GPX_AR_MULTI_CIRCLE_FUSION.md](../GPX_AR_MULTI_CIRCLE_FUSION.md), pas un nouveau filtre actif/garantie précision. Un décalage GPS commun ne converge pas par simple recoupement.

## Encore à valider
Jamais testée physiquement par cette CI : vraie précision latérale/longitudinale/hauteur/cap du GPX caméra en forêt/ville/pente/croisement, jumps ancre/relocalisation, qualité du Raw Depth/occlusion sur S21, consommations ON/OFF, GNSS perdu >30 secondes, vraie interruption écran, et couverture d'une covariance statistique. Sans vérité indépendante, **ni 110 %, ni centimètre, ni sécurité navigation garantis**.

Manuel pratique : [GPX_AR_MANUAL_TEST.md](../GPX_AR_MANUAL_TEST.md). Ne pas confondre essais simulés et mesures terrain.
