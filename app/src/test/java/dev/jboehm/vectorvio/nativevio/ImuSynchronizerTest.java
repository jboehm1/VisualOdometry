package dev.jboehm.vectorvio.nativevio;
import org.junit.Test;
import java.util.ArrayList;
import static org.junit.Assert.*;
public class ImuSynchronizerTest {
    @Test public void waitsForRealBracketAndInterpolates(){
        ArrayList<double[]> pairs=new ArrayList<>();ArrayList<Long> times=new ArrayList<>();
        ImuSynchronizer sync=new ImuSynchronizer((t,v)->{times.add(t);pairs.add(v);});
        sync.add(true,10_000_000,1,2,3);sync.add(false,15_000_000,4,5,6);
        assertTrue(pairs.isEmpty());sync.add(true,20_000_000,3,6,9);
        assertArrayEquals(new double[]{2,4,6,4,5,6},pairs.get(0),1e-10);
        assertEquals(Long.valueOf(15_000_000),times.get(0));
    }
    @Test public void rejectsExtrapolationGapsAndReordering(){
        ArrayList<double[]> pairs=new ArrayList<>();ImuSynchronizer sync=new ImuSynchronizer((t,v)->pairs.add(v));
        sync.add(false,1,0,0,0);sync.add(true,10_000_000,0,0,9.8);
        sync.add(false,20_000_000,0,0,0);sync.add(true,90_000_000,0,0,9.8);
        sync.add(true,80_000_000,0,0,9.8);sync.add(false,19_000_000,0,0,0);
        assertTrue(pairs.isEmpty());assertEquals(4,sync.dropped);
    }
    @Test public void boundedQueuesDropOldUnmatchedSamples(){
        ImuSynchronizer sync=new ImuSynchronizer((t,v)->fail("No acceleration bracket"));
        for(int i=1;i<=1000;i++)sync.add(false,i,0,0,0);
        assertEquals(744,sync.dropped);
    }
}
