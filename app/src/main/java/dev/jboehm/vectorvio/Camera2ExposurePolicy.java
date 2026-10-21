package dev.jboehm.vectorvio;

/** Pure shutter / ISO range clipping for standalone Camera2 manual experiment. */
public final class Camera2ExposurePolicy {
    public static final long MIN_SHUTTER_NS=125_000L, MAX_SHUTTER_NS=100_000_000L;
    public static long clampShutter(long requested,long min,long max,long frameDuration) {
        if(min<=0||max<min||frameDuration<min)throw new IllegalArgumentException("Invalid camera limits");
        return Math.max(min,Math.min(requested,Math.min(max,frameDuration)));
    }
    public static int clampIso(int requested,int min,int max) {
        if(min<=0||max<min)throw new IllegalArgumentException("Invalid ISO limits");
        return Math.max(min,Math.min(requested,max));
    }
    public static long durationFor30Fps(long minDuration,long maxExposure) {
        return Math.max(minDuration,Math.min(33_333_333L,Math.max(1,maxExposure)));
    }
    private Camera2ExposurePolicy(){}
}
