package dev.jboehm.vectorvio.hybrid;

/**
 * Lightweight TLIO-INSPIRED position fusion in an already common metric frame.
 * Not TLIO-trained neural weights, not a 15-state inertial EKF.
 * Neural/PDR incremental deltas are predictions; VIO/GNSS are separate
 * observations. XY covariance diagonal is intentionally CONSERVATIVE and
 * only diagnostic unless innovation/noise calibration is performed.
 *
 * Does NOT project latitude/longitude to AR world, deduce walking direction,
 * or smooth discontinuous ARCore reference resets.
 */
public final class UncertaintyFusion2D {
    public enum State { UNINITIALIZED, VISUAL, INERTIAL, UNRELIABLE }
    public enum MeasurementSource { VIO, GNSS_ALIGNED }
    public static final class Estimate {
        public final State state;
        public final long timeNs;
        public final double worldX,worldZ,sigmaMeters,visionGapSeconds;
        Estimate(State s,long t,double x,double z,double uncertainty,double gap){
            state=s;timeNs=t;worldX=x;worldZ=z;sigmaMeters=uncertainty;visionGapSeconds=gap;
        }
        public boolean mayRender3d(){return state==State.VISUAL || state==State.INERTIAL;}
    }
    private double x,z,varX,varZ;
    private long lastTimeNs=Long.MIN_VALUE,lastVisualNs=Long.MIN_VALUE;
    private State state=State.UNINITIALIZED;
    private int rejected;
    public void reset(){lastTimeNs=lastVisualNs=Long.MIN_VALUE;state=State.UNINITIALIZED;rejected=0;}
    public int rejected(){return rejected;}
    public State state(){return state;}
    public void initialize(long t,double xM,double zM,double accuracyM){
        if(t<=0||!Double.isFinite(xM)||!Double.isFinite(zM)||
            !Double.isFinite(accuracyM)||accuracyM<=0 || accuracyM>50)
            throw new IllegalArgumentException("Invalid initial VIO world pose");
        x=xM;z=zM;varX=varZ=Math.max(.05,accuracyM*accuracyM);
        lastTimeNs=lastVisualNs=t;state=State.VISUAL;
    }
    /** An externally calibrated WORLD frame displacement, not DEVICE XY. */
    public boolean predict(long t,double dxWorldM,double dzWorldM,double noiseM){
        if(state==State.UNINITIALIZED||state==State.UNRELIABLE||t<=lastTimeNs||
            !Double.isFinite(dxWorldM)||!Double.isFinite(dzWorldM)||
            !Double.isFinite(noiseM)||noiseM<0||noiseM>20){
            rejected++;return false;
        }
        double dt=(t-lastTimeNs)*1e-9;
        if(dt<=0||dt>2 || Math.hypot(dxWorldM,dzWorldM)>25*dt+.1){
            rejected++;return false;
        }
        // Neural scale may be uncalibrated; floor and growth prevent false trust.
        double floor=.4*dt+.15*Math.hypot(dxWorldM,dzWorldM);
        double q=Math.max(.15,noiseM+floor);
        x+=dxWorldM;z+=dzWorldM;varX+=q*q;varZ+=q*q;
        lastTimeNs=t;state=State.INERTIAL;
        if((t-lastVisualNs)>30_000_000_000L || varX+varZ>144)
            state=State.UNRELIABLE;
        return true;
    }
    /** Absolute measurement already transformed into valid same AR world frame. */
    public boolean update(long t,double measuredX,double measuredZ,double accuracyM,
                          MeasurementSource source) {
        if(source==null||t<=0||!Double.isFinite(measuredX)||!Double.isFinite(measuredZ)||
            !Double.isFinite(accuracyM)||accuracyM<=0||accuracyM>50 ||
            (lastTimeNs!=Long.MIN_VALUE&&t<lastTimeNs)){
            rejected++;return false;
        }
        if(state==State.UNINITIALIZED){
            if(source!=MeasurementSource.VIO){rejected++;return false;}
            initialize(t,measuredX,measuredZ,accuracyM);return true;
        }
        double r=Math.pow(Math.max(source==MeasurementSource.GNSS_ALIGNED?3.0:.15,accuracyM),2);
        double dx=measuredX-x,dz=measuredZ-z;
        double nis=dx*dx/(varX+r)+dz*dz/(varZ+r);
        // 2D chi-square 99.9 percentile 13.82. Outliers cannot teleport GPX.
        if(!Double.isFinite(nis)||nis>13.82){rejected++;return false;}
        double kx=varX/(varX+r),kz=varZ/(varZ+r);
        x+=kx*dx;z+=kz*dz;
        varX=Math.max(.0025,(1-kx)*varX);varZ=Math.max(.0025,(1-kz)*varZ);
        lastTimeNs=t;
        if(source==MeasurementSource.VIO){lastVisualNs=t;state=State.VISUAL;}
        else if(state==State.UNRELIABLE){state=State.UNRELIABLE;}
        return true;
    }
    public Estimate estimate(long nowNs){
        if(state==State.UNINITIALIZED)
            return new Estimate(state,0,Double.NaN,Double.NaN,Double.POSITIVE_INFINITY,0);
        double gap=nowNs>=lastVisualNs?(nowNs-lastVisualNs)*1e-9:0;
        State s=state;
        if(gap>30 || nowNs-lastTimeNs>2_000_000_000L || varX+varZ>144)
            s=State.UNRELIABLE;
        return new Estimate(s,lastTimeNs,x,z,Math.sqrt(varX+varZ),gap);
    }
}
