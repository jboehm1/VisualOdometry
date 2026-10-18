# Unified benchmark CLI coordination

- Date: 2026-10-18.
- Integration base: feature/slam-explorer at 4bd4b0c17f80756f2a7c2296887c33e966ace88a.
- Reviewed AGENTS.md and docs/DEVELOPMENT_COORDINATION.md on that commit.
- Isolated branch: feature/unified-benchmark-cli.
- Scope: dataset/engine compatibility registry, unified execution, result provenance, honest test-capability levels, and CI validation.
- New files: tools/campaigns/unified.py, tools/campaigns/tests/test_unified.py, .github/workflows/unified-benchmark.yml, docs/UNIFIED_BENCHMARK.md and this note.
- Shared files modified: NONE. Existing benchmark scripts and Android/Java PDR engine are only read/invoked. No edits to MainActivity.java, AndroidManifest.xml, DATA menu, old workflows, or parallel branch code.
- Constraints: Java/Python PDR must remain separate; SIMUL step-count results are not metric displacement. Published EuRoC VINS-Mono trajectories validate the evaluator, not Android ARCore. Ground truth cannot be provided to the estimator; sensor calibration, reference quality and time/frame matching must be explicit. Existing coordination snapshots are not locks. Verify PR combined CI before merge.
- Next step: implement CLI, unit tests, public-real-data jobs; document exact commit/test evidence; merge only after checking the live integration head.

## Réalisation et preuve au 18 octobre 2026

- Code CLI: `benchmark.py`, `tools/campaigns/unified.py`. Catalogue de compatibilité strict, plan et exécution; alias `java-pdr` → `android-pdr`.
- Unit tests: `tools/campaigns/tests/test_unified.py` et `test_unified_execution.py`. Les 23 tests du répertoire campagnes ont réussi en CI.
- Nouveau workflow isolé `.github/workflows/unified-benchmark.yml` : **SUCCESS** au SHA exact `c40a01fd1577fcca5bde629391d4f16d9bf046d2`, [run 39165806180](https://github.com/jboehm1/VisualOdometry/actions/runs/39165806180).
- Exécutions réellement observées: Java **PdrEngine** du chantier parallèle compilé, Python PDR sur fixture, 12 essais Java sur 4 personnes SIMUL avec annotations indépendantes; évaluation de vraies trajectoires publiées VINS-Mono/EuRoC avec drapeau explicite `actual_estimator_executed=false`.
- Trois artefacts de petits rapports JSON générés et consultables sur la page du run; pas de capteurs bruts dans les artefacts.
- Les combinaisons `euroc + android-arcore` et datasets sans adaptateur sont expressément refusées. OxIOD/canonical peuvent faire tourner Java/Python sur un fichier fourni, mais la qualité métrique n'est pas établie sans référence indépendante et calibration.
- **Aucun fichier partagé Android, menu, manifeste, moteur PDR Java, workflow existant ou catalogue existant n'a été modifié.** Seuls les nouveaux chemins listés dans la présente note ont changé, plus le lanceur neuf à la racine `benchmark.py`.
- Dernière étape: PR contre le nouveau HEAD d'intégration, CI sur le commit combiné, squash merge si propre; ne pas toucher aux branches concurrentes.
