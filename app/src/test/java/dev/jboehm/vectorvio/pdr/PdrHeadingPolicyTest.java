package dev.jboehm.vectorvio.pdr;

import android.hardware.SensorManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Consent, physical pose, magnetic quality and freshness are hard gates. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public final class PdrHeadingPolicyTest {
    private static boolean use(boolean allow,float worldUp,boolean magnet,int accuracy,long age){
        return PdrSensorBridge.usablePhoneHeading(allow,worldUp,magnet,accuracy,age);
    }
    @Test public void distanceOnlyNeverUsesDeviceYaw(){
        assertFalse(use(false,1,true,SensorManager.SENSOR_STATUS_ACCURACY_HIGH,0));
    }
    @Test public void phoneForwardRequiresLevelFaceUpAndFreshMagnet(){
        int ok=SensorManager.SENSOR_STATUS_ACCURACY_HIGH;
        assertTrue(use(true,1,true,ok,0));
        assertTrue(use(true,.8f,true,ok,1_000_000_000L));
        assertFalse(use(true,0,true,ok,0)); // phone vertical
        assertFalse(use(true,-1,true,ok,0)); // screen facing down
        assertFalse(use(true,.7f,true,ok,0)); // tilted >45°
        assertFalse(use(true,Float.NaN,true,ok,0));
        assertFalse(use(true,1,false,ok,0));
        assertFalse(use(true,1,true,ok,-1));
        assertFalse(use(true,1,true,ok,2_000_000_001L));
        assertFalse(use(true,1,true,SensorManager.SENSOR_STATUS_UNRELIABLE,0));
    }
}
