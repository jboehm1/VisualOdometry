package dev.jboehm.vectorvio.pdr;

/**
 * Low-cost, orientation-independent step fallback using accelerometer magnitude.
 * This is a conservative heuristic, NOT a validated ML step model. It should be
 * compared with STEP_DETECTOR and labeled "estimated" in the UI.
 */
public final class StepPeakDetector {
    private static final long REFRACTORY_NS=300_000_000L;
    private double slow=9.81;
    private boolean above;
    private long lastNs=Long.MIN_VALUE, lastPeakNs=Long.MIN_VALUE;
    public void reset(){slow=9.81;above=false;lastNs=Long.MIN_VALUE;lastPeakNs=Long.MIN_VALUE;}
    public boolean accept(long timeNs,double x,double y,double z) {
        if(timeNs<=0||timeNs<=lastNs||!Double.isFinite(x)||!Double.isFinite(y)
                ||!Double.isFinite(z)) return false;
        double magnitude=Math.sqrt(x*x+y*y+z*z);
        if(magnitude>80) return false;
        if(lastNs!=Long.MIN_VALUE && timeNs-lastNs>2_000_000_000L){
            slow=magnitude;above=false;
        }
        lastNs=timeNs;
        slow=.92*slow+.08*magnitude;
        double high=magnitude-slow;
        if(high<.25){above=false;return false;}
        if(!above && high>1.15){
            above=true;
            if(lastPeakNs==Long.MIN_VALUE || timeNs-lastPeakNs>=REFRACTORY_NS){
                lastPeakNs=timeNs;return true;
            }
        }
        return false;
    }
}
