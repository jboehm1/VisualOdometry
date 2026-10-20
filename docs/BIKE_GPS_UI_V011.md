# VECTOR VIO · Release candidat v0.11 (GPS, UX, vélo) · 2026-10-20

## Périmètre et preuves
- Basé sur intégration Android `feature/slam-explorer` SHA `7c804eca6a242114b9be2467ae75dc8e5a2f60dd` ; branches examinées : 31, PR ouvertes #17/#21/#22. 
- Branche de développement isolée `feature/release-v011-ui-gps-bike`; **ne pas fusionner** tant que CI au SHA exact n'est pas verte. Aucun changement aux moteurs PDR/GPX, à ARCore, au manifeste, aux modèles IA ni aux pipelines datasets. Les références aux anciennes branches restent des archives.
- Affichage de `versionName` runtime depuis `PackageManager`; `versionCode=11` / `versionName=0.11.0-alpha`. Numéro visible en permanence sur le HUD de l'activité principale et dans titre SETTINGS.
- Erreur UX corrigée : Android `AlertDialog.Builder.setMessage()` pouvait cacher `setItems()` dans le même dialogue. Les menus catégories / sous-menus / sélecteur replay deviennent des **listes visibles**. Le bouton supérieur `GPX` ouvre directement import, activation et options. La simulation reste accessible depuis Performance & tests.
- GPS vitesse indépendant du VIO, actif par défaut **si permission localisation déjà accordée**, désactivable via MENU → Location & sensors → GPS speed badge. Sans permission, le badge dit `GPS PERMISSION` (pas de demande intrusive automatique). Foreground seulement, pas de service masqué, pas de modification au calcul de distance VIO. Expiration visuelle après 5s. `GPS ±5m` est la précision horizontale déclarée **pas la puissance des satellites / dBm / certitude de trajectoire**; `v±` n'est indiqué que si Android fournit une précision de vitesse. `NETWORK` n'est pas considéré GNSS.
- MENU → Camera & overlays → Bike/downward camera hints : opt-out stocké en préférences. À forte inclinaison vers le bas, signale que la roue / guidon mobile peut gêner ARCore, invite à cadrer du sol **statique**. L'inclinaison est une heuristique d'orientation, pas une segmentation. Aucun algorithme non testé ne remplace les poses, ne fabrique de vitesse VIO ni ne masque des pixels ARCore.
- Toujours séparer `GPS speed` (vitesse de la position Android) et `VIO speed` (ARCore caméra/IMU) ; ne pas écrire de faux déplacement lors d'une perte de tracking.

## Charger un GPX sur le téléphone
1. Ouvrir l'app, toucher `GPX` (bouton supérieur), puis `IMPORT GPX`. Le sélecteur Android accepte un fichier `.gpx` local ou un fichier rendu accessible dans Files/Drive.
2. Choisir un segment continu si le fichier contient plusieurs traces.
3. Toucher `GPX` → `ENABLE AR GPX`, autoriser la localisation précise. Toucher `START` pour lancer la caméra si nécessaire.
4. Marcher/rouler en gardant des objets/textures fixes en vue sur 30–50 m pour caler l'orientation relative. Le ruban ne doit pas s'afficher comme ancré tant que le calage GPS/ARCore n'est pas crédible. Le guidage 2D alternatif est MENU → Navigation & GPX → GPS ROUTES.
5. En cas de GPS faible, de perte de suivi ou de retournement du téléphone, attendre un nouveau calage. Ajustement manuel dans le menu GPX est **visuel et temporaire** et ne corrige pas des positions brutes.

## Vélo : diagnostic et architecture à investiguer
| Cas | Mesure praticable maintenant | Limite |
|---|---|---|
| Téléphone fixe orienté vers l'avant/côté | ARCore VIO + GPS côte à côte, suivi de la fiabilité | Répétition / flou / lumière / trajectoire |
| Téléphone tenu vers le vélo / vers le bas | Alerte opt-out, vitesse GPS indépendante si disponible | Les pixels mobiles du vélo peuvent dominer le champ et tromper ARCore |
| Sol visible | Les plans ARCore aident **l'affichage GPX et la géométrie** | Ne sélectionnent **pas** les keypoints de tracking ARCore |
| Sol détecté par petit CNN | FUTUR : segmentation route/sol/objets mobiles, masquage dans un **moteur VO propre** | Nécessite images brutes, annotations, tests sur vélo, coût CPU, pose métrique et comparaison terrain ; pas un bouton magique d'ARCore |
| GPS indisponible | Continuer seulement si poses VIO suivies / récentes; badge GPS rouge | Ni drift ni absolu garantis |

## Protocole terrain S21 à exécuter
- Deux montages fixes provisoires : orienté sol avec roue visible, puis 0–15° vers l'avant; 3 répétitions de 200–500 m avec référence indépendante mesurée, GPS et VIO côte à côte.
- Relever: taux de frames TRACKING, GPS speed ±, VIO speed, temps sans vitesse, km/h p50/p95 par segments, temps de réacquisition, faux positifs de distance sur immobilité, consommation, caméra fixe vs tenue à la main.
- Vérifier permissions OFF/ON et navigation des six catégories, import GPX Android, portrait/paysage, lock/unlock, simulation, préférences et réinstallation APK sans effacer données.
- Si besoin futur d'odométrie robuste aux objets mobiles: réaliser une piste séparée de masquage de zones dynamiques dans un moteur VO externe, avec métriques mesurées avant intégration Android.

**Statut** : implémentation logicielle en PR expérimentale, validation de l'APK et terrain **non démontrée** au moment de l'écriture.
