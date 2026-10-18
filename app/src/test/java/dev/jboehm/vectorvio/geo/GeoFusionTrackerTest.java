package dev.jboehm.vectorvio.geo;
import org.junit.Test;
import static org.junit.Assert.*;
public class GeoFusionTrackerTest {
    @Test public void refusesUnknownTimebaseAndStaleFix(){
        GeoFusionTracker g=new GeoFusionTracker();
        g.onPose(1,1000,50_000_000_000L,0,0);
        assertEquals(GeoFusionTracker.Status.CLOCK_MISMATCH,g.onFix(900,48,7,4).status);
        g.reset();
        g.onPose(1,1_000_000_000L,1_005_000_000L,0,0);
        g.onPose(1,1_100_000_000L,1_105_000_000L,1,0);
        assertEquals(GeoFusionTracker.Status.WAITING_FOR_POSES,g.onFix(700_000_000L,48,7,4).status);
    }
    @Test public void alignmentUsesMatchedTimesAndNeverClaimsAbsoluteAccuracy(){
        GeoFusionTracker g=new GeoFusionTracker();
        double rad=Math.PI/180;
        for(int i=0;i<22;i++){
            long t=2_000_000_000L+i*200_000_000L;
            g.onPose(1,t,t+5_000_000L,i*2,0);
            g.onPose(1,t+100_000_000L,t+105_000_000L,i*2+1,0);
            GeoFusionTracker.Report report=g.onFix(t+50_000_000L,48,7+Math.toDegrees((i*2+.5)/(6371008.8*Math.cos(48*rad))),3);
            if(i>7){assertEquals(GeoFusionTracker.Status.CANDIDATE,report.status);assertTrue(report.hasCoordinate());}
        }
    }
}
