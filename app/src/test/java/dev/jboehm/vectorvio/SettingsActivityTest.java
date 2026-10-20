package dev.jboehm.vectorvio;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.Switch;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Real screen contract: readable reachable settings, no old nested AlertDialog navigation. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public final class SettingsActivityTest {
    private ActivityController<SettingsActivity> open(){return Robolectric.buildActivity(SettingsActivity.class).create().start().resume();}
    @Test public void everyCategoryCanBeSelectedAndAllActionsHaveExplanations(){
        ActivityController<SettingsActivity> c=open();
        try{
            SettingsActivity a=c.get();
            for(SettingsCatalog.Section section:SettingsCatalog.Section.values()){
                View tab=a.getWindow().getDecorView().findViewWithTag("tab_"+section.name());
                assertNotNull(tab);tab.performClick();
                for(SettingsCatalog.Item item:SettingsCatalog.items(section)){
                    assertNotNull(item.action.name(),a.getWindow().getDecorView().findViewWithTag("setting_"+item.action.name()));
                    assertFalse(SettingsPresentation.title(item.action).trim().isEmpty());
                    assertTrue(SettingsPresentation.explanation(item.action).length()>28);
                }
            }
        }finally{c.pause().stop().destroy();}
    }
    @Test public void togglesPersistWithoutClosingSettingsAndSurviveReopen(){
        org.robolectric.RuntimeEnvironment.getApplication().getSharedPreferences("spatial_explorer",Activity.MODE_PRIVATE)
            .edit().putBoolean("bike_view_hints",true).commit();
        ActivityController<SettingsActivity> c=open();
        try{
            SettingsActivity a=c.get();
            a.getWindow().getDecorView().findViewWithTag("tab_DISPLAY").performClick();
            Switch bike=(Switch)a.getWindow().getDecorView().findViewWithTag("switch_BIKE_VIEW_HINTS");
            assertNotNull(bike);assertTrue(bike.isChecked());
            bike.setChecked(false);
            assertFalse(a.getSharedPreferences("spatial_explorer",Activity.MODE_PRIVATE).getBoolean("bike_view_hints",true));
            assertFalse(a.isFinishing());
        }finally{c.pause().stop().destroy();}
        ActivityController<SettingsActivity> d=open();
        try{
            SettingsActivity a=d.get();
            a.getWindow().getDecorView().findViewWithTag("tab_DISPLAY").performClick();
            assertFalse(((Switch)a.getWindow().getDecorView().findViewWithTag("switch_BIKE_VIEW_HINTS")).isChecked());
        }finally{d.pause().stop().destroy();}
    }
    @Test public void extendedGpxIsAnOptOutNativeSwitch(){
        org.robolectric.RuntimeEnvironment.getApplication()
            .getSharedPreferences("spatial_explorer",Activity.MODE_PRIVATE)
            .edit().putBoolean("gpx_extended_preview",true).commit();
        ActivityController<SettingsActivity> c=open();
        try{
            SettingsActivity a=c.get();
            View row=a.getWindow().getDecorView().findViewWithTag("setting_GPX_EXTENDED_PREVIEW");
            assertNotNull(row);
            Switch enabled=(Switch)a.getWindow().getDecorView().findViewWithTag("switch_GPX_EXTENDED_PREVIEW");
            assertTrue(enabled.isChecked());
            enabled.setChecked(false);
            assertFalse(a.getSharedPreferences("spatial_explorer",Activity.MODE_PRIVATE)
                .getBoolean("gpx_extended_preview",true));
        }finally{c.pause().stop().destroy();}
    }

    @Test public void searchFindsGpxAcrossSectionsAndUsesTypedAction(){
        ActivityController<SettingsActivity> c=open();
        try{
            SettingsActivity a=c.get();
            EditText search=(EditText)a.getWindow().getDecorView().findViewWithTag("settings_search");
            assertNotNull(search);search.setText("GPX");
            View match=a.getWindow().getDecorView().findViewWithTag("setting_GPX_AR");
            assertNotNull(match);match.performClick();
            assertEquals(Activity.RESULT_OK,Shadows.shadowOf(a).getResultCode());
            assertEquals("GPX_AR",Shadows.shadowOf(a).getResultIntent().getStringExtra(SettingsActivity.EXTRA_ACTION));
        }finally{c.pause().stop().destroy();}
    }
}
