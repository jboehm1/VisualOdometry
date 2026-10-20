package dev.jboehm.vectorvio.gpxar;
import dev.jboehm.vectorvio.gps.GpsRoute;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public final class RouteIndexNearPriorityTest {
    @Test public void busyOldLoopCannotHideClosestFutureSegmentsAtLimit(){
        ArrayList<GpsRoute.Point> path=new ArrayList<>();
        for(int i=0;i<200;i++)path.add(new GpsRoute.Point(0.0001*Math.sin(i*.16),0.0006,Double.NaN));
        path.add(new GpsRoute.Point(0,0.00001,Double.NaN));
        path.add(new GpsRoute.Point(0.00003,0.00001,Double.NaN));
        RouteIndex index=new RouteIndex(new GpsRoute("dense",path));
        List<double[]> near=index.near(0,0,90,2);
        assertEquals(2,near.size());
        double min=Double.POSITIVE_INFINITY;
        for(double[] e:near)for(int k=0;k<4;k+=2)
            min=Math.min(min,Math.hypot(e[k]*111200,e[k+1]*111200));
        assertTrue("a new near endpoint must beat 60m-old cached loop",min<10);
        assertTrue(index.near(0,0,90,0).isEmpty());
    }
}
