package dev.jboehm.vectorvio.gps;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** VIO displacement changes only a bounded transition metric; GPS route remains authoritative. */
public class GpsVioPreviewTest {
    private static GpsRoute route(){
        return new GpsRoute("simple",List.of(
            new GpsRoute.Point(48.0,7.0,Double.NaN),
            new GpsRoute.Point(48.0,7.002,Double.NaN)));
    }
    private static GpsNavigator.Fix fix(double lon,long ns){
        return new GpsNavigator.Fix(48,lon,3,Double.NaN,Double.NaN,Double.NaN,ns);
    }
    @Test public void followsWithCorrectlyPairedRelativeDistance(){
        GpsNavigator nav=new GpsNavigator(route());nav.joinNearest();
        long start=1_000_000_000L;
        assertTrue(nav.update(fix(7,start),start+10_000_000L));
        assertTrue(nav.updateWithVio(fix(7.00008,start+1_000_000_000L),
            start+1_050_000_000L,6,start));
        assertTrue(nav.progress>1);
        assertTrue(nav.status==GpsNavigator.Status.FOLLOWING ||
            nav.status==GpsNavigator.Status.AMBIGUOUS);
    }
    @Test public void unpairedVioCannotOverrideGpsOnlyFallback(){
        GpsNavigator a=new GpsNavigator(route()),b=new GpsNavigator(route());
        a.joinNearest();b.joinNearest();
        long t=1_000_000_000L;
        a.update(fix(7,t),t+10_000_000L);
        b.update(fix(7,t),t+10_000_000L);
        a.updateWithVio(fix(7.0001,t+1_000_000_000L),t+1_020_000_000L,80,123);
        b.update(fix(7.0001,t+1_000_000_000L),t+1_020_000_000L);
        assertEquals(b.status,a.status);
        assertEquals(b.progress,a.progress,1e-9);
    }
    @Test public void gpsAccuracyStillSuspendsNavigation(){
        GpsNavigator nav=new GpsNavigator(route());nav.joinNearest();
        long t=1_000_000_000L;
        nav.update(fix(7,t),t+10_000_000L);
        assertFalse(nav.updateWithVio(new GpsNavigator.Fix(48,7.0001,40,
           Double.NaN,Double.NaN,Double.NaN,t+1_000_000_000L),
           t+1_010_000_000L,10,t));
        assertEquals(GpsNavigator.Status.POOR_ACCURACY,nav.status);
    }
}
