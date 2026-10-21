# VECTOR VIO — écran réglages v0.12 · 20 octobre 2026

## Retour utilisateur et décision UX
Le menu en modal Android v0.11 (catégorie puis sous-dialogue, texte denses, petite zone utilisable) est jugé illisible. Pas de nouveaux widgets éphémères : un **écran Android natif plein format** séparé remplace le menu imbriqué. Architecture sans dépendance externe : Android SDK Java 17, support minSdk 26.

## Spécification implémentée
- En-tête contrasté `PARAMÈTRES` + vraie version d'installation depuis PackageManager; bouton retour et barres système restaurées (aucun immersive UI).
- Six onglets horizontaux défilants (Parcours, GPS & IMU, Caméra, Données, Cartes, Avancé); zones tactiles au moins 46 dp; cartes de paramètres d'au moins 94dp; titres 17sp et explications 14sp à retour automatique.
- Recherche en temps réel sur titre, explication et groupe; chaque résultat peut être exécuté sans passer par catégories.
- Interrupteurs natifs persistants GPS-vitesse, télémétrie IMU, estimation du sol et conseils vélo. Permissions localisation demandées au clic si besoin; un refus remet à OFF. MainActivity relit et applique les préférences lors du retour au premier plan.
- Toutes les actions historiques conservées (GPX, GPS 2D, PDR, Courses, carte 3D, replay, collecte/export, diagnostics et simulation) par identifiants `SettingsCatalog.Action.name()` (jamais des positions ou labels traduits).
- Les commandes contextuelles qui nécessitent le moteur live renvoient à la caméra avant ouverture des contrôles concernés.

## Contraintes et limites
- L'ouverture d'une Activity séparée déclenche le `onPause()` de MainActivity : le suivi caméra, le chrono de recording et les ancres ARCore sont interrompus. C'est voulu pour éviter de prétendre à un suivi derrière un écran de réglages; le test de reprise est nécessaire.
- Les sous-menus spécifiques GPX, maps et export restent des dialogues natifs v0.11. Nouvelle amélioration possible : intégrer GPX dans un espace dédié si retour terrain.
- Contraste/taille calibrés en unités `sp/dp` ; tests Robolectric vérifient présence, interactions, toggle et dispatch. Une capture d'écran et un test VoiceOver/TalkBack sur vrai S21 restent à faire.
- Aucune modification du moteur d'odométrie, du calcul des vitesses ni des algorithmes PDR/GPX/SLAM. Pas de promesse de meilleure précision grâce à l'UX.

## Carte : grand affichage à partir de la miniature
- Tap sur la petite carte de droite : zoom plein panneau avec bouton × en haut à droite; gestes hors miniature restent ceux de la caméra.
- Haut : carte VIO locale et POI ; si GPX AR candidat + repères disponibles, lignes GPS alignées **à titre approximatif** en bleu.
- Bas : carte du GPX chargé entier même sans activer l'AR GPX. Projection longitude/latitude locale north-up, départ en cyan, arrivée en orange, position GPS si fraîche (cercle de précision déclaré).
- Jamais de superposition géographique de la trajectoire VIO avant calage : panneau GPX séparé avec badge **NON CALÉ AVEC VIO**. Calage candidat ne constitue pas une vérité terrain.
- Tester zoom/fermeture, GPX chargé ou absent, perte GPS, 50k points GPX, suivi AR, vieux segments distincts.

## Essais manuels
1. Ouvrir MENU, vérifier lecture des cartes à distance normale, revenir depuis système et bouton ‹, tester portrait, agrandissement police système et clavier de recherche.
2. Ouvrir chaque onglet; recherche « GPX » et « vitesse »; vérifier chaque ligne; essayer toggles en conservant l'écran ouvert.
3. Désactiver GPS puis revenir caméra: badge rouge GPS OFF; réactiver et autoriser; puis tester permission refusée.
4. Désactiver IMU et alertes vélo : aucun HUD relatif; réactiver et tester persistance après fermeture.
5. Pendant un enregistrement, ouvrir MENU : pause explicite / reprise, trajectoire sans raccord fictif.
6. Ouvrir GPX import, PDR, Courses, export, simulation; vérifier que l'action attendue est exactement celle de la ligne sélectionnée.
7. Comparer ancien et nouvel APK : sauvegarder d'abord vos exports ; voir issue #31 concernant signature Android entre builds GitHub.

## Git
Base SHA : `0eeeeb22a18b88cc6fb842ecea2556fb28754c74`. Nouvelle branche : `feature/v012-settings-screen`. Fichiers partagés concernés : MainActivity, HudView, AndroidManifest, app/build.gradle, tests et docs centrales. Moteurs, GPS et configuration CI laissés intacts.
