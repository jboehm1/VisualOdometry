package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.util.Collections;
public class LiveMapSanitizationTest {
    @Test public void invalidPointsDoNotPoisonValidKeyframes() throws Exception {
        float[] input={1,2,3,.8f, Float.NaN,2,3,.5f, 1,Float.POSITIVE_INFINITY,3,.5f,
            1,2,3,Float.NaN, 1,2,3,1.1f, 4,5,6,1};
        float[] clean=MapArchive.cleanPoints(input);
        assertArrayEquals(new float[]{1,2,3,.8f,4,5,6,1},clean,0);
        File file=File.createTempFile("live-map",".vmap");
        try {
            new MapArchive(Collections.singletonList(new MapArchive.Keyframe(1,1,
                new float[]{0,0,0,0,0,0,1},clean,new byte[0]))).write(file);
            assertArrayEquals(clean,MapArchive.read(file).frames.get(0).points,0);
        } finally {file.delete();}
        assertTrue(Float.isNaN(input[4]));
    }
    @Test public void allInvalidPointsStillAllowPoseOnlyMap() {
        assertEquals(0,MapArchive.cleanPoints(new float[]{0,0,Float.NEGATIVE_INFINITY,1}).length);
        assertFalse(MapArchive.validPose(new float[]{0,0,0,0,0,0,0}));
        assertFalse(MapArchive.validPose(new float[]{Float.NaN,0,0,0,0,0,1}));
        assertTrue(MapArchive.validPose(new float[]{0,0,0,0,0,0,1}));
    }
}
