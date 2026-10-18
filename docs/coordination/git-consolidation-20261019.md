# Audit GitHub et consolidation des développements — 19 octobre 2026

**Instantané contrôlé après revue de `feature/slam-explorer:AGENTS.md` (blob `9e56be8...`) et `docs/DEVELOPMENT_COORDINATION.md` (blob `61b55fc...`).** Les deux documents définissent un contrat : branches isolées, préserver les actions DATA et manifestes de chaque module, distinguer CI/terrain, revalider la combinaison réelle avant chaque fusion.

## Quelle branche est la base Android ?

- `master` : **archives 2019**, son README pointe vers l'application moderne ; ne pas y merger directement les fonctions Android.
- `feature/slam-explorer` : **branche centrale d'intégration Android**, SHA **`9ff7d3ae34019c759ca427c1f76a8e017039a4bc`** au début du présent audit (la fusion de #11).
- `docs/branch-consolidation-20261019` : branche courte **de documentation et contrôle**, créée depuis ce SHA. Aucune modification de `MainActivity.java`, `AndroidManifest.xml`, build Gradle, workflows existants, GPS, PDR ou ARCore.

Les outils GitHub ont retourné **20 branches** (dont plusieurs anciennes déjà intégrées), et **17 PR numérotées** : à l'issue des opérations vérifiées, **11 fusionnées, 3 fermées sans fusion, 3 encore ouvertes**. « Branche restante » n'implique pas « PR à merger ».

## Actions déjà effectuées pendant cette consolidation

| PR | Action | Pourquoi / garantie |
|---|---|---|
| **#1** · Android VIO original | **Fermée, non mergée** | Ancien prototype sur `feature/android-vio-odometer` intégralement ancêtre de `feature/slam-explorer` : 0 commit d'avance, 103 commits en retard lors du contrôle. La PR visait la branche historique `master`. Code déjà conservé. |
| **#2** · Spatial Explorer original | **Fermée, non mergée** | PR dans le mauvais sens : sa tête est `feature/slam-explorer` même, base = ancienne branche Android. Les deux côtés « tête/intégration actuelle » sont identiques, aucun apport à fusionner. |
| **#11** · GNSS natif vs Google FLP | **Fusionnée dans `feature/slam-explorer`** | Un mode A/B autonome opt-in, CSV/ZIP et 129 tests Android, sans rendre le FLP autoritaire. Branche head **`ad48fdbdbf2dc789ca202a51edbdff5651fd7f4b`**, CI branch réussies **39171989618** (Android) et **39171989629** (campagnes). Commit de merge **`9ff7d3ae34019c759ca427c1f76a8e017039a4bc`**. Attention : test combiné au SHA de merge **distinct** des CI de la branche #11 ; cette validation doit être reproduite par PR courte basée sur l'intégration actualisée. |

**PR précédemment déjà intégrées** dans `feature/slam-explorer` : #4 (benchmark), #6 (campagnes), #7/#9 (preuves PDR Java), #10 (CLI benchmark), #12/#14 (VIO/ADVIO), #15 (Atlas et feuille de route centrale), #16 (test env modulaire). #8 a été fusionnée **dans la branche de travail B1–B8** et non directement dans l'intégration. #13 a été fermée sans fusion après reprise dans #14.

## PR qui restent actives — ne pas les merger aveuglément

| PR | Branche / état au dernier relevé | Pourquoi ne pas fusionner automatiquement | Étape sûre suivante |
|---|---|---|---|
| [#3 · B1–B8](https://github.com/jboehm1/VisualOdometry/pull/3) | `work/slam-roadmap-b1-b2-b8`, SHA `51885d3747721f176c14083ccb391c0719691bac` ; **47 fichiers, 78 commits côté PR, conflit Git** | `MainActivity.java`, `AndroidManifest.xml`, `GpsNavigator`, GPS/GPX, `README.md`, workflow Android ; collisions avec PDR et FLP. Les 41 tâches documentées sur B1–B8 restent le détail historique. | Réduire les modifications partagées en sous-lots (outils/tests géo, store, vision ; puis adaptation UI), préserver les nouveaux modes, CI d'intégration à chaque lot. |
| [#5 · PDR sans caméra](https://github.com/jboehm1/VisualOdometry/pull/5) | `feature/camera-free-pdr` ; **~56 fichiers**, PR brouillon, **conflit Git** ; le SHA change avec les autres chats | Même indice DATA réutilisé par replay/courses, manifeste PdrActivity, scripts PDR et workflows. Des modèles IA simulés ne prouvent pas la précision du mouvement réel. | Préparer une intégration PDR depuis sa dernière tête **stable**, fusionner menu+manifeste par additions explicites, tester l'APK avec GPS FLP avant de toucher à B1–B8. |
| [#17 · fumée/radar/thermique — R&D](https://github.com/jboehm1/VisualOdometry/pull/17) | `feature/smoke-rescue-research`, **branche encore activement modifiée pendant l'audit** (plusieurs nouveaux SHAs observés), PR brouillon | Étude et scripts radar/UPO/thermal, datasets importants ; ses derniers commits touchent aussi des scripts/workflows ADVIO/benchmarks partagés. Plusieurs CI simultanées, dont une campagne UPO volumineuse, ont encore des états à vérifier. Aucune preuve d'usage en fumée réelle. | Le propriétaire stabilise la branche, CI au **dernier** SHA, revue licences/données/performance et vérification que l'Android principal n'a pas été modifié ; ensuite **fusion de la R&D isolée uniquement**, sans promesse de secours. |

**Une PR « mergeable » techniquement ne signifie pas fonctionnellement validée.** En particulier, ne pas intégrer une tête mouvante sans vérifier les résultats des workflows au **SHA exact**. Les autres chats peuvent pousser des commits après la présente photographie.

## Conflits et politique de résolution

Les PR #3, #5 et l'ancienne #11 modifient toutes `app/src/main/java/dev/jboehm/vectorvio/MainActivity.java`. PR #3 et #5 modifient aussi le manifeste et workflow Android ; #11 a déjà ajouté le menu diagnostique FLP dans l'intégration. Une fusion prenant tout un fichier « ours » ou « theirs » supprimerait des fonctionnalités.

Ordre pragmatique : **(1)** garder la baseline centrale + FLP comme base ; **(2)** PDR Java stable, en renommant les actions DATA par identifiants sémantiques ; **(3)** outils/testeurs/diagnostics B1–B8 indépendants ; **(4)** GPX/VIO et magasin avec manifeste complet et distinct ; **(5)** radar/fumée dans R&D hors chemin de navigation ; **(6)** CI + tests de menus/expor ts sur l'APK unifiée. Réserver `master` à l'ancien projet ; ne pas supprimer d'ancienne branche pendant des développements actifs.

**Contrat de qualification :** une CI verte sur `feature/slam-explorer` démontre que le code compilé et ses tests passent. Elle ne confirme ni position S21, ni précision GNSS absolue, ni suivi en fumée, ni autonomie réelle. Le Python PDR de campagne ne se substitue pas au moteur PDR Java Android.

## Pour retrouver tous les chantiers depuis GitHub

- [Feuille de route centrale](../ROADMAP_CENTRAL.md) et [JSON](../ROADMAP_CENTRAL.json)
- [Atlas V5](../VECTOR_VIO_ATLAS_INTERACTIF_v5.html)
- [Coordination commune](../DEVELOPMENT_COORDINATION.md)
- [Anciennes 41 tâches B1–B8](https://github.com/jboehm1/VisualOdometry/blob/work/slam-roadmap-b1-b2-b8/docs/ROADMAP_EXECUTION.md)

**Prochaine décision à documenter :** derniers SHA PDR/B1–B8 et CI sur une combinaison réelle de `MainActivity`, manifeste et dépendances. Aucune fermeture de PR active #3/#5/#17 ne doit être utilisée pour cacher un conflit.


## Événement Git survenu pendant l'audit

Le **19 octobre 2026 (heure locale de Berlin)**, un autre agent a également fusionné **PR #18 — V3 real smartphone suites / modular testenv**, commit de merge `a9683c47452c00f48c68527dc8d92f6fd3c00d82` dans `feature/slam-explorer`. Ce travail apporte des scénarios dataset **ADVIO-09 / Mobile-GVIO** sur le pipeline de test, sans changer les nouveaux modules PDR/courses/FLP. L'état **17 PR / 11 fusionnées / 3 closes / 3 ouvertes** plus haut est donc **l'instantané pris AVANT** cette arrivée ; #18 est une douzième PR fusionnée, et la présente PR documentaire #19 est transitoirement ouverte puis à clôturer par fusion. **Ne pas compter #18 comme encore à merger.**

En présence d'autres chats qui poussent en direct, **relever le SHA central à chaque fusion** : un bilan archivé à un instant donné ne reste pas automatiquement à jour. Les données vérifiables sont les dates/SHA GitHub, pas le compteur seul.


## Contrôle précoce ajouté pour sécuriser les prochains merges

`tools/coordination/android_menu_contract.py` inventorie en lecture seule les **cinq actions de menu** attendues et les activités non exportées du manifeste : GPS ROUTES, ARCORE REPLAY, FLP A/B, PDR et COURSES. `tools/tests/test_android_menu_contract.py` impose en CI le **socle actuel** (GPS, replay, FLP), et accepte l'arrivée future de PDR/COURSES sans casser les tests. Un scénario synthétique distinct vérifie qu'une configuration qui omet PDR/COURSES échoue si ces modes sont explicitement exigés.

Usage après intégration finale (représentation statique, pas un test de clic réel) :

```bash
python3 tools/coordination/android_menu_contract.py
# Une fois PDR et COURSES intégrés :
python3 tools/coordination/android_menu_contract.py --require GPS_ROUTES ARCORE_REPLAY FLP_AB PDR COURSES
```

**Critère de fusion PDR / B1–B8** : tous les cinq modes détectés avec enregistrement manifeste valide **ET** CI/JUnit/rendu réel des cinq actions après la fusion. Une simple occurrence de texte dans le code ne garantit ni l'ordre des choix ni la validité des écrans ; l'audit indique donc explicitement `STATIC_ONLY_NOT_UI_TESTED`.
