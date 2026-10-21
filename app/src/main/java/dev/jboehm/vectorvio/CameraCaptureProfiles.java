package dev.jboehm.vectorvio;

/**
 * Safe ARCore camera target selection. Profiles never promise manual Camera2
 * shutter/ISO control. Any switch requires a NEW ARCore Session, because
 * live camera-config swaps invalidate tracking reference/GPX anchoring.
 */
public final class CameraCaptureProfiles {
    public enum Profile { LOW_LIGHT_30, BALANCED_30, FAST_MOTION_60 }
    public static Profile fromPreference(String value) {
        if(value!=null)for(Profile p:Profile.values())if(p.name().equals(value))return p;
        return Profile.BALANCED_30;
    }
    public static int desiredFps(Profile p){return p==Profile.FAST_MOTION_60?60:30;}
    public static boolean isFast(Profile p){return p==Profile.FAST_MOTION_60;}
    public static boolean autoFocus(Profile p){return p==Profile.LOW_LIGHT_30;}
    /** Always silently fall back to 30 fps if target unavailable. */
    public static int actualFps(Profile p,boolean has60){return isFast(p)&&has60?60:30;}
    private CameraCaptureProfiles(){}
}
