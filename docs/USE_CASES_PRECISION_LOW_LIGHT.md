# Cas d'usage — précision locale, course, vélo, montagne et faible lumière

> **Étude exploratoire, 19 octobre 2026.** Intégration consultée : `feature/slam-explorer` au SHA `98cba16e45de162434c1b09d408e80b431524a93`. Documentation uniquement, pas de revendication de précision décimétrique ni de fonctionnalité de guidage de sécurité. Ce document complète [l'Atlas V5](VECTOR_VIO_ATLAS_INTERACTIF_v5.html) et la [roadmap centrale](ROADMAP_CENTRAL.md). Prioriser les **bénéfices mesurables** par rapport à une navigation GNSS standard, et non la précision comme objectif isolé.

## 1. Ce que « dix centimètres » signifie réellement

Trois grandeurs sont **différentes** :

1. **Précision relative locale** : retrouver une position par rapport à un départ / un repère connu, pendant une courte fenêtre, avec un petit écart. Un VIO peut être localement précis sans être juste dans le monde.
2. **Précision absolue géographique** : coordonnée latitude/longitude/altitude proche d'une référence géodésique indépendante. Une trajectoire VIO, même parfaite en relatif, ne la garantit **jamais seule** ; il faut un ancrage absolu fiable et une dérive maîtrisée.
3. **Exactitude du bon sentier / bon étage / bon côté** : choisir correctement entre deux chemins séparés de 1–3 m peut avoir beaucoup plus de valeur qu'afficher des coordonnées « ±0,10 m ». L'incertitude doit inclure erreurs systématiques, sauts et échecs.

**Cible 0,10 m = hypothèse de recherche**, pas capacité actuelle de VECTOR VIO, d'ARCore ou du GPS Android. Un GNSS RTK avec matériel/corrections et ciel dégagé peut être très précis ; la vision n'est pas automatiquement supérieure au GNSS et peut être plus mauvaise en faible lumière, mouvement rapide ou texture répétitive.

## 2. Cas concrets classés par plus-value probable

| Priorité | Cas d'usage | Problème du GPS ordinaire | Valeur additionnelle potentielle VIO+IMU+GNSS | Limite décisive |
|---|---|---|---|---|
| **P0** | **Trail running en forêt dense, vallon, canyon montagneux** | Couvert végétal, multipath, occultations ; trace qui saute entre sentiers voisins | Continuité relative sur une courte perte de GNSS, meilleure attribution d'embranchement, distance locale moins erratique | Sous-bois sombre, caméra secouée, coureur qui tourne le téléphone, dérive VIO |
| **P0** | **VTT / vélo sur sentier étroit ou route forestière** | Couvert et relief, positions erratiques en lacets | Mesurer les déplacements entre fixes, distinguer une épingle ou deux chemins proches, diagnostic de qualité | Vitesse + vibration + faible lumière + téléphone fixé : suivi visuel peut s'interrompre |
| **P1** | **Carrefour de deux chemins distants de 1–3 m** | Incertitude GNSS comparable à la séparation | Choix du chemin grâce à trajectoire récente, topologie de carte, cap et changements de direction ; demander confirmation si ambigu | Aucune certitude sans carte fiable ou observation discriminante |
| **P1** | **Retour sur trace en forêt / après courte zone sans GNSS** | Décalage du tracé, pertes de fixes | Retrouver les virages et la séquence de mouvements à l'échelle locale | Ne pas guider dans une zone dangereuse si la position n'est pas fiable |
| **P1** | **Course d'orientation et parcours techniques chronométrés** | Difficile de déterminer quel embranchement a été emprunté, découpage des segments | Analyse *a posteriori* des erreurs de parcours, trajectoire relative et temps de virage | Règles sportives et autorisations, position non garantie au décimètre |
| **P1** | **Vélo urbain sous ponts, tunnels courts, rues encaissées** | GNSS occulté et réfléchi | Continuité relative et recalage à la sortie ; différencier itinéraire parallèle | Faible texture, transitions de luminosité, autres usagers |
| **P2** | **Cartographie collaborative de sentiers, obstacles, largeur de passage** | GNSS insuffisant pour le côté précis du sentier ou un petit obstacle | Cartographie locale géoréférencée avec ancrages vérifiés ; observation de relief | Nécessite reconstruction/annotation, géoréférencement et confidentialité |
| **P2** | **Analyse technique de course, foulée, trajectoire d'un virage** | Fréquence et bruit du GNSS insuffisants pour des mouvements très locaux | Déplacement relatif à haute fréquence, courbure de trajectoire, répétabilité d'un segment | VIO porté sur le corps mélange mouvement du téléphone et trajectoire réelle |
| **P2** | **Accessibilité / guidage piéton en zone complexe** | Choisir rampe/escaliers/entrée précise | Distinguer des transitions locales et points d'intérêt | Exige repères sémantiques, cartes validées, fiabilité élevée ; ne pas promettre de sécurité |
| **Recherche** | **Fumée dense / pompier** | GNSS absent en intérieur, RGB inutilisable | PDR, radar, thermique, UWB/balises externes, détecter « position inconnue » | Aucun guidage opérationnel autorisé ; sécurité et matériel spécialisés |

**Priorité produit** : distinguer le bon embranchement et **réduire les trous de suivi** est probablement plus utile à court terme que promettre une géolocalisation absolue à 10 cm. Pour un coureur en montagne, un affichage honnête « position incertaine » vaut mieux qu'une fausse précision.

## 3. Branche spécifique : LOW LIGHT (course, vélo et montagne)

Scénarios à tester séparément : plein jour ; sous-bois ombragé ; crépuscule ; route éclairée de nuit ; forêt sombre ; sortie/entrée de tunnel ; pluie, gouttes sur l'objectif et éblouissement. Faire varier : marche, course, vélo sur route lisse, VTT sur vibrations ; fixation rigide sur guidon / poitrine / poche (les modes de port ne sont pas interchangeables).

**Mécanismes d'échec** : exposition plus longue → flou de mouvement ; faible nombre de points visuels ; vibrations ; forte vitesse angulaire ; phares qui saturent l'image ; changements brutaux d'exposition ; caméra occultée. ARCore distingue `INSUFFICIENT_LIGHT`, `INSUFFICIENT_FEATURES` et `EXCESSIVE_MOTION`. **Ne jamais extrapoler une trajectoire à travers un long trou de tracking comme si elle était mesurée.**

**Stratégie de développement, sans prétendre qu'elle existe déjà** :
- Journaliser séparément `tracking_state`, `tracking_failure_reason`, fréquence des poses réellement reçues, âge des fixes GNSS, cadence IMU, changement de segment, orientation et vitesse estimée.
- Comparer quatre configurations : GNSS seul ; FLP seul ; VIO seul ; fusion conservatrice GNSS+VIO. Ne pas compter deux fois des sources corrélées ; conserver les mesures brutes.
- Préférer GNSS/FLP lorsque la vision échoue ; VIO pour la continuité locale *uniquement* quand la qualité et la cohérence sont suffisantes ; sinon déclarer une **position incertaine**. Un PDR à pas de marche **ne convient pas au vélo** ; tester un modèle inertiel/contraintes vélo séparé.
- Ne pas imposer une lumière artificielle, le flash ou un téléphone tenu à la main comme condition de sécurité pour courir ou rouler ; optimiser d'abord acquisition et diagnostic, puis examiner les capteurs externes.

## 4. Protocole pour vérifier la plus-value réelle

**Plan minimal sur un même parcours**, répété trois fois et avec autorisations/conditions sûres : portion dégagée (contrôle), couvert forestier, sentiers parallèles, épingle, petit tunnel/sous-pont, portion sombre éclairée naturellement et zone de faible lumière contrôlée. Évaluer séparément course et vélo ; éviter de manipuler l'écran en déplacement.

**Référence indépendante** : levé RTK/total station de points fixes de contrôle, traces d'un système de référence validé ou itinéraire précisément mesuré ; noter les limites sous canopée. Ne jamais utiliser le GNSS Android mesuré **à la fois** comme entrée du filtre et comme vérité terrain. Ne jamais optimiser échelle/alignement sur les mêmes portions tenues à l'écart.

**Métriques exigées** : erreur de position absolue et relative p50/p95/p99 (m), dérive par distance (m/100 m), ATE/RPE seulement si vérité indépendante, taux de couverture de tracking (%), durée p95 des trous (s), temps de réacquisition, erreurs de choix d'embranchement (n), distance totale (erreur %), consommation (mWh/km), température, latence. **Rapporter aussi les échecs**, l'absence de mesure et la fraction du parcours exclue.

**Gates décisionnels expérimentaux, pas engagements de performance** : bénéfice statistiquement reproductible par rapport à GNSS/FLP seuls sur la même trace, **sans** augmentation des faux embranchements ; pas de métrique calculée seulement sur les portions faciles ; sortie explicite en état incertain si la confiance manque. Étudier la cible 0,10 m seulement après avoir démontré une amélioration au mètre puis au décimètre sur une référence adéquate.

## 5. Lecture critique des sources

- Google ARCore, [TrackingFailureReason](https://developers.google.com/ar/reference/java/com/google/ar/core/TrackingFailureReason) : les causes `INSUFFICIENT_LIGHT`, `EXCESSIVE_MOTION`, `INSUFFICIENT_FEATURES` sont des échecs documentés ; aucune garantie de précision.
- Google ARCore, [Performance considerations](https://developers.google.com/ar/develop/runtime) : flou lors de mouvements rapides et perturbation par vibration.
- Tomaštík et Varga, *Measurement*, 2021, [GNSS smartphone sous végétation](https://www.sciencedirect.com/science/article/pii/S0263224121003912) : mesures de téléphone, variabilité et dégradation sous arbres, centimètres uniquement dans certaines conditions favorables.
- *Forestry*, 2021, [Advances in smartphone positioning in forests](https://academic.oup.com/forestry/article-abstract/94/2/292/5901975) : RMSE horizontal 5,41–12,55 m en saison feuillue sur appareils testés ; conditions ouvertes 1,72–4,51 m ; les valeurs ne sont pas une garantie pour notre S21.
- *PLOS ONE*, 2023, [Effects of nearby trees on GNSS accuracy](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0283090) : multipath et dégradation sous canopée.
- *Sensors*, 2023, [Static positioning under tree canopy](https://pmc.ncbi.nlm.nih.gov/articles/PMC10056071/) : comparaison récepteur F9P vs Pixel 5, ~1,5 m sous canopée pour téléphone après traitement spécifique, mais protocole **statique de vingt minutes** : ne pas transposer aux coureurs/vélos.
- *Geodetski list*, septembre 2026, [revue GNSS sous forêt](https://ojs.srce.hr/geodetski-list/article/view/44106) : les approches multisenseurs améliorent potentiellement la continuité mais restent insuffisamment validées en opérationnel.

## 6. Tâches proposées au backlog central

- **L01 — P0** : enregistrer état/cause d'échec VIO, trous et fraîcheur GNSS par échantillon, sans ralentir les poses ; test de schéma CSV/ZIP.
- **L02 — P0** : campagne terrain course + vélo, jour/crépuscule/nuit, route/forêt/relief, répétitions et référence indépendante ; données horodatées, consentement/licences.
- **L03 — P0** : mesurer le gain GNSS seul vs FLP seul vs VIO seul vs fusion conservatrice ; p50/p95/p99, couverture, faux embranchements et consommation.
- **L04 — P1** : détection de mode dégradé/abstention, pas de position « 10 cm » inventée ni interpolation de tracking perdu ; tests sur tunnels/ombres.
- **L05 — P1** : moteur de choix de sentier topologique GPX avec incertitude, sentiers parallèles et épingles ; vérifier si le bon embranchement est réellement mieux identifié.
- **L06 — P1** : profils de fixation guidon / poitrine / poche, vibrations et orientation, en distinguant vélo d'un PDR piéton.
- **L07 — P2** : pilote cartographie locale / répétition de segments et analyse de foulée, uniquement si une valeur supérieure au GNSS est démontrée.
- **L08 — recherche** : faisabilité décimétrique **relative** sur courte fenêtre avec repères/contrôle externe ; documenter la dérive et la différence avec une précision absolue.

**État de toutes ces tâches : `NON_FAIT` ou `RECHERCHE`.** Il s'agit de nouvelles hypothèses, pas de fonctions déjà développées.
