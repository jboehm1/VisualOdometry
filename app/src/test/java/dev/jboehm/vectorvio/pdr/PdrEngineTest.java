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
    @Test public void cadenceAndSpeedAreBoundedToActualRecentSteps(){
        PdrEngine p=new PdrEngine();p.start(1);
        for(int i=0;i<8;i++)p.step(1_000_000_000L+i*500_000_000L);
        PdrEngine.Snapshot s=p.snapshot(4_500_000_000L);
        assertEquals(120,s.cadenceStepsPerMin,1e-7);
        assertEquals(1.48,s.estimatedSpeedMps,1e-7);
        assertEquals(0,p.snapshot(9_000_000_000L).cadenceStepsPerMin,0);
        p.pause();
        assertEquals(0,p.snapshot(5_000_000_000L).estimatedSpeedMps,0);
        p.resume();p.step(10_000_000_000L);
        assertEquals(0,p.snapshot(10_000_000_000L).cadenceStepsPerMin,0);
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
    @Test public void invalidPhoneHeadingStopsTrajectoryImmediatelyAndRejectsOlderSamples(){
        PdrEngine p=new PdrEngine();p.start(1);
        p.deviceHeading(1_000_000_000L,Math.PI/2);
        assertTrue(p.step(1_200_000_000L));
        assertTrue(p.invalidateDeviceHeading(1_400_000_000L));
        assertFalse(p.invalidateDeviceHeading(1_500_000_000L));
        p.deviceHeading(1_100_000_000L,Math.PI/2); // buffered stale sample
        assertTrue(p.step(1_700_000_000L));
        assertEquals(1,p.snapshot(1_700_000_000L).stepsWithoutHeading);
        assertEquals(PdrEngine.HeadingSource.UNAVAILABLE,p.snapshot(1_700_000_000L).headingSource);
        p.deviceHeading(1_800_000_000L,0);
        assertTrue(p.step(2_100_000_000L));
        assertTrue(p.path().get(2).gapBefore);
    }
    @Test public void lowQualityGnssFixImmediatelyRevokesOldCourse(){
        PdrEngine p=new PdrEngine();p.start(1);
        p.gpsCourse(1_000_000_000L,Math.PI/2,2f,5f,5f);
        assertEquals(PdrEngine.HeadingSource.GNSS_COURSE,p.snapshot(1_500_000_000L).headingSource);
        p.gpsCourse(2_000_000_000L,Math.PI/2,.2f,5f,5f);
        assertEquals(PdrEngine.HeadingSource.UNAVAILABLE,p.snapshot(2_100_000_000L).headingSource);
        assertTrue(p.step(2_200_000_000L));
        assertEquals(1,p.snapshot(2_200_000_000L).stepsWithoutHeading);
        // Late buffered strong fix must not restore the invalidated course.
        p.gpsCourse(1_800_000_000L,0,4f,2f,2f);
        assertEquals(PdrEngine.HeadingSource.UNAVAILABLE,p.snapshot(2_250_000_000L).headingSource);
        p.gpsCourse(2_400_000_000L,0,3f,3f,5f);
        assertEquals(PdrEngine.HeadingSource.GNSS_COURSE,p.snapshot(2_450_000_000L).headingSource);
    }
    @Test public void badGnssAccuracyAndOutOfOrderPressureAreRejected(){
        PdrEngine p=new PdrEngine();p.start(1);
        p.gpsCourse(1_000_000_000L,0,2f,-1f,5f);
        p.gpsCourse(1_000_000_001L,0,2f,5f,-1f);
        assertEquals(PdrEngine.HeadingSource.UNAVAILABLE,p.snapshot(1_500_000_000L).headingSource);
        p.pressure(3_000_000_000L,1013.25);
        p.pressure(4_000_000_000L,1012.0);
        double elevation=p.snapshot(4_000_000_000L).elevationM;
        p.pressure(3_500_000_000L,970.0);
        p.pressure(4_000_000_000L,970.0);
        assertEquals(elevation,p.snapshot(5_000_000_000L).elevationM,1e-9);
        p.start(6_000_000_000L);
        p.pressure(6_000_000_000L,1000);
        assertEquals(0,p.snapshot(6_000_000_000L).elevationM,1e-9);
    }
    @Test public void preStartAccelerationCannotPrimeFallbackState(){
        PdrEngine p=new PdrEngine();
        long startNs=5_000_000_000L;
        p.start(startNs);
        long t=1_000_000_000L;
        for(int k=0;k<6;k++)for(int j=0;j<30;j++){
            assertFalse(p.acceleration(t,0,0,j==10?15.0:9.81));
            t+=20_000_000L;
        }
        assertEquals(0,p.snapshot(startNs).steps);
        assertFalse(p.isInCurrentSession(startNs-1));
        assertTrue(p.isInCurrentSession(startNs));
        // A new single shock must not count as a valid walking bout.
        for(int j=0;j<30;j++)
            assertFalse(p.acceleration(startNs+j*20_000_000L,0,0,j==10?15.0:9.81));
        assertEquals(0,p.snapshot(startNs+600_000_000L).steps);
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
