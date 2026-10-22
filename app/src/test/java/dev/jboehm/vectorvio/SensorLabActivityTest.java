package dev.jboehm.vectorvio;

import android.app.Activity;
import android.content.Intent;
import android.widget.TextView;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Verifies phone-only Lab is reachable and never starts an implicit capture. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public final class SensorLabActivityTest {
    @Test public void noRecordingOnOpeningAndHardwareReportCannotPretendLwirOrRadar(){
        ActivityController<SensorLabActivity> c=Robolectric.buildActivity(SensorLabActivity.class).create().start().resume();
        try{
            SensorLabActivity a=c.get();
            assertNotNull(a.getWindow().getDecorView());
            android.widget.Spinner context=a.getWindow()
                .getDecorView().findViewWithTag("sensor_lab_condition");
            assertNotNull(context);
            assertEquals(8,context.getCount());
            assertEquals("STATIONARY_BRIGHT",context.getSelectedItem());
            java.util.ArrayList<View> matches=new java.util.ArrayList<>();
            a.getWindow().getDecorView().findViewsWithText(matches,"POSITION: UNKNOWN",View.FIND_VIEWS_WITH_TEXT);
            assertFalse(matches.isEmpty());
            org.json.JSONObject probe=SensorCapabilityReport.collect(a);
            assertFalse(probe.getBoolean("external_radar_or_lwir_supported_by_this_app"));
            assertFalse(probe.getBoolean("independent_ground_truth"));
            assertNotNull(probe.getJSONArray("sensors"));
            assertNotNull(probe.getJSONArray("cameras"));
            assertEquals("ANDROID_RUNTIME_PROBE_NOT_DEVICE_ACCURACY",probe.getString("source"));
        }catch(org.json.JSONException e){throw new AssertionError(e);}
        finally{c.pause().stop().destroy();}
    }
    @Test public void settingsRouteUsesTypedActionAndCameraRemainsOptional(){
        ActivityController<SettingsActivity> c=Robolectric.buildActivity(SettingsActivity.class).create().start().resume();
        try{
            SettingsActivity a=c.get();
            a.getWindow().getDecorView().findViewWithTag("tab_LOCATION").performClick();
            View item=a.getWindow().getDecorView().findViewWithTag("setting_SENSOR_LAB");
            assertNotNull(item);
            item.performClick();
            Intent result=Shadows.shadowOf(a).getResultIntent();
            assertEquals(Activity.RESULT_OK,Shadows.shadowOf(a).getResultCode());
            assertEquals("SENSOR_LAB",result.getStringExtra(SettingsActivity.EXTRA_ACTION));
            assertEquals(SettingsCatalog.Section.LOCATION,
                SettingsCatalog.byId(SettingsCatalog.Action.SENSOR_LAB).section);
        }finally{c.pause().stop().destroy();}
    }
}
