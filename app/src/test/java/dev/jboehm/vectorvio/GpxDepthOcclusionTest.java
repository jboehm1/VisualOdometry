package dev.jboehm.vectorvio;

import java.nio.ByteBuffer;
import org.junit.Test;
import static org.junit.Assert.*;

public class GpxDepthOcclusionTest {
    @Test public void highConfidenceForegroundOccludesWithExplicitMargin(){
        assertTrue(GpxDepthOcclusion.hiddenByDepth(1500,240,2.0));
        assertFalse(GpxDepthOcclusion.hiddenByDepth(1750,240,2.0));
        assertFalse(GpxDepthOcclusion.hiddenByDepth(1000,179,2.0));
        assertFalse(GpxDepthOcclusion.hiddenByDepth(0,255,2.0));
        assertFalse(GpxDepthOcclusion.hiddenByDepth(1000,255,Double.NaN));
        assertFalse(GpxDepthOcclusion.hiddenByDepth(1000,255,-1));
        assertFalse(GpxDepthOcclusion.hiddenByDepth(1000,255,.1));
    }
    @Test public void rawDepthUsesUnsignedLittleEndianAndRejectsTruncatedPlanes(){
        ByteBuffer data=ByteBuffer.wrap(new byte[]{(byte)0xDC,(byte)0x05,(byte)0xFF,(byte)0xFF});
        assertEquals(1500,GpxDepthOcclusion.unsigned16(data,0));
        assertEquals(65535,GpxDepthOcclusion.unsigned16(data,2));
        assertEquals(0,GpxDepthOcclusion.unsigned16(data,-1));
        assertEquals(0,GpxDepthOcclusion.unsigned16(data,3));
    }
    @Test public void resetNeverLeavesStaleMask(){
        GpxDepthOcclusion helper=new GpxDepthOcclusion();helper.reset();helper.reset();
        assertEquals(512,GpxDepthOcclusion.MAX_SEGMENTS);
        assertTrue(GpxDepthOcclusion.PERIOD_NS>=150_000_000L);
    }
}
