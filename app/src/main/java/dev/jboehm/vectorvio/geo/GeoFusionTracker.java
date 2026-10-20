package dev.jboehm.vectorvio.geo;

import dev.jboehm.vectorvio.GeoAlignment;

/**
 * Experimental non-authoritative geographic overlay. Never modifies odometry
 * or GPS position. Invalid clocks and discontinuities must degrade, not guess.
 */
public final class GeoFusionTracker {
    public enum Status { WAITING_FOR_POSES, CLOCK_MISMATCH, WAITING_FOR_FIXES,
        NEED_BASELINE, INCONSISTENT, CANDIDATE }
    public static final class Report {
        public final Status status;
        public final int matchedFixes, segment;
        public final double rmsMeters, baselineMeters, latitude,longitude, vioTravelMeters;
        public final long previousPairedFixNs;
        Report(Status status,int matchedFixes,int segment,double rmsMeters,double baselineMeters,
               double latitude,double longitude,double vioTravelMeters,long previousPairedFixNs){
            this.status=status;this.matchedFixes=matchedFixes;this.segment=segment;
            this.rmsMeters=rmsMeters;this.baselineMeters=baselineMeters;
            this.latitude=latitude;this.longitude=longitude;this.vioTravelMeters=vioTravelMeters;
            this.previousPairedFixNs=previousPairedFixNs;
        }
        public boolean hasCoordinate(){return status==Status.CANDIDATE;}
    }
    private final PoseFixSynchronizer poses=new PoseFixSynchronizer();
    private final GeoAlignment alignment=new GeoAlignment();
    private boolean mismatch;
    private PoseFixSynchronizer.Pose last;
    private PoseFixSynchronizer.Pose lastMatchedFix;
    private int matched;

    public synchronized void reset(){poses.reset();alignment.reset();last=lastMatchedFix=null;mismatch=false;matched=0;}
    /** elapsedRealtimeNowNs is Android SystemClock.elapsedRealtimeNanos(). */
    public synchronized void onPose(int segment,long cameraFrameNs,long elapsedRealtimeNowNs,double x,double z){
        if(cameraFrameNs<=0||elapsedRealtimeNowNs<=0||Math.abs((double)cameraFrameNs-elapsedRealtimeNowNs)>10_000_000_000d){
            mismatch=true;poses.reset();alignment.reset();last=lastMatchedFix=null;matched=0;return;
        }
        mismatch=false;
        if(last!=null && last.segment!=segment){alignment.reset();lastMatchedFix=null;matched=0;}
        if(poses.push(segment,cameraFrameNs,x,z))last=poses.interpolate(cameraFrameNs);
    }
    /** GNSS must supply elapsedRealtimeNanos, not UTC wall time. */
    public synchronized Report onFix(long fixNs,double lat,double lon,double gpsAccuracy){
        if(mismatch)return empty(Status.CLOCK_MISMATCH);
        PoseFixSynchronizer.Pose match=poses.interpolate(fixNs);
        if(match==null)return empty(Status.WAITING_FOR_POSES);
        if(!alignment.add(match.segment,fixNs,match.x,match.z,lat,lon,gpsAccuracy))return empty(Status.WAITING_FOR_FIXES);
        matched++;
        double movement=Double.NaN;
        long previousFixNs=0;
        if(lastMatchedFix!=null&&lastMatchedFix.segment==match.segment&&
           match.ns>lastMatchedFix.ns&&match.distanceMeters>=lastMatchedFix.distanceMeters){
            movement=match.distanceMeters-lastMatchedFix.distanceMeters;
            previousFixNs=lastMatchedFix.ns;
        }
        lastMatchedFix=match;
        last=match;
        GeoAlignment.Result a=alignment.estimate();
        Status status;
        switch(a.state){
            case NEED_BASELINE:status=Status.NEED_BASELINE;break;
            case INCONSISTENT:status=Status.INCONSISTENT;break;
            case CANDIDATE:status=Status.CANDIDATE;break;
            default:status=Status.WAITING_FOR_FIXES;
        }
        double[] projected=a.project(match.x,match.z);
        return new Report(status,a.samples,match.segment,a.residualRmsMeters,a.baselineMeters,
                projected==null?Double.NaN:projected[0],projected==null?Double.NaN:projected[1],movement,previousFixNs);
    }
    private Report empty(Status status){return new Report(status,matched,last==null?0:last.segment,Double.NaN,0,
            Double.NaN,Double.NaN,Double.NaN,0);}
}
