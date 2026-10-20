package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Random;
public final class CameraPreviewFlowTest {
    private static final int W=160,H=90;
    private static int[] texture(){
        int[] rgb=new int[W*H];
        Random rand=new Random(905);
        int[] cells=new int[W/4*H/4+W];
        for(int i=0;i<cells.length;i++)cells[i]=30+rand.nextInt(190);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            int q=cells[(y/4)*(W/4)+x/4];
            rgb[y*W+x]=0xff000000|(q<<16)|(q<<8)|q;
        }
        return rgb;
    }
    private static int[] translate(int[] input,int shiftX,int shiftY){
        int[] output=new int[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            int px=x-shiftX,py=y-shiftY;
            output[y*W+x]=px>=0&&px<W&&py>=0&&py<H?
                input[py*W+px]:0xff808080;
        }
        return output;
    }
    @Test public void constantSpeedCameraTranslationVisibleWithoutImuAcceleration(){
        int[] a=texture();int[] b=translate(a,5,-3);
        CameraPreviewFlow.Result result=CameraPreviewFlow.estimate(a,b,W,H,400_000_000L,8);
        assertTrue("real visual correspondences",result.reliable);
        assertTrue(result.inliers>=6);
        assertEquals(5,result.medianDx,1.0);
        assertEquals(-3,result.medianDy,1.0);
        assertTrue(result.fullFramePixelsPerSecond>50);
        long cap=CameraPreviewFlow.blurCapNs(result,100_000L,30_000_000L);
        assertTrue(cap<30_000_000L);
    }
    @Test public void emptySceneDoesNotInventBicycleMotion(){
        int[] blank=new int[W*H];
        java.util.Arrays.fill(blank,0xff969696);
        CameraPreviewFlow.Result result=CameraPreviewFlow.estimate(
            blank,blank,W,H,400_000_000L,8);
        assertFalse(result.reliable);assertEquals(0,result.inliers);
        assertEquals(30_000_000L,CameraPreviewFlow.blurCapNs(result,100_000L,30_000_000L));
    }
    @Test public void staleFrameMustNotBeInterpretedAsMotion(){
        int[] a=texture();int[] b=translate(a,3,0);
        assertFalse(CameraPreviewFlow.estimate(a,b,W,H,-1,8).reliable);
    }
}
