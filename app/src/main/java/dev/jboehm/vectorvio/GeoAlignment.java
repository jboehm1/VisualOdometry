package dev.jboehm.vectorvio;

import java.util.ArrayList;
import java.util.List;

/**
 * Experimental, pure-Java 2D rigid alignment of timestamp-paired metric VIO poses
 * to WGS84 GPS fixes. This estimates one yaw and one translation PER CONTINUOUS
 * tracking segment. It neither changes the odometer nor certifies GPX position.
 *
 * The caller must synchronize GPS fixes and VIO poses (same elapsedRealtime
 * clock, bounded interpolation) and reject stale fixes. Never pair the latest
 * camera pose to an arbitrarily old GPS fix.
 */
public final class GeoAlignment {
    public enum State { NEED_SAMPLES, NEED_BASELINE, INCONSISTENT, CANDIDATE }
    private static final double R = 6371008.8;
    private static final int MAX_SAMPLES = 64;
    private static final double MIN_BASELINE_M = 15;
    private static final double MAX_FIX_ACCURACY_M = 50;
    private final List<Sample> samples = new ArrayList<>();
    private int segment = Integer.MIN_VALUE;
    private long lastNs = Long.MIN_VALUE;
    private double lat0, lon0;

    private static final class Sample {
        final double x,z,e,n,accuracy;
        Sample(double x,double z,double e,double n,double accuracy) {
            this.x=x; this.z=z; this.e=e; this.n=n; this.accuracy=accuracy;
        }
    }

    public static final class Result {
        public final State state;
        public final int samples, segment, inliers;
        public final double yawRadians, eastOffsetMeters, northOffsetMeters;
        public final double baselineMeters, residualRmsMeters, typicalGpsAccuracyMeters;
        private final double lat0, lon0;
        private Result(State state,int samples,int segment,double yaw,double east,double north,
                       double baseline,double rms,double accuracy,int inliers,double lat0,double lon0) {
            this.state=state;this.samples=samples;this.segment=segment;this.inliers=inliers;
            this.yawRadians=yaw;this.eastOffsetMeters=east;this.northOffsetMeters=north;
            this.baselineMeters=baseline;this.residualRmsMeters=rms;
            this.typicalGpsAccuracyMeters=accuracy;this.lat0=lat0;this.lon0=lon0;
        }
        /** Coordinate is only available for a candidate; no global accuracy guarantee. */
        public double[] project(double x,double z) {
            if(state!=State.CANDIDATE || !Double.isFinite(x) || !Double.isFinite(z))return null;
            double east=Math.cos(yawRadians)*x-Math.sin(yawRadians)*z+eastOffsetMeters;
            double north=Math.sin(yawRadians)*x+Math.cos(yawRadians)*z+northOffsetMeters;
            double lat=lat0+Math.toDegrees(north/R);
            double lon=lon0+Math.toDegrees(east/(R*Math.cos(Math.toRadians(lat0))));
            return new double[]{lat,wrapLongitude(lon)};
        }
        /** Inverse of project; ARCore horizontal x/z, not east/north axes. */
        public double[] unproject(double latitude,double longitude) {
            if(state!=State.CANDIDATE || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                    || Math.abs(latitude)>85 || Math.abs(longitude)>180)return null;
            double e=Math.toRadians(wrapLongitude(longitude-lon0))*R*Math.cos(Math.toRadians(lat0))-eastOffsetMeters;
            double n=Math.toRadians(latitude-lat0)*R-northOffsetMeters;
            double c=Math.cos(yawRadians),s=Math.sin(yawRadians);
            return new double[]{c*e+s*n,-s*e+c*n};
        }

    }

    private static double wrapLongitude(double lon){return ((lon+540)%360)-180;}

    public synchronized void reset(){ samples.clear();segment=Integer.MIN_VALUE;lastNs=Long.MIN_VALUE;lat0=lon0=0; }

    /** Returns false for invalid/duplicate fixes; a segment change resets prior alignment. */
    public synchronized boolean add(int trackingSegment,long elapsedRealtimeNs,
                                    double vioX,double vioZ,double latitude,double longitude,
                                    double horizontalAccuracyMeters) {
        if(trackingSegment<=0 || elapsedRealtimeNs<=0 ||
           !Double.isFinite(vioX)||!Double.isFinite(vioZ)||
           !Double.isFinite(latitude)||!Double.isFinite(longitude)||
           Math.abs(latitude)>85 || Math.abs(longitude)>180 ||
           !Double.isFinite(horizontalAccuracyMeters)||horizontalAccuracyMeters<=0 ||
           horizontalAccuracyMeters>MAX_FIX_ACCURACY_M) return false;
        if(segment!=trackingSegment){ reset();segment=trackingSegment; }
        if(elapsedRealtimeNs<=lastNs)return false;
        if(samples.isEmpty()){lat0=latitude;lon0=longitude;}
        double north=Math.toRadians(latitude-lat0)*R;
        double east=Math.toRadians(wrapLongitude(longitude-lon0))*R*Math.cos(Math.toRadians(lat0));
        if(!Double.isFinite(east)||!Double.isFinite(north))return false;
        samples.add(new Sample(vioX,vioZ,east,north,horizontalAccuracyMeters));
        if(samples.size()>MAX_SAMPLES)samples.remove(0);
        lastNs=elapsedRealtimeNs;
        return true;
    }

    /** IRLS weighted Procrustes, robust to isolated GPS spikes but not common bias. */
    public synchronized Result estimate(){
        int count=samples.size();
        if(count<4)return result(State.NEED_SAMPLES,0,0,0,0,0,0,0);
        double baseline=0,acc=0;
        for(int i=0;i<count;i++){
            Sample a=samples.get(i);acc+=a.accuracy;
            for(int j=i+1;j<count;j++){
                Sample b=samples.get(j);
                baseline=Math.max(baseline,Math.hypot(a.x-b.x,a.z-b.z));
            }
        }
        acc/=count;
        // If the claimed GPS uncertainty is of the same order as movement,
        // yaw is weakly observable. Reject an apparently precise candidate.
        // This cannot detect common-mode multipath bias, only poor geometry.
        if(baseline<Math.max(MIN_BASELINE_M,4*acc))
            return result(State.NEED_BASELINE,0,0,0,baseline,0,acc,0);
        double[] weights=new double[count];
        for(int i=0;i<count;i++)weights[i]=1/Math.pow(Math.max(2,samples.get(i).accuracy),2);
        double theta=0, tx=0,ty=0,rms=0;
        for(int iter=0;iter<7;iter++){
            double sw=0,mx=0,mz=0,me=0,mn=0;
            for(int i=0;i<count;i++){
                Sample s=samples.get(i);double w=weights[i];
                sw+=w;mx+=w*s.x;mz+=w*s.z;me+=w*s.e;mn+=w*s.n;
            }
            mx/=sw;mz/=sw;me/=sw;mn/=sw;
            double cosTerm=0,sinTerm=0;
            for(int i=0;i<count;i++){
                Sample s=samples.get(i);double w=weights[i];
                double x=s.x-mx,z=s.z-mz,e=s.e-me,n=s.n-mn;
                cosTerm+=w*(x*e+z*n);
                sinTerm+=w*(x*n-z*e);
            }
            theta=Math.atan2(sinTerm,cosTerm);
            tx=me-Math.cos(theta)*mx+Math.sin(theta)*mz;
            ty=mn-Math.sin(theta)*mx-Math.cos(theta)*mz;
            double sum=0;
            for(int i=0;i<count;i++){
                Sample s=samples.get(i);
                double e=Math.cos(theta)*s.x-Math.sin(theta)*s.z+tx;
                double n=Math.sin(theta)*s.x+Math.cos(theta)*s.z+ty;
                double residual=Math.hypot(e-s.e,n-s.n);
                sum+=residual*residual;
                double gate=Math.max(5,2*s.accuracy);
                weights[i]=Math.min(1,gate/Math.max(1e-9,residual)) /
                          Math.pow(Math.max(2,s.accuracy),2);
            }
            rms=Math.sqrt(sum/count);
        }
        // A single gross GPS spike must not invalidate a coherent sequence.
        // Count compatible fixes, but preserve raw RMS for diagnostics.
        int inliers=0;
        double inlierSquares=0;
        for(Sample s:samples){
            double e=Math.cos(theta)*s.x-Math.sin(theta)*s.z+tx;
            double n=Math.sin(theta)*s.x+Math.cos(theta)*s.z+ty;
            double residual=Math.hypot(e-s.e,n-s.n);
            if(residual<=Math.max(8,3*s.accuracy)){
                inliers++;inlierSquares+=residual*residual;
            }
        }
        double inlierRms=inliers==0?Double.POSITIVE_INFINITY:Math.sqrt(inlierSquares/inliers);
        // Heuristic only. Persistent GPS bias is not observable from these fixes.
        State state=inliers<Math.max(4,(int)Math.ceil(count*.75)) ||
                inlierRms>Math.max(8,2*acc)?State.INCONSISTENT:State.CANDIDATE;
        return result(state,theta,tx,ty,baseline,rms,acc,inliers);
    }
    private Result result(State state,double yaw,double east,double north,
                          double baseline,double rms,double accuracy,int inliers){
        return new Result(state,samples.size(),segment,yaw,east,north,baseline,rms,accuracy,inliers,lat0,lon0);
    }
}
