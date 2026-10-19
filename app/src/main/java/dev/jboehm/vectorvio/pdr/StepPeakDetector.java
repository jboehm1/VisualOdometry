package dev.jboehm.vectorvio.pdr;

/**
 * Experimental magnitude-only step detector for devices without STEP_DETECTOR.
 * Reject isolated shocks until a second plausibly timed gait peak arrives.
 * No claim of calibrated step recall/precision: placement and motion can fool it.
 */
public final class StepPeakDetector {
    private static final long MIN_PEAK_NS=300_000_000L;
    private static final long MAX_PEAK_NS=1_250_000_000L;
    private static final long RESET_GAP_NS=1_500_000_000L;
    private static final long WARMUP_NS=200_000_000L;
    private static final double FILTER_TAU_NS=300_000_000.0;
    private double slow;
    private boolean above, initialized, rhythmic;
    private double previousPeriodNs=Double.NaN;
    private long firstNs=Long.MIN_VALUE, lastNs=Long.MIN_VALUE, lastCandidateNs=Long.MIN_VALUE;

    public void reset(){
        slow=0;above=false;initialized=false;rhythmic=false;
        previousPeriodNs=Double.NaN;
        firstNs=lastNs=lastCandidateNs=Long.MIN_VALUE;
    }
    public boolean accept(long timeNs,double x,double y,double z){
        if(timeNs<=0||timeNs<=lastNs || !Double.isFinite(x) ||
           !Double.isFinite(y)||!Double.isFinite(z))return false;
        double magnitude=Math.sqrt(x*x+y*y+z*z);
        if(!Double.isFinite(magnitude)||magnitude>80)return false;
        if(!initialized || (lastNs!=Long.MIN_VALUE && timeNs-lastNs>RESET_GAP_NS)){
            slow=magnitude;above=false;initialized=true;rhythmic=false;
            previousPeriodNs=Double.NaN;lastCandidateNs=Long.MIN_VALUE;
            firstNs=lastNs=timeNs;
            return false;
        }
        long dt=timeNs-lastNs;lastNs=timeNs;
        // Time-based filter, rather than per-sample 0.92, for 25/50/100 Hz IMUs.
        double alpha=1.0-Math.exp(-dt/FILTER_TAU_NS);
        slow+=alpha*(magnitude-slow);
        double high=magnitude-slow;
        if(high<.25){above=false;return false;}
        if(above||high<=1.15)return false;
        above=true;
        if(timeNs-firstNs<WARMUP_NS)return false;
        if(lastCandidateNs==Long.MIN_VALUE){
            lastCandidateNs=timeNs;return false;
        }
        long period=timeNs-lastCandidateNs;
        if(period<MIN_PEAK_NS)return false; // likely a secondary bump
        lastCandidateNs=timeNs;
        if(period>MAX_PEAK_NS){
            rhythmic=false;previousPeriodNs=Double.NaN;return false;
        }
        if(rhythmic && (period<.55*previousPeriodNs || period>1.8*previousPeriodNs)){
            rhythmic=false;previousPeriodNs=Double.NaN;
            return false; // re-acquire gait rather than interpreting a shake as travel
        }
        rhythmic=true;
        previousPeriodNs=Double.isFinite(previousPeriodNs)
            ?.75*previousPeriodNs+.25*period:period;
        return true;
    }
}
