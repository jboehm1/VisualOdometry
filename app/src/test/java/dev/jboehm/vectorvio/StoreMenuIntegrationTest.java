package dev.jboehm.vectorvio;
import android.app.Activity;
import android.content.Intent;
import android.view.View;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Verify MENU opens full-screen page; semantic action returns without positional indexing. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public final class StoreMenuIntegrationTest {
    @Test public void mainMenuOpensRealActivityAndDispatchesCoursePlanner(){
        ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).create();
        try{
            ArrayList<View> views=new ArrayList<>();
            c.get().getWindow().getDecorView().findViewsWithText(views,"MENU",View.FIND_VIEWS_WITH_TEXT);
            assertEquals(1,views.size());assertTrue(views.get(0).performClick());
            ShadowActivity.IntentForResult started=Shadows.shadowOf(c.get()).getNextStartedActivityForResult();
            assertNotNull(started);
            assertEquals(SettingsActivity.class.getName(),started.intent.getComponent().getClassName());
            SettingsCatalog.Item item=SettingsCatalog.byId(SettingsCatalog.Action.COURSES);
            assertEquals(SettingsCatalog.Section.NAVIGATION,item.section);
            c.get().onActivityResult(147,Activity.RESULT_OK,
                new Intent().putExtra(SettingsActivity.EXTRA_ACTION,item.action.name()));
            Intent planner=Shadows.shadowOf(c.get()).getNextStartedActivity();
            assertNotNull(planner);
            assertEquals(StorePlannerActivity.class.getName(),planner.getComponent().getClassName());
        }finally{c.destroy();}
    }
}
