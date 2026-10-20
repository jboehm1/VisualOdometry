package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;

public final class CameraCaptureQualityTest {
    @Test public void highSpeedLongShutterIsWarned(){
        CameraCaptureQuality.Assessment p=CameraCaptureQuality.classify(30_000_000L,2000,1.2,true);
        assertEquals(CameraCaptureQuality.State.DARK_AND_BLURRED,p.state);
        assertEquals(30,p.exposureMs,1e-9);assertTrue(p.angularBlurDeg>2);
    }
    @Test public void highIsoAloneIsNotMotionBlur(){
        CameraCaptureQuality.Assessment p=CameraCaptureQuality.classify(5_000_000L,2000,0,true);
        assertEquals(CameraCaptureQuality.State.HIGH_ISO,p.state);
        assertEquals(0,p.angularBlurDeg,1e-9);
    }
    @Test public void shortExposureAvoidsAngularSmear(){
        CameraCaptureQuality.Assessment p=CameraCaptureQuality.classify(1_000_000L,400,2,true);
        assertEquals(CameraCaptureQuality.State.NORMAL,p.state);
    }
    @Test public void missingDataDoesNotInventQuality(){
        assertEquals(CameraCaptureQuality.State.UNKNOWN,
            CameraCaptureQuality.classify(0,0,Double.NaN,true).state);
        assertEquals(CameraCaptureQuality.State.TRACKING_LOST,
            CameraCaptureQuality.classify(10_000_000L,300,1,false).state);
    }
}
