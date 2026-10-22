package dev.jboehm.vectorvio;

import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Actual custom-mode screen must reuse the ARCore HudView, not a status-only lab. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public final class UnifiedEngineHudTest {
    @Test public void baselineAndBasaltExposeOneVisibleEngineSelectorAndNativeSharedHud(){
        var baseline=Robolectric.buildActivity(MainActivity.class).create();
        try {
            assertNotNull(baseline.get().getWindow().getDecorView().findViewWithTag("engine_switcher"));
        }finally{baseline.destroy();}
        Intent intent=new Intent(RuntimeEnvironment.getApplication(),Camera2ManualActivity.class)
            .putExtra(Camera2ManualActivity.EXTRA_CUSTOM_VIO,true);
        var custom=Robolectric.buildActivity(Camera2ManualActivity.class,intent).create();
        try{
            var root=custom.get().getWindow().getDecorView();
            assertNotNull(root.findViewWithTag("custom_camera_fullscreen"));
            View hud=root.findViewWithTag("shared_vector_hud");
            assertNotNull(hud);
            assertTrue(hud instanceof HudView);
            assertNotNull(root.findViewWithTag("engine_switcher"));
            assertNotNull(root.findViewWithTag("custom_start"));
            assertNotNull(root.findViewWithTag("custom_stop"));
            assertNotNull(root.findViewWithTag("custom_export"));
            assertNotNull("Previously saved Basalt ZIPs must stay discoverable",
                root.findViewWithTag("basalt_history"));
            root.findViewWithTag("engine_switcher").performClick();
            var dialog=ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(dialog);
            assertNotNull("Selector must render real list items, not setMessage overshadowing setItems",
                dialog.getListView());
            assertEquals(3,dialog.getListView().getAdapter().getCount());
        }finally{custom.destroy();}
    }
    @Test public void ordinaryManualCameraRemainsSeparateFromExperimentHud(){
        var cam=Robolectric.buildActivity(Camera2ManualActivity.class).create();
        try {
            var root=cam.get().getWindow().getDecorView();
            assertNull(root.findViewWithTag("shared_vector_hud"));
            assertNull(root.findViewWithTag("engine_switcher"));
        }finally{cam.destroy();}
    }
}
