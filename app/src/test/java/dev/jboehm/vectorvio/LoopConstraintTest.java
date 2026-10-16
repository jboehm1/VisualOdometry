package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class LoopConstraintTest {
    private MapArchive.Keyframe frame(float x,float qy,float qw) {
        return new MapArchive.Keyframe(1,1,new float[]{x,0,0,0,qy,0,qw},new float[]{x,0,-1,1},new byte[0]);
    }
    @Test public void constrainsEndpointsButPreservesOriginal() {
        MapArchive source=new MapArchive(Arrays.asList(frame(0,0,1),frame(2,0,1),frame(.3f,0,1)));
        MapArchive corrected=LoopConstraint.apply(source,1);
        assertEquals(.3f,source.frames.get(2).pose[0],0);
        assertEquals(0,corrected.frames.get(2).pose[0],.00001f);
        assertEquals(1.85f,corrected.frames.get(1).pose[0],.00001f);
        assertTrue(corrected.manualLoopConstraint);
        assertFalse(source.manualLoopConstraint);
    }
    @Test public void correctsOrientationAndCloudInSameTransform() {
        MapArchive source=new MapArchive(Arrays.asList(frame(0,0,1),frame(2,0,1),frame(.3f,.70710677f,.70710677f)));
        MapArchive corrected=LoopConstraint.apply(source,1);
        float[] last=corrected.frames.get(2).pose;
        assertEquals(0,last[0],.00001f);assertEquals(0,last[4],.00001f);assertEquals(1,last[6],.00001f);
        assertEquals(1,corrected.frames.get(2).points[0],.00001f);
        assertArrayEquals(source.frames.get(0).points,corrected.frames.get(0).points,.00001f);
    }
    @Test(expected=IllegalArgumentException.class)public void refusesMissingSegment(){LoopConstraint.apply(new MapArchive(new ArrayList<>()),3);}
    @Test public void appliesTheManualClosureTransformToAnchoredMarkers() {
        List<MapArchive.Keyframe> frames=Arrays.asList(
            new MapArchive.Keyframe(1,1,new float[]{0,0,0,0,0,0,1},new float[0],new byte[0]),
            new MapArchive.Keyframe(1,2,new float[]{2,0,0,0,0,0,1},new float[0],new byte[0]),
            new MapArchive.Keyframe(1,3,new float[]{.2f,0,0,0,0,0,1},new float[0],new byte[0]));
        MapArchive.Poi poi=new MapArchive.Poi(1,3,"DOOR","exit",new float[]{.2f,0,0,0,0,0,1});
        MapArchive result=LoopConstraint.apply(new MapArchive(frames,Arrays.asList(poi),false),1);
        assertEquals(0,result.pois.get(0).pose[0],.00001f);
        assertEquals("exit",result.pois.get(0).label);
    }
}
