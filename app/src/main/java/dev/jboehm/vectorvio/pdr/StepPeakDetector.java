package dev.jboehm.vectorvio.pdr;

import java.util.ArrayDeque;

/**
 * Experimental acceleration fallback, independent of phone orientation.
 * Hardware STEP_DETECTOR remains preferred by the Android adapter.
 * The consensus policy was frozen before its SIMUL reserved-subject check;
 * this does not establish metre-scale accuracy or S21 field validation.
 */
public final class StepPeakDetector {
    public enum Mode { CONSENSUS_130, LEGACY_RESEARCH }
    public static final String ALGORITHM_ID="JAVA_CAUSAL_CONSENSUS130_V2";
    private final Mode mode;
    private final LegacyPeaks legacy=new LegacyPeaks();
    private final AdaptivePeaks adaptive=new AdaptivePeaks(true);
    private final Arbitration arbitration=new Arbitration(1.3);
    private long last=-1;

    public StepPeakDetector() { this(Mode.CONSENSUS_130); }
    /** Explicit historical baseline for research; no runtime UI switch. */
    public StepPeakDetector(Mode mode) {
        if(mode==null)throw new IllegalArgumentException("mode required");
        this.mode=mode;
    }
    public String algorithmId() {
        return mode==Mode.CONSENSUS_130?ALGORITHM_ID:"JAVA_THRESHOLD_RHYTHM_V1";
    }
    public void reset() {
        legacy.reset();adaptive.reset();arbitration.reset();last=-1;
    }
    public boolean accept(long t,double x,double y,double z) {
        if(t<=0 || t<=last || !Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(z) || Math.hypot(Math.hypot(x,y),z)>80)return false;
        last=t;
        boolean base=legacy.accept(t,x,y,z);
        if(mode==Mode.LEGACY_RESEARCH)return base;
        return arbitration.accept(t,base,adaptive.accept(t,x,y,z));
    }

    public static final class LegacyPeaks {
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

    public static final class AdaptivePeaks {
        private final boolean adaptive;
        private boolean initialized, rising;
        private double gravity, filtered, previous, variance;
        private long last = -1, first = -1, candidate = -1, crossing = -1, event = -1;

        public AdaptivePeaks(boolean adaptive) { this.adaptive = adaptive; }

        public void reset() {
            initialized = rising = false;
            gravity = filtered = previous = variance = 0;
            last = first = candidate = crossing = event = -1;
        }

        /** Observed threshold-crossing time, available only after peak confirmation. */
        public long eventTimestampNs() { return event; }

        public boolean accept(long time, double x, double y, double z) {
            if (time <= 0 || time <= last || !Double.isFinite(x)
                    || !Double.isFinite(y) || !Double.isFinite(z)) return false;
            double magnitude = Math.hypot(Math.hypot(x, y), z);
            if (!Double.isFinite(magnitude) || magnitude > 80) return false;
            if (!initialized || time - last > 1_500_000_000L) {
                reset(); initialized = true;
                gravity = magnitude; last = first = time;
                return false;
            }
            long dt = time - last; last = time;
            gravity += (1-Math.exp(-dt/300_000_000.0))*(magnitude-gravity);
            double high = magnitude-gravity;
            filtered += (1-Math.exp(-dt/20_000_000.0))*(high-filtered);
            variance += (1-Math.exp(-dt/2_000_000_000.0))*(high*high-variance);
            double threshold = adaptive ? Math.max(.8, Math.min(2., .65*Math.sqrt(variance))) : 1.15;
            if(filtered>previous && filtered>threshold && crossing<0)crossing=time;
            boolean peak = rising && filtered < previous && previous > threshold;
            long observedCrossing=crossing;
            rising = filtered > previous;
            if(!rising)crossing=-1;
            previous = filtered;
            if (!peak || time-first < 200_000_000L) return false;
            if (candidate < 0) { candidate = time; return false; }
            long period = time-candidate;
            if (period < 300_000_000L) return false;
            candidate = time;
            // Require two physically plausible peaks, but no inferred fixed rhythm:
            // changes of gait and asymmetric hand motion need not have equal periods.
            if(period>1_250_000_000L)return false;
            event=observedCrossing>0?observedCrossing:time;
            return true;
        }
    }

    public static final class Arbitration {
        private final double enterRatio;
        public Arbitration() { this(1.15); }
        public Arbitration(double enterRatio) {
            if(!Double.isFinite(enterRatio) || enterRatio<1.15 || enterRatio>1.5)
                throw new IllegalArgumentException("ratio outside prespecified research grid");
            this.enterRatio=enterRatio;
        }
        private final ArrayDeque<Long> baseline=new ArrayDeque<>(), filtered=new ArrayDeque<>();
        private long last=-1, emitted=-1, evidenceSince=-1;
        private boolean useFiltered;
        private int switches;

        public void reset() {
            baseline.clear();filtered.clear();last=emitted=evidenceSince=-1;
            useFiltered=false;switches=0;
        }

        public int switches() { return switches; }

        public boolean accept(long t, boolean base, boolean candidate) {
            if(t<=0 || t<=last)return false;
            if(last>0 && t-last>1_500_000_000L)reset();
            last=t;
            if(base)baseline.add(t);
            if(candidate)filtered.add(t);
            while(!baseline.isEmpty() && baseline.peekFirst()<t-8_000_000_000L)baseline.remove();
            while(!filtered.isEmpty() && filtered.peekFirst()<t-8_000_000_000L)filtered.remove();
            boolean supported=baseline.size()>=8 && filtered.size()>=8;
            double ratio=filtered.isEmpty()?1.:(double)baseline.size()/filtered.size();
            boolean change=supported && (useFiltered ? ratio<=1.05 : ratio>=enterRatio);
            if(change) {
                if(evidenceSince<0)evidenceSince=t;
                long duration=useFiltered?2_000_000_000L:1_000_000_000L;
                if(t-evidenceSince>=duration) {
                    useFiltered=!useFiltered;switches++;evidenceSince=-1;
                }
            } else evidenceSince=-1;
            boolean chosen=useFiltered?candidate:base;
            if(!chosen || (emitted>0 && t-emitted<270_000_000L))return false;
            emitted=t;return true;
        }
    }

}
