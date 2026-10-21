# VECTOR VIO — panorama des opportunités, concurrence et validation (21 octobre 2026)

> **Recherche marché/documentation, aucune validation de demande ni de performance terrain.** Base Git : PR #47, `fbf07c385a9251639ace9905158fe04441e8b8bb`. Le prototype intégré conserve ARCore/PDR/GPX et Basalt expérimental. Aucun nouveau module métier n'est implémenté ici. Cette recherche alimente la **section 23** de `docs/VECTOR_VIO_ATLAS_INTERACTIF_v5.html` (document canonique, sans copie concurrente).

## Hypothèse centrale et segmentation du marché

L'actif potentiel n'est pas un « GPS centimétrique » ni un meilleur SLAM universel. C'est un **journal spatial de visite** : trajectoire locale + événements horodatés + photos/notes reliées à un équipement ou un endroit + preuves de couverture + revisite et export explicite. La VIO/ARCore donne le mouvement **relatif** pendant une session; la position absolue et le recalage de visites distinctes exigent des repères ou un processus de relocalisation éprouvé. Mesurer le **coût d'une décision métier** avant la qualité esthétique des traces.

Hypothèses de différenciation **à prouver** :
1. Déploiement local rapide sur smartphone existant, sans installation radio ni scanner spécialisé, sur un petit site non encore cartographié. Alternative simple souvent meilleure : QR codes, photos et plan manuel.
2. Inspection **à la demande** d'une zone limitée, associant preuves datées à des objets et permettant de constater ce qui *n'a pas* été observé.
3. Export local interopérable et conservation de la provenance/qualité sans transmission cloud par défaut.
4. Dégradation explicite VIO/IMU/GNSS et **POSITION INCONNUE** plutôt qu'invention de position lors de coupures. Une carte AR flatteuse n'est pas une localisation fiable.
5. Coût équipement faible **en hypothèse**; coût total réel dépend surtout de la préparation, correction des erreurs, montage/usage mains libres, formation, batterie, support et obligations réglementaires.

## Paysage concurrentiel vérifié (sources primaires consultées 21/10/2026)

| Famille | Produits existants et capacité documentée | Ce qui n'est **pas** une lacune concurrentielle | Zone de test VECTOR |
|---|---|---|---|
| Navigation intérieure radio | [Situm](https://situm.com/docs/introduction-to-situm-rtls/) : empreintes Wi-Fi/BLE, plans et calibration; [limitations/dépendances](https://situm.com/docs/smartphones-and-sensors-requirements/). | Position indoor et navigation existent déjà, sans devoir filmer en permanence. « Aucun beacon requis » n'est **pas** unique : Situm peut fonctionner avec Wi-Fi existant selon cas. | Sites éphémères, zones non calibrées, visites courtes, sans plan et sans réseau; vérifier bénéfice contre la calibration Situm. |
| Navigation magnétique/radio | [IndoorAtlas](https://support.indooratlas.com/support/solutions/articles/36000089604-use-cases-and-requirements) propose PDR/geomagnétique, POI et guidage AR; [2–5 m typiques](https://support.indooratlas.com/support/solutions/articles/36000035912-what-is-the-positioning-accuracy-of-indooratlas-), variables selon environnement. | L'AR indoor et les POI sont déjà des fonctionnalités commercialisées. | Rattachement au **bon objet** en zone dense, sous réserve de vrai recalage local. |
| Plateforme SIG indoor | [ArcGIS IPS](https://www.esri.com/arcgis-blog/products/arcgis-ips/announcements/simplifying-indoor-positioning-in-arcgis) : solution d'entreprise et cartographie des étages, infrastructure radio; [déploiements survey-less](https://www.esri.com/arcgis-blog/products/arcgis-ips/indoor-gis/surveyless-indoor-positioning). | Ne pas dire que chaque système requiert un long parcours de fingerprinting : Esri a aussi une méthode sans relevé classique. | Petit site ponctuel hors SIG; export compatible si un client dispose déjà d'un GIS. |
| Blue dot enterprise | [Pointr](https://www.pointr.tech/technology/faq) : SDK et beacons; positionnement sans Internet pour certaines opérations. | « Fonctionne hors ligne » n'est pas unique. | Pas d'installation préalable, puis preuve du gain économique réel. |
| AR localisation mondiale | [ARCore Geospatial / VPS](https://developers.google.com/ar/develop/geospatial) : localisation visuelle en zones couvertes; [vérification de couverture](https://developers.google.com/ar/develop/java/geospatial/check-vps-availability); [Cloud Anchors](https://developers.google.com/ar/develop/cloud-anchors) nécessitent le cloud. | Positionnement visuel, alignement et ancres partagées ne sont pas nouveaux; le VPS peut aussi fonctionner en zones intérieures couvertes. | Site privé non couvert/air-gap, traçabilité locale, ou besoin spécifique à un équipement. Tester *d'abord* disponibilité VPS. |
| Reprise de carte AR | [Apple ARWorldMap](https://developer.apple.com/documentation/ARKit/saving-and-loading-world-data) permet sauvegarde et relocalisation d'une visite. | « Sauver une carte et la réouvrir » n'est pas une innovation universelle. Notre ARCore actuel ne restaure pas automatiquement son état de suivi natif entre visites. | Recalage Android multiplateforme avec repères QR/AprilTag; mesurer le taux d'échec au retour. |
| Capture chantier | [OpenSpace Capture](https://www.openspace.ai/products/capture/) : parcours 360° et mise en correspondance sur plans/BIM, prise de photos smartphone également. | Inspection de chantier et positionnement de photos sur plan sont déjà très développés. | Microchantier, inspection de quelques actifs, sans 360° ni BIM, export succinct et local; disponibilité produit/canal de vente à étudier. |
| Documentation 4D/BIM | [Cupix](https://www.cupix.com/) : vidéos 360°, modèles 3D et annotations temporelles. | Les « annotations spatiotemporelles de chantier » sont une catégorie établie. | Journal léger et immédiat sur parc partiel, sans reconstruction 3D complète. |
| Scanner professionnel | [NavVis VLX 3](https://www.navvis.com/vlx-3) : lidar portable, SLAM, points de contrôle, précision de relevé. | Ne pas promettre survey-grade ou concurrencer son nuage géométrique avec le seul smartphone. | Triage léger/POI et préinspection, puis passer au relevé professionnel si tolérances requises. |
| Jumeaux numériques | [Matterport](https://matterport.com/) : capture smartphone et technologies plus spécialisées, jumeaux numériques; [plans](https://matterport.com/digital-twin-features). | « Faire une visite 3D avec un téléphone » est un marché actif. | Dossiers d'événements/localisation plutôt que numérisation complète d'un bâtiment. |
| Rendu navigation classique | Google Maps, Komoot, OsmAnd et autres outils GPX/offline. | Le hors ligne, l'import GPX ou la navigation vocale ne suffisent pas à différencier VECTOR. | Bifurcations proches observables, journal de passage et amélioration mesurée de décision. |

Les indications de disponibilité/portée des concurrents proviennent de leurs documents respectifs, pas d'un test comparatif indépendant. Aucun prix, ROI, part de marché ou degré de satisfaction client n'est vérifié. **Pas d'affirmations de supériorité** tant que protocole comparatif et métriques communes manquent.

## Catalogue élargi (24 hypothèses, sans taille de marché inventée)

**P0 : expériences rapides directement rattachées à une erreur ou un coût évitable**
- Maintenance usine/HVAC/locaux techniques : « photo/mesure rattachée à la mauvaise armoire » ; référence QR et plan manuel.
- Inspection photovoltaïque toiture/façade/carport : module/pince/coffret, photo de défaut et pièce; difficulté : module répétitif, verre réfléchissant, toiture et sécurité; QR ou numéro de module souvent suffisant.
- Réseaux techniques et sous-sols : marqueurs vannes, gaines, armoires et regards accessibles; plan existant + code-barres concurrent.
- Petit chantier/rénovation : parcours d'observations ciblées et suivi avant/après plutôt que scan 360° exhaustif; OpenSpace/Cupix concurrents directs.
- Audit de couverture de locaux : où un technicien est réellement entré, zones **non vues** distinctes de zones libres; contrôle manuel/checklist comme baseline.
- Kit de diagnostic/traçabilité SLAM intégré aux inspections : exporter événements de confiance/écarts pour équipes R&D, prestataires vision/robotique.

**P1 : valeur plausible, dépendance de relocalisation, repères ou terrain**
- Entrepôt/atelier temporaire : étagère/outil/stock; RFID/QR/réseau WMS compétitifs.
- Réaliser un parcours de remise en service (commissioning) : vérifier que tous les équipements nommés ont été visités.
- Hôtellerie, événement et exposition temporaire : guidage sans implantation permanente; cartes imprimées et QR peuvent gagner.
- Musées temporaires/expositions : déclencher histoires au bon endroit; apps AR existantes.
- Infrastructure souterraine/tunnel **autorisé et hors exploitation critique** : nicher/photos/mesures; scènes répétitives et absence GNSS.
- Maintenance des parkings/caves et micro-installations : inspection par secteurs; niveaux/escaliers et confusion d'étage.
- Inventaire forestier / biodiversité : arbres/placettes et photos; sous canopée, GPS biaisé; précision absolue impossible sans repères.
- Agriculture/serres : rangées répétitives, comptage d'incidents; répétitivité dégrade la vision.
- Parcours de vérification de panneaux/réseaux extérieurs : semis GNSS et dérive relative; repères absolus périodiques.
- Démonstration guidée d'une installation PV / visite commerciale B2B : histoire spatialisée; valeur perçue à tester.
- Formation et audit de parcours sécurité **non opérationnel** : comparer couverture théorique/réelle, pas guider des évacuations.
- Scan local court pour petits robots/AMR : pipeline de test et collecte; intégration capteurs externes/format ROS.

**P2 / faible priorité : différenciation faible ou contraintes fortes**
- Courses en supermarché : données rayons privées, gros concurrents, friction caméra.
- Navigation indoor grand public en hôpital/aéroport : cartes/plan/infra/exigences élevés; concurrents établis.
- VTT/trail/cyclisme : GPS et supports dédiés déjà efficaces; suivi smartphone caméra consomme et peut distraire.
- Retour sur ses pas sans visibilité, intervention pompier, secours : **R&D non destiné à l'évacuation ou l'emploi opérationnel**, GPS/caméra/IMU insuffisants en fumée dense.
- Relevé métrologique ou certification de bâtiment : exige capteurs et géoréférencement certifiés; smartphone non qualifié.
- Suivi personnes sans leur accord : écarter (vie privée et réglementation).

## Principes de tests et critères de sortie

| Expérience | Concurrent minimal à battre | Test proposé (cibles d'étude, pas résultats) | Go / No-Go |
|---|---|---|---|
| I01 — inspection 20 équipements, même visite | photos + QR + liste / plan annoté | 20 actifs, 3 visites, 2 testeurs, vraies erreurs d'attribution, temps total et révisions, coûts de préparation; tracker p95 de décalage depuis points indépendants | GO si moins d'erreurs et temps total/formation acceptable; écarter si QR seul gagne |
| I02 — retour le lendemain | QR/AprilTag au début de chaque zone | 10 POI, fermeture complète de l'app, revisite à 24 h, différents éclairages et orientations; taux de POI retrouvés et faux positifs | GO uniquement si relocalisation et attribution d'objet stables; sinon limiter produit aux sessions ponctuelles |
| I03 — microchantier sans plan | photos datées + checklist | 2 zones, 20 défauts, comparaison avec capture 360° existante et rapport manuel; temps d'installation à rapport final | GO seulement si avantage clair sur une tâche ciblée |
| I04 — GPX intersection vélo/trail | smartphone GPS/Komoot/OsmAnd | 10 bifurcations multi-trajets, vérité indépendante d'itinéraire, mêmes conditions, faux embranchements, arrêts, batterie, distractions | NO-GO si coût/sécurité annule le gain |
| I05 — ressources / tolérance panne | ARCore brut et GPS seul | parcours répétés ARCore / Basalt, perte de tracking, distance/pose indépendante et énergie, protocole ABBA | ne promouvoir Basalt que s'il domine sur un usage défini et coût raisonnable |
| I06 — entrepôt répétitif | codes-barres/WMS standard | 30 rayons presque identiques, test aveugle, confusion de rangées et redémarrage | NO-GO si sans fiducials persistants le système confond souvent les rayons |

**Mesurer** : taux de bonnes associations équipement↔observation, erreurs coûteuses, temps d'installation, temps à l'export, couverture utile, taux d'abstention et erreurs cachées, revisite après redémarrage, énergie, perception utilisateurs. Les seuils commerciaux doivent être fixés avec pilotes avant résultats; les chiffres d'essais ci-dessus sont des protocoles proposés.

## Limites structurelles qui changent les décisions

- ARCore actuel : état interne de suivi non sérialisé/restauré; les « cartes sauvegardées » VECTOR ne sont pas des cartes ARCore relocalisées.
- Native Basalt Android PR #47 : intégré en APK expérimental compilé, calibration factory **non vérifiée**, temps caméra/IMU supposé nul, rolling shutter non compensé, pas de précision terrain; pas de preuve que Basalt surpasse ARCore.
- Caméra non pointée vers texture ou éteinte : pas de VIO; PDR Java est piéton à pas, pas un estimateur fiable de vitesse vélo ou de déplacement arbitraire.
- Structures répétitives, reflets PV, obscurité, vibrations, profondeur insuffisante, fumée : reconnaissance/localisation peuvent être ambiguës; refuser d'attribuer un objet si incertain.
- Aucun mode ne prouve une précision absolue 10 cm sans ancrages/RTK/référence; certification métrique et navigation de sécurité exclues.
- Photos/vidéos/plans de sites techniques souvent confidentiels; offline-local/permissions/export doivent être examinés avec le client, pas présumés suffisants.
- Le téléphone tenu en main peut être incompatible avec des tâches industrielles; étudier support mains libres, caméra orientée et interaction minimale.

## Décision produit proposée (non adoptée)

**Candidat MVP** : « journal d'inspection spatial à faible préparation », pour 1–2 segments dans un bâtiment technique : choix zone/objet par QR/AprilTag, chemin relatif ARCore, photo/annotation horodatée, visualisation d'incertitude/segments perdus, rapport ZIP/PDF/CSV au choix, revisite seulement après preuve d'un recalage indépendant. La valeur provient des erreurs de rattachement évitées, **pas** du nombre de points SLAM.

**Alternative gagnante possible** : QR+photo+checklist sans tracking. La tester simultanément et l'accepter si plus performante; VECTOR pourrait devenir un logiciel de documentation d'inspection, avec VIO facultatif.

**Nouvelles entrées backlog** :
- O01 : 8–12 entretiens métier structurés (maintenance, installations PV, petits chantiers) et coûts d'erreur.
- O02 : comparer flux QR/plan seul, ARCore+QR et Basalt+QR sur mêmes observations.
- O03 : modéliser identité d'objet/zone/POI, origine, covariance/qualité et version de plan.
- O04 : prototype export local et checklist « vu / non vu / position inconnue ».
- O05 : relocalisation inter-visites avec fiducials et test réellement aveugle.
- O06 : étude consentement/retention données et interface mains libres/arrêt de capture.
- O07 : radar fumée gardé dans la branche R&D, sans revendication de sécurité.
- O08 : coût terrain et valeur monétaire validés par au moins deux entreprises avant abonnement.

## Sources & qualité des preuves

Sources primaires de capacité : liens fournisseurs/API ci-dessus, consultés le 21/10/2026. Elles établissent **l'existence des fonctions revendiquées par les fournisseurs**, non leurs performances comparatives. Le marché, les prix, la demande, les gains de temps, les seuils d'acceptabilité, les risques juridiques et la stabilité inter-session sont des **hypothèses non mesurées**. Les limites du code VECTOR sont vérifiées dans le dépôt au SHA indiqué. Les notes du projet sur la fumée et les tests restent inchangées. 
