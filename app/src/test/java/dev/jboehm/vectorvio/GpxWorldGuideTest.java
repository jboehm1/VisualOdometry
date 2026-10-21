package dev.jboehm.vectorvio;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Display geometry tests, NOT evidence of absolute GPS/ARCore precision. */
public final class GpxWorldGuideTest {
    @Test public void dashedRouteVisibleWithoutDetectedPlaneButNeverBecomesSolid(){
        List<double[]> e=Collections.singletonList(new double[]{0,0,20,0});
        List<float[]> observed=GpxGroundProjector.project(e,1.5,Collections.emptyList());
        assertTrue(observed.isEmpty());
        List<float[]> preview=GpxExtendedGuide.dashes(e,0,1.5,0,0,Collections.emptyList());
        assertTrue(preview.size()>=6);
        assertTrue(preview.size()<=GpxExtendedGuide.MAX_DASHES);
        float previous=-100;
        for(float[] line:preview){
            assertEquals(6,line.length);
            assertTrue(line[0]>previous);
            assertTrue(line[3]>line[0]);
            assertTrue(line[3]-line[0]<2.6f);
            assertEquals(.10f,line[1],.0001f);
            previous=line[0];
        }
    }
    @Test public void observedFloorWinsAndGroundProjectionIsIndependentOfCameraHeight(){
        GroundTrail.Floor floor=new GroundTrail.Floor(0,new float[]{-10,-10,10,-10,10,10,-10,10});
        List<double[]> e=Collections.singletonList(new double[]{0,0,7,0});
        List<GroundTrail.Floor> floors=Collections.singletonList(floor);
        assertFalse(GpxGroundProjector.project(e,1.2,floors).isEmpty());
        assertTrue(GpxExtendedGuide.dashes(e,0,1.2,0,0,floors).isEmpty());
        assertTrue(GpxExtendedGuide.dashes(e,0,1.8,0,0,floors).isEmpty());
    }
    @Test public void previewRespectsFiniteBoundsAndStrictGlobalBudget(){
        List<double[]> many=new ArrayList<>();
        for(int i=0;i<2000;i++)many.add(new double[]{0,i*.04,60,i*.04});
        List<float[]> g=GpxExtendedGuide.dashes(many,0,1.5,0,0,Collections.emptyList());
        assertEquals(GpxExtendedGuide.MAX_DASHES,g.size());
        assertTrue(GpxExtendedGuide.dashes(Collections.singletonList(new double[]{0,0,Double.NaN,10}),
             0,1,0,0,Collections.emptyList()).isEmpty());
    }
    @Test public void physicalWorldThicknessIsLargestNearbyButBounded(){
        assertTrue(GpxStrokePolicy.halfWidthMeters(0)>GpxStrokePolicy.halfWidthMeters(10));
        assertTrue(GpxStrokePolicy.halfWidthMeters(10)>GpxStrokePolicy.halfWidthMeters(90));
        assertTrue(GpxStrokePolicy.dpWidth(0)>GpxStrokePolicy.dpWidth(90));
        assertTrue(GpxStrokePolicy.halfWidthMeters(0)<.15f);
        WorldGeometry gpu=new WorldGeometry();gpu.add(3,new float[]{0,0,0,1,0,0},GpxStrokePolicy.halfWidthMeters(0));
        assertEquals(18,gpu.route.size);
        float thick=Math.abs(gpu.route.data[2]-gpu.route.data[5]);
        gpu.clear();gpu.add(3,new float[]{0,0,0,1,0,0},GpxStrokePolicy.halfWidthMeters(80));
        assertTrue(thick>Math.abs(gpu.route.data[2]-gpu.route.data[5]));
        gpu.finishRoute();for(int i=0;i<gpu.route.size;i++)assertTrue(Float.isFinite(gpu.route.data[i]));
    }
    @Test public void nearestEdgePointsTowardTrailNotTowardInventedWorldNorth(){
        List<double[]> edges=Arrays.asList(new double[]{6,-5,6,5},new double[]{10,-5,10,5});
        double[] p=GpxWorldCue.nearest(edges,0,0);
        assertNotNull(p);assertEquals(6,p[0],1e-9);assertEquals(0,p[1],1e-9);assertEquals(6,p[2],1e-9);
        assertNull(GpxWorldCue.nearest(Collections.emptyList(),0,0));
        assertNull(GpxWorldCue.nearest(edges,Double.NaN,0));
    }
}
