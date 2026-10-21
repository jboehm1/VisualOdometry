package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;

public final class VisualObservabilityGateTest {
    private final VisualObservabilityGate g=new VisualObservabilityGate();
    private static CameraCaptureQuality.State ok=CameraCaptureQuality.State.NORMAL;
    @Test public void trackingMustStabilizeAndNeverProducesMetricConfidence() {
        assertEquals(VisualObservabilityGate.State.VISUAL_CAUTION,g.update(
            1_000_000_000L,1,true,true,ok,false).state);
        VisualObservabilityGate.Decision d=g.update(1_020_000_000L,2,true,true,ok,false);
        assertEquals(VisualObservabilityGate.State.VISUAL_OBSERVED,d.state);
        assertTrue(d.arcorePoseAvailable);assertFalse(d.translationFromInertial);
    }
    @Test public void onlyShortOrientationAfterLossNoDeadReckonedDistance() {
        g.update(1_000_000_000L,1,true,true,ok,false);
        g.update(1_010_000_000L,2,true,true,ok,false);
        VisualObservabilityGate.Decision d=g.update(1_500_000_000L,3,false,true,ok,false);
        assertEquals(VisualObservabilityGate.State.ORIENTATION_ONLY,d.state);
        assertFalse(d.arcorePoseAvailable);assertFalse(d.translationFromInertial);
        assertEquals(VisualObservabilityGate.State.POSITION_UNKNOWN,g.update(
            2_520_000_000L,4,false,true,ok,false).state);
    }
    @Test public void startupLossStaysUnknownEvenWithImu() {
        assertEquals(VisualObservabilityGate.State.POSITION_UNKNOWN,g.update(
            100_000_000L,1,false,true,ok,null).state);
    }
    @Test public void blurTextureAndStaleImuAreWarned() {
        g.update(100_000_000L,1,true,true,ok,false);
        assertEquals("BLUR_RISK",g.update(110_000_000L,2,true,true,
            CameraCaptureQuality.State.DARK_AND_BLURRED,false).reason);
        assertEquals("LOW_TEXTURE",g.update(120_000_000L,3,true,true,ok,true).reason);
        assertEquals("IMU_STALE",g.update(130_000_000L,4,true,false,ok,false).reason);
        assertEquals(VisualObservabilityGate.State.VISUAL_CAUTION,g.update(
            140_000_000L,5,true,true,ok,false).state);
        assertEquals(VisualObservabilityGate.State.VISUAL_OBSERVED,g.update(
            150_000_000L,6,true,true,ok,false).state);
    }
    @Test public void invalidAndDuplicateClocksFailClosed() {
        g.update(100_000_000L,1,true,true,ok,false);
        assertEquals("DUPLICATE_OR_INVALID_CLOCK",g.update(
            110_000_000L,1,true,true,ok,false).reason);
        assertEquals(VisualObservabilityGate.State.POSITION_UNKNOWN,g.update(
            110_000_000L,2,true,true,ok,false).state);
        g.reset();
        assertEquals(VisualObservabilityGate.State.POSITION_UNKNOWN,g.update(
            0,4,true,true,ok,false).state);
    }
    @Test public void rebootOrSessionResetCannotReuseAnchor() {
        g.update(100_000_000L,1,true,true,ok,false);
        g.reset();
        assertEquals(VisualObservabilityGate.State.POSITION_UNKNOWN,g.update(
            200_000_000L,2,false,true,ok,false).state);
    }
}
