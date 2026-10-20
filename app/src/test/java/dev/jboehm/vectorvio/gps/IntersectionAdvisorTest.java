package dev.jboehm.vectorvio.gps;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class IntersectionAdvisorTest {
    @Test public void onlyClaimsGpxTurnNotPhysicalBranch(){
        GpsRoute route=new GpsRoute("bend",List.of(
            new GpsRoute.Point(48,7,Double.NaN),
            new GpsRoute.Point(48,7.0001,Double.NaN),
            new GpsRoute.Point(48.0001,7.0001,Double.NaN)));
        IntersectionAdvisor.Hint hint=IntersectionAdvisor.upcoming(route,2,3);
        assertNotNull(hint);assertTrue(Math.abs(hint.turnDegrees)>70);
        assertNull(IntersectionAdvisor.upcoming(route,2,50));
        assertNull(IntersectionAdvisor.upcoming(route,route.length-1,3));
    }
}
