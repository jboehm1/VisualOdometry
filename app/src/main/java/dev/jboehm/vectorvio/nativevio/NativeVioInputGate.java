package dev.jboehm.vectorvio.nativevio;

import java.util.Objects;

/**
 * Fail-closed, Android-free guard for a future Camera2 -> native VIO adapter.
 *
 * NO pose or metric odometry is calculated here. This class does not start a camera,
 * read IMU sensors or integrate with ARCore. Real capture metadata and a calibrated
 * sensor clock must be provided by the acquisition layer.
 *
 * State is per camera session. Call reset() on pause, camera switching, loss of
 * tracking or calibration replacement. Access from one serial acquisition thread.
 */
public final class NativeVioInputGate {
    /** Camera2 SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME maps to elapsedRealtimeNanos(). */
    public enum CameraClock { REALTIME, UNKNOWN }

    public enum Code {
        ACCEPTED, CALIBRATION_UNVERIFIED, CAMERA_MISMATCH, CAMERA_CLOCK_UNKNOWN,
        INVALID_CAPTURE, NON_MONOTONIC_FRAME, NO_BRACKETING_IMU,
        IMU_GAP, INVALID_IMU_WINDOW
    }

    /**
     * Explicit, externally validated calibration. A number alone does not make a
     * calibration valid. The source version must change after lens/focus/crop change.
     */
    public static final class Calibration {
        public final String cameraId, version;
        public final int width, height;
        public final long cameraToImuOffsetNs;
        public final boolean verified;
        public Calibration(String cameraId, String version, int width, int height,
                           long cameraToImuOffsetNs, boolean verified) {
            this.cameraId = Objects.requireNonNull(cameraId, "cameraId");
            this.version = Objects.requireNonNull(version, "version");
            if (cameraId.isEmpty() || version.isEmpty() || width <= 0 || height <= 0
                    || Math.abs(cameraToImuOffsetNs) > 500_000_000L) {
                throw new IllegalArgumentException("Invalid calibration identity or offset");
            }
            this.width = width;
            this.height = height;
            this.cameraToImuOffsetNs = cameraToImuOffsetNs;
            this.verified = verified;
        }
    }

    public static final class Frame {
        public final String cameraId, calibrationVersion;
        public final int width, height;
        public final long startExposureNs, exposureNs, frameDurationNs;
        public final CameraClock clock;
        /**
         * startExposureNs = Camera2 SENSOR_TIMESTAMP or matched Image timestamp,
         * only after validating that image/metadata are from the SAME frame.
         */
        public Frame(String cameraId, String calibrationVersion, int width, int height,
                     long startExposureNs, long exposureNs, long frameDurationNs,
                     CameraClock clock) {
            this.cameraId = Objects.requireNonNull(cameraId);
            this.calibrationVersion = Objects.requireNonNull(calibrationVersion);
            this.width = width;
            this.height = height;
            this.startExposureNs = startExposureNs;
            this.exposureNs = exposureNs;
            this.frameDurationNs = frameDurationNs;
            this.clock = Objects.requireNonNull(clock);
        }
    }

    /** Range of real BOOTTIME SensorEvent timestamps that includes BOTH sensors. */
    public static final class ImuBracket {
        public final long firstGyroNs, lastGyroNs, firstAccelNs, lastAccelNs;
        public ImuBracket(long firstGyroNs, long lastGyroNs,
                          long firstAccelNs, long lastAccelNs) {
            this.firstGyroNs = firstGyroNs;
            this.lastGyroNs = lastGyroNs;
            this.firstAccelNs = firstAccelNs;
            this.lastAccelNs = lastAccelNs;
        }
    }

    public static final class Decision {
        public final Code code;
        public final long imuMidExposureNs;
        public final long acquisitionSegment;
        private Decision(Code code, long imuMidExposureNs, long acquisitionSegment) {
            this.code = code;
            this.imuMidExposureNs = imuMidExposureNs;
            this.acquisitionSegment = acquisitionSegment;
        }
        public boolean accepted() { return code == Code.ACCEPTED; }
    }

    // Policy constants, not empirically validated timing accuracy or covariance.
    private static final long MAX_IMU_BRACKET_SPAN_NS = 100_000_000L;
    private static final long MAX_TIMESTAMP_STEP_NS = 1_000_000_000L;
    private final Calibration calibration;
    private long lastFrameNs = -1;
    private long acquisitionSegment = 1;

    public NativeVioInputGate(Calibration calibration) {
        this.calibration = Objects.requireNonNull(calibration);
    }

    /** Start a new acquisition segment; no implicit pose continuity is allowed. */
    public void reset() {
        lastFrameNs = -1;
        acquisitionSegment++;
    }

    public long segmentId() { return acquisitionSegment; }

    public Decision accept(Frame frame, ImuBracket imu) {
        Objects.requireNonNull(frame, "frame");
        if (!calibration.verified) return reject(Code.CALIBRATION_UNVERIFIED);
        if (!frame.cameraId.equals(calibration.cameraId) ||
                !frame.calibrationVersion.equals(calibration.version) ||
                frame.width != calibration.width || frame.height != calibration.height) {
            return reject(Code.CAMERA_MISMATCH);
        }
        if (frame.clock != CameraClock.REALTIME) return reject(Code.CAMERA_CLOCK_UNKNOWN);
        if (frame.startExposureNs <= 0 || frame.exposureNs <= 0 ||
                frame.frameDurationNs < frame.exposureNs ||
                frame.exposureNs > MAX_TIMESTAMP_STEP_NS) {
            return reject(Code.INVALID_CAPTURE);
        }
        if (lastFrameNs >= 0 && frame.startExposureNs <= lastFrameNs) {
            return reject(Code.NON_MONOTONIC_FRAME);
        }
        if (lastFrameNs >= 0 && frame.startExposureNs - lastFrameNs > MAX_TIMESTAMP_STEP_NS) {
            // Missing frames cannot be smoothly bridged by simply accepting a pose.
            reset();
        }
        if (imu == null || imu.firstGyroNs <= 0 || imu.firstAccelNs <= 0 ||
                imu.firstGyroNs > imu.lastGyroNs || imu.firstAccelNs > imu.lastAccelNs) {
            return reject(Code.INVALID_IMU_WINDOW);
        }
        final long midNs;
        try {
            midNs = Math.addExact(Math.addExact(frame.startExposureNs,
                frame.exposureNs / 2), calibration.cameraToImuOffsetNs);
        } catch (ArithmeticException ex) {
            return reject(Code.INVALID_CAPTURE);
        }
        if (imu.firstGyroNs > midNs || imu.lastGyroNs < midNs ||
                imu.firstAccelNs > midNs || imu.lastAccelNs < midNs) {
            return reject(Code.NO_BRACKETING_IMU);
        }
        if (imu.lastGyroNs - imu.firstGyroNs > MAX_IMU_BRACKET_SPAN_NS ||
                imu.lastAccelNs - imu.firstAccelNs > MAX_IMU_BRACKET_SPAN_NS) {
            return reject(Code.IMU_GAP);
        }
        lastFrameNs = frame.startExposureNs;
        return new Decision(Code.ACCEPTED, midNs, acquisitionSegment);
    }

    private Decision reject(Code code) {
        return new Decision(code, -1, acquisitionSegment);
    }
}
