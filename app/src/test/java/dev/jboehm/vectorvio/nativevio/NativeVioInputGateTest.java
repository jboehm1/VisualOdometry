package dev.jboehm.vectorvio.nativevio;

import org.junit.Test;
import static org.junit.Assert.*;

public final class NativeVioInputGateTest {
    private static NativeVioInputGate.Calibration calibration(boolean verified) {
        return new NativeVioInputGate.Calibration("rear0", "calib-v1", 1280, 720, 2_000_000L, verified);
    }
    private static NativeVioInputGate.Frame frame(long t) {
        return new NativeVioInputGate.Frame("rear0","calib-v1",1280,720,
            t,8_000_000L,33_333_333L,NativeVioInputGate.CameraClock.REALTIME);
    }
    private static NativeVioInputGate.ImuBracket imu(long center) {
        return new NativeVioInputGate.ImuBracket(center-5_000_000L,
            center+5_000_000L,center-10_000_000L,center+10_000_000L);
    }
    @Test public void acceptsVerifiedPairedCaptureAndBracketingGyroAccel() {
        NativeVioInputGate gate=new NativeVioInputGate(calibration(true));
        long start=10_000_000_000L;
        NativeVioInputGate.Decision result=gate.accept(frame(start),imu(start+6_000_000L));
        assertTrue(result.accepted());
        assertEquals(start+6_000_000L,result.imuMidExposureNs);
        assertEquals(1,result.acquisitionSegment);
    }
    @Test public void cannotPublishWithoutVerifiedCalibrationOrRealtimeClock() {
        NativeVioInputGate unverified=new NativeVioInputGate(calibration(false));
        assertEquals(NativeVioInputGate.Code.CALIBRATION_UNVERIFIED,
            unverified.accept(frame(10_000_000_000L),imu(10_006_000_000L)).code);
        NativeVioInputGate valid=new NativeVioInputGate(calibration(true));
        NativeVioInputGate.Frame unknown=new NativeVioInputGate.Frame("rear0","calib-v1",
            1280,720,10_000_000_000L,8_000_000L,33_333_333L,
            NativeVioInputGate.CameraClock.UNKNOWN);
        assertEquals(NativeVioInputGate.Code.CAMERA_CLOCK_UNKNOWN,
            valid.accept(unknown,imu(10_006_000_000L)).code);
    }
    @Test public void refusesDifferentCameraCropOrCalibrationVersion() {
        NativeVioInputGate gate=new NativeVioInputGate(calibration(true));
        NativeVioInputGate.Frame cropped=new NativeVioInputGate.Frame(
            "rear0","calib-v1",640,480,10_000_000_000L,8_000_000L,
            33_333_333L,NativeVioInputGate.CameraClock.REALTIME);
        assertEquals(NativeVioInputGate.Code.CAMERA_MISMATCH,
            gate.accept(cropped,imu(10_006_000_000L)).code);
    }
    @Test public void rejectsMissingImuAndDuplicateFrameWithoutConsumingTimestamp() {
        NativeVioInputGate gate=new NativeVioInputGate(calibration(true));
        long start=10_000_000_000L;
        assertEquals(NativeVioInputGate.Code.NO_BRACKETING_IMU,
            gate.accept(frame(start),imu(start+150_000_000L)).code);
        assertTrue(gate.accept(frame(start),imu(start+6_000_000L)).accepted());
        assertEquals(NativeVioInputGate.Code.NON_MONOTONIC_FRAME,
            gate.accept(frame(start),imu(start+6_000_000L)).code);
        assertTrue(gate.accept(frame(start+33_000_000L),
            imu(start+39_000_000L)).accepted());
    }
    @Test public void longCameraGapStartsNewSegmentAndPreventsSilentContinuity() {
        NativeVioInputGate gate=new NativeVioInputGate(calibration(true));
        long start=10_000_000_000L;
        assertTrue(gate.accept(frame(start),imu(start+6_000_000L)).accepted());
        NativeVioInputGate.Decision gap=gate.accept(frame(start+2_000_000_000L),
            imu(start+2_006_000_000L));
        assertTrue(gap.accepted());
        assertEquals(2,gap.acquisitionSegment);
        gate.reset();
        assertEquals(3,gate.segmentId());
    }
    @Test public void rejectsHugeImuWindowAndInvalidShutterFramePeriod() {
        NativeVioInputGate gate=new NativeVioInputGate(calibration(true));
        long t=10_000_000_000L;
        NativeVioInputGate.ImuBracket huge=new NativeVioInputGate.ImuBracket(
            t-200_000_000L,t+200_000_000L,t-200_000_000L,t+200_000_000L);
        assertEquals(NativeVioInputGate.Code.IMU_GAP,gate.accept(frame(t),huge).code);
        NativeVioInputGate.Frame invalid=new NativeVioInputGate.Frame("rear0","calib-v1",
            1280,720,t,40_000_000L,33_333_333L,NativeVioInputGate.CameraClock.REALTIME);
        assertEquals(NativeVioInputGate.Code.INVALID_CAPTURE,
            gate.accept(invalid,imu(t+6_000_000L)).code);
    }
}
