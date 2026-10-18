package dev.jboehm.vectorvio.geo;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Thread-safe bounded pose history for pairing GNSS fixes with VIO poses.
 * Input timestamps MUST have been verified against the same monotonic clock.
 * Does not extrapolate across tracking gaps, reset segments or unknown timebases.
 */
public final class PoseFixSynchronizer {
    public static final long MAX_INTERVAL_NS = 350_000_000L;
    private static final int LIMIT = 360;
    private final ArrayDeque<Pose> history = new ArrayDeque<>();
    private int segment;
    private long lastNs;

    public static final class Pose {
        public final int segment;
        public final long ns;
        public final double x, z, distanceMeters;
        Pose(int segment,long ns,double x,double z,double distanceMeters){
            this.segment=segment;this.ns=ns;this.x=x;this.z=z;this.distanceMeters=distanceMeters;
        }
    }
    public synchronized void reset(){history.clear();segment=0;lastNs=0;}
    public synchronized boolean push(int currentSegment,long elapsedRealtimeNs,double x,double z){
        if(currentSegment<=0 || elapsedRealtimeNs<=0 || !Double.isFinite(x)||!Double.isFinite(z))return false;
        if(segment!=currentSegment || (lastNs>0&&elapsedRealtimeNs-lastNs>700_000_000L))reset();
        if(elapsedRealtimeNs<=lastNs)return false;
        Pose prior=history.peekLast();
        double traveled=prior==null?0:prior.distanceMeters+Math.hypot(x-prior.x,z-prior.z);
        segment=currentSegment;lastNs=elapsedRealtimeNs;
        history.addLast(new Pose(currentSegment,elapsedRealtimeNs,x,z,traveled));
        while(history.size()>LIMIT)history.removeFirst();
        return true;
    }
    /** Match fix time only inside a continuous, short, same-segment interval. */
    public synchronized Pose interpolate(long fixElapsedRealtimeNs){
        if(fixElapsedRealtimeNs<=0||history.size()<2)return null;
        Iterator<Pose> it=history.iterator();
        Pose a=it.next();
        while(it.hasNext()){
            Pose b=it.next();
            if(fixElapsedRealtimeNs>=a.ns && fixElapsedRealtimeNs<=b.ns){
                long span=b.ns-a.ns;
                if(span<=0||span>MAX_INTERVAL_NS||b.segment!=a.segment)return null;
                double t=(double)(fixElapsedRealtimeNs-a.ns)/span;
                return new Pose(a.segment,fixElapsedRealtimeNs,a.x+t*(b.x-a.x),
                    a.z+t*(b.z-a.z),a.distanceMeters+t*(b.distanceMeters-a.distanceMeters));
            }
            a=b;
        }
        return null;
    }
}
