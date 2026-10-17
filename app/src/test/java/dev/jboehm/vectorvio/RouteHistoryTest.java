package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;
public class RouteHistoryTest {
    @Test public void fullRouteSurvivesTrackingBreakAndFitsItsWholeExtent(){
        Odometer o=new Odometer();o.start(0);o.onPose(10,0,20,1_000_000_000L);o.onPose(11,0,20,1_100_000_000L);o.lostTracking();o.onPose(30,0,40,2_000_000_000L);o.onPose(31,0,40,2_100_000_000L);
        Odometer.Snapshot s=o.snapshot(1000);assertEquals(4,s.worldTrail.size());assertEquals(10,s.worldTrail.get(0).x,0);assertEquals(31,s.worldTrail.get(3).x,0);assertNotEquals(s.worldTrail.get(1).segment,s.worldTrail.get(2).segment);assertEquals(2,s.totalMeters,1e-6);
        double[] box=RouteHistory.bounds(s.worldTrail);for(Odometer.Point p:s.worldTrail){assertTrue(Math.abs(p.x-box[0])<=box[2]/2);assertTrue(Math.abs(p.z-box[1])<=box[2]/2);}
    }
    @Test public void longRouteIsDecimatedWithoutDiscardingItsBeginning(){RouteHistory history=new RouteHistory();for(int i=0;i<20000;i++)history.add(new Odometer.Point(i,0,0,1));assertEquals(0,history.snapshot().get(0).x,0);assertEquals(19999,history.last().x,0);assertTrue(history.snapshot().size()<8192);}
    @Test public void cachedSnapshotRemainsImmutableAcrossUpdatesAndReset(){
        RouteHistory h=new RouteHistory();h.add(new Odometer.Point(1,2,3,1));
        java.util.List<Odometer.Point> first=h.snapshot();assertSame(first,h.snapshot());
        h.add(new Odometer.Point(4,5,6,1));assertEquals(1,first.size());assertEquals(2,h.snapshot().size());
        h.clear();assertEquals(0,h.snapshot().size());assertEquals(1,first.size());
    }
}
