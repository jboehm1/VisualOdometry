package dev.jboehm.vectorvio.pdr;

import java.util.Locale;

/**
 * Observes SENSOR callback time hygiene, independent of Android SDK.
 * Android sensor timestamps and arrival timestamps both use elapsedRealtimeNanos.
 * A long interval or callback delay is only an INDICATOR of batching/throttling;
 * it is NOT a certified dropped-sample count or a FIFO overflow measurement.
 */
public final class SensorTimingMonitor {
    public static final int ACCEL=0, GYRO=1, MAGNET=2, ROTATION=3, PRESSURE=4, STEP=5;
    public static final long GAP_NS=250_000_000L;
    public static final long LATE_NS=200_000_000L;
    private static final String[] NAMES={"accel","gyro","magnet","rotation","pressure","step"};
    private long beforeSessionCallbacks;

    /** Immutable copy of counters for a sensor kind. */
    public static final class Stats {
        public final long events, duplicates, outOfOrder, longIntervals,
                          delayedCallbacks, futureTimestamps, invalidTimestamps;
        public final long maximumIntervalNs, maximumDelayNs;
        private Stats(long n,long dup,long order,long gaps,long delayed,long future,
                      long invalid,long maxInterval,long maxDelay){
            events=n;duplicates=dup;outOfOrder=order;longIntervals=gaps;
            delayedCallbacks=delayed;futureTimestamps=future;invalidTimestamps=invalid;
            maximumIntervalNs=maxInterval;maximumDelayNs=maxDelay;
        }
    }
    private static final class Counter {
        long latestNs=Long.MIN_VALUE;
        long events,duplicates,outOfOrder,longIntervals,delayedCallbacks,
             futureTimestamps,invalidTimestamps,maximumIntervalNs,maximumDelayNs;
        Stats snapshot(){
            return new Stats(events,duplicates,outOfOrder,longIntervals,delayedCallbacks,
                futureTimestamps,invalidTimestamps,maximumIntervalNs,maximumDelayNs);
        }
    }
    private final Counter[] counters=new Counter[NAMES.length];

    public SensorTimingMonitor(){reset();}
    public void reset(){
        for(int i=0;i<NAMES.length;i++)counters[i]=new Counter();
        beforeSessionCallbacks=0;
    }
    public void beforeSessionCallback(){beforeSessionCallbacks++;}
    public long beforeSessionCallbacks(){return beforeSessionCallbacks;}
    public void observe(int kind,long sensorTimeNs,long arrivalTimeNs){
        if(kind<0 || kind>=counters.length)throw new IllegalArgumentException("unknown sensor");
        Counter c=counters[kind];c.events++;
        if(sensorTimeNs<=0 || arrivalTimeNs<=0){
            c.invalidTimestamps++;return;
        }
        if(c.latestNs!=Long.MIN_VALUE && sensorTimeNs<=c.latestNs){
            if(sensorTimeNs==c.latestNs)c.duplicates++;
            else c.outOfOrder++;
        }else{
            // Only periodic-ish raw IMUs have a meaningful 250-ms gap threshold.
            // Pressure and human step events are naturally infrequent.
            if(kind<=ROTATION && c.latestNs!=Long.MIN_VALUE){
                long interval=sensorTimeNs-c.latestNs;
                c.maximumIntervalNs=Math.max(c.maximumIntervalNs,interval);
                if(interval>GAP_NS)c.longIntervals++;
            }
            c.latestNs=sensorTimeNs;
        }
        if(arrivalTimeNs<sensorTimeNs)c.futureTimestamps++;
        else{
            long lag=arrivalTimeNs-sensorTimeNs;
            c.maximumDelayNs=Math.max(c.maximumDelayNs,lag);
            if(lag>LATE_NS)c.delayedCallbacks++;
        }
    }
    public Stats stats(int kind){
        if(kind<0 || kind>=counters.length)throw new IllegalArgumentException("unknown sensor");
        return counters[kind].snapshot();
    }
    /** Compact live status (not a confidence score). */
    public String summary(){
        long events=0,dup=0,order=0,gaps=0,delayed=0,future=0,invalid=0;
        for(Counter c:counters){
            events+=c.events;dup+=c.duplicates;order+=c.outOfOrder;
            gaps+=c.longIntervals;delayed+=c.delayedCallbacks;
            future+=c.futureTimestamps;invalid+=c.invalidTimestamps;
        }
        return String.format(Locale.US,
            "Timing · events %d · duplicate %d · reordered %d · IMU gaps>250ms %d"
              +" · delivery lag>200ms %d · future %d · invalid %d · before START %d",
            events,dup,order,gaps,delayed,future,invalid,beforeSessionCallbacks);
    }
    /** Per-type bounded report for the exported metadata, never a loss estimate. */
    public String detailedReport(){
        StringBuilder out=new StringBuilder(summary()).append('\n');
        for(int i=0;i<NAMES.length;i++){
            Stats s=stats(i);
            out.append(String.format(Locale.US,
                "%s: events=%d duplicate=%d reordered=%d gaps_over_250ms=%d"
                  +" delayed_over_200ms=%d future=%d invalid=%d max_interval_ms=%.1f max_lag_ms=%.1f%n",
                NAMES[i],s.events,s.duplicates,s.outOfOrder,s.longIntervals,
                s.delayedCallbacks,s.futureTimestamps,s.invalidTimestamps,
                s.maximumIntervalNs/1e6,s.maximumDelayNs/1e6));
        }
        out.append("NOTE: gaps and latency indicate possible batching/throttling;"
                   +" not proof of lost samples or sensor FIFO saturation.\n");
        return out.toString();
    }
}
