# Rejouer un export GPX AR

Java JDK 17+ et Python 3.11+ suffisent; aucune dépendance Python, aucun SDK Android requis. Depuis la racine du dépôt :

```sh
python3 tools/gpx_ar/replay.py capture.zip --out reports/gpx-capture --input-origin real_capture
python3 -m unittest discover -s tools/gpx_ar/tests -v
```

Prendre le **Dataset ZIP** d’une session où GPX AR était activé et la capture démarrée. Il faut les trois CSV `_gpx_ar_route`, `_gpx_ar_poses` et `_gpx_ar_fixes`. Plusieurs révisions de route nécessitent `--revision N`. Les anciens exports sans coordonnées métriques sont acceptés seulement sans ambiguïté de révision; leur sensibilité aux corrections du repère monde n’est pas effacée par le rejeu.

Le script compile et exécute les mêmes sources **Java ArRouteGuide, GeoAlignment, PoseFixSynchronizer et GpsNavigator/RouteMatcher** que l’application. Il traite tous les événements en ordre de réception, avec les horloges de mesure originales. Les diagnostics sortent à 1 Hz dans `replay.csv`; `summary.json` contient le SHA256 de l’entrée et des sources du moteur, la provenance déclarée, les états et les comptes. ZIP dangereux, CSV excessif, ordre temporel incorrect et révisions ambiguës échouent explicitement. Une horloge inconnue ne devient jamais une observation valide.

C’est un rejeu du **recalage sur poses enregistrées**, pas une nouvelle exécution ARCore à partir des pixels : MP4 et IMU restent disponibles dans l’export mais ne sont pas décodés par cet outil. Aucun rendu caméra ni précision absolue ne sont validés par son seul succès. La CI fabrique une fixture connue et archive entrée et résultats sous `measured-replay-results/gpx-ar`; elle la déclare `synthetic_fixture`, jamais capture réelle.

Pour une comparaison absolue, fournir un CSV indépendant `elapsed_ns,latitude,longitude`, dans la même horloge monotone Android :

```sh
python3 tools/gpx_ar/replay.py capture.zip --out reports/reference --truth reference.csv --reference-note "Origine et synchronisation de la référence" --input-origin real_capture
```

Appariement au plus proche à 100 ms, sans ajustement spatial sur la référence : p50/p95/RMSE/max et nombre d’appariements sont rapportés. L’indépendance de la référence n’est pas vérifiable automatiquement; le rapport l’indique, et ne transforme pas cette comparaison en certification. Sans référence : `ground_truth=NOT_PROVIDED`, `absolute_accuracy_validated=false`.

## Rendu et repères

La nouvelle capture conserve positions monde **et** métriques, segment et révision. Le recalage utilise un repère métrique transporté par une référence ARCore proche, distincte de l’odomètre. L’inverse ramène les portions GPX dans le monde courant. Projection horizontale WGS84 ellipsoïdale ECEF/ENU, axes AR x/−z; aucun ajustement d’échelle. Les arêtes GPX sont subdivisées à 25 m maximum pour l’index, sans modifier le GPX source; limite 200 000 sommets et extension locale 100 km.

Le sol reste celui des plans observés; aucune altitude GPX n’invente un terrain. Les portions projetées utilisent des tuiles métriques de 6 m, 32 ancres maximum et 2 créations par image de présentation. Références réutilisées, détachées en sortie/perte/reset; échec d’ancre masque la portion. Le transport d’ancre conserve les corrections entre révisions; une nouvelle solution GPS peut encore déplacer la ligne. Ce mécanisme n’est ni VPS, ni une suppression de biais GNSS.

DATA → GPX AR → DISPLAY OPTIONS : direction estimée, repères tous les 25 m, départ/arrivée, POI locaux avec distance directe, distance restante. Les options n’altèrent aucune mesure ni donnée enregistrée. Les waypoints nommés importés du GPX ne sont pas encore affichés. Ancres physiques, raccords sur sols difficiles et précision caméra restent à qualifier avec le protocole téléphone.
