package dev.jboehm.vectorvio.pdr;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.content.Context;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowSensor;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public final class PdrAndroidTest {
    @Test public void cannotRecordWithoutAnActualStepSource() throws Exception {
        ActivityController<PdrActivity> c=Robolectric.buildActivity(PdrActivity.class).create().start().resume();
        try{
            Field f=PdrActivity.class.getDeclaredField("engine");f.setAccessible(true);
            PdrEngine engine=(PdrEngine)f.get(c.get());
            c.get().startSession();
            assertFalse(engine.isRecording());
        }finally{c.stop().destroy();}
    }
    @Test public void defaultSensorBridgeDoesNotOptIntoDeviceHeading(){
        android.app.Application app=org.robolectric.RuntimeEnvironment.getApplication();
        PdrSensorBridge bridge=new PdrSensorBridge(app,new PdrEngine(),new PdrSensorBridge.Callback(){
            public void sample(String type,long t,double x,double y,double z,int accuracy){}
            public void step(long t){}
            public void rotationVector(long t,float[] v,int accuracy){}
        });
        bridge.start(false);
        assertFalse(bridge.isDeviceHeadingEnabled());
        bridge.stop();
    }
    @Test public void screenStartsWithoutCameraAndStopsOnBackground() throws Exception {
        ActivityController<PdrActivity> c=Robolectric.buildActivity(PdrActivity.class).create().start().resume();
        try{
            // In Robolectric no phone hardware exists by default. Declare one
            // accelerometer so the real PDR START safety gate can succeed.
            SensorManager sensors=(SensorManager)c.get().getSystemService(Context.SENSOR_SERVICE);
            Shadows.shadowOf(sensors).addSensor(ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER));
            Field f=PdrActivity.class.getDeclaredField("engine");f.setAccessible(true);
            PdrEngine engine=(PdrEngine)f.get(c.get());
            c.get().startSession();
            assertTrue(engine.isRecording());
            c.pause();assertFalse(engine.isRecording());
            assertEquals(0,engine.snapshot(1).cadenceStepsPerMin,0);
        }finally{c.stop().destroy();}
    }
}
