package dev.jboehm.vectorvio;

import org.junit.Test;
import static org.junit.Assert.*;

public class SyntheticVisionTest {
    @Test public void mapIsEmptyUntilPixelsProduceTrackedMotion(){
        SyntheticVision vision=new SyntheticVision();
        int[] blank=new int[SyntheticVision.WIDTH*SyntheticVision.HEIGHT];
        SyntheticVision.Result start=vision.process(blank,SyntheticVision.WIDTH,SyntheticVision.HEIGHT);
        assertEquals(0,start.points.size());assertEquals(0,start.path.size());
        int[] textured=texture(SyntheticVision.WIDTH,SyntheticVision.HEIGHT,0);
        for(int i=0;i<4;i++)start=vision.process(shift(textured,SyntheticVision.WIDTH,SyntheticVision.HEIGHT,i*4,0),SyntheticVision.WIDTH,SyntheticVision.HEIGHT);
        assertTrue("camera pixel correspondences should establish tracks",start.trackedCount>0);
        assertTrue("the estimated camera path should begin after visual motion is observed",start.path.size()>0);
    }

    @Test public void triangulatedLandmarksAreThreeDimensionalAndDeterministic(){
        int w=SyntheticVision.WIDTH,h=SyntheticVision.HEIGHT;int[] base=texture(w,h,17);
        SyntheticVision first=new SyntheticVision(),second=new SyntheticVision();
        SyntheticVision.Result a=null,b=null;
        for(int i=0;i<10;i++){
            int[] frame=shift(base,w,h,i*3,0);
            a=first.process(frame,w,h,zeroTurnSensors(i*33_000_000L));
            b=second.process(frame,w,h,zeroTurnSensors(i*33_000_000L));
        }
        assertNotNull(a);assertTrue("two-view image observations should triangulate map points",a.points.size()>0);
        SyntheticVision.MapPoint p=a.points.get(0);
        assertTrue(Float.isFinite(p.x)&&Float.isFinite(p.y)&&Float.isFinite(p.z));
        assertTrue("triangulated points must have positive estimated depth",p.depth>.15f);
        assertEquals(a.points.size(),b.points.size());
        assertEquals(a.x,b.x,0);assertEquals(a.y,b.y,0);assertEquals(a.z,b.z,0);
        assertTrue("estimated path stores XYZ camera positions",a.path.get(0).length==3);
    }

    @Test public void resetClearsEstimatorAndItsMap(){
        SyntheticVision vision=new SyntheticVision();int w=SyntheticVision.WIDTH,h=SyntheticVision.HEIGHT;int[] base=texture(w,h,3);
        for(int i=0;i<8;i++)vision.process(shift(base,w,h,i*3,0),w,h,sensors(i*33_000_000L));
        vision.reset();SyntheticVision.Result result=vision.process(base,w,h,sensors(0));
        assertEquals(0,result.points.size());assertEquals(0,result.path.size());assertEquals(0,result.x,0);assertEquals(0,result.y,0);assertEquals(0,result.z,0);
    }

    @Test public void gyroMeasurementChangesEstimatedOrientationWithoutGroundTruthPose(){
        SyntheticVision vision=new SyntheticVision();int[] blank=new int[SyntheticVision.WIDTH*SyntheticVision.HEIGHT];
        vision.process(blank,SyntheticVision.WIDTH,SyntheticVision.HEIGHT,new SyntheticVision.SensorSample(0,0,9.80665f,0,0,.3f,0));
        SyntheticVision.Result result=vision.process(blank,SyntheticVision.WIDTH,SyntheticVision.HEIGHT,new SyntheticVision.SensorSample(100_000_000L,0,9.80665f,0,0,.3f,0));
        assertEquals(.03f,result.yaw,.0001f);
        assertEquals(0,result.points.size());assertEquals(0,result.path.size());
    }

    private int[] texture(int w,int h,int seed){
        int[] pixels=new int[w*h];int state=seed+1;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            state=state*1664525+1013904223;int v=(state>>>24)&255;
            // Larger patches keep Harris features stable while retaining unique matches.
            int cell=((x/8)*73856093)^((y/8)*19349663)^seed;v=(v+(cell>>>24))&255;
            pixels[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;
        }
        return pixels;
    }
    private int[] shift(int[] src,int w,int h,int dx,int dy){
        int[] out=new int[src.length];for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int sx=x+dx,sy=y+dy;out[y*w+x]=sx>=0&&sx<w&&sy>=0&&sy<h?src[sy*w+sx]:0xff000000;
        }return out;
    }
    private SyntheticVision.SensorSample sensors(long t){SimulationModel.Sample s=SimulationModel.sample(t);return new SyntheticVision.SensorSample(s.timeNs,s.ax,s.ay,s.az,s.gx,s.gy,s.gz);}
    private SyntheticVision.SensorSample zeroTurnSensors(long t){return new SyntheticVision.SensorSample(t,0,9.80665f,0,0,0,0);}
}
