package dev.jboehm.vectorvio;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic frustum/LOD test, not an ARCore, GNSS, or physical terrain validation. */
public final class GpxFullWorldRenderTest {
    /** A synthetic positive-Z perspective in homogeneous clip coordinates. */
    private static float[] forwardProjection(){
        float[] vp=new float[16];
        vp[0]=.001f;vp[5]=.001f;vp[11]=.001f;
        return vp;
    }
    @Test public void farPreviewDependsOnFrustumNotDistanceNinetyMetres(){
        float[] vp=forwardProjection();
        List<double[]> visible=Collections.singletonList(new double[]{100,500,122,500});
        List<float[]> lines=GpxExtendedGuide.dashesInView(visible,0,1.5,0,0,
            Collections.emptyList(),vp);
        assertFalse("far 500m line can be drawn beyond the old 90m cutoff",lines.isEmpty());
        List<double[]> behind=Collections.singletonList(new double[]{100,-500,122,-500});
        assertTrue("camera must not paint lines behind its current view",
            GpxExtendedGuide.dashesInView(behind,0,1.5,0,0,Collections.emptyList(),vp).isEmpty());
    }
    @Test public void longVisibleLoopsRespectBudgetButDoNotDropAllLateSegments(){
        List<double[]> edges=new ArrayList<>();
        for(int i=0;i<3200;i++)
            edges.add(new double[]{100+i*.02,500,103+i*.02,500});
        List<float[]> lines=GpxExtendedGuide.dashesInView(edges,0,1.5,0,0,
            Collections.emptyList(),forwardProjection());
        assertEquals(GpxExtendedGuide.MAX_WORLD_DASHES,lines.size());
        int late=0;
        for(float[] line:lines)if(line[0]>145)late++;
        assertTrue("later visible GPX portions should survive a dense beginning",late>50);
        for(float[] line:lines)for(float f:line)assertTrue(Float.isFinite(f));
    }
}
