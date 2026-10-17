package dev.jboehm.vectorvio;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class GroundTrailTest {
    @Test public void selectsFloorBelowCameraRatherThanTableOrOtherStorey(){
        float[] square={-2,-2,2,-2,2,2,-2,2};
        Odometer.Point p=new Odometer.Point(0,1.6,0);
        assertEquals(0,GroundTrail.floorY(p,Arrays.asList(new GroundTrail.Floor(.8f,square),new GroundTrail.Floor(0,square),new GroundTrail.Floor(-3,square))),0);
        assertEquals(1.6,p.y,0);
    }
    @Test public void unknownFootprintIsExplicitRatherThanInventedFloor(){
        assertTrue(Float.isNaN(GroundTrail.floorY(new Odometer.Point(10,1.6,0),Collections.singletonList(new GroundTrail.Floor(0,new float[]{-1,-1,1,-1,1,1,-1,1})))));
    }
}
