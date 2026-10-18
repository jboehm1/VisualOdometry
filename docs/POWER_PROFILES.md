# Profils de consommation — 0.10.0-alpha

## Décision et fonctionnement

ARCore ouvre/pilote la caméra et fusionne les images avec les capteurs inertiels. Notre app ne lui fournit pas un sous-ensemble choisi d’images. La session est configurée pour une cible caméra de 30 FPS ; l’API publique propose 30/60, pas 5/15. La cadence réelle peut être moindre (exposition, charge, pertes de suivi). La fréquence interne du VIO d’ARCore n’est pas réglable via un simple paramètre de notre app.

La version 0.9 réduisait nos appels Session.update avec l’affichage : elle pouvait donc sous-échantillonner les poses et les virages. La version 0.10 remplace ce comportement : **tous les profils gardent une cible de lecture/traitement de 30 poses/s**, sans réinitialiser la session lors d’un appui. L’odométrie, les keyframes/POI, les réglages de capture de carte, les métadonnées de frames et la demande IMU ne dépendent plus du profil d’affichage. Le CSV principal garde son échantillonnage existant (~10 Hz) ; le diagnostic _motion.csv et les appels odométriques suivent chaque nouvelle frame reçue. Aucun nouveau filtre de translation n’est ajouté.

## Présentation indépendante

Bouton VIEW : Full 30, Balanced 15, Very low 5 FPS et Hold 5 → 30 pendant l’appui sur l’image. Le mode choisi est conservé ; l’appui ne l’est jamais. Relâchement, annulation, relâchement du doigt principal, perte de focus et pause annulent le boost. Les contrôles du HUD ne le déclenchent pas. Choisir Full reste possible sans maintenir un geste.

LiveFrameScheduler utilise un HandlerThread dédié VioCadence. Il demande le traitement à ~30/s et le rendu à des échéances indépendantes. CoalescingTask garde au plus une mise à jour en attente/en cours : un thread GL lent n’accumule pas de frames périmées à rejouer. Toutes les opérations Session.update et GL restent sérialisées sur le thread GL, avec vérification du contexte EGL. Le premier rendu établit le contexte ; ensuite queueEvent peut traiter les poses sans demander un swap à chaque pose.

GLSurfaceView reste en RENDERMODE_WHEN_DIRTY. onDrawFrame présente uniquement la dernière frame disponible, prépare les overlays à au plus ~20 Hz et selon le profil, et réduit les rafraîchissements du HUD. La capture de géométrie persistante garde sa politique antérieure à chaque nouvelle pose. Les timestamps dupliqués ne redéclenchent pas l’odométrie ou les métadonnées. POSE Hz mesure le nombre d’intervalles de frames distinctes sur une fenêtre, au lieu de moyenner les inverses des intervalles (qui biaisait le résultat lors de frames manquantes). L’indicateur tombe à zéro si aucune nouvelle frame n’est reçue depuis >0,7 s, quand la présentation continue.

Limiter le rendu diminue le travail de préparation/dessin/swap ; cela ne signifie pas que le capteur caméra tourne à 5 FPS. Le thread GL est partagé : un rendu ou une capture photo coûteuse peut toujours retarder la lecture des poses. La cible de 30/s est un budget, pas une garantie temps réel ou une promesse de précision/batterie. Aucun pourcentage d’économie n’a été mesuré sur téléphone.

## Marche, course et fréquence minimale

La vitesse seule ne définit pas une fréquence minimale fiable. Le déplacement des pixels dépend aussi de la profondeur des objets, de la rotation du téléphone, du champ de vision, du temps d’exposition/flou, des textures et des occlusions. À vitesse de translation nulle, une rotation rapide peut déjà perdre le suivi.

Exemples cinématiques, **pas des seuils de validation VIO** : déplacement entre images = vitesse / fréquence.

| Vitesse | 5 images/s | 15 images/s | 30 images/s |
| --- | --- | --- | --- |
| Marche 1,5 m/s (5,4 km/h) | 30 cm | 10 cm | 5 cm |
| Course 3 m/s (10,8 km/h) | 60 cm | 20 cm | 10 cm |
| Course 5 m/s (18 km/h) | 1 m | 33 cm | 17 cm |
| Sprint 8 m/s (28,8 km/h) | 1,6 m | 53 cm | 27 cm |

Rotation à 180°/s : 36° entre images à 5 FPS, 12° à 15, 6° à 30. Des images proches d’un mur et une caméra balancée en courant peuvent donc être plus difficiles qu’un déplacement rapide vers un paysage lointain.

Choix actuel : caméra et lecture des poses cibles 30/s pour marche ET course ; affichage 5/15/30 au choix. 15 FPS caméra serait une hypothèse de test pour une marche douce, pas un minimum garanti ; 5 FPS n’est pas retenu pour l’odométrie en courant. Tester 60 FPS pourrait être utile pour des mouvements très rapides sur matériel compatible, mais consomme davantage et ne corrige pas à lui seul le flou. Aucun basculement dynamique 30/60 selon GPS n’est introduit : la vitesse GPS n’est pas un indicateur suffisant et changer de configuration AR peut perturber le suivi.

Pour déterminer un minimum empirique, il faut des captures vidéo/IMU synchronisées et une référence de distance/trajectoire, couvrant marche/course, rotations, lumière et scènes variées. Réduire seulement la fréquence de poses d’un même suivi ARCore ne teste pas le comportement du VIO avec moins d’images d’entrée. Les données caméra 30 FPS ne valident pas une hypothèse 60 FPS.

## Collecte WALK

| Profil | Vidéo demandée | Débit cible |
| --- | --- | --- |
| Full | 1280 × 720, 30 FPS | 3 Mbit/s |
| Light | 1280 × 720, 15 FPS | 1,5 Mbit/s |
| Detail | 1920 × 1080, 30 FPS | 6 Mbit/s |
| Ultra | 640 × 480, 5 FPS | 0,45 Mbit/s |

Ultra sélectionne une plage AE annoncée dont la borne haute est 5 FPS. Si absente, il prend la plus petite borne haute annoncée entre 5 et 30 FPS, avec préférence pour une borne basse élevée à borne haute égale. Le statut indique le repli ; metadata.json distingue requested_fps, target_fps, fps_fallback et ae_fps_range. Le nombre effectif d’images dépend aussi de l’exposition et de l’appareil. Les timestamps Camera2 restent disponibles dans frames.csv. Une résolution ou configuration encodeur indisponible produit une erreur explicite ; aucun enregistrement vidéo réussi n’est inventé.

Full/Light/Detail conservent leur sélection stricte de borne haute. Tous demandent IMU 200 Hz et GPS 1 Hz ; Ultra sacrifie résolution, compression et résolution temporelle pour la collecte longue. Pas de cartographie AR dans WALK. Pour une promenade sans images, GPS ROUTES évite complètement la caméra et ARCore.

## Validation

Tests du scheduler (même cadence de traitement pour tous les profils, boost visuel sans requête de pose supplémentaire, arrêt/reprise), coalescence/bornes en cas de consommateur lent/erreur, comptage des frames distinctes, geste Android/menu (annulation et focus) et sélection de plage caméra avec repli. CI : unit tests, lint, APK debug/release et régressions GPS/AR. À vérifier sur appareil : gestes multitouch, cadence réelle, transitions, températures et autonomie. Mesurer plusieurs profils à luminosité, scène et durée identiques ; ne pas déduire une consommation proportionnelle aux FPS.

Sources :
- https://developers.google.com/ar/reference/java/com/google/ar/core/CameraConfig.TargetFps
- https://developers.google.com/ar/reference/java/com/google/ar/core/Config.UpdateMode
- https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
