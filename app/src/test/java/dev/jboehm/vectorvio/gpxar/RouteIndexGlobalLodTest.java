package dev.jboehm.vectorvio.gpxar;
import dev.jboehm.vectorvio.gps.GpsRoute;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Global rendering selection tests. No ground truth or physical ARCore claim. */
public final class RouteIndexGlobalLodTest {
    @Test public void wholeFortyKilometreRouteRemainsRepresentedOnBothSides(){
        List<GpsRoute.Point> points=new ArrayList<>();
        for(int i=0;i<2001;i++)
            points.add(new GpsRoute.Point(48.0,7.0+i*.0003,Double.NaN));
        RouteIndex index=new RouteIndex(new GpsRoute("40 km",points));
        // Position at route midpoint; old 90 m mode must NOT define world coverage.
        List<double[]> sample=index.world(48.0,7.30,50000,1200);
        assertFalse(sample.isEmpty());
        assertTrue(sample.size()<=1200);
        double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
        for(double[] edge:sample){
            for(double v:edge)assertTrue(Double.isFinite(v));
            lo=Math.min(lo,Math.min(edge[1],edge[3]));
            hi=Math.max(hi,Math.max(edge[1],edge[3]));
        }
        assertTrue("past GPX must remain spatially represented",lo<7.05);
        assertTrue("future GPX must remain spatially represented",hi>7.55);
        assertTrue("local observed-floor query remains local",
            index.near(48,7.30,90,950).size()<100);
    }
    @Test public void globalSamplingSpreadsBudgetAcrossDenseLoopRatherThanOnlyBeginning(){
        List<GpsRoute.Point> points=new ArrayList<>();
        for(int i=0;i<25000;i++)
            points.add(new GpsRoute.Point(48+i*.00000001,7+i*.000001,Double.NaN));
        RouteIndex index=new RouteIndex(new GpsRoute("dense",points));
        List<double[]> world=index.world(48,7,50000,200);
        assertTrue(world.size()<=200);
        assertTrue(world.size()>20);
        assertTrue("last path region represented",world.get(world.size()-1)[3]>7.023);
    }
    @Test public void invalidQueryAndZeroBudgetYieldNoRenderingData(){
        RouteIndex index=new RouteIndex(new GpsRoute("simple",Arrays.asList(
            new GpsRoute.Point(48,7,Double.NaN),
            new GpsRoute.Point(48,7.02,Double.NaN))));
        assertTrue(index.world(Double.NaN,7,50000,50).isEmpty());
        assertTrue(index.world(48,7,50000,0).isEmpty());
        assertTrue(index.world(48,7,80,50).isEmpty());
    }
    @Test public void largerThanFiftyThousandImportedPointsAreLegal(){
        List<GpsRoute.Point> points=new ArrayList<>();
        for(int i=0;i<50010;i++)
            points.add(new GpsRoute.Point(48,7+i*.00000001,Double.NaN));
        GpsRoute route=new GpsRoute("50010",points);
        assertEquals(50010,route.points.size());
    }
}
