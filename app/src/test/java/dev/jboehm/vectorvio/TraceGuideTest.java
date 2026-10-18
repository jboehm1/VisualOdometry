package dev.jboehm.vectorvio;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class TraceGuideTest {
    private Odometer.Point p(double x,double z){return new Odometer.Point(x,0,z,1);}
    @Test public void reverseGuidanceFreezesRouteAndKeepsTravelAroundCorner(){
        TraceGuide g=new TraceGuide();List<Odometer.Point> path=new ArrayList<>(Arrays.asList(p(0,0),p(5,0),p(5,5)));
        assertTrue(g.start(path,p(5,5)));path.clear();
        TraceGuide.Guidance s=g.update(p(5,3));assertEquals(8,s.remaining,1e-8);assertEquals(0,s.offRoute,0);assertEquals(1.5,s.z,1e-8);
        s=g.update(p(4,0));assertEquals(4,s.remaining,1e-8);assertEquals(2.5,s.x,1e-8);
        assertTrue(g.update(p(.1,0)).arrived);
    }
    @Test public void offRoutePointsBackToTraceAndNeverConnectsLostFrame(){
        TraceGuide g=new TraceGuide();g.start(Arrays.asList(p(0,0),p(10,0)),p(10,0));
        TraceGuide.Guidance s=g.update(p(7,3));assertEquals(3,s.offRoute,0);assertEquals(7,s.x,0);assertEquals(0,s.z,0);
        assertNull(g.update(new Odometer.Point(7,0,0,2)));assertFalse(g.active());
    }
    @Test public void crossingDoesNotJumpToStartOfEarlierLoop(){
        TraceGuide g=new TraceGuide();g.start(Arrays.asList(p(0,0),p(5,0),p(5,5),p(0,5),p(0,0)),p(0,0));
        TraceGuide.Guidance s=g.update(p(0,0));assertEquals(20,s.remaining,0);assertFalse(s.arrived);
    }
}
