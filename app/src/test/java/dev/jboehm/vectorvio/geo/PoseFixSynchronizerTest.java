package dev.jboehm.vectorvio.geo;
import org.junit.Test;
import static org.junit.Assert.*;

public class PoseFixSynchronizerTest {
    @Test public void interpolatesOnlyWithinKnownContinuousSegment(){
        PoseFixSynchronizer s=new PoseFixSynchronizer();
        assertTrue(s.push(1,1_000_000_000L,0,0));
        assertTrue(s.push(1,1_100_000_000L,10,4));
        PoseFixSynchronizer.Pose m=s.interpolate(1_050_000_000L);
        assertNotNull(m);assertEquals(5,m.x,1e-10);assertEquals(2,m.z,1e-10);
        assertNull(s.interpolate(1_250_000_000L)); // no extrapolation
        assertFalse(s.push(1,1_050_000_000L,9,9));
        assertTrue(s.push(2,1_200_000_000L,20,20));
        assertNull(s.interpolate(1_100_000_000L));
    }
    @Test public void refusesLostTrackingGap(){
        PoseFixSynchronizer s=new PoseFixSynchronizer();
        s.push(1,1_000_000_000L,0,0);
        s.push(1,2_000_000_000L,8,0);
        assertNull(s.interpolate(1_500_000_000L));
        assertNull(s.interpolate(2_000_000_000L));
        s.push(1,2_100_000_000L,10,0);
        assertNotNull(s.interpolate(2_050_000_000L));
    }
}
