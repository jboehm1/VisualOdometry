package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;
public class RotationOdometerTest {
    private float[] yaw(double a){return new float[]{0,(float)Math.sin(a/2),0,(float)Math.cos(a/2)};}
    @Test public void multipleFullTurnsWithSmallRotationCoupledPoseErrorDoNotBecomeTravel(){
        Odometer o=new Odometer();o.start(0);
        for(int i=0;i<=720;i++){double a=i*Math.PI/180;o.onPose(.04*Math.cos(a),0,.04*Math.sin(a),yaw(a),1_000_000_000L+i*33_333_333L);}
        assertEquals(0,o.snapshot(25000).totalMeters,1e-6);
        assertEquals(0,o.snapshot(25000).gaps);
    }
    @Test public void translationWhileTurningIsNotDisabledOrSubtracted(){
        Odometer o=new Odometer();o.start(0);
        for(int i=0;i<=200;i++)o.onPose(i*.01,0,0,yaw(i*.02),1_000_000_000L+i*33_333_333L);
        assertTrue(o.snapshot(8000).totalMeters>=1.88);assertTrue(o.snapshot(8000).totalMeters<=2.001);
    }
    @Test public void slowStraightMovementStillAccumulatesAndQuaternionSignDoesNotCreateRotation(){
        Odometer o=new Odometer();o.start(0);for(int i=0;i<=100;i++)o.onPose(i*.005,0,0,new float[]{0,0,0,i%2==0?1:-1},1_000_000_000L+i*100_000_000L);
        assertTrue(o.snapshot(11000).totalMeters>=.465);
    }
    @Test public void pauseClearsRotationGateAndInvalidOrientationIsRejected(){
        Odometer o=new Odometer();o.start(0);o.onPose(0,0,0,yaw(0),1_000_000_000L);o.onPose(0,0,0,yaw(2),1_100_000_000L);
        o.pause(200);o.start(300);o.onPose(0,0,0,yaw(2),2_000_000_000L);o.onPose(.05,0,0,yaw(2),2_100_000_000L);
        assertEquals(.05,o.snapshot(500).totalMeters,1e-6);
        assertEquals(Odometer.Result.REJECTED,o.onPose(1,0,0,new float[]{0,0,0,0},2_200_000_000L));
    }
}
