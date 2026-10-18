package dev.jboehm.vectorvio;
import android.os.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class LiveFrameSchedulerTest {
    private void advance(int ms){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));}
    @Test public void lowCadenceBoostsImmediatelyAndReturnsOnRelease(){
        AtomicInteger calls=new AtomicInteger();LiveFrameScheduler s=new LiveFrameScheduler(new Handler(Looper.getMainLooper()),calls::incrementAndGet);
        s.setMode(LiveFrameScheduler.Mode.HOLD);s.start();advance(999);assertEquals(5,calls.get());
        int before=calls.get();s.setPressed(true);advance(999);assertEquals(30,s.fps());assertTrue(calls.get()-before>=29);
        before=calls.get();s.setPressed(false);advance(999);assertEquals(5,s.fps());assertEquals(5,calls.get()-before);s.stop();
    }
    @Test public void stopClearsBoostAndCancelsEveryPendingRequest(){
        AtomicInteger calls=new AtomicInteger();LiveFrameScheduler s=new LiveFrameScheduler(new Handler(Looper.getMainLooper()),calls::incrementAndGet);
        s.setMode(LiveFrameScheduler.Mode.HOLD);s.start();s.start();s.setPressed(true);advance(100);s.stop();int before=calls.get();advance(1000);
        assertEquals(before,calls.get());assertFalse(s.pressed());assertEquals(5,s.fps());s.start();advance(999);assertEquals(5,calls.get()-before);s.stop();
    }
    @Test public void selectionResetsHoldAndUnknownSavedModeDefaultsToFull(){
        LiveFrameScheduler s=new LiveFrameScheduler(new Handler(Looper.getMainLooper()),()->{});
        assertEquals(LiveFrameScheduler.Mode.FULL,LiveFrameScheduler.Mode.from(null));assertEquals(LiveFrameScheduler.Mode.FULL,LiveFrameScheduler.Mode.from("broken"));
        s.setMode(LiveFrameScheduler.Mode.HOLD);s.setPressed(true);s.setMode(LiveFrameScheduler.Mode.BALANCED);
        assertFalse(s.pressed());assertEquals(15,s.fps());s.setPressed(true);assertEquals(15,s.fps());s.stop();
    }
}
