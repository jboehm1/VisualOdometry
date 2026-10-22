package dev.jboehm.vectorvio;

import android.app.Activity;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Reproduces the failure route: Basalt START must not finish the screen or
 * return to ARCore when the camera/IMU bootstrap is not ready. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public final class BasaltNoSilentFallbackTest {
    @Test public void basaltNativeJniHasSeparateProcessFromPrimaryArcore()throws Exception{
        var app=RuntimeEnvironment.getApplication();
        ActivityInfo child=app.getPackageManager().getActivityInfo(
            new ComponentName(app,Camera2ManualActivity.class),0);
        ActivityInfo primary=app.getPackageManager().getActivityInfo(
            new ComponentName(app,MainActivity.class),0);
        assertTrue("Native process must be isolated",child.processName.endsWith(":basalt"));
        assertFalse(primary.processName.endsWith(":basalt"));
    }
    @Test public void startWithNoActiveCameraStaysInBasaltInsteadOfReturningToArcore(){
        Intent intent=new Intent(RuntimeEnvironment.getApplication(),Camera2ManualActivity.class)
            .putExtra(Camera2ManualActivity.EXTRA_CUSTOM_VIO,true);
        var controller=Robolectric.buildActivity(Camera2ManualActivity.class,intent).create();
        try{
            Camera2ManualActivity a=controller.get();
            View start=a.getWindow().getDecorView().findViewWithTag("custom_start");
            assertNotNull(start);
            start.performClick();
            assertFalse("START cannot implicitly close custom activity",a.isFinishing());
            assertEquals("No implicit engine-switch result",Activity.RESULT_CANCELED,
                Shadows.shadowOf(a).getResultCode());
            assertNotNull(a.getWindow().getDecorView().findViewWithTag("shared_vector_hud"));
            // Explicit BACK is the only way to return with RESULT_OK.
            a.onBackPressed();
            assertTrue(a.isFinishing());
            assertEquals(Activity.RESULT_OK,Shadows.shadowOf(a).getResultCode());
        }finally{controller.destroy();}
    }
    @Test public void abnormalChildExitDisplaysExplicitDiagnosticInArcore(){
        var c=Robolectric.buildActivity(MainActivity.class).create();
        try{
            MainActivity a=c.get();
            BasaltIncidentJournal.mark(a,"BEFORE_NATIVE_SESSION_INIT");
            a.onActivityResult(148,Activity.RESULT_CANCELED,null);
            var dialog=ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(dialog);
            assertTrue(dialog.isShowing());
            android.widget.TextView message=dialog.findViewById(android.R.id.message);
            assertNotNull(message);
            assertTrue(message.getText().toString().contains("BEFORE_NATIVE_SESSION_INIT"));
            assertFalse(a.isFinishing());
        }finally{c.destroy();}
    }
}
