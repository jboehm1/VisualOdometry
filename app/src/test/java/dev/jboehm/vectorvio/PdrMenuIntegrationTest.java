package dev.jboehm.vectorvio;
import android.app.AlertDialog;
import android.content.Intent;
import android.view.View;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Prove the combined menu launches the PDR screen without losing GPS/GPX. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public final class PdrMenuIntegrationTest {
    @Test public void noCameraPdrIsReachableFromCurrentNavigationCategory(){
        ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).create();
        try {
            ArrayList<View> matches=new ArrayList<>();
            c.get().getWindow().getDecorView().findViewsWithText(matches,"MENU",View.FIND_VIEWS_WITH_TEXT);
            assertEquals(1,matches.size());assertTrue(matches.get(0).performClick());
            org.robolectric.shadows.ShadowActivity.IntentForResult request=
                Shadows.shadowOf(c.get()).getNextStartedActivityForResult();
            assertEquals(SettingsActivity.class.getName(),request.intent.getComponent().getClassName());
            ActivityController<SettingsActivity> page=Robolectric.buildActivity(SettingsActivity.class).create().start().resume();
            try{
                assertTrue(page.get().getWindow().getDecorView().findViewWithTag("tab_NAVIGATION").performClick());
                View row=page.get().getWindow().getDecorView().findViewWithTag("setting_PDR");
                assertNotNull(row);
                row.performClick();
                Intent result=Shadows.shadowOf(page.get()).getResultIntent();
                assertEquals("PDR",result.getStringExtra(SettingsActivity.EXTRA_ACTION));
                c.get().onActivityResult(147,android.app.Activity.RESULT_OK,result);
            }finally{page.pause().stop().destroy();}
            
            java.util.List<SettingsCatalog.Item> actions=SettingsCatalog.items(SettingsCatalog.Section.NAVIGATION);
            int found=-1;
            for(int i=0;i<actions.size();i++)if(actions.get(i).action==SettingsCatalog.Action.PDR)found=i;
            assertTrue("PDR must not collide with GPX AR, 2D GPS or manual slide",found>=0);

            Intent intent=Shadows.shadowOf(c.get()).getNextStartedActivity();
            assertNotNull(intent);
            assertNotNull(intent.getComponent());
            assertEquals(dev.jboehm.vectorvio.pdr.PdrActivity.class.getName(),intent.getComponent().getClassName());
            assertFalse("Opening PDR must not silently start the video recorder",
              c.get().getSharedPreferences("spatial_explorer",0).getBoolean("training_enabled",false));
        }finally{c.destroy();}
    }
}