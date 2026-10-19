package dev.jboehm.vectorvio.location;

import android.Manifest;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class AndroidLocationSourceTest {
    @Test public void missingFinePermissionDegradesAndCloseDoesNotEmitFixes()throws Exception{
        Context context=RuntimeEnvironment.getApplication();
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        BlockingQueue<String> states=new LinkedBlockingQueue<>();
        AndroidLocationSource source=new AndroidLocationSource(context,new LocationSource.Listener(){
            public void onSample(LocationSample s){throw new AssertionError("No fix without permission");}
            public void onStatus(String s){states.offer(s);}
        });
        try{source.start(LocationSource.Mode.AUTO);assertEquals("PRECISE LOCATION REQUIRED",states.poll(3,TimeUnit.SECONDS));source.stop();assertEquals("LOCATION OFF",states.poll(3,TimeUnit.SECONDS));}
        finally{source.close();}
    }
}
