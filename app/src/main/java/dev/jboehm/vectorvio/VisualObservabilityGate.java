package dev.jboehm.vectorvio;

/**
 * Fail-closed VISUAL OBSERVABILITY diagnostic (not a learned trajectory estimator).
 *
 * A gyroscope may propagate an orientation for a short interval; acceleration
 * integration alone cannot produce a reliable translation, especially on a bike.
 * No state here licenses evacuation or automatic location-based decisions.
 *
 * This gate never modifies ARCore pose, odometer, geofusion, GPX or map state.
 * Numeric uncertainty is intentionally NOT fabricated from heuristic quality.
 */
public final class VisualObservabilityGate {
    public enum State {
        VISUAL_OBSERVED, VISUAL_CAUTION, ORIENTATION_ONLY, POSITION_UNKNOWN
    }
    public static final class Decision {
        public final State state;
        /** True if and only if ARCore currently reports tracking. Not metric ground truth. */
        public final boolean arcorePoseAvailable;
        /** Never means that translation may be extrapolated from the IMU. */
        public final boolean translationFromInertial;
        public final long visualAgeMs;
        public final String reason;
        private Decision(State s,boolean tracked,long age,String why){
            state=s;arcorePoseAvailable=tracked;translationFromInertial=false;
            visualAgeMs=age;reason=why;
        }
    }
    private static final long ORIENTATION_HORIZON_NS=1_500_000_000L;
    private long lastObservedNs=-1, lastClockNs=-1, lastFrameNs=-1;
    private int consecutiveGood;
    private State lastState=State.POSITION_UNKNOWN;

    public synchronized void reset(){
        lastObservedNs=lastClockNs=lastFrameNs=-1;
        consecutiveGood=0;lastState=State.POSITION_UNKNOWN;
    }

    /**
     * @param nowNs monotonic Android elapsedRealtimeNanos (not a wall clock)
     * @param frameNs ARCore frame timestamp; used ONLY for duplicate detection
     * @param tracking current ARCore TrackingState.TRACKING
     * @param imuFresh both accelerometer and gyroscope currently fresh
     * @param optical measured exposure/ISO heuristic; null if unavailable
     * @param lowTexture null if no genuine CPU camera thumbnail was sampled
     */
    public synchronized Decision update(long nowNs,long frameNs,boolean tracking,
            boolean imuFresh,CameraCaptureQuality.State optical,Boolean lowTexture){
        if(nowNs<=0 || frameNs<=0 || (lastClockNs>=0 && nowNs<=lastClockNs) ||
                (lastFrameNs>=0 && frameNs<=lastFrameNs)){
            // Replayed frames or bad clocks must not refresh the visual anchor.
            consecutiveGood=0;lastState=State.POSITION_UNKNOWN;
            return new Decision(State.POSITION_UNKNOWN,false,Long.MAX_VALUE,"DUPLICATE_OR_INVALID_CLOCK");
        }
        lastClockNs=nowNs;lastFrameNs=frameNs;
        if(tracking){
            lastObservedNs=nowNs;
            boolean poorOptical=optical==CameraCaptureQuality.State.MOTION_BLUR_RISK ||
                optical==CameraCaptureQuality.State.DARK_AND_BLURRED;
            boolean poorTexture=Boolean.TRUE.equals(lowTexture);
            if(poorOptical||poorTexture||!imuFresh){
                consecutiveGood=0;lastState=State.VISUAL_CAUTION;
                return new Decision(State.VISUAL_CAUTION,true,0,
                    !imuFresh?"IMU_STALE":poorTexture?"LOW_TEXTURE":"BLUR_RISK");
            }
            consecutiveGood++;
            if(consecutiveGood<2){
                lastState=State.VISUAL_CAUTION;
                return new Decision(State.VISUAL_CAUTION,true,0,"REACQUIRING");
            }
            lastState=State.VISUAL_OBSERVED;
            return new Decision(State.VISUAL_OBSERVED,true,0,"ARCORE_TRACKING_NOT_GROUND_TRUTH");
        }
        consecutiveGood=0;
        long age=lastObservedNs<0?Long.MAX_VALUE:nowNs-lastObservedNs;
        if(imuFresh && age>=0 && age<=ORIENTATION_HORIZON_NS){
            lastState=State.ORIENTATION_ONLY;
            return new Decision(State.ORIENTATION_ONLY,false,age/1_000_000,"NO_TRANSLATION_FROM_IMU");
        }
        lastState=State.POSITION_UNKNOWN;
        return new Decision(State.POSITION_UNKNOWN,false,age==Long.MAX_VALUE?Long.MAX_VALUE:Math.max(0,age/1_000_000),
            "VISION_LOST_OR_IMU_STALE");
    }
}
