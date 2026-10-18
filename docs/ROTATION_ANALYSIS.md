# Analyse rotation / distance — 18 octobre 2026

## Conclusion et niveau de preuve

Détecter une forte rotation est facile avec le gyroscope. Certifier l'absence de translation simultanée ne l'est pas. Il existe néanmoins une piste structurelle concrète dans notre code : il différencie directement des coordonnées monde ARCore issues de frames différentes, alors que le contrat ARCore prévient que le repère peut être réajusté. Corriger le repère est distinct d'un filtre qui supprime de petits déplacements.

Ce rapport établit le problème de convention et ses effets possibles par construction mathématique. Il ne prouve pas que c'est la cause dominante du dernier test S21, dont la capture n'est pas disponible ici. Aucun changement de l'estimateur live n'est introduit par ce rapport. La version cc2533c reste sans deadband.

## Audit du chemin live

- MainActivity : Session.update -> Camera TRACKING -> Camera.getPose -> getTranslation -> Odometer.onPose.
- getPose désigne la caméra physique, pas une matrice de vue inversée par erreur. Le quaternion n'est pas ajouté aux mètres. getDisplayOrientedPose sert au cône de la carte, pas au compteur.
- Odometer conserve une ancienne position numérique puis soustrait la suivante ; distance = norme de cette différence. Le HUD affiche directement totalMeters et speedMps ; pas de second compteur caché découvert.
- Perte de suivi et sauts >25m/s interrompent le segment, mais une correction plus petite et continue peut rester TRACKING et passer ces règles.
- ImuMonitor enregistre des mesures diagnostiques ; il ne modifie pas la fusion privée d'ARCore.
- RouteHistory conserve aussi des positions numériques anciennes pour le tracé. La cohérence du repère concerne donc la route et les cartes historiques, pas uniquement le compteur. Les champs Anchor présents dans SceneMapper ne corrigent pas l'entrée du compteur.

## 1. Formule correcte, hypothèse de repère incomplète

Dans un repère fixe, T_k=(R_k,p_k) décrit la pose caméra vers monde. La translation relative est R_(k-1)^T*(p_k-p_(k-1)). Sa norme égale celle de p_k-p_(k-1). Une rotation à position constante donne zéro. Cette partie du calcul précédent était correcte.

Mais si ARCore applique une correction G_k=(Q_k,b_k), les coordonnées reçues deviennent p'_k=Q_k*p_k+b_k. La différence p'_k-p'_(k-1) peut être non nulle alors que p_k=p_(k-1). Calculer inverse(T'_(k-1))*T'_k ne suffit pas : les deux poses restent exprimées dans deux estimations du monde différentes. Il faut une référence suivie commune, réévaluée dans chaque frame.

Google décrit explicitement le caractère révisable des coordonnées caméra/ancres et recommande une ancre ou une position relative à une ancre proche pour conserver une position entre frames [1]. C'est une cause possible documentée, pas une preuve que chaque rotation déclenche une correction globale.

## 2. Correction de repère à tester, sans seuil de distance

Avec une même ancre A suivie, calculer à chaque frame :

    C_k = inverse(A_k) * T_k

A_k ET T_k doivent être lus après le même Session.update. Conserver ensuite les coordonnées relatives C_k pour mesurer les incréments. Si une correction rigide commune G_k affecte les deux :

    inverse(G_k*A_k) * (G_k*T_k) = inverse(A_k) * T_k

La correction s'annule algébriquement, sans tronquer les translations. Un arc physique reste un arc physique. Soustraire seulement la translation de l'ancre est insuffisant si son orientation évolue : utiliser toute la transformation inverse.

Limites : une ancre est elle-même estimée ; des corrections locales non rigides ou différentielles et du bruit caméra/ancre subsistent. Ce n'est pas une vérité terrain ni une garantie zéro dérive. Vérifier TRACKING de l'ancre, gérer pause/perte de suivi, détacher les références inutiles. Ne pas garder une ancre distante sur tout un long trajet ; prévoir des références proches et une transition calculée avec ancienne et nouvelle ancre dans la même frame, sans ajouter le changement d'origine aux mètres. La consigne officielle de proximité sert au choix des références, pas à supprimer un déplacement [2,3].

Implémentation prudente suivante : journaliser en parallèle les poses monde caméra/ancre, les poses relatives, les identifiants d'ancre et changements de référence. Comparer les deux longueurs en mode diagnostic avant de remplacer la mesure. Garder le compteur et la capture bruts pour audit. Une référence relative ne doit pas être passée comme une position monde à WorldOverlayRenderer ; convertir explicitement le tracé pour le rendu.

## 3. Expérience déterministe sur le vrai Odometer

Exécuter tools/rotation/RotationAudit.java avec Odometer.java et RouteHistory.java. Les transformations et erreurs sont imposées ; aucun ARCore réel n'est exécuté et aucune amplitude de correction réelle n'est estimée.

| Construction | Anciennes différences monde | Coordonnées relatives idéales | Attendu |
|---|---:|---:|---:|
| Centre fixe, orientation variable | 0 m | 0 m | 0 m |
| Centre fixe, repère monde variable | 3,222506 m | 0 m | 0 m |
| Arc physique rayon 30 cm et repère variable | 2,605151 m | 1,884932 m | 1,884932 m (polygone échantillonné) |

Les assertions vérifient l'annulation du changement de repère et la conservation de l'arc. Ce n'est pas une évaluation du SDK ou d'une future classe Android de gestion d'ancres.

Autre entrée construite : 1 mm d'erreur alternée entre frames à 30 Hz pendant 60 s donne 1,8 m dans le compteur brut. Cela démontre le biais d'une somme de normes, sans rotation ni changement de repère. Le retrait des deadbands préserve les incréments, mais ne rend pas leur somme exacte physiquement. Ne pas réintroduire un seuil caché en réaction.

## 4. Détection visuelle de rotation pure

Pour une scène statique, une caméra centrale calibrée et une rotation sans translation, les correspondances vérifient :

    u2 ~ K2 * R_(2<-1) * inverse(K1) * u1

Pour nos rotations caméra-vers-monde, R_(2<-1)=transpose(R2)*R1, à condition que les orientations soient dans un repère cohérent. Les axes caméra OpenGL et ceux des pixels OpenCV doivent être convertis ; ne pas mélanger +Y haut/-Z avant et +Y bas/+Z avant. Une orientation obtenue du gyro requiert aussi extrinsèques et synchronisation caméra/IMU.

Méthode : suivre des points distribués sur l'image, prédire leur déplacement sous rotation, mesurer le résidu après compensation, comparer les modèles rotation seule et rotation+translation avec une incertitude mesurée. Une simple homographie libre ne suffit pas : un plan avec translation produit aussi une homographie. La matrice essentielle devient dégénérée lorsque la translation est nulle ; une direction t renvoyée par recoverPose ne prouve pas une translation métrique.

Un faible résidu signifie « compatible avec rotation seule à cette précision », pas « translation exactement nulle ». Les scènes lointaines, les plans, le manque de texture, les objets mobiles, le flou, la distorsion et le rolling shutter limitent la décision. L'issue INDETERMINE est nécessaire. Des critères statistiques liés au bruit sont nécessaires à tout détecteur ; cela ne justifie pas un seuil arbitraire de 12 cm sur les mètres.

La formule de rotation-image est décrite par OpenCV [4]. Le contraste avec le cas plan explique pourquoi la seule estimation d'homographie n'est pas une preuve.

## 5. Ce que l'IMU peut et ne peut pas certifier

Le gyro mesure la rotation même si le téléphone avance. Une accélération corrigée de gravité proche de zéro est aussi compatible avec une translation à vitesse constante. Une contrainte classique « appareil immobile » exige généralement aussi une faible vitesse angulaire ; elle ne s'applique donc pas directement à une rotation pure. OpenVINS explique ces limites des mises à jour zéro-vitesse [5].

Le centre optique et le centre de rotation peuvent différer. À distance r du pivot, vitesse tangentielle = norme(omega × r), et un tour complet décrit 2*pi*r. Pour r=10 cm, cela vaut environ 0,628 m : géométrie réelle, pas erreur numérique. Si l'on veut la trajectoire du porteur plutôt que celle de la caméra, il faut définir/calibrer le point suivi ; aucune correction universelle ne se déduit du seul angle.

## 6. Protocole pour départager les causes

Enregistrer une session par cas, avec événements explicites et vidéo :

1. Téléphone posé, aucune rotation : caractériser bruit/dérive de référence.
2. Rotation lente autour du centre caméra sur support, puis rotation rapide : séparer translation physique, flou et effets de lecture.
3. Téléphone sur bras à rayon mesuré : vérifier que l'arc réel n'est pas supprimé.
4. Translation droite sans rotation, puis même distance avec virages : détecter le sous-comptage.
5. Retour vers une scène déjà vue : rechercher un changement commun caméra/ancre.
6. Refaire sur scène proche texturée et scène lointaine/peu texturée ; geler la configuration avant la comparaison.

Pour chaque frame : horodatage, pose caméra monde, pose ancre monde, pose relative, tracking caméra/ancre, changement de référence, incrément brut et relatif, rotation, intrinsics et diagnostics d'image. Distinguer les flux à cadence vidéo des POSE CSV environ 10 Hz. Une ancienne vidéo sans poses d'ancres ne permet pas de reconstruire rétroactivement la correction commune avec certitude.

Critère de diagnostic : si caméra ET ancre changent ensemble tandis que leur pose relative reste stable, la piste repère est renforcée. Si la pose relative dérive aussi et que l'image est compatible avec rotation seule, investiguer VIO/image/calibration. Si l'image montre une parallaxe cohérente et un pivot décentré, la caméra se déplace réellement. Aucun de ces diagnostics n'est établi pour le dernier essai utilisateur.

## Sources primaires consultées

1. https://developers.google.com/ar/reference/java/com/google/ar/core/Pose — conventions et révisions du repère monde.
2. https://developers.google.com/ar/reference/java/com/google/ar/core/Anchor — mise à jour des poses d'ancres et état de suivi.
3. https://developers.google.com/ar/develop/anchors — références proches, réutilisation, corrections indépendantes.
4. https://docs.opencv.org/4.11.0/d9/dab/tutorial_homography.html — rotation pure et homographie.
5. https://docs.openvins.com/update-zerovelocity.html — limites et tests de zéro-vitesse.
6. https://developers.google.com/ar/reference/java/com/google/ar/core/Camera — pose de la caméra physique.

## Mise en œuvre après cet audit

Le changement suivant introduit `AnchorCoordinates` et `AnchoredOdometry` dans le live. Chaque pose métrique est `metricFromAnchor * inverse(worldFromAnchor) * worldFromCamera`, avec caméra/ancre lues dans la même frame. Référence locale initiale à axes monde (Y gravité), renouvelée à plus de 4 m ; ce rayon n'est pas un seuil d'acceptation des mètres. Transfert de référence par transformation complète dans la même frame, sans nouveau segment ni distance artificielle. Une seule référence active, deux temporairement pendant le transfert ; anciennes références détachées. Perte d'ancre/caméra, pause et trou de timestamps créent une rupture explicite. Pas de repli sur les différences monde si l'ancre manque. Un échec du compteur n'arrête pas la capture vidéo.

Aucun deadband n'est réintroduit. La correction annule les changements rigides communs, mais le bruit et les corrections différentielles subsistent. Ce n'est pas un détecteur visuel de rotation pure et la disparition du défaut S21 reste à constater sur appareil.

`*_motion.csv` journalise chaque frame mesurée avec caméra monde, ancre monde, identifiant de référence et position métrique. Les poses brutes historiques et MP4 restent inchangés. La route monde historique destinée à l'affichage n'est pas encore entièrement réoptimisée/reprojetée après correction de carte. La nouvelle route métrique destinée au guidage est séparée, bornée et décimée pour affichage/navigation ; elle ne remplace pas les poses de diagnostic.

Tests : algèbre réelle de `com.google.ar.core.Pose`, 360 corrections de repère, transferts répétés, incréments millimétriques, arc physique, duplication de timestamp, perte/réacquisition, trou temporel et échec de création d'ancre. Les tests d'ancres utilisent des références simulées ; aucun téléphone n'est piloté.
