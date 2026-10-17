package dev.jboehm.vectorvio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure-Java, deterministic distance accumulator for external world-space poses.
 *
 * ARCore's pose coordinates are in metres in an arbitrary local world frame.
 * This class DOES NOT estimate camera poses or read raw IMU data; ARCore does VIO.
 * No path is integrated across tracking gaps, pauses, timestamp discontinuities
 * or implausibly fast jumps. Distance remains an *estimate*, not ground truth.
 *
 * All public methods are synchronized: UI controls run on the main thread,
 * while ARCore calls onPose on the GL thread.
 */
public final class Odometer {
    public static final double MIN_STEP_METERS = 0.035;
    public static final double MAX_SPEED_MPS = 25.0;
    public static final double MAX_GAP_SECONDS = 0.70;
    private static final int MAX_TRAIL_POINTS = 400;
    private static final double TRAIL_MIN_SPACING_METERS = 0.15;

    public enum Result { INACTIVE, REFERENCE, ACCEPTED, NO_CHANGE, GAP, REJECTED }

    public static final class Point {
        public final double x, y, z;
        public final int segment;
        public Point(double x, double y, double z) { this(x,y,z,0); }
        public Point(double x,double y,double z,int segment) { this.x=x;this.y=y;this.z=z;this.segment=segment; }
        public double distance(Point p) {
            return Math.sqrt(Math.pow(x-p.x,2) + Math.pow(y-p.y,2) + Math.pow(z-p.z,2));
        }
    }

    public static final class Snapshot {
        public final boolean recording, tracking;
        public final double totalMeters, horizontalMeters, displacementMeters;
        public final double ascentMeters, descentMeters, verticalMeters, speedMps;
        public final double netX, netY, netZ;
        public final long samples, outliers, gaps, elapsedMilliseconds;
        public final int segments;
        public final List<Point> trail,worldTrail;

        private Snapshot(Odometer a, long nowMs) {
            recording = a.recording;
            tracking = a.tracking;
            totalMeters = a.totalMeters;
            horizontalMeters = a.horizontalMeters;
            netX = a.netX;
            netY = a.netY;
            netZ = a.netZ;
            displacementMeters = Math.sqrt(netX*netX + netY*netY + netZ*netZ);
            ascentMeters = a.ascentMeters;
            descentMeters = a.descentMeters;
            verticalMeters = netY;
            speedMps = a.speedMps;
            samples = a.samples;
            outliers = a.outliers;
            gaps = a.gaps;
            segments = a.segments;
            elapsedMilliseconds = a.elapsedMs + (a.recording ? Math.max(0, nowMs-a.resumeTimeMs) : 0);
            trail = Collections.unmodifiableList(new ArrayList<>(a.trail));worldTrail=a.worldTrail.snapshot();
        }
    }

    private boolean recording = false, tracking = false;
    private Point reference;
    private Point lastObserved;
    private double[] lastOrientation;
    private double rotationSinceReference;
    private static final double ROTATION_NOISE_METERS_PER_RADIAN=.20;
    private static final double MAX_ROTATION_DEADBAND_METERS=.12;
    private double totalMeters, horizontalMeters, ascentMeters, descentMeters;
    private double netX, netY, netZ, speedMps;
    private long samples, outliers, gaps;
    private int segments;
    private long lastObservedNs, referencePoseNs, elapsedMs, resumeTimeMs;
    private final ArrayList<Point> trail = new ArrayList<>();
    private final RouteHistory worldTrail=new RouteHistory();

    public synchronized void start(long nowMs) {
        if (recording) return;
        recording = true;
        tracking = false;
        reference = null;
        lastObserved = null;
        lastOrientation=null;rotationSinceReference=0;
        resumeTimeMs = nowMs;
        speedMps = 0;
    }

    public synchronized void pause(long nowMs) {
        if (!recording) return;
        elapsedMs += Math.max(0, nowMs - resumeTimeMs);
        recording = false;
        tracking = false;
        reference = null;
        lastObserved = null;
        lastOrientation=null;rotationSinceReference=0;
        speedMps = 0;
    }

    public synchronized void reset() {
        recording = false;
        tracking = false;
        reference = null;
        lastObserved = null;
        lastOrientation=null;rotationSinceReference=0;
        totalMeters = horizontalMeters = ascentMeters = descentMeters = 0;
        netX = netY = netZ = speedMps = 0;
        samples = outliers = gaps = 0;
        segments = 0;
        elapsedMs = resumeTimeMs = lastObservedNs = referencePoseNs = 0;
        trail.clear();worldTrail.clear();
    }

    public synchronized void lostTracking() {
        if (tracking && recording) gaps++;
        tracking = false;
        reference = null;
        lastObserved = null;
        lastOrientation=null;rotationSinceReference=0;
        speedMps = 0;
    }

    public synchronized Result onPose(double x,double y,double z,long timestampNs){
        return integrate(x,y,z,timestampNs,null);
    }
    /** Orientation-aware live input. Noise gate expands during rotation; translations
     * beyond the bounded gate retain their full displacement, including while turning. */
    public synchronized Result onPose(double x,double y,double z,float[] quaternion,long timestampNs){
        if(!recording)return Result.INACTIVE;
        if(quaternion==null||quaternion.length!=4)return Result.REJECTED;
        double norm=0;for(float v:quaternion){if(!Float.isFinite(v))return Result.REJECTED;norm+=(double)v*v;}
        if(norm<1e-12)return Result.REJECTED;
        double[] q=new double[4];norm=Math.sqrt(norm);for(int i=0;i<4;i++)q[i]=quaternion[i]/norm;
        return integrate(x,y,z,timestampNs,q);
    }
    private Result integrate(double x, double y, double z, long timestampNs,double[] orientation) {
        if (!recording) return Result.INACTIVE;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || timestampNs <= 0) return Result.REJECTED;
        tracking = true;
        Point p = new Point(x, y, z);
        samples++;
        if (reference == null) {
            lastOrientation=orientation;rotationSinceReference=0;
            reference = p;
            lastObserved = p;
            lastObservedNs = timestampNs;
            referencePoseNs = timestampNs;
            segments++;
            beginTrailSegment(p);
            return Result.REFERENCE;
        }
        // Check gaps against the previous FRAME, not the previous accepted
        // keyframe. Otherwise standing still for 0.7s would cause a fake gap.
        double frameDt = (timestampNs - lastObservedNs) / 1_000_000_000.0;
        if (frameDt <= 0) return Result.NO_CHANGE; // duplicate/out-of-order frame
        double frameStep = p.distance(lastObserved);
        lastObserved = p;
        lastObservedNs = timestampNs;

        if (frameDt > MAX_GAP_SECONDS) {
            lastOrientation=orientation;rotationSinceReference=0;
            reference = p;
            referencePoseNs = timestampNs;
            speedMps = 0;
            gaps++;
            segments++;
            beginTrailSegment(p);
            return Result.GAP;
        }
        // Check instantaneous frame-to-frame motion for relocalization jumps,
        // including jumps after standing still for a long time.
        if (frameStep / frameDt > MAX_SPEED_MPS) {
            lastOrientation=orientation;rotationSinceReference=0;
            reference = p;
            referencePoseNs = timestampNs;
            speedMps = 0;
            outliers++;
            segments++;
            beginTrailSegment(p);
            return Result.REJECTED;
        }

        if(orientation!=null&&lastOrientation!=null){
            double dot=0;for(int i=0;i<4;i++)dot+=orientation[i]*lastOrientation[i];
            rotationSinceReference+=2*Math.acos(Math.max(0,Math.min(1,Math.abs(dot))));
        }
        if(orientation==null)rotationSinceReference=0;
        lastOrientation=orientation;
        double dt = (timestampNs - referencePoseNs) / 1_000_000_000.0;
        double step = p.distance(reference);

        double minimumStep=orientation==null?MIN_STEP_METERS:Math.min(MAX_ROTATION_DEADBAND_METERS,
            MIN_STEP_METERS+ROTATION_NOISE_METERS_PER_RADIAN*rotationSinceReference);
        if (step < minimumStep) {
            // Keep the reference point so slow genuine movements accumulate.
            speedMps *= 0.91;
            return Result.NO_CHANGE;
        }

        double dx = p.x-reference.x, dy = p.y-reference.y, dz = p.z-reference.z;
        totalMeters += step;
        horizontalMeters += Math.hypot(dx, dz);
        netX += dx;
        netY += dy;
        netZ += dz;
        if (dy > 0) ascentMeters += dy; else descentMeters += -dy;
        // Short keyframe windows provide a low-noise average velocity.
        // After prolonged stationary time, use the latest frame motion instead
        // of dividing the new movement by minutes spent standing still.
        double instantaneousMps = dt <= MAX_GAP_SECONDS ? step/dt : frameStep/frameDt;
        speedMps = speedMps == 0 ? instantaneousMps : 0.72*speedMps + 0.28*instantaneousMps;
        reference = p;
        rotationSinceReference=0;
        referencePoseNs = timestampNs;

        Point last = trail.isEmpty() ? null : trail.get(trail.size()-1);
        Point current = new Point(netX, netY, netZ, segments);
        if (last == null || Math.hypot(current.x-last.x, current.z-last.z) >= TRAIL_MIN_SPACING_METERS) {
            if (trail.size() == MAX_TRAIL_POINTS) trail.remove(0);
            trail.add(current);
        }
        Point lastWorld=worldTrail.last();
        if(lastWorld==null||lastWorld.segment!=segments||p.distance(lastWorld)>=TRAIL_MIN_SPACING_METERS)worldTrail.add(new Point(p.x,p.y,p.z,segments));
        return Result.ACCEPTED;
    }

    private void beginTrailSegment(Point world) {
        worldTrail.add(new Point(world.x,world.y,world.z,segments));
        // A new independent local frame. Never stitch unknown rotations/translations.
        netX=netY=netZ=0;
        if(trail.size()==MAX_TRAIL_POINTS) trail.remove(0);
        trail.add(new Point(0,0,0,segments));
    }

    public synchronized int currentSegment(){return segments;}

    public synchronized boolean isRecording(){return recording;}

    public synchronized Snapshot snapshot(long nowMs) { return new Snapshot(this, nowMs); }
}
