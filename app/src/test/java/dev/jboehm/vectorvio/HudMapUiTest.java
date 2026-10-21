package dev.jboehm.vectorvio;
import android.view.MotionEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Detects map tap target regressions without pretending device localization accuracy. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public final class HudMapUiTest {
    @Test public void smallMapIsClickableAndBigMapHasCloseHitTarget(){
        HudView hud=new HudView(RuntimeEnvironment.getApplication());
        float dp=RuntimeEnvironment.getApplication().getResources().getDisplayMetrics().density;
        hud.layout(0,0,(int)(400*dp),(int)(850*dp));
        assertTrue(hud.canToggleMapAt(360*dp,610*dp));
        assertFalse(hud.canToggleMapAt(80*dp,610*dp));
        hud.toggleMap();
        assertTrue(hud.isMapExpanded());
        assertTrue(hud.canToggleMapAt(355*dp,180*dp));
        assertFalse(hud.canToggleMapAt(150*dp,180*dp));
    }
    @Test public void longitudeWrapStaysCloseAcrossAntimeridian(){
        assertEquals(0.002,HudView.wrapLongitude(-359.998),1e-4);
        assertEquals(-0.002,HudView.wrapLongitude(359.998),1e-4);
    }
}
