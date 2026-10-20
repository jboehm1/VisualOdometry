package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;
public final class CameraExposureOptimizerTest {
    private CameraExposureOptimizer.Limits limits(){
        return new CameraExposureOptimizer.Limits(100_000L,33_000_000L,100,6400);
    }
    private CameraImageQuality.Metrics m(int p10,int mid,double clips){
        return new CameraImageQuality.Metrics(p10,mid,mid+20,.15,clips,.12,1000);
    }
    @Test public void isoNeverExceedsUserNoiseBudget(){
        CameraExposureOptimizer c=new CameraExposureOptimizer();
        CameraExposureOptimizer.Decision d=c.update(1_000_000_000L,
            m(6,15,0),1_000_000L,100,limits(),800,90);
        assertTrue(d.iso<=800);assertTrue(d.exposureNs>=100_000L);
        assertTrue(d.exposureNs<=33_000_000L);
    }
    @Test public void darkSceneRequiresMorePhotonBudgetThanBright(){
        CameraExposureOptimizer c=new CameraExposureOptimizer();
        CameraExposureOptimizer.Decision a=c.update(1_000_000_000L,
            m(5,20,0),4_000_000L,800,limits(),1600,90);
        CameraExposureOptimizer.Decision b=c.update(2_000_000_000L,
            m(10,30,0),a.exposureNs,a.iso,limits(),1600,90);
        assertTrue((double)b.exposureNs*b.iso >=(double)a.exposureNs*a.iso);
    }
    @Test public void brightSceneKeepsShutterShortAndDropsIsoAtMinimumShutter(){
        CameraExposureOptimizer c=new CameraExposureOptimizer();
        CameraExposureOptimizer.Decision d=c.update(1_000_000_000L,
            m(170,230,.15),100_000L,300,limits(),1600,90);
        assertEquals(100_000L,d.exposureNs);
        assertTrue(d.iso<=300);
    }
    @Test public void rateLimiterAvoidsThrashing(){
        CameraExposureOptimizer c=new CameraExposureOptimizer();
        c.update(1_000_000_000L,m(0,15,0),1_000_000,100,limits(),800,90);
        assertFalse(c.update(1_100_000_000L,m(0,15,0),1_000_000,100,limits(),800,90).changed);
    }
    @Test public void bicycleGyroReducesAllowedShutterEvenIfDark(){
        CameraExposureOptimizer.Limits hw=limits();
        long slow=CameraExposureOptimizer.motionShutterCapNs(
            0,CameraExposureOptimizer.MotionMode.WALK,hw);
        long fast=CameraExposureOptimizer.motionShutterCapNs(
            2.0,CameraExposureOptimizer.MotionMode.BIKE,hw);
        assertEquals(25_000_000L,slow);
        assertTrue(fast<2_000_000L);
        assertTrue(fast<hw.maxExposureNs);
        CameraExposureOptimizer c=new CameraExposureOptimizer();
        CameraExposureOptimizer.Decision d=c.updateMotionAware(
            1_000_000_000L,m(0,4,0),20_000_000L,500,hw,3200,90,
            2.0,CameraExposureOptimizer.MotionMode.BIKE);
        assertTrue(d.exposureNs<=fast);
        assertTrue(d.iso<=3200);
        assertTrue(d.motionConstrained);
        assertTrue(d.insufficientLight);
        assertTrue(d.predictedAngularBlurDeg<=.181);
    }
    @Test public void noGyroscopeHasConservativeModeLimit(){
        long cap=CameraExposureOptimizer.motionShutterCapNs(
            Double.NaN,CameraExposureOptimizer.MotionMode.BIKE,limits());
        assertEquals(10_000_000L,cap);
        assertEquals(100_000L,CameraExposureOptimizer.motionShutterCapNs(
            1000,CameraExposureOptimizer.MotionMode.BIKE,limits()));
    }
    @Test public void goodLightAvoidsISOOverflowAndKeepsExposureTiny(){
        CameraExposureOptimizer c=new CameraExposureOptimizer();
        CameraExposureOptimizer.Decision d=c.updateMotionAware(
            1_000_000_000L,m(75,93,0),200_000,250,limits(),1000,90,
            4,CameraExposureOptimizer.MotionMode.BIKE);
        assertTrue(d.exposureNs<=1_000_000);
        assertTrue(d.iso<=1000);
        assertFalse(d.insufficientLight);
    }
    @Test public void bigMotionChangeTriggersEmergencyShutterCut(){
        CameraExposureOptimizer c=new CameraExposureOptimizer();
        c.updateMotionAware(1_000_000_000L,m(70,80,0),
            20_000_000,1200,limits(),1600,90,0,CameraExposureOptimizer.MotionMode.WALK);
        CameraExposureOptimizer.Decision urgent=c.updateMotionAware(
            1_100_000_000L,m(10,50,0),20_000_000,1200,limits(),
            1600,90,4,CameraExposureOptimizer.MotionMode.BIKE);
        assertTrue(urgent.changed);
        assertTrue(urgent.exposureNs<=1_000_000L);
    }
    @Test public void clampHardStopsEvenIfSceneBlack(){
        CameraExposureOptimizer c=new CameraExposureOptimizer();
        long exposure=33_000_000L;int iso=1600;
        for(int t=1;t<=30;t++){
            CameraExposureOptimizer.Decision d=c.update(t*1_000_000_000L,
                m(0,0,0),exposure,iso,limits(),1600,90);
            exposure=d.exposureNs;iso=d.iso;
        }
        // Motion-aware WALK profile explicitly caps long exposures at 1/40 s.
        assertEquals(25_000_000L,exposure);
        assertEquals(1600,iso);
    }
}
