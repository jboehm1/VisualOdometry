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

/** Validate the B6 action coexists with B1-B8, PDR and GPX. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public final class StoreMenuIntegrationTest {
    @Test public void storePlannerLaunchesWithoutDisplacingPdrOrGpx(){
        ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).create();
        try {
            ArrayList<View> views=new ArrayList<>();
            c.get().getWindow().getDecorView().findViewsWithText(views,"MENU",View.FIND_VIEWS_WITH_TEXT);
            assertEquals(1,views.size());assertTrue(views.get(0).performClick());
            AlertDialog root=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            assertEquals(6,root.getListView().getCount());
            root.getListView().performItemClick(null,SettingsCatalog.Section.NAVIGATION.ordinal(),
                SettingsCatalog.Section.NAVIGATION.ordinal());
            AlertDialog category=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull("The category should display a real clickable ListView",category.getListView());
            assertEquals(SettingsCatalog.count(SettingsCatalog.Section.NAVIGATION),category.getListView().getCount());
            java.util.List<SettingsCatalog.Item> actions=SettingsCatalog.items(SettingsCatalog.Section.NAVIGATION);
            int course=-1,pdr=-1,gpx=-1;
            for(int i=0;i<actions.size();i++){
                if(actions.get(i).action==SettingsCatalog.Action.COURSES)course=i;
                if(actions.get(i).action==SettingsCatalog.Action.PDR)pdr=i;
                if(actions.get(i).action==SettingsCatalog.Action.GPX_AR)gpx=i;
            }
            assertTrue(course>=0&&pdr>=0&&gpx>=0);
            assertNotEquals(course,pdr);assertNotEquals(course,gpx);
            category.getListView().performItemClick(null,course,course);
            Intent started=Shadows.shadowOf(c.get()).getNextStartedActivity();
            assertNotNull(started);
            assertEquals(StorePlannerActivity.class.getName(),started.getComponent().getClassName());
        }finally{c.destroy();}
    }
}
