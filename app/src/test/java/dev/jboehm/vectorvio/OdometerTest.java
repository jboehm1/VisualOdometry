package dev.jboehm.vectorvio;

import org.junit.Test;
import static org.junit.Assert.*;

public class OdometerTest {
    @Test public void integratesMetricPathButNotZeroMotion() {
        Odometer o = new Odometer();
        o.start(1000);
        assertEquals(Odometer.Result.REFERENCE, o.onPose(0,0,0,1_000_000_000L));
        assertEquals(Odometer.Result.NO_CHANGE, o.onPose(0.001,0,0,1_100_000_000L));
        assertEquals(Odometer.Result.ACCEPTED, o.onPose(0.3,0.4,0,1_200_000_000L));
        Odometer.Snapshot s = o.snapshot(2000);
        assertEquals(0.5,s.totalMeters,1e-8);
        assertEquals(0.3,s.horizontalMeters,1e-8);
        assertEquals(0.4,s.ascentMeters,1e-8);
        assertEquals(0.5,s.displacementMeters,1e-8);
        assertEquals(1,s.segments);
    }

    @Test public void trackingLossNeverTurnsIntoTravel() {
        Odometer o = new Odometer();
        o.start(0);
        o.onPose(0,0,0,1_000_000_000L);
        o.onPose(1,0,0,2_000_000_000L); // gap 1s deliberately rejected
        o.lostTracking();
        o.onPose(200,0,0,3_000_000_000L);
        o.onPose(200.2,0,0,3_100_000_000L);
        assertEquals(.2,o.snapshot(1000).totalMeters,1e-8);
        assertTrue(o.snapshot(1000).gaps >= 1);
    }

    @Test public void pauseAndResumeDoNotConnectPositions() {
        Odometer o = new Odometer();
        o.start(100);
        o.onPose(0,0,0,1_000_000_000L);
        o.onPose(1,0,0,1_100_000_000L);
        o.pause(200);
        o.start(500);
        o.onPose(100,0,0,2_000_000_000L);
        o.onPose(100.5,0,0,2_100_000_000L);
        assertEquals(1.5,o.snapshot(600).totalMeters,1e-8);
        assertEquals(200,o.snapshot(600).elapsedMilliseconds);
    }

    @Test public void rejectsImplausibleTrackingJump() {
        Odometer o = new Odometer();
        o.start(0);
        o.onPose(0,0,0,1_000_000_000L);
        assertEquals(Odometer.Result.REJECTED,o.onPose(100,0,0,1_100_000_000L));
        assertEquals(0,o.snapshot(1).totalMeters,0);
        assertEquals(1,o.snapshot(1).outliers);
    }

    @Test public void resetClearsState() {
        Odometer o = new Odometer();
        o.start(0);
        o.onPose(0,0,0,1_000_000_000L);
        o.onPose(1,0,0,1_100_000_000L);
        o.reset();
        assertEquals(0,o.snapshot(100).totalMeters,0);
        assertFalse(o.snapshot(100).recording);
        assertEquals(0,o.snapshot(100).segments);
    }

    @Test public void slowlyAccumulatingMotionNotForgotten() {
        Odometer o = new Odometer();
        o.start(0);
        o.onPose(0,0,0,1_000_000_000L);
        for(int i=1;i<=4;i++) o.onPose(i*.01,0,0,1_000_000_000L+i*100_000_000L);
        assertEquals(.04,o.snapshot(100).totalMeters,1e-8);
    }
}
