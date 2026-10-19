# Coordination — étude course, vélo, montagne et LOW LIGHT

- **SHA d'intégration consulté** : `98cba16e45de162434c1b09d408e80b431524a93` sur `feature/slam-explorer`, le 19 octobre 2026.
- **Règles lues** : `AGENTS.md` et `docs/DEVELOPMENT_COORDINATION.md`. Travail sur branche isolée, pas de réécriture de modules d'autres chats, pas de promesse de précision ou de sécurité sans vérité indépendante, CI au SHA combiné avant fusion.
- **Branche de travail** : `docs/precision-usecases-lowlight-20261019`, basée exactement sur ce SHA.
- **Périmètre** : recherche documentaire sourcée et ajout au catalogue central des réflexions : trail, vélo/VTT, faible lumière, forêt, montagne, canyon urbain, sentiers parallèles, précision relative versus absolue.
- **Fichiers partagés modifiés** : `docs/ROADMAP_CENTRAL.md`, `docs/ROADMAP_CENTRAL.json` ; **nouveau** `docs/USE_CASES_PRECISION_LOW_LIGHT.md` et le présent fichier. **Aucun** changement à `MainActivity.java`, `AndroidManifest.xml`, workflows, moteur GPS, VIO, PDR ou benchmark.
- **Autres branches** : PR #3 B1–B8, #5 PDR et #17 fumée poursuivent leur développement. La présente PR ne modifie ni leur code ni leurs fichiers de coordination.
- **Prochaine étape** : revue du document et validation des liens/JSON par CI ; fusion documentaire uniquement après contrôle. Pour une phase de développement ultérieure, faire L01 instrumentation des échecs de suivi, L02 terrain, L03 comparaison A/B avec référence indépendante.
- **Limites** : aucune nouvelle expérience physique, pas de preuve que VECTOR VIO atteint 0,10 m, pas de validation faible lumière sur le Galaxy S21, aucun mode vélo implémenté ici.
