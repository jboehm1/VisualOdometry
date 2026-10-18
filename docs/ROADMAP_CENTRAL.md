# VECTOR VIO — Feuille de route CENTRALE (tous chantiers)

> **Point d'entrée unique des développements actuels.** État arrêté le **19 octobre 2026** d'après les branches GitHub poussées. **Ni statut « 100 % global », ni qualification de sécurité ou de précision physique**. Une ligne « LOGICIEL_VALIDE » ne signifie pas « testé sur le S21 ».

## Quelle branche fait autorité ?

Le dépôt GitHub a encore pour branche par défaut `master`, **archive du projet original de 2019**. L'application Android courante se développe sur **`feature/slam-explorer`**, qui héberge ce document et sert de branche d'intégration. `docs/DEVELOPMENT_COORDINATION.md` décrit le protocole de fusion et `AGENTS.md` doit être lu avant de toucher aux fichiers partagés. Ne pas confondre « centralisé sur GitHub » avec « tous les modules déjà fusionnés ».

**Accès rapide :** [Atlas V5 visuel](VECTOR_VIO_ATLAS_INTERACTIF_v5.html) · [Backlog central JSON](ROADMAP_CENTRAL.json) · [Coordination Git](DEVELOPMENT_COORDINATION.md) · [B1–B8 : détail des 41 sous-tâches](https://github.com/jboehm1/VisualOdometry/blob/work/slam-roadmap-b1-b2-b8/docs/ROADMAP_EXECUTION.md) · [B1–B8 : JSON 41 tâches](https://github.com/jboehm1/VisualOdometry/blob/work/slam-roadmap-b1-b2-b8/docs/VECTOR_VIO_TASKS.json) · [Pilote GPS↔FLP](https://github.com/jboehm1/VisualOdometry/blob/feature/android-flp-comparison/docs/LOCATION_FL_PILOT.md).

## Priorités d'exécution (sans éluder les tests)

1. **P0 — Reproductibilité/socle :** test combiné sans effacer PDR, GPX, COURSES, replay ni le nouveau GPS↔FLP. Réconcilier les entrées DATA, activités du manifeste et exports sur une branche d'intégration dédiée ; CI puis smoke sur la **même APK**. Respecter l'invariant pose ≠ VIEW FPS.
2. **P0 — Baseline terrain GNSS :** collecter GPS direct vs Google FLP sur S21 (statique, course, forêt, canyon, tunnel), conserver les positions brutes et leur provenance. Les deux sources peuvent partager le GNSS ; les comparaisons entre elles n'établissent pas l'ATE.
3. **P0 — VIO/IMU :** évaluer erreur métrique avec référence indépendante, synchronisation de temps, pertes de tracking, segments réinitialisés et consommation. Identifier séparément moteurs ARCore, PDR Java, PDR Python, VIO externe.
4. **P1 — Valeur produit :** tirer une amélioration *démontrée* du matching GPX avec VIO/PDR seulement si les benchmarks le justifient ; conserver l'ambiguïté et l'abstention. Tests magasin/retour et segmentation hors ligne.
5. **Recherche hors critique — fumée dense / pompier :** scénario pertinent pour localiser des intervenants en bâtiments, mais **aucune fonction de navigation de sauvetage** ne doit être annoncée. GNSS souvent absent en intérieur, RGB/ARCore peu fiable dans fumée épaisse, PDR dérive en rampant/escalier, équipements soumis à chaleur. Étudier capteurs **externes** (thermique, UWB, radar, balises, pression), et commencer exclusivement par des protocoles contrôlés non dangereux.

## État par chantier et limites

| ID | Chantier / propriétaire | Priorité | État audité | Point bloquant |
|:--|:--|:--:|:--|:--|
| **CORE** | Application Android / exploration — `feature/slam-explorer` | P0 | Intégration partielle vérifiée | Précision/course/S21, régimes thermiques et comportement multi-modules non validés. |
| **B1B8** | GPS↔VIO, GPX, vision exploratoire, courses, retour — `work/slam-roadmap-b1-b2-b8` | P0 | Fonctions développées sur branche séparée, PR #3 en brouillon | Certains prototypes ne sont pas liés à la caméra/au service GPS ; code non intégré avec PDR et replay. |
| **PDR** | Sans caméra / inertiel seulement — `feature/camera-free-pdr` | P1 | Moteur Java/Android et replay sur branche séparée, PR #5 | PDR Python de campagne est différent du moteur Java ; pas de calibration course/rampement/fixation appareil. |
| **FLP** | A/B localisation Android GNSS natif vs Google FLP — `feature/android-flp-comparison` | P0 | Pilote Android implémenté et CI verte sur SHA fonctionnel, PR #11 ouverte | Les deux sources partagent potentiellement le GNSS; désaccord ≠ précision, mesures physiques et batterie manquantes. |
| **LAB** | Datasets, validation A/B/C et benchmark métrique — `feature/three-validation-campaigns + feature/slam-explorer + work/slam-roadmap-b1-b2-b8` | P0 | Infrastructure et campagnes CI présentes, couverture réelle incomplète | Dataset catalogué ≠ acquis/rejoué ; baseline PDR Python ≠ Java; EuRoC non joué directement par ARCore Android. |
| **SMOKE** | Fumée dense / pompier : recherche non sécuritaire — `R&D futur, aucun moteur pompier déclaré` | P2 (recherche) / P0 (interdire les promesses de sécurité) | Cas d'usage documenté ; aucun système qualifié ni essai réel | Erreur de position potentiellement mortelle ; visibilité nulle, repères modifiés, chaleur, communications rompues, étage ambigu. |

**Historique détaillé :** la feuille B1–B8 sur `work/slam-roadmap-b1-b2-b8` inventorie **41 sous-tâches** et annonçait une moyenne indicative de **68 % au 18/10/2026**. Cette moyenne ne reflète pas l'intégration avec les autres branches et n'est pas recalculée ici. Les seuils de sûreté, capacité à retrouver un pompier et précision GPS absolue sont **non validés**.

## Tâches inter-chantiers, preuves et critères d'acceptation

Les états signifient : `DOCUMENTE` = protocole/décision rédigé ; `PARTIEL` et `EN_COURS` = implémentation incomplète ; `LOGICIEL_VALIDE` = vérifié par test logiciel identifié, **pas terrain** ; `A_TESTER_TERRAIN` / `NON_FAIT` / `BLOQUE_*` = preuve manquante.

### CORE — Application Android / exploration

**Branche(s)** : `feature/slam-explorer` · **Constat** : Intégration partielle vérifiée. **Évidence** : Odomètre ARCore, HUD, carte, GPS ROUTES, capture et replay déjà présents sur branche intégration.

| Tâche | Priorité | Statut | Critère d'acceptation précis |
|---|---|---|---|
| **C01** · Préserver 30 poses/s visées indépendamment de VIEW 5/15/30 | P0 | `EN_COURS` | Revue invariants + métriques cadence en marche/course et vérité terrain. |
| **C02** · Unifier menu DATA sans collision d'indices PDR/replay/courses/FLP | P0 | `BLOQUE_INTEGRATION` | Tous les sous-écrans accessibles et actions correctes dans une seule APK. |
| **C03** · Tester l'APK réellement réunie et la compatibilité des exports | P0 | `NON_FAIT` | CI GitHub sur SHA intégré, smoke Android de chaque mode, ZIP valides. |

**Risque restant** : Précision/course/S21, régimes thermiques et comportement multi-modules non validés.


### B1B8 — GPS↔VIO, GPX, vision exploratoire, courses, retour

**Branche(s)** : `work/slam-roadmap-b1-b2-b8` · **Constat** : Fonctions développées sur branche séparée, PR #3 en brouillon. **Évidence** : 41 tâches sources détaillées ; progression indicative interne de 68 % au 18/10, non recalculée après nouvelles branches.

| Tâche | Priorité | Statut | Critère d'acceptation précis |
|---|---|---|---|
| **B01** · Garder les 41 sous-tâches B1…B8 comme backlog de référence détaillé | P1 | `DOCUMENTE` | Référence versionnée intacte ; aucune tâche masquée par le résumé inter-chantiers. |
| **B02** · Synchroniser GNSS/ARCore et vérifier drift/timebase sur S21 | P0 | `A_TESTER_TERRAIN` | Fixes appariés, resets documentés, pas de saut de segment et écart au contrôle indépendant. |
| **B03** · Raccordement conservateur GPX+VIO avec abstention | P1 | `PARTIEL` | Figure-huit, sentiers parallèles, horloge décalée, GPS multipath, incertitude visible. |
| **B04** · Évaluer chemin visuel sur images réelles avec masques annotés | P2 | `PROTOTYPE` | IoU/rappel, hors de l'échantillon d'entraînement, budget réel énergie/latence. |
| **B05** · Contrôler retour sans recoller les trous de suivi | P1 | `PARTIEL` | Retour interrompu explicitement en absence de repère commun ; aucune promesse secours. |
| **B06** · Tests magasin et confort d'édition des rayons | P2 | `PARTIEL` | Test pratique offline, corrections d'allées sans données privées involontaires. |

**Risque restant** : Certains prototypes ne sont pas liés à la caméra/au service GPS ; code non intégré avec PDR et replay.


### PDR — Sans caméra / inertiel seulement

**Branche(s)** : `feature/camera-free-pdr` · **Constat** : Moteur Java/Android et replay sur branche séparée, PR #5. **Évidence** : Moteur PDR Android, acquisition, écran, export ZIP et replay moteur commun; historique CI sur la branche.

| Tâche | Priorité | Statut | Critère d'acceptation précis |
|---|---|---|---|
| **P01** · Vérifier la cadence et le moteur exact Java sur téléphone | P1 | `A_TESTER_TERRAIN` | Préciser orientation corps/téléphone, port en poche, immobilité et marche. |
| **P02** · Quantifier la dérive en sortie de tunnel/indoors | P1 | `NON_FAIT` | Tests 3 parcours et dérives p50/p95 de position/longueur, référence indépendante. |
| **P03** · Étudier mouvements atypiques : escaliers, rampement, charge portée | P2 | `RECHERCHE` | Conserver abstention et échec explicite lorsqu'une marche à pas n'est pas reconnaissable. |

**Risque restant** : PDR Python de campagne est différent du moteur Java ; pas de calibration course/rampement/fixation appareil.


### FLP — A/B localisation Android GNSS natif vs Google FLP

**Branche(s)** : `feature/android-flp-comparison` · **Constat** : Pilote Android implémenté et CI verte sur SHA fonctionnel, PR #11 ouverte. **Évidence** : 129 tests Android, Lint/APK et campagnes existantes au SHA 04ad04e ; raw/paired CSV, analyse Python offline, ZIP local.

| Tâche | Priorité | Statut | Critère d'acceptation précis |
|---|---|---|---|
| **F01** · Collecter GNSS_PROVIDER et FLP en parallèle, sans modifier la navigation | P0 | `LOGICIEL_VALIDE` | Captures privées distinctes, horodatages, refus des fixes périmés ; CI du module. |
| **F02** · Test stationnaire extérieur 5 min sur S21 | P0 | `NON_FAIT` | Jitter, fraîcheur, couverture et latences des deux sources par même fenêtre. |
| **F03** · Parcours mesuré ouvert, forêt, canyon urbain et retour après tunnel | P0 | `NON_FAIT` | Erreur de position à référence indépendante + taux de réussite/absence; 3 répétitions. |
| **F04** · Comparer séparément la consommation GPS seul/FLP seul/simultanés | P1 | `NON_FAIT` | mAh/Wh ou puissance contrôlée, chauffe et fréquence des demandes documentées. |
| **F05** · Décider conserver GNSS, FLP ou double-source selon critères | P1 | `BLOQUE_TERRAIN` | Décision et seuils fondés sur courbes, pas sur un simple accord des sources. |

**Risque restant** : Les deux sources partagent potentiellement le GNSS; désaccord ≠ précision, mesures physiques et batterie manquantes.


### LAB — Datasets, validation A/B/C et benchmark métrique

**Branche(s)** : `feature/three-validation-campaigns + feature/slam-explorer + work/slam-roadmap-b1-b2-b8` · **Constat** : Infrastructure et campagnes CI présentes, couverture réelle incomplète. **Évidence** : Campagnes A/B/C et scripts, provenance normalisée V5 B1–B8; PR #14 de benchmark ADVIO-09 ouverte.

| Tâche | Priorité | Statut | Critère d'acceptation précis |
|---|---|---|---|
| **T01** · Vérifier dataset réellement téléchargé, licence et calibration | P0 | `EN_COURS` | Chemins locaux, hashes, formats, provenance, aucune donnée vérité terrain injectée. |
| **T02** · Mesurer ATE/RPE/couverture avec moteur et vérité indépendants | P0 | `PARTIEL` | Segment/timebase identifiés ; métriques à chaque échec; pas d'optimisation d'échelle cachée. |
| **T03** · Rejouer PDR Java réel, distinguer la baseline Python | P0 | `NON_FAIT` | Même séquence IMU, même version moteur, logs et mesures comparables explicitement. |
| **T04** · Tester vraie vidéo ARCore S21 avec le même moteur | P0 | `A_TESTER_TERRAIN` | Données capture réelles, horloges, CSV, reprise, EOF, export, erreurs propres. |

**Risque restant** : Dataset catalogué ≠ acquis/rejoué ; baseline PDR Python ≠ Java; EuRoC non joué directement par ARCore Android.


### SMOKE — Fumée dense / pompier : recherche non sécuritaire

**Branche(s)** : `R&D futur, aucun moteur pompier déclaré` · **Constat** : Cas d'usage documenté ; aucun système qualifié ni essai réel. **Évidence** : Références NIST sur localisation secours en fumée, GNSS non fiable indoor et capteurs complémentaires.

| Tâche | Priorité | Statut | Critère d'acceptation précis |
|---|---|---|---|
| **S01** · Définir modes de défaillance fumée dense, obscurité et environnement mouvant | P0 | `DOCUMENTE` | Matrice perte GNSS/RGB/PDR, escaliers, portes fermées, rampement, gants et chaleur. |
| **S02** · Protocole laboratoire : fumée artificielle NON dangereuse, sans flamme et supervision | P1 | `NON_FAIT` | Accord site et opérateurs, protocole de sécurité, parcours de contrôle indépendant. |
| **S03** · Bench 3D inertiel porté sur équipements, rampement/escaliers | P2 | `NON_FAIT` | Erreur par étage, temps depuis dernier recalage, abandon si erreur inconnue. |
| **S04** · Étude matériels externes IR thermique, UWB/balises, radar et barométrie | P2 | `RECHERCHE` | Compatibilité, coûts, essais spécifiques; aucun support n'est supposé intégré au Galaxy S21. |
| **S05** · Détection de l'échec sûre : abstention, incertitude, capteurs morts, lien rompu | P0 | `NON_FAIT` | Interface indique POSITION INCONNUE et interdit tout guidage présenté comme fiable. |
| **S06** · Validation indépendante et revue spécialistes secours avant tout usage d'intervention | P0 | `BLOQUE_VALIDATION` | Essais comparatifs avec professionnels/organisme qualifié ; pas de revendication secours à court terme. |

**Risque restant** : Erreur de position potentiellement mortelle ; visibilité nulle, repères modifiés, chaleur, communications rompues, étage ambigu.



## Volet spécifique pompier / fumée intense (R&D uniquement)

**Cas concret** : retrouver un intervenant à l'intérieur d'un bâtiment enfumé sans GPS ni image RGB exploitable, y compris lorsqu'il progresse accroupi, rampe, grimpe un escalier ou déplace un tuyau. La position initiale à la porte n'est pas une solution : quelques degrés d'erreur d'orientation créent une grande dérive après quelques dizaines de mètres ; un compteur de pas peut ne détecter aucun « pas » lors du rampement. Un bâtiment peut comporter plusieurs étages, des obstacles/murs qui changent, du bruit radio et une température destructrice pour un smartphone classique.

**Noyau du futur benchmark** : position XY **et étage**, erreur p50/p95 vs référence indépendante, pourcentage temps sans localisation, latence de perte et reprise, temps depuis la dernière référence, batterie/thermique, portage des EPI, latence et perte de liaison (si communication ajoutée), statut `POSITION INCONNUE` dès insuffisance de preuve. Les cartes préexistantes peuvent être obsolètes. Un tracé récent n'est pas une garantie de passage libre.

**Recherche matériel (non présent aujourd'hui)** : thermique (nécessite un capteur externe, saturations et visibilité IR non garanties), radar/mmWave, UWB/ancres ou compagnons, inertiel portable, pression barométrique pour transitions d'étage, redondance des communications. **Le Galaxy S21 ne devient pas un équipement pompier certifié avec un logiciel.**

**Sécurité incontournable** : aucun test en feu/fumée toxique ni mission réelle pour ce prototype, uniquement bancs et installations d'entraînement encadrées ; le prototype ne doit pas guider une évacuation réelle ni se substituer aux procédures, aux équipements et à la coordination opérationnelle. Sources de contexte : [NIST, localisation des intervenants](https://www.nist.gov/ctl/pscr/pervasive-accurate-and-reliable-lbs-emergency-responders) · [NIST, techniques UWB/inertielles](https://www.nist.gov/ctl/pscr/infrastructure-free-localization-system-firefighters) · [NIST 2026, tests FRST](https://www.nist.gov/blogs/taking-measure/how-thousands-tiny-dots-can-save-first-responders-lives).

## Définition d'une clôture et protocole de coordination

- **Logiciel** : code commité, CI Gradle+JUnit/Python+lint, export vérifié, APK disponible au SHA testé, puis contrôle d'intégration de tous les modules.
- **Terrain non critique** : au moins trois essais par scénario sur S21, vérité terrain indépendante, erreurs de position et distance avec pourcentages de couverture, taux d'abstention, énergie et thermique. Rapporter aussi les pannes.
- **Infrastructure future fumée/pompier** : une revue de risques et une campagne indépendante avec professionnels seraient un préalable très éloigné ; aucune valeur actuelle ne satisfait une qualification pour opérations.
- **Fusion Git** : lire `AGENTS.md` et `DEVELOPMENT_COORDINATION.md`, relever à nouveau les têtes, préserver les interfaces et actions des autres chats, documenter les SHA, vérifier *l'APK finale*, ne pas faire « ours/theirs » sur un fichier partagé.
- **Mise à jour** : le responsable de chaque chantier complète d'abord sa documentation et publie ses preuves ; le responsable d'intégration met ensuite à jour **ce document** et `ROADMAP_CENTRAL.json`. Les chiffres des branches non fusionnées doivent rester marqués « historique » pour empêcher les divergences.

## Références de branches et source technique de l'instantané

| Source | Référence consultée |
|---|---|
| Intégration Android actuelle | `feature/slam-explorer` — `5f0105effd4fb2fb66b7d23620189c9605576de7` |
| GPS+VIO B1–B8 | `work/slam-roadmap-b1-b2-b8` — `51885d3747721f176c14083ccb391c0719691bac` (PR #3) |
| PDR Android | `feature/camera-free-pdr` — `a98b8ccf8a1a63aab8b233b2aed2f59dfd509a80` (PR #5) |
| GPS↔FLP A/B | `feature/android-flp-comparison` — `ad48fdbdbf2dc789ca202a51edbdff5651fd7f4b` (PR #11) |
| Campagnes A/B/C | `feature/three-validation-campaigns` — `9dbe7a26d16b57248b9f07fb03549e28ebc096a0` |
| Historique GitHub | `master` — `d9e4a8b4fe6b3d6caa32d435e00d66b8fe5976c7`, archive 2019 |

> **Vigilance** : les SHA ci-dessus sont un instantané daté. Les autres chats peuvent pousser après cet audit. Une nouvelle fusion doit consulter à nouveau les branches, et une CI verte sur une branche ne prouve pas la validité de l'application combinée.
