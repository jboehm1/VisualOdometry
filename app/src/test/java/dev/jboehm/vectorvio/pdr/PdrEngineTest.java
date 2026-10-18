package dev.jboehm.vectorvio.pdr;

import org.junit.Test;
import static org.junit.Assert.*;

public final class PdrEngineTest {
    @Test public void distanceAccumulatesWithoutCameraOrHeading() {
        PdrEngine p=new PdrEngine();p.start(1);
        for(int i=1;i<=10;i++)assertTrue(p.step(i*500_000_000L));
        PdrEngine.Snapshot s=p.snapshot(5_000_000_000L);
        assertEquals(10,s.steps);assertEquals(7.4,s.distanceM,1e-9);
        assertEquals(10,s.stepsWithoutHeading);assertFalse(s.hasTrajectory);
    }
    @Test public void pathUsesNorthClockwiseAndDoesNotInventMissingSegments(){
        PdrEngine p=new PdrEngine();p.start(1);
        p.deviceHeading(1_000_000_000L,Math.PI/2);
        assertTrue(p.step(1_500_000_000L));
        assertEquals(.74,p.snapshot(1_500_000_000L).eastM,1e-8);
        assertEquals(0,p.snapshot(1_500_000_000L).northM,1e-8);
        p.step(5_000_000_000L);
        assertEquals(2,p.snapshot(5_000_000_000L).steps);
        assertEquals(1,p.snapshot(5_000_000_000L).stepsWithoutHeading);
        assertEquals(2,p.path().size());
        assertFalse(p.path().get(1).gapBefore);
    }
    @Test public void pathHasGapAcrossUnknownHeading(){
        PdrEngine p=new PdrEngine();p.start(1);
        p.deviceHeading(1_000_000_000L,0);p.step(1_500_000_000L);
        p.step(6_000_000_000L);
        p.deviceHeading(6_500_000_000L,0);p.step(7_000_000_000L);
        assertEquals(3,p.path().size());
        assertTrue(p.path().get(2).gapBefore);
    }
    @Test public void gnssCourseQualityGateAndExpiry() {
        PdrEngine p=new PdrEngine();p.start(1);
        p.deviceHeading(1_000_000_000L,0);
        p.gpsCourse(1_000_000_001L,Math.PI/2,2f,10f,10f);
        p.step(2_000_000_000L);
        assertEquals(PdrEngine.HeadingSource.GNSS_COURSE,p.snapshot(2_000_000_000L).headingSource);
        p.step(6_500_000_000L);
        assertEquals(1,p.snapshot(6_500_000_000L).stepsWithoutHeading);
        p.deviceHeading(7_000_000_000L,0);
        p.gpsCourse(7_000_000_001L,Math.PI/2,.5f,1f,5f);
        p.step(7_500_000_000L);
        assertEquals(PdrEngine.HeadingSource.DEVICE_ASSUMED,p.snapshot(7_500_000_000L).headingSource);
    }
    @Test public void filtersDuplicatesAndPause(){
        PdrEngine p=new PdrEngine();p.start(1);
        assertTrue(p.step(1_000_000_000L));
        assertFalse(p.step(1_000_000_000L));
        assertFalse(p.step(1_100_000_000L));
        p.pause();assertFalse(p.step(2_000_000_000L));
        p.resume();assertTrue(p.step(2_500_000_000L));
        assertEquals(2,p.snapshot(2_500_000_000L).steps);
    }
    @Test public void pressureIsRelativeAndCalibrationBounded() {
        PdrEngine p=new PdrEngine();
        try{p.setStepLengthMeters(Double.NaN);fail();}catch(IllegalArgumentException expected){}
        p.setStepLengthMeters(.85);p.start(1);
        p.pressure(2,1013.25);p.pressure(3,1012);
        assertTrue(p.snapshot(3).elevationM>9);
        assertEquals(.85,p.snapshot(3).stepLengthM,1e-10);
    }
    @Test public void rejectsBufferedPreSessionEvents() {
        PdrEngine p=new PdrEngine();
        p.start(10_000_000_000L);
        p.deviceHeading(9_000_000_000L,Math.PI/2);
        p.gpsCourse(9_000_000_000L,Math.PI/2,2f,3f,2f);
        p.pressure(9_000_000_000L,1000);
        assertFalse(p.step(9_000_000_000L));
        assertTrue(p.step(10_500_000_000L));
        assertEquals(1,p.snapshot(10_500_000_000L).stepsWithoutHeading);
        assertTrue(Double.isNaN(p.snapshot(10_500_000_000L).elevationM));
    }
    @Test public void resumeNeverReusesOldHeadingOrBridgesPausedMovement() {
        PdrEngine p=new PdrEngine();
        p.start(1);
        p.deviceHeading(1_000_000_000L,0);
        p.step(1_500_000_000L);
        p.pause();p.resume();
        p.step(1_800_000_000L);
        assertEquals(1,p.snapshot(1_800_000_000L).stepsWithoutHeading);
        p.deviceHeading(2_000_000_000L,0);
        p.step(2_500_000_000L);
        assertEquals(3,p.path().size());
        assertTrue(p.path().get(2).gapBefore);
    }
    @Test public void accelerationFallbackDetectsDistinctSyntheticPeaks() {
        PdrEngine p=new PdrEngine();p.start(1);
        long t=1_000_000_000L;
        for(int k=0;k<7;k++){
            for(int j=0;j<30;j++){
                double z=j==10?14.5:(j>=11&&j<20?9.5:9.81);
                p.acceleration(t,0,0,z);t+=20_000_000L;
            }
        }
        assertTrue(p.snapshot(t).steps>=4);
        assertTrue(p.snapshot(t).steps<=7);
    }
}
