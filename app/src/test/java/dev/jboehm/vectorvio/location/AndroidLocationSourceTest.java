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
    @Test public void unavailableGoogleFallsBackOnlyInAutoModeAndGpsCanBeDisabled()throws Exception{
        Context context=RuntimeEnvironment.getApplication();
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        android.location.LocationManager manager=(android.location.LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
        Shadows.shadowOf(manager).setProviderEnabled(android.location.LocationManager.GPS_PROVIDER,true);
        BlockingQueue<String> states=new LinkedBlockingQueue<>();
        AndroidLocationSource source=new AndroidLocationSource(context,new LocationSource.Listener(){
            public void onSample(LocationSample s){}
            public void onStatus(String s){states.offer(s);}
        },()->false);
        try{
            source.start(LocationSource.Mode.FUSED);assertEquals("GOOGLE PLAY SERVICES UNAVAILABLE",states.poll(3,TimeUnit.SECONDS));
            source.start(LocationSource.Mode.AUTO);assertEquals("GPS NATIVE · SEARCHING",states.poll(3,TimeUnit.SECONDS));
            source.stop();assertEquals("LOCATION OFF",states.poll(3,TimeUnit.SECONDS));
            Shadows.shadowOf(manager).setProviderEnabled(android.location.LocationManager.GPS_PROVIDER,false);
            source.start(LocationSource.Mode.GPS);assertEquals("GPS DISABLED · ENABLE LOCATION",states.poll(3,TimeUnit.SECONDS));
        }finally{source.close();}
    }

}
