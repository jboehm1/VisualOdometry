# Campagne B : essai sur **véritables enregistrements IMU de piétons** (18-10-2026)

## Objet / niveaux de preuve

Ce rapport est une mesure **réelle**, réalisée dans GitHub Actions :
[run #39162292672](https://github.com/jboehm1/VisualOdometry/actions/runs/39162292672).
Il évalue le **détecteur de pas de secours du moteur Java Android** `dev.jboehm.vectorvio.pdr.PdrEngine` face à des intervalles de pas annotés. Ce **n'est pas** une mesure de précision métrique du trajet ou du SLAM ARCore sur Galaxy S21.

- Origine : [simpleLoc / SIMUL](https://github.com/simpleLoc/SIMUL-dataset), six IMU synchronisées sur le corps de participants en mouvement, licence CC BY-SA 4.0.
- Version source : `ee7eb4e89bf33a17cafb4268207b802773427493` (pinned ; SHA-256 originaux dans le JSON de sortie).
- Moteur **effectivement exécuté** : `PdrEngine.java`, `StepPeakDetector.java` et `PdrReplay.java` de **notre branche Java PDR parallèle**, commit `f8228aa7acbc453001d623c537bd8d7f0a9bc176` ; compilation `javac` et exécution `java` dans un runner Linux.
- Outil ajouté : `PdrStepTrace.java` utilise les méthodes **exactes du même PdrEngine compilé**, sans réimplémenter le détecteur, pour enregistrer les timestamps d'étapes acceptées.
- Baseline : `tools/campaigns/imu_pdr.py` Python ; résultats étiquetés séparément.
- Source des annotations : `.stepNonoverlap` (intervalles annotés depuis les IMU des pieds, et non vérité de position GNSS ou motion-capture). **Les annotations sont téléchargées et lues après les estimations.**
- Unités vérifiées : magnitude de gravité sur premiers échantillons `~9,7–9,8 m/s²`. Les timestamps sont convertis en nanosecondes relatives pour les événements du replay Java.
- Appareil simulé : aucun. Ce sont de **vraies données collectées**, jouées sur CPU avec le code de l'algorithme Java, pas les capteurs physiques du S21.
- Réglages : paramètres par défaut des détecteurs ; **aucune optimisation sur les annotations de ces séquences**.

## Résultats (trois participants, trois positions de l'IMU)

**Définition erreur de nombre de pas :** `100 × (N_estimé − N_annoté) / N_annoté`.

**F1 temporel :** appariement chronologique unique des temps de pas estimés avec les **intervalles annotés** étendus de ±80 ms. Cette définition généreuse vérifie une cohérence avec des intervalles, **pas** un écart exact au contact du pied.

| Séquence SIMUL | IMU | Pas annotés | Pas Java | Erreur Java | F1 intervalles | Pas Python |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Participant 17, 142,60 s | Poche avant gauche | 218 | 238 | +9,2 % | 0,956 | 232 |
| Participant 17, 142,60 s | Main | 218 | 244 | +11,9 % | 0,944 | 264 |
| Participant 17, 142,60 s | Poche arrière | 218 | 266 | +22,0 % | 0,872 | 256 |
| Participant 24, 86,25 s | Poche avant gauche | 80 | 101 | +26,2 % | 0,862 | 106 |
| Participant 24, 86,25 s | Main | 80 | 76 | −5,0 % | 0,936 | 85 |
| Participant 24, 86,25 s | Poche arrière | 80 | 102 | +27,5 % | 0,835 | 105 |
| Participant 25, 140,65 s | Poche avant gauche | 174 | 216 | +24,1 % | 0,887 | 207 |
| Participant 25, 140,65 s | Main | 174 | 175 | +0,6 % | 0,974 | 180 |
| Participant 25, 140,65 s | Poche arrière | 174 | 250 | +43,7 % | 0,797 | 223 |

**Total de sources :** 29 563 échantillons temporels synchronisés (trois enregistrements), **9 combinaisons séquence/emplacement**, avec vraie accélération et gyroscope. Les gyro sont conservés comme provenance mais **non utilisés pour reconstruire un cap** : l'API Java PDR ne consomme pas directement ce capteur, et nous n'avons pas injecté le heading de la vérité terrain.

**Constats réels :** le détecteur fonctionne assez bien en main sur les deux séquences courtes testées, mais **sur-détecte nettement des pas en poche arrière** (jusqu'à +43,7 %), et aussi certaines séquences en poche avant. Ces résultats montrent un problème de robustesse selon le port du capteur et **ne suffisent pas** à affirmer une précision kilométrique/SLAM, ni à établir une généralisation statistique à tous les utilisateurs.

## Chaîne reproductible

Exécution complète, de bout en bout, en lisant les fichiers d'origine via un commit GitHub externe figé et les sources Java figées :

```bash
python -m unittest discover -s tools/campaigns/tests -p 'test_android_pdr_bridge.py' -v
python tools/campaigns/simul_real.py --out campaign_results/real_simul
```

Intégration continue : `.github/workflows/real-pedestrian-java.yml`. Les contrôles exigent trois séquences, trois emplacements chacune, les deux moteurs identifiés, au moins 200 échantillons, aucune entrée `HEADING` synthétique et aucun rejet de ligne Java.

Fichier `real_step_report.json` : commits des moteurs/données, sommes SHA-256 des sources/événements, nombres d'échantillons, comptes de pas et métriques F1/precision/recall. Les CSV volumineux d'origine ne sont **pas** redistribués dans les artifacts GitHub ; le rapport seul est téléchargeable.

- [Rapport GitHub Actions](https://github.com/jboehm1/VisualOdometry/actions/runs/39162292672)
- [Copie du rapport sur ton Google Drive privé](https://drive.google.com/file/d/1wkbHUY19Iut2r6qe3zpgIwPVCYpZtCTh/view)

## Limites et travail restant

1. **Réévaluer le même moteur** avec données smartphone Android réelles et STEP_DETECTOR natif (notre replay Java utilise le fallback d'accélération).
2. Étendre l'échantillonnage aux courses, escaliers, rotations, arrêts et positions variées, en pré-enregistrant une stratégie de validation tenue à l'écart.
3. Mesurer l'erreur de **distance** uniquement avec une référence indépendante (mètre ruban, odomètre étalonné, Vicon, etc.) ; la valeur `N_pas × 0,74 m` dépend d'une hypothèse de longueur de pas et n'est **pas** une vérité.
4. Pour la 2D, capturer l'orientation **réelle du téléphone** indépendante des annotations de référence ; ne jamais injecter le cap du GT dans le moteur.
5. L'intégration de la branche PDR Java **dans l'application Android d'intégration** doit faire l'objet d'un chantier de merge distinct (conflits menu DATA/manifest/planner) ; ce rapport n'en prouve pas le résultat.
6. Les campagnes **A VIO complète sur images/IMU brutes** et **C ARCore sur véritable Galaxy S21** demeurent à certifier séparément. La campagne A précédente a scoré des trajectoires VINS-Mono **déjà estimées**.

Cette vérification représente une **preuve réelle limitée au comptage de pas Java**, sans revendication de qualité produit ni de précision 6-DoF.
