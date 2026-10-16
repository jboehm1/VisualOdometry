package dev.jboehm.vectorvio;

import org.junit.Test;
import static org.junit.Assert.*;

public class SyntheticVisionTest {
    @Test public void imageOnlyTrackerDetectsAndMapsRepeatedVisualFeatures(){
        int w=SyntheticVision.WIDTH,h=SyntheticVision.HEIGHT;int[] frame=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int cell=((x/16)+(y/16))&1;int noise=((x*1103515245+y*12345)>>>20)&15;int v=(cell==0?55:205)+noise;
            frame[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;
        }
        SyntheticVision vision=new SyntheticVision();
        SyntheticVision.Result first=vision.process(frame,w,h),second=vision.process(frame,w,h),third=vision.process(frame,w,h);
        assertTrue("corners should be found in the camera pixels",first.detected>0);
        assertTrue("stationary frame correspondences should be tracked",second.trackedCount>0);
        assertTrue("map landmarks must come from tracked image observations",second.points.size()>0);
        assertEquals("persistent feature tracks should not add duplicate map landmarks",second.points.size(),third.points.size());
    }
    @Test public void resetStartsANewImageDerivedMap(){
        SyntheticVision vision=new SyntheticVision();int[] frame=new int[SyntheticVision.WIDTH*SyntheticVision.HEIGHT];
        for(int i=0;i<frame.length;i++)frame[i]=(i*7919)|0xff000000;
        vision.process(frame,SyntheticVision.WIDTH,SyntheticVision.HEIGHT);vision.reset();
        SyntheticVision.Result result=vision.process(frame,SyntheticVision.WIDTH,SyntheticVision.HEIGHT);
        assertEquals(0,result.points.size());assertEquals(0,result.path.size());
    }
}
