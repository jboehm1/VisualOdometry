# VECTOR VIO — test réel de perte GPS avec poses ARCore de téléphone (20/10/2026)

## Problème traité

L'idée « une fois le GPX recalé, la caméra suit exactement la route sans GPS » est **une hypothèse à mesurer**, pas une garantie. Une chaîne ARCore visuellement stable ne mesure pas sa propre erreur horizontale absolue, et les estimations de pose sont relatives au repère 3D en mémoire.

Nous réutilisons le **moteur réel Java GPX Android** (`ArRouteGuide`) pour la partie géocalage/GPS et ajoutons un score de dérive relative sur des *poses ARCore publiques déjà calculées par un vrai Google Pixel*. Ce n'est pas le S21 de l'utilisateur, et on ne rejoue pas la vidéo dans ARCore sur un CI Linux.

## Jeux et protocole

- **ADVIO-09** (Cortés et al. ECCV 2018, https://doi.org/10.5281/zenodo.1476931), archive 70 695 364 octets, MD5 épinglé `a5fedb98e2f50954c670e61d04642286`, licence **CC BY-NC 4.0**.
- Vraies données : **2 687 poses ARCore Pixel** (~89,5 s) et **9 662 poses de référence** synchronisées issues de la navigation inertielle reconstruite avec points de fixation manuellement annotés. La référence n'est pas une vérité purement optique/surveyée sans incertitude.
- Dans cette archive, le fichier `iphone/platform-location.csv` n'est **pas présent** : 0 GPS réellement disponible pour cette séquence. Le test de la fusion crée des pseudo-fixes **SYNTHÉTIQUES**, uniquement avant la coupure, avec bruit gaussien sigma=1,5 m/axe et graine déterministe. Ne jamais les présenter comme du GPS mesuré.
- Le GPX proxy est formé des coordonnées locales de la référence, sans nom de route réel. L'estimateur géographique ne colle pas son résultat aux points de route. **Pendant la période de coupure, aucune vérité n'est fournie à l'estimateur**.
- Scénarios : coupure GPS après **20 s** ou **40 s**, puis une durée jusqu'à la fin (~70 s et ~50 s). Mêmes poses Pixel, référence réservée au score de sortie et à l'amorce GNSS synthétique.
- Analyse complémentaire de la caméra elle-même : SE(2) métrique (yaw + translation, **jamais mise à l'échelle**), ajusté uniquement sur la période précédant la coupure, puis test sur le reste. Ce score est **optimiste** sur la disponibilité d'un bon recalage initial et ne mesure pas l'appli APK.

## Résultats CI reproductibles

Workflow `GPX • real phone ARCore poses with GNSS dropout` : https://github.com/jboehm1/VisualOdometry/actions/runs/39335466027, **SUCCESS** sur code `afae3013d39b9499f75e9dfe360254eb38be007a`.

| Mesure | Coupure après 20 s | Coupure après 40 s |
|---|---:|---:|
| GPS simulé durant amorce (1 Hz) | 21 fixes | 41 fixes |
| Plus grande séparation des poses Pixel durant amorce | 14,169 m | 14,372 m |
| Seuil nécessaire au recalage Java | 15 m minimum | 15 m minimum |
| Résultat `ArRouteGuide` | **NEED_BASELINE : tracé masqué** | **NEED_BASELINE : tracé masqué** |
| Calibration de caméra SE(2) : erreur RMSE sur période d'amorce | 5,494 m | 7,295 m |
| Erreur 2D absolue post-coupure au recalage initial p50 | 13,854 m | 6,538 m |
| Erreur 2D absolue post-coupure au recalage initial p95 | **31,996 m** | **21,739 m** |
| Erreur de déplacement relatif depuis la dernière pose d'amorce p95 | **19,169 m** | **13,502 m** |
| Temps de coupure de GPS testé | 69,52 s | 49,53 s |
| Poses externes scorées après coupure | 2 086 | 1 486 |

**Lecture correcte :** sur cette séquence particulière, le moteur Java **refuse** un calage car les points ne couvrent pas la distance de base nécessaire. Cela prévient une impression trompeuse de précision. Les grandes erreurs du diagnostic SE(2) représentent un mélange de tracking ARCore externe, orientation/translation initiales, éventuels décalages de caméras/corps et imperfection de la vérité ADVIO. **Il serait faux d'affirmer que ces chiffres sont la dérive pure du Galaxy S21 ou du ruban GPX observé**.

Les erreurs relatives depuis la dernière pose de calibration évitent d'attribuer tout le décalage initial aux mouvements ultérieurs, mais conservent le risque d'un angle initial mal observable.

## Exécution réutilisable et source de vérité

```bash
# Validation des contracts et de l'estimateur Java GPX réel
python3 -m unittest discover -s tools/gpx_ar/tests -v

# Entrée ADVIO originale déjà téléchargée par le pipeline modulaire existant.
python3 tools/gpx_ar/real_phone_dropout.py \
  --advio-zip /data/advio-09.zip \
  --out campaign_results/gpx-blackout \
  --calibration-seconds 20 40

# Fonctionne aussi avec d'autres archives ADVIO-NN présentant la même arborescence.
# Pour un véritable export S21 :
python3 tools/gpx_ar/replay.py /data/vector_vio_gpx_session.zip \
  --out campaign_results/gpx-actual-phone \
  --input-origin real_capture
# Et --truth /data/reference_independante.csv --reference-note "methode..."
# si un contrôle géodésique indépendant et synchronisé est disponible.
```

Le workflow dédié télécharge le dataset réel déjà connu de `tools/campaigns/advio09_real.py` et produit de petits JSON/replays CSV, **sans republier vidéo brute ni trajet pseudo-GNSS à partir de vérité terrain**. Les archives d'autres versions ADVIO sont acceptées localement sans renommer les chemins. Audit optionnel du GPS vrai de l'iPhone enregistré séparément; aucun GPS d'un autre appareil n'est silencieusement inventé ou fusionné.

## Suite minimale (sans complexité inutile)

1. À l'écran, distinguer qualité de tracking ARCore (TRACKING/LOST), disponibilité d'un recalage géographique (CANDIDATE/NEED_BASELINE/INCONSISTENT) et mode VIO_ONLY. Ils ne sont pas interchangeables.
2. Conserver le seuil `GeoAlignment` qui empêche un faux placement certain. Continuer à marcher ou sélectionner une ancre utilisateur fiable lorsque la géométrie est insuffisante. **Ne pas contourner l'absence de cap observable.**
3. Rejouer 2–3 vrais parcours S21 avec GPS/IMU/poses et une référence externe indépendante (repères physiques, trajectoire RTK ou caméra externe), marquant les coupures de 30/60/180 s. Mesurer p50/p95, orientation, `tracking_coverage`, retour de GNSS, écran verrouillé. Le status ARCore seul n'est pas une barre d'erreur statistique.
4. Réutiliser le même benchmark après toute amélioration de l'algorithme d'alignement ou de fusion à fenêtre glissante; ne déployer qu'en présence de gain mesuré sans accroissement du taux de fausse confiance.

**Conclusion :** on peut très bien avoir un ruban *visuellement* fluide pendant une perte de GPS, mais la correspondance géographique peut devenir erronée. Ce test justifie de mesurer la dérive et le recalage séparément, pas d'ajouter immédiatement un factor graph lourd ni un seuil de temps arbitraire.

Sources : https://github.com/AaltoVision/ADVIO ; https://www.ecva.net/papers/eccv_2018/papers_ECCV/html/Santiago_Cortes_ADVIO_An_Authentic_ECCV_2018_paper.php
