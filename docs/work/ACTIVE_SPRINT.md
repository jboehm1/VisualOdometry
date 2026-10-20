# VECTOR VIO — checkpoint opérationnel (Work, reprise déterministe)

> Snapshot **2026-10-21**. Ce fichier est un checkpoint vérifiable, **pas** un
> verrou Git ni une tâche persistante. À chaque reprise, contrôler Git/CI live
> avant d'exécuter une commande. Ne pas remplacer le travail d'autres branches.

```yaml
project: VECTOR_VIO
goal: commercial_quality_GPX_PDR_parallel_ARCore_native_VIO
integration_branch: feature/slam-explorer
integration_sha_inspected: 3d251b8e06536a972b88bcf01bd34912ea5ceabd
working_branch: feature/v015-commercial-nativevio-contracts
working_sha_inspected_before_checkpoint: c19ce295e637e315b85228e2cc92b73490f4f1a0
active_pr: 43
pr_stack: [38, 39, 40, 41, 42, 43]
work_started_from: 7f8da2396e01d930b3c762bf53c04600f2d5956f
last_confirmed_native_contract_ci:
  sha: d97f50baeea154add055dfb634b9bc37fcd06ea4
  run: 39516730933
  conclusion: success
last_confirmed_dataset_ci:
  sha: d97f50baeea154add055dfb634b9bc37fcd06ea4
  run: 39516731174
  conclusion: success
android_at_new_head: CHECK_GITHUB_ACTIONS_EXACT_SHA
external_ground_truth_s21: NOT_COLLECTED
native_vio_metric_estimator: NOT_IMPLEMENTED
java_pdr_field_accuracy: NOT_MEASURED
gpx_anchor_independent_accuracy: NOT_MEASURED
commercial_release_ready: false
```

## Cycle d'amélioration (reprise sans demander la même confirmation)

1. `git fetch origin '+refs/heads/*:refs/remotes/origin/*'`, inspecter
   `feature/slam-explorer`, les PR et les changements concurrents :
   `AGENTS.md`, `docs/DEVELOPMENT_COORDINATION.md` d'abord.
2. Vérifier l'Android CI, les campagnes et l'historique sur le **SHA courant**.
   Si un test échoue, collecter le **log réel** avant d'ajouter une feature.
3. Sélectionner une priorité d'échec vérifiable, écrire une hypothèse précise,
   choisir un dataset autorisé **avec modalité et vérité adaptées**, mesurer
   la baseline au SHA figé.
4. Modifier **le minimum de fichiers partagés**, construire, lancer tests
   anti-régression, ne jamais injecter la vérité dans l'estimateur.
5. Rejouer exactement le même protocole sur les mêmes octets et mesures,
   consigner le gain/perte, CPU, RAM, énergie, latence, p95, taux LOST et
   stabilité GPX; rejeter optimisation de benchmark sur split test.
6. Pousser une PR isolée, CI au SHA exact; rafraîchir ce checkpoint, la roadmap,
   la doc technique, les schémas Atlas HTML et le handover.
7. Reprendre le cycle tant que les ressources et les outils Work le permettent.
   En cas de limite, enregistrer l'état et la commande suivante, **sans
   inventer du travail en arrière-plan ou un score de 100 %**.

## Backlog immédiat et critères objectifs

| ID | État | Critère d'acceptation | Prochaine mesure |
|---|---|---|---|
| D01 | **Implémenté + tests CI** | Bloquer toute dérive du contenu local, configs et code | Vérifier `repro_manifest.py verify` sur un corpus réel à SHA fixé |
| D02 | **CI synthétique passée** | Réutiliser Java PdrEngine exact ET baseline Python sans GT | Faire un rapport SIMUL vrai humain, puis OxIOD |
| D03 | **NON FAIT** | Comparaison PDR sujets tenus à l'écart | Obtenir sources OxIOD/RoNIN, termes et vérité exploitable |
| V01 | **Contrats seulement** | Basalt/Kimera sur vrai caméra/IMU calibrés | Compiler moteur BSD pin, mesure EuRoC/TUM-VI |
| V02 | **NON FAIT** | Caméra2+IMU vrais et JNI avec pose métrique | Sensor timestamp matching+calibration test sur S21 |
| G01 | **NON FAIT** | GPX ancré avec référence RTK/survey, p95 et branches correctes | Trajet en ville/forêt, tunnel et GPS multipath |
| Q01 | **NON FAIT** | A/B ARCore vs natif sur S21 (répétitions contrôlées) | Captures comparables, mesure énergie/CPU/FPS |
| R01 | **BLOQUÉ** | Revue licences/notice/SBOM & protection données, stabilité signature | Cartographie réelle du binaire de release |

**Commande suivante recommandée**, après contrôle Git live :

```bash
python tools/datasets/repro_manifest.py lock \
  --suite tools/testenv/suites/deterministic-pdr-smoke.json \
  --receipt /tmp/vector-pdr-smoke-lock.json
python tools/datasets/repro_manifest.py verify \
  --suite tools/testenv/suites/deterministic-pdr-smoke.json \
  --receipt /tmp/vector-pdr-smoke-lock.json
python testenv.py run \
  --suite tools/testenv/suites/deterministic-pdr-smoke.json \
  --out campaign_results/pdr-smoke --resume
```

Pour vraie campagne, voir `docs/DATASET_REPRODUCIBILITY_V016.md`. La
commande ci-dessus est **seulement synthétique** et ne mesure pas la précision.

**Fichiers partagés modifiés par cette branche** : README, Architecture,
Roadmap Central, Atlas HTML et documentation nouvelle. Aucun fichier
`MainActivity.java`, manifeste, moteur Java PDR, GPX, réglages Android ni
moteur ARCore modifié dans ces ajouts. Les descendants PR #43 dépendent de
PR #42 et de ses ancêtres : pas de merge aveugle.

**Important pour le successeur :** le commit mentionné comme inspecté
est volontairement l'état avant écriture de ce fichier ; il faut vérifier le
HEAD réel. Voir `docs/HANDOVER_WORK_AUTONOMOUS_V016.md` pour objectifs,
risques, métriques, boucle et limites de commercialisation.
