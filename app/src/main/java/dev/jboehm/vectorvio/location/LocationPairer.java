package dev.jboehm.vectorvio.location;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Bounded one-to-one timestamp matching of LocationManager GPS and Google FLP.
 * Neither stream is independent truth; no extrapolation or fusion into navigation.
 */
public final class LocationPairer {
    public static final long MAX_PAIR_DELTA_NS = 1_500_000_000L;
    private static final int MAX_QUEUE = 12;
    private static final long MAX_WAIT_NS = 6_000_000_000L;

    public static final class Pair {
        public final LocationSample gps, fused;
        public final double distanceMeters;
        public final long absoluteTimestampDeltaNs;
        Pair(LocationSample gps, LocationSample fused) {
            this.gps = gps; this.fused = fused;
            this.distanceMeters = LocationSample.horizontalDistanceMeters(gps, fused);
            this.absoluteTimestampDeltaNs = Math.abs(gps.fixElapsedNs - fused.fixElapsedNs);
        }
    }
    public static final class Snapshot {
        public final int gpsCount, fusedCount, pairCount, rejectedGps, rejectedFused;
        public final double meanDisagreementMeters, maxDisagreementMeters, meanPairTimeOffsetMs;
        Snapshot(int gpsCount,int fusedCount,int pairCount,int rejectedGps,int rejectedFused,
                 double meanDisagreementMeters,double maxDisagreementMeters,double meanPairTimeOffsetMs) {
            this.gpsCount=gpsCount;this.fusedCount=fusedCount;this.pairCount=pairCount;
            this.rejectedGps=rejectedGps;this.rejectedFused=rejectedFused;
            this.meanDisagreementMeters=meanDisagreementMeters;
            this.maxDisagreementMeters=maxDisagreementMeters;
            this.meanPairTimeOffsetMs=meanPairTimeOffsetMs;
        }
    }
    private final ArrayDeque<LocationSample> gps=new ArrayDeque<>(),fused=new ArrayDeque<>();
    private long lastGpsNs,lastFusedNs;
    private int gpsCount,fusedCount,pairCount,rejectedGps,rejectedFused;
    private double disagreementSum,disagreementMax,timeDiffSum;

    /** Called sequentially by a single location worker. Invalid fixes stay in raw CSV only. */
    public Pair accept(LocationSample sample) {
        if(sample==null||sample.source==null)return null;
        boolean isGps=sample.source==LocationSample.Source.GPS_PROVIDER;
        if(isGps)gpsCount++;else fusedCount++;
        if(sample.validity()!=LocationSample.Validity.OK ||
            sample.fixElapsedNs<=(isGps?lastGpsNs:lastFusedNs)){
            if(isGps)rejectedGps++;else rejectedFused++;
            return null;
        }
        if(isGps)lastGpsNs=sample.fixElapsedNs;else lastFusedNs=sample.fixElapsedNs;
        ArrayDeque<LocationSample> own=isGps?gps:fused,opposite=isGps?fused:gps;
        trim(own,sample.fixElapsedNs);trim(opposite,sample.fixElapsedNs);
        LocationSample nearest=null;long best=Long.MAX_VALUE;
        for(LocationSample other:opposite) {
            long dt=Math.abs(sample.fixElapsedNs-other.fixElapsedNs);
            if(dt<=MAX_PAIR_DELTA_NS&&dt<best){nearest=other;best=dt;}
        }
        if(nearest==null) {
            own.addLast(sample);while(own.size()>MAX_QUEUE)own.removeFirst();
            return null;
        }
        opposite.remove(nearest);
        Pair pair=isGps?new Pair(sample,nearest):new Pair(nearest,sample);
        pairCount++;disagreementSum+=pair.distanceMeters;
        disagreementMax=Math.max(disagreementMax,pair.distanceMeters);
        timeDiffSum+=pair.absoluteTimestampDeltaNs/1_000_000d;
        return pair;
    }
    private static void trim(ArrayDeque<LocationSample> queue,long now) {
        for(Iterator<LocationSample> it=queue.iterator();it.hasNext();)
            if(now-it.next().fixElapsedNs>MAX_WAIT_NS)it.remove();
    }
    public Snapshot snapshot() {
        return new Snapshot(gpsCount,fusedCount,pairCount,rejectedGps,rejectedFused,
            pairCount==0?Double.NaN:disagreementSum/pairCount,
            pairCount==0?Double.NaN:disagreementMax,
            pairCount==0?Double.NaN:timeDiffSum/pairCount);
    }
    public void reset() {
        gps.clear();fused.clear();lastGpsNs=lastFusedNs=0;
        gpsCount=fusedCount=pairCount=rejectedGps=rejectedFused=0;
        disagreementSum=disagreementMax=timeDiffSum=0;
    }
}
