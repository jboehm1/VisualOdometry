# Profils de consommation — 0.9.0-alpha

## Live AR

Bouton POWER dans la vue caméra : Full 30, Balanced 15, Very low 5 mises à jour/s et Hold 5 → 30 pendant un appui sur l’image. Le mode choisi est conservé ; un appui actif ne l’est jamais. Relâchement, annulation, relâchement du doigt principal, perte de focus et pause annulent l’accélération. Les contrôles du HUD ne déclenchent pas le boost. Choisir Full reste possible sans maintenir un geste.

LiveFrameScheduler pilote GLSurfaceView en RENDERMODE_WHEN_DIRTY, via des demandes périodiques sur le thread UI. Il ne bloque pas le thread GL, ne rejoue pas de demandes en rafale après retard et arrête les demandes à la pause. Chaque rendu demandé exécute le pipeline existant (Session.update, pose, odométrie, map, overlay). La cadence est une cible, pas un débit garanti sur un appareil lent. Le HUD affiche APP Hz, mesuré à partir des timestamps des images reçues, distinct de la cible du bouton POWER.

ARCore est configuré pour une caméra cible 30 FPS à la création de la session. Son capteur et son suivi interne restent actifs entre nos appels : demander cinq mises à jour/s n’est pas faire fonctionner la caméra à 5 FPS. Aucun gain d’autonomie chiffré n’a été mesuré sur S21. Les réglages de la caméra ne sont pas changés à chaque geste : pas de redémarrage de session, ni de rupture volontaire du repère AR.

À basse cadence, moins de poses sont consommées/enregistrées et la reconstruction reçoit moins de points de vue. Courbes et mouvements rapides peuvent être sous-échantillonnés ; la distance et la carte ne sont pas garanties identiques. Les contrôles de continuité existants restent actifs et aucun filtre de distance supplémentaire n’est ajouté. L’IMU conserve sa cadence de demande. Une éventuelle vidéo ARCore garde sa propre cadence caméra ; les métadonnées de frames suivent les appels de l’app. Le choix de profil est journalisé au démarrage/reprise et lors des changements.

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

Tests du scheduler (cadence basse, boost immédiat, relâchement, arrêt/reprise, sélection), geste Android/menu (annulation et focus) et sélection de plage caméra avec repli. CI : unit tests, lint, APK debug/release et régressions GPS/AR. À vérifier sur appareil : gestes multitouch, cadence réelle, transitions, températures et autonomie. Mesurer plusieurs profils à luminosité, scène et durée identiques ; ne pas déduire une consommation proportionnelle aux FPS.

Sources :
- https://developers.google.com/ar/reference/java/com/google/ar/core/CameraConfig.TargetFps
- https://developers.google.com/ar/reference/java/com/google/ar/core/Config.UpdateMode
- https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
