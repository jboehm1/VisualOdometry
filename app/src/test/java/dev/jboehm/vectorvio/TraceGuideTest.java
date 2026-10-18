package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class TraceGuideTest {
    @Test public void backtracksKnownSegmentOnly(){
        TraceGuide guide=new TraceGuide();List<Odometer.Point> points=new ArrayList<>();
        for(int i=0;i<=25;i++)points.add(new Odometer.Point(i,0,0,4));
        assertTrue(guide.start(points,new Odometer.Point(25,0,0,4)));
        TraceGuide.Guidance g=guide.update(new Odometer.Point(18,0,0,4));
        assertNotNull(g);assertEquals(18,g.remaining,.1);assertEquals(0,g.offRoute,.01);
        assertTrue(g.x<18);
        assertNull(guide.update(new Odometer.Point(12,0,0,5)));
        assertFalse(guide.active());
    }
    @Test public void neverReturnsDirectionForAbsentOrZeroLengthTrace(){
        TraceGuide guide=new TraceGuide();
        assertFalse(guide.start(List.of(new Odometer.Point(0,0,0,1)),new Odometer.Point(0,0,0,1)));
        assertNull(guide.update(new Odometer.Point(1,0,0,1)));
    }
    @Test public void doesNotSnapBackwardsTwentyMetersAtCrossing(){
        TraceGuide guide=new TraceGuide();ArrayList<Odometer.Point> points=new ArrayList<>();
        for(int i=0;i<100;i++)points.add(new Odometer.Point(i,0,0,1));
        assertTrue(guide.start(points,new Odometer.Point(99,0,0,1)));
        TraceGuide.Guidance g=guide.update(new Odometer.Point(10,0,0,1));
        assertNotNull(g);assertTrue(g.remaining>=78);
    }
}
