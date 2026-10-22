package dev.jboehm.vectorvio;

/**
 * Pure-Java fail-closed camera/IMU startup guard.
 * Only validates availability and freshness. It cannot certify camera-IMU
 * extrinsics, metric precision, JNI stability or a physical Galaxy S21.
 */
public final class BasaltStartSafety {
    public enum Reason {
        READY, CAMERA_NOT_OPEN, CAMERA_STREAM_NOT_READY, NO_FACTORY_BOOTSTRAP,
        IMU_NOT_AVAILABLE, CAMERA_FRAMES_STALE, IMU_STALE,
        NATIVE_LIBRARY_UNAVAILABLE, PREVIOUS_SESSION_CLOSING,
        ALREADY_RECORDING
    }
    public static final class Inputs {
        public boolean foreground,cameraOpened,streamConfigured,bootstrapAvailable,
            gyroAvailable,accelAvailable,nativeLibraryAvailable,closing,recording;
        public long nowNs,lastCaptureReceivedNs,lastImageReceivedNs,lastGyroReceivedNs,lastAccelReceivedNs;
    }
    private static final long MAX_CAMERA_AGE=2_000_000_000L;
    private static final long MAX_IMU_AGE=1_000_000_000L;
    private BasaltStartSafety(){}
    private static boolean fresh(long now,long t,long horizon) {
        return now>0 && t>0 && t<=now && now-t<=horizon;
    }
    public static Reason evaluate(Inputs in) {
        if(in==null||!in.foreground||!in.cameraOpened)return Reason.CAMERA_NOT_OPEN;
        if(!in.streamConfigured)return Reason.CAMERA_STREAM_NOT_READY;
        if(!in.bootstrapAvailable)return Reason.NO_FACTORY_BOOTSTRAP;
        if(!in.accelAvailable||!in.gyroAvailable)return Reason.IMU_NOT_AVAILABLE;
        if(in.closing)return Reason.PREVIOUS_SESSION_CLOSING;
        if(in.recording)return Reason.ALREADY_RECORDING;
        if(!in.nativeLibraryAvailable)return Reason.NATIVE_LIBRARY_UNAVAILABLE;
        if(!fresh(in.nowNs,in.lastCaptureReceivedNs,MAX_CAMERA_AGE)||
           !fresh(in.nowNs,in.lastImageReceivedNs,MAX_CAMERA_AGE))return Reason.CAMERA_FRAMES_STALE;
        if(!fresh(in.nowNs,in.lastGyroReceivedNs,MAX_IMU_AGE)||
           !fresh(in.nowNs,in.lastAccelReceivedNs,MAX_IMU_AGE))return Reason.IMU_STALE;
        return Reason.READY;
    }
    public static String explanation(Reason reason) {
        switch(reason) {
            case READY:return "Caméra + IMU livrent des données récentes. Démarrage expérimental.";
            case CAMERA_NOT_OPEN:return "Caméra non ouverte. Reste en mode caméra, sans suivi.";
            case CAMERA_STREAM_NOT_READY:return "Flux Camera2 non configuré. Réessayez après l'apparition de l'image.";
            case NO_FACTORY_BOOTSTRAP:return "Calibration Basalt absente. Lancez d'abord ARCore en mise au point fixe et attendez TRACKING.";
            case IMU_NOT_AVAILABLE:return "Gyroscope ou accéléromètre indisponible. VIO refusée.";
            case CAMERA_FRAMES_STALE:return "Aucune image YUV et métadonnée Camera2 récentes. VIO refusée.";
            case IMU_STALE:return "Capteurs IMU sans mesures récentes. VIO refusée.";
            case NATIVE_LIBRARY_UNAVAILABLE:return "Bibliothèque native Basalt indisponible. VIO refusée.";
            case PREVIOUS_SESSION_CLOSING:return "Arrêt natif précédent en cours. Démarrage refusé.";
            case ALREADY_RECORDING:return "Une session Basalt est déjà active.";
            default:return "Impossible de vérifier le démarrage Basalt.";
        }
    }
}
