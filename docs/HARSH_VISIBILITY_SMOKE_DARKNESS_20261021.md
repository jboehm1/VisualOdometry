# VECTOR VIO — niches de localisation en obscurité, fumée et environnement dégradé
**Date de l'analyse : 21 octobre 2026.** Recherche prospective, non-qualifiée pour sauver des vies, la lutte contre l'incendie ou la navigation en situation critique. Complément de l'Atlas HTML V5.5 et de la R&D fumée du dépôt. **Aucun changement moteur Android ni équipement vendu ici.**

## Résultat déterminant : quatre problèmes différents

| Contexte | Physique / échec principal | Smartphone ARCore/Camera2 seul | Approche qui mérite un test | Critère d'abstention |
|---|---|---|---|---|
| Nuit / obscurité sans fumée | Manque de photons et de points stables ; exposition longue = flou si le téléphone bouge | Fonctionne *parfois* avec éclairage actif et mouvement lent ; Camera2 optimise seulement l'image RGB dont nous avons accès, pas nécessairement le flux interne ARCore | lampe visible ou proche IR **compatible avec la sensibilité spectrale du capteur** (à vérifier), VIO+IMU, fusion qualité | Tracking perdu, flou, faible nombre de features + distribution spatiale insuffisante |
| Lumière faible avec fort mouvement | arbitrage gain ISO / temps de pose; cadence, rolling shutter, flou | Shutter court + ISO accru aide si signal suffisant. Boost logiciel/IA **ne recrée pas la géométrie absente** | contrôle qualité image, ROI pondéré, tracker avec mode « position inconnue » | Flou, absence de parallaxe utile / scène texturée |
| Fumée légère / moyenne | Diffusion et atténuation visible; profondeur trompeuse | Résultats imprévisibles, dépend de densité/longueur optique/aérosol; LIDAR peut revenir sur le nuage proche au lieu du mur | thermique LWIR et/ou radar mmWave, IMU ; comparer aux mesures externes | aucun contraste exploitable, retours radar instables |
| Fumée **très dense**, vapeur, feu | Occlusion, fumées chaudes émettrices en IR, saturation, diffusion, surfaces répétitives | **Ne sait pas voir à travers avec caméra ordinaire**, même avec un meilleur ISO/exposition ; la thermique LWIR **n'est pas magique** et peut être aveuglée par vapeur/fumée dense/objets chauds | radar mmWave 60/77 GHz + inertiel; thermique comme aide complémentaire selon conditions, repères physiques et refus de produire une route si non observable | aucune information géométrique fiable : LOCALISATION INCONNUE et repli sur procédures professionnelles existantes |
| Poussière, brouillard, fumée blanche simulée | Mie scattering, changements de réflectance, lidar perturbé | variable | privilégier essai radar+IMU; différencier poussière/fumée/steam quantitativement | confidence tracking dérive, obstacles non détectables |

**Éclaircissement** : NIR illumine la scène mais est aussi diffusé par les aérosols. LWIR détecte le rayonnement thermique, n'a pas besoin de lumière visible mais dépend du contraste thermique; son image ne peut être envoyée directement dans ARCore comme une source visuelle interchangeable. Les smartphones standard n'ont **ni radar d'imagerie 4D, ni caméra LWIR**, et tous les modes Camera2/ARCore ne partagent pas le même pipeline caméra.

## Carte des niches — fonction à acheter, contre-preuve, et limites

### P0 : zones d'essai **sans flamme et à risque faible**

1. **Inspection d'un local industriel en blackout (sans fumée)** — client hypothétique : maintenance/diagnostic ; souffrance = perdre ses points de repère dans une pièce sans éclairage fixe. Comparateur : lampe frontale, photographies et repères QR; la VIO n'a intérêt que si la caméra reste utilisable, le rendu donne plus de valeur et ne gêne pas le travail. *GO seulement si le suivi réduit les erreurs ou les re-visites*.
2. **Audits de tunnels/parkings/locaux souterrains non éclairés et autorisés** — comparer trois modes RGB normal, RGB+lampe et mode inertiel avec checkpoints. Problème de répétition des couloirs, de GNSS inaccessible et de revisite.
3. **Formation / parcours en fumée artificielle** — reconstituer l'itinéraire d'un mannequin de test et les pertes de tracking en **entraînement contrôlé sans feu**, supervision humaine. L'objectif est l'**analyse a posteriori** de trajectoire, pas l'aide au déplacement en situation réelle.
4. **Banc de mesure de robustesse multi-capteurs en environnement dégradé** — clientèle hypothétique : laboratoires et intégrateurs ROS/robotique. Produire diagnostics, pertes de tracking, coût CPU/énergie, dérive, parcimonie radar, et évaluation comparable; avantage éventuel = données et reproductibilité, pas « meilleur SLAM » auto-proclamé.

### P1 : applications intéressantes si performances prouvées

5. **Inspection robotisée de galeries/câbles et égouts non dangereux** — robot capteurs externes, radar+IMU ou lidar+thermique ; les options actuelles commercialisées et les contraintes d'étanchéité doivent être analysées. La VIO smartphone est au mieux un contrôleur d'interface.
6. **Usines de ciment, sciage, entrepôts poussiéreux** — poussière non équivalente à fumée chaude ; radar peut conserver des structures géométriques. Ne pas utiliser un prototype générique dans une zone explosive (ATEX).
7. **Re-localisation de robot dans atelier avec brouillard/fumée de test** — diagnostic technique, simulation en salle autorisée, non guidage d'humains; comparer radar-inertiel à lidar-inertiel et vision-inertiel.
8. **Inspection de parcs PV de nuit ou peu éclairés** — avec éclairage sécurisé et thermographie de diagnostic, intérêt possible à relier un résultat thermique à un module identifié; GPS+QR restent des références, ne pas faire de métrologie thermique ni de diagnostic de panne autonome non validé.

### P2 / recherche sous fortes contraintes

9. **Aide à la recherche de personnes en bâtiment en feu** — besoin énorme, mais matériel professionnel déjà commercialisé, exigences thermiques, robustesse, assurance, certification et essais indépendants considérables. **INTERDIT COMME FONCTION OPÉRATIONNELLE du prototype courant.** Explorer seulement collecte de données volontaires et analyse de formation avec professionnels.
10. **Assistance à l'évacuation en fumée dense** — la fausse assurance (« retour par ici ») est plus dangereuse qu'un refus de positionnement. Ne pas générer de consignes d'évacuation. Réserver à recherche, simulateur et études supervisées.
11. **Mines souterraines en activité** — poussière, zones explosives, radio contraignante et normes; possibilité de recherche hors site; pas de mise en œuvre réelle sans certification.
12. **Géolocalisation permanente d'équipes de secours** — technologie concurrente d'équipement professionnel et fort enjeu humain / vie privée; exclure comme promesse MVP.

## Capteurs : contraintes et prototypes possibles

| Matériel / capteur | Coût visible le 21/10/2026 | Valeur technique | Limitations bloquantes |
|---|---|---|---|
| **Galaxy S21 existant** RGB + IMU | 0 € additionnel | référence visuelle/inertielle ARCore, Basalt R&D ; test noir partiel avec lampe | **pas** de thermique ou radar imagerie intégré, même avec Camera2 / ISO |
| Lampe visible externe + marqueurs ArUco/AprilTag | coût variable, faible (ne pas inventer de prix) | permet d'éprouver un parcours de nuit avec identité de zones | elle ne rend pas transparente la fumée ; marqueurs disparaissent en occlusion |
| **TOPDON TC001 USB-C** | **225 € HT** prix catalogue fournisseur (TVA/livraison peuvent changer) | LWIR 256×192 ; prototype thermique contrôlé sur Android/Windows | accès aux **trames radiométriques horodatées, timestamps, fréquence effective, UVC/SDK et compatibilité Android S21 à vérifier**. Produit de diagnostic, non certifié pour présence de feu |
| **TI IWR6843ISK** mmWave 60–64 GHz | **248,23 €** affichés chez Mouser Allemagne, conditions de prix non déterminées ici | USB nuage de points, 3 TX/4 RX, angle approx. 120° azimut / 30° élévation | basse résolution angulaire, multipath, miroirs métalliques, effets d'orientation, synchronisation inertielle, alimentation/host et drivers. **Ce n'est pas une caméra** ni une carte 3D dense prête à l'emploi |
| **TI IWRL6432BOOST** mmWave 57–64 GHz | prix non vérifié | USB point cloud, 3RX/2TX, FOV 120°×80° annoncé | nuage plus épars, résultats SLAM non prouvés ; alternative de bench à comparer selon disponibilité |
| **FLIR K2 professionnelle** | **999 € HT** catalogue FLIR UE | thermique robuste 160×120, conçu pour lutte anti-incendie | n'est pas un module USB-C SLAM ; acheter une caméra de pompier n'apporte pas une pose 6DoF ni l'accès brut |
| Kit radar + thermique + IMU + boîtier | dépend des options | seule approche plausible pour géométrie robuste à forte perte du visible | intégration C++/ROS, drivers, synchronisation et calibration rigides, puissance, masse, ergonomie, certification; prototype sur PC d'abord |

**Budget labo prudent** : « radar IWR6843ISK (~248 € cité) + ordinateur déjà disponible » pour un premier nuage de points; « + thermique TC001 (~225 € HT) » seulement si une vraie API image/timestamp est démontrée. **Pas de promesse de navigation à travers la fumée pour 500 €** : capteur + intégration ≠ algorithme et vérité terrain. Les devis sont indicatifs, peuvent mélanger HT/TTC et requièrent contrôle lors de l'achat.

## Concurrents / état de l'art : ce qui existe déjà

- [Qwake C-THRU Navigator](https://www.qwake.tech/navigator) : appareil frontal mains libres avec caméra LWIR 320×240, contours, points chauds et indicateur directionnel d'entrée; quatre modes visuels (d'après fournisseur), communication avec un poste commandement. L'affirmation « 2x faster searches » provient du marketing Qwake, non de notre mesure. **Ne pas prétendre inventer la vision thermique AR du pompier**.
- [FLIR Firefighting Cameras](https://www.flir.com/de-de/browse/first-responders/firefighting-cameras/) : K2/K33/K75/K85/K95 professionnels, modes pour vision dans fumée, essais feu et certification variables selon version. Une fonction « caméra thermique » seule est déjà vendue.
- [DHS S&T technology availability (01/07/2024)](https://www.dhs.gov/science-and-technology/news/2024/07/01/st-makes-innovative-fire-safety-technology-available-real-world-testing) : dispositif monté au casque en essai pour traverser la fumée, démonstration déjà ancienne; non une autorisation réglementaire pour notre prototype.
- [SmokeNav (2024)](https://advanced.onlinelibrary.wiley.com/doi/10.1002/aisy.202400241) : **IMU au pied + radar mmWave**, grille probabiliste et segmentation sémantique. Important : **c'est une architecture spécialisée, pas le PDR du smartphone en poche**.
- [ROVTIO open source](https://github.com/ntnu-arl/rovtio) : caméra visible + thermique + IMU, odométrie traitant les flux hétérogènes. Précise les pertes de contraste thermique et la nécessité de calibrer chaque capteur. Code ROS, **non transposable directement en APK S21**.
- [ETIO open source](https://github.com/HITSZ-NRSL/ETIO) : odométrie monoculaire thermique inertielle à base de contours, pistes de méthode; prérequis et licence à vérifier avant réutilisation.
- [PL-LIT (2026)](https://arxiv.org/abs/2606.29259) : LiDAR–inertiel–thermique, calibration photométrique en ligne pour gestion AGC. **Prépublication**, performances revendiquées par auteurs, pas une preuve de performances dans la fumée de feu.
- [NIST Firefighting TIC](https://nvlpubs.nist.gov/nistpubs/Legacy/TN/nbstechnicalnote1499.pdf) : étude métrologique des performances des imageurs thermiques dans conditions incendie. Les images LWIR ne sont pas une garantie de détection à travers toutes concentrations de fumée/vapeur.

## Datasets : adéquation, coût de traitement et vraies limites

1. **SmokeBasement 2025**, [Zenodo DOI:10.5281/zenodo.15173055](https://zenodo.org/records/15173055). **Disponible**, ~27 GB, 6 séquences sous-sol et fumée d'entraînement; *seulement* thermique stéréo + LiDAR (horodatages hôtes pour LiDAR). **Pas d'IMU ni radar**; pas de vérité terrain indépendante certifiée. Candidat pour évaluer perte de visibilité LiDAR / robustesse d'images thermiques, pas directement un benchmark radar/IMU.
2. **FIReStereo 2025**, [site/données](https://firestereo.github.io/). Stéréo thermique+LiDAR+IMU et cartes profondeur calculées, 16 trajets, inclut fumée et nuit; source indique **204 594 images thermiques**, ~58% avec fumée; cadre drone/forêt/urbain : pas représentatif d'un pompier rampant dans un bâtiment. GT depth dérivée de LiDAR; à ne pas utiliser comme entrée estimateur évalué à la même vérité.
3. **SubT-MRS CVPR 2024**, [AirLab](https://superodometry.com/datasets). Données sous-terraines de robots avec RGB/LiDAR/thermique/IMU, multi-plateformes; smoke/dust/dark possibles. Vérifier individuellement capteurs présents, calibration et GT par séquence avant de parler de métriques.
4. **SNAIL-Radar-Indoor**, [site de référence](https://snail-radar-indoor.github.io/). Page annonce **217 séquences, ~851,52 minutes, ~255,68 GB** et fumée véritable; radar 4D+LiDAR+IMU, principalement pertinent pour notre question radar. **ALERTE CHRONOLOGIQUE : le changelog du site date la publication intégrale du 30/10/2026, soit APRÈS notre date d'analyse 21/10/2026**. Ne pas décrire les 217 séquences comme déjà obtenues/testées aujourd'hui. Vérifier accessibilité effective et licence des fichiers avant ingestion. Les trajectoires « reference » sont **produites par estimateurs** (LiDAR ou radar/IMU), pas une vérité indépendante absolue.
5. **SNAIL-Radar outdoor (2024)**, [données et étude](https://arxiv.org/abs/2407.11705). Radar 4D, LiDAR, caméras, IMU, nuit/pluie et référence calibrée sur TLS pour des sous-ensembles. Disponible pour un **premier harnais radar**, mais ne prouve pas résultats en fumée intérieure.

### Compatibilité pipeline : ne pas tromper les métriques

**Pas de simple « ajout dataset = comparaison équitable ».** Chaque source a ses modales, son horloge et sa GT particulière. Définir `sensor_modalities`, `sync_provenance`, `calibration_provenance`, `reference_source`, `reference_independent`, `ground_truth_coverage`, `trajectory_frame`, `smoke_type_and_density`, `license`, `download_verified`, `checksum_sha256`, `range`. Interdire un score ATE si pas de vérité terrain indépendante; garder sinon des proxies étiquetés « cohérence / fermeture de boucle / similarité à une référence d'estimateur ». Comparer sur les **mêmes séquences observables**, fréquence et budget matériel explicités.

Benchmark en escalier :
- Phase **E0 sans matériel** : registre de sources + smoke metadata + sample FIReStereo/SmokeBasement, tests parseurs et sanity temporelle. **Aucun chiffre prétendu avant exécution sur fichiers réels.**
- Phase **E1 obscurité contrôlée sans fumée** : S21 RGB+IMU, marqueurs QR/AprilTag, éclairage progressif/lampe, orientation/tremblement, masquage caméra, pose de contrôle externe et répétitions. Comparer tracking reconnu / invalide, erreurs de position relatives, temps et batterie; ne jamais s'appuyer sur « le système a tracé quelque chose ».
- Phase **E2 fumée d'entraînement sans feu** : salle autorisée et ventilée avec professionnel sécurité, seule expérience possible; caméra, thermique externe, radar à distance, trajectoire mesurée autrement. **Ne jamais tester avec combustion réelle, personne confinée ou fumée non contrôlée.**
- Phase **E3 PC radar + IMU** : nuages horodatés, calibration temporelle/extrinsèque, ICP radar inadéquat si densité faible; statistiques de returns, Doppler cohérent, observabilité et navigation R&D simulée. Pas de portage direct Android ni de HUD sécurité avant preuve.
- Phase **E4 thermique+radar** : valeur **incrémentale** du canal LWIR par rapport au radar/IMU seul et au RGB/IMU, en conditions changeantes, sans inférer une image réaliste d'un nuage de points épars.

**Décision UX** : statut explicite `VISIBLE_VIO` / `ILLUMINATED_VIO` / `THERMAL_EXPERIMENTAL` / `RADAR_EXPERIMENTAL` / `INERTIAL_DEAD_RECKONING_UNCERTAIN` / `POSITION_UNKNOWN`. L'indication en absence d'observabilité doit être « aucune localisation fiable disponible », jamais flèche d'évacuation.

## Planification et protection des autres chantiers Git

Chantier courant **documentaire** sur branche isolée `docs/market-opportunities-atlas-20261021`, PR #48 vers la PR #47; réutiliser la R&D fumée existante, ne pas écraser le code ou les datasets de #17. Modifier uniquement Atlas HTML, présente note et coordination, ainsi que tests UI si nécessaire. **Ne pas toucher aux moteurs, PDR, APK, workflow benchmark central** sans mandat ciblé et tests. Prioriser revue des licences/données avant de télécharger 27GB/255GB en CI GitHub Actions.

## Risques majeurs à ne pas gommer

- **Sécurité** : aucun dispositif proposé n'est adapté au guidage de pompiers, à l'évacuation, à la détection garantie de personnes ou au sauvetage; une localisation fausse peut tuer.
- **Capteurs** : radar voit éventuellement des volumes/retours, *pas* une scène photographique, pas nécessairement les obstacles non métalliques; MIMO 4D « 4D » désigne range/azimuth/elevation/Doppler, **pas** la pose 6DoF instantanée.
- **Comparaison** : thermique peut gagner de nuit et perdre dans scène isotherme; radar peut gagner en fumée et échouer dans couloirs métalliques multipath, ories faibles et scènes répétitives.
- **Déploiement** : ATEX, températures, port de casque, bouclier thermique, autonomie, communication, confidentialité, ergonomie, poids, certification, responsabilité; les prototypes USB ne répondent pas à ces exigences.
- **Données** : trajectoires de référence dérivées des estimateurs et profondeur LiDAR projetée ne doivent jamais devenir fausse « GT absolue ».

*Sources consultées le 21/10/2026. Fournisseurs = description non vérifiée indépendamment. DOI et dépôts = nature du dataset ou du code, pas validation terrain de VECTOR.*
