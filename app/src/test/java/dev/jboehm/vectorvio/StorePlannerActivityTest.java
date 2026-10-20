package dev.jboehm.vectorvio;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Actual Activity wiring smoke: no camera/location permission or ARCore needed. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class StorePlannerActivityTest {
    private static boolean textContains(View v,String query) {
        if(v instanceof TextView && ((TextView)v).getText().toString().contains(query))return true;
        if(v instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)v;
            for(int i=0;i<group.getChildCount();i++)if(textContains(group.getChildAt(i),query))return true;
        }
        return false;
    }
    @Test public void launchesOfflineAndRendersPlanFromExplicitManualEdges(){
        StorePlannerActivity a=Robolectric.buildActivity(StorePlannerActivity.class).setup().get();
        View root=a.findViewById(android.R.id.content);
        assertNotNull(root);
        assertTrue(textContains(root,"COURSES"));
        assertTrue(textContains(root,"TRACÉ ESTIMÉ"));
        assertTrue(textContains(root,"17,0 m")); // 3 + 4 + 5 + 5
        a.finish();
    }
}
