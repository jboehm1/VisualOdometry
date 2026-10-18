package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;
public class RotationOdometerTest {
    private float[] yaw(double a){return new float[]{0,(float)Math.sin(a/2),0,(float)Math.cos(a/2)};}
    @Test public void fixedCameraCenterWithMultipleTurnsAddsNoDistanceOrSpeed(){
        Odometer o=new Odometer();o.start(0);
        for(int i=0;i<=720;i++)o.onPose(1,2,3,yaw(i*Math.PI/180),1_000_000_000L+i*33_333_333L);
        assertEquals(0,o.snapshot(25000).totalMeters,0);
        assertEquals(0,o.snapshot(25000).speedMps,0);
    }
    @Test public void rotationCannotChangeHowIdenticalTranslationsAreIntegrated(){
        Odometer a=new Odometer(),b=new Odometer();a.start(0);b.start(0);
        for(int i=0;i<=720;i++){
            double angle=i*Math.PI/180,x=.15*Math.cos(angle),z=.15*Math.sin(angle);
            long t=1_000_000_000L+i*33_333_333L;
            assertEquals(a.onPose(x,0,z,t),b.onPose(x,0,z,yaw(angle),t));
            assertEquals(a.snapshot(0).totalMeters,b.snapshot(0).totalMeters,0);
            assertEquals(a.snapshot(0).speedMps,b.snapshot(0).speedMps,0);
        }
        assertTrue(a.snapshot(0).totalMeters>1.8);
    }
}
