# Preuve de tests réels déclenchés depuis ChatGPT — 2026-10-18

- Dépôt : `jboehm1/VisualOdometry`.
- Base relue : `feature/slam-explorer` commit `9662709a508064e011de3f34109a8a73d9374122`.
- Coordination lue : `AGENTS.md` et `docs/DEVELOPMENT_COORDINATION.md` sur cette base. La coordination est un instantané, non un verrou.
- Branche isolée : `feature/chat-live-dataset-proof-20261018`.
- Périmètre : faire démarrer dans le cloud, depuis cette conversation, une campagne réelle Java PDR et Python baseline, puis transférer uniquement le rapport vers Google Drive et vérifier l'identifiant du fichier Drive.
- Modifications prévues : nouveaux `tools/campaigns/chat_real_proof.py`, `.github/workflows/chat-real-proof.yml` et **ce fichier**. Aucun changement des moteurs Java, des algorithmes historiques, de `MainActivity.java`, du menu DATA, du manifeste, de la calibration ou des workflows existants.
- Fichiers partagés concernés : aucun chemin de production ou workflow existant. Le script appelle `tools/campaigns/simul_real.py` **en lecture** sans le modifier et compile les fichiers Java figés depuis la branche PDR parallèle. Les sorties appartiennent au nouveau workflow uniquement.
- Règles : ne pas confondre détecteur Java `PdrEngine` et baseline Python ; annotations de pas lues après estimation ; aucune vérité terrain envoyée au détecteur ; F1 de comptage ≠ précision métrique de l'odomètre, et aucun simulateur Linux n'exécute réellement ARCore du S21.
- Nouveau participant réellement enregistré : SIMUL personne 8, `data/by-person/8/awindaRecording_20230424133253.csv` (11 902 263 octets GitHub), sur trois positions. Rejouer aussi personnes 17, 24 et 25. Source externe figée par SHA `ee7eb4e89bf33a17cafb4268207b802773427493`.
- Prochaine étape : observer jobs et artefact du nouveau workflow au SHA effectif, vérifier les mesures et SHA des sources, puis utiliser le connecteur Drive pour copier le ZIP **du rapport uniquement** dans `SLAM_BENCHMARKS/04_REPORTS`. Le flux inverse Drive → runner GitHub exige toujours une autorisation explicite et ne sera pas prétendu fonctionnel sans test.
- Déclenchement et validation : aucune fusion automatique avec les branches `feature/camera-free-pdr` ou `work/slam-roadmap-b1-b2-b8`. Garder ce chantier isolé, revalider le HEAD avant PR.
