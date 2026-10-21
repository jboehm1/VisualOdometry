package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;
public class WorldGeometryTest {
    @Test public void polygonFanPreservesEveryVertexAndPoint(){WorldGeometry g=new WorldGeometry();g.add(0,new float[]{0,0,0,1,0,0,1,0,1,0,0,1});assertEquals(18,g.floor.size);g.add(2,new float[]{1,2,3});assertEquals(3,g.points.size);}
    @Test public void ribbonIsEightCentimetersWideAndDoesNotMutateMeasuredInput(){WorldGeometry g=new WorldGeometry();float[] line={0,0,0,1,.1f,0};g.add(3,line);assertEquals(18,g.route.size);assertEquals(.08f,Math.abs(g.route.data[2]-g.route.data[5]),1e-6);assertEquals(0,line[1],0);assertEquals(.1f,line[4],0);g.clear();assertEquals(0,g.route.size);}
    @Test public void invalidGeometryAndZeroLengthDoNotReachGpu(){WorldGeometry g=new WorldGeometry();g.add(2,new float[]{Float.NaN,0,0});g.add(3,new float[]{0,0,0,0,0,0});assertEquals(0,g.points.size);assertEquals(0,g.route.size);}
    @Test public void cornerSegmentsShareTheirEdgesWithoutMovingTheCenterline(){
        WorldGeometry g=new WorldGeometry();g.add(3,new float[]{0,0,0,1,0,0});g.add(3,new float[]{1,0,0,1,0,1});
        assertEquals(36,g.route.size);
        for(int i=0;i<3;i++)assertEquals(g.route.data[6+i],g.route.data[18+i],1e-6);
        for(int i=0;i<3;i++)assertEquals(g.route.data[15+i],g.route.data[21+i],1e-6);
        assertEquals(1,(g.route.data[6]+g.route.data[15])/2,1e-6);
        assertEquals(0,(g.route.data[8]+g.route.data[17])/2,1e-6);
        g.finishRoute();int finalized=g.route.size;g.finishRoute();assertEquals(finalized,g.route.size);
    }
    @Test public void trackingBreaksAndHairpinsHaveSeparateBoundedRoundedEnds(){
        WorldGeometry g=new WorldGeometry();g.add(3,new float[]{0,0,0,1,0,0});g.add(3,new float[]{5,0,0,6,0,0});g.finishRoute();
        for(int i=0;i<g.route.size;i+=3){float x=g.route.data[i];assertTrue(x<=1.041f||x>=4.959f);}
        g.clear();g.add(3,new float[]{0,0,0,1,0,0});g.add(3,new float[]{1,0,0,0,0,0});g.finishRoute();
        for(int i=0;i<g.route.size;i++)assertTrue(Float.isFinite(g.route.data[i]));
    }
}
