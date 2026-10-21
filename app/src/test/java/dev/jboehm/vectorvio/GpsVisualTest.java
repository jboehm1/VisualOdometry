package dev.jboehm.vectorvio;
import android.graphics.*;
import android.os.*;
import android.view.View;
import dev.jboehm.vectorvio.gps.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import java.io.*;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
/** Native Android Canvas fixtures, not physical-phone screenshots. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GpsVisualTest {
    private GpsRoute.Point p(double x,double y){return new GpsRoute.Point(48+Math.toDegrees(y/Geo.R),7+Math.toDegrees(x/Geo.R/Math.cos(Math.toRadians(48))),Double.NaN);}
    private void render(View root,String name,int w,int h)throws Exception{
        root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);
        Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);root.draw(new Canvas(b));
        File dir=new File("build/reports/tests/visual-fixtures");assertTrue(dir.isDirectory()||dir.mkdirs());try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}
    }
    @Test public void rendersGuidanceAndUncertaintyAtTwoPhoneSizes()throws Exception{
        org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofSeconds(100));
        GpsRoute r=new GpsRoute("Promenade · fixture synthétique",Arrays.asList(p(0,0),p(200,0),p(200,100),p(50,100),p(50,250),p(0,250),p(0,0)));
        GpsNavigator.Fix f=new GpsNavigator.Fix(p(70,8).lat,p(70,8).lon,8,2,90,Double.NaN,SystemClock.elapsedRealtimeNanos());
        GpsNavigationService.State previous=GpsNavigationService.latest;boolean wasRunning=GpsNavigationService.running;
        GpsNavigationService.latest=new GpsNavigationService.State(r,f,GpsNavigator.Status.FOLLOWING,70,8,90,true,false,0,"");GpsNavigationService.running=true;
        ActivityController<GpsRoutesActivity> c=Robolectric.buildActivity(GpsRoutesActivity.class).create().start().resume().visible();
        try{
            shadowOf(Looper.getMainLooper()).idle();View root=c.get().getWindow().getDecorView();render(root,"gps_following_393",393,852);render(root,"gps_following_small",360,640);
            GpsNavigationService.latest=new GpsNavigationService.State(r,f,GpsNavigator.Status.AMBIGUOUS,70,8,Double.NaN,true,false,0,"Plusieurs branches compatibles avec le GPS");shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));render(root,"gps_ambiguous",393,852);
        }finally{c.pause().stop().destroy();GpsNavigationService.latest=previous;GpsNavigationService.running=wasRunning;}
    }
}
