package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class GpxGroundProjectorTest {
    private GroundTrail.Floor floor(float y){return new GroundTrail.Floor(y,new float[]{-10,-10,10,-10,10,10,-10,10});}
    @Test public void changingPhoneHeightDoesNotMoveGroundRibbon(){
        List<double[]> edges=Collections.singletonList(new double[]{0,0,4,0});
        List<float[]> low=GpxGroundProjector.project(edges,1,Collections.singletonList(floor(0)));
        List<float[]> high=GpxGroundProjector.project(edges,2,Collections.singletonList(floor(0)));
        assertFalse(low.isEmpty());assertEquals(low.size(),high.size());
        for(int i=0;i<low.size();i++)assertArrayEquals(low.get(i),high.get(i),0);
    }
    @Test public void unknownFloorAndInvalidEdgesAreHidden(){
        assertTrue(GpxGroundProjector.project(Collections.singletonList(new double[]{0,0,4,0}),1,Collections.emptyList()).isEmpty());
        assertTrue(GpxGroundProjector.project(Collections.singletonList(new double[]{0,0,Double.NaN,0}),1,Collections.singletonList(floor(0))).isEmpty());
    }
    @Test public void slopeComesFromPlaneEquationNotPhoneHeight(){
        GroundTrail.Floor slope=new GroundTrail.Floor(0,0,0,-.2f,1,0,new float[]{-10,-10,10,-10,10,10,-10,10});
        List<float[]> lines=GpxGroundProjector.project(Collections.singletonList(new double[]{0,0,3,0}),2,Collections.singletonList(slope));
        assertFalse(lines.isEmpty());for(float[] l:lines){assertEquals(.2*l[0]+.035,l[1],1e-5);assertEquals(.2*l[3]+.035,l[4],1e-5);}
    }
    @Test public void tabletopOrderDoesNotOverrideObservedLowerFloor(){
        List<float[]> lines=GpxGroundProjector.project(Collections.singletonList(new double[]{0,0,3,0}),2,Arrays.asList(floor(1.2f),floor(0)));
        assertFalse(lines.isEmpty());for(float[] edge:lines){assertEquals(.035f,edge[1],1e-6);assertEquals(.035f,edge[4],1e-6);}
    }

}
