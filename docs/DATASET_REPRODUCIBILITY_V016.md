# VECTOR VIO — matrice de datasets et exécutions reproductibles (v0.16)

**21 octobre 2026 — pipeline ajouté ; mesures terrain et double VIO non réalisées.**

But : rendre les comparaisons GPX / PDR Java / VIO ARCore / VIO natif **simples,
réutilisables, déterministes et falsifiables**. Conserver tous les anciens
adapters, les suites et les CI du dépôt : ce travail ajoute une couche de
verrouillage de sources, sans les remplacer ni télécharger des données privées.

## 1. Réutilisation des moteurs existants (ne rien inventer)

- `testenv.py plan/run` + `tools/testenv/contracts.py` / `runner.py` :
  source **unique** de capacité dataset × moteur, déclencheur réutilisable,
  tests avec seuils, timeout, fingerprint code et rapports historiques.
- `benchmark.py` / `tools/campaigns/unified.py` : contrats d'inférence,
  distinction stricte « Java PDR réel » / « Python PDR baseline » /
  « indexation caméra » / « score d'une trajectoire externe ».
- `tools/benchmark/bench.py` : EuRoC/TUM-VI camera/IMU/calibration index
  puis métriques d'une estimation *réellement exécutée ailleurs*. Index ≠ SLAM.
- `tools/pdr/datasets.py` : parsers OxIOD CSV et RoNIN HDF5 vers les vrais
  événements `PdrReplay.java`. Ne jamais passer GT dans les événements.
- `tools/campaigns/mobile_gvio_raw.py` / `mobile_gvio_ab.py` : images
  de téléphone et gyro réels, mais **monoculaire non-métrique**.
- `tools/vio/compare_measured_runs.py` : agrégateur de **résultats déjà
  mesurés** seulement. Rejette clocks et échelles ajustées au GT du test ;
  ne fabrique ni ATE, ni énergie, ni précision.

## 2. Quoi tester, avec quelle vérité ?

| Cible | Dataset / suite | Entrées réellement exploitables | Ce qu'il faut mesurer | État/limites de preuve |
|---|---|---|---|---|
| Contrats PDR sans réseau | `deterministic-pdr-smoke` / canonical | IMU synthétique, Java `PdrEngine` + Python | Regression/compatibilité et limites | **Code + CI** ; aucune précision réelle |
| Nombre de pas PDR | SIMUL, `benchmark.py run --dataset simul --engine android-pdr` | IMU humain et annotations de pas | TP/FP, cadence, couverture | Vrai moteur Java **compilé par l'outil**, pas garantie de l'APK ni ATE |
| PDR selon port/posture | OxIOD; RoNIN | IMU/timestamps, GT de qualité variable | erreur pas/distance puis dérive XY par sujet/posture **si heading vérifié** | Adaptateurs en partie présents ; données, licences et benchmark complet à prouver |
| Caméra + gyro mobile | Mobile-GVIO Indoor-2 | MP4/images, gyro, calibration, Fast-LIO2 | suivi visuel, inliers, coût CPU, **score de forme Sim(3)** | Vraies données, mais **échelle/clock ajustées après inférence**, PAS ATE métrique produit |
| Vidéo + IMU humaine | ADVIO09 | ~70 MB enregistrés, gyro, externe Pixel ARCore | ingestion brute et **ATE externe Pixel**, non appli | Données/reconstruction de référence partiellement dépendantes de l'inertiel ; CC BY-NC |
| VIO natif métrique | EuRoC MH_01 puis séquences tenues à l'écart | Stéréo mono, IMU calibrés, GT Vicon/Leica | ATE/RPE mètres, init, drift, perte et coût | Indexation présente ; **pas d'exécution Basalt Android** |
| VIO difficile | TUM VI | Fisheye/IMU, référence sur segments couverts | reprise suivi, jitter, ATE **sur fenêtre de GT couverte** | Indexation seulement; ne pas scorer hors couverture |
| GPX aligné monde | **Captures S21 + route RTK indépendante** | Camera/VIO + Fused/GNSS + GPX + référence indépendante | erreur latérale/longitudinale p50/p95, saut ancrage, mauvaises branches, acquisition/reprise | **Aucun dataset public unique du dépôt ne fournit encore cette validation de bout en bout** |
| Nuit, forêt, escaliers, tunnels | Captures utilisateur + séquences réelles appropriées | GNSS ambigu, caméra low light, IMU, temps de perte | durée sans tracking, ambiguïtés résolues, CPU/énergie | tests physiques manquants; tests de défaut synthétiques nécessaires |

**Ne pas télécharger un corpus privé/non commercial dans un APK commercial.**
La licence du code et celle des données/poids sont deux objets différents.
Les datasets ADVIO/Hilti et potentiellement d'autres sont réservés aux
usages prévus par leur licence. Confirmer indépendamment le droit d'usage
en entraînement lié à une exploitation commerciale avant de le faire.

## 3. Nouveau système de verrouillage déterministe

`tools/datasets/repro_manifest.py` exploite `load_suite`,
`job_inputs` et `runner.code_identity` déjà présents ; **pas de nouveau
registre de moteur ni exécuteur parallèle**.

Il enregistre, sans chemin absolu privé :
- contenu SHA256 et nombre d'octets de chaque fichier d'entrée local ;
- tous les fichiers d'une racine de dataset, ordre lexicographique stable ;
- SHA256 du JSON de suite et des sources Python utilisées par `testenv` ;
- SHA Git (si disponible), ID dataset/engine et provenance réelle ;
- état explicite `LOCAL_CONTENT_SHA256`, `SYNTHETIC_FIXTURE_NOT_REAL_DATA`
  ou `UNVERIFIED_REMOTE_BYTES_AT_PREFLIGHT`.

La vérification refait le hash **sur les vrais octets** ; rien n'est inféré
d'un simple nom de dossier. Symlinks interdits, 200 000 fichiers et 50 GiB
max par entrée, sortie créée une fois (pas d'écrasement historique). Les
datasets lourds sont re-hashés à la demande : ce coût est assumé dans la
phase de verrouillage, pas à chaque frame de l'application.

```bash
# Contrat sans réseau, exécute uniquement des fixtures IMU synthétiques.
python testenv.py plan --suite tools/testenv/suites/deterministic-pdr-smoke.json
python tools/datasets/repro_manifest.py lock \
  --suite tools/testenv/suites/deterministic-pdr-smoke.json \
  --receipt /tmp/pdr-fixture-lock.json
python tools/datasets/repro_manifest.py verify \
  --suite tools/testenv/suites/deterministic-pdr-smoke.json \
  --receipt /tmp/pdr-fixture-lock.json
python testenv.py run \
  --suite tools/testenv/suites/deterministic-pdr-smoke.json \
  --out campaign_results/pdr-smoke

# Tests réels existants : NE PAS les appeler des fixtures ni les mélanger.
python benchmark.py catalog
python testenv.py plan --suite tools/testenv/suites/real-phone.json
# Requiert droits et téléchargements réels ; ~1 Go Mobile-GVIO + traitement.
python testenv.py run --suite tools/testenv/suites/real-phone.json \
  --out campaign_results/real-phone --only advio09
```

`--require-real-local` échoue **volontairement** sur les fixtures et les
datasets publics dont les octets ne sont pas disponibles dans une racine
locale. Pour de vraies données, créer une suite locale déclarant ses chemins
d'entrées, puis utiliser `lock --require-real-local`. Sans fichiers locaux,
la suite publique est **planifiée**, pas certifiée reproductible en bytes.

Un manifeste valide n'indique **pas** une licence acceptée ou une
synchronisation de capteurs correctement calibrée. Sur les runners qui
téléchargent une archive publique, exiger aussi le checksum original, la
version de l'adapter, une preuve de calibrations, et une trace de sortie
persistante. Ne sauvegarder que rapports et hashes dans Actions, sauf choix
explicite de l'utilisateur pour les données personnelles.

## 4. Protocole strict pour comparer ARCore, VIO natif et PDR

**Deux comparaisons différentes :**
1. **Même données** quand possible pour deux estimateurs ouverts :
   images YUV brutes + IMU identiques, même calibration, mêmes horloges,
   même séquence & conditions ; configuration/sha/seed figés. VIO natif
   Basalt vs autre moteur sont comparables sur EuRoC/TUM-VI.
2. **Même trajet répété** pour ARCore vs VIO natif sur Android :
   les deux demandent la caméra et **ne partagent pas nécessairement le
   même flux**. ABBA/BAAB, parcours mesuré, équipement et angle constants,
   même tranche de luminosité/température si possible ; sorties séparées
   et étiquette `repeated_surveyed_route`, jamais `same_raw`.
   Il faut isoler les biais dus aux passages et comparer des intervalles
   comparables, avec plusieurs répétitions.

**Mesures obligatoires** : disponibilité tracking % du temps/GT,
démarrage/reprise médiane+p95, dérive de distance, ATE/RPE métrique
**uniquement si référence indépendante et repères calibrés**, erreur
latérale GPX p50/p95/p99, fausses reconnaissances de branche, consommation
J/min, latence d'une pose p95, fréquence des poses, RAM, CPU, FPS et
température. Les observations sans GT restent des *diagnostics*.

Le moteur VIO « rapide et économe » qui décroche dans un tunnel n'est pas
forcément meilleur que le moteur plus lent qui conserve la trace, et la
« trace jolie » n'implique pas un ancrage correct. Présenter le front de
Pareto avec les métriques brutes et leurs intervalles de confiance.

## 5. CI / critères de preuve

`.github/workflows/datasets-determinism.yml` :
- tests de mutations d'entrée/empreinte, symlink, taille, fuite d'absolu,
  rejet dataset/engine non supporté, vérité synthétique ;
- `lock` puis `verify` du même fichier et **rejet en mode real-local** ;
- tests PDR Java **réel** et Python **réel** sur entrée SYNTHÉTIQUE,
  `testenv.py run`, rapports et hashes en artifact.

**Cela ne télécharge pas Mobile-GVIO et ne mesure pas ARCore/GPX.
Ne pas utiliser CI verte comme preuve de précision sur le S21.**
Le benchmark real-phone existant en dehors de ce smoke est préservé.
Vérifier le résultat sur SHA exact et les problèmes du runner avant de
qualifier ce chantier.

## 6. Étapes prioritaires pour l'agent Work

1. Inventorier exécutions réelles et *données en octets disponibles* :
   distinguer catalogue, dataset acquis, parseur, estimateur exécuté, GT,
   score post-hoc, et usage commercial permis.
2. Étendre des **suites existantes**, pas créer un quatrième évaluateur :
   une config par expérience, unités/repères/GT/clock/seed/sha documentés.
3. Ajouter une vraie suite **GPS+GPX** avec S21 et référence RTK/survey
   + règles de défaut : multipath, tunnel, redémarrage, bifurcations.
   Sans vérité indépendante, qualifier uniquement la disponibilité.
4. Ajouter OxIOD/RoNIN held-out subjects sur **le Java PDR Android exact**.
   Évaluer les usages marche/course/poche/sac/vélo séparément; distinguer
   biais pas, heading et distance.
5. Réutiliser Basalt/engine permissif une fois compilé, puis mesurer la
   **même implémentation** sur dataset et via Android JNI, pas un
   estimateur Python différent présenté comme le produit.
6. Comparer GPX PDR ARCore VIO sans triche de vérité, mesurer coût/batterie
   sur téléphone, faire évoluer paramètres uniquement sur split train/dev
   et geler le test tenu à l'écart.
7. Mettre à jour `docs/work/ACTIVE_SPRINT.md`, le handover, la feuille de route,
   `docs/VECTOR_VIO_ATLAS_INTERACTIF_v5.html` et le PR après chaque
   changement vérifié.

**Promesse contrôlable : une pipeline reproductible, pas un résultat
d'exactitude que le monde physique ne peut garantir.**
