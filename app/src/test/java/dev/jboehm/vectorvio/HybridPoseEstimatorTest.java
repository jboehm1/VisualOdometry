package dev.jboehm.vectorvio;
import dev.jboehm.vectorvio.hybrid.HybridPoseEstimator;
import org.junit.Test;
import static org.junit.Assert.*;

public final class HybridPoseEstimatorTest {
    @Test public void noPoseMeansNoInventedRoute(){
        HybridPoseEstimator f=new HybridPoseEstimator();
        assertEquals(HybridPoseEstimator.State.NO_REFERENCE,f.snapshot(1).state);
        f.visualLost(1,HybridPoseEstimator.MotionKind.WALKING);
        assertFalse(f.predict(new HybridPoseEstimator.Prediction(2,1,0,0,
            HybridPoseEstimator.Source.NEURAL)));
    }
    @Test public void sensorDeltaInWorldFrameSurvivesShortOutage(){
        HybridPoseEstimator f=new HybridPoseEstimator();
        long t=10_000_000_000L;
        f.onVisualPose(t,4,5);
        f.visualLost(t+1_000_000L,HybridPoseEstimator.MotionKind.WALKING);
        assertTrue(f.predict(new HybridPoseEstimator.Prediction(t+1_001_000_000L,0.5,0.7,.2,
            HybridPoseEstimator.Source.NEURAL)));
        HybridPoseEstimator.Snapshot s=f.snapshot(t+1_001_000_000L);
        assertTrue(s.mayDrawIndicativeGpx());
        assertEquals(4.5,s.xM,1e-9);assertEquals(5.7,s.zM,1e-9);
        assertTrue(s.allowanceM>1.5);
    }
    @Test public void unrealisticVelocityAndOutOfOrderAreRejected(){
        HybridPoseEstimator f=new HybridPoseEstimator();long t=5_000_000_000L;
        f.onVisualPose(t,0,0);f.visualLost(t,HybridPoseEstimator.MotionKind.WALKING);
        assertFalse(f.predict(new HybridPoseEstimator.Prediction(t+1_000_000_000L,100,0,0,
            HybridPoseEstimator.Source.NEURAL)));
        assertFalse(f.predict(new HybridPoseEstimator.Prediction(t,1,0,0,
            HybridPoseEstimator.Source.NEURAL)));
        assertEquals(0,f.snapshot(t+1_000_000_000L).xM,1e-9);
    }
    @Test public void staleOutageBecomesUnreliableAndVisionReanchors(){
        HybridPoseEstimator f=new HybridPoseEstimator();long t=5_000_000_000L;
        f.onVisualPose(t,0,0);f.visualLost(t,HybridPoseEstimator.MotionKind.WALKING);
        assertEquals(HybridPoseEstimator.State.UNRELIABLE,
            f.snapshot(t+35_000_000_000L).state);
        f.onVisualPose(t+35_000_000_001L,100,5);
        assertEquals(HybridPoseEstimator.State.TRACKED,f.snapshot(t+35_000_000_001L).state);
        assertEquals(100,f.snapshot(t+35_000_000_001L).xM,1e-9);
    }
    @Test public void walkingAndCyclingAreDifferentMotionModels(){
        HybridPoseEstimator f=new HybridPoseEstimator();long t=100_000_000_000L;
        f.onVisualPose(t,0,0);f.visualLost(t,HybridPoseEstimator.MotionKind.WALKING);
        assertFalse(f.predict(new HybridPoseEstimator.Prediction(t+1_000_000_000L,8,0,.1,
            HybridPoseEstimator.Source.EXTERNAL_SENSOR)));
        f.reset();f.onVisualPose(t,0,0);f.visualLost(t,HybridPoseEstimator.MotionKind.CYCLING);
        assertTrue(f.predict(new HybridPoseEstimator.Prediction(t+1_000_000_000L,8,0,.1,
            HybridPoseEstimator.Source.EXTERNAL_SENSOR)));
    }
    @Test public void uncertaintyCannotPretendToBeCalibrated(){
        HybridPoseEstimator f=new HybridPoseEstimator();long t=5_000_000_000L;
        f.onVisualPose(t,0,0);f.visualLost(t,HybridPoseEstimator.MotionKind.WALKING);
        assertFalse(f.predict(new HybridPoseEstimator.Prediction(t+1_000_000_000L,1,0,-.5,
            HybridPoseEstimator.Source.NEURAL)));
        assertFalse(f.predict(new HybridPoseEstimator.Prediction(t+1_000_000_000L,1,0,Double.NaN,
            HybridPoseEstimator.Source.NEURAL)));
    }
}
