package dev.jboehm.vectorvio;

import org.junit.Test;
import static org.junit.Assert.*;

public class GeoAlignmentTest {
    private static final double R=6371008.8;
    private static double lat(double north){return 48+Math.toDegrees(north/R);}
    private static double lon(double east){return 7+Math.toDegrees(east/(R*Math.cos(Math.toRadians(48))));}

    @Test public void needsMotionAndMultipleFixes(){
        GeoAlignment a=new GeoAlignment();
        assertEquals(GeoAlignment.State.NEED_SAMPLES,a.estimate().state);
        for(int i=0;i<5;i++)a.add(1,(i+1)*1_000_000_000L,i*.1,0,lat(i*.1),7,3);
        assertEquals(GeoAlignment.State.NEED_BASELINE,a.estimate().state);
        assertNull(a.estimate().project(2,3));
    }
    @Test public void alignsNoisyPathWithoutAssumingGpsIsGroundTruth(){
        GeoAlignment a=track(false);
        GeoAlignment.Result r=a.estimate();
        assertEquals(GeoAlignment.State.CANDIDATE,r.state);
        assertEquals(18,r.samples);
        assertEquals(18,r.inliers);
        assertEquals(31,Math.toDegrees(r.yawRadians),3);
        double[] p=r.project(12,2);
        double rad=Math.toRadians(31);
        assertEquals(lat(-12+Math.sin(rad)*12+Math.cos(rad)*2),p[0],0.00005);
        assertEquals(lon(33+Math.cos(rad)*12-Math.sin(rad)*2),p[1],0.00005);
    }
    @Test public void toleratesOneBadGpsFixWithoutInventingPerfectPrecision(){
        GeoAlignment.Result r=track(true).estimate();
        assertEquals(GeoAlignment.State.CANDIDATE,r.state);
        assertEquals(17,r.inliers);
        assertTrue(r.residualRmsMeters>10); // Still visible to UI/logs.
        assertEquals(31,Math.toDegrees(r.yawRadians),6);
    }
    @Test public void rejectsInvalidAndOutOfOrderAndKeepsSegmentsSeparate(){
        GeoAlignment a=new GeoAlignment();
        assertFalse(a.add(1,1,0,0,48,7,0));
        assertFalse(a.add(1,1,0,0,90,7,2));
        assertTrue(a.add(1,1,0,0,48,7,2));
        assertFalse(a.add(1,1,1,0,48,7,2));
        assertTrue(a.add(2,2,0,0,48,7,2));
        assertEquals(2,a.estimate().segment);
        assertEquals(1,a.estimate().samples);
    }
    @Test public void commonGpsBiasRemainsUnobservable(){
        // A perfectly smooth 40 m GPS offset produces an equally smooth fit:
        // deterministic tests cannot prove geographic absolute accuracy.
        GeoAlignment a=new GeoAlignment();
        for(int i=0;i<10;i++)assertTrue(a.add(1,(i+1)*1_000_000_000L,i*3,0,lat(40),lon(i*3+40),3));
        assertEquals(GeoAlignment.State.CANDIDATE,a.estimate().state);
        double[] projected=a.estimate().project(0,0);
        assertEquals(lat(40),projected[0],0.00001);
        assertEquals(lon(40),projected[1],0.00001);
    }
    @Test public void highReportedGpsNoiseNeedsMuchLongerTravel(){
        GeoAlignment a=new GeoAlignment();
        for(int i=0;i<8;i++)a.add(1,(i+1)*1_000_000_000L,i*3,0,lat(0),lon(i*3),12);
        assertEquals(GeoAlignment.State.NEED_BASELINE,a.estimate().state);
        assertNull(a.estimate().project(0,0));
    }
    private GeoAlignment track(boolean outlier){
        GeoAlignment a=new GeoAlignment();
        double angle=Math.toRadians(31);
        for(int i=0;i<18;i++){
            double x=i*2,z=Math.sin(i*.5)*4;
            double east=33+Math.cos(angle)*x-Math.sin(angle)*z;
            double north=-12+Math.sin(angle)*x+Math.cos(angle)*z;
            if(outlier&&i==8){east+=50;north+=60;}
            else {east+=Math.sin(i*3)*.45;north+=Math.cos(i*4)*.45;}
            assertTrue(a.add(1,(i+1)*1_000_000_000L,x,z,lat(north),lon(east),3));
        }
        return a;
    }

    @Test public void frozenGpsCannotObserveYawDespiteMovingVioAndSmallResidual(){
        GeoAlignment a=new GeoAlignment();
        for(int i=0;i<10;i++)a.add(1,(i+1)*1_000_000_000L,i*2,0,lat(0),lon(0),3);
        GeoAlignment.Result r=a.estimate();
        assertEquals(GeoAlignment.State.NEED_BASELINE,r.state);
        assertEquals(0,r.baselineMeters,1e-6);
        assertNull(r.project(0,0));
        assertNull(r.unproject(48,7));
    }

    @Test public void rejectedHighLeveragePairCannotSupplyBaseline(){
        GeoAlignment a=new GeoAlignment();
        for(int i=0;i<10;i++){
            double x=i==9?100:i*.1;
            a.add(1,(i+1)*1_000_000_000L,x,0,lat(0),lon(i*.1),3);
        }
        GeoAlignment.Result r=a.estimate();
        assertEquals(9,r.inliers);
        assertEquals(GeoAlignment.State.NEED_BASELINE,r.state);
        assertTrue(r.baselineMeters<1);
        assertTrue(r.residualRmsMeters>20); // Keep the rejected residual visible.
    }

    @Test public void tinyGpsJitterCannotSupplyBaseline(){
        GeoAlignment a=new GeoAlignment();
        for(int i=0;i<10;i++)a.add(1,(i+1)*1_000_000_000L,i*2,0,
            lat(Math.sin(i)*.2),lon(Math.cos(i)*.2),3);
        assertEquals(GeoAlignment.State.NEED_BASELINE,a.estimate().state);
    }

    @Test public void uncertainGpsInlierCannotBorrowAccuracyFromStationaryFixes(){
        GeoAlignment a=new GeoAlignment();
        for(int i=0;i<64;i++)a.add(1,(i+1)*1_000_000_000L,i*18.0/63,0,
            lat(0),lon(i==63?20:0),i==63?50:3);
        GeoAlignment.Result r=a.estimate();
        assertEquals(64,r.inliers); // Residual compatibility alone is insufficient.
        assertEquals(GeoAlignment.State.NEED_BASELINE,r.state);
        assertNull(r.project(0,0));
    }
}
