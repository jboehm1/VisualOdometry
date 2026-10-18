# Campagne B — passerelle vers le vrai moteur PDR Android Java

Cette passerelle est volontairement **indépendante** du moteur de démonstration Python `tools/campaigns/imu_pdr.py`. Elle permet de préparer les données capteurs pour la classe `dev.jboehm.vectorvio.pdr.PdrReplay` qui se trouve actuellement sur la branche parallèle `feature/camera-free-pdr`.

## Contrat

Entrée IMU canonique CSV : `t_ns,type,x,y,z`, avec timestamps monotones en nanosecondes.

- `ACCEL` : les trois axes originaux en m/s² sont transmis en événements `ACCEL`. La classe Java utilise son propre `StepPeakDetector` si aucun `STEP` n'est fourni. Aucun pas n'est inventé par le convertisseur.
- `GYRO` : comptabilisé et conservé **uniquement dans le fichier source**, pas transmis à `PdrReplay` car son API n'accepte pas directement les trois axes du gyroscope. Une intégration gyro inertielle complète nécessitera un composant d'attitude **dans le chantier PDR Java**.
- `BARO` : transmis sous forme de `PRESSURE` (hPa si l'entrée respecte l'unité canonique déclarée).
- `MAG` : comptabilisé, non exploité par le moteur Java actuel.
- `HEADING` : optionnel, issu d'une source **mesurée** externe explicitement déclarée, sous la forme CSV `t_ns,heading_rad`. Ne jamais utiliser le cap de la vérité terrain pour produire ces événements.

Sans heading, `PdrEngine` peut fournir la **distance par les pas**, mais ne doit pas présenter une trajectoire 2D complète comme observée. La direction du téléphone ne correspond pas forcément à la direction de la marche.

## Préparation et tests hôte

Depuis la racine du dépôt :

```bash
python3 -m unittest discover -s tools/campaigns/tests -p 'test_android_pdr_bridge.py' -v

python3 tools/campaigns/android_pdr_bridge.py \
  --imu /datasets/oxiod/canonical_imu.csv \
  --out /tmp/java_pdr/events.csv

# Si un véritable cap du téléphone (pas une référence vérité terrain) est enregistré :
python3 tools/campaigns/android_pdr_bridge.py \
  --imu /datasets/phone/imu.csv \
  --heading-csv /datasets/phone/device_heading.csv \
  --heading-source phone_rotation_vector_logged \
  --out /tmp/java_pdr/events.csv
```

Le convertisseur génère `events.csv` et `events.manifest.json` avec SHA-256, provenance, compteurs et limites. Il ne génère pas de chiffres ATE/RPE : **cela serait trompeur sans exécution de l'estimateur Java et référence indépendante**.

## Exécution Java (uniquement après accès à la branche PDR)

La commande prévue, dans **un clone/worktree isolé de `feature/camera-free-pdr`** où les classes Java sont disponibles :

```bash
mkdir -p /tmp/java_pdr/classes
javac -d /tmp/java_pdr/classes \
  app/src/main/java/dev/jboehm/vectorvio/pdr/StepPeakDetector.java \
  app/src/main/java/dev/jboehm/vectorvio/pdr/PdrEngine.java \
  tools/pdr/PdrReplay.java

java -cp /tmp/java_pdr/classes dev.jboehm.vectorvio.pdr.PdrReplay \
  /tmp/java_pdr/events.csv /tmp/java_pdr/path.csv
```

**Ce chantier n'ajoute ni ne duplique ces classes Java** dans la branche d'intégration. Il attend leur intégration revue pour les utiliser dans un futur runner commun. Ne pas confondre `path.csv` au repère magnétique local avec la pose EuRoC en 3D.

## Validation réelle attendue

1. Vérifier les axes/unités/timestamps d'un vrai dataset OxIOD ou d'une capture Galaxy S21. Le cap doit être acquis indépendamment de la vérité terrain.
2. Exécuter `PdrReplay.java` en déclarant son commit exact, la longueur de pas/config et les hashs capteurs.
3. Comparer à une référence indépendante `tools/pdr/evaluate.py` **uniquement après validation des horloges et du repère East/North** ; si la référence utilise Tango/VIO, l'indiquer comme proxy, pas comme vérité optique.
4. Produire un rapport canonique incluant disponibilité heading, pas sans heading, couverture, interruptions, erreurs absolues et distances mesurées.
5. Ajouter un test d'intégration avec le vrai module Java sur le SHA combiné, puis un essai physique S21.

## Coordination

Voir [la note dédiée](../../docs/coordination/validation-java-pdr-bridge.md). Aucun fichier partagé `MainActivity.java`, `AndroidManifest.xml`, `.github/workflows/**` ou module Java PDR parallèle n'est modifié dans ce lot.
