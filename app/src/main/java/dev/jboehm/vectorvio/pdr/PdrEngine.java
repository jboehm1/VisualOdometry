package dev.jboehm.vectorvio.pdr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Camera-independent pedestrian dead reckoning. All times use elapsedRealtimeNanos.
 * The 2D coordinates are LOCAL MAGNETIC EAST/NORTH-like metres, NOT geographic coordinates.
 * Distance remains available without heading; never invent a 2D position in that case.
 *
 * A heading supplied by Android's rotation vector describes the PHONE, not necessarily
 * the travel direction. The host must explain the fixed, phone-top-forward assumption.
 * GNSS bearing is used only when a recent, quality-gated measured course is available.
 * No claim is made that this estimate has a calibrated covariance.
 */
public final class PdrEngine {
    public enum HeadingSource { UNAVAILABLE, DEVICE_ASSUMED, GNSS_COURSE }
    public static final class Point {
        public final long timeNs;
        public final double eastM, northM;
        public final boolean gapBefore;
        Point(long t, double e, double n,boolean gap) { timeNs=t; eastM=e; northM=n; gapBefore=gap; }
    }
    public static final class Snapshot {
        public final boolean recording, hasTrajectory;
        public final int steps;
        public final double distanceM, eastM, northM, stepLengthM, elevationM, headingDeg;
        /** Cadence is inferred from accepted step timestamps, not accelerometer integration. */
        public final double cadenceStepsPerMin, estimatedSpeedMps;
        public final HeadingSource headingSource;
        public final int trajectorySteps, stepsWithoutHeading;
        public final long lastStepNs;
        Snapshot(PdrEngine p, long nowNs) {
            recording=p.recording; steps=p.steps; distanceM=p.distanceM;
            eastM=p.eastM; northM=p.northM; stepLengthM=p.stepLengthM;
            elevationM=p.elevationM; hasTrajectory=p.trajectorySteps>0;
            trajectorySteps=p.trajectorySteps;
            stepsWithoutHeading=p.stepsWithoutHeading; lastStepNs=p.lastStepNs;
            Heading h=p.headingAt(nowNs); headingSource=h.source;
            headingDeg=h.source==HeadingSource.UNAVAILABLE?Double.NaN:Math.toDegrees(h.radians);
            if(recording && p.lastStepNs!=Long.MIN_VALUE && nowNs>=p.lastStepNs
                    && nowNs-p.lastStepNs<=4_000_000_000L
                    && Double.isFinite(p.smoothedStepPeriodNs)){
                cadenceStepsPerMin=60_000_000_000.0/p.smoothedStepPeriodNs;
                estimatedSpeedMps=stepLengthM*cadenceStepsPerMin/60.0;
            } else {
                cadenceStepsPerMin=0;
                estimatedSpeedMps=0;
            }
        }
    }
    private static final long MIN_STEP_NS=270_000_000L;
    private static final long HEADING_MAX_AGE_NS=3_000_000_000L;
    private static final long GNSS_MAX_AGE_NS=4_000_000_000L;
    private boolean recording;
    /** A sensor sample arriving from before START must never move this session. */
    private long sessionStartNs=Long.MIN_VALUE;
    private long lastStepNs=Long.MIN_VALUE, deviceTimeNs=Long.MIN_VALUE, gpsTimeNs=Long.MIN_VALUE;
    private long lastPressureNs=Long.MIN_VALUE;
    private boolean deviceHeadingAvailable, gpsCourseAvailable;
    private double deviceHeadingRad, gpsHeadingRad, stepLengthM=.74, distanceM, eastM, northM;
    private double baselinePressureHpa=Double.NaN, elevationM=Double.NaN;
    private double smoothedStepPeriodNs=Double.NaN;
    private int steps, trajectorySteps, stepsWithoutHeading;
    private boolean gapPending;
    private final ArrayList<Point> path=new ArrayList<>();
    private final StepPeakDetector fallback=new StepPeakDetector();

    public void setStepLengthMeters(double meters) {
        if (!Double.isFinite(meters)||meters<.35||meters>1.8)
            throw new IllegalArgumentException("step length out of [.35,1.8] m");
        stepLengthM=meters;
    }
    public void start(long timeNs) {
        if(timeNs<=0)throw new IllegalArgumentException("session start timestamp must be positive");
        sessionStartNs=timeNs;
        recording=true; lastStepNs=Long.MIN_VALUE; deviceTimeNs=Long.MIN_VALUE;
        gpsTimeNs=Long.MIN_VALUE; lastPressureNs=Long.MIN_VALUE;
        deviceHeadingAvailable=false; gpsCourseAvailable=false;
        distanceM=0; eastM=0; northM=0; steps=0;
        trajectorySteps=0; stepsWithoutHeading=0;
        elevationM=Double.NaN; baselinePressureHpa=Double.NaN;
        smoothedStepPeriodNs=Double.NaN;
        fallback.reset();gapPending=false;path.clear();path.add(new Point(timeNs,0,0,false));
    }
    public void pause() {
        recording=false;fallback.reset();lastStepNs=Long.MIN_VALUE;
        smoothedStepPeriodNs=Double.NaN;gapPending=true;
    }
    public void resume() {
        recording=true;fallback.reset();lastStepNs=Long.MIN_VALUE;
        smoothedStepPeriodNs=Double.NaN;
        deviceTimeNs=Long.MIN_VALUE;gpsTimeNs=Long.MIN_VALUE;
        deviceHeadingAvailable=false;gpsCourseAvailable=false;gapPending=true;
    }
    public boolean isRecording() { return recording; }
    /** Gate delayed callbacks from the preceding session before they reach the trace. */
    public boolean isInCurrentSession(long timeNs) {
        return recording && timeNs>=sessionStartNs && timeNs>0;
    }

    /** Android SensorManager azimuth: radians clockwise from north; phone top Y axis. */
    public void deviceHeading(long timeNs, double radians) {
        if (!recording||timeNs<sessionStartNs||timeNs<=deviceTimeNs||!Double.isFinite(radians)) return;
        deviceTimeNs=timeNs; deviceHeadingRad=wrap(radians); deviceHeadingAvailable=true;
    }
    /**
     * Immediately stop using the phone azimuth when posture or magnet quality fails.
     * It is also recorded as HEADING_INVALID so offline replay gets the same gaps.
     * Keep the timestamp high-water mark to reject late buffered older rotations.
     */
    public boolean invalidateDeviceHeading(long timeNs) {
        if(!recording || timeNs<sessionStartNs || timeNs<deviceTimeNs
                || !deviceHeadingAvailable) return false;
        deviceTimeNs=timeNs;
        deviceHeadingAvailable=false;
        return true;
    }
    /** GNSS course must first be converted TRUE -> MAGNETIC north by Android adapter. */
    public void gpsCourse(long timeNs, double radians, float speedMps,
                          float horizontalAccuracyM, float bearingAccuracyDeg) {
        if (!recording || timeNs<sessionStartNs || timeNs<=gpsTimeNs) return;
        // A newer low-quality fix revokes an older accepted course immediately.
        // It is logged in the trace, so Java replay makes the identical decision.
        gpsTimeNs=timeNs;
        if(!Double.isFinite(radians) ||
           !Float.isFinite(speedMps) || speedMps<1.2f ||
           !Float.isFinite(horizontalAccuracyM) || horizontalAccuracyM<0f || horizontalAccuracyM>20f ||
           !Float.isFinite(bearingAccuracyDeg) || bearingAccuracyDeg<0f || bearingAccuracyDeg>25f){
            gpsCourseAvailable=false;return;
        }
        gpsCourseAvailable=true;gpsHeadingRad=wrap(radians);
    }
    /** Revoke GNSS direction immediately on provider/permission/measurement loss. */
    public boolean invalidateGpsCourse(long timeNs) {
        if(!recording || timeNs<sessionStartNs || timeNs<gpsTimeNs ||
           !gpsCourseAvailable) return false;
        gpsTimeNs=timeNs;
        gpsCourseAvailable=false;
        return true;
    }
    public void pressure(long timeNs, double hPa) {
        if(!recording||timeNs<sessionStartNs||timeNs<=lastPressureNs ||
                !Double.isFinite(hPa)||hPa<300||hPa>1100) return;
        lastPressureNs=timeNs;
        if(!Double.isFinite(baselinePressureHpa)) baselinePressureHpa=hPa;
        elevationM=44330.0*(1.0-Math.pow(hPa/baselinePressureHpa,0.190294957));
    }
    public boolean step(long timeNs) {
        if (!recording || timeNs<sessionStartNs || (lastStepNs!=Long.MIN_VALUE
                && (timeNs<=lastStepNs || timeNs-lastStepNs<MIN_STEP_NS))) return false;
        if(lastStepNs!=Long.MIN_VALUE){
            long dt=timeNs-lastStepNs;
            if(dt<=2_000_000_000L){
                smoothedStepPeriodNs=Double.isFinite(smoothedStepPeriodNs)
                    ? .7*smoothedStepPeriodNs+.3*dt : dt;
            } else {
                smoothedStepPeriodNs=Double.NaN;
            }
        }
        lastStepNs=timeNs; ++steps; distanceM+=stepLengthM;
        Heading heading=headingAt(timeNs);
        if (heading.source==HeadingSource.UNAVAILABLE) {++stepsWithoutHeading;gapPending=true;return true;}
        eastM+=stepLengthM*Math.sin(heading.radians);
        northM+=stepLengthM*Math.cos(heading.radians);
        trajectorySteps++;
        path.add(new Point(timeNs,eastM,northM,gapPending));
        gapPending=false;
        return true;
    }
    /** Fallback used ONLY when Android's STEP_DETECTOR is missing or not permitted. */
    public boolean acceleration(long timeNs, double x, double y, double z) {
        if (!isInCurrentSession(timeNs)) return false;
        return fallback.accept(timeNs,x,y,z) && step(timeNs);
    }
    public Snapshot snapshot(long timeNs){return new Snapshot(this,timeNs);}
    public List<Point> path(){return Collections.unmodifiableList(new ArrayList<>(path));}
    private Heading headingAt(long t) {
        if(gpsCourseAvailable && gpsTimeNs!=Long.MIN_VALUE && t>=gpsTimeNs && t-gpsTimeNs<=GNSS_MAX_AGE_NS)
            return new Heading(HeadingSource.GNSS_COURSE,gpsHeadingRad);
        if(deviceHeadingAvailable && deviceTimeNs!=Long.MIN_VALUE && t>=deviceTimeNs && t-deviceTimeNs<=HEADING_MAX_AGE_NS)
            return new Heading(HeadingSource.DEVICE_ASSUMED,deviceHeadingRad);
        return new Heading(HeadingSource.UNAVAILABLE,Double.NaN);
    }
    private static double wrap(double a) {
        a %= 2*Math.PI;return a<0?a+2*Math.PI:a;
    }
    private static final class Heading {
        final HeadingSource source; final double radians;
        Heading(HeadingSource s,double r){source=s;radians=r;}
    }
}
