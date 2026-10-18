package dev.jboehm.vectorvio;
import com.google.ar.core.Pose;
import org.junit.Test;
import static org.junit.Assert.*;

public class AnchorCoordinatesTest {
    private Pose pose(double x,double y,double z,double yaw){return new Pose(new float[]{(float)x,(float)y,(float)z},new float[]{0,(float)Math.sin(yaw/2),0,(float)Math.cos(yaw/2)});}
    private void near(Pose a,Pose b){
        assertArrayEquals(a.getTranslation(),b.getTranslation(),2e-5f);
        float[] qa=a.getRotationQuaternion(),qb=b.getRotationQuaternion();
        double dot=0;for(int i=0;i<4;i++)dot+=qa[i]*qb[i];assertEquals(1,Math.abs(dot),2e-5);
    }
    @Test public void commonWorldCorrectionsCancelForEveryCameraOrientation(){
        AnchorCoordinates f=new AnchorCoordinates();Pose a=pose(2,1,-3,.7);
        for(int i=0;i<360;i++){
            Pose c=a.compose(pose(.3,0,.2,i*.02)),g=pose(i*.01,.2,-.1,i*.03);
            near(f.camera(a,c),f.camera(g.compose(a),g.compose(c)));
        }
    }
    @Test public void repeatedRebasesPreservePoseAndActualMillimeterTravel(){
        AnchorCoordinates f=new AnchorCoordinates();Pose a=Pose.IDENTITY;
        for(int i=0;i<120;i++){
            Pose c=pose(i*.001,i*.002,-i*.003,i*.05);
            Pose expected=f.camera(a,c),b=pose(i*.02,.5,-.2,i*.07);
            f.rebase(a,b);near(expected,f.camera(b,c));a=b;
        }
        f.reset();near(pose(1,2,3,.4),f.camera(Pose.IDENTITY,pose(1,2,3,.4)));
    }
    @Test public void actualAccumulatorIgnoresCommonCorrectionButKeepsRotatingTravel(){
        for(boolean move:new boolean[]{false,true}){
            AnchorCoordinates f=new AnchorCoordinates();Odometer o=new Odometer();o.start(0);
            for(int i=0;i<=360;i++){
                double angle=i*Math.PI/180;Pose g=pose(.1*Math.sin(angle),0,.1*Math.cos(angle),angle/2);
                Pose c=pose(move?.3*Math.cos(angle):1,0,move?.3*Math.sin(angle):0,angle);
                Pose m=f.camera(g,g.compose(c));
                o.onPose(m.tx(),m.ty(),m.tz(),1_000_000_000L+i*33_333_333L);
            }
            // Float Pose arithmetic leaves micrometer-scale roundoff, no distance deadband is used.
            assertEquals(move?360*2*.3*Math.sin(Math.PI/360):0,o.snapshot(0).totalMeters,2e-4);
        }
    }
    @Test public void rawWorldDisplayDoesNotEnterMetricDistance(){
        Odometer o=new Odometer();o.start(0);
        o.onMeasuredPose(0,0,0,new float[]{100,0,0},1_000_000_000L);
        o.onMeasuredPose(.001,0,0,new float[]{200,0,0},1_100_000_000L);
        assertEquals(.001,o.snapshot(0).totalMeters,1e-10);
        assertEquals(100,o.snapshot(0).worldTrail.get(0).x,0);
    }
}
