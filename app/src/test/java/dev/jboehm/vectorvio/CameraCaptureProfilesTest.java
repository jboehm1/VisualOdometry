package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;
public final class CameraCaptureProfilesTest {
 @Test public void defaultNeverAssumes60(){assertEquals(30,CameraCaptureProfiles.actualFps(CameraCaptureProfiles.fromPreference("unknown"),true));}
 @Test public void onlyFastRequests60(){assertEquals(60,CameraCaptureProfiles.desiredFps(CameraCaptureProfiles.Profile.FAST_MOTION_60));}
 @Test public void unsupported60FallsBack(){assertEquals(30,CameraCaptureProfiles.actualFps(CameraCaptureProfiles.Profile.FAST_MOTION_60,false));}
 @Test public void lowLightAutofocusIsDistinct(){assertTrue(CameraCaptureProfiles.autoFocus(CameraCaptureProfiles.Profile.LOW_LIGHT_30));assertFalse(CameraCaptureProfiles.autoFocus(CameraCaptureProfiles.Profile.BALANCED_30));}
}
