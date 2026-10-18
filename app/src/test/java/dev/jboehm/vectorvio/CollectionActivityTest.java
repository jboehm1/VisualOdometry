package dev.jboehm.vectorvio;
import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class CollectionActivityTest {
    @Test public void reopeningControllerDoesNotRestartOrStopAnExistingRecording(){
        CollectionService.running=true;CollectionService.status="REC · existing";
        ActivityController<CollectionActivity> c=null;
        try{
            c=Robolectric.buildActivity(CollectionActivity.class).create().start().resume().visible();
            assertNull(shadowOf(c.get()).getNextStartedService());
            c.pause().stop().destroy();c=null;
            assertTrue(CollectionService.running);
        }finally{if(c!=null)c.pause().stop().destroy();CollectionService.running=false;CollectionService.status="Stopped";}
    }
}
