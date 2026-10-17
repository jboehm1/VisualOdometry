package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.atomic.AtomicInteger;
public class OverlayPolicyTest {
    @Test public void failureCircuitSurvivesRecreationUntilExplicitRetry(){
        OverlayPolicy policy=new OverlayPolicy(false,false);assertTrue(policy.useGpu());
        policy.failure();assertFalse(policy.useGpu());
        OverlayPolicy recreated=new OverlayPolicy(false,policy.failed());assertFalse(recreated.useGpu());
        recreated.retry();assertTrue(recreated.useGpu());assertFalse(recreated.failed());
        recreated.chooseCanvas();assertFalse(recreated.useGpu());recreated.chooseAuto();assertTrue(recreated.useGpu());
    }
    @Test public void autoDoesNotClearExistingFailure(){OverlayPolicy p=new OverlayPolicy(true,true);p.chooseAuto();assertFalse(p.useGpu());}
    @Test public void healthyGlReturnsWithoutDraining(){AtomicInteger calls=new AtomicInteger();GlErrors.check("draw",()->{calls.incrementAndGet();return 0;});assertEquals(1,calls.get());}
    @Test public void outOfMemoryAndContextLossBecomeFailures(){
        for(int error:new int[]{0x505,0x507}){AtomicInteger calls=new AtomicInteger();try{GlErrors.check("upload",()->calls.getAndIncrement()==0?error:0);fail();}catch(IllegalStateException e){assertTrue(e.getMessage().contains(Integer.toHexString(error)));}assertEquals(2,calls.get());}
    }
    @Test public void brokenDriverErrorQueueIsBounded(){AtomicInteger calls=new AtomicInteger();try{GlErrors.check("draw",()->{calls.incrementAndGet();return 0x502;});fail();}catch(IllegalStateException expected){}assertEquals(8,calls.get());}
}
