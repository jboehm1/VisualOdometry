package dev.jboehm.vectorvio.gpxar;
import org.junit.Test;
import static org.junit.Assert.*;
public class RouteDisplayOptionsTest {
    @Test public void presentationFlagsAreIndependentAndUnknownBitsAreDiscarded(){
        RouteDisplayOptions o=new RouteDisplayOptions(RouteDisplayOptions.DIRECTION|RouteDisplayOptions.LOCAL_POIS|128);
        assertTrue(o.enabled(RouteDisplayOptions.DIRECTION));assertTrue(o.enabled(RouteDisplayOptions.LOCAL_POIS));assertFalse(o.enabled(RouteDisplayOptions.DISTANCES));assertEquals(9,o.flags);
    }
}
