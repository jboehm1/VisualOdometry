package dev.jboehm.vectorvio.vision;

/**
 * Explicitly event-triggered vision budget: does not start/own the camera.
 * Caller handles camera permission, ownership, thermal limits and actual model.
 */
public final class VisionTriggerPolicy {
    public enum Reason { NONE, GPX_AMBIGUOUS, UPCOMING_TURN, USER_REQUEST }
    public enum Decision { SKIP, RUN, CAMERA_UNAVAILABLE, COOL_DOWN }
    private static final long COOLDOWN_NS=4_000_000_000L;
    private long lastRunNs=Long.MIN_VALUE;
    private int runCount;
    private long windowStart;
    /** Hard cap 10 requests per minute, in addition to 4s spacing. */
    public synchronized Decision request(long elapsedRealtimeNs,boolean cameraAvailable,
                                         Reason reason,boolean thermalThrottle){
        if(reason==Reason.NONE||elapsedRealtimeNs<=0)return Decision.SKIP;
        if(!cameraAvailable||thermalThrottle)return Decision.CAMERA_UNAVAILABLE;
        if(lastRunNs!=Long.MIN_VALUE && elapsedRealtimeNs-lastRunNs<COOLDOWN_NS)return Decision.COOL_DOWN;
        if(windowStart==0||elapsedRealtimeNs-windowStart>=60_000_000_000L){windowStart=elapsedRealtimeNs;runCount=0;}
        if(runCount>=10)return Decision.COOL_DOWN;
        lastRunNs=elapsedRealtimeNs;runCount++;return Decision.RUN;
    }
    public synchronized void reset(){lastRunNs=Long.MIN_VALUE;runCount=0;windowStart=0;}
}
