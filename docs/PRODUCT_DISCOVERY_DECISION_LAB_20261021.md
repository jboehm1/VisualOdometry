# VECTOR VIO — étude de décision produit et plan de validation métier (21 octobre 2026)

> **Version recherche, sans marché ni résultats clients validés.** Cette note approfondit [le panorama initial](USE_CASE_OPPORTUNITY_LANDSCAPE_20261021.md) et [les niches sectorielles](OPPORTUNITY_DEEP_DIVE_20261021.md). Résumé interactif dans l'Atlas HTML §25. Sources externes en fin de document. Référence Git #48, dépôt `jboehm1/VisualOdometry`. Tout seuil ci-dessous est un **seuil de décision proposé**, pas un chiffre scientifique, contractuel ni une performance observée.

## 1. Pivot du positionnement : quelle information nouvelle peut réellement fournir le mouvement ?

Distinguer quatre niveaux non interchangeables :

| Niveau | Ce que nous connaissons | Qu'apporte VIO ? | Quand elle n'apporte **rien** |
|---|---|---|---|
| **Identité d'objet** | ID d'armoire, pince, module, pièce, rack | Ordre et continuité du trajet entre identités **si** la caméra reste exploitable | Le QR / code-barres donne déjà l'ID de manière souvent plus fiable |
| **Événement sur objet** | Photo, valeur mesurée, horodatage, opérateur consentant, preuve instrumentale | Contexte relatif et voisins probables; abstention quand ambigu | Une photo ne certifie pas couple, conformité ni qualité du geste |
| **Couverture de parcours** | Zones visitées avec états OBSERVÉ / INCERTAIN / NON DOCUMENTÉ | Continuité géométrique entre repères sous bon suivi | Traverser un couloir ne prouve pas qu'on a inspecté ses composants |
| **Repérage inter-visite** | Identifiant d'objet et référence physique réobservée | Aide à trouver/repositionner le POI après recalage | Un repère ARCore local n'est pas restauré automatiquement lors d'une nouvelle session; cloud anchor exige Internet |

L'**identité explicite de l'objet reste prioritaire**. La VIO peut proposer une localisation relative, mais doit se taire si elle ne sait pas départager des objets physiquement similaires.

### Trois stratégies MVP possibles, dont deux sans VIO

- **M0 — Baseline utile tout de suite** : liste de pièces/équipements + code stable (QR ou numéro) + photo/note/checklist + export local. Teste l'idée métier avec très peu de R&D. Compteur de « points vérifiés » seulement si vérification explicite, jamais inférée par simple passage.
- **M1 — VIO comme confort facultatif** : M0 + chemin relatif ARCore + badge de confiance + suggestion de zone uniquement + correction manuelle; ne jamais attribuer automatiquement une photo à une pièce sur seule distance estimée.
- **M2 — Re-localisation/inspection spatiale** : M1 + repères pérennes et calibration des poses/horloges + correspondance d'objets mesurée + revisite, snapshots locaux, éventuellement moteur Basalt lorsque ses performances le justifient. **M2 est un programme R&D, pas le MVP déjà existant**.

## 2. Ce que les vrais concurrents éliminent comme avantage

| Segment | Capacité concurrente vérifiée par fournisseur | Ce qu'il est incorrect de revendiquer | Hypothèse étroite subsistante |
|---|---|---|---|
| Construction avec plans | [OpenSpace Field](https://www.openspace.ai/products/field/) (sorti 2026) annonce localisation automatique de Field Notes en intérieur, collecte smartphone et Procore/Autodesk | « Première autolocalisation indoor d'une anomalie smartphone » | Outillage léger d'assemblage / composants particuliers, sans workflow de gros chantier |
| PV/O&M | [Raptor Maps Mobile](https://raptormaps.com/solar-tech-docs/mobile-app-overview) offre notes par équipement, carte, offline; [numéros de série et guide de marche](https://pages.raptormaps.com/raptor-maps-knowledge-hub/how-to-use-raptor-solar-to-support-module-serial-scanning) | « Premier scan PV avec géolocalisation/traçabilité/offline » | Montage mécanique sur toiture/carport et remontée d'une *nomenclature de fabrication*, éventuellement intégrée aux numéros pièce du fournisseur |
| Rondes | [LiteLog](https://litelog.de/en/solutions/guard-tour-system) et [MeridCore](https://meridcore.com/en/products/rounds/) : QR/NFC, horodatage, itinéraire et anomalies sans capteurs VIO | « Preuve de ronde par téléphone sans matériel » | Détection de segments omis **entre** checkpoints, sous réserve de métriques de faux positifs/temps |
| Indoor sans plan | [Navenio](https://navenio.com/our-technology/) décrit cartographie via parcours inertiel/radio et exploitation sans plans | « Premier suivi indoor sans carte au smartphone » | Fonction d'audit ponctuelle associant des événements à un objet contrôlé, pas suivre les employés |
| Recherche GNSS-denied | [iTHESEUS](https://imar-navigation.de/de/produkte-uebersicht/product-overview-by-product/item/itheseus-system-for-fully-autonomous-indoor-navigation) commercialise une solution inertielle dédiée; chiffres de précision/autonomie sont **déclarations fournisseur** | « Inertiel sans GPS est inédit » | Smartphone multi-moteurs comme instrument de diagnostic / benchmark, sans mission critique |
| Relocalisation/ancres | [ARCore Anchors](https://developers.google.com/ar/develop/anchors) et [Cloud Anchors](https://developers.google.com/ar/develop/cloud-anchors) : ancres locales liées à la session, partage cloud nécessite Internet | « Sauvegarder simplement un point local donne une carte réutilisable » | Repères fiduciaires QR/AprilTag pérennes et identité métier, mesurés après fermeture/redémarrage |

**Nuance** : les documents fabricants confirment les fonctions *annoncées*, pas leur performance ni leur tarification. Les informations commerciales changeantes doivent être revalidées lors des entretiens.

## 3. Six micro-niches réellement testables (sans prétendre qu'elles sont libres)

### A. Montage mécanique PV / nomenclature projet — première hypothèse à challenger

**Personne / moment** : conducteur de travaux, poseur ou SAV venant vérifier des éléments de montage répétés le long d'une façade, d'un carport, d'une clôture solaire ou d'un système intégré en toiture.

**Décision à prendre** : quel élément ou ensemble de visserie/clamp est documenté, et quelle réserve doit revenir à l'équipe de montage ? Une fiche peut contenir `project_id / variant / drawing_revision / assembly_id / component_id / inspector_event_id` ; éviter de prendre `estimate_number`/numéro de devis pour une identité pérenne.

**MVP M0** : importer une nomenclature et un plan local, scanner un ID d'assemblage/zone, photographier, signaler anomalie et exporter CSV/ZIP/PDF. Le simple numéro d'assemblage peut être une excellente solution sans VIO.

**Test différentiel M1** : sur un lot de pinces simulées *quasi identiques*, la trajectoire ARCore propose la zone/ordre des éléments; l'opérateur conserve la décision d'identité. Les reflets, le verre, la répétition et le travail en hauteur sont des risques ; **ne pas expérimenter sur un toit sans protocole de sécurité**.

**No-go** : 0 gain par rapport au QR/plan, fausse attribution, caméra rarement libre, coût de pose des repères, usage gênant avec gants. Aucune preuve de serrage ou tenue mécanique par photo.

### B. Documentation de réseaux avant fermeture — attention à la métrologie

**Personne / moment** : installateur électricien/CVC avant cloisonnement ou fermeture de faux plafond.

**Besoin** : retrouver *quel cliché* illustre *quelle cavité ou jonction* des semaines plus tard. Ici la VIO n'ajoute de valeur que si elle réduit l'effort de correspondance entre photos et éléments situés près les uns des autres.

**MVP** : repère de mur/élément de plan + photographie + annotation, éventuellement trajectoire relative sous un point de départ identifié. Ne jamais reconvertir une dérive VIO en « cote as-built » contractuelle.

**No-go** : documenter plus vite avec un plan PDF et 3 photos repérées; exigences RTK/mesures qui dépassent le smartphone.

### C. Inspection micro-site / bâtiment technique sans BIM

**Personne / moment** : technicien chargé de 10–40 actifs dans local peu documenté.

**Besoin** : un dossier de visite court, retrouvable par armoire/porte/zone, sans création préalable d'un jumeau numérique.

**MVP** : zones et objets, images et export lisible par tiers. **OpenSpace Field couvre déjà l'auto-positionnement de tâches** : on ne suppose pas absence de concurrence.

**No-go** : plan papier/QR remplit mieux les tâches ; contraintes de confidentialité ou caméra allumée.

### D. Local technique fortement contraint en confidentialité

**Contexte** : une entité refuserait un cloud public pour images de locaux; hypothèse à valider, non un marché identifié. « Offline » seul est commun.

**Test** : distinguer *capture locale*, *absence de télémétrie et d'API*, *permissions/identité*, *chiffrement en transit et repos*, *politique de rétention*, *effacement effectif*, *MDM*, et *export approuvé*. Ne pas qualifier le prototype de « sécurisé » ou « conforme » avant audit.

**No-go** : interdiction de photographier ou téléphone non autorisé; QR + formulaire sur terminal métier mieux adapté.

### E. Vérification de couverture par segments entre repères

**Contexte** : inspection/formation dans laquelle les seules lectures QR d'entrée/sortie ne renseignent pas les couloirs réellement empruntés.

**Test** : trajet réel contre plan chronométré avec interrupteur « caméra couverte » et fausses boucles; comparer faux « visité » et faux « non visité » à une checklist papier.

**Piège** : passage ≠ inspection, badge de couverture seulement comme **indication de mouvement**; jamais preuve réglementaire de contrôle.

### F. Banc de diagnostic VIO mobile / recherche et développeurs

**Acheteur hypothétique** : ingénieurs AR, robotique ou QA mobile ayant besoin de reproduire un décrochage VIO sur téléphone normal.

**MVP** : export unifié (`engine_id`, cadence d'entrée, horloge caméra↔IMU, exposition/ISO, couverture de pose, pertes/relocalisations, énergie, référence externe) + relecture identique; pas de promesse d'exactitude de Basalt. Pour replay vidéo équivalent, capture explicitement consentie des *frames brutes* nécessaire; ZIP de télémétrie seul ne suffit pas.

**Comparateur** : outils SDK, ROS, OpenVINS/Basalt host et scripts ATE; valeur = reproductibilité du même échec, pas seulement graphe attrayant.

## 4. Décisions éliminatoires avant tout scoring

Un classement marketing arbitraire peut masquer un **bloqueur matériel**. Évaluer les conditions dans cet ordre :

1. **Capture autorisée ?** Si pas de caméra/photo autorisée : abandon de tout concept VIO/image pour la tâche. Étudier seulement checklist/QR/IMU consentie.
2. **Caméra physiquement visible et utilisable ?** Mains occupées, téléphone en poche, reflets PV, arrière-plan uniforme : VIO optionnelle seulement, jamais cœur du protocole.
3. **Coordonnées absolues / cote certifiée exigées ?** Si oui, GNSS RTK, géomètre, instrumentation et système calibré; aucune promesse de remplacer cette mesure.
4. **ID de pièce déjà déterminé par QR ?** Tester d'abord QR seul. La VIO ne doit ajouter une complexité que si erreur/cout évité est mesuré.
5. **Besoin de revisite après redémarrage ?** Étape obligatoire de recalage physique et test inter-session; première visite n'est pas une référence mondiale.
6. **Besoin d'une décision de sécurité ?** Aucun guidage d'évacuation, pompier ou preuve de conformité avec l'app actuelle.

Ces portes ne sont pas des preuves de commercialisation; elles évitent surtout de développer un projet incapable de répondre au contexte.

## 5. Expérience comparative minimale : M0 / M1 / M2

**Protocole de faisabilité**, sans affirmer une qualification statistique :

- Choisir trois sites réels *autorisés* (atelier, installation PV à hauteur accessible, local technique) et définir 20 objets/POI par site; erreurs et identités réelles vérifiées indépendamment.
- Participants utilisateurs réels et consentants, leur demander de résoudre les mêmes opérations dans un ordre équilibré, alternant M0 QR+photo, M1 M0+ARCore+qualité, et, seulement après disponibilité technique, M2 avec recalage.
- Aucun entraînement/calibration de paramètres sur le lot tenu à l'écart; mesurer préparation, trajet, durée capture, annotation, correction, génération rapport, faux positifs et abstentions.
- Définir à l'avance une règle **critique** : zéro attribution *automatique non confirmée* sur pièce visuellement ambiguë, quel que soit un score ATE moyen. Mesurer les corrections demandées.
- Test **revisite à J+1** : fermer l'app, lumière différente, mêmes pièces; compter vrais/ faux matchs, interventions manuelles et situations « localisation inconnue ».
- Test **mauvais capteurs** : caméra couverte, faible texture et reflet, lumière insuffisante, mouvement rapide, perte IMU / GNSS et reprise; vérifier que la UI indique la limite au lieu de promettre une position.
- Évaluation **métier** : taux de bon objet, erreurs difficiles à détecter, minutes total jusqu'au rapport prêt, nombre de revisites, corrections, acceptation des permissions et batterie. Enregistrer les raisons d'abandon de l'app.

Critères GO/NO-GO **à fixer avec les métiers avant campagne** : tendance nette à réduction du coût d'une erreur ou du temps complet, sans augmentation de fausses associations; sinon vendre éventuellement M0 **sans** VIO. 20 cas et 2 personnes ne donnent pas une preuve suffisante de faible taux d'erreur pour un produit critique.

## 6. Structure de données recommandée (concept, non implémenté)

```text
Site(site_id, site_version, floor_or_zone_reference, consent_policy)
Asset(asset_id, site_id, drawing_revision, zone_id, explicit_qr_or_fiducial)
Run(run_id, phone_model, engine_id, calibration_id, started_utc, clock_quality,
    policy_and_consent, stopped_utc)
Segment(run_id, t0, t1, pose_frame_id, tracking_quality, valid_observation,
        reasons_for_abstention, alignment_reference)
Evidence(event_id, run_id, t, asset_id_confirmed_by_user,
         asset_id_suggested_nullable, photo_hash_nullable, measurement_source,
         note, actor_consent, location_confidence, export_provenance)
Review(action_id, event_id, reviewed_at, reviewer, decision, justification)
```

La preuve d'un **événement métier** est distincte de la vérité de position, et la précision / qualité de pose doit avoir une définition explicite (la densité de features n'est pas une covariance).

## 7. Séquence courte de discovery utile à un entrepreneur

Six questions prioritaires lors des entretiens (ne pas présenter une démo SLAM avant) :

1. Raconte ta dernière erreur de rattachement photo/équipement ou ton dernier retour inutile sur site.
2. Quel coût réel en temps, délai, sécurité, réputation ou facture ? Peux-tu montrer un exemple anonymisé ?
3. Qu'est-ce que l'équipe utilise aujourd'hui : PDF, QR, Odoo/ERP, tableau, outil chantier, GPS, appareil 360° ?
4. Qu'est-ce qui rend la saisie difficile : gants, mains prises, répétitivité des éléments, absence de plan, manque de réseau ?
5. Quelles données/photo ne peuvent pas sortir du site ou être prises, et quelles validations doivent rester humaines ?
6. Si la solution était parfaite techniquement, qui l'achèterait, la déploierait et la maintiendrait ? Pour quelle tâche précise ?

## 8. Règle de sélection : « faire d'abord ce qui peut échouer vite »

1. **Prochaine semaine produit (hypothèse)** : un formulaire M0 pilotable depuis plan CSV/QR/PDF, ne nécessitant ni VIO ni accès client.
2. **Étude différenciation** : un comparatif M1 sur 20 objets semblables et sur un trajet traversant plusieurs zones, avec refus d'assignation automatique en cas d'ambiguïté.
3. **R&D VIO** : calibration S21, benchmark ARCore/Basalt, faible luminosité et reflets, consommation; aucune fonctionnalité client dépendante de Basalt avant résultats indépendants.
4. **Décision** : la VIO reste optionnelle si le journal d'inspection M0 suffit. Explorer le banc de tests/diagnostic si aucun gain métier net n'apparaît.

### Sources primaires et prudence

- [OpenSpace Field — produit](https://www.openspace.ai/products/field/) et [release notes 2026](https://support.openspace.ai/hc/en-us/articles/41951884027667-Release-Notes).
- [Raptor Solar — notes sur équipement et offline](https://pages.raptormaps.com/raptor-maps-knowledge-hub/how-to-view-and-update-anomalies-in-the-raptor-solar-mobile-app) et [serial scanning / parcours](https://pages.raptormaps.com/raptor-maps-knowledge-hub/how-to-use-raptor-solar-to-support-module-serial-scanning).
- [Navenio — localisation sans plans par capteurs inertiels + radio](https://navenio.com/our-technology/).
- [LiteLog — rondes QR/NFC](https://litelog.de/en/solutions/guard-tour-system), [MeridCore — route/checkpoints](https://meridcore.com/en/products/rounds/).
- [Google ARCore — limite texture/éclairage/reflets](https://developers.google.com/ar/design/environment/definition); [types d'ancres et distance conseillée](https://developers.google.com/ar/develop/anchors); [Cloud Anchors exigent internet](https://developers.google.com/ar/develop/cloud-anchors).
- [NIST Z-Axis Test Facility (13 mai 2026)](https://www.nist.gov/news-events/news/2026/05/z-axis-test-facility-improving-indoor-localization-public-safety) : témoigne de la difficulté scientifique et de la nécessité d'une vérité indépendante multiétage; aucune performance VECTOR n'en découle.
- [iMAR iTHESEUS](https://imar-navigation.de/de/produkte-uebersicht/product-overview-by-product/item/itheseus-system-for-fully-autonomous-indoor-navigation) : existence de produits inertiels spécialisés, chiffres publiés par le fabricant non répliqués ici.

Toutes les prétentions commerciales/techniques de fournisseurs sont **attributions de sources**, pas essais A/B. Pas de chiffre de taille de marché, bénéfice utilisateur, volonté de payer, précision S21, autonomie ou certification inventé.
