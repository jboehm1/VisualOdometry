package dev.jboehm.vectorvio;

import org.junit.Test;
import static org.junit.Assert.*;

public class SimulationModelTest {
    @Test public void samplesAreDeterministicAndPeriodic() {
        SimulationModel.Sample a=SimulationModel.sample(12_345_678_901L);
        SimulationModel.Sample b=SimulationModel.sample(12_345_678_901L);
        SimulationModel.Sample c=SimulationModel.sample(SimulationModel.DURATION_NS+12_345_678_901L);
        assertEquals(a.x,b.x,0);assertEquals(a.ay,b.ay,0);assertEquals(a.gy,c.gy,0);
        assertEquals(a.x,c.x,0);assertEquals(a.yaw,c.yaw,0);
    }
    @Test public void routeHasWalkScaleMotionAndFiniteImu() {
        SimulationModel.Sample start=SimulationModel.sample(0);
        SimulationModel.Sample middle=SimulationModel.sample(SimulationModel.DURATION_NS/4);
        assertTrue(Math.hypot(start.x-middle.x,start.z-middle.z)>3);
        double meanVertical=0;int count=0;
        for(long t=0;t<SimulationModel.DURATION_NS;t+=SimulationModel.DURATION_NS/79) {
            SimulationModel.Sample s=SimulationModel.sample(t);
            assertTrue(Float.isFinite(s.ax)&&Float.isFinite(s.ay)&&Float.isFinite(s.az));
            assertTrue(Float.isFinite(s.gx)&&Float.isFinite(s.gy)&&Float.isFinite(s.gz));
            assertTrue(s.y>.5f&&s.y<2.5f);
            meanVertical+=s.ay;count++;
        }
        assertEquals(9.80665,meanVertical/count,.15);
    }
}
