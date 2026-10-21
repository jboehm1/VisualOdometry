package dev.jboehm.vectorvio;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
public class FramePipelineTest {
    @Test public void stalledConsumerHasOneTaskAndNoCatchUpBurst(){
        ArrayDeque<Runnable> queue=new ArrayDeque<>();AtomicInteger calls=new AtomicInteger();CoalescingTask task=new CoalescingTask(queue::add,calls::incrementAndGet);
        for(int i=0;i<1000;i++)task.offer();assertEquals(1,queue.size());queue.remove().run();assertEquals(1,calls.get());assertTrue(queue.isEmpty());task.offer();assertEquals(1,queue.size());queue.remove().run();assertEquals(2,calls.get());
    }
    @Test public void dispatchOrWorkFailureDoesNotLatchTask(){
        AtomicInteger dispatches=new AtomicInteger();CoalescingTask rejected=new CoalescingTask(r->{dispatches.incrementAndGet();throw new IllegalStateException();},()->{});
        for(int i=0;i<2;i++)try{rejected.offer();fail();}catch(IllegalStateException expected){}assertEquals(2,dispatches.get());
        ArrayDeque<Runnable> queue=new ArrayDeque<>();CoalescingTask bad=new CoalescingTask(queue::add,()->{throw new IllegalStateException();});
        for(int i=0;i<2;i++){bad.offer();assertEquals(1,queue.size());try{queue.remove().run();fail();}catch(IllegalStateException expected){}}
    }
    @Test public void duplicateFramesAreIgnoredAndRateCountsRealIntervals(){
        FrameRateMeter meter=new FrameRateMeter();assertFalse(meter.accept(0));assertTrue(meter.accept(1_000_000_000L));assertFalse(meter.accept(1_000_000_000L));assertFalse(meter.accept(900_000_000L));
        // Alternating 20/80 ms: 20 Hz, not average(50,12.5)=31.25 Hz.
        assertTrue(meter.accept(1_020_000_000L));assertTrue(meter.accept(1_100_000_000L));assertEquals(20,meter.hz(),.001);
        assertTrue(meter.accept(3_000_000_000L));assertEquals(0,meter.hz(),0);meter.reset();assertTrue(meter.accept(1));
    }
}
