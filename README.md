# VisualOdometry — Android VECTOR VIO et archive historique

> **Vous êtes sur `master`, la branche GitHub par défaut qui conserve le projet historique de 2019.** L'application Android **VECTOR VIO** actuelle et sa coordination sont développées sur **`feature/slam-explorer`**. Les deux projets ne doivent pas être confondus.

## 📌 Ouvrir le pilotage CENTRAL du projet Android

- **[Feuille de route centrale — toutes les tâches et tous les chantiers](https://github.com/jboehm1/VisualOdometry/blob/feature/slam-explorer/docs/ROADMAP_CENTRAL.md)** — Android live, B1–B8, PDR, GPS/FLP, datasets/tests et fumée dense/pompiers **en recherche uniquement**.
- **[Atlas interactif V5 (HTML)](https://github.com/jboehm1/VisualOdometry/blob/feature/slam-explorer/docs/VECTOR_VIO_ATLAS_INTERACTIF_v5.html)** — cas d'usage, architecture, idées et historique conservé. Télécharger le HTML puis l'ouvrir dans un navigateur pour l'interactivité hors ligne.
- [État machine-readable des chantiers (JSON)](https://github.com/jboehm1/VisualOdometry/blob/feature/slam-explorer/docs/ROADMAP_CENTRAL.json).
- [Règles de coordination de plusieurs agents](https://github.com/jboehm1/VisualOdometry/blob/feature/slam-explorer/docs/DEVELOPMENT_COORDINATION.md).
- [Branche de l'application Android moderne](https://github.com/jboehm1/VisualOdometry/tree/feature/slam-explorer).

**À retenir :** les PR expérimentales peuvent être vertes en CI **sans** être fusionnées dans l'application Android finale ; une localisation GNSS/VIO/PDR n'est pas qualifiée pour une opération de secours. Le scénario pompier en fumée dense est un **objectif de recherche non opérationnel**.

## Code historique

Les fichiers d'odométrie et notebooks de cette branche `master` demeurent la base originale. **Cette page est seulement un portail documentaire** vers le développement actuel : elle ne remplace, ne fusionne et ne supprime aucun fichier ni aucune expérience du dépôt historique.

Pour récupérer l'application Android actuelle :

```bash
git clone -b feature/slam-explorer https://github.com/jboehm1/VisualOdometry.git
```
