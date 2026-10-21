package dev.jboehm.vectorvio;
import android.app.AlertDialog;
import android.view.*;
import android.os.SystemClock;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import java.lang.reflect.Field;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class MainPowerTest {
    private Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private void touch(View view,int action){long now=SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(now,now,action,100,200,0);try{assertTrue(view.dispatchTouchEvent(event));}finally{event.recycle();}}
    @Test public void chooserOffersFourModesAndHoldClearsOnCancelAndFocusLoss()throws Exception{
        ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).create();
        try{
            MainActivity a=c.get();View chip=(View)field(a,"powerButton");chip.performClick();
            AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);assertEquals(4,dialog.getListView().getCount());
            dialog.getListView().performItemClick(null,3,3);
            LiveFrameScheduler scheduler=(LiveFrameScheduler)field(a,"frameScheduler");assertEquals(LiveFrameScheduler.Mode.HOLD,scheduler.mode());
            View camera=(View)field(a,"surface");touch(camera,MotionEvent.ACTION_DOWN);assertEquals(30,scheduler.fps());
            touch(camera,MotionEvent.ACTION_CANCEL);assertEquals(5,scheduler.fps());
            touch(camera,MotionEvent.ACTION_DOWN);a.onWindowFocusChanged(false);assertEquals(5,scheduler.fps());
            touch(camera,MotionEvent.ACTION_UP);assertEquals(5,scheduler.fps());
            assertNull(Shadows.shadowOf(a).getNextStartedService());
        }finally{c.destroy();}
    }
}
