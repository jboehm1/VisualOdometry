package dev.jboehm.vectorvio;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class CustomVioHistoryUiTest {
    @Test public void previousRunIsReachableAfterActivityRecreationWithoutCameraOrNativeEngine()throws Exception {
        String name="1700000000000-12345678";
        File dir=new File(RuntimeEnvironment.getApplication().getFilesDir(),"custom-vio/"+name);assertTrue(dir.mkdirs()||dir.isDirectory());
        Files.write(new File(dir,"custom_metadata.json").toPath(),"{\"started_elapsed_ns\":100}".getBytes(StandardCharsets.UTF_8));
        Intent intent=new Intent(RuntimeEnvironment.getApplication(),Camera2ManualActivity.class).putExtra(Camera2ManualActivity.EXTRA_CUSTOM_VIO,true);
        var controller=Robolectric.buildActivity(Camera2ManualActivity.class,intent).create().start().resume();
        try{
            Camera2ManualActivity a=controller.get();
            a.getWindow().getDecorView().findViewWithTag("basalt_history").performClick();
            var dialog=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);
            dialog.getListView().performItemClick(dialog.getListView().getChildAt(0),0,0);
            var exported=Shadows.shadowOf(a).getNextStartedActivityForResult();assertNotNull(exported);
            assertEquals(Intent.ACTION_CREATE_DOCUMENT,exported.intent.getAction());assertEquals("application/zip",exported.intent.getType());
            Bundle state=new Bundle();controller.saveInstanceState(state);assertEquals(name,state.getString("basalt_export_directory"));
            assertTrue(state.getBoolean("basalt_export_recovery"));
        }finally{controller.pause().stop().destroy();}
    }
}
