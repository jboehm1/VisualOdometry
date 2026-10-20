package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Random;
public final class RegionWeightedGradientTest {
    private static int[] frame(int w,int h,int lo,int hi){
        int[] rgb=new int[w*h];Random r=new Random(1122);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            // Coarse black-white texture, unlike camera readout high-freq noise
            int q=((x/9+y/7)%2==0?lo:hi);
            rgb[y*w+x]=0xff000000|(q<<16)|(q<<8)|q;
        }
        return rgb;
    }
    @Test public void lowExposureHasUsefulPositiveGradientSlope(){
        RegionWeightedGradient.Result s=RegionWeightedGradient.evaluate(
            frame(160,90,12,80),160,90);
        assertTrue(s.reliable);
        assertTrue(s.quality>1);
        assertTrue("adding exposure should expose dark features",s.derivativePerEv>0);
        assertTrue(s.texturedRegionFraction>0);
    }
    @Test public void highExposureHighlightClippingNeverGivesNaN(){
        RegionWeightedGradient.Result s=RegionWeightedGradient.evaluate(
            frame(160,90,235,255),160,90);
        assertTrue(Double.isFinite(s.quality));
    }
    @Test public void texturelessImageIsUnreliable(){
        int[] a=new int[160*90];java.util.Arrays.fill(a,0xff808080);
        RegionWeightedGradient.Result r=RegionWeightedGradient.evaluate(a,160,90);
        assertFalse(r.reliable);assertEquals(0,r.quality,0.000001);
    }
    @Test public void noiseLikeCheckerboardHasLowerQualityThanStructuralEdges(){
        int w=160,h=90;int[] noise=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int g=(x+y)%2==0?10:225;
            noise[y*w+x]=0xff000000|(g<<16)|(g<<8)|g;
        }
        RegionWeightedGradient.Result n=RegionWeightedGradient.evaluate(noise,w,h);
        RegionWeightedGradient.Result e=RegionWeightedGradient.evaluate(frame(w,h,30,190),w,h);
        assertTrue(e.quality>n.quality);
    }
    @Test(expected=IllegalArgumentException.class)
    public void malformedImageRejected(){
        RegionWeightedGradient.evaluate(new int[8],160,90);
    }
}
