package dev.jboehm.vectorvio;

import android.app.AlertDialog;
import android.content.Intent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import android.view.View;
import java.nio.file.*;
import java.util.*;
import dev.jboehm.vectorvio.gpxar.GpxArRouteStore;
import dev.jboehm.vectorvio.gps.GpsRoute;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class GpxArAndroidTest {

    private static AlertDialog openCategory(ActivityController<MainActivity> c,SettingsCatalog.Section section){
        ArrayList<View> buttons=new ArrayList<>();
        c.get().getWindow().getDecorView().findViewsWithText(buttons,"MENU",View.FIND_VIEWS_WITH_TEXT);
        assertEquals(1,buttons.size());assertTrue(buttons.get(0).performClick());
        AlertDialog root=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        assertEquals(6,root.getListView().getCount());
        for(int i=0;i<6;i++)assertTrue(root.getListView().getAdapter().getItem(i).toString()
            .startsWith(SettingsCatalog.Section.values()[i].title));
        root.getListView().performItemClick(null,section.ordinal(),section.ordinal());
        AlertDialog category=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        assertEquals(SettingsCatalog.count(section),category.getListView().getCount());
        return category;
    }
    private static AlertDialog openGpx(ActivityController<MainActivity> c){
        AlertDialog category=openCategory(c,SettingsCatalog.Section.NAVIGATION);
        assertTrue(category.getListView().getAdapter().getItem(0).toString().startsWith("GPX AR"));
        category.getListView().performItemClick(null,0,0);
        return org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    }
    @Test public void categorizedMenuPreservesGpxImportAndOtherModules()throws Exception{
        ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).create();
        try{
            AlertDialog gpx=openGpx(c);
            assertEquals(12,gpx.getListView().getCount());
            assertTrue(gpx.getListView().getAdapter().getItem(8).toString().startsWith("ADD ALTITUDE"));
            assertTrue(gpx.getListView().getAdapter().getItem(10).toString().contains("MANUAL GPX"));
            gpx.getListView().performItemClick(null,1,1);
            Intent request=Shadows.shadowOf(c.get()).getNextStartedActivity();
            assertNotNull(request);assertEquals(Intent.ACTION_OPEN_DOCUMENT,request.getAction());
        }finally{c.destroy();}
        ActivityController<MainActivity> next=Robolectric.buildActivity(MainActivity.class).create();
        try{
            AlertDialog locations=openCategory(next,SettingsCatalog.Section.LOCATION);
            assertTrue(locations.getListView().getAdapter().getItem(1).toString().contains("GPS ↔ FUSED"));
            locations.getListView().performItemClick(null,1,1);
            Intent request=Shadows.shadowOf(next.get()).getNextStartedActivity();
            assertNotNull(request);assertEquals(LocationComparisonActivity.class.getName(),request.getComponent().getClassName());
        }finally{next.destroy();}
        ActivityController<MainActivity> replay=Robolectric.buildActivity(MainActivity.class).create();
        try{
            AlertDialog capture=openCategory(replay,SettingsCatalog.Section.CAPTURE);
            assertTrue(capture.getListView().getAdapter().getItem(2).toString().contains("ARCORE REPLAY"));
            capture.getListView().performItemClick(null,2,2);
            assertTrue(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getTitle().toString().contains("ARCore replay"));
        }finally{replay.destroy();}
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

    @Test public void displayOptionsPersistWithoutStartingCaptureOrLocation()throws Exception{
        org.robolectric.RuntimeEnvironment.getApplication().getSharedPreferences("spatial_explorer",0)
            .edit().putInt("gpx_display_flags",31).commit();
        ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).create();
        try{
            AlertDialog gpx=openGpx(c);gpx.getListView().performItemClick(null,7,7);
            AlertDialog options=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            assertEquals(5,options.getListView().getCount());
            android.widget.ListView list=options.getListView();
            list.setItemChecked(0,true);
            list.performItemClick(null,0,0);assertFalse(list.isItemChecked(0));
            assertEquals(0,c.get().getSharedPreferences("spatial_explorer",0).getInt("gpx_display_flags",31)&1);
            list.performItemClick(null,0,0);assertTrue(list.isItemChecked(0));
            assertEquals(1,c.get().getSharedPreferences("spatial_explorer",0).getInt("gpx_display_flags",0)&1);
            list.performItemClick(null,0,0);assertFalse(list.isItemChecked(0));
            assertNull(Shadows.shadowOf(c.get()).getNextStartedService());
            assertNull(Shadows.shadowOf(c.get()).getNextStartedActivity());
        }finally{c.destroy();}
        ActivityController<MainActivity> next=Robolectric.buildActivity(MainActivity.class).create();
        try{assertEquals(0,next.get().getSharedPreferences("spatial_explorer",0).getInt("gpx_display_flags",31)&1);}
        finally{next.destroy();}
    }
    @Test public void depthToggleSurvivesRecreationAndDoesNotEnableCapture(){
        org.robolectric.RuntimeEnvironment.getApplication().getSharedPreferences("spatial_explorer",0)
            .edit().putBoolean("gpx_depth_occlusion",false).commit();
        ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).create();
        try{
            AlertDialog gpx=openGpx(c);assertEquals("RAW DEPTH OCCLUSION · OFF",gpx.getListView().getAdapter().getItem(9));
            gpx.getListView().performItemClick(null,9,9);
            assertTrue(c.get().getSharedPreferences("spatial_explorer",0).getBoolean("gpx_depth_occlusion",false));
            assertNull(Shadows.shadowOf(c.get()).getNextStartedService());
        }finally{c.destroy();}
        ActivityController<MainActivity> next=Robolectric.buildActivity(MainActivity.class).create();
        try{
            AlertDialog menu=openGpx(next);
            assertEquals("RAW DEPTH OCCLUSION · ON",menu.getListView().getAdapter().getItem(9));
            assertNull(Shadows.shadowOf(next.get()).getNextStartedService());
        }finally{next.destroy();}
    }
    @Test public void manualAdjustmentRequiresLiveArAndFreshAlignment(){
        ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).create();
        try{
            AlertDialog settings=openCategory(c,SettingsCatalog.Section.NAVIGATION);
            assertTrue(settings.getListView().getAdapter().getItem(1).toString().startsWith("Adjust GPX on screen"));
            settings.getListView().performItemClick(null,1,1);
            assertNull("No camera or tracking is started",Shadows.shadowOf(c.get()).getNextStartedService());
            assertNull(Shadows.shadowOf(c.get()).getNextStartedActivity());
        }finally{c.destroy();}
    }
}
