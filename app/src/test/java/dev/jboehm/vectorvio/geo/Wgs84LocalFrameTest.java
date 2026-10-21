package dev.jboehm.vectorvio.geo;
import org.junit.Test;
import static org.junit.Assert.*;
public class Wgs84LocalFrameTest {
    @Test public void equatorUsesKnownWgs84SemiMajorRadius(){
        double[] en=new Wgs84LocalFrame(0,0).toLocal(0,.001);
        assertEquals(111.31949078762194,en[0],1e-8);assertEquals(0,en[1],0);
    }
    @Test public void inverseWorksAtDifferentLatitudesAndDateline(){
        for(double lat:new double[]{-70,0,48,80})for(double lon:new double[]{-179.9999,7,179.9999}){
            Wgs84LocalFrame f=new Wgs84LocalFrame(lat,lon);
            for(double[] en:new double[][]{{0,0},{30,20},{-1000,1500},{10000,-20000}}){
                double[] geo=f.toGeographic(en[0],en[1]),back=f.toLocal(geo[0],geo[1]);assertArrayEquals(en,back,1e-6);
            }
        }
    }
    @Test public void invalidAndBeyondHorizonDoNotInventCoordinates(){
        assertNull(new Wgs84LocalFrame(48,7).toGeographic(Double.NaN,0));
        assertNull(new Wgs84LocalFrame(48,7).toGeographic(20000000,0));
    }
}
