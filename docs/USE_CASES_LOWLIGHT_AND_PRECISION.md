# VECTOR VIO — Vélo, faible luminosité et localisation de précision

**Date : 19 octobre 2026 · statut : hypothèses produit à valider, pas fonctionnalités certifiées.**
Base consultée : `feature/slam-explorer` SHA `98cba16e45de162434c1b09d408e80b431524a93`. Développement documentaire isolé : `docs/lowlight-bike-precision-usecases`. Aucun fichier Android, manifeste, workflow ou module d'un autre chat modifié.

## Ce qu'on cherche vraiment à gagner

La position GNSS ordinaire est souvent suffisamment bonne en espace ouvert. Une odométrie visuelle/inertielle ne justifie sa complexité **que si** elle apporte un bénéfice mesurable : continuité lors des trous GNSS, discrimination de sentiers voisins, trajectoire locale plus lisse, mesure des changements relatifs rapides ou géométrie détaillée du passage. **Une résolution de 10 cm n'est pas une précision absolue de 10 cm.** Le VIO monoculaire/ARCore peut mesurer des déplacements relatifs fins sur un segment favorable, tout en dérivant sur plusieurs minutes ; l'origine globale, le cap, l'échelle et les ruptures de tracking doivent être quantifiés. La fusion avec un GNSS biaisé ne corrige pas automatiquement ce biais.

## Matrice de valeur des cas d'usage

| Rang | Usage concret | Problème GNSS / besoin | Apport plausible VIO + IMU | Obstacle / critère |
|---|---|---|---|---|
| **A** | VTT, trail et course sur **sentiers parallèles** en forêt, épingles de montagne, bifurcations à quelques mètres | Couvert végétal, multipath, GPX ambigu | Géométrie des virages, déplacement local, correspondance au tracé **avec abstention** si ambigu | Montée/descente, cap, vibration, masquage, durée du trou GNSS ; comparer à référence indépendante |
| **A** | **Tunnel, passage sous roche, sous-bois profond**, puis retour GNSS | Absence de fixes et saut de position à la reprise | Continuité relative temporaire, segment d'incertitude explicite | Dérive croissante ; caméra parfois inutilisable ; ne jamais combler fictivement les trous |
| **A** | **Vélo à faible lumière** (crépuscule, route éclairée) | GNSS parfois suffisant, mais intérêt pour trajectoire locale et coupures | Expérience de robustesse et sélection adaptative de capteurs | Flou de mouvement, faible texture, vibrations ; ne pas supposer un gain avant mesure |
| **B** | Course à pied / trail en montagne, passages serrés, lacets | GPS parfois saute entre lacets voisins | Orientation et trajectoire relative entre fixes | Mouvement corporel, port du téléphone, occlusion ; PDR course à calibrer distinctement |
| **B** | VTT technique / analyse de ligne sur une descente, choix de trajectoire | Différences locales inférieures à la précision GPS | Comparaison **relative** de lignes et virages dans un repère commun | Fixation rigide, fréquence réelle, synchronisation et référence centimétrique |
| **B** | Exploration de sentiers et création de GPX plus fidèle | Traces GNSS irrégulières sous arbres ou falaises | Lissage sous contraintes physiques et détection des ruptures | Risque de tracer un chemin faux si VIO dérive ; conserver les observations brutes |
| **B** | Courses d'orientation / épreuves hors route | Choix d'un chemin parmi des voies proches | Indice local de direction/virage, non preuve d'emplacement absolu | Les règles sportives peuvent interdire l'aide ; pas d'assistance trompeuse |
| **C** | Sports de précision, chronométrage et passage d'une ligne | Déterminer passage à un endroit précis | Détection de franchissement **relatif** si repère local connu | Un GPS de smartphone ou ARCore non calibré ne certifie pas le franchissement à 10 cm |
| **C** | Robotique de terrain / relevé de parcours / accessibilité | Besoin de répétabilité géométrique | Cartographie locale + boucles + repères visuels | Pour vraie précision centimétrique, envisager RTK GNSS, marqueurs, LiDAR/stéréo et calibration |
| **Recherche séparée** | Environnement enfumé / secours | GNSS absent et caméra RGB souvent aveugle | IMU à court terme ; potentiels capteurs externes radar/UWB/thermique | **Jamais présenté comme guidage opérationnel** ; revue sécurité et vérité indépendante |

**Contre-exemples importants :** route dégagée avec bon ciel GNSS ; vélo nocturne sans éclairage ni texture ; mouvement rapide et caméra secouée ; sentier de montagne avec falaises masquant aussi la vision ; longues coupures GNSS sans repères ni fermeture de boucle. Dans ces cas, VIO peut faire **moins bien** que GNSS seul. La montagne n'est pas automatiquement un bon terrain pour la vision : couvert forestier, parois, ombres et vibrations se cumulent.

## Profils faible luminosité à distinguer

1. **Jour → crépuscule** : mesurer le seuil de dégradation en continu, à vitesse et parcours comparables.
2. **Nuit en ville éclairée** : contrastes variables, phares, scintillement, flou et surfaces répétitives.
3. **Forêt ou montagne de nuit** : faible texture, caméra presque noire ; GPS/FLP prioritaire s'il reste fiable ; **aucune pose VIO fictive**.
4. **Tunnel** : la vision peut échouer malgré une bonne inertie ; tracer la durée depuis la dernière pose fiable.
5. **Course à pied de nuit** : mouvement oscillant et orientation du téléphone variables ; distinguer port à la main, torse et sac.
6. **Vélo** : montage rigide, vibrations haute fréquence, occultation et stabilité de la caméra. **Le PDR à pas ne s'applique pas au pédalage** ; un modèle spécifique vélo demanderait capteurs/contraintes adaptés.

## Cibles et garde-fous de précision

- **10 cm** : objectif de recherche *local et court terme*, **pas** précision annoncée pour l'application ; une précision absolue globale à ce niveau nécessite en pratique une référence/calibration bien plus exigeante que le GNSS grand public et des essais contrôlés.
- Rapporter séparément : erreur **absolue** XY et altitude, erreur **relative** sur fenêtres 1/5/10/30 s, distance cumulée, erreur de cap, couverture de tracking, temps avant dérive, latence, autonomie et taux de **mauvaise branche GPX**.
- Une trajectoire qui paraît lisse n'est pas nécessairement correcte ; les métriques ATE/RPE dépendent des repères, de l'alignement, de l'horloge et de la référence. Ne jamais utiliser une trace GNSS ordinaire comme vérité absolue « 10 cm ».
- Références souhaitables selon scénario : **RTK GNSS contrôlé en ciel ouvert**, repères topographiques relevés, parcours à balises, photogrammétrie calibrée, système de mesure local. Mesurer la qualité propre de la référence en forêt/tunnel ; le RTK ne résout pas magiquement les pertes de satellite.
- L'utilisateur doit voir **GNSS**, **VIO fiable**, **VIO dégradée**, **position incertaine** ; revenir au GNSS sans téléportation de trajectoire et ne pas annoncer un chemin unique si plusieurs sont plausibles.

## Campagne de test prioritaire sur Galaxy S21

**D'abord** fixer le téléphone solidement, caméra orientée vers des surfaces texturées, éviter l'usage en main à vélo ; parcourir à vitesse modérée un trajet sûr en journée puis au crépuscule (pas d'interaction écran en roulant). Enregistrer simultanément GPS direct, FLP, VIO/ARCore, IMU, timestamps, vitesse, lumière estimée si disponible, états de tracking et raisons d'échec ; noter support/vitesse/météo/éclairage. Comparer à une trace de référence indépendante **sans optimiser la transformation sur les données évaluées**.

**Ensuite** répéter trois fois chaque catégorie : route ouverte (baseline), sous arbres, canyon rocheux/urbain, virages serrés, passage court sans GNSS, nuit éclairée. Tester vélo **et** course à pied séparément ; distinguer téléphone au guidon, sur torse et en poche. Conserver les segments non mesurés comme manquants et les échecs de tracking.

**Critère d'intérêt produit :** amélioration reproductible face à GNSS/FLP seul (par ex. moins d'erreurs de branchement GPX et meilleure couverture avec erreur bornée) **sans augmenter le taux de fausses certitudes**. Les seuils chiffrés seront fixés **après** une première campagne de référence ; aucun « 10 cm » promis à ce stade.

## Pistes techniques ordonnées

- **P0 : instrumentation** qualité ARCore, motifs d'échec (`INSUFFICIENT_LIGHT`, `EXCESSIVE_MOTION`, `INSUFFICIENT_FEATURES`), luminosité/exposition si accessibles, IMU, pose rate vs VIEW rate, resets, GPS/FLP brut et segments. Tester de nuit avant d'investir dans des modèles.
- **P0 : benchmark par environnement** et évaluation de l'incertitude/abstention, en gardant les moteurs Android ARCore/PDR Java et les baselines Python distincts.
- **P1 : fusion adaptative GNSS/FLP + VIO**, uniquement quand la qualité est démontrée ; pondérer l'incertitude, éviter la double-comptabilisation de GNSS et FLP corrélés.
- **P1 : matching GPX multi-hypothèses**, topologie des sentiers, vitesse/cap, historique et abstention lorsque deux sentiers restent plausibles.
- **P2 : VIO robuste à faible lumière** : choisir exposition/cadence, modèles d'amélioration d'image ou apprentissage si et seulement si un benchmark hors entraînement démontre un gain réel à budget énergie raisonnable ; le débruitage peut inventer des textures.
- **P2 : références externes** (RTK, baromètre, marqueurs, LiDAR/stéréo, capteurs vélo) lorsque l'objectif de précision justifie coût, intégration et autonomie.

## Lectures de référence (pour cadrer, non preuve de performance VECTOR VIO)

- ARCore, suivi et échecs de tracking : https://developers.google.com/ar/reference/java/com/google/ar/core/TrackingFailureReason
- ARCore, limites environnementales : https://developers.google.com/ar/develop
- Android Location API : https://developer.android.com/develop/sensors-and-location/location
- Google Fused Location Provider : https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient
- Android GNSS measurements : https://developer.android.com/develop/sensors-and-location/sensors/gnss
- GNSS en environnement contraint, publications et guides de référence : https://www.gps.gov/systems/gps/performance/accuracy/

## Coordination

Les tâches détaillées et leur état restent dans `docs/ROADMAP_CENTRAL.md` et `docs/ROADMAP_CENTRAL.json`. L'Atlas HTML V5 conserve l'historique visuel ; **ce document ne modifie pas l'Atlas ni n'invente une fonction Android disponible**. Toute implémentation doit passer par une branche séparée et préserver `MainActivity.java`, manifeste, replay, PDR, GPX, FLP et les tests combinés.
