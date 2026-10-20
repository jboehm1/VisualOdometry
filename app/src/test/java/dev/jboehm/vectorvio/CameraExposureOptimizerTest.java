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
    @Test public void clampHardStopsEvenIfSceneBlack(){
        CameraExposureOptimizer c=new CameraExposureOptimizer();
        long exposure=33_000_000L;int iso=1600;
        for(int t=1;t<=30;t++){
            CameraExposureOptimizer.Decision d=c.update(t*1_000_000_000L,
                m(0,0,0),exposure,iso,limits(),1600,90);
            exposure=d.exposureNs;iso=d.iso;
        }
        assertEquals(33_000_000L,exposure);
        assertEquals(1600,iso);
    }
}
