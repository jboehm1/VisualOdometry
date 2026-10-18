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
    @Test public void detectedSurfaceHeightDoesNotFollowPhoneHeight(){
        List<GroundTrail.Floor> floors=Collections.singletonList(new GroundTrail.Floor(0,new float[]{-2,-2,2,-2,2,2,-2,2}));
        for(double height:new double[]{.2,.5,1.2,2.5})assertEquals(0,GroundTrail.floorY(new Odometer.Point(0,height,0),floors),0);
    }
    @Test public void actualPlaneEquationPreservesInclinationRatherThanFlatteningToCenter(){
        // Plane y=.2*x, when a provider supplies that geometry; no slope detection is claimed.
        GroundTrail.Floor floor=new GroundTrail.Floor(0,0,0,-.2f,1,0,new float[]{-2,-2,2,-2,2,2,-2,2});
        assertEquals(.2,GroundTrail.floorY(new Odometer.Point(1,1.6,0),Collections.singletonList(floor)),1e-6);
        assertEquals(-.2,GroundTrail.floorY(new Odometer.Point(-1,1.6,0),Collections.singletonList(floor)),1e-6);
    }
    @Test public void malformedAndVerticalSurfacesAreNeverGround(){
        Odometer.Point p=new Odometer.Point(0,1.6,0);
        assertTrue(Float.isNaN(GroundTrail.floorY(p,Collections.singletonList(new GroundTrail.Floor(0,null)))));
        assertTrue(Float.isNaN(GroundTrail.floorY(p,Collections.singletonList(new GroundTrail.Floor(0,0,0,1,0,0,new float[]{-1,-1,1,-1,1,1})))));
    }
}
