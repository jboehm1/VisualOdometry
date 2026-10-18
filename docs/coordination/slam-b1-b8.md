# Coordination — chantier SLAM B1–B8 / validation V5

**Prise en compte enregistrée : 18 octobre 2026.** Ce document décrit l'état Git **poussé**, pas les modifications non publiées d'autres chats.

## Références Git réellement consultées

| Élément | Ref / SHA |
| --- | --- |
| Branche d'intégration (lue avant intervention) | `feature/slam-explorer` — **`6c8f54f38af868195c8580a5c0c64c9999a6878f`** |
| Instructions communes | `feature/slam-explorer:AGENTS.md` — blob `9e56be8eb4a4d4575399b2324daa6fad4b05db7d` |
| Coordination générale | `feature/slam-explorer:docs/DEVELOPMENT_COORDINATION.md` — blob `61b55fcea7c9a2e241735f0c17505ecc8721404b` |
| Branche B1–B8 précédente / base de mon nouveau travail | `work/slam-roadmap-b1-b2-b8` — **`f34c8f704b7f35f085825551f74feb2734f83bdc`** |
| **Ma branche de travail isolée** | **`work/slam-b1-b8-validation-v5`**, créée exactement sur `f34c8f7` |
| Chantier parallèle PDR (tête revue) | `feature/camera-free-pdr` — **`f8228aa7acbc453001d623c537bd8d7f0a9bc176`** ; PR #5 |
| Chantier parallèle campagnes (tête revue) | `feature/three-validation-campaigns` — **`9dbe7a26d16b57248b9f07fb03549e28ebc096a0`** |
| Autre chantier benchmark | `feature/real-dataset-benchmark` — `6315552ad4ea7712a063b7091012ea16132a8562` |
| Notre PR précédente | PR #3, brouillon ; **ne pas merger en l'état** |

Le document de coordination central est un **instantané daté** et ne garantit ni l'absence de commits ultérieurs ni l'existence de verrous Git. Cette entrée est spécifique au chantier B1–B8.

## Périmètre et contraintes comprises

1. **Ne pas écraser d'autres développements.** PDR Android, campagnes de replay, navigation GPS/GPX, capture vidéo, cartographie, VIEW à 5/15/30 FPS avec échantillonnage de poses distinct, export et menus DATA doivent tous subsister après intégration.
2. **Conflits connus :** les trois développements touchent `MainActivity.java` et/ou `AndroidManifest.xml`. Le positionnement des actions DATA par indice `9` est une collision concrète : PDR, courses et replay doivent garder une action distincte. Les modifications supplémentaires « GPX + VIO LIVE » du chantier B1–B8 ajoutent encore une entrée. Une fusion prenant un fichier entier d'une seule branche supprimerait des fonctionnalités.
3. **Fichiers partagés concernés, mais exclus des modifications de ma branche V5 :**
   - `app/src/main/java/dev/jboehm/vectorvio/MainActivity.java` — **pas de modification V5** ;
   - `app/src/main/AndroidManifest.xml` — **pas de modification V5** ;
   - `.github/workflows/android.yml` et autres workflows — **pas de modification V5** ;
   - `README.md`, menus DATA, activités/exports déjà partagés — **pas de modification V5** ;
   - `docs/DEVELOPMENT_COORDINATION.md` et `AGENTS.md` — **pas de réécriture depuis ce chantier**.
   Ces fichiers peuvent déjà être différents dans la **base héritée** de `work/slam-roadmap-b1-b2-b8`. Ne pas confondre « non touché par V5 » avec « identique à l'intégration ».
4. **Fichiers que je peux modifier en V5, propriété de ce chantier :** `app/src/main/java/dev/jboehm/vectorvio/geo/*`, `GeoAlignment.java` et leurs tests ; `tools/eval/trajectory_metrics.py` et tests ; `tools/quality_report.py` et tests ; `tools/vision/*` et tests ; documentation spécifique dans `docs/coordination/slam-b1-b8.md` et, si nécessaire, `docs/ROADMAP_EXECUTION.md` / `docs/VECTOR_VIO_TASKS.json`. Tout changement à `gps/GpsNavigator.java`, `store/*` ou une interface commune devra faire l'objet d'une comparaison ciblée avec l'intégration et d'un test explicite de compatibilité.
5. **Distinguer les preuves :** baseline PDR Python ≠ moteur PDR Java/Android ; un catalogue de datasets ≠ des datasets réellement acquis ; simulation, fixtures et CI verte ≠ précision terrain Samsung S21. L'ARCore APK ne lit pas directement EuRoC/TUM VI. L'ATE/RPE, l'échelle, les repères (ENU, axes téléphone/caméra), l'horloge, les resets et la provenance de vérité terrain doivent être explicités.
6. **GPS/VIO reste non autoritaire :** garder position GNSS mesurée, correction candidate et erreur résiduelle distinctes ; les biais GNSS corrélés ne disparaissent pas avec un bon ajustement. L'ambiguïté d'un carrefour ne doit jamais être convertie en certitude ; les pertes de suivi nécessitent une dégradation visible. Un odomètre de caméra ≠ distance de marche du porteur.
7. **CI et fusion :** lancer les tests sur le SHA exact de la branche V5, puis tester la **combinaison effective** séparément avant fusion. Une CI verte individuelle ne valide pas l'application réunifiée. Pas de fusion automatique ou de réécriture d'historique partagé.

## État de travail et prochaine étape

**Étape accomplie :** lecture des deux instructions sur l'intégration à `6c8f54f`, vérification des références Git actuelles, inspection de `MainActivity`, du manifeste et du workflow sur les branches en parallèle, création de la branche isolée `work/slam-b1-b8-validation-v5`.

**Prochaine étape de ce chantier :** améliorer le banc de validation hors ligne des poses/mesures (provenance, métriques, règles d'horloge et segmentation) et ajouter des tests ciblés sur les sous-modules **sans toucher aux fichiers partagés**. L'intégrateur réunira ultérieurement PDR, replay/campagnes, modes courses et GPX+VIO avec actions de menu stables, manifeste cumulatif, et CI + smoke de l'APK combinée. Avant ce passage : relever à nouveau tous les SHA et contrôler chaque chemin conflictuel.

**Handoff :** publier pour chaque lot le commit précis, les tests réellement exécutés et les limites restantes. Ne jamais présenter des pourcentages documentaires comme une qualification de sécurité en neige/brouillard ou une fiabilité centimétrique.

## Mise à jour V5 — premiers commits isolés

- Coordination créée au commit **`00a009480df4c41cd2ce9bd36ab5598243b34ad2`** sur la branche indiquée ci-dessus, avant tout nouveau travail V5.
- Un premier lot **sans modification des fichiers partagés** ajoute `tools/eval/provenance.py`, `tools/tests/test_benchmark_provenance.py`, `tools/eval/examples/manifest-fixture.json`, `tools/eval/examples/metrics-fixture.json` et actualise `tools/eval/README.md`.
- Fonction : enveloppe de provenance canonique des évaluations, nom réel du moteur, configuration et SHA, séparation référence indépendante/données synthétiques, refus des résultats numériques non finis. Les trois évaluateurs peuvent conserver leurs résultats structurés imbriqués sous `metrics` sans être combinés ou assimilés à la précision terrain. Un manifeste **déclare** la provenance sans la vérifier : le résultat conserve `qualified_for_safety_navigation=false`.
- Revue et CI : PR **#8**, base `work/slam-roadmap-b1-b2-b8` (PR brouillon), tests en cours au moment de la première soumission. Le PR #3 ne change pas de base automatiquement et les conflits entre chantiers ne sont pas résolus par ce lot.
- **Prochain jalon** : vérifier la CI au *dernier* commit de PR #8, relever les résultats exacts, préparer un rapport machine-readable sur les jeux réels obtenus *uniquement après téléchargement et acceptation des licences*, puis livrer ce module au responsable de l'intégration combinée. Ne pas fusionner automatiquement.
