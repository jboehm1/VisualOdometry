package dev.jboehm.vectorvio;
import android.Manifest;
import android.app.Application;
import android.content.*;
import android.graphics.*;
import android.location.*;
import android.os.*;
import android.view.View;
import dev.jboehm.vectorvio.gps.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.*;
import java.util.*;
import java.lang.reflect.Field;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class GpsAndroidTest {
    private GpsRoute route(){return new GpsRoute("Route & test",Arrays.asList(new GpsRoute.Point(48,7,Double.NaN),new GpsRoute.Point(48.002,7.002,Double.NaN)));}
    @Test public void privateRouteRoundTripsThroughAndroidParserAndAtomicStorage()throws Exception{
        RouteRepository repository=new RouteRepository(RuntimeEnvironment.getApplication());String id=repository.save(Collections.singletonList(route()));
        try{assertEquals("Route & test",repository.read(id).get(0).name);assertTrue(repository.list().contains(id));try{repository.file("../escape.gpx");fail();}catch(java.io.IOException expected){}}
        finally{repository.delete(id);}
    }
    @Test public void routeScreenAndDiagramRenderWithoutLocationPermissionOrNetwork(){
        GpsNavigationService.running=false;
        ActivityController<GpsRoutesActivity> c=Robolectric.buildActivity(GpsRoutesActivity.class).create().start().resume();
        try{GpsRouteView view=new GpsRouteView(c.get());view.setRoute(route());view.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY));view.layout(0,0,360,360);view.draw(new Canvas(Bitmap.createBitmap(360,360,Bitmap.Config.ARGB_8888)));assertNull(shadowOf(c.get()).getNextStartedService());}
        finally{c.pause().stop().destroy();}
    }
    private Handler worker(GpsNavigationService service)throws Exception{Field f=GpsNavigationService.class.getDeclaredField("worker");f.setAccessible(true);return (Handler)f.get(service);}
    private void drain(Handler worker)throws Exception{CountDownLatch latch=new CountDownLatch(1);worker.post(latch::countDown);assertTrue(latch.await(5,TimeUnit.SECONDS));}
    @Test public void serviceStartsOnlyOnExplicitActionAndReleasesOnStop()throws Exception{
        Application app=RuntimeEnvironment.getApplication();shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION);
        LocationManager manager=(LocationManager)app.getSystemService(Context.LOCATION_SERVICE);shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER,true);
        RouteRepository repository=new RouteRepository(app);String id=repository.save(Collections.singletonList(route()));GpsNavigationService.running=false;
        Intent intent=new Intent(app,GpsNavigationService.class).setAction(GpsNavigationService.START).putExtra("route",id);
        ServiceController<GpsNavigationService> c=Robolectric.buildService(GpsNavigationService.class,intent).create();
        try{
            c.startCommand(0,1);Handler h=worker(c.get());drain(h);assertTrue(GpsNavigationService.running);assertNotNull(GpsNavigationService.latest.route);
            c.get().onStartCommand(new Intent(app,GpsNavigationService.class).setAction(GpsNavigationService.STOP),0,2);h.getLooper().getThread().join(5000);shadowOf(Looper.getMainLooper()).idle();assertFalse(GpsNavigationService.running);assertFalse(GpsNavigationService.latest.running);
        }finally{c.destroy();repository.delete(id);GpsNavigationService.running=false;}
    }
}
