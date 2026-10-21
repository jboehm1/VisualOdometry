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
    private LiveFrameScheduler scheduler(AtomicInteger poses,AtomicInteger draws){return new LiveFrameScheduler(new Handler(Looper.getMainLooper()),poses::incrementAndGet,draws::incrementAndGet);}
    @Test public void displayBoostDoesNotChangeTrackingFrequency(){
        AtomicInteger poses=new AtomicInteger(),draws=new AtomicInteger();LiveFrameScheduler s=scheduler(poses,draws);
        s.setMode(LiveFrameScheduler.Mode.HOLD);s.start();advance(999);assertEquals(5,draws.get());assertTrue(poses.get()>=30);
        int p=poses.get(),d=draws.get();s.setPressed(true);advance(999);assertEquals(30,s.fps());assertTrue(draws.get()-d>=29);assertTrue(Math.abs((poses.get()-p)-30)<=1);
        p=poses.get();d=draws.get();s.setPressed(false);advance(999);assertEquals(5,s.fps());assertEquals(5,draws.get()-d);assertTrue(Math.abs((poses.get()-p)-30)<=1);s.stop();
    }
    @Test public void everyDisplayModeReceivesTheSameTrackingRequests(){
        int expected=-1;
        for(LiveFrameScheduler.Mode mode:LiveFrameScheduler.Mode.values()){
            AtomicInteger poses=new AtomicInteger(),draws=new AtomicInteger();LiveFrameScheduler s=scheduler(poses,draws);s.setMode(mode);s.start();advance(999);s.stop();
            if(expected<0)expected=poses.get();assertEquals(expected,poses.get());assertTrue(draws.get()>=mode.fps-1);assertTrue(draws.get()<=mode.fps+1);
        }
    }
    @Test public void stopClearsBoostAndCancelsBothLoops(){
        AtomicInteger poses=new AtomicInteger(),draws=new AtomicInteger();LiveFrameScheduler s=scheduler(poses,draws);
        s.setMode(LiveFrameScheduler.Mode.HOLD);s.start();s.start();s.setPressed(true);advance(100);s.stop();int p=poses.get(),d=draws.get();advance(1000);
        assertEquals(p,poses.get());assertEquals(d,draws.get());assertFalse(s.pressed());assertEquals(5,s.fps());s.start();advance(999);assertEquals(5,draws.get()-d);assertTrue(poses.get()-p>=30);s.stop();
    }
    @Test public void selectionResetsHoldAndUnknownSavedModeDefaultsToFull(){
        LiveFrameScheduler s=scheduler(new AtomicInteger(),new AtomicInteger());
        assertEquals(LiveFrameScheduler.Mode.FULL,LiveFrameScheduler.Mode.from(null));assertEquals(LiveFrameScheduler.Mode.FULL,LiveFrameScheduler.Mode.from("broken"));
        s.setMode(LiveFrameScheduler.Mode.HOLD);s.setPressed(true);s.setMode(LiveFrameScheduler.Mode.BALANCED);
        assertFalse(s.pressed());assertEquals(15,s.fps());s.setPressed(true);assertEquals(15,s.fps());s.stop();
    }
    @Test public void curvedRunningPathKeepsIdenticalDistanceAcrossDisplayModes(){
        double expected=-1;
        for(LiveFrameScheduler.Mode mode:LiveFrameScheduler.Mode.values()){
            Odometer odometer=new Odometer();odometer.start(0);AtomicInteger samples=new AtomicInteger();
            LiveFrameScheduler s=new LiveFrameScheduler(new Handler(Looper.getMainLooper()),()->{
                int i=samples.getAndIncrement();double angle=i/30.0; // radius 5 m, speed 5 m/s
                odometer.onPose(5*Math.cos(angle),0,5*Math.sin(angle),1_000_000_000L+i*33_333_333L);
            },()->{});
            s.setMode(mode);s.start();advance(9999);s.stop();Odometer.Snapshot result=odometer.snapshot(10000);
            assertTrue(result.totalMeters>49);assertEquals(0,result.gaps);
            if(expected<0)expected=result.totalMeters;assertEquals(expected,result.totalMeters,1e-10);
        }
    }
}
