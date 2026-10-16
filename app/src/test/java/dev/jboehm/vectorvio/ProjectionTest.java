package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;
public class ProjectionTest {
    private final float[] identity={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
    @Test public void projectsCenterAndFlipsScreenY() {
        assertArrayEquals(new float[]{.5f,.5f},Projection.point(identity,0,0,0),.00001f);
        assertArrayEquals(new float[]{1,0},Projection.point(identity,1,1,0),.00001f);
    }
    @Test public void rejectsDepthAndOutsideScreen() {
        assertNull(Projection.point(identity,0,0,2));
        assertNull(Projection.point(identity,2,0,0));
        float[] perspective=identity.clone();perspective[15]=-1;
        assertNull(Projection.point(perspective,0,0,0));
    }
    @Test public void clipsPlaneCrossingViewportInsteadOfDiscardingIt() {
        float[] xy=Projection.polygon(identity,new float[]{-2,-.5f,0,2,-.5f,0,2,.5f,0,-2,.5f,0});
        assertEquals(8,xy.length);
        for(float v:xy)assertTrue(v>=0 && v<=1);
    }
    @Test public void clipsTrailAtNearPlane() {
        float[] xy=Projection.line(identity,new float[]{0,0,-2},new float[]{.5f,0,0});
        assertNotNull(xy);assertEquals(.625f,xy[0],.0001f);
        assertNull(Projection.line(identity,new float[]{0,0,-3},new float[]{0,0,-2}));
    }
}
