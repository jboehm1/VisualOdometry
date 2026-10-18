# Chaîne de preuve: ChatGPT → GitHub Actions → Google Drive

## Test réel déclenché le 18 octobre 2026

L'utilisateur a demandé une **preuve effective** que ChatGPT puisse lancer, contrôler et archiver des tests SLAM/PDR sur des datasets publics. Aucun simulateur IMU synthétique n'est utilisé dans cette campagne.

- Branche dédiée `feature/chat-live-dataset-proof-20261018`, issue du SHA `9662709a508064e011de3f34109a8a73d9374122` de `feature/slam-explorer`.
- Workflow exécuté : [GitHub Actions 39163878643](https://github.com/jboehm1/VisualOdometry/actions/runs/39163878643), **success**.
- Commit du test exécuté : `fb803717d2cb2f9313833ab069e6d143e8f278f0`.
- Programme nouveau `tools/campaigns/chat_real_proof.py` ; il appelle le runner commun `tools/campaigns/simul_real.py` sans le modifier.
- Dataset source officiel : SIMUL, dépôt `simpleLoc/SIMUL-dataset` au commit `ee7eb4e89bf33a17cafb4268207b802773427493`, vraies mesures capteurs.
- Moteur Java compilé : PdrEngine + StepPeakDetector de notre branche de développement PDR au commit **figé** `f8228aa7acbc453001d623c537bd8d7f0a9bc176`. Python baseline indépendant et séparé.
- **4 personnes distinctes** (08, 17, 24, 25), **trois emplacements** par personne (poche avant, main, poche arrière), **12 exécutions Java**, 41 942 instants IMU.
- 5 tests du bridge et 12 essais passés. La vérité terrain est constituée **d'intervalles de pas annotés** et n'est chargée qu'après l'inférence.
- Résultat pour la nouvelle personne 08, 251 pas annotés : poche avant **254** (+1,2 %, F1 0,970), main **219** (−12,7 %, F1 0,932), poche arrière **268** (+6,8 %, F1 0,898). Les 9 autres essais sont détaillés dans le rapport JSON.
- **Ce test valide le détecteur Java de pas**, pas la précision métrique de la trajectoire et **pas ARCore sur un vrai Galaxy S21**.

## Preuve du dépôt Google Drive

GitHub Actions a créé un artefact `chat-proven-real-java-pdr-report` (ID `12150126566`). Le connecteur GitHub l'a rendu accessible en référence de fichier ; le connecteur Google Drive a accepté cette référence et enregistré le ZIP dans `SLAM_BENCHMARKS/04_REPORTS/`.

- **Lien du fichier Drive lu à nouveau et vérifié** :
  https://drive.google.com/file/d/1TZzSd9GIuQnenq4A7TWamVw6iePRAXqh/view
- Nom : `CHAT_PROOF_20261018_SIMUL_4_participants_12_java_trials.zip`
- Taille archivée : **7 737 octets**.
- Contenu ZIP : `chat_proof_receipt.json` et `real_step_report.json`, **pas de dataset brut**.
- Transfert **ChatGPT → GitHub Actions → ChatGPT → Drive** : **réellement démontré**, après vérification du run réussi et lecture des métadonnées du fichier sur Drive.

## Limite de synchronisation Drive → GitHub

Ce test télécharge SIMUL depuis son dépôt public dans GitHub Actions, sans faire appel à Drive pour obtenir les données brutes.

La récupération **automatique** d'un dataset privé stocké sur le Drive de l'utilisateur n'est **pas encore démontrée**, car les identifiants OAuth fournis à ChatGPT ne sont pas accessibles aux runners GitHub. Le workflow de récupération privé `.github/workflows/drive-campaigns.yml` existe, mais doit recevoir une configuration `rclone` de moindre privilège dans le secret GitHub `SLAM_RCLONE_CONFIG_B64`. Celui-ci n'a pas été configuré ni vérifié depuis ce chat. Sans secret, GitHub Actions ne peut pas lire directement les dossiers privés Drive.

Une alternative est de lancer des benchmarks sur des URLs publiques épinglées par checksum, comme pour SIMUL, et d'utiliser Drive seulement pour archiver les rapports. L'objectif est de ne **pas** utiliser Drive comme stockage obligatoire de toutes les données publiques si elles peuvent être retéléchargées ponctuellement dans le cloud.

## Place approximative à réserver

Le **total exact des 230 fichiers `data/` de SIMUL sur GitHub au commit épinglé**, somme des tailles déclarées des blobs, est **2 568 578 090 octets (2,569 Go décimaux)**. Cette mesure inclut CSV et annotations ; elle n'est pas une mesure d'espace consommé sur le Drive utilisateur, puisque les sources ne sont pas conservées là-bas.

Pour une première campagne multimodale, conserver seulement les archives ou données brutes nécessaires (quelques séquences) et les rapports, sans archives + extraction redondante. Prévoir ~10–15 Go **libres** au départ est une estimation de capacité, pas le quota effectivement constaté du Drive utilisateur.

## Réutilisation

```bash
python tools/campaigns/chat_real_proof.py --out campaign_results/chat_proof
```

Le workflow `.github/workflows/chat-real-proof.yml` est isolé des workflows préexistants et se déclenche pour ses propres fichiers sur la branche dédiée ainsi que `feature/slam-explorer` après intégration. L'action `workflow_dispatch` peut ne pas être visible dans GitHub tant que le workflow n'existe pas sur la branche **par défaut `master`**, qui demeure le projet historique de 2019. Les déclenchements par push sur les branches enregistrées sont vérifiés.

## Étapes pour vraies mesures métriques

- OxIOD ou RoNIN : importer les axes et les horloges, adapter **la bonne référence indépendante**, mesurer la trajectoire et pas seulement les pas. Ne pas déclarer un résultat métrique avant d'avoir testé un moteur de trajectoire approprié.
- EuRoC ou TUM VI : installer un véritable estimateur OpenVINS / ORB-SLAM3, décoder les images, appliquer la calibration et les extrinsèques, puis score ATE/RPE. Une trajectoire VINS-Mono publiée est un test de l'évaluateur, pas du moteur Android.
- Galaxy S21 : enregistrer et rejouer le format officiel ARCore, mesurer des routes de référence, température, batterie, pause/reprise, orientation et vitesse.

**Fichiers de production Android modifiés par cette preuve : aucun.**
