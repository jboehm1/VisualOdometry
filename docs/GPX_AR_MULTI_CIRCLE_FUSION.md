# GPX AR — fusion « multi-cercles » GNSS + VIO : modèle et portes de validation

## Intuition

Une mesure GNSS (lat/lon) avec « ±r » ne signifie pas que la vraie position est sur un cercle de rayon r. C'est une **zone de vraisemblance** 2D (souvent approximée par ellipse/covariance, parfois lourdes queues/biais), pas un anneau de trilatération. Marcher fournit d'autres observations et des déplacements relatifs VIO/IMU; on peut ajuster toute la trajectoire pour recouper **les contraintes de position ET de déplacement**.

Si les GNSS sont indépendants, le gain peut augmenter avec leur nombre. S'ils ont un **biais commun** (immeubles, feuillage, multipath), 100 mesures de précision déclarée 2 m peuvent toutes être décalées de 7 m. La fusion GPS_PROVIDER et GOOGLE_FUSED du même téléphone ne double **pas** le nombre de récepteurs indépendants.

## Mathématiques concrètes

Variables dans un repère local ENU WGS84 : poses métriques `p_i`, orientation/yaw `theta_i`, transformation rigide globale `T_gw`, biais GNSS lent `b_k` et éventuellement dérive des pas/odomètre `d_i`. Aucun facteur ne suppose que l'utilisateur marche exactement sur le GPX importé.

- GNSS : `z_i = position_geo(T_gw(p_i)) + b_i + epsilon_i`, avec `epsilon_i ~ N(0,R_i)` seulement si ce modèle est vérifié. L'API fournit principalement une précision horizontale scalaire; cela ne suffit pas à garantir une ellipse vraie ni sa couverture.
- Mouvement VIO : `p_j - p_i = R(theta_i) delta_ij^VIO + eta_ij`, avec covariance cohérente, segment et même horloge. La translation VIO donne des **déplacements relatifs**, pas un nouvel ancrage géographique absolu.
- Biais GNSS : `b_j = b_i + xi_ij` (marche aléatoire bornée / noyau temporel) : éviter de compter des observations GNSS corrélées comme répétitions indépendantes.
- Optimisation à fenêtre glissante / fixed-lag factor graph : `min sum rho_G(||z_i-h(T,p_i,b_i)||^2_Ri) + sum rho_V(||deltaVIO_ij-g(p_i,p_j)||^2_Qij) + sum ||b_j-b_i||^2_B + priors`. `rho` robuste (Huber/Cauchy), pertes et réinitialisation par segment, contraintes des coordonnées et horloges WGS84/ENU.
- En 2D, deux ellipses indépendantes de même covariance `R` donnent idéalement `R/2`. Avec corrélation `rho`, `Var(moyenne de n)=sigma²*(rho+(1-rho)/n)` : si `rho=1`, **aucune amélioration**.

Important : un filtre de Kalman étendu (EKF), un factor graph incremental (iSAM2/GTSAM) ou un solveur fixe fenêtre peut modéliser la trajectoire et les biais. Le choix sera basé sur tests de robustesse/CPU, **pas sur l'apparence d'une ligne plus lisse**. Une erreur GPX commune à tous les points ne se corrige pas en les traitant comme vérité absolue. Les informations depth/DEM et la pente ARCore ne constituent pas un ancrage horizontal global supplémentaire.

## Ce qui est déjà présent

- Google Fused Location Provider produit des positions via plusieurs sources Android/Google; il ne fournit pas ici un factor graph exploitable sur nos poses ARCore avec estimation du biais local et relecture rétroactive.
- `GeoAlignment` existant fait un recalage rigide 2D robuste IRLS des paires poses/fixes, avec fenêtre 64 fixes et baseline minimal 15 m. `ArRouteGuide` récupère son candidat et un résidu, **pas** un état/intervalle de confiance calibré tenant compte de multipath commun.
- `GpxRibbonAnchors` stabilise l'affichage local entre recalages ARCore. Une belle ligne fixe n'atteste ni position absolue ni absence de dérive.
- À chaque verrouillage écran, `onPause` libère session active/arrête GNSS et invalide l'ancrage; après déverrouillage le moteur doit retrouver une base GNSS/VIO. **Aucune promesse de continuité de position pendant écran verrouillé.**

## Tâche F06 — exploration puis intégration conditionnelle

1. Générer des trajets synthétiques courbes, aller-retour et chemins parallèles avec ARCore/IMU relatifs, GNSS indépendants + impulsions + biais lent commun + corrections/relocalisations, dropout et reboots/verrouillage écran; garder des jeux de tests gelés différents du réglage.
2. Baseline : `GeoAlignment` existant, FLP natif; expérimentation offline `fixed-lag factor graph` ou EKF à biais explicite et marges robustes, sans toucher au calcul d'odométrie.
3. Mesurer par scenario et même métrique : erreur absolue/latérale/longitudinale/cap p50/p95, fraction de faux « sûrs », évolution du biais estimé, temps jusqu'au candidat, stabilité/jumps, retard CPU/mémoire, consommations. Répéter avec vérité indépendante réelle.
4. Intégrer uniquement si gain robuste sur sessions tenues à part **sans cacher le pire cas** et sans forcer les positions sur le GPX; afficher l'état « géographie non observable » si commun biais impossible à identifier.
5. Budget visé : fenêtre bornée ≤120 poses/≤64 GNSS, décorrélation/hold-out, cadence correction indépendante des poses VIEW. Aucun calibrage de bruit via métriques du set final. Production = NON tant que Q02 ne valide pas les intervalles.

## Sources

- GTSAM GPSFactor : https://borglab.github.io/gtsam/gpsfactorexample/
- GTSAM IMU preintegration : https://borglab.github.io/gtsam/imufactor/ et https://borglab.github.io/gtsam/preintegratedimumeasurements/
- GTSAM CombinedImuFactor + covariance biais : https://gtsam.org/doxygen/a04659.html
- ARCore Raw Depth (seulement structure locale, pas géo) : https://developers.google.com/ar/develop/java/depth/raw-depth
