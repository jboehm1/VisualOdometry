package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;
public class CaptureProfileTest {
    @Test public void unknownOrAbsentProfileKeepsOriginalFullCapture(){
        for(String id:new String[]{null,"","invalid"}){
            CaptureProfile p=CaptureProfile.from(id);assertSame(CaptureProfile.FULL,p);
            assertEquals(1280,p.width);assertEquals(720,p.height);assertEquals(30,p.fps);assertEquals(3000000,p.bitrate);
        }
    }
    @Test public void lightChangesOnlyVideoAndBatchingNeverChangesRequestedSampling(){
        CaptureProfile p=CaptureProfile.from("light");assertEquals(15,p.fps);assertEquals(1500000,p.bitrate);
        for(CaptureProfile profile:new CaptureProfile[]{p,CaptureProfile.FULL,CaptureProfile.DETAIL}){
            assertEquals(5000,profile.imuPeriodUs);assertEquals(1000,profile.gpsPeriodMs);
            assertEquals(0,profile.batchingUs(0));assertEquals(50000,profile.batchingUs(20));assertEquals(250000,profile.batchingUs(1000));
        }
    }
}
