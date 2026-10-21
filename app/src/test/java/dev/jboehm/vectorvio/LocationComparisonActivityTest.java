package dev.jboehm.vectorvio;
import static org.junit.Assert.*;
import android.widget.TextView;
import android.view.View;
import android.view.ViewGroup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;

/** Screen construction never silently subscribes to either location provider. */
@RunWith(RobolectricTestRunner.class)
public class LocationComparisonActivityTest {
    @Test public void screenPresentsExplicitOptInAndExportControls(){
        ActivityController<LocationComparisonActivity> c=
            Robolectric.buildActivity(LocationComparisonActivity.class).setup();
        try {
            ViewGroup root=(ViewGroup)c.get().findViewById(android.R.id.content);
            assertTrue(contains(root,"GPS  ↔  ANDROID FUSED"));
            assertTrue(contains(root,"DÉMARRER LA COMPARAISON"));
            assertTrue(contains(root,"EXPORTER LE DERNIER TEST"));
        }finally{c.pause().stop().destroy();}
    }
    private static boolean contains(View v,String fragment){
        if(v instanceof TextView && String.valueOf(((TextView)v).getText()).contains(fragment))return true;
        if(v instanceof ViewGroup){
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++)if(contains(g.getChildAt(i),fragment))return true;
        }
        return false;
    }
}
