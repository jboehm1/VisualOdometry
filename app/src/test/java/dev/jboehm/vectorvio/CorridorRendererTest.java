package dev.jboehm.vectorvio;

import org.junit.Test;
import static org.junit.Assert.*;

public class CorridorRendererTest {
    @Test public void everyCameraHeadingFollowsTheDirectionOfTravelOverThreeLoops(){
        for(SimulationModel.Route route:new SimulationModel.Route[]{SimulationModel.DEFAULT_ROUTE,SimulationModel.REVERSE_VALIDATION_ROUTE})
        for(long t=0;t<3*route.durationNs;t+=100_000_000L){
            SimulationModel.Sample a=SimulationModel.sample(t,route),b=SimulationModel.sample(t+1_000_000L,route);
            double dx=b.x-a.x,dz=b.z-a.z,length=Math.hypot(dx,dz);
            double forward=(-Math.sin(a.yaw)*dx-Math.cos(a.yaw)*dz)/length;
            assertTrue("camera must face forward at "+t,forward>.999);
            assertTrue(CorridorRenderer.corridorDistance(a.x,a.z,route)<-route.halfWidth+.005);
            assertEquals(t,a.timeNs);
        }
    }
    @Test public void everyHorizontalRayHitsAClosedWallIncludingAtRoundedTurns(){
        for(SimulationModel.Route route:new SimulationModel.Route[]{SimulationModel.DEFAULT_ROUTE,SimulationModel.REVERSE_VALIDATION_ROUTE})
        for(long t=0;t<route.durationNs;t+=500_000_000L){
            SimulationModel.Sample pose=SimulationModel.sample(t,route);
            for(int x=0;x<CorridorRenderer.WIDTH;x+=8){
                double u=(x+.5-ReplayCamera.CX)/ReplayCamera.FOCAL;
                double dx=Math.cos(pose.yaw)*u-Math.sin(pose.yaw),dz=-Math.sin(pose.yaw)*u-Math.cos(pose.yaw);
                double depth=CorridorRenderer.traceWall(pose.x,pose.z,dx,dz,route);
                assertTrue(depth>0&&depth<20);
                assertEquals("wall intersection must not escape through a polygon hole",0,CorridorRenderer.corridorDistance(pose.x+dx*depth,pose.z+dz*depth,route),.003);
            }
        }
    }
    @Test public void videoAndEstimatorShareTheActualPinholeCalibration(){
        assertEquals(SyntheticVision.WIDTH,CorridorRenderer.WIDTH);
        assertEquals(SyntheticVision.HEIGHT,CorridorRenderer.HEIGHT);
        int[] pixels=CorridorRenderer.render(SimulationModel.sample(8_000_000_000L),1);
        for(int color:pixels)assertEquals("each ray must resolve an opaque scene surface",0xff000000,color&0xff000000);
    }
}
