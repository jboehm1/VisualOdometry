package dev.jboehm.vectorvio;

/** Plain-language copy for every category and action; no fragile list indices. */
final class SettingsPresentation {
    static String tab(SettingsCatalog.Section section) {
        switch(section){
            case NAVIGATION:return "Parcours";
            case LOCATION:return "GPS & IMU";
            case DISPLAY:return "Caméra";
            case CAPTURE:return "Données";
            case MAPS:return "Cartes";
            case ADVANCED:return "Avancé";
            default:throw new IllegalArgumentException();
        }
    }
    static String title(SettingsCatalog.Action a) {
        switch(a){
            case GPX_EXTENDED_PREVIEW:return "GPX au loin (pointillé)";
            case GPX_AR:return "Guidage GPX sur caméra";
            case GPS_MANUAL_ALIGN:return "Ajustement manuel du GPX";
            case GPS_ROUTES:return "Navigation GPS sur carte";
            case PDR:return "Odométrie sans caméra (PDR)";
            case COURSES:return "Planifier un trajet en magasin";
            case RETURN_TRACE:return "Retour par la trace";
            case GPS_TOGGLE:return "Afficher la vitesse GPS";
            case GPS_COMPARISON:return "Comparer GPS et Google Fused";
            case IMU_TOGGLE:return "Afficher les capteurs IMU";
            case LAYERS:return "Couches de la scène";
            case UNKNOWN_GROUND:return "Estimer les zones de sol inconnues";
            case BIKE_VIEW_HINTS:return "Alertes caméra inclinée / vélo";
            case OVERLAY_RENDER:return "Moteur d'affichage";
            case CAMERA_CAPTURE_PROFILE:return "Profil caméra AR";
            case CAMERA2_MANUAL_LAB:return "Camera2 : shutter et ISO manuels";
            case CUSTOM_VIO:return "VIO custom : Basalt expérimental";
            case VIDEO_TRAINING:return "Enregistrer les données caméra + IMU";
            case COLLECT_ONLY:return "Collecte sans affichage";
            case ARCORE_REPLAY:return "Rejouer un enregistrement ARCore";
            case EXPORT:return "Exporter les sessions";
            case CAPTURE_INFO:return "Contenu des enregistrements";
            case MAP_VIEW:return "Contrôles de la carte 3D";
            case MAP_SAVED:return "Mes cartes sauvegardées";
            case MAP_SETTINGS:return "Réglages des cartes";
            case MAP_PLACE_POI:return "Placer un point d'intérêt";
            case POWER:return "Performance et consommation";
            case SIMULATION:return "Simulation hors ligne";
            case HELP:return "Explications et limites";
            default:throw new IllegalArgumentException("Missing title: "+a);
        }
    }
    static String explanation(SettingsCatalog.Action a) {
        switch(a){
            case GPX_EXTENDED_PREVIEW:return "Afficher futur et passé au-delà du sol détecté. Trait violet pointillé : hauteur supposée, non mesurée. Désactivable.";
            case GPX_AR:return "Importer une trace .gpx, choisir le sens et activer son affichage sur le sol observé. Alignement GPS et caméra nécessaire.";
            case GPS_MANUAL_ALIGN:return "Déplacer visuellement le tracé pour le comparer au chemin réel. Ne change jamais les données GPS ni la distance mesurée.";
            case GPS_ROUTES:return "Suivre un GPX en mode 2D, même sans caméra. Utilise la localisation du téléphone.";
            case PDR:return "Estimer pas et distance avec accéléromètre et capteurs Android. Adapté à la marche/course ; précision non garantie.";
            case COURSES:return "Créer manuellement les allées et la liste d'articles d'un magasin. Aucun suivi intérieur automatique.";
            case RETURN_TRACE:return "Afficher le chemin inverse enregistré dans le repère local. Nécessite une session active et un suivi continu.";
            case GPS_TOGGLE:return "Affiche la vitesse GPS à côté de la vitesse VIO. ±m = précision horizontale estimée, pas la puissance du signal. Option désactivable.";
            case GPS_COMPARISON:return "Comparer localisation Android native et Google Fused. Deux mesures peuvent partager le même GPS et la même erreur.";
            case IMU_TOGGLE:return "Superposer les mesures brutes d'accélération et rotation du téléphone. Diagnostic, pas une vitesse.";
            case LAYERS:return "Choisir les plans détectés, points de scène et trace locale. Change seulement les éléments dessinés.";
            case UNKNOWN_GROUND:return "Si aucun sol n'est détecté, dessiner une surface approximative sous la caméra. Ne corrige pas l'odométrie.";
            case BIKE_VIEW_HINTS:return "Afficher une alerte quand la caméra voit surtout vers le bas. Ne masque pas les pièces mobiles du vélo.";
            case OVERLAY_RENDER:return "Choisir le rendu graphique automatique ou de secours. N'affecte pas l'estimation des poses.";
            case CAMERA_CAPTURE_PROFILE:return "Choisir le profil AR 30 ou 60 FPS, selon les capacités du téléphone. Le réglage ne force pas le shutter et ne prend effet qu’après redémarrage de l’application.";
            case CAMERA2_MANUAL_LAB:return "Piloter vraiment la vitesse d’obturation et la sensibilité ISO avec Camera2, dans un mode séparé où ARCore et le GPX 3D sont suspendus.";
            case CUSTOM_VIO:return "Comparer ARCore à Basalt avec les contrôles caméra optimisés. Calibration usine expérimentale non vérifiée ; enregistrement séparé des poses, IMU, métadonnées caméra et ressources. ARCore est suspendu pendant ce test.";
            case VIDEO_TRAINING:return "Enregistrer explicitement une séquence ARCore pour améliorer les tests. Consomme de l'espace et de la batterie.";
            case COLLECT_ONLY:return "Collecter les mesures avec moins d'affichage. Le service et les autorisations sont gérés séparément.";
            case ARCORE_REPLAY:return "Rejouer une capture MP4 produite par l'API ARCore. Une vidéo MP4 classique n'est pas compatible.";
            case EXPORT:return "Exporter CSV et/ou ZIP contenant éventuellement position, capteurs et images. Vérifier les données privées avant partage.";
            case CAPTURE_INFO:return "Détail des données enregistrées et de leurs limites scientifiques.";
            case MAP_VIEW:return "Accéder aux commandes de la carte locale, captures et vérification des trajectoires.";
            case MAP_SAVED:return "Ouvrir les scènes 3D déjà sauvegardées sur ce téléphone.";
            case MAP_SETTINGS:return "Modifier fréquence des images clés, seuils de mouvement et de confiance.";
            case MAP_PLACE_POI:return "Placer un repère sur un plan détecté. Une surface doit être suivie par ARCore.";
            case POWER:return "Adapter la fréquence de rafraîchissement de l'écran. La capture des poses reste séparée.";
            case SIMULATION:return "Tester une trajectoire et des capteurs synthétiques, sans prétendre mesurer la précision réelle.";
            case HELP:return "Comprendre la différence entre mesure VIO, GPS, PDR, GPX et affichage.";
            default:throw new IllegalArgumentException("Missing description: "+a);
        }
    }
    static boolean toggle(SettingsCatalog.Action a){
        return a==SettingsCatalog.Action.GPX_EXTENDED_PREVIEW ||
               a==SettingsCatalog.Action.GPS_TOGGLE ||
               a==SettingsCatalog.Action.IMU_TOGGLE ||
               a==SettingsCatalog.Action.UNKNOWN_GROUND ||
               a==SettingsCatalog.Action.BIKE_VIEW_HINTS;
    }
    static String note(SettingsCatalog.Section section){
        switch(section){
            case NAVIGATION:return "GPX : une ligne sur caméra n'est fiable qu'après calage. Retour et PDR restent expérimentaux.";
            case LOCATION:return "Vitesse VIO = déplacement caméra/IMU. Vitesse GPS = localisation Android. Elles sont indépendantes.";
            case DISPLAY:return "Un sol détecté aide à afficher le GPX ; il ne filtre pas les points suivis par ARCore.";
            case CAPTURE:return "Attention : vos exports peuvent contenir des coordonnées GPS et des images privées.";
            case MAPS:return "Une carte sauvegardée ne restitue pas à elle seule le suivi interne d'ARCore.";
            case ADVANCED:return "Les tests synthétiques et la CI ne prouvent pas la précision en conditions réelles.";
            default:return "";
        }
    }
    private SettingsPresentation(){}
}
