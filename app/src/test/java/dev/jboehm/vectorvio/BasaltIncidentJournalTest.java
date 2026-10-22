package dev.jboehm.vectorvio;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public final class BasaltIncidentJournalTest {
    @Test public void minimalDurableStageWithoutIdentityOrSensorInformation(){
        var ctx=RuntimeEnvironment.getApplication();
        BasaltIncidentJournal.mark(ctx,"NATIVE_START_BEFORE_JNI");
        assertEquals("NATIVE_START_BEFORE_JNI",BasaltIncidentJournal.read(ctx));
        BasaltIncidentJournal.mark(ctx,"ERROR:Java.Linkage$Failed");
        assertEquals("ERROR:JAVA_LINKAGE_FAILED",BasaltIncidentJournal.read(ctx));
    }
}
