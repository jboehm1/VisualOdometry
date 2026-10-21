# Suivi GPS / GPX — contrat et validation

Implémentation initiale 0.8.0-alpha, 18 octobre 2026. Cette fonctionnalité reste distincte des cartes AR, de l'odométrie caméra et du collecteur WALK. Aucun GPS n'est fusionné silencieusement dans les mètres VIO.

## Problème traité

Estimer la progression sur un parcours prédéfini malgré bruit GPS, croisements et chemins parallèles. Le parcours importé est une hypothèse connue ; ce n'est pas une preuve que l'utilisateur marche sur le bon chemin. En l'absence d'un réseau routier/de sentiers, on ne peut pas identifier les chemins voisins absents du GPX.

La position GPS brute et sa précision déclarée restent visibles, distinctes de la position estimée sur la trace. La précision Android est un rayon annoncé à 68 %, pas une borne absolue. Les états ambigus/imprécis/périmés doivent rester explicites plutôt que masquer une incertitude par un point collé sur la ligne.

## Interface

DATA -> GPS ROUTES (également depuis WALK).

- IMPORT GPX : copie privée du segment choisi ; aucune dépendance ultérieure à l'URI externe.
- MES PARCOURS : ouvrir/supprimer, avec confirmation de suppression.
- DÉPART : rejoindre le début puis suivre le parcours.
- REPRENDRE : reprendre la progression sauvegardée pour ce même parcours/sens, avec une nouvelle position GPS.
- REJOINDRE TRACE : acquisition parmi les segments du parcours ; en cas d'ambiguïté, confirmer par appui long sur la trace et choix du repère kilométrique.
- Sens inverse : avant démarrage, inverse l'ordre des points.
- ENREGISTRER / SAUVER PROMENADE : journaliser une promenade et l'enregistrer comme GPX réutilisable. Fonctionne aussi sans parcours de guidage.
- RÉCUPÉRER TRACE : récupération des lignes complètes après interruption, ou suppression confirmée du journal.
- EXPORT GPX : export explicite du parcours choisi ; aucun upload automatique.

Vue hors ligne de la trace, pas de fond de carte. Zoom/pan, double appui pour ajuster ; début vert, fin rose, GPS bleu avec précision, estimation sur trace jaune seulement en suivi non ambigu. Repères kilométriques indicatifs. L'affichage utilise une projection locale schématique ; les calculs géographiques utilisent des arcs de grand cercle et non les pixels de la vue. Le cap cible est relatif au nord vrai, pas une flèche dépendant de l'orientation du téléphone. Pas de calcul routier, de commandes vocales ou d'instructions de carrefour certifiées.

## Moteur

- Coordonnées WGS84, distances sphériques grand cercle (rayon moyen 6371008,8 m). Pas une géodésie ellipsoïdale de précision topographique.
- GPX piste/route : les segments disjoints ne sont jamais raccordés. Une route GPX à points espacés représente des arcs/lignes entre ses points, pas un calcul le long des routes.
- Projection sur les arcs ; candidats multiples dans un rayon dépendant de la précision. Fenêtre de progression/temps limitant les sauts de branche, acquisition globale explicite.
- Faisceau de type Viterbi, 12 états, jusqu'à 24 candidats : coût distance/précision, cohérence entre déplacement observé et distance le long de la trace, cap de déplacement si exploitable. Scores heuristiques non calibrés ; aucun pourcentage de confiance affiché.
- Comparaison des branches concurrentes : si l'écart de score est insuffisant entre hypothèses séparées, état AMBIGUOUS. Aucun réseau OSM ni GraphHopper intégré.
- Les seuils de guidage GPS n'affectent pas le compteur VIO ni les poses de caméra enregistrées.

## Qualité GPS et événements

Horloge monotone Sensor/Location elapsedRealtime ; fix futur, périmé (>5 s), coordonnées invalides et timestamps non monotones ne peuvent pas avancer. Précision >30 m : progression suspendue. Saut dépassant 80 m/s plus marge d'incertitude rejeté. Interruption >15 s : progression gelée puis réacquisition ; reprise automatique uniquement après plusieurs nouvelles positions proches et non ambiguës (précision ≤15 m). Sinon reprise explicite. Aucun trajet manquant n’est inventé. Seuils d'ingénierie à qualifier sur terrain, pas des garanties probabilistes.

Écart : distance brute au segment suivi distincte du point recalé. Confirmation hors parcours après plusieurs fixes dont distance moins précision dépasse 20 m ; retour avec hystérésis quand distance plus précision est inférieure à 20 m. Vibration optionnelle, limitée à une fois/minute, uniquement en état hors parcours confirmé. Pas d'alerte affirmative sur un GPS trop imprécis.

Arrivée : progression proche de la fin ET proximité de l'extrémité dans une zone estimée de 10–20 m selon la précision, confirmées sur plusieurs fixes. Un parcours fermé ne doit pas annoncer l'arrivée dès le départ.

## Android, énergie et vie privée

Service GPS de premier plan, notification ARRÊTER ; démarrage explicite depuis activité visible, permissions localisation et notifications. Écran éteint : pas de caméra, ARCore, rendu ou verrou CPU ajouté par ce service. Il peut coexister avec WALK, dont le coût caméra est séparé. Pas de permission Internet ajoutée, pas de permission localisation d'arrière-plan, pas de redémarrage caché après arrêt forcé. Le GPS lui-même consomme ; aucun gain d'autonomie chiffré sans essai matériel.

Progression sauvegardée périodiquement et à l'arrêt, puis reprise explicite ; aucun ancien fix n'est présenté comme une position actuelle après redémarrage. Journal promenade privé avec lignes complètes, flush périodique, fermeture/fsync à l'arrêt ; fin partielle ignorée en récupération. Trous GPS de plus de 15 s, précision insuffisante et sauts créent des segments séparés dans le GPX dérivé. Les petites traces sans longueur ou segments d'un seul point ne deviennent pas une route navigable. Ne jamais promettre un export sans perte après coupure d'alimentation.

## Entrées et bornes

GPX 1.0/1.1 ou namespace vide compatible, trk/trkseg et rte/rtept. DTD/entités externes interdites ; nombres non finis/hors plage rejetés. Limites : 12 MiB, 50000 points par import, 256 segments, profondeur XML 32 ; noms et textes bornés. Les segments valides sont sélectionnés explicitement, pas raccordés en silence. Copies canoniques privées à identifiants UUID, écritures AtomicFile, chemin externe interdit. Bibliothèque bornée à 200 fichiers et quota de stockage. Route active et progression ne sont pas recalées dans le repère AR.

## Validation automatisée et limites

Le parseur/stockage/écran/service sont vérifiés sous Robolectric, avec rendu Android natif de scénarios synthétiques. Les tests du moteur et le benchmark déterministe sont exécutés en CI. La validation matérielle reste à effectuer.
Le code est une implémentation testable, pas une certification de navigation. Validation dehors nécessaire : S21 précis/approximatif, couvert forestier, bifurcations rapprochées, routes parallèles, pertes réseau/GPS, écran verrouillé, changement d'app, notification STOP, récupération après arrêt et batterie. Les tests synthétiques ne prouvent pas que le bon sentier est reconnu en forêt.

## Sources primaires

- GPX 1.1 : https://www.topografix.com/GPX/1/1/
- Android Location : https://developer.android.com/reference/android/location/Location
- Services de localisation : https://developer.android.com/develop/background-work/services/fgs/service-types
- Newson/Krumm, principe de matching par séquence : https://www.microsoft.com/research/publication/hidden-markov-map-matching-noise-sparseness/
- GraphHopper, implémentation sur réseau routier à distinguer de notre polyline : https://github.com/graphhopper/map-matching

## Deuxième passe de validation

- Le test de départ bruité a été renforcé : la progression doit réellement avancer, pas simplement éviter une mauvaise arrivée. Correction de la condition d'entrée près du départ.
- Le parseur est testé dans le runtime Android via Robolectric ; l'ancien test JUnit seul appelait un stub Android. Sur hôte, un KXml indépendant a aussi vérifié l'import/les rejets. Le flag optionnel process-docdecl n'est modifié que si actif ; les événements DOCDECL restent toujours rejetés.
- 30 graines de bruit, boucle de 850 m avec branches parallèles séparées de 25 m et biais de 7 m sur la moitié des runs : aucun échec au seuil de régression de 20 m RMSE de progression ; pire RMSE observée 6,211456 m. Ces chiffres décrivent cette construction synthétique, pas une précision annoncée du téléphone.
- Route dense 50000 points, 1000 fixes séquentiels : ~0,09 s sur hôte lors de la mesure locale (hors construction du modèle), pas un benchmark Android/batterie. Le matching s'effectue sur un worker à cadence GPS ; la vue dessine au plus environ 6000 points. Les points originaux et le GPX ne sont pas décimés par l'affichage.
- Ajout de tests sur fixes futurs/invalides, précision qui devient périmée, sens inverse, reprise après plusieurs fixes et saut isolé. Un contrôle supplémentaire utilise la vitesse GPS déclarée exploitable pour rejeter un déplacement incompatible avec celle-ci.
- Script déterministe tools/gps/run.sh ajouté à CI ; JSON de synthèse archivé avec les résultats, sans vidéo/position utilisateur privée.

## Troisième passe : conservation et interface

- Interface compacte : DÉMARRER/ARRÊTER GPS, PARCOURS, REJOINDRE et OPTIONS. Les actions de bibliothèque, import/export et récupération sont regroupées dans PARCOURS ; les actions d’enregistrement et de cadrage dans OPTIONS.
- Import SAF sur worker avec annulation explicite et échéance de 30 s. L’annulation du fournisseur externe reste au mieux de ses capacités ; l’interface ne bloque pas en attente de son I/O.
- Journal V2 : lignes R de positions reçues (latitude, longitude, altitude disponible, temps monotone ns, précision, vitesse/cap retenus par les contrôles de qualité), puis lignes P des points admis dans le GPX dérivé et B pour les ruptures. Les mesures écartées du GPX restent dans R. Les anciens journaux V1 restent récupérables.
- Après sauvegarde, le journal source est conservé à côté du GPX et exportable explicitement via PARCOURS → exporter le journal GPS. Les champs vitesse/cap ont déjà été contrôlés par le service ; ce journal ne constitue pas un dump complet de tous les champs Android Location. Les vidéos et IMU restent dans les exports de capture séparés.
- Le GPX exporté est canonique : géométrie, noms et altitude disponible. Il ne conserve pas les extensions, timestamps et toutes les métadonnées du document original.
- Tests supplémentaires indépendants de la boucle initiale : figure huit, deux épingles et zigzag, chacun dans les deux sens, dix graines, soit 60 runs. Pire RMSE de progression 8,592559 m, aucun échec au seuil de régression de 25 m. Avec les 30 runs initiaux, 90 runs synthétiques passent. Cela ne mesure pas une précision terrain.
- Les contrôles de continuité sont communs au guidage et au GPX dérivé ; ils ne suppriment pas les lignes R et ne touchent pas les poses VIO.

## Extensions caméra et marche sans caméra

Non implémentées dans cette livraison : segmentation visuelle des sentiers, reconnaissance de lieux géographiques, fusion GPS/VIO et navigation pédestre par pas. Une segmentation peut proposer des zones visuellement praticables sans prouver leur identité géographique. Le recalage GPS demande une transformation géographique et un modèle d’incertitude ; une direction visuelle seule ne suffit pas.

Sans caméra, GPS et cap de déplacement permettent déjà ce suivi GPX. Un futur compteur de pas Android, selon capteurs/permissions disponibles, pourrait fournir pas et distance estimée avec longueur de pas calibrée. L’orientation du téléphone n’est pas forcément celle de la marche. Pendant une perte GPS, toute position calculée par pas/IMU doit porter une incertitude croissante et rester distincte des positions mesurées ; le podomètre ne convient pas à estimer les kilomètres à vélo ou en voiture.
