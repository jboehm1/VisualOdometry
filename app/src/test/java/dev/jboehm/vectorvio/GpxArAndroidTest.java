package dev.jboehm.vectorvio;

import android.app.AlertDialog;
import android.content.Intent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import dev.jboehm.vectorvio.gpxar.GpxArRouteStore;
import dev.jboehm.vectorvio.gps.GpsRoute;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class GpxArAndroidTest {
    @Test public void dataActionCoexistsWithRoutesReplayAndFusedComparison()throws Exception{
        ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).create();
        try{
            Method menu=MainActivity.class.getDeclaredMethod("dataMenu");menu.setAccessible(true);menu.invoke(c.get());
            AlertDialog data=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            Set<String> labels=new HashSet<>();for(int i=0;i<data.getListView().getCount();i++)labels.add(data.getListView().getAdapter().getItem(i).toString());
            assertTrue(labels.contains("GPS ROUTES — import GPX / follow / record"));
            assertTrue(labels.contains("GPS ↔ FUSED — Android location A/B (experimental)"));
            assertTrue(labels.contains("ARCORE REPLAY — evaluate saved video with same odometer"));
            int position=-1;for(int i=0;i<data.getListView().getCount();i++)if(data.getListView().getAdapter().getItem(i).equals("GPX AR — experimental camera guidance"))position=i;
            assertTrue(position>=0);data.getListView().performItemClick(null,position,position);
            AlertDialog ar=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertEquals(7,ar.getListView().getCount());
            ar.getListView().performItemClick(null,1,1);
            Intent request=Shadows.shadowOf(c.get()).getNextStartedActivity();assertNotNull(request);assertEquals(Intent.ACTION_OPEN_DOCUMENT,request.getAction());
        }finally{c.destroy();}
    }
    @Test public void selectedRouteRoundTripPreservesElevationAndInvalidReplacementKeepsPrevious()throws Exception{
        Path temp=Files.createTempDirectory("gpx-ar-store");GpxArRouteStore store=new GpxArRouteStore(temp.toFile());
        try{
            assertNull(store.load());
            GpsRoute route=new GpsRoute("retour & pente",Arrays.asList(new GpsRoute.Point(48,7,300),new GpsRoute.Point(48.001,7.001,Double.NaN)));
            store.save(route);GpsRoute actual=store.load();assertEquals(route.name,actual.name);assertEquals(300,actual.points.get(0).ele,0);assertTrue(Double.isNaN(actual.points.get(1).ele));
            try{store.save(new GpsRoute("polar",Arrays.asList(new GpsRoute.Point(89,0,0),new GpsRoute.Point(89,.01,0))));fail();}catch(IllegalArgumentException expected){}
            assertEquals(route.name,store.load().name);store.clear();assertNull(store.load());
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(temp)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(Exception ignored){}});}}
    }
}
