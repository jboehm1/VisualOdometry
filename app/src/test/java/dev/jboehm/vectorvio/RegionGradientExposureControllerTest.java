package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;
public final class RegionGradientExposureControllerTest {
    private CameraExposureOptimizer.Limits limits(){
        return new CameraExposureOptimizer.Limits(120_000L,33_000_000L,100,12800);
    }
    private CameraImageQuality.Metrics dark(){
        return new CameraImageQuality.Metrics(8,18,78,.7,.01,.14,.10,37,1000);
    }
    private RegionWeightedGradient.Result score(double gradient,double derivative,double irradiance){
        return new RegionWeightedGradient.Result(gradient,derivative,irradiance,.4,.02,true);
    }
    @Test public void imageFlowCapsShutterRegardlessOfBikeAcceleration(){
        CameraPreviewFlow.Result optic=new CameraPreviewFlow.Result(
            90,720,20,14,true,3,0);
        RegionGradientExposureController c=new RegionGradientExposureController();
        RegionGradientExposureController.Decision d=c.update(10_000_000_000L,
            score(25,12,.09),dark(),10_000_000L,3200,limits(),1600,
            0,CameraExposureOptimizer.MotionMode.BIKE,optic);
        assertTrue(d.shutterCapNs<=3_000_000L);
        assertTrue(d.exposureNs<=d.shutterCapNs);
        assertTrue(d.iso<=1600);
        assertTrue(d.changed);
    }
    @Test public void noTextureFallsBackToBrightnessFeedback(){
        RegionGradientExposureController c=new RegionGradientExposureController();
        RegionWeightedGradient.Result noFeatures=new RegionWeightedGradient.Result(0,0,.05,0,0,false);
        RegionGradientExposureController.Decision d=c.update(1_000_000_000L,
            noFeatures,dark(),3_000_000L,800,limits(),3200,
            Double.NaN,CameraExposureOptimizer.MotionMode.WALK,null);
        assertTrue(d.stepEv>0);
        assertEquals("BRIGHTNESS_FALLBACK",d.status);
    }
    @Test public void derivativeCanReduceOverExposure(){
        RegionGradientExposureController c=new RegionGradientExposureController();
        CameraImageQuality.Metrics bright=new CameraImageQuality.Metrics(130,205,245,.0,.01,.2,1200);
        RegionGradientExposureController.Decision d=c.update(1_000_000_000L,
            score(18,-15,.8),bright,2_000_000L,600,limits(),1600,
            0,CameraExposureOptimizer.MotionMode.WALK,null);
        assertTrue(d.stepEv<0);
        assertTrue((double)d.exposureNs*d.iso<(double)2_000_000L*600);
    }
    @Test public void afterFirstUpdateRateLimitsPhotometricChange(){
        RegionGradientExposureController c=new RegionGradientExposureController();
        c.update(1_000_000_000L,score(19,2,.3),dark(),1_000_000,300,limits(),1600,
            .1,CameraExposureOptimizer.MotionMode.WALK,null);
        RegionGradientExposureController.Decision d=c.update(1_050_000_000L,
            score(19,200,.3),dark(),1_000_000L,300,limits(),1600,
            .1,CameraExposureOptimizer.MotionMode.WALK,null);
        assertEquals("SETTLING",d.status);
        assertEquals(0,d.stepEv,0);
    }
}
