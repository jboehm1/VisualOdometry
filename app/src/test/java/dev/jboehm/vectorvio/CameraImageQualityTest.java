package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
public final class CameraImageQualityTest {
    private int[] frame(int y){int[] a=new int[96*54];Arrays.fill(a,0xff000000|(y<<16)|(y<<8)|y);return a;}
    @Test public void uniformDarkSceneIsNotPretendedToBeUsable(){
        CameraImageQuality.Metrics m=CameraImageQuality.analyze(frame(5),96,54);
        assertEquals(5,m.p50);assertTrue(m.dark());assertTrue(m.lacksTexture());
    }
    @Test public void highlightsIndicateSaturation(){
        CameraImageQuality.Metrics m=CameraImageQuality.analyze(frame(254),96,54);
        assertTrue(m.clippedFraction>.95);assertEquals(254,m.p90);
    }
    @Test public void contrastProducesEdgeProxy(){
        int[] a=frame(100);
        for(int y=0;y<54;y++)for(int x=0;x<96;x++)
            if((x+y)%2==0)a[y*96+x]=0xffd0d0d0;
        CameraImageQuality.Metrics m=CameraImageQuality.analyze(a,96,54);
        assertTrue(m.edgeFraction>.8);
        // Alternating single-pixel checkerboard is a useful noise/aliasing
        // adversarial fixture, NOT coherent VO texture.
        assertTrue(m.coherentEdgeFraction<.045);assertTrue(m.lacksTexture());
    }
    @Test public void smoothedFeaturesIgnoreBrightFeaturelessSky(){
        int[] a=frame(220);
        for(int y=27;y<54;y++)for(int x=0;x<96;x++){
            int g=((x/6)%2==0)?32:92;
            a[y*96+x]=0xff000000|(g<<16)|(g<<8)|g;
        }
        CameraImageQuality.Metrics m=CameraImageQuality.analyze(a,96,54);
        assertTrue("scene dominated by bright sky",m.p50>150);
        assertTrue("shadow texture recognized",m.coherentEdgeFraction>.045);
        assertTrue("texture-weighted median exposes dim route",m.featureMedian<m.p50);
        assertFalse(m.lacksTexture());
    }
    @Test public void smoothUniformSceneContainsNoFakeFeatures(){
        CameraImageQuality.Metrics m=CameraImageQuality.analyze(frame(105),96,54);
        assertEquals(105,m.featureMedian);
        assertEquals(0,m.coherentEdgeFraction,1e-8);
        assertTrue(m.lacksTexture());
    }
    @Test(expected=IllegalArgumentException.class)public void invalidFrameRejected(){
        CameraImageQuality.analyze(new int[10],96,54);
    }
}
