package dev.jboehm.vectorvio.pdr;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public final class PdrAndroidTest {
    @Test public void screenStartsWithoutCameraAndStopsOnBackground() throws Exception {
        ActivityController<PdrActivity> c=Robolectric.buildActivity(PdrActivity.class).create().start().resume();
        try{
            Field f=PdrActivity.class.getDeclaredField("engine");f.setAccessible(true);
            PdrEngine engine=(PdrEngine)f.get(c.get());
            c.get().startSession();
            assertTrue(engine.isRecording());
            c.pause();assertFalse(engine.isRecording());
        }finally{c.stop().destroy();}
    }
}
