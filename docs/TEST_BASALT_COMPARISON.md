# Comparer ARCore et VIO custom dans le même APK

Version candidate : **0.15.0-alpha1 / code16**. La ligne Build des paramètres
identifie le commit réel. Basalt est un estimateur images + IMU ; le contrôleur
d'exposition (gradient par régions, gyro et mouvement visuel) est un module
distinct, réutilisé dans son écran Camera2. Le mode ARCore conserve son
fonctionnement précédent. Le PDR sans caméra reste disponible séparément.

## Premier essai

1. Exporter les anciennes sessions avant une mise à jour : la signature debug
   peut différer entre builds (ticket #31). Ne pas désinstaller sans sauvegarde.
2. Ouvrir le mode ARCore habituel, choisir un profil caméra à focus fixe
   (Balanced 30 plutôt que Low Light), démarrer et attendre TRACKING quelques
   secondes sur une scène texturée. Les informations géométriques usine sont
   alors mémorisées si le téléphone expose toutes les métadonnées nécessaires.
3. Faire le trajet de référence avec ARCore, arrêter et exporter la session
   complète **ZIP**. Une distance mesurée indépendamment est préférable au GPS.
4. Ouvrir **Paramètres → Caméra → VIO custom : Basalt expérimental** (ou chercher
   « Basalt »). ARCore est alors suspendu. Appuyer sur **START NEW BASALT RUN**,
   rester immobile brièvement, puis bouger doucement devant une scène texturée
   pour l'initialisation. Refaire le même trajet, même téléphone et même montage.
5. Appuyer sur **STOP + EXPORT BASALT RUN (ZIP)**. Revenir à ARCore avec la flèche
   de retour. Chaque enregistrement possède son moteur et sa provenance.

Si les informations usine sont absentes, si la caméra/les dimensions/le focus/
le recadrage/la stabilisation ne correspondent pas, ou si l'horloge caméra
n'est pas REALTIME, le test est bloqué ou ses images sont rejetées. Le message
indique la cause ; aucune trajectoire ARCore ne remplace silencieusement Basalt.
Une interruption d'IMU de plus de 30 ms arrête le calcul plutôt que de masquer
les mesures manquantes. Un changement d'activité termine la session custom ;
la reprise nécessite un nouvel enregistrement et une nouvelle initialisation.

## Ce que contient le ZIP custom

- `poses.csv` : poses natives, quaternion, compte de points observés/landmarks,
  état de qualité heuristique et distance acceptée par l'odomètre commun.
- `imu_raw.csv` : événements réels, type Android, temps et précision annoncée.
- `imu_interpolated.csv` : accélération interpolée entre mesures réelles aux
  instants du gyroscope, distincte des événements bruts.
- `camera.csv` : image/résultat associés par horodatage, exposition/ISO/durée,
  rolling skew, temps corrigé et motif d'acceptation ou de rejet.
- `optical_quality.csv` : diagnostics de l'exposition pendant cet enregistrement.
- `session_resources.csv` et métadonnées : même collecteur CPU/mémoire/batterie/
  thermique/latences/disponibilités que pour ARCore et PDR.
- `custom_metadata.json` : moteur, source, calibration et hypothèses, début/fin,
  compteurs, erreurs et statut. Les images ne sont pas sauvegardées.

Les valeurs GPU/températures indisponibles restent absentes avec une raison.
L'énergie mesurée vient de la batterie de tout le téléphone ; le CPU est celui
du processus de l'application. Les durées GPU des fenêtres ne couvrent pas les
mêmes surfaces de rendu dans ARCore et Camera2. Ce sont des modes complets,
pas une comparaison isolée des seuls solveurs.

```sh
python tools/telemetry/report.py arcore.zip basalt.zip --out comparison.json
```

Même montage, luminosité écran, conditions de lumière, température initiale et
configuration vidéo/GPS. Une série ARCore/Basalt/Basalt/ARCore réduit l'effet
de l'ordre et de la chauffe. Comparer distance, erreurs de fermeture, pertes
de suivi, débit et ressources ; une trajectoire exacte exige une référence
indépendante synchronisée. Aucun vainqueur automatique n'est calculé.

## Limites conservées dans l'application et les exports

La calibration usine transférée entre ARCore et Camera2 est **non vérifiée**.
Décalage temporel caméra/IMU supposé nul, bruit IMU générique, modèle pinhole
approximatif, OIS et distorsion non qualifiés. Le rolling shutter est horodaté
au milieu de la lecture, sans compensation géométrique du mouvement. Les
seuils points/landmarks indiquent une disponibilité, pas une covariance ni une
preuve de précision. L'essai sur le téléphone reste nécessaire.

La commande d'exposition est une interprétation indépendante des principes
du résumé du papier 2025. Le texte intégral n'a pas été accessible ; sa
reproduction exacte des équations et paramètres n'est pas certifiée.

Le replay Android automatisé utilise de vraies images EuRoC monoculaires
rectifiées et leur IMU, sans référence accessible au moteur. Il valide la
liaison JNI et la production de poses sur émulateur, pas la calibration ou
la précision du S21, ni une supériorité sur ARCore.
