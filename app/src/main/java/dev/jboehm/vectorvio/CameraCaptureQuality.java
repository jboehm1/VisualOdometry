package dev.jboehm.vectorvio;

/**
 * Diagnostic classifier based on ACTUAL per-frame Camera2 metadata returned by ARCore.
 * Heuristics are not a calibrated image-quality score and never override ARCore AE.
 * All measurements are from the SAME sensor/readout; gyro is device angular speed.
 */
public final class CameraCaptureQuality {
    public enum State { UNKNOWN, TRACKING_LOST, NORMAL, HIGH_ISO, MOTION_BLUR_RISK, DARK_AND_BLURRED }
    public static final class Assessment {
        public final State state;
        public final double exposureMs, angularBlurDeg;
        public final int iso;
        Assessment(State s,double ms,int iso,double blur) {
            state=s;exposureMs=ms;this.iso=iso;angularBlurDeg=blur;
        }
        public String label() {
            switch(state) {
                case MOTION_BLUR_RISK:return "MOTION BLUR?";
                case DARK_AND_BLURRED:return "DARK + BLUR?";
                case HIGH_ISO:return "HIGH ISO";
                case TRACKING_LOST:return "AR TRACK LOST";
                case NORMAL:return "EXPOSURE OK?";
                default:return "CAMERA METADATA N/A";
            }
        }
    }
    private CameraCaptureQuality() {}
    /**
     * Approximate angular smear = |gyroscope| * exposure_time. This does not
     * account for translation, scene depth, stabilization, rolling shutter or
     * camera/gyro time offset, and must not be presented as image ground truth.
     */
    public static Assessment classify(long exposureNs,int iso,double gyroRadPerSec,
                                      boolean tracking) {
        if(exposureNs<=0||exposureNs>1_000_000_000L||iso<=0||iso>102400
            ||!Double.isFinite(gyroRadPerSec)||gyroRadPerSec<0)
            return new Assessment(tracking?State.UNKNOWN:State.TRACKING_LOST,
                Double.NaN,0,Double.NaN);
        double exposureMs=exposureNs/1e6;
        double blurDeg=Math.toDegrees(gyroRadPerSec*exposureNs/1e9);
        if(!tracking)return new Assessment(State.TRACKING_LOST,exposureMs,iso,blurDeg);
        // Deliberately CONSERVATIVE warnings; not universal device thresholds.
        boolean blur=blurDeg>=.4;
        boolean dark=iso>=1600 || exposureMs>=25;
        return new Assessment(blur && dark?State.DARK_AND_BLURRED:
            blur?State.MOTION_BLUR_RISK:dark?State.HIGH_ISO:State.NORMAL,exposureMs,iso,blurDeg);
    }
}
