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
        public Point(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
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
        public final List<Point> trail;

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
            trail = Collections.unmodifiableList(new ArrayList<>(a.trail));
        }
    }

    private boolean recording = false, tracking = false;
    private Point reference;
    private double totalMeters, horizontalMeters, ascentMeters, descentMeters;
    private double netX, netY, netZ, speedMps;
    private long samples, outliers, gaps;
    private int segments;
    private long previousPoseNs, elapsedMs, resumeTimeMs;
    private final ArrayList<Point> trail = new ArrayList<>();

    public synchronized void start(long nowMs) {
        if (recording) return;
        recording = true;
        tracking = false;
        reference = null;
        resumeTimeMs = nowMs;
        speedMps = 0;
    }

    public synchronized void pause(long nowMs) {
        if (!recording) return;
        elapsedMs += Math.max(0, nowMs - resumeTimeMs);
        recording = false;
        tracking = false;
        reference = null;
        speedMps = 0;
    }

    public synchronized void reset() {
        recording = false;
        tracking = false;
        reference = null;
        totalMeters = horizontalMeters = ascentMeters = descentMeters = 0;
        netX = netY = netZ = speedMps = 0;
        samples = outliers = gaps = 0;
        segments = 0;
        elapsedMs = resumeTimeMs = previousPoseNs = 0;
        trail.clear();
    }

    public synchronized void lostTracking() {
        if (tracking && recording) gaps++;
        tracking = false;
        reference = null;
        speedMps = 0;
    }

    public synchronized Result onPose(double x, double y, double z, long timestampNs) {
        if (!recording) return Result.INACTIVE;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || timestampNs <= 0) return Result.REJECTED;
        tracking = true;
        Point p = new Point(x, y, z);
        samples++;
        if (reference == null) {
            reference = p;
            previousPoseNs = timestampNs;
            segments++;
            if (trail.isEmpty()) trail.add(new Point(netX, netY, netZ));
            return Result.REFERENCE;
        }
        double dt = (timestampNs - previousPoseNs) / 1_000_000_000.0;
        if (dt <= 0) return Result.NO_CHANGE; // duplicate camera frame
        if (dt > MAX_GAP_SECONDS) {
            reference = p;
            previousPoseNs = timestampNs;
            speedMps = 0;
            gaps++;
            segments++;
            return Result.GAP;
        }

        double step = p.distance(reference);
        if (step / dt > MAX_SPEED_MPS) {
            // A jump or relocalization is not a physical displacement.
            reference = p;
            previousPoseNs = timestampNs;
            speedMps = 0;
            outliers++;
            segments++;
            return Result.REJECTED;
        }

        if (step < MIN_STEP_METERS) {
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
        double instantaneousMps = step/dt;
        speedMps = speedMps == 0 ? instantaneousMps : 0.72*speedMps + 0.28*instantaneousMps;
        reference = p;
        previousPoseNs = timestampNs;

        Point last = trail.isEmpty() ? null : trail.get(trail.size()-1);
        Point current = new Point(netX, netY, netZ);
        if (last == null || Math.hypot(current.x-last.x, current.z-last.z) >= TRAIL_MIN_SPACING_METERS) {
            if (trail.size() == MAX_TRAIL_POINTS) trail.remove(0);
            trail.add(current);
        }
        return Result.ACCEPTED;
    }

    public synchronized Snapshot snapshot(long nowMs) { return new Snapshot(this, nowMs); }
}
