package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ShortHorizonGyroTest {
    @Test public void integratesHalfTurnZInDeviceCoordinatesWithoutPosition(){
        ShortHorizonGyro g=new ShortHorizonGyro();
        g.anchor(1_000_000_000L);
        for(int i=1;i<=100;i++){
            long t=1_000_000_000L+i*10_000_000L;
            g.gyro(t,0,0,Math.PI/2);
        }
        ShortHorizonGyro.Estimate r=g.snapshot(2_000_000_000L);
        assertTrue(r.reason,r.usable);
        assertEquals(100,r.samples);
        assertEquals(0.,r.x,1e-8);
        assertEquals(0.,r.y,1e-8);
        assertEquals(Math.sqrt(.5),r.z,1e-6);
        assertEquals(Math.sqrt(.5),r.w,1e-6);
        assertEquals(90.,r.angleDegrees,1e-4);
    }
    @Test public void cannotIntegrateWithoutVisualAnchorAndNeedsTwoSamples(){
        ShortHorizonGyro g=new ShortHorizonGyro();
        g.gyro(1_000_000_000L,0,0,1);
        assertEquals("NO_VISUAL_ANCHOR",g.snapshot(1_000_000_000L).reason);
        g.anchor(1_000_000_000L);
        g.gyro(1_020_000_000L,0,0,1);
        assertFalse(g.snapshot(1_020_000_000L).usable);
    }
    @Test public void expiresAfterOneAndHalfSecondsAndStaleSample(){
        ShortHorizonGyro g=new ShortHorizonGyro();
        g.anchor(1_000_000_000L);
        g.gyro(1_010_000_000L,0,0,1);
        g.gyro(1_020_000_000L,0,0,1);
        assertEquals("GYRO_STALE",g.snapshot(1_280_000_001L).reason);
        assertEquals("ANCHOR_EXPIRED",g.snapshot(2_500_000_001L).reason);
        assertFalse(g.snapshot(2_500_000_001L).usable);
    }
    @Test public void reAnchoringResetsDeviceRotationRatherThanJoiningSessions(){
        ShortHorizonGyro g=new ShortHorizonGyro();
        g.anchor(1_000_000_000L);
        g.gyro(1_010_000_000L,0,0,2);
        g.gyro(1_020_000_000L,0,0,2);
        assertTrue(g.snapshot(1_020_000_000L).angleDegrees>0);
        g.anchor(1_030_000_000L);
        assertFalse(g.snapshot(1_030_000_000L).usable);
        g.gyro(1_040_000_000L,0,0,0);
        g.gyro(1_050_000_000L,0,0,0);
        assertEquals(0,g.snapshot(1_050_000_000L).angleDegrees,1e-8);
    }
    @Test public void rejectsLargeGapNonfiniteAndDuplicate(){
        ShortHorizonGyro g=new ShortHorizonGyro();
        g.anchor(100);
        g.gyro(101,0,0,1);
        g.gyro(101,0,0,1);
        assertEquals("GYRO_OUT_OF_ORDER_OR_GAP",g.snapshot(102).reason);
        g.anchor(1000);
        g.gyro(1100,0,0,1);
        g.gyro(200_000_000L,0,0,1);
        assertEquals("GYRO_OUT_OF_ORDER_OR_GAP",g.snapshot(200_000_000L).reason);
        g.anchor(300_000_000L);
        g.gyro(300_000_001L,Double.NaN,0,0);
        assertFalse(g.snapshot(300_000_002L).usable);
    }
    @Test public void gyroDataFromBeforeAnchorIsNeverApplied(){
        ShortHorizonGyro g=new ShortHorizonGyro();
        g.anchor(500_000_000L);
        g.gyro(480_000_000L,0,0,2);
        g.gyro(510_000_000L,0,0,2);
        g.gyro(520_000_000L,0,0,2);
        assertEquals(2,g.snapshot(520_000_000L).samples);
    }
}
