# VECTOR VIO — approfondissement des niches, barrières et angles mort des concurrents (21 octobre 2026)

> Recherche de capacités concurrentes et **hypothèses de différenciation non validées**. Ne pas confondre la disponibilité des fonctionnalités fournisseurs avec performances comparatives, adoption, retour sur investissement, conformité ou attribution exacte des positions VECTOR. Complément à `USE_CASE_OPPORTUNITY_LANDSCAPE_20261021.md`; Atlas HTML V5.3 = synthèse consultable.

## Correction stratégique déterminante

Les catégories **« mobile offline + inspection + photos + QR »** et **« une anomalie automatiquement placée sur un plan »** ne sont pas des espaces vides :

- **OpenSpace Field**, communiqué le **3 février 2026**, propose déjà l'autolocalisation des problèmes depuis smartphone, notes vocales et synchronisation Procore/Autodesk. Voir [communiqué OpenSpace](https://www.openspace.ai/press-releases/openspace-announces-general-availability-of-openspace-field-bringing-visual-intelligence-directly-into-field-execution/) et [punch list](https://www.openspace.ai/resources/use-cases/punch-lists/). C'est un concurrent direct à notre ancien concept générique de journal chantier.
- [PlanRadar](https://www.planradar.com/us/product/punch-list/) propose tâches liées aux plans/BIM, photos, formulaires et rapports; le simple « rapport digital avec POI » n'est donc pas distinctif.
- [Raptor Maps](https://raptormaps.com/products/solar-inspections-analytics) et son [outil terrain offline](https://raptormaps.com/products/solar-management-platform) ont localisation géographique d'anomalies PV et maintenance; [Raptor Solar DG](https://raptormaps.com/products/raptor-solar-dg) couvre même le segment C&I. [Scanifly Mobile](https://scanifly.com/product/) propose collecte et listes d'inspection PV. Ne pas revendiquer une « première app inspection PV hors ligne ».
- [Fulcrum](https://www.fulcrumapp.com/gis/), [Dalux FM](https://www.dalux.com/en-gb/products/dalux-fm/), [GoCodes](https://gocodes.com/features/mobile-asset-tracking-app/), [Zapworks Spaces](https://zap.works/spaces/industrial-facilities/) et [InMapz](https://inmapz.com/) couvrent sous des formes différentes collecte SIG, inspection QR, offline, navigation d'équipements et plans. « Aucune infrastructure / offline / scan QR » ne suffit pas à qualifier une niche sans concurrence.
- **Travaux cachés et réseaux** : [DroneDeploy Utilities](https://www.dronedeploy.com/solutions/utility/), [Fieldpost](https://www.fieldposthq.com/industries/excavation-sitework) et surtout [DataXplor](https://www.groupe-geoliance.com/en/products/data-capture/dataxplor) associent déjà photos, tranchées avant rebouchage, reconstruction et parfois RTK. L'usage *as-built précis* n'est **pas** un créneau libre; une VIO S21 non étalonnée n'est pas acceptable comme mesure d'implantation.
- **Portes coupe-feu et conformité** : [Fire Door App](https://firedoorapp.co.uk/fire-door-inspection-software) et [DoorTRACE](https://play.google.com/store/apps/details?id=uk.co.doortrace.engineer) proposent QR, visites hors ligne, photos, plans et rapports. Ce domaine est encadré; un POC SLAM ne doit pas se présenter comme outil de certification.
- [Arckium Mobile](https://arcana-soft.com/en/product/mobile) montre que QR/CMMS/voix/offline sont déjà réunis dans une app technicien.

## Nouvelles hypothèses à considérer / déclassement de marchés saturés

| Opportunité | Segment acheteur plausible (non vérifié) | Ce qui pourrait manquer sur le marché | Pourquoi VIO, et quand ne pas l'utiliser ? | Référence minimale et protocole |
|---|---|---|---|---|
| **Q1. Contrôle d'assemblage des systèmes de montage PV** | fabricants de rails/pinces, installateurs de toits, SAV | dossier **composant / position sur calepinage / avant-après** lié aux références exactes, surtout projets atypiques | VIO guide l'itinéraire; **aucune photo ne prouve le couple de serrage**. Étiquette QR/numéro + preuve instrumentale prime pour le contrôle. | Photo + check-list + repère de rail; 20 pièces semblables, erreurs d'identité et temps total. |
| **Q2. Réception de projets PV complexes (carports, façade, clôture)** | installateurs de sous-ensembles et maîtres d'ouvrage | documentation par zone et pièce pour lots évolutifs, avec exports interopérables | Relier la séquence de passage à un plan. VIO secondaire au repérage d'objet, pas une métrologie. | Plan annoté + 2D code ; 2 équipes, 30 anomalies, dossier client. |
| **Q3. Travaux cachés avant fermeture (gaines/plafonds/cloisons)** | petites entreprises MEP/électriciens | index rapide de photos sur emplacement **sans maquette** | Trajet court VIO entre repères *connus*, jamais profondeur ou positions absolues garanties. Relevés réglementaires indépendants. | Photos datées + repères de mur ; 15 installations, restitution aveugle après fermeture simulée. |
| **Q4. Petit chantier de retrofit / bâti existant sans BIM** | installateurs CVC, artisans, expertises | mini-plan des anomalies sur un site trop petit pour système chantier enterprise | VIO facultative, marche limitée et points fixes QR; comparer très strictement à plan PDF avec épingles. | Plan simple + photo; coûts de déploiement/rapport et erreurs. |
| **Q5. Traçabilité dans local technique interdit au cloud** | exploitants sensibles, salles techniques | collecte locale et preuves stockées uniquement sur appareil, export piloté | avantage potentiel de confidentialité **à valider auprès d'un client**, pas preuve de conformité. | Formulaire PDF offline + QR sans compte, même site. |
| **Q6. Rondes de racks en data center** | techniciens d'exploitation | association très dense aux **bonnes baies et positions** | VIO seule confond des couloirs/racks identiques; obligation codes rack et règles de confidentialité. | Barcode de baie + CMDB existante, taux de faux objet, chronométrage. |
| **Q7. Inventaire/évolution des installations en gaines verticales** | maintenance d'immeubles et facilities | visite courte multiétage avec événements et liens entre équipements | l'IMU/baromètre ne donne pas toujours le bon étage; repères étage/porte obligatoires. | Plan par étage + QR; audit sur 3 niveaux. |
| **Q8. Expertises dégât des eaux/assurance avec revisite** | petits cabinets et rénovateurs | avant/après contextualisé à un objet sans scan 3D coûteux | photo + plan satisfont peut-être tous les besoins ; consentement/confidentialité. | album photos structuré, même scène après 24h. |
| **Q9. Portes coupe-feu** | inspecteurs spécialisés | probablement **faible différenciation** ; QR/plan/PDF déjà fournis | pas de certificat automatique, pas d'inférence de la conformité via position | vérifier pain non résolu avec métier avant coder. |
| **Q10. Actifs médicaux en hôpital** | responsables facilities | **forte concurrence RTLS** et confidentialité; téléphone-caméra peu pratique | à déprioriser sauf micro-zone de maintenance technique fermée | challenger RFID/RTLS et checklists QR existants. |

Ces priorités sont ordinales et expérimentales (P0–P2), pas chiffrées. Elles ne constituent **aucune** indication de clients acquis ni de demandes solvables.

## Expériences d'achat et preuve d'usage avant code métier

1. **Entretien contextualisé** : demander un exemple réel de rapport passé avec photo attribuée au mauvais actif; calculer minutes reconstitution, personnes mobilisées, re-visites; poser « comment le résolvez-vous aujourd'hui ? ». Ne jamais démarrer par « voudriez-vous du SLAM ? ».
2. **Pilote sans VIO** : une version QR + photo + checklist opérant entièrement hors ligne, comparaison à Odoo/ERP/système existant, pour découvrir la valeur d'un registre de pièces.
3. **VIO activable** : même tâche avec segment ARCore, même repérage et nombre de photos; comparer taux de **fausse attribution**, temps d'installation, rapport, batterie et permission caméra.
4. **Masquage/abstention** : déclarer l'emplacement inconnu si non observable. Mesurer explicitement le nombre d'images acceptées à tort comme bonne pièce; un bon tracé moyen ne suffit pas.
5. **Revisite** : fermer/redémarrer, retourner le lendemain, QR/AprilTag de zone et photos du même équipement; compter faux appariements. Aucun gain revendiqué si cette étape échoue.
6. **Retour client** : deux prototypes en situation supervisée dans domaines distincts, seuils de valeur définis avant essai et décision d'arrêter si QR seul gagne.

## Critères UX de l'Atlas

- **Découvrir** : panorama en une vue, priorités, sources et règle « simple outil d'abord ».
- **Comparer** : choisir deux opportunités, visualiser problème, méthode minimale, avantage potentiel, obstacle et expérience, sans score marketing inventé.
- **Explorer** : recherche par mot, filtre priorité et domaine, ajout explicite au gestionnaire d'idées local existant.
- **Comprendre** : navigation entre Marchés / Produit / Recherche / Développement / Tous; tous les contenus historiques consultables et conservés.
- **Décider** : sorties go/no-go, états observé / hypothétique / non mesuré visibles.
- **Conserver** : pas de dépendance CDN, stockage local d'idées et export JSON préservés; responsive mobile, navigation clavier; un test navigateur sera ajouté séparément.

Sources consultées le 21/10/2026; les pages de fournisseur attestent une **description de leurs produits**, pas une vérification de leur qualité, de leur tarif ou de leur conformité. L'Atlas ne mesure aucun marché ni client.
