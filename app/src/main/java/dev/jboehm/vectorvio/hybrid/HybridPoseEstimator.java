package dev.jboehm.vectorvio.hybrid;

/**
 * Experimental *coordinate-correct* hybrid pose continuity for VECTOR VIO v0.14.
 * Only accepts world-frame metric displacements produced by a calibrated adapter.
 * This class does NOT infer motion from IMU itself and is NOT yet AR renderer input.
 * On outage it reports increasing diagnostic uncertainty and a hard visibility cap.
 */
public final class HybridPoseEstimator {
    public enum State { NO_REFERENCE, TRACKED, DEAD_RECKONING, UNRELIABLE }
    public enum MotionKind { WALKING, CYCLING, UNKNOWN }
    public enum Source { PEDOMETER, NEURAL, EXTERNAL_SENSOR }
    public static final class Prediction {
        /** Coordinates must already be in same metric AR world axes as visual pose. */
        public final long timestampNs;
        public final double dxWorldM,dzWorldM,noiseMeters;
        public final Source source;
        public Prediction(long t,double dx,double dz,double sigma,Source s) {
            timestampNs=t;dxWorldM=dx;dzWorldM=dz;noiseMeters=sigma;source=s;
        }
    }
    public static final class Snapshot {
        public final State state;
        public final double xM,zM,allowanceM,secondsWithoutVision;
        public final long timestampNs;
        public final Source source;
        Snapshot(State s,double x,double z,double u,double duration,long t,Source src){
            state=s;xM=x;zM=z;allowanceM=u;secondsWithoutVision=duration;
            timestampNs=t;source=src;
        }
        public boolean mayDrawIndicativeGpx(){return state==State.DEAD_RECKONING;}
    }
    private static final long MAX_OUTAGE_NS=30_000_000_000L;
    private static final double MAX_INDICATIVE_ALLOWANCE_M=12.0;
    private State state=State.NO_REFERENCE;
    private MotionKind motion=MotionKind.UNKNOWN;
    private Source source;
    private long lastNs=Long.MIN_VALUE,outageNs=Long.MIN_VALUE;
    private double x,z,allowanceM;
    /** Tracking pose, not GPS coordinate. Do not confuse fit uncertainties. */
    public void onVisualPose(long timestampNs,double xWorldM,double zWorldM) {
        if(timestampNs<=0||!Double.isFinite(xWorldM)||!Double.isFinite(zWorldM)
            ||(lastNs!=Long.MIN_VALUE&&timestampNs<=lastNs))return;
        x=xWorldM;z=zWorldM;lastNs=timestampNs;outageNs=Long.MIN_VALUE;
        allowanceM=0;state=State.TRACKED;source=null;
    }
    public void visualLost(long timestampNs,MotionKind intendedMode) {
        if(timestampNs<=0||lastNs==Long.MIN_VALUE||timestampNs<lastNs)return;
        if(state==State.TRACKED) {
            outageNs=timestampNs;motion=intendedMode==null?MotionKind.UNKNOWN:intendedMode;
            state=State.DEAD_RECKONING;allowanceM=1.5;
        }
    }
    /** Reject stale/out-of-frame/neural outlier deltas instead of silently moving GPX. */
    public boolean predict(Prediction p) {
        if(p==null||state!=State.DEAD_RECKONING||p.source==null||p.timestampNs<=lastNs
            ||p.timestampNs<outageNs||p.timestampNs-outageNs>MAX_OUTAGE_NS
            ||!Double.isFinite(p.dxWorldM)||!Double.isFinite(p.dzWorldM)
            ||!Double.isFinite(p.noiseMeters)||p.noiseMeters<0 || p.noiseMeters>20)
            return false;
        double dt=(p.timestampNs-lastNs)/1e9;
        if(dt>2 || dt<=0)return false; // no bridging silent sensor gaps
        double distance=Math.hypot(p.dxWorldM,p.dzWorldM);
        double maxSpeed=motion==MotionKind.WALKING?4:motion==MotionKind.CYCLING?25:5;
        if(distance>maxSpeed*dt+.15)return false;
        x+=p.dxWorldM;z+=p.dzWorldM;lastNs=p.timestampNs;source=p.source;
        // Deliberately diagnostic (NOT statistically calibrated).
        allowanceM+=Math.max(.02,.15*distance)+Math.max(0,p.noiseMeters);
        if(allowanceM>MAX_INDICATIVE_ALLOWANCE_M)state=State.UNRELIABLE;
        return true;
    }
    public Snapshot snapshot(long nowNs) {
        if(state==State.NO_REFERENCE)return new Snapshot(State.NO_REFERENCE,Double.NaN,Double.NaN,
            Double.POSITIVE_INFINITY,0,0,null);
        double duration=outageNs==Long.MIN_VALUE?0:Math.max(0,(nowNs-outageNs)/1e9);
        double allowance=outageNs==Long.MIN_VALUE?0:allowanceM+.2*duration;
        State shown=state;
        if(outageNs!=Long.MIN_VALUE&&(nowNs<lastNs||nowNs-outageNs>MAX_OUTAGE_NS
                ||nowNs-lastNs>2_000_000_000L||allowance>MAX_INDICATIVE_ALLOWANCE_M))
            shown=State.UNRELIABLE;
        return new Snapshot(shown,x,z,allowance,duration,lastNs,source);
    }
    public void reset() {
        state=State.NO_REFERENCE;source=null;lastNs=outageNs=Long.MIN_VALUE;allowanceM=0;
    }
}
