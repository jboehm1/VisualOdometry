package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;
public class WorldGeometryTest {
    @Test public void polygonFanPreservesEveryVertexAndPoint(){WorldGeometry g=new WorldGeometry();g.add(0,new float[]{0,0,0,1,0,0,1,0,1,0,0,1});assertEquals(18,g.floor.size);g.add(2,new float[]{1,2,3});assertEquals(3,g.points.size);}
    @Test public void ribbonIsEightCentimetersWideAndDoesNotMutateMeasuredInput(){WorldGeometry g=new WorldGeometry();float[] line={0,0,0,1,.1f,0};g.add(3,line);assertEquals(18,g.route.size);assertEquals(.08f,Math.abs(g.route.data[2]-g.route.data[5]),1e-6);assertEquals(0,line[1],0);assertEquals(.1f,line[4],0);g.clear();assertEquals(0,g.route.size);}
    @Test public void invalidGeometryAndZeroLengthDoNotReachGpu(){WorldGeometry g=new WorldGeometry();g.add(2,new float[]{Float.NaN,0,0});g.add(3,new float[]{0,0,0,0,0,0});assertEquals(0,g.points.size);assertEquals(0,g.route.size);}
}
