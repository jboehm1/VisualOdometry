# GPX AR — essai de la version expérimentale

Le code est sur `feature/gpx-ar-guidance`, PR #24. Le suivi détaillé est dans [GPX_AR_GUIDANCE_PLAN.md](GPX_AR_GUIDANCE_PLAN.md) et [GPX_AR_TASKS.json](GPX_AR_TASKS.json).

## Démarrage

1. Installer l’APK de cette branche. DATA → **GPX AR — experimental camera guidance** → **IMPORT GPX**.
2. Choisir un segment continu. L’import conserve les coordonnées et les altitudes disponibles dans le stockage privé. Il n’active pas la localisation automatiquement.
3. **ENABLE AR GPX**, autoriser la localisation précise. **LOCATION SOURCE** permet AUTO, GPS natif ou Google Fused seul. AUTO tente Google Fused et se replie sur GPS si indisponible/erreur ou sans fix frais pendant 12 secondes.
4. Fermer le menu puis **START / RESUME**. Marcher en regardant des surfaces texturées et stables. Plusieurs fixes et un déplacement suffisamment grand par rapport au bruit GPS sont nécessaires. À titre de règle du moteur actuel : au moins 4 couples et une base d’au moins max(15 m, 4 × précision GPS moyenne déclarée).
5. Bleu : placement GPX candidat. Orange : trace locale enregistrée. Une petite flèche bleue est une direction horizontale estimée, indépendante de la présence d’un sol. Elle dépend d’un recalage et d’un matching utilisables.

## GPX réels et ajout d'altitude (octobre 2026)

- **GPX avec `<ele>`** : import possible immédiatement, l'altitude originale de chaque point est préservée dans le GPX sélectionné et les exports. Une altitude GPX n'est toutefois **pas encore** utilisée pour inventer un sol lointain dans la caméra : le ruban suit les plans de sol locaux observés par ARCore.
- **GPX sans aucune altitude** : importer le GPX, puis DATA → GPX AR → **ADD ALTITUDE · EU-DEM (ONLINE)**. Mettre la capture/GPX AR en pause. Lire et accepter l'envoi d'échantillons de la route à `api.opentopodata.org`. Un réseau et le consentement explicite sont requis.
- Le service public OpenTopoData interroge **EU-DEM 25 m / EVRS2000**. Le client échantillonne au maximum **1001 points** espacés d'environ 50 m ou plus, puis interpole les altitudes entre ces points aux sommets originaux. Ces hauteurs sont **estimées** (qualité verticale EU-DEM d'environ ±7 m RMSE), ni des mesures GNSS ni une vérité de terrain. Pour les ponts, falaises, escaliers, étages ou passages souterrains, le DEM peut représenter la mauvaise surface.
- Le nom de la route sauvegardée porte `EU-DEM 25m (estimated EVRS2000)` et le CSV exporté indique `EVRS2000_ESTIMATED`. Une route contenant **quelques** altitudes originales et **quelques** vides est refusée pour éviter de mélanger des datums incompatibles. Si la requête échoue, l'original reste intact.
- La requête DEM n'est **jamais** automatique lors de l'import. Elle n'est pas exécutée dans les tests CI. L'APK d'une branche antérieure au code DEM ne contient pas cette option.

**Séparer trois phénomènes :** altitude GPX/DEM (référentiel vertical géographique), altitude GPS du téléphone (erreur et datum éventuellement différents), et géométrie locale ARCore (repère relatif). Sans calibration verticale et sans terrain local visible, ces données ne se combinent pas automatiquement en un ruban 3D précis.

Références : [OpenTopoData, API](https://www.opentopodata.org/api/), [service public et quotas](https://www.opentopodata.org/), [EU-DEM, précision et datum](https://www.opentopodata.org/datasets/eudem/), [EU-DEM / Eurostat](https://ec.europa.eu/eurostat/web/gisco/geodata/digital-elevation-model/eu-dem).

## Lire les états

| État | Interprétation et action |
|---|---|
| WALK TO ALIGN | Géométrie insuffisante pour estimer le cap. Continuer une translation, pas une rotation sur place. |
| NO OBSERVED FLOOR | Aucun plan de sol observé sous la portion GPX. La route au sol reste cachée; orienter la caméra pour observer le terrain. |
| STALE LOCATION / POSE | Les dernières observations ne sont plus assez récentes; le placement est caché. |
| UNVERIFIED / MISMATCHED CLOCK | La caméra ne fournit pas une horloge Android REALTIME vérifiable, ou les timestamps sont incompatibles. Aucun offset n’est deviné. |
| AMBIGUOUS PATH | Plusieurs portions sont plausibles selon le matching existant; pas de flèche de direction certifiée. |
| CANDIDATE / allowance ~X m | Recalage expérimental; X est un diagnostic, pas une erreur absolue ni un intervalle de confiance calibré. Un biais commun GPS peut déplacer la ligne. |

## Vérifications à réaliser sur téléphone

- **Hauteur** : sur un sol déjà observé, lever/baisser le téléphone. Le bleu doit rester sur ce plan tant qu’il reste reconnu comme sol plausible; une zone inconnue doit être masquée.
- **Courbes** : tester un parcours puis son sens inverse (REVERSE SELECTED ROUTE). Conserver des points de référence indépendants pour mesurer le décalage; un bel ajustement au GPX ne valide pas sa précision.
- **Pause/reset** : pause de capture, arrière-plan ou perte AR demandent une nouvelle acquisition du recalage. RESET ALIGNMENT conserve le GPX. REMOVE SELECTED ROUTE efface seulement cette sélection privée.
- **Providers** : répéter les passages GPS natif puis Google Fused. Comparer aux mêmes repères physiques; l’accord entre les deux APIs n’est pas une vérité terrain.
- **Sols difficiles** : pente, marches, passage surélevé, herbe. L’itération actuelle utilise les plans locaux observés; ni relief lointain, ni altitude GPX inconnue ne remplace une surface mesurée.
- **Performance** : noter profil VIEW, cadence poses affichée, durée, batterie/température et éventuels freezes. Le profil VIEW ne décime pas les poses de recalage.

## Export utile pour le diagnostic

Utiliser **EXPORT → Dataset ZIP** après la capture. Pour obtenir la vidéo, armer **DATA → Training video** AVANT START/RESUME; sans capture vidéo préalable, aucun MP4 ne peut être reconstitué.

Les sessions GPX AR enregistrées ajoutent :

- `_gpx_ar_route.csv` : GPX sélectionné, index, révision et altitude facultative; datum vertical UNKNOWN.
- `_gpx_ar_fixes.csv` : source GPS/Fused, timestamps originaux/arrivée, coordonnées/précisions/vitesse/cap disponibles, mock, admission à la file d’appariement. L’admission ne signifie pas que la mesure a finalement été appariée.
- `_gpx_ar_poses.csv` : positions monde et métriques transportées, révision, segment, timestamp ARCore, timestamp caméra Android vérifié et réception. 0 pour le timestamp caméra signifie horloge non vérifiée.
- `_gpx_ar_alignment.csv` : états et diagnostics, séparés des poses et des mesures absolues.

Ce journal permet d’étudier la synchronisation et le moteur sans supposer des horloges égales. Il ne contient pas de référence de position indépendante. La validation métrique réelle et la consommation sur S21 restent à faire.

## Ancres et options

DISPLAY OPTIONS permet de masquer direction, repères 25 m, départ/arrivée, POI locaux et distance restante indépendamment. Vérifier qu’un POI masqué reste enregistré dans la carte. Marcher sur plus de 100 m et effectuer un aller-retour : observer raccords, dérive et relocalisation, pas seulement la beauté du ruban. Un échec d’ancre doit masquer la portion sans couper la capture. Le rejeu reproductible est décrit dans [GPX_AR_REPLAY.md](GPX_AR_REPLAY.md).
