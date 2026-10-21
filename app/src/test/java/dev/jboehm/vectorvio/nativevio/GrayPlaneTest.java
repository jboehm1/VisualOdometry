package dev.jboehm.vectorvio.nativevio;
import java.nio.ByteBuffer;
import org.junit.Test;
import static org.junit.Assert.*;
public class GrayPlaneTest {
    @Test public void honorsStridesAndPositionWithoutChangingBuffer(){
        ByteBuffer b=ByteBuffer.wrap(new byte[]{99,1,0,2,0,3,88,4,0,5,0,6});b.position(1);
        byte[] y=GrayPlane.copy(b,3,2,6,2);
        assertArrayEquals(new byte[]{1,2,3,4,5,6},y);assertEquals(1,b.position());
        b.put(1,(byte)77);assertEquals(1,y[0]);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsTruncatedLastRow(){GrayPlane.copy(ByteBuffer.allocate(7),3,2,5,1);}
    @Test(expected=IllegalArgumentException.class) public void rejectsOverlappingRows(){GrayPlane.copy(ByteBuffer.allocate(20),4,2,3,1);}
}
